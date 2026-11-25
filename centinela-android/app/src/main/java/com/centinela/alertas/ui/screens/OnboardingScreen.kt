package com.centinela.alertas.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.centinela.alertas.data.Category
import com.centinela.alertas.ui.components.HudButton
import com.centinela.alertas.ui.components.HudPanel
import com.centinela.alertas.ui.components.SpinnerRings
import com.centinela.alertas.ui.components.categoryIcon
import com.centinela.alertas.ui.theme.Hud

@Composable
fun OnboardingScreen(onStart: () -> Unit) {
    Column(
        Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().verticalScroll(rememberScrollState()).padding(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Spacer(Modifier.height(20.dp))
        SpinnerRings(Modifier.size(130.dp))
        Text("CENTINELA", color = Hud.Cyan, fontSize = 32.sp, fontWeight = FontWeight.Black, fontFamily = Hud.Mono, letterSpacing = 8.sp)
        Text("SISTEMA PERSONAL DE ALERTA Y PROTECCIÓN", color = Hud.TextDim, fontSize = 10.sp, fontFamily = Hud.Mono,
            letterSpacing = 2.sp, textAlign = TextAlign.Center)
        HudPanel(Modifier.fillMaxWidth(), title = "MÓDULOS") {
            val desc = mapOf(
                Category.SISMO to "Sismos en tiempo casi real, radar por distancia a ti.",
                Category.DESAPARECIDO to "Personas desaparecidas, Alerta Amber y fichas de búsqueda.",
                Category.FUGA to "Fugas de prisión y reos prófugos reportados.",
                Category.BUSCADO to "Más buscados por FBI, Interpol y policía.",
            )
            Category.entries.forEach { c ->
                Row(Modifier.padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(categoryIcon(c), null, tint = Hud.category(c), modifier = Modifier.size(22.dp))
                    Spacer(Modifier.width(12.dp))
                    Column {
                        Text(c.label, color = Hud.category(c), fontSize = 12.sp, fontWeight = FontWeight.Bold, fontFamily = Hud.Mono)
                        Text(desc[c] ?: "", color = Hud.TextDim, fontSize = 11.sp, fontFamily = Hud.Mono, lineHeight = 15.sp)
                    }
                }
            }
        }
        HudPanel(Modifier.fillMaxWidth(), title = "PERMISOS", accent = Hud.Amber) {
            Text("• UBICACIÓN: calcular la distancia de sismos y enviar tu posición en un S.O.S.\n" +
                "• NOTIFICACIONES: avisarte al instante aunque la app esté cerrada.\n\n" +
                "Tus datos no salen del teléfono. Centinela es informativa y no reemplaza alertas oficiales.",
                color = Hud.Text, fontSize = 11.sp, fontFamily = Hud.Mono, lineHeight = 16.sp)
        }
        HudButton("INICIAR SISTEMA", Modifier.fillMaxWidth(), icon = Icons.Filled.Bolt, filled = true, onClick = onStart)
        Spacer(Modifier.height(20.dp))
    }
}
