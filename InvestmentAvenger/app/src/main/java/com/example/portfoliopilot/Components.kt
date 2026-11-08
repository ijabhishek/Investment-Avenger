package com.example.portfoliopilot

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import java.io.File
import java.io.FileOutputStream

// ---------------------------------------------------------------------------
// Clipboard / sharing
// ---------------------------------------------------------------------------
fun copyToClipboard(context: Context, text: String, message: String = "Copied") {
    val manager = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    manager.setPrimaryClip(ClipData.newPlainText("Investment Avenger", text))
    Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
}

fun shareText(context: Context, text: String, subject: String? = null, chooserTitle: String = "Share") {
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        if (subject != null) putExtra(Intent.EXTRA_SUBJECT, subject)
        putExtra(Intent.EXTRA_TEXT, text)
    }
    context.startActivity(Intent.createChooser(intent, chooserTitle))
}

// ---------------------------------------------------------------------------
// Profile photo: copied into the app's private storage (so it survives restarts) and shrunk.
// ---------------------------------------------------------------------------
fun saveProfilePhoto(context: Context, uri: Uri): String? {
    return try {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        var sample = 1
        while (bounds.outWidth / sample > 1024 || bounds.outHeight / sample > 1024) sample *= 2
        val options = BitmapFactory.Options().apply { inSampleSize = sample }
        val bitmap = context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, options) }
            ?: return null
        val file = File(context.filesDir, "profile_${System.currentTimeMillis()}.jpg")
        FileOutputStream(file).use { bitmap.compress(Bitmap.CompressFormat.JPEG, 90, it) }
        context.filesDir.listFiles()?.filter { it.name.startsWith("profile_") && it != file }?.forEach { it.delete() }
        file.absolutePath
    } catch (_: Exception) {
        null
    }
}

private fun loadProfileBitmap(context: Context, reference: String): ImageBitmap? {
    if (reference.isBlank()) return null
    return try {
        if (reference.startsWith("/")) {
            BitmapFactory.decodeFile(reference)?.asImageBitmap()
        } else {
            // Photos saved by older versions were stored as content links.
            context.contentResolver.openInputStream(Uri.parse(reference))?.use { BitmapFactory.decodeStream(it) }?.asImageBitmap()
        }
    } catch (_: Exception) {
        null
    }
}

@Composable
fun ProfileAvatar(photo: String, name: String, size: Dp, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val bitmap = remember(photo) { loadProfileBitmap(context, photo) }
    Box(
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.primaryContainer),
        contentAlignment = Alignment.Center
    ) {
        if (bitmap != null) {
            androidx.compose.foundation.Image(
                bitmap = bitmap,
                contentDescription = "Profile photo",
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )
        } else {
            Text(
                name.take(1).uppercase().ifBlank { "A" },
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onPrimaryContainer
            )
        }
    }
}

// ---------------------------------------------------------------------------
// Small building blocks
// ---------------------------------------------------------------------------
/** "Label ........ value". The label wraps if needed; the value stays right-aligned. */
@Composable
fun LabelValueRow(label: String, value: String, valueColor: Color = Color.Unspecified) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.Top) {
        Text(label, style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
        Text(
            value,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Bold,
            color = valueColor,
            textAlign = TextAlign.End,
            modifier = Modifier.weight(1f)
        )
    }
}

