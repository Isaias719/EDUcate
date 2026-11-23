package com.educate.app

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

val Navy = Color(0xFF152A4A)
val Bg = Color(0xFFF7F9FC)
val Soft = Color(0xFFEBF1F8)
val Line = Color(0xFFE5EBF2)
val Muted = Color(0xFF6B7B90)
val Ok = Color(0xFF2E7D4F)
val OkBg = Color(0xFFEAF6EE)
val Warn = Color(0xFFB27200)
val WarnBg = Color(0xFFFEF5E7)
val ErrColor = Color(0xFFBA1A1A)

private val EMAIL_REGEX = "^[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$".toRegex()
fun isValidEmail(email: String): Boolean = email.isNotBlank() && EMAIL_REGEX.matches(email.trim())

@Composable
fun EduTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = lightColorScheme(
            primary = Navy, onPrimary = Color.White, background = Bg, surface = Color.White,
            outline = Line
        ),
        content = content
    )
}

// ------------------------------------------------------------ fechas
private val es = Locale("es", "MX")
private fun parse(iso: String) = Instant.parse(iso.replace("+00:00", "Z")).atZone(ZoneId.systemDefault())
fun fmtDate(iso: String): String = parse(iso).format(DateTimeFormatter.ofPattern("d MMM", es)).replace(".", "").uppercase(es)
fun fmtDateLong(iso: String): String = parse(iso).format(DateTimeFormatter.ofPattern("d 'de' MMMM", es))
fun fmtDateTime(iso: String): String = parse(iso).format(DateTimeFormatter.ofPattern("d MMM, HH:mm", es))
fun fmtTime(iso: String): String = parse(iso).format(DateTimeFormatter.ofPattern("HH:mm", es))
fun dayOf(iso: String): Int = parse(iso).dayOfMonth
fun fmtSize(bytes: Long): String = if (bytes >= 1_048_576) "%.1f MB".format(bytes / 1_048_576.0) else "${maxOf(1, bytes / 1024)} KB"

// ------------------------------------------------------------ carga genérica
sealed interface UiState<out T> {
    data object Loading : UiState<Nothing>
    data class Ok<T>(val v: T) : UiState<T>
    data class Err(val msg: String) : UiState<Nothing>
}

@Composable
fun <T> Load(key: Any? = Unit, loader: suspend () -> T, content: @Composable (T, () -> Unit) -> Unit) {
    var state by remember(key) { mutableStateOf<UiState<T>>(UiState.Loading) }
    var tick by remember(key) { mutableIntStateOf(0) }
    LaunchedEffect(key, tick) {
        state = try {
            UiState.Ok(loader())
        } catch (e: Exception) {
            UiState.Err(Net.message(e))
        }
    }
    when (val s = state) {
        is UiState.Loading -> Box(Modifier.fillMaxSize(), Alignment.Center) { CircularProgressIndicator(color = Navy) }
        is UiState.Err -> Column(
            Modifier.fillMaxSize().padding(24.dp), Arrangement.Center, Alignment.CenterHorizontally
        ) {
            Text(s.msg, color = Muted)
            Spacer(Modifier.height(12.dp))
            Button(onClick = { tick++ }, colors = ButtonDefaults.buttonColors(containerColor = Navy)) { Text("Reintentar") }
        }
        is UiState.Ok -> content(s.v) { tick++ }
    }
}

// ------------------------------------------------------------ componentes
@Composable
fun Card2(
    modifier: Modifier = Modifier, color: Color = Color.White, border: Boolean = true,
    onClick: (() -> Unit)? = null, content: @Composable ColumnScope.() -> Unit
) {
    val shape = RoundedCornerShape(16.dp)
    val m = modifier.fillMaxWidth().clip(shape)
        .background(color).let { if (onClick != null) it.clickable(onClick = onClick) else it }
    Surface(
        modifier = m, color = color, shape = shape,
        border = if (border) BorderStroke(1.dp, Line) else null
    ) {
        Column(Modifier.padding(14.dp), content = content)
    }
}

@Composable
fun H1(text: String) = Text(text, fontSize = 23.sp, fontWeight = FontWeight.Bold, color = Navy)

@Composable
fun H2(text: String, modifier: Modifier = Modifier) = Text(text, fontSize = 17.sp, fontWeight = FontWeight.Bold, color = Navy, modifier = modifier)

@Composable
fun Sub(text: String, modifier: Modifier = Modifier, textAlign: TextAlign? = null) = Text(text, fontSize = 12.sp, color = Muted, modifier = modifier, textAlign = textAlign)

@Composable
fun PrimaryButton(
    text: String,
    enabled: Boolean = true,
    modifier: Modifier = Modifier,
    icon: androidx.compose.ui.graphics.vector.ImageVector? = null,
    onClick: () -> Unit
) {
    Button(
        onClick = onClick, enabled = enabled, modifier = modifier.fillMaxWidth().height(48.dp),
        shape = RoundedCornerShape(12.dp), colors = ButtonDefaults.buttonColors(containerColor = Navy)
    ) {
        if (icon != null) {
            Icon(icon, null, Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
        }
        Text(text, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
    }
}

@Composable
fun OutlineButton(
    text: String,
    modifier: Modifier = Modifier,
    icon: androidx.compose.ui.graphics.vector.ImageVector? = null,
    onClick: () -> Unit
) {
    OutlinedButton(
        onClick = onClick, modifier = modifier.fillMaxWidth().height(48.dp), shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.dp, Navy.copy(alpha = 0.5f))
    ) {
        if (icon != null) {
            Icon(icon, null, tint = Navy, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
        }
        Text(text, color = Navy, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
    }
}

@Composable
fun ProgressBar(percent: Double, modifier: Modifier = Modifier, track: Color = Line, fill: Color = Navy, height: Dp = 6.dp) {
    Box(modifier.fillMaxWidth().height(height).clip(CircleShape).background(track)) {
        Box(Modifier.fillMaxWidth((percent / 100.0).coerceIn(0.0, 1.0).toFloat()).fillMaxHeight().background(fill))
    }
}

@Composable
fun Pill(text: String, bg: Color = Soft, fg: Color = Navy) {
    Text(
        text, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = fg,
        modifier = Modifier.clip(RoundedCornerShape(50)).background(bg).padding(horizontal = 10.dp, vertical = 4.dp)
    )
}

@Composable
fun Avatar(initials: String, size: Dp = 38.dp) {
    Box(Modifier.size(size).clip(CircleShape).background(Soft), Alignment.Center) {
        Text(initials, color = Navy, fontWeight = FontWeight.Bold, fontSize = 13.sp)
    }
}

@Composable
fun TopBar(
    title: String,
    onBack: (() -> Unit)? = null,
    actions: @Composable RowScope.() -> Unit = {
        IconButton(onClick = {}) {
            Icon(Icons.Filled.MoreVert, "Opciones", tint = Navy)
        }
    }
) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (onBack != null) {
            IconButton(onClick = onBack) {
                Icon(androidx.compose.material.icons.Icons.AutoMirrored.Filled.ArrowBack, "Volver", tint = Navy)
            }
        } else {
            Spacer(Modifier.width(12.dp))
        }
        Text(title, fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Navy, modifier = Modifier.weight(1f))
        actions()
    }
}

@Composable
fun EmptyState(text: String) {
    Box(Modifier.fillMaxWidth().padding(vertical = 28.dp), Alignment.Center) { Sub(text) }
}
