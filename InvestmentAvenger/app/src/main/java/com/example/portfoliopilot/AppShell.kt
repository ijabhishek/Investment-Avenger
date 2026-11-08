package com.example.portfoliopilot

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.widget.ImageView
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Analytics
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Storefront
import androidx.compose.material3.Button
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity

private val SplashNavy = Color(0xFF03162E)

/** The app's own start-up screen (5 seconds): animated logo, name and the quote of the day. */
@Composable
fun SplashScreen() {
    val quote = remember { dailyQuote() }
    Box(Modifier.fillMaxSize().background(SplashNavy), contentAlignment = Alignment.Center) {
        Column(
            modifier = Modifier.fillMaxWidth().systemBarsPadding().padding(horizontal = 28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically)
        ) {
            AndroidView(
                factory = { ctx ->
                    ImageView(ctx).apply {
                        scaleType = ImageView.ScaleType.CENTER_INSIDE
                        setImageResource(R.drawable.investmentgif)
                        (drawable as? android.graphics.drawable.Animatable)?.start()
                    }
                },
                modifier = Modifier.size(180.dp)
            )
            Text(
                "INVESTMENT AVENGER",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = Color.White,
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(8.dp))
            Text(
                "\"${quote.first}\"",
                style = MaterialTheme.typography.bodyMedium,
                fontStyle = FontStyle.Italic,
                color = Color(0xFFE3EBF5),
                textAlign = TextAlign.Center
            )
            Text(
                "— ${quote.second}",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF8FD9A8),
                textAlign = TextAlign.Center
            )
        }
    }
}

/** Shown while the app lock is active. */
@Composable
fun LockScreen(activity: FragmentActivity, vm: PortfolioViewModel) {
    var message by remember { mutableStateOf("") }
    val unlock: () -> Unit = {
        if (!AppLock.isAvailable(activity)) {
            // The phone's screen lock was removed: do not lock the user out of their own data.
            vm.unlock()
        } else {
            AppLock.prompt(activity, "Unlock Investment Avenger", onSuccess = { vm.unlock() }, onError = { message = it })
        }
    }
    LaunchedEffect(Unit) { unlock() }

    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).systemBarsPadding(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(14.dp), modifier = Modifier.padding(32.dp)) {
            Icon(Icons.Default.Lock, null, Modifier.size(64.dp), tint = MaterialTheme.colorScheme.primary)
            Text("Investment Avenger is locked", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
            if (message.isNotBlank()) Text(message, style = MaterialTheme.typography.bodySmall, textAlign = TextAlign.Center)
            Button(onClick = unlock) { Text("Unlock") }
        }
    }
}

/** Asks once (Android 13+) for permission to show the daily quote notification. */
@Composable
private fun NotificationPermissionPrompt(vm: PortfolioViewModel) {
    val context = LocalContext.current
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
    LaunchedEffect(vm.notificationsEnabled, vm.notifAsked) {
        if (Build.VERSION.SDK_INT >= 33 && vm.notificationsEnabled && !vm.notifAsked &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            vm.markNotifAsked()
            launcher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }
}

@Composable
fun PortfolioApp(vm: PortfolioViewModel, activity: FragmentActivity) {
    if (vm.showSplash) {
        SplashScreen()
        return
    }
    if (vm.appLockEnabled && vm.locked) {
        LockScreen(activity, vm)
        return
    }
    NotificationPermissionPrompt(vm)

    if (vm.showNotepad) {
        NotepadScreen(vm)
        return
    }

    val isProfileTab = vm.selectedTab == 4
    val density = LocalDensity.current

    Scaffold(
        topBar = {
            AppTopBar(
                title = when (vm.selectedTab) {
                    0 -> "Dashboard"
                    1 -> "Portfolio"
                    2 -> "Live Markets"
                    3 -> "Analytics"
                    else -> vm.userName.ifBlank { "My Profile" }
                },
                userName = vm.userName,
                profilePhoto = vm.profilePhoto,
                showAdd = !isProfileTab,
                onProfileClick = { vm.showProfileDialog = true },
                onAdd = {
                    vm.activeHoldingForAction = null
                    vm.showBuyDialog = true
                }
            )
        },
        bottomBar = {
            // Keep the bottom labels on one line even when the phone's font size is large.
            CompositionLocalProvider(LocalDensity provides Density(density.density, minOf(density.fontScale, 1.15f))) {
                NavigationBar {
                    val items = listOf(
                        Triple("Dashboard", Icons.Default.Dashboard, 0),
                        Triple("Portfolio", Icons.Default.AccountBalanceWallet, 1),
                        Triple("Market", Icons.Default.Storefront, 2),
                        Triple("Analytics", Icons.Default.Analytics, 3),
                        Triple("Profile", Icons.Default.Person, 4)
                    )
                    items.forEach { (label, icon, index) ->
                        NavigationBarItem(
                            selected = vm.selectedTab == index,
                            onClick = { vm.selectedTab = index },
                            icon = { Icon(icon, null) },
                            label = { Text(label, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.labelSmall) }
                        )
                    }
                }
            }
        }
    ) { padding ->
        Box(Modifier.padding(padding)) {
            when (vm.selectedTab) {
                0 -> DashboardScreen(vm)
                1 -> PortfolioScreen(vm)
                2 -> MarketScreen(vm)
                3 -> AnalyticsScreen(vm)
                else -> ProfileScreen(vm)
            }
        }
    }

    if (vm.showBuyDialog) {
        BuyInvestmentDialog(
            initialHolding = vm.activeHoldingForAction,
            onSearch = { query -> vm.searchInvestments(query) },
            onFetchQuote = { symbol -> vm.fetchInvestmentQuote(symbol) },
            onLiveQuoteFor = { holding -> vm.liveQuoteFor(holding) },
            onDismiss = { vm.showBuyDialog = false; vm.activeHoldingForAction = null },
            onConfirm = { r ->
                vm.executeBuy(r.name, r.symbol, r.market, r.category, r.currency, r.units, r.price, r.charges, r.notes, r.yahooSymbol, r.livePrice)
            }
        )
    }

    if (vm.showOnboarding) {
        ProfileDialog(initialName = "", initialPhoto = "", onComplete = { name, photo -> vm.updateProfile(name, photo) })
    }
    if (vm.showProfileDialog) {
        ProfileDialog(
            initialName = vm.userName,
            initialPhoto = vm.profilePhoto,
            onComplete = { name, photo -> vm.updateProfile(name, photo) },
            onDismiss = { vm.showProfileDialog = false }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AppTopBar(
    title: String,
    userName: String,
    profilePhoto: String,
    showAdd: Boolean,
    onProfileClick: () -> Unit,
    onAdd: () -> Unit
) {
    CenterAlignedTopAppBar(
        title = { Text(title, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        navigationIcon = {
            // The only "add investment" button: top-left, a little bigger than a normal icon.
            if (showAdd) {
                IconButton(onClick = onAdd, modifier = Modifier.padding(start = 4.dp).size(48.dp)) {
                    Icon(Icons.Default.Add, "Add investment", modifier = Modifier.size(32.dp), tint = MaterialTheme.colorScheme.primary)
                }
            }
        },
        actions = {
            ProfileAvatar(
                photo = profilePhoto,
                name = userName,
                size = 40.dp,
                modifier = Modifier.padding(end = 12.dp).clickable(onClick = onProfileClick)
            )
        },
        colors = TopAppBarDefaults.centerAlignedTopAppBarColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    )
}
