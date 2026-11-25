package com.centinela.alertas.ui.screens

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Campaign
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.FlashlightOn
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.ShareLocation
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.centinela.alertas.data.Contact
import com.centinela.alertas.data.GeoPoint
import com.centinela.alertas.data.Settings
import com.centinela.alertas.ui.components.HudButton
import com.centinela.alertas.ui.components.HudPanel
import com.centinela.alertas.ui.components.hudFrame
import com.centinela.alertas.ui.components.segArc
import com.centinela.alertas.ui.theme.Hud
import com.centinela.alertas.util.Emergency
import com.centinela.alertas.util.LocationHelper
import com.centinela.alertas.util.Siren
import com.centinela.alertas.util.SosTorch
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

@Composable
fun SosScreen(settings: Settings, location: GeoPoint?, onUpdate: ((Settings) -> Settings) -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val siren = remember { Siren() }
    val torch = remember { SosTorch(ctx.applicationContext) }
    var sirenOn by remember { mutableStateOf(false) }
    var torchOn by remember { mutableStateOf(false) }
    var showAdd by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf<String?>(null) }
    DisposableEffect(Unit) { onDispose { siren.stop(); torch.stop() } }

    fun triggerSos() {
        scope.launch {
            status = "OBTENIENDO POSICIÓN…"
            Emergency.vibrate(ctx, longArrayOf(0, 120, 80, 120, 80, 400))
            val loc = LocationHelper.current(ctx) ?: location ?: settings.location
            val body = buildString {
                append(settings.sosMessage)
                if (loc != null) append(" ").append(LocationHelper.mapsLink(loc)) else append(" (ubicación no disponible)")
                append(" — Enviado con Centinela")
            }
            if (settings.contacts.isEmpty()) {
                status = "SIN CONTACTOS · COMPARTIENDO MENSAJE"
                Emergency.share(ctx, body, "Enviar S.O.S")
            } else {
                status = "S.O.S LISTO PARA ${settings.contacts.size} CONTACTO(S)"
                Emergency.sms(ctx, settings.contacts, body)
            }
        }
    }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 14.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("MANTÉN PRESIONADO 2 S PARA ACTIVAR", color = Hud.TextDim, fontSize = 11.sp, fontFamily = Hud.Mono, letterSpacing = 2.sp)
        HoldSosButton(onTriggered = ::triggerSos)
        status?.let { Text("▸ $it", color = Hud.Amber, fontSize = 11.sp, fontFamily = Hud.Mono) }

        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            HudButton("LLAMAR ${settings.region.emergency}", Modifier.weight(1f), accent = Hud.Red, icon = Icons.Filled.Call, filled = true) {
                Emergency.dial(ctx, settings.region.emergency)
            }
            HudButton("UBICACIÓN", Modifier.weight(1f), icon = Icons.Filled.ShareLocation) {
                scope.launch {
                    val loc = LocationHelper.current(ctx) ?: location
                    if (loc != null) Emergency.share(ctx, "Mi ubicación actual: ${LocationHelper.mapsLink(loc)}", "Compartir ubicación")
                    else status = "ACTIVA EL GPS Y CONCEDE PERMISO DE UBICACIÓN"
                }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            HudButton(if (sirenOn) "DETENER SIRENA" else "SIRENA", Modifier.weight(1f), accent = Hud.Amber,
                icon = Icons.Filled.Campaign, filled = sirenOn) {
                if (sirenOn) siren.stop() else siren.start(); sirenOn = !sirenOn
            }
            HudButton(if (torchOn) "APAGAR LUZ" else "LUZ S.O.S", Modifier.weight(1f), accent = Hud.Amber,
                icon = Icons.Filled.FlashlightOn, filled = torchOn, enabled = torch.available) {
                if (torchOn) torch.stop() else torch.start(); torchOn = !torchOn
            }
        }

        HudPanel(Modifier.fillMaxWidth(), title = "CONTACTOS DE EMERGENCIA", accent = Hud.Green, trailing = "${settings.contacts.size}") {
            if (settings.contacts.isEmpty()) {
                Text("Agrega personas de confianza. Al activar S.O.S se abrirá un SMS con tu ubicación para todas ellas.",
                    color = Hud.TextDim, fontSize = 11.sp, fontFamily = Hud.Mono, lineHeight = 16.sp)
            }
            settings.contacts.forEachIndexed { i, c ->
                Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("%02d".format(i + 1), color = Hud.Green, fontSize = 11.sp, fontFamily = Hud.Mono)
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) {
                        Text(c.name.uppercase(), color = Hud.Text, fontSize = 13.sp, fontFamily = Hud.Mono, fontWeight = FontWeight.Bold)
                        Text(c.phone, color = Hud.TextDim, fontSize = 11.sp, fontFamily = Hud.Mono)
                    }
                    Icon(Icons.Filled.Call, "Llamar", tint = Hud.Cyan, modifier = Modifier.size(40.dp).clickable { Emergency.dial(ctx, c.phone) }.padding(9.dp))
                    Icon(Icons.Filled.Delete, "Eliminar", tint = Hud.Red, modifier = Modifier.size(40.dp).clickable {
                        onUpdate { s -> s.copy(contacts = s.contacts.filterIndexed { j, _ -> j != i }) }
                    }.padding(9.dp))
                }
            }
            Spacer(Modifier.height(8.dp))
            HudButton("AGREGAR CONTACTO", Modifier.fillMaxWidth(), accent = Hud.Green, icon = Icons.Filled.PersonAdd) { showAdd = true }
        }

        HudPanel(Modifier.fillMaxWidth(), title = "MENSAJE S.O.S", accent = Hud.Blue) {
            var msg by remember(settings.sosMessage) { mutableStateOf(settings.sosMessage) }
            OutlinedTextField(
                value = msg, onValueChange = { msg = it; onUpdate { s -> s.copy(sosMessage = it) } },
                modifier = Modifier.fillMaxWidth(), colors = hudFieldColors(),
                textStyle = androidx.compose.ui.text.TextStyle(fontFamily = Hud.Mono, fontSize = 13.sp, color = Hud.Text)
            )
            Text("Se añade automáticamente el enlace de tu ubicación.", color = Hud.TextDim, fontSize = 10.sp, fontFamily = Hud.Mono,
                modifier = Modifier.padding(top = 6.dp))
        }
        Spacer(Modifier.height(10.dp))
    }

    if (showAdd) {
        var name by remember { mutableStateOf("") }
        var phone by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showAdd = false },
            containerColor = Hud.Panel,
            title = { Text("NUEVO CONTACTO", fontFamily = Hud.Mono, color = Hud.Cyan, letterSpacing = 2.sp) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(name, { name = it }, label = { Text("Nombre", fontFamily = Hud.Mono) }, singleLine = true, colors = hudFieldColors())
                    OutlinedTextField(phone, { phone = it }, label = { Text("Teléfono", fontFamily = Hud.Mono) }, singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone), colors = hudFieldColors())
                }
            },
            confirmButton = {
                TextButton(enabled = name.isNotBlank() && phone.count { it.isDigit() } >= 7, onClick = {
                    onUpdate { s -> s.copy(contacts = s.contacts + Contact(name.trim(), phone.trim())) }
                    showAdd = false
                }) { Text("GUARDAR", color = Hud.Green, fontFamily = Hud.Mono) }
            },
            dismissButton = { TextButton(onClick = { showAdd = false }) { Text("CANCELAR", color = Hud.TextDim, fontFamily = Hud.Mono) } }
        )
    }
}

