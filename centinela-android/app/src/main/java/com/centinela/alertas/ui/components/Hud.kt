package com.centinela.alertas.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.centinela.alertas.ui.theme.Hud

/* ───────────── Fondo: cuadrícula + viñeta + línea de escaneo ───────────── */
@Composable
fun HudBackground(modifier: Modifier = Modifier, content: @Composable BoxScope.() -> Unit) {
    val t = rememberInfiniteTransition(label = "bg")
    val scan by t.animateFloat(0f, 1f, infiniteRepeatable(tween(6000, easing = LinearEasing)), label = "scan")
    Box(
        modifier
            .fillMaxSize()
            .background(Hud.Bg)
            .drawBehind {
                val step = 28.dp.toPx()
                var x = 0f
                while (x < size.width) { drawLine(Hud.Line.copy(alpha = 0.35f), Offset(x, 0f), Offset(x, size.height), 0.6f); x += step }
                var y = 0f
                while (y < size.height) { drawLine(Hud.Line.copy(alpha = 0.35f), Offset(0f, y), Offset(size.width, y), 0.6f); y += step }
                drawRect(Brush.radialGradient(
                    listOf(Color.Transparent, Hud.BgDeep.copy(alpha = 0.85f)),
                    center = Offset(size.width / 2, size.height * 0.35f), radius = size.maxDimension * 0.75f))
                val sy = scan * size.height
                drawRect(
                    Brush.verticalGradient(listOf(Color.Transparent, Hud.Cyan.copy(alpha = 0.07f), Color.Transparent), sy - 60f, sy + 4f),
                    topLeft = Offset(0f, sy - 60f), size = Size(size.width, 64f))
                drawLine(Hud.Cyan.copy(alpha = 0.18f), Offset(0f, sy), Offset(size.width, sy), 1f)
            },
        content = content,
    )
}

/* ───────────── Marco biselado con esquinas brillantes ───────────── */
fun Modifier.hudFrame(accent: Color = Hud.Cyan, cut: Dp = 12.dp, fill: Color = Hud.Panel.copy(alpha = 0.78f), glow: Boolean = true) =
    this.drawBehind {
        val c = cut.toPx()
        val w = size.width; val h = size.height
        val path = Path().apply {
            moveTo(c, 0f); lineTo(w - c * 0.4f, 0f); lineTo(w, c * 0.4f); lineTo(w, h - c); lineTo(w - c, h)
            lineTo(c * 0.4f, h); lineTo(0f, h - c * 0.4f); lineTo(0f, c); close()
        }
        drawPath(path, Brush.verticalGradient(listOf(fill, fill.copy(alpha = fill.alpha * 0.7f))))
        if (glow) drawPath(path, accent.copy(alpha = 0.10f), style = Stroke(6.dp.toPx()))
        drawPath(path, accent.copy(alpha = 0.45f), style = Stroke(1.dp.toPx()))
        val L = 16.dp.toPx(); val sw = 2.4.dp.toPx()
        // esquina superior izquierda (bisel)
        drawLine(accent, Offset(0f, c + L), Offset(0f, c), sw, StrokeCap.Square)
        drawLine(accent, Offset(0f, c), Offset(c, 0f), sw, StrokeCap.Square)
        drawLine(accent, Offset(c, 0f), Offset(c + L, 0f), sw, StrokeCap.Square)
        // esquina inferior derecha
        drawLine(accent, Offset(w, h - c - L), Offset(w, h - c), sw, StrokeCap.Square)
        drawLine(accent, Offset(w, h - c), Offset(w - c, h), sw, StrokeCap.Square)
        drawLine(accent, Offset(w - c, h), Offset(w - c - L, h), sw, StrokeCap.Square)
        // marcas
        val tick = 4.dp.toPx()
        for (i in 0..3) drawRect(accent.copy(alpha = 0.7f), Offset(w - 18.dp.toPx() - i * (tick + 3), 4.dp.toPx()), Size(tick, 2.dp.toPx()))
    }

@Composable
fun HudPanel(
    modifier: Modifier = Modifier,
    title: String? = null,
    accent: Color = Hud.Cyan,
    trailing: String? = null,
    contentPadding: Dp = 14.dp,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(modifier.hudFrame(accent).padding(contentPadding)) {
        if (title != null) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(6.dp).background(accent))
                Spacer(Modifier.width(8.dp))
                Text(title, color = accent, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 2.5.sp, fontFamily = Hud.Mono)
                Spacer(Modifier.width(8.dp))
                Box(Modifier.weight(1f).height(1.dp).background(Brush.horizontalGradient(listOf(accent.copy(alpha = 0.6f), Color.Transparent))))
                if (trailing != null) {
                    Spacer(Modifier.width(8.dp))
                    Text(trailing, color = Hud.TextDim, fontSize = 10.sp, fontFamily = Hud.Mono, letterSpacing = 1.sp)
                }
            }
            Spacer(Modifier.height(10.dp))
        }
        content()
    }
}

