package com.example.portfoliopilot

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

@Composable
fun DashboardScreen(vm: PortfolioViewModel) {
    val holdings = vm.holdings
    val stats = remember(holdings) { buildMarketStats(holdings) }
    val activeStats = stats.filter { it.holdings.isNotEmpty() }
    val sales = vm.sales
    val quote = remember { dailyQuote() }
    val greeting = remember { greetingForNow() }
    val fx = if (vm.usdInr > 0.0) vm.usdInr else 1.0
    val ranked = remember(holdings, fx) {
        holdings.sortedByDescending { if (it.currency == "USD") it.value * fx else it.value }.take(5)
    }

    LazyColumn(Modifier.fillMaxSize().padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        item { Spacer(Modifier.height(2.dp)) }

        // Good morning / afternoon / evening + the quote of the day
        item {
            QuoteCard(
                heading = "$greeting, ${vm.userName.ifBlank { "Investor" }}! 👋",
                quote = quote
            )
        }

        item {
            Card(shape = RoundedCornerShape(24.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)) {
                Column(Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Total Portfolio Valuation", style = MaterialTheme.typography.labelLarge)
                    if (activeStats.isEmpty()) {
                        Text("No investments yet", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    }
                    activeStats.forEachIndexed { index, s ->
                        if (index > 0) HorizontalDivider()
                        MarketSummaryRow(s)
                    }
                    if (sales.isNotEmpty()) {
                        HorizontalDivider()
                        MARKETS.forEach { m ->
                            if (sales.any { it.market == m }) {
                                val realized = realizedPnl(sales, m)
                                LabelValueRow(
                                    "Realized profit ${marketLabel(m)}",
                                    fmtSignedMoney(realized, marketCurrency(m)),
                                    if (realized >= 0) PROFIT_GREEN else LOSS_RED
                                )
                            }
                        }
                    }
                    if (vm.holdingsStatus.isNotBlank()) {
                        Text(vm.holdingsStatus, style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
        }

        item {
            Row(Modifier.fillMaxWidth().height(IntrinsicSize.Max), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                MetricCard("Active Holdings", "${holdings.size}", Modifier.weight(1f).fillMaxHeight())
                MetricCard("Purchases", "${vm.purchases.size}", Modifier.weight(1f).fillMaxHeight())
                MetricCard("Sales", "${vm.sales.size}", Modifier.weight(1f).fillMaxHeight())
            }
        }

        item {
            Card(shape = RoundedCornerShape(18.dp)) {
                Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("Asset Allocation", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    if (activeStats.isEmpty()) {
                        Text(
                            "Tap the + button at the top-left to add your first investment.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.secondary
                        )
                    }
                    activeStats.forEach { s ->
                        Text(
                            marketLabel(s.market),
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.padding(top = 6.dp)
                        )
                        val total = s.value.takeIf { it > 0 } ?: 1.0
                        s.holdings.groupBy { it.category }.mapValues { e -> e.value.sumOf { it.value } }.forEach { (category, sum) ->
                            val fraction = (sum / total).toFloat().coerceIn(0f, 1f)
                            Column(Modifier.fillMaxWidth().padding(vertical = 3.dp)) {
                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.Top) {
                                    Text(category, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                                    Text(
                                        "${fmtMoney(sum, s.currency, 0)} (${"%.1f".format(fraction * 100)}%)",
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.Bold,
                                        textAlign = TextAlign.End,
                                        modifier = Modifier.weight(1.2f)
                                    )
                                }
                                Spacer(Modifier.height(4.dp))
                                LinearProgressIndicator(progress = { fraction }, modifier = Modifier.fillMaxWidth().height(8.dp))
                            }
                        }
                    }
                }
            }
        }

        item { Text("Top Holdings", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold) }
        if (holdings.isEmpty()) {
            item {
                Card(shape = RoundedCornerShape(18.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
                    Column(Modifier.padding(20.dp).fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Default.AccountBalanceWallet, null, modifier = Modifier.size(48.dp), tint = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.height(8.dp))
                        Text("Welcome to Investment Avenger!", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center)
                        Text(
                            "Your portfolio is empty. Add your first stock, mutual fund or ETF.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center
                        )
                        Spacer(Modifier.height(12.dp))
                        Button(onClick = { vm.activeHoldingForAction = null; vm.showBuyDialog = true }) {
                            Icon(Icons.Default.Add, null)
                            Spacer(Modifier.width(4.dp))
                            Text("Add Investment")
                        }
                    }
                }
            }
        } else {
            items(ranked, key = { it.id }) { h -> HoldingSummaryCard(h) }
        }


    }
}

@Composable
fun HoldingSummaryCard(holding: Holding) {
    Card(shape = RoundedCornerShape(16.dp)) {
        Row(
            Modifier.fillMaxWidth().padding(14.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.Top
        ) {
            Column(Modifier.weight(1f)) {
                Text(holding.name, fontWeight = FontWeight.Bold)
                Text("${holding.symbol} • ${marketLabel(holding.market)}", style = MaterialTheme.typography.bodySmall)
                Text(
                    "${fmtQty(holding.units)} units • avg ${fmtMoney(holding.averageCost, holding.currency)}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.secondary
                )
            }
            Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(fmtMoney(holding.value, holding.currency), fontWeight = FontWeight.Bold, textAlign = TextAlign.End)
                PnlChip(holding.pnl, holding.pnlPct, holding.currency)
            }
        }
    }
}