@Composable
fun hudFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedBorderColor = Hud.Cyan, unfocusedBorderColor = Hud.Line, focusedTextColor = Hud.Text, unfocusedTextColor = Hud.Text,
    cursorColor = Hud.Cyan, focusedLabelColor = Hud.Cyan, unfocusedLabelColor = Hud.TextDim,
    focusedContainerColor = Hud.BgDeep.copy(alpha = 0.5f), unfocusedContainerColor = Hud.BgDeep.copy(alpha = 0.5f),
)

@Composable
private fun HoldSosButton(onTriggered: () -> Unit) {
    val scope = rememberCoroutineScope()
    val progress = remember { Animatable(0f) }
    val t = rememberInfiniteTransition(label = "sos")
    val pulse by t.animateFloat(0.85f, 1.1f, infiniteRepeatable(tween(1100), RepeatMode.Reverse), label = "p")
    val spin by t.animateFloat(0f, 360f, infiniteRepeatable(tween(7000, easing = LinearEasing)), label = "s")
    var job by remember { mutableStateOf<Job?>(null) }

    Box(
        Modifier.size(250.dp).pointerInput(Unit) {
            awaitEachGesture {
                awaitFirstDown()
                job?.cancel()
                job = scope.launch {
                    progress.snapTo(0f)
                    progress.animateTo(1f, tween(2000, easing = LinearEasing))
                    onTriggered()
                    progress.animateTo(0f, tween(400))
                }
                waitForUpOrCancellation()
                if (progress.value < 1f) {
                    job?.cancel()
                    scope.launch { progress.animateTo(0f, tween(250)) }
                }
            }
        },
        contentAlignment = Alignment.Center
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val s = size.minDimension
            val c = center
            drawCircle(Brush.radialGradient(listOf(Hud.Red.copy(alpha = 0.35f), Color.Transparent), c, s / 2 * pulse), s / 2 * pulse)
            rotate(spin) { segArc(Hud.Red.copy(alpha = 0.7f), s * 0.47f, 2.dp.toPx(), 12, 18f) }
            rotate(-spin * 1.5f) { segArc(Hud.Amber.copy(alpha = 0.5f), s * 0.41f, 1.5.dp.toPx(), 4, 50f) }
            val r = s * 0.35f
            drawArc(Hud.Line, 0f, 360f, false, Offset(c.x - r, c.y - r), Size(r * 2, r * 2), style = Stroke(8.dp.toPx()))
            drawArc(Hud.Red, -90f, 360f * progress.value, false, Offset(c.x - r, c.y - r), Size(r * 2, r * 2),
                style = Stroke(8.dp.toPx(), cap = StrokeCap.Round))
            drawCircle(Hud.Red.copy(alpha = 0.18f + 0.4f * progress.value), s * 0.29f)
            drawCircle(Hud.Red, s * 0.29f, style = Stroke(2.dp.toPx()))
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("S.O.S", color = Hud.Text, fontSize = 40.sp, fontWeight = FontWeight.Black, fontFamily = Hud.Mono, letterSpacing = 4.sp)
            Text(if (progress.value > 0f) "${(progress.value * 100).toInt()}%" else "EMERGENCIA", color = Hud.Red,
                fontSize = 11.sp, fontFamily = Hud.Mono, letterSpacing = 3.sp)
        }
    }
}
