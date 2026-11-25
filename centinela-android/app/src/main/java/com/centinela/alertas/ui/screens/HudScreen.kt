package com.centinela.alertas.ui.screens

import androidx.compose.foundation.background
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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Sos
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.centinela.alertas.data.Alert
import com.centinela.alertas.data.Category
import com.centinela.alertas.data.Settings
import com.centinela.alertas.data.Severity
import com.centinela.alertas.ui.UiState
import com.centinela.alertas.ui.components.AlertCard
import com.centinela.alertas.ui.components.ArcGauge
import com.centinela.alertas.ui.components.BlinkDot
import com.centinela.alertas.ui.components.HudButton
import com.centinela.alertas.ui.components.HudLabelValue
import com.centinela.alertas.ui.components.HudPanel
import com.centinela.alertas.ui.components.Radar
import com.centinela.alertas.ui.components.categoryIcon
import com.centinela.alertas.ui.theme.Hud
import com.centinela.alertas.util.Geo
import com.centinela.alertas.util.TimeFmt

@Composable
fun HudScreen(
    state: UiState,
    settings: Settings,
    saved: Set<String>,
    onRefresh: () -> Unit,
    onLocate: () -> Unit,
    onOpen: (Alert) -> Unit,
    onCategory: (Category) -> Unit,
    onSos: () -> Unit,
) {
    val center = state.location ?: settings.location ?: settings.region.center
    val quakes = state.alerts.filter { it.category == Category.SISMO }
    val threatColor = when {
        state.threat >= 0.75f -> Hud.Red
        state.threat >= 0.45f -> Hud.Amber
        else -> Hud.Cyan
    }
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 14.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        if (state.loading) LinearProgressIndicator(Modifier.fillMaxWidth().height(2.dp), color = Hud.Cyan, trackColor = Hud.Line)

        /* ── NIVEL DE AMENAZA ── */
        HudPanel(title = "NIVEL DE AMENAZA", accent = threatColor, trailing = "24H") {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(132.dp), contentAlignment = Alignment.Center) {
                    ArcGauge(state.threat, threatColor, Modifier.fillMaxSize())
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("${(state.threat * 100).toInt()}", color = threatColor, fontSize = 34.sp, fontWeight = FontWeight.Black, fontFamily = Hud.Mono)
                        Text(state.threatLabel, color = threatColor, fontSize = 10.sp, letterSpacing = 2.sp, fontFamily = Hud.Mono)
                    }
                }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Category.entries.forEach { c ->
                        val col = Hud.category(c)
                        val enabled = c in settings.enabledCategories
                        Row(
                            Modifier.fillMaxWidth().clickable { onCategory(c) }.padding(vertical = 3.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(categoryIcon(c), null, tint = if (enabled) col else Hud.TextDim, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(8.dp))
                            Text(c.code, color = if (enabled) col else Hud.TextDim, fontSize = 11.sp, fontFamily = Hud.Mono, fontWeight = FontWeight.Bold)
                            Spacer(Modifier.width(6.dp))
                            Box(Modifier.weight(1f).height(4.dp).background(Hud.Line)) {
                                val frac = (state.count(c, 24 * 7) / 40f).coerceIn(0.02f, 1f)
                                Box(Modifier.fillMaxWidth(if (enabled) frac else 0f).height(4.dp).background(col))
                            }
                            Spacer(Modifier.width(8.dp))
                            Text(if (enabled) "${state.count(c, 24)}" else "OFF", color = Hud.Text, fontSize = 13.sp,
                                fontFamily = Hud.Mono, fontWeight = FontWeight.Bold)
                        }
                    }
                    Text("BARRAS: 7 DÍAS · CIFRA: 24 H", color = Hud.TextDim, fontSize = 8.sp, fontFamily = Hud.Mono, letterSpacing = 1.sp)
                }
            }
        }

        /* ── ACCIONES RÁPIDAS ── */
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            HudButton("S.O.S", Modifier.weight(1f), accent = Hud.Red, icon = Icons.Filled.Sos, filled = true, onClick = onSos)
            HudButton(if (state.loading) "ESCANEANDO" else "ESCANEAR", Modifier.weight(1f), icon = Icons.Filled.Refresh,
                enabled = !state.loading, onClick = onRefresh)
        }

        /* ── RADAR ── */
        if (Category.SISMO in settings.enabledCategories) {
            val inRange = quakes.filter { q ->
                q.lat != null && q.lon != null &&
                    (settings.radiusKm <= 0 || Geo.distanceKm(center, com.centinela.alertas.data.GeoPoint(q.lat, q.lon)) <= settings.radiusKm)
            }
            val strongest = inRange.maxByOrNull { it.magnitude ?: 0.0 }
            HudPanel(title = "RADAR SÍSMICO", trailing = if (settings.radiusKm > 0) "R ${settings.radiusKm} KM" else "GLOBAL") {
                Box(Modifier.fillMaxWidth().aspectRatio(1f).padding(8.dp)) {
                    Radar(inRange, center, settings.radiusKm, Modifier.fillMaxSize())
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    HudLabelValue("EVENTOS 7D", "${inRange.size}")
                    HudLabelValue("MÁX", strongest?.magnitude?.let { "M%.1f".format(it) } ?: "—",
                        color = strongest?.let { Hud.severity(it.severity) } ?: Hud.Text)
                    HudLabelValue("ÚLTIMO", inRange.maxByOrNull { it.time }?.let { TimeFmt.ago(it.time) } ?: "—")
                }
                strongest?.let {
                    Spacer(Modifier.height(8.dp))
                    Text("▸ ${it.title}", color = Hud.CyanSoft, fontSize = 11.sp, fontFamily = Hud.Mono, maxLines = 1,
                        overflow = TextOverflow.Ellipsis, modifier = Modifier.clickable { onOpen(it) })
                }
            }
        }

        /* ── PRIORIDAD ALTA ── */
        val priority = state.alerts
            .filter { it.severity != Severity.INFO }
            .sortedWith(compareByDescending<Alert> { it.severity.weight }.thenByDescending { it.time })
            .filter { it.time > System.currentTimeMillis() - 7 * 86_400_000L }
            .take(6)
        HudPanel(title = "PRIORIDAD ALTA", accent = Hud.Amber, trailing = "${priority.size}") {
            if (priority.isEmpty()) {
                Text(if (state.loading) "ADQUIRIENDO DATOS…" else "SIN ALERTAS PRIORITARIAS · ZONA ESTABLE",
                    color = Hud.TextDim, fontSize = 11.sp, fontFamily = Hud.Mono)
            }
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                priority.forEach { AlertCard(it, state.location, it.id in saved) { onOpen(it) } }
            }
        }

        /* ── ENLACES DE DATOS ── */
        HudPanel(title = "ENLACES DE DATOS", accent = Hud.Blue,
            trailing = if (state.lastUpdate > 0) "SYNC ${TimeFmt.clock(state.lastUpdate)}" else "SYNC —") {
            if (state.status.isEmpty()) Text("INICIANDO ENLACES…", color = Hud.TextDim, fontSize = 11.sp, fontFamily = Hud.Mono)
            state.status.forEach { s ->
                Row(Modifier.fillMaxWidth().padding(vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
                    val col = when { s.message == "INACTIVO" -> Hud.TextDim; s.ok -> Hud.Green; else -> Hud.Red }
                    BlinkDot(col, 7.dp, if (s.ok) 1400 else 500)
                    Spacer(Modifier.width(10.dp))
                    Text(s.name, color = Hud.Text, fontSize = 12.sp, fontFamily = Hud.Mono, fontWeight = FontWeight.Bold, modifier = Modifier.width(96.dp))
                    Text(
                        when { s.message == "INACTIVO" -> "INACTIVO"; s.ok -> "EN LÍNEA · ${s.count} REG"; else -> "SIN ENLACE · CACHÉ" },
                        color = col, fontSize = 11.sp, fontFamily = Hud.Mono, modifier = Modifier.weight(1f)
                    )
                }
            }
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.clickable(onClick = onLocate)) {
                Icon(Icons.Filled.MyLocation, null, tint = Hud.Green, modifier = Modifier.size(14.dp))
                Spacer(Modifier.width(8.dp))
                val loc = state.location ?: settings.location
                Text(
                    if (loc != null) "POS ${"%.4f".format(loc.lat)}, ${"%.4f".format(loc.lon)}" else "POS NO DISPONIBLE · USANDO ${settings.region.name.uppercase()}",
                    color = Hud.Green, fontSize = 10.sp, fontFamily = Hud.Mono
                )
            }
        }
        Spacer(Modifier.height(6.dp))
    }
}
