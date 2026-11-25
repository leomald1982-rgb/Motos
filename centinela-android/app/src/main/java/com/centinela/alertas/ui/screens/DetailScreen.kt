package com.centinela.alertas.ui.screens

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CutCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material.icons.filled.SupportAgent
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.centinela.alertas.data.Alert
import com.centinela.alertas.data.Category
import com.centinela.alertas.data.GeoPoint
import com.centinela.alertas.data.Settings
import com.centinela.alertas.ui.components.CategoryBadge
import com.centinela.alertas.ui.components.HudButton
import com.centinela.alertas.ui.components.HudLabelValue
import com.centinela.alertas.ui.components.HudPanel
import com.centinela.alertas.ui.components.Tag
import com.centinela.alertas.ui.theme.Hud
import com.centinela.alertas.util.Emergency
import com.centinela.alertas.util.Geo
import com.centinela.alertas.util.TimeFmt

@Composable
fun DetailScreen(alert: Alert?, me: GeoPoint?, settings: Settings, saved: Boolean, onBack: () -> Unit, onToggleSave: (Alert) -> Unit) {
    val ctx = LocalContext.current
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, "Volver", tint = Hud.Cyan,
                modifier = Modifier.size(44.dp).clickable(onClick = onBack).padding(10.dp))
            Text("EXPEDIENTE", color = Hud.Cyan, fontSize = 13.sp, fontFamily = Hud.Mono, letterSpacing = 3.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.weight(1f))
            if (alert != null) Icon(if (saved) Icons.Filled.Star else Icons.Filled.StarBorder, "Guardar", tint = Hud.Amber,
                modifier = Modifier.size(44.dp).clickable { onToggleSave(alert) }.padding(10.dp))
        }
        if (alert == null) {
            Text("REGISTRO NO DISPONIBLE", color = Hud.TextDim, fontFamily = Hud.Mono, modifier = Modifier.padding(24.dp))
            return
        }
        val sev = Hud.severity(alert.severity)
        val cat = Hud.category(alert.category)
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 14.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            HudPanel(accent = sev) {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Tag(alert.category.label, cat); Tag(alert.severity.label, sev); Tag(alert.source, Hud.Blue)
                }
                Spacer(Modifier.height(10.dp))
                if (alert.isPerson && alert.imageUrl != null) {
                    ScanPortrait(alert.imageUrl, cat)
                    Spacer(Modifier.height(12.dp))
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (!(alert.isPerson && alert.imageUrl != null)) { CategoryBadge(alert.category, 48); Spacer(Modifier.width(12.dp)) }
                    Text(alert.title, color = Hud.Text, fontSize = 17.sp, fontWeight = FontWeight.Bold, fontFamily = Hud.Mono, lineHeight = 22.sp)
                }
                Spacer(Modifier.height(10.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    HudLabelValue("REGISTRO", if (alert.time > 0) TimeFmt.ago(alert.time) else "VIGENTE")
                    alert.magnitude?.let { HudLabelValue("MAGNITUD", "%.1f".format(it), sev) }
                    alert.depthKm?.let { HudLabelValue("PROF.", "%.0f KM".format(it)) }
                    if (me != null && alert.lat != null && alert.lon != null) {
                        val p = GeoPoint(alert.lat, alert.lon)
                        HudLabelValue("DIST.", "%.0f KM %s".format(Geo.distanceKm(me, p), Geo.cardinal(Geo.bearing(me, p))), Hud.CyanSoft)
                    }
                }
                if (alert.time > 0) {
                    Spacer(Modifier.height(6.dp))
                    Text(TimeFmt.full(alert.time), color = Hud.TextDim, fontSize = 10.sp, fontFamily = Hud.Mono)
                }
            }

            alert.reward?.let {
                HudPanel(title = "RECOMPENSA", accent = Hud.Green) {
                    Text(it, color = Hud.Green, fontSize = 12.sp, fontFamily = Hud.Mono, lineHeight = 17.sp)
                }
            }

            HudPanel(title = "DATOS DE INTELIGENCIA") {
                Text(alert.details.ifBlank { alert.summary }, color = Hud.Text, fontSize = 12.sp, fontFamily = Hud.Mono, lineHeight = 18.sp)
            }

            if (alert.isPerson || alert.category == Category.FUGA) {
                HudPanel(title = "PROTOCOLO", accent = Hud.Amber) {
                    Text(
                        "1. NO te acerques ni intentes detener a la persona.\n" +
                            "2. Anota hora, lugar, ropa, vehículo y dirección de huida.\n" +
                            "3. Reporta de inmediato a las autoridades.\n" +
                            "4. Comparte solo información de fuentes oficiales.",
                        color = Hud.Text, fontSize = 12.sp, fontFamily = Hud.Mono, lineHeight = 18.sp
                    )
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                alert.url?.let { url ->
                    HudButton("FUENTE", Modifier.weight(1f), icon = Icons.AutoMirrored.Filled.OpenInNew) { Emergency.open(ctx, url) }
                }
                HudButton("COMPARTIR", Modifier.weight(1f), icon = Icons.Filled.Share) {
                    Emergency.share(ctx, buildString {
                        append("⚠ ${alert.category.label}: ${alert.title}\n${alert.summary}")
                        alert.url?.let { append("\n$it") }
                        append("\n— vía Centinela")
                    })
                }
            }
            if (alert.lat != null && alert.lon != null) {
                HudButton("VER EN MAPA", Modifier.fillMaxWidth(), icon = Icons.Filled.Map) {
                    Emergency.open(ctx, "geo:${alert.lat},${alert.lon}?q=${alert.lat},${alert.lon}(${android.net.Uri.encode(alert.title)})")
                }
            }
            if (alert.isPerson || alert.category == Category.FUGA) {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    HudButton("LLAMAR ${settings.region.emergency}", Modifier.weight(1f), accent = Hud.Red, icon = Icons.Filled.Call, filled = true) {
                        Emergency.dial(ctx, settings.region.emergency)
                    }
                    val tip = settings.region.anonymousTip
                    when {
                        tip != null -> HudButton("DENUNCIA $tip", Modifier.weight(1f), accent = Hud.Amber, icon = Icons.Filled.SupportAgent) { Emergency.dial(ctx, tip) }
                        alert.source.startsWith("FBI") -> HudButton("TIPS FBI", Modifier.weight(1f), accent = Hud.Amber, icon = Icons.Filled.SupportAgent) { Emergency.open(ctx, "https://tips.fbi.gov/") }
                    }
                }
            }
            Spacer(Modifier.height(20.dp))
        }
    }
}