@Composable
fun HudButton(
    text: String,
    modifier: Modifier = Modifier,
    accent: Color = Hud.Cyan,
    icon: ImageVector? = null,
    filled: Boolean = false,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    Row(
        modifier
            .alpha(if (enabled) 1f else 0.4f)
            .hudFrame(accent, cut = 8.dp, fill = if (filled) accent.copy(alpha = 0.22f) else Hud.Panel.copy(alpha = 0.6f), glow = filled)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        if (icon != null) {
            Icon(icon, null, tint = accent, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
        }
        Text(text, color = if (filled) Hud.Text else accent, fontFamily = Hud.Mono, fontWeight = FontWeight.Bold,
            fontSize = 12.sp, letterSpacing = 1.5.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
fun HudChip(text: String, selected: Boolean, accent: Color = Hud.Cyan, onClick: () -> Unit) {
    Box(
        Modifier
            .hudFrame(accent.copy(alpha = if (selected) 1f else 0.45f), cut = 6.dp,
                fill = if (selected) accent.copy(alpha = 0.2f) else Hud.Panel.copy(alpha = 0.5f), glow = selected)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 7.dp)
    ) {
        Text(text, color = if (selected) Hud.Text else accent.copy(alpha = 0.8f), fontSize = 11.sp,
            fontFamily = Hud.Mono, fontWeight = FontWeight.Bold, letterSpacing = 1.5.sp)
    }
}

@Composable
fun BlinkDot(color: Color, size: Dp = 8.dp, periodMs: Int = 900) {
    val t = rememberInfiniteTransition(label = "blink")
    val a by t.animateFloat(1f, 0.15f, infiniteRepeatable(tween(periodMs), RepeatMode.Reverse), label = "a")
    Box(Modifier.size(size).alpha(a).background(color, CircleShape).drawBehind {
        drawCircle(color.copy(alpha = 0.35f), radius = this.size.minDimension)
    })
}

@Composable
fun HudLabelValue(label: String, value: String, color: Color = Hud.Text, modifier: Modifier = Modifier) {
    Column(modifier) {
        Text(label, color = Hud.TextDim, fontSize = 9.sp, letterSpacing = 2.sp, fontFamily = Hud.Mono)
        Text(value, color = color, fontSize = 15.sp, fontWeight = FontWeight.Bold, fontFamily = Hud.Mono, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
fun HudDivider(accent: Color = Hud.Cyan) {
    Canvas(Modifier.fillMaxWidth().height(6.dp)) {
        val y = size.height / 2
        drawLine(accent.copy(alpha = 0.35f), Offset(0f, y), Offset(size.width, y), 1f,
            pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 6f)))
        drawRect(accent, Offset(0f, y - 2f), Size(24f, 4f))
        drawRect(accent, Offset(size.width - 24f, y - 2f), Size(24f, 4f))
    }
}

/* ───────────── Anillos giratorios segmentados (emblema) ───────────── */
@Composable
fun SpinnerRings(modifier: Modifier = Modifier, color: Color = Hud.Cyan) {
    val t = rememberInfiniteTransition(label = "rings")
    val r1 by t.animateFloat(0f, 360f, infiniteRepeatable(tween(9000, easing = LinearEasing)), label = "r1")
    val r2 by t.animateFloat(360f, 0f, infiniteRepeatable(tween(5000, easing = LinearEasing)), label = "r2")
    Canvas(modifier) {
        val s = size.minDimension
        rotate(r1) { segArc(color, s * 0.48f, 3.dp.toPx(), 6, 38f) }
        rotate(r2) { segArc(Hud.Blue, s * 0.36f, 2.dp.toPx(), 3, 80f) }
        drawCircle(color.copy(alpha = 0.25f), s * 0.24f, style = Stroke(1.dp.toPx()))
        drawCircle(color, s * 0.09f)
        drawCircle(color.copy(alpha = 0.3f), s * 0.16f)
    }
}

fun DrawScope.segArc(color: Color, radius: Float, stroke: Float, segments: Int, sweep: Float) {
    val step = 360f / segments
    for (i in 0 until segments) {
        drawArc(color, i * step, sweep, false,
            topLeft = Offset(center.x - radius, center.y - radius), size = Size(radius * 2, radius * 2),
            style = Stroke(stroke, cap = StrokeCap.Butt))
    }
}

/* ───────────── Medidor de arco (nivel de amenaza) ───────────── */
@Composable
fun ArcGauge(value: Float, color: Color, modifier: Modifier = Modifier, segments: Int = 28) {
    val t = rememberInfiniteTransition(label = "gauge")
    val spin by t.animateFloat(0f, 360f, infiniteRepeatable(tween(14000, easing = LinearEasing)), label = "spin")
    Canvas(modifier) {
        val s = size.minDimension
        val r = s * 0.42f
        val start = 135f; val total = 270f
        val gap = 2.5f
        val seg = total / segments
        val lit = (value.coerceIn(0f, 1f) * segments).toInt()
        for (i in 0 until segments) {
            val c = if (i < lit) color else Hud.Line
            drawArc(c, start + i * seg, seg - gap, false,
                topLeft = Offset(center.x - r, center.y - r), size = Size(r * 2, r * 2), style = Stroke(s * 0.07f))
        }
        val r2 = s * 0.5f
        rotate(spin) {
            drawArc(Hud.Blue.copy(alpha = 0.6f), 0f, 60f, false, Offset(center.x - r2 + 4, center.y - r2 + 4),
                Size(r2 * 2 - 8, r2 * 2 - 8), style = Stroke(1.5.dp.toPx()))
            drawArc(Hud.Blue.copy(alpha = 0.6f), 180f, 60f, false, Offset(center.x - r2 + 4, center.y - r2 + 4),
                Size(r2 * 2 - 8, r2 * 2 - 8), style = Stroke(1.5.dp.toPx()))
        }
        drawCircle(Hud.Line, s * 0.3f, style = Stroke(1f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(4f, 6f))))
    }
}
