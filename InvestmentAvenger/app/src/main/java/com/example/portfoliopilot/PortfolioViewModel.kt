package com.example.portfoliopilot

import android.app.Application
import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

val Context.dataStore by preferencesDataStore(name = "portfolio_v13_prefs")

private const val SPLASH_MILLIS = 5000L
private const val REFRESH_MILLIS = 5 * 60 * 1000L

private val MARKET_WATCHLIST = listOf(
    "^NSEI", "^BSESN", "^GSPC", "^IXIC",
    "RELIANCE.NS", "TCS.NS", "HDFCBANK.NS", "BHARTIARTL.NS", "ICICIBANK.NS",
    "INFY.NS", "SBIN.NS", "LICI.NS", "HINDUNILVR.NS", "ITC.NS", "LT.NS",
    "BAJFINANCE.NS", "HCLTECH.NS", "MARUTI.NS", "SUNPHARMA.NS", "TATASTEEL.NS",
    "AXISBANK.NS", "KOTAKBANK.NS", "TITAN.NS", "ASIANPAINT.NS", "NTPC.NS",
    "POWERGRID.NS", "ADANIENT.NS", "WIPRO.NS", "ULTRACEMCO.NS",
    "AAPL", "MSFT", "NVDA", "GOOGL", "AMZN", "META", "TSLA", "BRK-B", "LLY", "AVGO", "JPM",
    "V", "UNH", "XOM", "MA", "HD", "JNJ", "PG", "BAC", "NFLX", "ADBE", "CRM",
    "AMD", "INTC", "CSCO"
)

/** Everything that is written to disk, captured in one piece so saves are consistent. */
private data class SaveSnapshot(
    val holdings: List<Holding>,
    val purchases: List<PurchaseTransaction>,
    val sales: List<SaleTransaction>,
    val notes: List<Note>,
    val userName: String,
    val profilePhoto: String,
    val themeMode: String,
    val accentColor: String,
    val fontStyle: String,
    val textSize: String,
    val notificationsEnabled: Boolean,
    val notifyHour: Int,
    val notifAsked: Boolean,
    val appLockEnabled: Boolean
)

class PortfolioViewModel(application: Application) : AndroidViewModel(application) {
    private val appContext: Context get() = getApplication<Application>()

    private val HOLDINGS_KEY = stringPreferencesKey("holdings_json")
    private val PURCHASES_KEY = stringPreferencesKey("purchases_json")
    private val SALES_KEY = stringPreferencesKey("sales_json")
    private val NOTES_KEY = stringPreferencesKey("notes_json")
    private val USER_NAME_KEY = stringPreferencesKey("user_name")
    private val PROFILE_PHOTO_KEY = stringPreferencesKey("profile_photo_uri")
    private val THEME_MODE_KEY = stringPreferencesKey("theme_mode")
    private val ACCENT_COLOR_KEY = stringPreferencesKey("accent_color")
    private val FONT_STYLE_KEY = stringPreferencesKey("font_style")
    private val TEXT_SIZE_KEY = stringPreferencesKey("text_size")
    private val NOTIFY_ENABLED_KEY = booleanPreferencesKey("notify_enabled")
    private val NOTIFY_HOUR_KEY = intPreferencesKey("notify_hour")
    private val NOTIFY_ASKED_KEY = booleanPreferencesKey("notify_asked")
    private val APP_LOCK_KEY = booleanPreferencesKey("app_lock")

    // ---- Portfolio data ----
    var holdings by mutableStateOf<List<Holding>>(emptyList())
        private set
    var purchases by mutableStateOf<List<PurchaseTransaction>>(emptyList())
        private set
    var sales by mutableStateOf<List<SaleTransaction>>(emptyList())
        private set
    var savedNotes by mutableStateOf<List<Note>>(emptyList())
        private set

    // ---- Live market data ----
    var marketQuotes by mutableStateOf<List<MarketQuote>>(emptyList())
        private set
    var quoteStatus by mutableStateOf("Open this tab to load live quotes")
        private set
    var holdingsStatus by mutableStateOf("")
        private set
    // USD -> INR rate. Used ONLY to rank "Top Holdings" across markets; totals stay per currency.
    var usdInr by mutableStateOf(0.0)
        private set

