package com.example.portfoliopilot

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay

// ---------------------------------------------------------------------------
// Profile
// ---------------------------------------------------------------------------
/** First-run welcome (no [onDismiss]) and the "Edit profile" dialog (with Cancel). */
@Composable
fun ProfileDialog(
    initialName: String,
    initialPhoto: String,
    onComplete: (String, String?) -> Unit,
    onDismiss: (() -> Unit)? = null
) {
    val context = LocalContext.current
    var nameInput by remember { mutableStateOf(initialName) }
    var photo by remember { mutableStateOf(initialPhoto) }
    var photoChanged by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val quote = remember { dailyQuote() }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) {
            val saved = saveProfilePhoto(context, uri)
            if (saved != null) {
                photo = saved
                photoChanged = true
            } else {
                error = "Could not use that photo. Please try another one."
            }
        }
    }

    val cancelButton: (@Composable () -> Unit)? = if (onDismiss != null) {
        { TextButton(onClick = onDismiss) { Text("Cancel") } }
    } else null

    AlertDialog(
        onDismissRequest = { onDismiss?.invoke() },
        title = { Text(if (onDismiss == null) "Welcome to Investment Avenger" else "Edit profile") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Card(
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("💡 \"${quote.first}\"", style = MaterialTheme.typography.bodySmall, fontStyle = FontStyle.Italic)
                        Text("— ${quote.second}", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, modifier = Modifier.align(Alignment.End))
                    }
                }
                ProfileAvatar(photo, nameInput, 80.dp, Modifier.clickable { picker.launch("image/*") })
                TextButton(onClick = { picker.launch("image/*") }) { Text("Choose profile photo") }
                OutlinedTextField(
                    value = nameInput,
                    onValueChange = { nameInput = it },
                    label = { Text("Your name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                if (nameInput.isBlank()) error = "Please enter your name."
                else onComplete(nameInput.trim(), if (photoChanged) photo else null)
            }) { Text("Save") }
        },
        dismissButton = cancelButton
    )
}

