package com.example.portfoliopilot

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.ShowChart
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay

private fun priceText(q: MarketQuote): String =
    if (q.currency == "INR" || q.currency == "USD") fmtMoney(q.price, q.currency)
    else "${q.currency} ${fmtQty(Math.round(q.price * 100.0) / 100.0)}"

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun MarketScreen(vm: PortfolioViewModel) {
    // Quotes are only downloaded when this tab is opened (keeps the app fast and light on data).
    LaunchedEffect(Unit) { vm.ensureMarketLoaded() }

    var marketTab by remember { mutableStateOf("All") }
    var query by remember { mutableStateOf("") }
    var results by remember { mutableStateOf<List<MarketQuote>>(emptyList()) }
    var searching by remember { mutableStateOf(false) }
    var searchMessage by remember { mutableStateOf<String?>(null) }

    // Search Yahoo Finance as the user types (after a short pause).
    LaunchedEffect(query) {
        val q = query.trim()
        if (q.length < 2) {
            results = emptyList()
            searchMessage = null
            searching = false
            return@LaunchedEffect
        }
        delay(450)
        searching = true
        searchMessage = null
        try {
            results = vm.searchMarket(q)
            if (results.isEmpty()) searchMessage = "No company found on Yahoo Finance for \"$q\"."
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            results = emptyList()
            searchMessage = "Could not reach Yahoo Finance. Check your internet connection."
        }
        searching = false
    }

    val watchlist = vm.marketQuotes
        .filter { marketTab == "All" || it.market == marketTab }
        .filter { query.isBlank() || it.name.contains(query, ignoreCase = true) || it.symbol.contains(query, ignoreCase = true) }

    Column(Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        Spacer(Modifier.height(4.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Live Markets", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Text(vm.quoteStatus, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.secondary)
            }
            IconButton(onClick = { vm.fetchLiveMarketQuotes() }) { Icon(Icons.Default.Refresh, "Refresh market") }
        }
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            placeholder = { Text("Search any company, fund or index…") },
            leadingIcon = { Icon(Icons.Default.Search, null) },
            singleLine = true,
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(6.dp))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            listOf("All", MARKET_INDIA, MARKET_USA).forEach { tab ->
                FilterChip(selected = marketTab == tab, onClick = { marketTab = tab }, label = { Text(tab) })
            }
        }
        Spacer(Modifier.height(8.dp))

        LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxSize()) {
            if (query.trim().length >= 2) {
                item {
                    Text("From Yahoo Finance", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                    if (searching) LinearProgressIndicator(Modifier.fillMaxWidth().padding(top = 6.dp))
                    searchMessage?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.secondary) }
                }
                items(results, key = { "search-${it.symbol}" }) { q -> QuoteRow(q) }
                if (watchlist.isNotEmpty()) {
                    item { Text("In the watchlist", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold) }
                }
            }

            if (vm.marketQuotes.isEmpty() && query.isBlank()) {
                item {
                    Column(
                        Modifier.fillMaxWidth().padding(top = 36.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Icon(Icons.Default.ShowChart, null, modifier = Modifier.size(48.dp), tint = MaterialTheme.colorScheme.primary)
                        Text("Live quotes are not available yet", fontWeight = FontWeight.SemiBold)
                        Text(vm.quoteStatus, style = MaterialTheme.typography.bodySmall, textAlign = TextAlign.Center)
                        Button(onClick = { vm.fetchLiveMarketQuotes() }) { Text("Try again") }
                    }
                }
            } else {
                items(watchlist, key = { "watch-${it.symbol}" }) { q -> QuoteRow(q) }
            }
            item {
                Text(
                    "Free Yahoo Finance data. Quotes can be delayed – not for trade execution.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.secondary,
                    modifier = Modifier.padding(vertical = 8.dp)
                )
            }
        }
    }
}

@Composable
private fun QuoteRow(q: MarketQuote) {
    Card(shape = RoundedCornerShape(16.dp)) {
        Row(
            Modifier.fillMaxWidth().padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Text(q.name, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text(
                    listOf(q.symbol, q.exchange.ifBlank { marketLabel(q.market) }).joinToString(" • "),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.secondary
                )
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(priceText(q), fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.End)
                val up = q.change >= 0
                Text(
                    "${if (up) "+" else ""}${"%.2f".format(q.change)} (${fmtPct(q.changePct)})",
                    color = if (up) PROFIT_GREEN else LOSS_RED,
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.bodySmall,
                    textAlign = TextAlign.End
                )
            }
        }
    }
}