    // ---- Profile and settings ----
    var userName by mutableStateOf("")
        private set
    var profilePhoto by mutableStateOf("")
        private set
    var themeMode by mutableStateOf("System")
        private set
    var accentColor by mutableStateOf("Emerald")
        private set
    var fontStyle by mutableStateOf("Default")
        private set
    var textSize by mutableStateOf("Auto")
        private set
    var notificationsEnabled by mutableStateOf(true)
        private set
    var notifyHour by mutableIntStateOf(9)
        private set
    var notifAsked by mutableStateOf(false)
        private set
    var appLockEnabled by mutableStateOf(false)
        private set
    var locked by mutableStateOf(false)
        private set

    // ---- Screen state ----
    var selectedTab by mutableIntStateOf(0)
    var showBuyDialog by mutableStateOf(false)
    var showOnboarding by mutableStateOf(false)
    var showProfileDialog by mutableStateOf(false)
    var showSplash by mutableStateOf(true)
    var showNotepad by mutableStateOf(false)
    var activeHoldingForAction by mutableStateOf<Holding?>(null)

    private var loaded = false
    private var marketLoading = false
    private var refreshingHoldings = false

    // Saves run on an app-wide scope so the last change is never lost when the screen closes.
    private val saveScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val saveRequests = Channel<SaveSnapshot>(Channel.CONFLATED)

    init {
        saveScope.launch {
            for (snapshot in saveRequests) writeSnapshot(snapshot)
        }
        viewModelScope.launch {
            loadSaved()
            refreshHoldingPrices()
            if (notificationsEnabled) QuoteNotifier.schedule(appContext, notifyHour, replace = false)
        }
        viewModelScope.launch {
            delay(SPLASH_MILLIS)
            showSplash = false
        }
        viewModelScope.launch {
            while (isActive) {
                delay(REFRESH_MILLIS)
                refreshHoldingPrices()
                if (marketQuotes.isNotEmpty()) fetchLiveMarketQuotes()
            }
        }
    }

    private inline fun <reified T> decodeList(json: String?): List<T> {
        if (json.isNullOrBlank()) return emptyList()
        return try {
            AppJson.decodeFromString<List<T>>(json)
        } catch (_: Exception) {
            emptyList()
        }
    }

    private suspend fun loadSaved() {
        val prefs = try {
            appContext.dataStore.data.first()
        } catch (_: Exception) {
            null
        }
        if (prefs != null) {
            val h = withContext(Dispatchers.Default) { decodeList<Holding>(prefs[HOLDINGS_KEY]) }
            val p = withContext(Dispatchers.Default) { decodeList<PurchaseTransaction>(prefs[PURCHASES_KEY]) }
            val s = withContext(Dispatchers.Default) { decodeList<SaleTransaction>(prefs[SALES_KEY]) }
            val n = withContext(Dispatchers.Default) { decodeList<Note>(prefs[NOTES_KEY]) }
            holdings = h
            purchases = p
            sales = s
            savedNotes = n
            userName = prefs[USER_NAME_KEY].orEmpty()
            profilePhoto = prefs[PROFILE_PHOTO_KEY].orEmpty()
            prefs[THEME_MODE_KEY]?.takeIf { it.isNotBlank() }?.let { themeMode = it }
            prefs[ACCENT_COLOR_KEY]?.takeIf { it.isNotBlank() }?.let { accentColor = it }
            prefs[FONT_STYLE_KEY]?.takeIf { it.isNotBlank() }?.let { fontStyle = it }
            prefs[TEXT_SIZE_KEY]?.takeIf { it.isNotBlank() }?.let { textSize = it }
            prefs[NOTIFY_ENABLED_KEY]?.let { notificationsEnabled = it }
            prefs[NOTIFY_HOUR_KEY]?.let { notifyHour = it.coerceIn(0, 23) }
            prefs[NOTIFY_ASKED_KEY]?.let { notifAsked = it }
            prefs[APP_LOCK_KEY]?.let { appLockEnabled = it }
        }
        loaded = true
        locked = appLockEnabled
        if (userName.isBlank()) showOnboarding = true
    }

