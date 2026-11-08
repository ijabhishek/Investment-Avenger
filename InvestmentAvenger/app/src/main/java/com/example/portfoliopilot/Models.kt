package com.example.portfoliopilot

import androidx.compose.ui.graphics.Color
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.util.Calendar
import java.util.Locale
import java.util.UUID

/** Lenient JSON: data saved by older versions of the app (extra/missing fields) still loads. */
val AppJson = Json {
    ignoreUnknownKeys = true
    encodeDefaults = true
}

// ---------------------------------------------------------------------------
// Markets
// ---------------------------------------------------------------------------
const val MARKET_INDIA = "India"
const val MARKET_USA = "USA"
const val MARKET_GLOBAL = "Global"
val MARKETS = listOf(MARKET_INDIA, MARKET_USA, MARKET_GLOBAL)

/** India is held in INR; USA and Global assets are held in USD. */
fun marketCurrency(market: String): String = if (market == MARKET_INDIA) "INR" else "USD"

fun marketLabel(market: String): String = when (market) {
    MARKET_INDIA -> "🇮🇳 India"
    MARKET_USA -> "🇺🇸 USA"
    else -> "🌍 Global"
}

// ---------------------------------------------------------------------------
// Formatting helpers (one currency symbol only, e.g. ₹1,234.50 or $1,234.50)
// ---------------------------------------------------------------------------
fun currencySymbol(currency: String): String = if (currency.equals("INR", ignoreCase = true)) "₹" else "$"

fun fmtMoney(amount: Double, currency: String, decimals: Int = 2): String {
    val sign = if (amount < 0 && Math.round(Math.abs(amount) * Math.pow(10.0, decimals.toDouble())) > 0) "-" else ""
    return sign + currencySymbol(currency) + String.format(Locale.US, "%,.${decimals}f", Math.abs(amount))
}

fun fmtSignedMoney(amount: Double, currency: String, decimals: Int = 2): String {
    val text = fmtMoney(amount, currency, decimals)
    return if (text.startsWith("-")) text else "+$text"
}

fun fmtPct(value: Double): String = String.format(Locale.US, "%+.2f%%", value)

/** 10.0 -> "10", 2.5 -> "2.5", 0.123456 -> "0.1235" */
fun fmtQty(units: Double): String {
    if (units == Math.floor(units) && Math.abs(units) < 1e12) return units.toLong().toString()
    return String.format(Locale.US, "%.4f", units).trimEnd('0').trimEnd('.')
}

/** Price text that can be put back into an input field. */
fun fmtPriceInput(price: Double): String = fmtQty(Math.round(price * 10000.0) / 10000.0)

val PROFIT_GREEN = Color(0xFF2E9D69)
val LOSS_RED = Color(0xFFD32F2F)

fun greetingForNow(): String {
    val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
    return when {
        hour < 12 -> "Good morning"
        hour < 17 -> "Good afternoon"
        else -> "Good evening"
    }
}

// ---------------------------------------------------------------------------
// Portfolio data
// ---------------------------------------------------------------------------
@Serializable
data class Holding(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val symbol: String,
    val market: String,
    val category: String,
    val currency: String,
    val units: Double,
    /** Weighted average purchase price of every unit still held. */
    val averageCost: Double,
    /** Latest market price. Live for Yahoo-linked holdings, manual otherwise. */
    val currentPrice: Double,
    /** Full Yahoo Finance symbol (e.g. RELIANCE.NS). Blank = price is updated by hand. */
    val yahooSymbol: String = ""
) {
    val invested: Double get() = units * averageCost
    val value: Double get() = units * currentPrice
    val pnl: Double get() = value - invested
    val pnlPct: Double get() = if (invested == 0.0) 0.0 else pnl / invested * 100.0
}

@Serializable
data class PurchaseTransaction(
    val id: String = UUID.randomUUID().toString(),
    val holdingId: String,
    val name: String,
    val symbol: String,
    val market: String,
    val category: String,
    val currency: String,
    val units: Double,
    val price: Double,
    val charges: Double,
    val totalCost: Double,
    val date: String,
    val time: String,
    val notes: String = ""
)

@Serializable
data class SaleTransaction(
    val id: String = UUID.randomUUID().toString(),
    val holdingId: String,
    val name: String,
    val symbol: String,
    val market: String,
    val category: String,
    val currency: String,
    val unitsSold: Double,
    val avgCostBasis: Double,
    val sellPrice: Double,
    val grossAmount: Double,
    val charges: Double,
    val netAmount: Double,
    val realizedPnl: Double,
    val realizedPnlPct: Double,
    val date: String,
    val time: String,
    val notes: String = ""
)

/** One note in the notepad. Bold is stored as **text** and italic as __text__. */
@Serializable
data class Note(
    val id: String = UUID.randomUUID().toString(),
    val title: String,
    val content: String,
    val timestamp: String,
    val updatedAt: Long = 0L
)

/** Totals for one market, always in that market's own currency. */
data class MarketStats(val market: String, val holdings: List<Holding>) {
    val currency: String = marketCurrency(market)
    val invested: Double = holdings.sumOf { it.invested }
    val value: Double = holdings.sumOf { it.value }
    val pnl: Double = value - invested
    val pnlPct: Double = if (invested == 0.0) 0.0 else pnl / invested * 100.0
}

fun buildMarketStats(holdings: List<Holding>): List<MarketStats> =
    MARKETS.map { m -> MarketStats(m, holdings.filter { it.market == m }) }

fun realizedPnl(sales: List<SaleTransaction>, market: String): Double =
    sales.filter { it.market == market }.sumOf { it.realizedPnl }

// ---------------------------------------------------------------------------
// Market data (not saved)
// ---------------------------------------------------------------------------
data class MarketQuote(
    val symbol: String,
    val name: String,
    val market: String,
    val currency: String,
    val price: Double,
    val change: Double,
    val changePct: Double,
    val exchange: String = ""
)

data class YahooSearchHit(val symbol: String, val name: String, val exchange: String, val type: String)

data class YahooQuoteInfo(
    val symbol: String,
    val name: String,
    val price: Double,
    val prevClose: Double,
    val currency: String,
    val exchange: String
)

// ---------------------------------------------------------------------------
// Notes: **bold** and __italic__ markup helpers
// ---------------------------------------------------------------------------
fun Note.plainContent(): String = content.replace("**", "").replace("__", "")
