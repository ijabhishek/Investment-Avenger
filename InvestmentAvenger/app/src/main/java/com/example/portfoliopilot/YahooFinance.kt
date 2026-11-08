package com.example.portfoliopilot

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.HttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.IOException
import java.net.URLEncoder
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

/**
 * Free Yahoo Finance access (unofficial, no API key). All network calls run on Dispatchers.IO,
 * use HTTPS only and never throw except [search], which reports "unreachable" with an IOException.
 */
object YahooFinance {
    private const val USER_AGENT =
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/122.0.0.0 Safari/537.36"

    private val cookieStore = ConcurrentHashMap<String, List<Cookie>>()

    private val http: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .cookieJar(object : CookieJar {
                override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
                    cookieStore[url.host] = cookies
                }

                override fun loadForRequest(url: HttpUrl): List<Cookie> = cookieStore[url.host] ?: emptyList()
            })
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(10, TimeUnit.SECONDS)
            .build()
    }

    @Volatile private var warmed = false

    /** Blocking. Visits Yahoo once so the session gets its cookies. */
    private fun warmUp() {
        if (warmed) return
        try {
            val request = Request.Builder()
                .url("https://finance.yahoo.com/quote/AAPL/history/?guccounter=1")
                .header("User-Agent", USER_AGENT)
                .build()
            http.newCall(request).execute().close()
            warmed = true
        } catch (_: Exception) {
        }
    }

    /** Blocking GET. Returns the body or null. */
    private fun get(url: String): String? = try {
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", USER_AGENT)
            .header("Accept", "application/json, text/plain, */*")
            .header("Referer", "https://finance.yahoo.com")
            .build()
        http.newCall(request).execute().use { r -> if (r.isSuccessful) r.body?.string() else null }
    } catch (_: Exception) {
        null
    }

    /** Blocking. Latest price, previous close and currency of one symbol. */
    private fun chart(symbol: String): YahooQuoteInfo? {
        val body = get("https://query1.finance.yahoo.com/v8/finance/chart/$symbol?interval=1d&range=1d")
        if (body.isNullOrBlank()) return null
        return try {
            val meta = JSONObject(body).optJSONObject("chart")
                ?.optJSONArray("result")?.optJSONObject(0)?.optJSONObject("meta") ?: return null
            val price = meta.optDouble("regularMarketPrice", 0.0)
            if (price <= 0.0) return null
            var prev = meta.optDouble("chartPreviousClose", 0.0)
            if (prev <= 0.0) prev = meta.optDouble("previousClose", 0.0)
            YahooQuoteInfo(
                symbol = meta.optString("symbol", symbol),
                name = meta.optString("longName").ifBlank { meta.optString("shortName", symbol) },
                price = price,
                prevClose = prev,
                currency = meta.optString("currency", ""),
                exchange = meta.optString("exchangeName", "")
            )
        } catch (_: Exception) {
            null
        }
    }

    suspend fun quote(symbol: String): YahooQuoteInfo? = withContext(Dispatchers.IO) {
        warmUp()
        chart(symbol)
    }

    /** Several symbols at once (8 requests in parallel). Symbols that fail are left out. */
    suspend fun quotes(symbols: List<String>): Map<String, YahooQuoteInfo> = withContext(Dispatchers.IO) {
        if (symbols.isEmpty()) return@withContext emptyMap<String, YahooQuoteInfo>()
        warmUp()
        val result = LinkedHashMap<String, YahooQuoteInfo>()
        coroutineScope {
            symbols.distinct().chunked(8).forEach { chunk ->
                chunk.map { s -> async { s to chart(s) } }.awaitAll().forEach { (s, q) ->
                    if (q != null) result[s] = q
                }
            }
        }
        result
    }

    /** Search by company / fund name or code. Throws IOException when Yahoo cannot be reached. */
    suspend fun search(query: String): List<YahooSearchHit> = withContext(Dispatchers.IO) {
        val q = query.trim()
        if (q.length < 2) return@withContext emptyList<YahooSearchHit>()
        warmUp()
        val body = get(
            "https://query2.finance.yahoo.com/v1/finance/search?q=${URLEncoder.encode(q, "UTF-8")}" +
                "&quotesCount=12&newsCount=0&listsCount=0&enableFuzzyQuery=false"
        ) ?: throw IOException("Yahoo Finance search is not reachable")
        val allowed = setOf("EQUITY", "ETF", "MUTUALFUND", "CRYPTOCURRENCY")
        val array = try {
            JSONObject(body).optJSONArray("quotes")
        } catch (_: Exception) {
            null
        } ?: return@withContext emptyList<YahooSearchHit>()
        val hits = ArrayList<YahooSearchHit>()
        for (i in 0 until array.length()) {
            val o = array.optJSONObject(i) ?: continue
            val symbol = o.optString("symbol")
            val type = o.optString("quoteType")
            if (symbol.isBlank() || type !in allowed) continue
            hits.add(
                YahooSearchHit(
                    symbol = symbol,
                    name = o.optString("longname").ifBlank { o.optString("shortname", symbol) },
                    exchange = o.optString("exchDisp").ifBlank { o.optString("exchange") },
                    type = type
                )
            )
        }
        hits
    }

    /** Search results together with their live price (used by the Market tab). */
    suspend fun searchWithPrices(query: String, limit: Int = 8): List<MarketQuote> {
        val hits = search(query).take(limit)
        if (hits.isEmpty()) return emptyList()
        val prices = quotes(hits.map { it.symbol })
        return hits.mapNotNull { hit ->
            val info = prices[hit.symbol] ?: return@mapNotNull null
            val change = if (info.prevClose > 0.0) info.price - info.prevClose else 0.0
            val pct = if (info.prevClose > 0.0) change / info.prevClose * 100.0 else 0.0
            MarketQuote(
                symbol = hit.symbol,
                name = hit.name,
                market = if (info.currency == "INR") MARKET_INDIA else MARKET_USA,
                currency = info.currency,
                price = info.price,
                change = change,
                changePct = pct,
                exchange = hit.exchange
            )
        }
    }
}