    private fun saveAll() {
        if (!loaded) return
        saveRequests.trySend(
            SaveSnapshot(
                holdings, purchases, sales, savedNotes, userName, profilePhoto, themeMode, accentColor,
                fontStyle, textSize, notificationsEnabled, notifyHour, notifAsked, appLockEnabled
            )
        )
    }

    private suspend fun writeSnapshot(s: SaveSnapshot) {
        try {
            val hJson = AppJson.encodeToString(s.holdings)
            val pJson = AppJson.encodeToString(s.purchases)
            val sJson = AppJson.encodeToString(s.sales)
            val nJson = AppJson.encodeToString(s.notes)
            appContext.dataStore.edit { prefs ->
                prefs[HOLDINGS_KEY] = hJson
                prefs[PURCHASES_KEY] = pJson
                prefs[SALES_KEY] = sJson
                prefs[NOTES_KEY] = nJson
                prefs[USER_NAME_KEY] = s.userName
                prefs[PROFILE_PHOTO_KEY] = s.profilePhoto
                prefs[THEME_MODE_KEY] = s.themeMode
                prefs[ACCENT_COLOR_KEY] = s.accentColor
                prefs[FONT_STYLE_KEY] = s.fontStyle
                prefs[TEXT_SIZE_KEY] = s.textSize
                prefs[NOTIFY_ENABLED_KEY] = s.notificationsEnabled
                prefs[NOTIFY_HOUR_KEY] = s.notifyHour
                prefs[NOTIFY_ASKED_KEY] = s.notifAsked
                prefs[APP_LOCK_KEY] = s.appLockEnabled
            }
        } catch (_: Exception) {
        }
    }

    // ------------------------------------------------------------------
    // Profile / settings
    // ------------------------------------------------------------------
    fun updateProfile(name: String, photoPath: String?) {
        userName = name.trim()
        if (photoPath != null) profilePhoto = photoPath
        showOnboarding = false
        showProfileDialog = false
        saveAll()
    }

    fun updateAppearance(
        theme: String = themeMode,
        accent: String = accentColor,
        font: String = fontStyle,
        size: String = textSize
    ) {
        themeMode = theme
        accentColor = accent
        fontStyle = font
        textSize = size
        saveAll()
    }

    fun updateNotifications(enabled: Boolean, hour: Int = notifyHour) {
        notificationsEnabled = enabled
        notifyHour = hour
        if (enabled) QuoteNotifier.schedule(appContext, hour, replace = true) else QuoteNotifier.cancel(appContext)
        saveAll()
    }

    fun markNotifAsked() {
        notifAsked = true
        saveAll()
    }

    fun setAppLock(enabled: Boolean) {
        appLockEnabled = enabled
        locked = false
        saveAll()
    }

    fun lock() {
        if (appLockEnabled) locked = true
    }

    fun unlock() {
        locked = false
    }

    // ------------------------------------------------------------------
    // Notepad
    // ------------------------------------------------------------------
    /** Creates or updates a note. Returns its id. */
    fun saveNote(id: String?, title: String, content: String): String {
        val noteId = id ?: UUID.randomUUID().toString()
        val note = Note(
            id = noteId,
            title = title.trim().ifBlank { "Untitled note" },
            content = content,
            timestamp = SimpleDateFormat("dd MMM yyyy, hh:mm a", Locale.getDefault()).format(Date()),
            updatedAt = System.currentTimeMillis()
        )
        savedNotes = listOf(note) + savedNotes.filterNot { it.id == noteId }
        saveAll()
        return noteId
    }

    fun deleteNote(id: String) {
        savedNotes = savedNotes.filterNot { it.id == id }
        saveAll()
    }

    // ------------------------------------------------------------------
    // Live market data (Yahoo Finance)
    // ------------------------------------------------------------------
    fun ensureMarketLoaded() {
        if (marketQuotes.isEmpty()) fetchLiveMarketQuotes()
    }

