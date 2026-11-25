package com.centinela.alertas.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.unit.dp
import com.centinela.alertas.data.Alert
import com.centinela.alertas.data.GeoPoint
import com.centinela.alertas.ui.theme.Hud
import com.centinela.alertas.util.Geo
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Radar táctico: coloca los sismos alrededor de tu posición según rumbo y distancia.
 * Escala raíz cuadrada para que lo cercano tenga más resolución.
 */
@Composable
fun Radar(quakes: List<Alert>, center: GeoPoint, rangeKm: Int, modifier: Modifier = Modifier) {
    val t = rememberInfiniteTransition(label = "radar")
    val sweep by t.animateFloat(0f, 360f, infiniteRepeatable(tween(4000, easing = LinearEasing)), label = "sweep")
    val pulse by t.animateFloat(0.4f, 1f, infiniteRepeatable(tween(900), RepeatMode.Reverse), label = "pulse")
    val range = if (rangeKm <= 0) 20000 else rangeKm
    val paint = android.graphics.Paint().apply {
        color = android.graphics.Color.argb(170, 110, 154, 179); textSize = 26f; isAntiAlias = true
        typeface = android.graphics.Typeface.MONOSPACE
    }
    Canvas(modifier) {
        val r = size.minDimension / 2f * 0.92f
        val c = center
        val ctr = this.center
        // fondo
        drawCircle(Brush.radialGradient(listOf(Hud.Cyan.copy(alpha = 0.08f), Color.Transparent), ctr, r), r)
        // anillos
        for (i in 1..4) {
            drawCircle(Hud.Cyan.copy(alpha = if (i == 4) 0.6f else 0.22f), r * i / 4f, style = Stroke(if (i == 4) 2f else 1f))
        }
        // ticks exteriores
        for (d in 0 until 360 step 5) {
            val a = Math.toRadians(d.toDouble() - 90)
            val len = if (d % 30 == 0) 14f else 6f
            val p1 = Offset(ctr.x + (r * cos(a)).toFloat(), ctr.y + (r * sin(a)).toFloat())
            val p2 = Offset(ctr.x + ((r + len) * cos(a)).toFloat(), ctr.y + ((r + len) * sin(a)).toFloat())
            drawLine(Hud.Cyan.copy(alpha = if (d % 30 == 0) 0.8f else 0.35f), p1, p2, 1.5f)
        }
        // cruz
        drawLine(Hud.Cyan.copy(alpha = 0.2f), Offset(ctr.x - r, ctr.y), Offset(ctr.x + r, ctr.y), 1f,
            pathEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 8f)))
        drawLine(Hud.Cyan.copy(alpha = 0.2f), Offset(ctr.x, ctr.y - r), Offset(ctr.x, ctr.y + r), 1f,
            pathEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 8f)))
        // barrido
        rotate(sweep - 90f) {
            drawArc(
                Brush.sweepGradient(
                    0f to Color.Transparent, 0.82f to Color.Transparent, 1f to Hud.Cyan.copy(alpha = 0.35f), center = ctr
                ),
                startAngle = 0f, sweepAngle = 360f, useCenter = true,
                topLeft = Offset(ctr.x - r, ctr.y - r), size = Size(r * 2, r * 2)
            )
            drawLine(Hud.Cyan, ctr, Offset(ctr.x + r, ctr.y), 2f)
        }
        // etiquetas
        drawContext.canvas.nativeCanvas.apply {
            drawText("N", ctr.x - 8f, ctr.y - r - 20f, paint)
            for (i in 1..4) {
                val km = range * (i / 4f) * (i / 4f)
                drawText("${km.toInt()}km", ctr.x + 4f, ctr.y - r * i / 4f + 24f, paint)
            }
        }
        // blips
        quakes.forEach { q ->
            val lat = q.lat ?: return@forEach; val lon = q.lon ?: return@forEach
            val p = GeoPoint(lat, lon)
            val dist = Geo.distanceKm(c, p)
            if (dist > range) return@forEach
            val brg = Math.toRadians(Geo.bearing(c, p) - 90)
            val rr = (sqrt(dist / range) * r).toFloat()
            val pos = Offset(ctr.x + (rr * cos(brg)).toFloat(), ctr.y + (rr * sin(brg)).toFloat())
            val col = Hud.severity(q.severity)
            val m = (q.magnitude ?: 3.0).toFloat()
            val size = (2f + (m - 2f).coerceAtLeast(0f) * 1.3f).dp.toPx()
            if (q.severity.weight > 1) drawCircle(col.copy(alpha = 0.22f * pulse), size * 2.4f, pos)
            drawCircle(col.copy(alpha = 0.9f), size, pos)
        }
        // tú
        drawCircle(Hud.Green, 5.dp.toPx(), ctr)
        drawCircle(Hud.Green.copy(alpha = 0.4f), 10.dp.toPx() * pulse, ctr, style = Stroke(2f))
    }
}