/** Label at the top and value at the bottom, so cards in a row line up at any font size. */
@Composable
fun MetricCard(label: String, value: String, modifier: Modifier = Modifier) {
    Card(modifier, shape = RoundedCornerShape(14.dp)) {
        Column(Modifier.fillMaxSize().padding(12.dp), verticalArrangement = Arrangement.SpaceBetween) {
            Text(label, style = MaterialTheme.typography.labelSmall)
            Spacer(Modifier.height(4.dp))
            Text(value, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        }
    }
}

/** A coloured +/- pill. Green when [pnl] >= 0, red otherwise. */
@Composable
fun PnlChip(pnl: Double, pct: Double, currency: String) {
    val color = if (pnl >= 0) PROFIT_GREEN else LOSS_RED
    Text(
        "${fmtSignedMoney(pnl, currency)} (${fmtPct(pct)})",
        color = color,
        style = MaterialTheme.typography.labelMedium,
        fontWeight = FontWeight.Bold,
        textAlign = TextAlign.End,
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(color.copy(alpha = 0.12f))
            .padding(horizontal = 10.dp, vertical = 4.dp)
    )
}

/** One market: title + value on the first line, invested amount + profit/loss on the second. */
@Composable
fun MarketSummaryRow(stats: MarketStats) {
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.Top) {
            Text(marketLabel(stats.market), style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
            Text(
                fmtMoney(stats.value, stats.currency),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.End,
                modifier = Modifier.weight(1.4f)
            )
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("Invested ${fmtMoney(stats.invested, stats.currency, 0)}", style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
            PnlChip(stats.pnl, stats.pnlPct, stats.currency)
        }
    }
}

/** Card with an icon + title, used for the sections of the Profile tab. */
@Composable
fun SectionCard(title: String, icon: ImageVector, content: @Composable ColumnScope.() -> Unit) {
    Card(shape = RoundedCornerShape(18.dp)) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Icon(icon, null, tint = MaterialTheme.colorScheme.primary)
                Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            }
            content()
        }
    }
}

/** A quote with Copy, Share and (optionally) "another quote" buttons. */
@Composable
fun QuoteCard(
    heading: String,
    quote: Pair<String, String>,
    modifier: Modifier = Modifier,
    onNewQuote: (() -> Unit)? = null
) {
    val context = LocalContext.current
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
    ) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(heading, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onPrimaryContainer)
            Text(
                "\"${quote.first}\"",
                style = MaterialTheme.typography.bodyMedium,
                fontStyle = FontStyle.Italic,
                color = MaterialTheme.colorScheme.onPrimaryContainer
            )
            Text(
                "— ${quote.second}",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.align(Alignment.End)
            )
            Row(Modifier.align(Alignment.End), verticalAlignment = Alignment.CenterVertically) {
                if (onNewQuote != null) {
                    IconButton(onClick = onNewQuote) { Icon(Icons.Default.Refresh, "Another quote") }
                }
                IconButton(onClick = { copyToClipboard(context, quoteAsText(quote), "Quote copied") }) {
                    Icon(Icons.Default.ContentCopy, "Copy quote")
                }
                IconButton(onClick = { shareText(context, quoteAsText(quote), chooserTitle = "Share quote") }) {
                    Icon(Icons.Default.Share, "Share quote")
                }
            }
        }
    }
}

/** Fancy credit footer shown at the bottom of the Dashboard and the Profile tab. */
@Composable
fun AppFooter(modifier: Modifier = Modifier) {
    val primary = MaterialTheme.colorScheme.primary
    val tertiary = MaterialTheme.colorScheme.tertiary
    val brush = Brush.horizontalGradient(listOf(primary, tertiary, primary))
    val shape = RoundedCornerShape(22.dp)
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .border(1.5.dp, brush, shape)
            .padding(horizontal = 18.dp, vertical = 20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Text("✦  ✦  ✦", color = tertiary, style = MaterialTheme.typography.titleMedium)
        Text(
            "The idea of this app was born from",
            style = MaterialTheme.typography.labelLarge,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            "💡 X-Men Laxman",
            style = MaterialTheme.typography.headlineSmall.copy(brush = brush),
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center
        )
        Text(
            "and made real by",
            style = MaterialTheme.typography.labelLarge,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            "🛠️ Abhishek",
            style = MaterialTheme.typography.headlineSmall.copy(brush = brush),
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(6.dp))
        Text(
            "Investment Avenger • Version 1.0\nMade with passion for financial freedom ✨",
            style = MaterialTheme.typography.labelSmall,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