    fun fetchLiveMarketQuotes() {
        if (marketLoading) return
        marketLoading = true
        viewModelScope.launch {
            quoteStatus = "Refreshing live quotes from Yahoo Finance…"
            val infos = YahooFinance.quotes(MARKET_WATCHLIST)
            val list = MARKET_WATCHLIST.mapNotNull { symbol ->
                val info = infos[symbol] ?: return@mapNotNull null
                val displayName = when (symbol) {
                    "^NSEI" -> "NIFTY 50"
                    "^BSESN" -> "BSE SENSEX"
                    "^GSPC" -> "S&P 500"
                    "^IXIC" -> "NASDAQ Composite"
                    else -> info.name
                }
                val change = if (info.prevClose > 0.0) info.price - info.prevClose else 0.0
                val pct = if (info.prevClose > 0.0) change / info.prevClose * 100.0 else 0.0
                val india = symbol.endsWith(".NS") || symbol.startsWith("^NSE") || symbol.startsWith("^BSE")
                MarketQuote(
                    symbol = symbol.removeSuffix(".NS"),
                    name = displayName,
                    market = if (india) MARKET_INDIA else MARKET_USA,
                    currency = if (india) "INR" else "USD",
                    price = info.price,
                    change = change,
                    changePct = pct
                )
            }
            if (list.isNotEmpty()) {
                marketQuotes = list
                val time = SimpleDateFormat("dd MMM, hh:mm a", Locale.getDefault()).format(Date())
                quoteStatus = "Yahoo Finance • ${list.size} quotes • Updated $time"
            } else {
                quoteStatus = "Live feed unavailable. Check your internet and tap refresh."
            }
            marketLoading = false
        }
    }

    /** Updates the price of every holding that is linked to Yahoo Finance. */
    fun refreshHoldingPrices() {
        val symbols = holdings.map { it.yahooSymbol }.filter { it.isNotBlank() }.distinct()
        if (symbols.isEmpty() || refreshingHoldings) return
        refreshingHoldings = true
        viewModelScope.launch {
            holdingsStatus = "Updating live prices…"
            val infos = YahooFinance.quotes(symbols + "INR=X")
            infos["INR=X"]?.let { if (it.price > 0.0) usdInr = it.price }
            val prices = symbols.mapNotNull { s -> infos[s]?.let { s to it.price } }.toMap()
            if (prices.isNotEmpty()) {
                // Re-read the list so a purchase made while fetching is not overwritten.
                holdings = holdings.map { h -> prices[h.yahooSymbol]?.let { h.copy(currentPrice = it) } ?: h }
                val time = SimpleDateFormat("dd MMM, hh:mm a", Locale.getDefault()).format(Date())
                holdingsStatus = "Live prices updated $time"
                saveAll()
            } else {
                holdingsStatus = "Live prices unavailable • showing last saved prices"
            }
            refreshingHoldings = false
        }
    }

    suspend fun searchInvestments(query: String): List<YahooSearchHit> = YahooFinance.search(query)

    suspend fun fetchInvestmentQuote(symbol: String): YahooQuoteInfo? = YahooFinance.quote(symbol)

    suspend fun searchMarket(query: String): List<MarketQuote> = YahooFinance.searchWithPrices(query)

    /** Current market price of an investment you already own (used by "Buy more"). */
    suspend fun liveQuoteFor(holding: Holding): YahooQuoteInfo? {
        val candidates = when {
            holding.yahooSymbol.isNotBlank() -> listOf(holding.yahooSymbol)
            holding.market == MARKET_INDIA -> listOf("${holding.symbol}.NS", "${holding.symbol}.BO")
            holding.market == MARKET_USA -> listOf(holding.symbol)
            else -> emptyList()
        }
        for (candidate in candidates) {
            val quote = YahooFinance.quote(candidate)
            if (quote != null && quote.currency == holding.currency) return quote
        }
        return null
    }

    // ------------------------------------------------------------------
    // Buying and selling (average-cost method)
    // ------------------------------------------------------------------
    fun updateCurrentPrice(holdingId: String, newPrice: Double) {
        holdings = holdings.map { h -> if (h.id == holdingId) h.copy(currentPrice = newPrice) else h }
        saveAll()
    }