@Composable
private fun ScanPortrait(url: String, accent: Color) {
    val t = rememberInfiniteTransition(label = "scanp")
    val y by t.animateFloat(0f, 1f, infiniteRepeatable(tween(2200, easing = LinearEasing)), label = "y")
    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        Box(
            Modifier.width(200.dp).aspectRatio(0.8f).clip(CutCornerShape(14.dp)).border(1.5.dp, accent, CutCornerShape(14.dp))
                .background(Hud.BgDeep)
                .drawWithContent {
                    drawContent()
                    val yy = y * size.height
                    drawRect(Brush.verticalGradient(listOf(Color.Transparent, accent.copy(alpha = 0.35f)), yy - 50f, yy),
                        topLeft = Offset(0f, yy - 50f), size = androidx.compose.ui.geometry.Size(size.width, 50f))
                    drawLine(accent, Offset(0f, yy), Offset(size.width, yy), 2f)
                    val L = 22f
                    listOf(Offset(14f, 14f) to Pair(1, 1), Offset(size.width - 14f, 14f) to Pair(-1, 1),
                        Offset(14f, size.height - 14f) to Pair(1, -1), Offset(size.width - 14f, size.height - 14f) to Pair(-1, -1)
                    ).forEach { (o, d) ->
                        drawLine(accent, o, Offset(o.x + L * d.first, o.y), 3f)
                        drawLine(accent, o, Offset(o.x, o.y + L * d.second), 3f)
                    }
                }
        ) {
            AsyncImage(model = url, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
        }
    }
    Spacer(Modifier.height(6.dp))
    Text("◢ IDENTIFICACIÓN VISUAL · ANALIZANDO", color = accent, fontSize = 9.sp, letterSpacing = 2.sp, fontFamily = Hud.Mono,
        modifier = Modifier.fillMaxWidth(), textAlign = androidx.compose.ui.text.style.TextAlign.Center)
}
