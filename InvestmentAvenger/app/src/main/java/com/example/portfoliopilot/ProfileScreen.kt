package com.example.portfoliopilot

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.EditNote
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.TextFields
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider

private val NOTIFY_HOURS = listOf(7 to "7 AM", 9 to "9 AM", 12 to "12 PM", 18 to "6 PM", 21 to "9 PM")

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ProfileScreen(vm: PortfolioViewModel) {
    val context = LocalContext.current
    var quote by remember { mutableStateOf(dailyQuote()) }

    val notificationPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (!granted) {
            Toast.makeText(context, "Allow notifications in phone Settings to receive the daily quote.", Toast.LENGTH_LONG).show()
        }
    }

    fun requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    LazyColumn(Modifier.fillMaxSize().padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        item { Spacer(Modifier.height(2.dp)) }

        // Who you are
        item {
            Card(shape = RoundedCornerShape(20.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh)) {
                Row(
                    Modifier.fillMaxWidth().padding(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    ProfileAvatar(vm.profilePhoto, vm.userName, 56.dp)
                    Column(Modifier.weight(1f)) {
                        Text("${greetingForNow()},", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.secondary)
                        Text(vm.userName.ifBlank { "Investor" }, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    }
                    TextButton(onClick = { vm.showProfileDialog = true }) { Text("Edit") }
                }
            }
        }

        // Quote with copy / share
        item {
            QuoteCard(
                heading = "💡 Financial wisdom",
                quote = quote,
                onNewQuote = { quote = financeQuotes.random() }
            )
        }

        // Notepad
        item {
            SectionCard("My Notepad", Icons.Default.EditNote) {
                Text(
                    "Write notes with a title, make text bold or italic, copy and share them. ${vm.savedNotes.size} saved.",
                    style = MaterialTheme.typography.bodySmall
                )
                Button(onClick = { vm.showNotepad = true }) {
                    Icon(Icons.Default.EditNote, null)
                    Spacer(Modifier.width(8.dp))
                    Text("Open notepad")
                }
            }
        }

        // Share report: text + CSV in one place
        item {
            SectionCard("Share Report", Icons.Default.Share) {
                Text(
                    "Send your holdings, purchases and sales through WhatsApp, e-mail or any app.",
                    style = MaterialTheme.typography.bodySmall
                )
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = {
                        shareText(context, vm.generateTextReport(), "My Portfolio Report - Investment Avenger", "Share portfolio report")
                    }) {
                        Icon(Icons.Default.Description, null)
                        Spacer(Modifier.width(8.dp))
                        Text("Share text report")
                    }
                    OutlinedButton(onClick = {
                        val file = vm.exportCsvFile(context)
                        if (file != null) {
                            val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
                            val intent = Intent(Intent.ACTION_SEND).apply {
                                type = "text/csv"
                                putExtra(Intent.EXTRA_STREAM, uri)
                                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                            }
                            context.startActivity(Intent.createChooser(intent, "Export portfolio report"))
                        } else {
                            Toast.makeText(context, "Could not create the report file", Toast.LENGTH_SHORT).show()
                        }
                    }) {
                        Icon(Icons.Default.Download, null)
                        Spacer(Modifier.width(8.dp))
                        Text("Export portfolio (.csv)")
                    }
                }
            }
        }

        // Look and feel
        item {
            SectionCard("Theme", Icons.Default.Palette) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    THEME_MODES.forEach { mode ->
                        FilterChip(
                            selected = vm.themeMode == mode,
                            onClick = { vm.updateAppearance(theme = mode) },
                            label = { Text(if (mode == "Retro") "Retro ✦" else mode) }
                        )
                    }
                }
                if (vm.themeMode == "Retro") {
                    Text("Retro uses old-time parchment colors and a typewriter font.", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.secondary)
                }
                Text("Accent color", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    ACCENT_OPTIONS.forEach { (name, color) ->
                        FilterChip(
                            selected = vm.accentColor == name,
                            onClick = { vm.updateAppearance(accent = name) },
                            leadingIcon = { Box(Modifier.size(16.dp).clip(CircleShape).background(color)) },
                            label = { Text(name) }
                        )
                    }
                }
            }
        }

        item {
            SectionCard("Font & text size", Icons.Default.TextFields) {
                Text("Font", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    FONT_OPTIONS.forEach { name ->
                        FilterChip(
                            selected = vm.fontStyle == name,
                            onClick = { vm.updateAppearance(font = name) },
                            label = { Text(name, fontFamily = fontFamilyFor(name)) }
                        )
                    }
                }
                Text("Text size", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    TEXT_SIZE_OPTIONS.forEach { size ->
                        FilterChip(
                            selected = vm.textSize == size,
                            onClick = { vm.updateAppearance(size = size) },
                            label = { Text(size) }
                        )
                    }
                }
                Text(
                    "Auto fits the text to your screen width. Layouts re-arrange themselves for every size.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.secondary
                )
            }
        }

        // Daily quote notification
        item {
            SectionCard("Daily quote notification", Icons.Default.Notifications) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("Send a quote every day, even when the app is closed", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                    Switch(
                        checked = vm.notificationsEnabled,
                        onCheckedChange = { enabled ->
                            vm.updateNotifications(enabled)
                            if (enabled) requestNotificationPermissionIfNeeded()
                        }
                    )
                }
                if (vm.notificationsEnabled) {
                    Text("Time", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        NOTIFY_HOURS.forEach { (hour, label) ->
                            FilterChip(
                                selected = vm.notifyHour == hour,
                                onClick = { vm.updateNotifications(true, hour) },
                                label = { Text(label) }
                            )
                        }
                    }
                    Text(
                        "Android may deliver it a few minutes late to save battery.",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.secondary
                    )
                }
            }
        }

        // Security
        item {
            SectionCard("Privacy & security", Icons.Default.Lock) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Column(Modifier.weight(1f)) {
                        Text("App lock", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                        Text(
                            "Ask for fingerprint, face or phone PIN every time the app opens. Also hides the app in recent apps and blocks screenshots.",
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                    Switch(
                        checked = vm.appLockEnabled,
                        onCheckedChange = { enabled ->
                            if (enabled && !AppLock.isAvailable(context)) {
                                Toast.makeText(context, "First set a screen lock or fingerprint in your phone settings.", Toast.LENGTH_LONG).show()
                            } else {
                                vm.setAppLock(enabled)
                            }
                        }
                    )
                }
                Text(
                    "Your data stays on this phone only: no account, no cloud, no backup copy.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.secondary
                )
            }
        }

        // Einstein quote (kept) + credits
        item {
            Card(shape = RoundedCornerShape(18.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
                Column(Modifier.fillMaxWidth().padding(18.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        "\"Compound interest is the eighth wonder of the world. He who understands it, earns it... he who doesn't... pays it.\"",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium,
                        fontStyle = FontStyle.Italic,
                        textAlign = TextAlign.Center
                    )
                    Text("— Albert Einstein", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.secondary)
                }
            }
        }
        item { AppFooter(Modifier.padding(bottom = 16.dp)) }
    }
}