    /**
     * Buying more: the new average cost is (old units x old average + new units x price) / total units.
     * [livePrice] is the current market price when known; it becomes the holding's current price,
     * so buying below the market never shows an artificial loss.
     */
    fun executeBuy(
        name: String, symbol: String, market: String, category: String, currency: String,
        units: Double, price: Double, charges: Double, notes: String,
        yahooSymbol: String = "", livePrice: Double? = null
    ) {
        val now = Date()
        val dateStr = SimpleDateFormat("dd MMM yyyy", Locale.getDefault()).format(now)
        val timeStr = SimpleDateFormat("hh:mm a", Locale.getDefault()).format(now)
        val cleanSymbol = symbol.trim().uppercase()

        val existing = holdings.find {
            (yahooSymbol.isNotBlank() && it.yahooSymbol.equals(yahooSymbol, ignoreCase = true)) ||
                (it.symbol.equals(cleanSymbol, ignoreCase = true) && it.market.equals(market, ignoreCase = true))
        }
        val holdingId = existing?.id ?: UUID.randomUUID().toString()

        holdings = if (existing != null) {
            val totalUnits = existing.units + units
            val newAverage = (existing.units * existing.averageCost + units * price) / totalUnits
            val updated = existing.copy(
                units = totalUnits,
                averageCost = newAverage,
                currentPrice = livePrice ?: existing.currentPrice,
                yahooSymbol = existing.yahooSymbol.ifBlank { yahooSymbol }
            )
            holdings.map { if (it.id == existing.id) updated else it }
        } else {
            holdings + Holding(
                id = holdingId,
                name = name.trim(),
                symbol = cleanSymbol,
                market = market,
                category = category.trim().ifBlank { "Other" },
                currency = currency,
                units = units,
                averageCost = price,
                currentPrice = livePrice ?: price,
                yahooSymbol = yahooSymbol
            )
        }

        purchases = listOf(
            PurchaseTransaction(
                holdingId = holdingId,
                name = name.trim(),
                symbol = cleanSymbol,
                market = market,
                category = category.trim().ifBlank { "Other" },
                currency = currency,
                units = units,
                price = price,
                charges = charges,
                totalCost = units * price + charges,
                date = dateStr,
                time = timeStr,
                notes = notes.trim()
            )
        ) + purchases
        saveAll()
        showBuyDialog = false
        activeHoldingForAction = null
        if (holdings.any { it.id == holdingId && it.yahooSymbol.isNotBlank() }) refreshHoldingPrices()
    }

    /** Selling: the cost of the sold units is units x the holding's average cost; the average cost stays the same. */
    fun executeSell(holding: Holding, unitsToSell: Double, sellPrice: Double, charges: Double, notes: String) {
        val current = holdings.find { it.id == holding.id } ?: return
        if (unitsToSell <= 0.0 || unitsToSell > current.units + 1e-9) return

        val now = Date()
        val costBasis = unitsToSell * current.averageCost
        val grossAmount = unitsToSell * sellPrice
        val netAmount = grossAmount - charges
        val realized = netAmount - costBasis

        sales = listOf(
            SaleTransaction(
                holdingId = current.id,
                name = current.name,
                symbol = current.symbol,
                market = current.market,
                category = current.category,
                currency = current.currency,
                unitsSold = unitsToSell,
                avgCostBasis = current.averageCost,
                sellPrice = sellPrice,
                grossAmount = grossAmount,
                charges = charges,
                netAmount = netAmount,
                realizedPnl = realized,
                realizedPnlPct = if (costBasis == 0.0) 0.0 else realized / costBasis * 100.0,
                date = SimpleDateFormat("dd MMM yyyy", Locale.getDefault()).format(now),
                time = SimpleDateFormat("hh:mm a", Locale.getDefault()).format(now),
                notes = notes.trim()
            )
        ) + sales

        val remaining = current.units - unitsToSell
        holdings = if (remaining <= 0.00001) {
            holdings.filterNot { it.id == current.id }
        } else {
            holdings.map { if (it.id == current.id) it.copy(units = remaining) else it }
        }
        saveAll()
        activeHoldingForAction = null
    }

