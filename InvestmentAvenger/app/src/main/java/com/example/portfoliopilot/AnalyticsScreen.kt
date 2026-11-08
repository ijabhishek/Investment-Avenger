package com.example.portfoliopilot

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

private val ChartColors = listOf(
    Color(0xFF2E7D32), Color(0xFF1976D2), Color(0xFFF57C00), Color(0xFF7B1FA2), Color(0xFFE91E63),
    Color(0xFF00897B), Color(0xFF6D4C41), Color(0xFF546E7A)
)

@Composable
fun AnalyticsScreen(vm: PortfolioViewModel) {
    val holdings = vm.holdings
    val stats = remember(holdings) { buildMarketStats(holdings).filter { it.holdings.isNotEmpty() } }
    val best = remember(holdings) { holdings.maxByOrNull { it.pnlPct } }
    val worst = remember(holdings) { holdings.minByOrNull { it.pnlPct } }
    val movers = remember(holdings) { holdings.sortedByDescending { Math.abs(it.pnlPct) }.take(8) }

    LazyColumn(Modifier.fillMaxSize().padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        item {
            Spacer(Modifier.height(4.dp))
            Text("Analytics", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Text(
                "Profit, loss and allocation of every market at a glance",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.secondary
            )
        }

        if (holdings.isEmpty() && vm.purchases.isEmpty()) {
            item {
                Card(shape = RoundedCornerShape(18.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
                    Column(Modifier.padding(24.dp).fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("📊 No analytics yet", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                        Text(
                            "Add investments and record trades to see charts and performance here.",
                            textAlign = TextAlign.Center,
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
            }
            return@LazyColumn
        }

        // Profit / loss per market
        item {
            Card(shape = RoundedCornerShape(22.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)) {
                Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Profit & loss snapshot", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    stats.forEachIndexed { index, s ->
                        if (index > 0) HorizontalDivider()
                        MarketSummaryRow(s)
                    }
                    if (vm.sales.isNotEmpty()) {
                        HorizontalDivider()
                        MARKETS.forEach { m ->
                            if (vm.sales.any { it.market == m }) {
                                val r = realizedPnl(vm.sales, m)
                                LabelValueRow("Realized ${marketLabel(m)}", fmtSignedMoney(r, marketCurrency(m)), if (r >= 0) PROFIT_GREEN else LOSS_RED)
                            }
                        }
                    }
                }
            }
        }

        // Best and worst holding
        if (best != null && worst != null && holdings.size > 1) {
            item {
                Row(Modifier.fillMaxWidth().height(IntrinsicSize.Max), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    PerformerCard("🏆 Best performer", best, Modifier.weight(1f).fillMaxHeight())
                    PerformerCard("⚠️ Needs attention", worst, Modifier.weight(1f).fillMaxHeight())
                }
            }
        }

        // Return % of each holding
        if (movers.isNotEmpty()) {
            item {
                Card(shape = RoundedCornerShape(18.dp)) {
                    Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text("Return by holding", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        val maxPct = movers.maxOf { Math.abs(it.pnlPct) }.takeIf { it > 0 } ?: 1.0
                        movers.forEach { h ->
                            val color = if (h.pnl >= 0) PROFIT_GREEN else LOSS_RED
                            Column(Modifier.fillMaxWidth()) {
                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Text(h.name, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                                    Text(fmtPct(h.pnlPct), color = color, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyMedium)
                                }
                                Spacer(Modifier.height(3.dp))
                                LinearProgressIndicator(
                                    progress = { (Math.abs(h.pnlPct) / maxPct).toFloat().coerceIn(0.02f, 1f) },
                                    modifier = Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(50)),
                                    color = color,
                                    trackColor = color.copy(alpha = 0.15f)
                                )
                            }
                        }
                    }
                }
            }
        }

        // One donut per market (India, USA, Global) in that market's own currency
        stats.forEach { s ->
            item(key = "donut-${s.market}") { MarketBreakdownCard(s) }
        }
        stats.forEach { s ->
            item(key = "capital-${s.market}") { CapitalVsValueCard(s) }
        }

        // Win ratio
        if (holdings.isNotEmpty()) {
            item {
                Card(shape = RoundedCornerShape(18.dp)) {
                    Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text("Portfolio health", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        val winning = holdings.count { it.pnl >= 0 }
                        val losing = holdings.size - winning
                        val winRate = winning.toFloat() / holdings.size
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text("In profit: $winning", color = PROFIT_GREEN, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                            Text("In loss: $losing", color = LOSS_RED, fontWeight = FontWeight.Bold, textAlign = TextAlign.End, modifier = Modifier.weight(1f))
                        }
                        LinearProgressIndicator(
                            progress = { winRate },
                            modifier = Modifier.fillMaxWidth().height(10.dp).clip(RoundedCornerShape(50)),
                            color = PROFIT_GREEN,
                            trackColor = if (losing > 0) LOSS_RED else PROFIT_GREEN
                        )
                        Text("Win rate ${"%.0f".format(winRate * 100)}%  •  ${vm.purchases.size} buys  •  ${vm.sales.size} sells", style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }

        // Recent sales
        if (vm.sales.isNotEmpty()) {
            item {
                Card(shape = RoundedCornerShape(18.dp)) {
                    Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Recent sales", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        vm.sales.take(5).forEach { s ->
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                                Column(Modifier.weight(1f)) {
                                    Text(s.name, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    Text(s.date, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.secondary)
                                }
                                PnlChip(s.realizedPnl, s.realizedPnlPct, s.currency)
                            }
                        }
                    }
                }
            }
        }
        item { Spacer(Modifier.height(16.dp)) }
    }
}

@Composable
private fun PerformerCard(title: String, holding: Holding, modifier: Modifier) {
    Card(modifier, shape = RoundedCornerShape(16.dp)) {
        Column(Modifier.fillMaxSize().padding(12.dp), verticalArrangement = Arrangement.SpaceBetween) {
            Column {
                Text(title, style = MaterialTheme.typography.labelMedium)
                Text(holding.name, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
            Spacer(Modifier.height(6.dp))
            Text(
                fmtPct(holding.pnlPct),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = if (holding.pnl >= 0) PROFIT_GREEN else LOSS_RED
            )
        }
    }
}

/** Donut chart + legend for ONE market, in that market's own currency. */
@Composable
fun MarketBreakdownCard(stats: MarketStats) {
    Card(shape = RoundedCornerShape(18.dp)) {
        Column(Modifier.fillMaxWidth().padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text("${marketLabel(stats.market)} breakdown", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
            Spacer(Modifier.height(12.dp))
            val categories = stats.holdings.groupBy { it.category }.mapValues { e -> e.value.sumOf { it.value } }.entries.toList()
            val total = stats.value.takeIf { it > 0 } ?: 1.0

            Box(modifier = Modifier.size(200.dp), contentAlignment = Alignment.Center) {
                Canvas(modifier = Modifier.fillMaxSize().padding(12.dp)) {
                    var start = -90f
                    categories.forEachIndexed { index, entry ->
                        val sweep = (entry.value / total).toFloat() * 360f
                        drawArc(
                            color = ChartColors[index % ChartColors.size],
                            startAngle = start,
                            sweepAngle = sweep,
                            useCenter = false,
                            style = Stroke(width = 44f)
                        )
                        start += sweep
                    }
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(horizontal = 40.dp)) {
                    Text("Total value", style = MaterialTheme.typography.labelSmall)
                    Text(
                        fmtMoney(stats.value, stats.currency, 0),
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            Spacer(Modifier.height(16.dp))
            Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                categories.forEachIndexed { index, entry ->
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.Top) {
                        Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.Top) {
                            Box(Modifier.padding(top = 4.dp).size(12.dp).clip(CircleShape).background(ChartColors[index % ChartColors.size]))
                            Text(entry.key, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                        }
                        Text(
                            "${fmtMoney(entry.value, stats.currency)} (${"%.1f".format(entry.value / total * 100)}%)",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.End,
                            modifier = Modifier.weight(1.3f)
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun CapitalVsValueCard(stats: MarketStats) {
    val maxValue = maxOf(stats.invested, stats.value).takeIf { it > 0 } ?: 1.0
    Card(shape = RoundedCornerShape(18.dp)) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("${marketLabel(stats.market)} • capital vs value", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            LabelValueRow("Capital invested", fmtMoney(stats.invested, stats.currency))
            LinearProgressIndicator(
                progress = { (stats.invested / maxValue).toFloat().coerceIn(0f, 1f) },
                modifier = Modifier.fillMaxWidth().height(12.dp).clip(RoundedCornerShape(50)),
                color = Color(0xFF1976D2)
            )
            LabelValueRow("Current value", fmtMoney(stats.value, stats.currency))
            LinearProgressIndicator(
                progress = { (stats.value / maxValue).toFloat().coerceIn(0f, 1f) },
                modifier = Modifier.fillMaxWidth().height(12.dp).clip(RoundedCornerShape(50)),
                color = Color(0xFF2E7D32)
            )
            LabelValueRow("Profit / loss", fmtSignedMoney(stats.pnl, stats.currency), if (stats.pnl >= 0) PROFIT_GREEN else LOSS_RED)
        }
    }
}
