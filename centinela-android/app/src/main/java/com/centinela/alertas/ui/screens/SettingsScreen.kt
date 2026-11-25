package com.centinela.alertas.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.Radar
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.centinela.alertas.data.Category
import com.centinela.alertas.data.Regions
import com.centinela.alertas.data.Settings
import com.centinela.alertas.ui.components.HudButton
import com.centinela.alertas.ui.components.HudChip
import com.centinela.alertas.ui.components.HudPanel
import com.centinela.alertas.ui.components.categoryIcon
import com.centinela.alertas.ui.theme.Hud
import com.centinela.alertas.work.AlertWorker
import com.centinela.alertas.work.Notifier
import kotlin.math.roundToInt

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SettingsScreen(settings: Settings, onUpdate: ((Settings) -> Settings) -> Unit) {
    val ctx = LocalContext.current
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 14.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        HudPanel(title = "MÓDULOS DE VIGILANCIA") {
            Category.entries.forEach { c ->
                ToggleRow(c.label, "Mostrar y escanear", c in settings.enabledCategories, Hud.category(c), c) { on ->
                    onUpdate { s -> s.copy(enabledCategories = if (on) s.enabledCategories + c else s.enabledCategories - c) }
                }
            }
        }

        HudPanel(title = "REGIÓN DE OPERACIÓN", accent = Hud.Blue, trailing = settings.region.name.uppercase()) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Regions.all.forEach { r ->
                    HudChip(r.code, settings.regionCode == r.code, Hud.Blue) { onUpdate { s -> s.copy(regionCode = r.code) } }
                }
            }
            Spacer(Modifier.size(10.dp))
            Text("Define idioma de noticias, notificaciones de Interpol por nacionalidad y números de emergencia (${settings.region.emergency}).",
                color = Hud.TextDim, fontSize = 10.sp, fontFamily = Hud.Mono, lineHeight = 14.sp)
            Spacer(Modifier.size(10.dp))
            var kw by remember(settings.localKeyword) { mutableStateOf(settings.localKeyword) }
            OutlinedTextField(
                value = kw, onValueChange = { kw = it }, singleLine = true,
                label = { Text("Zona / ciudad (filtra noticias)", fontFamily = Hud.Mono, fontSize = 11.sp) },
                placeholder = { Text("Ej. Monterrey", fontFamily = Hud.Mono) },
                modifier = Modifier.fillMaxWidth(), colors = hudFieldColors(),
                textStyle = TextStyle(fontFamily = Hud.Mono, fontSize = 13.sp, color = Hud.Text)
            )
            Spacer(Modifier.size(8.dp))
            HudButton("APLICAR ZONA", Modifier.fillMaxWidth(), accent = Hud.Blue, enabled = kw != settings.localKeyword) {
                onUpdate { s -> s.copy(localKeyword = kw.trim()) }
            }
        }

        HudPanel(title = "PARÁMETROS SÍSMICOS", accent = Hud.Cyan) {
            SliderRow("MAGNITUD MÍNIMA A MOSTRAR", "M%.1f".format(settings.minMagnitude), settings.minMagnitude, 2f..7f, 9) {
                onUpdate { s -> s.copy(minMagnitude = (it * 2).roundToInt() / 2f) }
            }
            val radii = listOf(100, 250, 500, 1000, 1500, 3000, 5000, 0)
            Text("RADIO DE VIGILANCIA", color = Hud.TextDim, fontSize = 10.sp, fontFamily = Hud.Mono, letterSpacing = 2.sp)
            Spacer(Modifier.size(6.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                radii.forEach { r ->
                    HudChip(if (r == 0) "GLOBAL" else "$r KM", settings.radiusKm == r) { onUpdate { s -> s.copy(radiusKm = r) } }
                }
            }
        }

        HudPanel(title = "NOTIFICACIONES", accent = Hud.Amber) {
            ToggleRow("NOTIFICACIONES", "Avisos aunque la app esté cerrada", settings.notificationsEnabled, Hud.Amber) { on ->
                onUpdate { s -> s.copy(notificationsEnabled = on) }
            }
            Category.entries.forEach { c ->
                ToggleRow("AVISAR: ${c.code}", c.label.lowercase().replaceFirstChar { it.uppercase() }, c in settings.notifyCategories, Hud.category(c), c) { on ->
                    onUpdate { s -> s.copy(notifyCategories = if (on) s.notifyCategories + c else s.notifyCategories - c) }
                }
            }
            SliderRow("AVISAR SISMOS DESDE", "M%.1f".format(settings.notifyMagnitude), settings.notifyMagnitude, 3f..8f, 9) {
                onUpdate { s -> s.copy(notifyMagnitude = (it * 2).roundToInt() / 2f) }
            }
            Text("ESCANEO EN SEGUNDO PLANO", color = Hud.TextDim, fontSize = 10.sp, fontFamily = Hud.Mono, letterSpacing = 2.sp)
            Spacer(Modifier.size(6.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(15, 30, 60).forEach { m ->
                    HudChip("$m MIN", settings.backgroundMinutes == m, Hud.Amber) { onUpdate { s -> s.copy(backgroundMinutes = m) } }
                }
            }
            Spacer(Modifier.size(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                HudButton("PROBAR", Modifier.weight(1f), accent = Hud.Amber, icon = Icons.Filled.NotificationsActive) { Notifier.test(ctx) }
                HudButton("ESCANEAR YA", Modifier.weight(1f), accent = Hud.Amber, icon = Icons.Filled.Radar) { AlertWorker.runNow(ctx) }
            }
        }

        HudPanel(title = "MODO EN VIVO", accent = Hud.Green) {
            ToggleRow("MODO EN VIVO", "Actualiza con la app abierta", settings.liveMode, Hud.Green) { on ->
                onUpdate { s -> s.copy(liveMode = on) }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(30, 60, 120, 300).forEach { sec ->
                    HudChip(if (sec < 60) "${sec}S" else "${sec / 60} MIN", settings.liveRefreshSec == sec, Hud.Green) {
                        onUpdate { s -> s.copy(liveRefreshSec = sec) }
                    }
                }
            }
        }

        HudPanel(title = "FUENTES Y AVISO LEGAL", accent = Hud.TextDim) {
            Text(
                "• Sismos: Servicio Geológico de EE. UU. (USGS), cobertura mundial.\n" +
                    "• Buscados y desaparecidos: API pública del FBI y notificaciones Roja/Amarilla de Interpol.\n" +
                    "• Fugas, Alerta Amber y fichas de búsqueda locales: noticias agregadas de medios.\n\n" +
                    "Centinela es una herramienta informativa y NO sustituye a los sistemas oficiales de alerta (p. ej. SASMEX), " +
                    "ni a protección civil o a la policía. Ante una emergencia llama al ${settings.region.emergency}. " +
                    "No intentes detener a ninguna persona: reporta a las autoridades.",
                color = Hud.TextDim, fontSize = 11.sp, fontFamily = Hud.Mono, lineHeight = 16.sp
            )
            Spacer(Modifier.size(6.dp))
            Text("CENTINELA v1.0.0", color = Hud.Cyan, fontSize = 10.sp, fontFamily = Hud.Mono, letterSpacing = 2.sp)
        }
        Spacer(Modifier.size(10.dp))
    }
}

@Composable
private fun ToggleRow(title: String, subtitle: String, checked: Boolean, accent: Color, category: Category? = null, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        if (category != null) {
            Icon(categoryIcon(category), null, tint = if (checked) accent else Hud.TextDim, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(10.dp))
        }
        Column(Modifier.weight(1f)) {
            Text(title, color = if (checked) Hud.Text else Hud.TextDim, fontSize = 12.sp, fontFamily = Hud.Mono, fontWeight = FontWeight.Bold)
            Text(subtitle, color = Hud.TextDim, fontSize = 10.sp, fontFamily = Hud.Mono)
        }
        Switch(
            checked = checked, onCheckedChange = onChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Hud.BgDeep, checkedTrackColor = accent, checkedBorderColor = accent,
                uncheckedThumbColor = Hud.TextDim, uncheckedTrackColor = Hud.BgDeep, uncheckedBorderColor = Hud.Line
            )
        )
    }
}

@Composable
private fun SliderRow(title: String, value: String, current: Float, range: ClosedFloatingPointRange<Float>, steps: Int, onChange: (Float) -> Unit) {
    var v by remember(current) { mutableStateOf(current) }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(title, color = Hud.TextDim, fontSize = 10.sp, fontFamily = Hud.Mono, letterSpacing = 2.sp, modifier = Modifier.weight(1f))
        Text("M%.1f".format((v * 2).roundToInt() / 2f), color = Hud.Cyan, fontSize = 14.sp, fontFamily = Hud.Mono, fontWeight = FontWeight.Bold)
    }
    Slider(
        value = v, onValueChange = { v = it }, onValueChangeFinished = { onChange(v) },
        valueRange = range, steps = steps,
        colors = SliderDefaults.colors(thumbColor = Hud.Cyan, activeTrackColor = Hud.Cyan, inactiveTrackColor = Hud.Line,
            activeTickColor = Hud.BgDeep, inactiveTickColor = Hud.TextDim)
    )
}