    // ------------------------------------------------------------------
    // Reports
    // ------------------------------------------------------------------
    fun generateTextReport(): String {
        val sb = StringBuilder()
        val stamp = SimpleDateFormat("dd MMM yyyy, hh:mm a", Locale.getDefault()).format(Date())
        sb.append("📊 INVESTMENT AVENGER – PORTFOLIO REPORT\n$stamp\n\n")

        buildMarketStats(holdings).filter { it.holdings.isNotEmpty() }.forEach { m ->
            sb.append("${marketLabel(m.market)}: value ${fmtMoney(m.value, m.currency)} • invested ${fmtMoney(m.invested, m.currency)} • P&L ${fmtSignedMoney(m.pnl, m.currency)} (${fmtPct(m.pnlPct)})\n")
        }

        sb.append("\n--- ACTIVE HOLDINGS (${holdings.size}) ---\n")
        if (holdings.isEmpty()) sb.append("No active holdings.\n")
        holdings.forEach { h ->
            sb.append("• ${h.name} (${h.symbol}) – ${h.market} • ${h.category}\n")
            sb.append("  ${fmtQty(h.units)} units @ avg ${fmtMoney(h.averageCost, h.currency)} | price ${fmtMoney(h.currentPrice, h.currency)} | value ${fmtMoney(h.value, h.currency)} | P&L ${fmtSignedMoney(h.pnl, h.currency)} (${fmtPct(h.pnlPct)})\n")
        }

        sb.append("\n--- PURCHASES (${purchases.size}) ---\n")
        if (purchases.isEmpty()) sb.append("No purchases recorded.\n")
        purchases.forEach { p ->
            sb.append("• [${p.date}] ${p.name} (${p.symbol}) – ${fmtQty(p.units)} units @ ${fmtMoney(p.price, p.currency)} (total ${fmtMoney(p.totalCost, p.currency)})")
            if (p.notes.isNotBlank()) sb.append(" | Note: ${p.notes}")
            sb.append("\n")
        }

        sb.append("\n--- SALES (${sales.size}) ---\n")
        if (sales.isEmpty()) sb.append("No sales recorded.\n")
        sales.forEach { s ->
            sb.append("• [${s.date}] ${s.name} (${s.symbol}) – sold ${fmtQty(s.unitsSold)} units @ ${fmtMoney(s.sellPrice, s.currency)} | profit ${fmtSignedMoney(s.realizedPnl, s.currency)}")
            if (s.notes.isNotBlank()) sb.append(" | Note: ${s.notes}")
            sb.append("\n")
        }
        return sb.toString()
    }

    private fun csv(value: String): String = "\"" + value.replace("\"", "\"\"") + "\""

    fun exportCsvFile(context: Context): File? {
        return try {
            val file = File(context.cacheDir, "portfolio_report.csv")
            val sb = StringBuilder()
            sb.append("PORTFOLIO HOLDINGS\n")
            sb.append("Name,Symbol,Market,Category,Currency,Units,AvgCost,CurrentPrice,Invested,CurrentValue,PnL,PnL%\n")
            holdings.forEach { h ->
                sb.append("${csv(h.name)},${csv(h.symbol)},${h.market},${csv(h.category)},${h.currency},${h.units},${h.averageCost},${h.currentPrice},${h.invested},${h.value},${h.pnl},${"%.2f".format(Locale.US, h.pnlPct)}\n")
            }
            sb.append("\nPURCHASE HISTORY\n")
            sb.append("Date,Time,Name,Symbol,Market,Category,Units,Price,Charges,TotalCost,Notes\n")
            purchases.forEach { p ->
                sb.append("${p.date},${p.time},${csv(p.name)},${csv(p.symbol)},${p.market},${csv(p.category)},${p.units},${p.price},${p.charges},${p.totalCost},${csv(p.notes)}\n")
            }
            sb.append("\nSALES HISTORY\n")
            sb.append("Date,Time,Name,Symbol,Market,UnitsSold,AvgCost,SellPrice,GrossAmount,Charges,NetAmount,RealizedPnL,Notes\n")
            sales.forEach { s ->
                sb.append("${s.date},${s.time},${csv(s.name)},${csv(s.symbol)},${s.market},${s.unitsSold},${s.avgCostBasis},${s.sellPrice},${s.grossAmount},${s.charges},${s.netAmount},${s.realizedPnl},${csv(s.notes)}\n")
            }
            file.writeText(sb.toString())
            file
        } catch (_: Exception) {
            null
        }
    }
}