// ---------------------------------------------------------------------------
// Manual price update (for investments that are not linked to Yahoo Finance)
// ---------------------------------------------------------------------------
@Composable
fun UpdatePriceDialog(holding: Holding, onDismiss: () -> Unit, onConfirm: (Double) -> Unit) {
    var priceInput by remember { mutableStateOf(fmtPriceInput(holding.currentPrice)) }
    var error by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Update price - ${holding.name}") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Enter the current market price per unit:")
                OutlinedTextField(
                    value = priceInput,
                    onValueChange = { priceInput = it },
                    label = { Text("Current price (${currencySymbol(holding.currency)})") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true
                )
                error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val p = priceInput.toDoubleOrNull()
                if (p == null || p < 0) error = "Please enter a valid price." else onConfirm(p)
            }) { Text("Update") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

// ---------------------------------------------------------------------------
// Buy
// ---------------------------------------------------------------------------
data class BuyRequest(
    val name: String,
    val symbol: String,
    val market: String,
    val category: String,
    val currency: String,
    val units: Double,
    val price: Double,
    val charges: Double,
    val notes: String,
    val yahooSymbol: String,
    /** Current market price, when known. */
    val livePrice: Double?
)

private val CATEGORY_SUGGESTIONS = listOf("Stock", "Mutual Fund", "ETF", "Gold", "Crypto", "Bond", "Real Estate")

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun BuyInvestmentDialog(
    initialHolding: Holding?,
    onSearch: suspend (String) -> List<YahooSearchHit>,
    onFetchQuote: suspend (String) -> YahooQuoteInfo?,
    onLiveQuoteFor: suspend (Holding) -> YahooQuoteInfo?,
    onDismiss: () -> Unit,
    onConfirm: (BuyRequest) -> Unit
) {
    val isNew = initialHolding == null
    var useYahoo by remember { mutableStateOf(false) }

    // Manual entry
    var name by remember { mutableStateOf("") }
    var symbol by remember { mutableStateOf("") }
    var market by remember { mutableStateOf(MARKET_INDIA) }
    var category by remember { mutableStateOf("Stock") }

    // Shared
    var units by remember { mutableStateOf("") }
    var price by remember { mutableStateOf(initialHolding?.let { fmtPriceInput(it.currentPrice) } ?: "") }
    var charges by remember { mutableStateOf("0") }
    var notes by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }

    // Yahoo search (new purchase)
    var query by remember { mutableStateOf("") }
    var searching by remember { mutableStateOf(false) }
    var searchError by remember { mutableStateOf<String?>(null) }
    var hits by remember { mutableStateOf<List<YahooSearchHit>>(emptyList()) }
    var picked by remember { mutableStateOf<YahooSearchHit?>(null) }
    var pickedQuote by remember { mutableStateOf<YahooQuoteInfo?>(null) }
    var loadingQuote by remember { mutableStateOf(false) }
    var asGlobal by remember { mutableStateOf(false) }

    // Buy more: current market price of what you already own
    var liveQuote by remember { mutableStateOf<YahooQuoteInfo?>(null) }
    var loadingLive by remember { mutableStateOf(initialHolding != null) }

    LaunchedEffect(initialHolding?.id) {
        val holding = initialHolding ?: return@LaunchedEffect
        loadingLive = true
        val quote = try {
            onLiveQuoteFor(holding)
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            null
        }
        liveQuote = quote
        if (quote != null) price = fmtPriceInput(quote.price)
        loadingLive = false
    }

    LaunchedEffect(query, useYahoo) {
        if (!useYahoo || picked != null) return@LaunchedEffect
        if (query.trim().length < 2) { hits = emptyList(); searchError = null; searching = false; return@LaunchedEffect }
        delay(400)
        searching = true
        searchError = null
        try {
            hits = onSearch(query)
            if (hits.isEmpty()) searchError = "No investment found for \"${query.trim()}\"."
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            hits = emptyList()
            searchError = "Could not reach Yahoo Finance. Check your internet and try again."
        }
        searching = false
    }

    LaunchedEffect(picked) {
        val hit = picked ?: return@LaunchedEffect
        loadingQuote = true
        pickedQuote = null
        val quote = try {
            onFetchQuote(hit.symbol)
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            null
        }
        loadingQuote = false
        if (quote == null) {
            searchError = "Could not fetch the live price for ${hit.symbol}. Try again or use manual entry."
        } else {
            pickedQuote = quote
            price = fmtPriceInput(quote.price)
            asGlobal = false
            searchError = null
        }
    }

    val yahooCurrencyOk = pickedQuote?.currency.let { it == "INR" || it == "USD" }
    val unitsVal = units.toDoubleOrNull()
    val priceVal = price.toDoubleOrNull()
    val chargesVal = charges.toDoubleOrNull() ?: 0.0

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initialHolding == null) "New purchase" else "Buy more - ${initialHolding.name}") },
        text = {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.heightIn(max = 480.dp)) {
                // ---- Buy more: live market price ----
                if (initialHolding != null) {
                    item {
                        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer), modifier = Modifier.fillMaxWidth()) {
                            Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                when {
                                    loadingLive -> {
                                        Text("Fetching the current market price…", style = MaterialTheme.typography.bodySmall)
                                        LinearProgressIndicator(Modifier.fillMaxWidth())
                                    }
                                    liveQuote != null -> {
                                        Text("Current market price (live)", style = MaterialTheme.typography.labelSmall)
                                        Text(fmtMoney(liveQuote!!.price, initialHolding.currency), fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                                    }
                                    else -> {
                                        Text("Last known price", style = MaterialTheme.typography.labelSmall)
                                        Text(fmtMoney(initialHolding.currentPrice, initialHolding.currency), fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                                        Text("Live price not found for this investment.", style = MaterialTheme.typography.labelSmall)
                                    }
                                }
                                Text(
                                    "You own ${fmtQty(initialHolding.units)} units at an average of ${fmtMoney(initialHolding.averageCost, initialHolding.currency)}.",
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }
                        }
                    }
                }

                // ---- New purchase: choose how to add ----
                if (isNew) {
                    item {
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            FilterChip(selected = !useYahoo, onClick = { useYahoo = false }, label = { Text("a. Manually add") })
                            FilterChip(selected = useYahoo, onClick = { useYahoo = true }, label = { Text("b. Fetch from Yahoo") })
                        }
                    }
                }

                if (isNew && useYahoo) {
                    if (picked == null) {
                        item {
                            OutlinedTextField(
                                value = query,
                                onValueChange = { query = it },
                                label = { Text("Search investment name or code") },
                                placeholder = { Text("e.g. Reliance, TCS, Apple, VOO") },
                                leadingIcon = { Icon(Icons.Default.Search, null) },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                        if (searching) item { LinearProgressIndicator(Modifier.fillMaxWidth()) }
                        searchError?.let { message ->
                            item { Text(message, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
                        }
                        items(hits, key = { it.symbol }) { hit ->
                            Card(
                                modifier = Modifier.fillMaxWidth().clickable { picked = hit },
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                            ) {
                                Column(Modifier.fillMaxWidth().padding(10.dp)) {
                                    Text(hit.name, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                                    Text(
                                        "${hit.symbol} • ${hit.exchange} • ${hit.type.lowercase().replaceFirstChar { it.uppercase() }}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.secondary
                                    )
                                }
                            }
                        }
                    } else {
                        item {
                            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer), modifier = Modifier.fillMaxWidth()) {
                                Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Text("Selected investment", style = MaterialTheme.typography.labelSmall)
                                    Text(picked!!.name, fontWeight = FontWeight.Bold)
                                    Text("Code: ${picked!!.symbol} • ${picked!!.exchange}", style = MaterialTheme.typography.bodySmall)
                                    val q = pickedQuote
                                    if (loadingQuote) {
                                        Text("Fetching live price…", style = MaterialTheme.typography.bodySmall)
                                    } else if (q != null) {
                                        val shown = if (yahooCurrencyOk) fmtMoney(q.price, q.currency) else "${q.currency} ${fmtQty(q.price)}"
                                        Text("Live price: $shown", fontWeight = FontWeight.SemiBold)
                                        if (!yahooCurrencyOk) {
                                            Text("Only INR and USD investments are supported (this one trades in ${q.currency}).", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                                        } else if (q.currency == "USD") {
                                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                                Text("Track as Global asset", style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
                                                Switch(checked = asGlobal, onCheckedChange = { asGlobal = it })
                                            }
                                        }
                                    }
                                    TextButton(onClick = { picked = null; pickedQuote = null; hits = emptyList(); price = ""; searchError = null }) {
                                        Text("Change investment")
                                    }
                                }
                            }
                        }
                        searchError?.let { message ->
                            item { Text(message, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
                        }
                    }
                } else if (isNew) {
                    // ---- Manual entry ----
                    item { OutlinedTextField(name, { name = it }, label = { Text("Investment name") }, singleLine = true, modifier = Modifier.fillMaxWidth()) }
                    item { OutlinedTextField(symbol, { symbol = it }, label = { Text("Ticker / scheme code") }, singleLine = true, modifier = Modifier.fillMaxWidth()) }
                    item { Text("Market", style = MaterialTheme.typography.labelLarge) }
                    item {
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            MARKETS.forEach { m ->
                                FilterChip(selected = market == m, onClick = { market = m }, label = { Text(marketLabel(m)) })
                            }
                        }
                    }
                    item {
                        Text(
                            when (market) {
                                MARKET_INDIA -> "Amounts in ₹ (INR)."
                                MARKET_USA -> "Amounts in $ (USD)."
                                else -> "Global assets (other countries, gold, crypto…) are tracked in $ (USD)."
                            },
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.secondary
                        )
                    }
                    item {
                        OutlinedTextField(
                            category, { category = it },
                            label = { Text("Asset category (type any name)") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                    item {
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            CATEGORY_SUGGESTIONS.forEach { c ->
                                FilterChip(selected = category.equals(c, ignoreCase = true), onClick = { category = c }, label = { Text(c) })
                            }
                        }
                    }
                }

                // ---- Amounts ----
                val showAmounts = !isNew || !useYahoo || (picked != null && pickedQuote != null)
                if (showAmounts) {
                    item { OutlinedTextField(units, { units = it }, label = { Text("Quantity / units") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), singleLine = true, modifier = Modifier.fillMaxWidth()) }
                    item {
                        OutlinedTextField(
                            price, { price = it },
                            label = { Text(if (isNew && !useYahoo) "Purchase price per unit" else "Purchase price per unit (live, editable)") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                    item { OutlinedTextField(charges, { charges = it }, label = { Text("Brokerage / charges") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), singleLine = true, modifier = Modifier.fillMaxWidth()) }
                    item { OutlinedTextField(notes, { notes = it }, label = { Text("Notes (optional)") }, minLines = 1, maxLines = 3, modifier = Modifier.fillMaxWidth()) }

                    val currency = when {
                        initialHolding != null -> initialHolding.currency
                        useYahoo -> pickedQuote?.currency ?: "USD"
                        else -> marketCurrency(market)
                    }
                    if (unitsVal != null && priceVal != null && unitsVal > 0 && priceVal >= 0) {
                        item {
                            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer), modifier = Modifier.fillMaxWidth()) {
                                Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                    Text("Total cost: ${fmtMoney(unitsVal * priceVal + chargesVal, currency)}", fontWeight = FontWeight.SemiBold)
                                    if (initialHolding != null) {
                                        val totalUnits = initialHolding.units + unitsVal
                                        val newAverage = (initialHolding.units * initialHolding.averageCost + unitsVal * priceVal) / totalUnits
                                        val marketPrice = liveQuote?.price ?: initialHolding.currentPrice
                                        val pnl = (marketPrice - newAverage) * totalUnits
                                        Text("New average cost: ${fmtMoney(newAverage, currency)}", style = MaterialTheme.typography.bodySmall)
                                        Text(
                                            "Profit/loss at market price: ${fmtSignedMoney(pnl, currency)}",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = if (pnl >= 0) PROFIT_GREEN else LOSS_RED,
                                            fontWeight = FontWeight.SemiBold
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                error?.let { message -> item { Text(message, color = MaterialTheme.colorScheme.error) } }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val u = units.toDoubleOrNull()
                val p = price.toDoubleOrNull()
                val valid = u != null && p != null && u > 0 && p >= 0
                when {
                    initialHolding != null -> {
                        if (!valid) error = "Please enter valid units and price."
                        else onConfirm(
                            BuyRequest(
                                initialHolding.name, initialHolding.symbol, initialHolding.market, initialHolding.category,
                                initialHolding.currency, u!!, p!!, chargesVal, notes,
                                liveQuote?.symbol ?: "", liveQuote?.price
                            )
                        )
                    }
                    useYahoo -> {
                        val quote = pickedQuote
                        val hit = picked
                        if (hit == null || quote == null) error = "Search and select an investment first."
                        else if (!yahooCurrencyOk) error = "Only INR and USD investments are supported."
                        else if (!valid) error = "Please enter valid units and price."
                        else {
                            val india = quote.currency == "INR"
                            val yahooCategory = when (hit.type) {
                                "ETF" -> "ETF"
                                "MUTUALFUND" -> "Mutual Fund"
                                "CRYPTOCURRENCY" -> "Crypto"
                                else -> "Stock"
                            }
                            onConfirm(
                                BuyRequest(
                                    hit.name.ifBlank { quote.name }, hit.symbol.removeSuffix(".NS"),
                                    if (india) MARKET_INDIA else if (asGlobal) MARKET_GLOBAL else MARKET_USA,
                                    yahooCategory, quote.currency, u!!, p!!, chargesVal, notes, hit.symbol, quote.price
                                )
                            )
                        }
                    }
                    else -> {
                        if (name.isBlank() || symbol.isBlank() || category.isBlank()) error = "Please fill the name, ticker and category."
                        else if (!valid) error = "Please enter valid units and price."
                        else onConfirm(
                            BuyRequest(name, symbol, market, category, marketCurrency(market), u!!, p!!, chargesVal, notes, "", null)
                        )
                    }
                }
            }) { Text("Confirm purchase") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

// ---------------------------------------------------------------------------
// Sell
// ---------------------------------------------------------------------------
@Composable
fun SellInvestmentDialog(
    holding: Holding,
    onDismiss: () -> Unit,
    onConfirmSell: (Double, Double, Double, String) -> Unit
) {
    var unitsSoldStr by remember { mutableStateOf(fmtQty(holding.units)) }
    var sellPriceStr by remember { mutableStateOf(fmtPriceInput(holding.currentPrice)) }
    var chargesStr by remember { mutableStateOf("0") }
    var notes by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }

    val unitsSold = unitsSoldStr.toDoubleOrNull() ?: 0.0
    val sellPrice = sellPriceStr.toDoubleOrNull() ?: 0.0
    val charges = chargesStr.toDoubleOrNull() ?: 0.0
    val grossAmount = unitsSold * sellPrice
    val netAmount = grossAmount - charges
    val costBasis = unitsSold * holding.averageCost
    val realized = netAmount - costBasis
    val realizedPct = if (costBasis == 0.0) 0.0 else realized / costBasis * 100.0

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Sell - ${holding.name}") },
        text = {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.heightIn(max = 500.dp)) {
                item { Text("Available: ${fmtQty(holding.units)} units • Average cost: ${fmtMoney(holding.averageCost, holding.currency)}") }
                item { OutlinedTextField(unitsSoldStr, { unitsSoldStr = it }, label = { Text("Quantity to sell") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), singleLine = true, modifier = Modifier.fillMaxWidth()) }
                item { OutlinedTextField(sellPriceStr, { sellPriceStr = it }, label = { Text("Selling price per unit") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), singleLine = true, modifier = Modifier.fillMaxWidth()) }
                item { OutlinedTextField(chargesStr, { chargesStr = it }, label = { Text("Selling charges / brokerage") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), singleLine = true, modifier = Modifier.fillMaxWidth()) }
                item { OutlinedTextField(notes, { notes = it }, label = { Text("Notes (optional)") }, minLines = 1, maxLines = 3, modifier = Modifier.fillMaxWidth()) }
                item {
                    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer), modifier = Modifier.fillMaxWidth()) {
                        Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text("Sale preview", fontWeight = FontWeight.Bold)
                            Text("Net proceeds: ${fmtMoney(netAmount, holding.currency)}")
                            Text("Cost (units × average cost): ${fmtMoney(costBasis, holding.currency)}")
                            Text(
                                "Realized P&L: ${fmtSignedMoney(realized, holding.currency)} (${fmtPct(realizedPct)})",
                                color = if (realized >= 0) PROFIT_GREEN else LOSS_RED,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
                error?.let { message -> item { Text(message, color = MaterialTheme.colorScheme.error) } }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                if (unitsSold <= 0 || unitsSold > holding.units + 1e-9 || sellPrice < 0) error = "Invalid quantity or selling price."
                else onConfirmSell(unitsSold, sellPrice, charges, notes)
            }) { Text("Confirm sale") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}
