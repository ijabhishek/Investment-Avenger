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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AddShoppingCart
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Sell
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun PortfolioScreen(vm: PortfolioViewModel) {
    var sellTarget by remember { mutableStateOf<Holding?>(null) }
    var priceEditTarget by remember { mutableStateOf<Holding?>(null) }
    var portfolioTab by remember { mutableIntStateOf(0) }
    var marketFilter by remember { mutableStateOf("All") }

    val visibleHoldings = if (marketFilter == "All") vm.holdings else vm.holdings.filter { it.market == marketFilter }
    val notesByHolding = remember(vm.purchases) {
        vm.purchases.filter { it.notes.isNotBlank() }.groupBy { it.holdingId }
    }

    LazyColumn(Modifier.fillMaxSize().padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item {
            Spacer(Modifier.height(4.dp))
            Text("Your Investments", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(8.dp))
            TabRow(selectedTabIndex = portfolioTab) {
                Tab(selected = portfolioTab == 0, onClick = { portfolioTab = 0 }, text = { Text("Holdings (${vm.holdings.size})") })
                Tab(selected = portfolioTab == 1, onClick = { portfolioTab = 1 }, text = { Text("History (${vm.purchases.size + vm.sales.size})") })
            }
            if (portfolioTab == 0 && vm.holdings.isNotEmpty()) {
                Spacer(Modifier.height(8.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    listOf("All").plus(MARKETS).forEach { m ->
                        FilterChip(selected = marketFilter == m, onClick = { marketFilter = m }, label = { Text(if (m == "All") "All" else marketLabel(m)) })
                    }
                }
            }
        }

        if (portfolioTab == 1) {
            item {
                Text("Purchase history", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text("Trade records stay here even after you sell an investment.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.secondary)
            }
            if (vm.purchases.isEmpty()) {
                item { Text("No purchases recorded yet.", color = MaterialTheme.colorScheme.secondary) }
            } else {
                items(vm.purchases, key = { "purchase-${it.id}" }) { p ->
                    Card(shape = RoundedCornerShape(16.dp)) {
                        Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.AddShoppingCart, null, tint = PROFIT_GREEN, modifier = Modifier.size(28.dp))
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text("${p.name} (${p.symbol})", fontWeight = FontWeight.Bold)
                                Text("Bought ${fmtQty(p.units)} units @ ${fmtMoney(p.price, p.currency)}", style = MaterialTheme.typography.bodySmall)
                                Text("${p.date} • ${p.time}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.secondary)
                                if (p.notes.isNotBlank()) Text("📝 ${p.notes}", style = MaterialTheme.typography.bodySmall)
                            }
                            Spacer(Modifier.width(8.dp))
                            Text(fmtMoney(p.totalCost, p.currency), fontWeight = FontWeight.Bold, textAlign = TextAlign.End)
                        }
                    }
                }
            }
            item {
                Spacer(Modifier.height(4.dp))
                Text("Sale history", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            }
            if (vm.sales.isEmpty()) {
                item { Text("No sales recorded yet.", color = MaterialTheme.colorScheme.secondary) }
            } else {
                items(vm.sales, key = { "sale-${it.id}" }) { sale ->
                    Card(shape = RoundedCornerShape(16.dp)) {
                        Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Sell, null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(28.dp))
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text("${sale.name} (${sale.symbol})", fontWeight = FontWeight.Bold)
                                Text("Sold ${fmtQty(sale.unitsSold)} units @ ${fmtMoney(sale.sellPrice, sale.currency)}", style = MaterialTheme.typography.bodySmall)
                                Text("Average cost was ${fmtMoney(sale.avgCostBasis, sale.currency)}", style = MaterialTheme.typography.labelSmall)
                                Text("${sale.date} • ${sale.time}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.secondary)
                                if (sale.notes.isNotBlank()) Text("📝 ${sale.notes}", style = MaterialTheme.typography.bodySmall)
                            }
                            Spacer(Modifier.width(8.dp))
                            Column(horizontalAlignment = Alignment.End) {
                                Text(fmtMoney(sale.netAmount, sale.currency), fontWeight = FontWeight.Bold)
                                Text(
                                    "P/L ${fmtSignedMoney(sale.realizedPnl, sale.currency)}",
                                    color = if (sale.realizedPnl >= 0) PROFIT_GREEN else LOSS_RED,
                                    style = MaterialTheme.typography.labelSmall
                                )
                            }
                        }
                    }
                }
            }
        } else if (vm.holdings.isEmpty()) {
            item {
                Card(shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
                    Column(Modifier.padding(24.dp).fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("No active holdings found.", fontWeight = FontWeight.Bold)
                        Text("Add your investments to start tracking value and returns.", textAlign = TextAlign.Center)
                        Spacer(Modifier.height(12.dp))
                        Button(onClick = { vm.activeHoldingForAction = null; vm.showBuyDialog = true }) { Text("Add First Investment") }
                    }
                }
            }
        } else {
            items(visibleHoldings, key = { it.id }) { holding ->
                val holdingNotes = notesByHolding[holding.id].orEmpty()
                val linked = holding.yahooSymbol.isNotBlank()
                Card(shape = RoundedCornerShape(18.dp)) {
                    Column(Modifier.fillMaxWidth().padding(16.dp)) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.Top) {
                            Column(Modifier.weight(1f)) {
                                Text(holding.name, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                                Text("${holding.symbol} • ${marketLabel(holding.market)} • ${holding.category}", style = MaterialTheme.typography.bodySmall)
                                Text(
                                    if (linked) "Live price from Yahoo Finance" else "Manual price",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                            PnlChip(holding.pnl, holding.pnlPct, holding.currency)
                        }
                        Spacer(Modifier.height(10.dp))
                        LabelValueRow("Units", fmtQty(holding.units))
                        LabelValueRow("Average cost", fmtMoney(holding.averageCost, holding.currency))
                        LabelValueRow("Invested", fmtMoney(holding.invested, holding.currency))
                        LabelValueRow("Current market price", fmtMoney(holding.currentPrice, holding.currency))
                        LabelValueRow("Current value", fmtMoney(holding.value, holding.currency))
                        if (holdingNotes.isNotEmpty()) {
                            Spacer(Modifier.height(8.dp))
                            Text("📝 Notes", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                            holdingNotes.take(5).forEach { n -> Text("${n.date}: ${n.notes}", style = MaterialTheme.typography.bodySmall) }
                            if (holdingNotes.size > 5) {
                                Text("+ ${holdingNotes.size - 5} more in History", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.secondary)
                            }
                        }
                        Spacer(Modifier.height(8.dp))
                        // FlowRow: buttons wrap to a second line instead of overflowing when the font is large.
                        FlowRow(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp, Alignment.End)) {
                            if (!linked) {
                                TextButton(onClick = { priceEditTarget = holding }) {
                                    Icon(Icons.Default.Edit, null, Modifier.size(16.dp))
                                    Spacer(Modifier.width(4.dp))
                                    Text("Update Price", maxLines = 1)
                                }
                            }
                            TextButton(onClick = { vm.activeHoldingForAction = holding; vm.showBuyDialog = true }) {
                                Icon(Icons.Default.Add, null, Modifier.size(16.dp))
                                Spacer(Modifier.width(4.dp))
                                Text("Buy More", maxLines = 1)
                            }
                            TextButton(onClick = { sellTarget = holding }) {
                                Icon(Icons.Default.Sell, null, Modifier.size(16.dp))
                                Spacer(Modifier.width(4.dp))
                                Text("Sell", color = MaterialTheme.colorScheme.error, maxLines = 1)
                            }
                        }
                    }
                }
            }
        }
        item { Spacer(Modifier.height(16.dp)) }
    }

    sellTarget?.let { target ->
        SellInvestmentDialog(
            holding = target,
            onDismiss = { sellTarget = null },
            onConfirmSell = { unitsSold, sellPrice, charges, notes ->
                vm.executeSell(target, unitsSold, sellPrice, charges, notes)
                sellTarget = null
            }
        )
    }

    priceEditTarget?.let { target ->
        UpdatePriceDialog(
            holding = target,
            onDismiss = { priceEditTarget = null },
            onConfirm = { newPrice ->
                vm.updateCurrentPrice(target.id, newPrice)
                priceEditTarget = null
            }
        )
    }
}
