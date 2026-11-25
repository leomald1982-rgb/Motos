package com.centinela.alertas.ui

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ViewList
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Radar
import androidx.compose.material.icons.filled.Sos
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.centinela.alertas.MainActivity
import com.centinela.alertas.data.Category
import com.centinela.alertas.ui.components.BlinkDot
import com.centinela.alertas.ui.components.HudBackground
import com.centinela.alertas.ui.components.SpinnerRings
import com.centinela.alertas.ui.components.hudFrame
import com.centinela.alertas.ui.screens.DetailScreen
import com.centinela.alertas.ui.screens.FeedFilter
import com.centinela.alertas.ui.screens.FeedScreen
import com.centinela.alertas.ui.screens.HudScreen
import com.centinela.alertas.ui.screens.OnboardingScreen
import com.centinela.alertas.ui.screens.SettingsScreen
import com.centinela.alertas.ui.screens.SosScreen
import com.centinela.alertas.ui.theme.Hud
import com.centinela.alertas.util.TimeFmt
import kotlinx.coroutines.delay

private data class Tab(val route: String, val label: String, val icon: ImageVector)

private val tabs = listOf(
    Tab("hud", "HUD", Icons.Filled.Radar),
    Tab("feed", "ALERTAS", Icons.AutoMirrored.Filled.ViewList),
    Tab("sos", "S.O.S", Icons.Filled.Sos),
    Tab("settings", "AJUSTES", Icons.Filled.Tune),
)

@Composable
fun CentinelaRoot(vm: MainViewModel, pendingAlertId: String?, onPendingConsumed: () -> Unit) {
    val state by vm.state.collectAsStateWithLifecycle()
    val settings by vm.settings.collectAsStateWithLifecycle()
    val saved by vm.saved.collectAsStateWithLifecycle()
    val onboarded by vm.onboarded.collectAsStateWithLifecycle()
    val nav = rememberNavController()
    val savedIds = remember(saved) { saved.map { it.id }.toSet() }

    val permLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        vm.refreshLocation(); vm.refresh()
    }

    LaunchedEffect(pendingAlertId, state.alerts.size) {
        val id = pendingAlertId ?: return@LaunchedEffect
        if (vm.find(id) != null) { nav.navigate("detail/${Uri.encode(id)}"); onPendingConsumed() }
    }

    HudBackground {
        when (onboarded) {
            null -> {}
            false -> OnboardingScreen {
                permLauncher.launch(MainActivity.runtimePermissions)
                vm.finishOnboarding()
            }
            true -> Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) {
                TopBar(state)
                Box(Modifier.weight(1f)) {
                    NavHost(nav, startDestination = "hud") {
                        composable("hud") {
                            HudScreen(
                                state, settings, savedIds,
                                onRefresh = vm::refresh,
                                onLocate = { permLauncher.launch(MainActivity.runtimePermissions) },
                                onOpen = { nav.navigate("detail/${Uri.encode(it.id)}") },
                                onCategory = { c -> nav.navigateTab("feed?f=${c.toFilter().name}") },
                                onSos = { nav.navigateTab("sos") },
                            )
                        }
                        composable("feed?f={f}", arguments = listOf(navArgument("f") { type = NavType.StringType; defaultValue = FeedFilter.TODO.name })) { e ->
                            val f = runCatching { FeedFilter.valueOf(e.arguments?.getString("f") ?: "") }.getOrDefault(FeedFilter.TODO)
                            FeedScreen(state, saved, f, onRefresh = vm::refresh, onOpen = { nav.navigate("detail/${Uri.encode(it.id)}") })
                        }
                        composable("sos") { SosScreen(settings, state.location) { vm.updateSettings(it) } }
                        composable("settings") { SettingsScreen(settings) { vm.updateSettings(it) } }
                        composable("detail/{id}", arguments = listOf(navArgument("id") { type = NavType.StringType })) { e ->
                            val id = Uri.decode(e.arguments?.getString("id") ?: "")
                            val alert = vm.find(id)
                            DetailScreen(alert, state.location ?: settings.location, settings, id in savedIds,
                                onBack = { nav.popBackStack() }, onToggleSave = { vm.toggleSaved(it) })
                        }
                    }
                    CriticalBanner(state.banner, Modifier.align(Alignment.TopCenter), onDismiss = vm::dismissBanner) { b ->
                        vm.dismissBanner(); nav.navigate("detail/${Uri.encode(b.id)}")
                    }
                }
                BottomBar(nav)
            }
        }
    }
}

private fun Category.toFilter() = when (this) {
    Category.SISMO -> FeedFilter.SIS
    Category.DESAPARECIDO -> FeedFilter.DES
    Category.FUGA -> FeedFilter.FUG
    Category.BUSCADO -> FeedFilter.BUS
}

private fun NavHostController.navigateTab(route: String) = navigate(route) {
    popUpTo("hud") { saveState = false }
    launchSingleTop = true
}

@Composable
private fun TopBar(state: UiState) {
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) { while (true) { now = System.currentTimeMillis(); delay(1000) } }
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        SpinnerRings(Modifier.size(40.dp))
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text("CENTINELA", color = Hud.Cyan, fontSize = 20.sp, fontWeight = FontWeight.Black, fontFamily = Hud.Mono, letterSpacing = 5.sp)
            Text("SISTEMA DE ALERTA TÁCTICA", color = Hud.TextDim, fontSize = 8.sp, fontFamily = Hud.Mono, letterSpacing = 2.5.sp)
        }
        Column(horizontalAlignment = Alignment.End) {
            Text(TimeFmt.clock(now), color = Hud.Text, fontSize = 15.sp, fontFamily = Hud.Mono, fontWeight = FontWeight.Bold)
            Row(verticalAlignment = Alignment.CenterVertically) {
                val ok = state.status.isEmpty() || state.status.any { it.ok && it.message == null }
                BlinkDot(if (state.loading) Hud.Amber else if (ok) Hud.Green else Hud.Red, 6.dp)
                Spacer(Modifier.width(5.dp))
                Text(if (state.loading) "ESCANEANDO" else if (ok) "EN LÍNEA" else "SIN RED", color = Hud.TextDim, fontSize = 8.sp,
                    fontFamily = Hud.Mono, letterSpacing = 1.5.sp)
            }
        }
    }
    Box(Modifier.fillMaxWidth().height(1.dp).background(Brush.horizontalGradient(listOf(Color.Transparent, Hud.Cyan, Color.Transparent))))
}

@Composable
private fun BottomBar(nav: NavHostController) {
    val entry by nav.currentBackStackEntryAsState()
    val current = entry?.destination?.route?.substringBefore("?")?.substringBefore("/")
    Column {
        Box(Modifier.fillMaxWidth().height(1.dp).background(Brush.horizontalGradient(listOf(Color.Transparent, Hud.Cyan.copy(alpha = 0.6f), Color.Transparent))))
        Row(
            Modifier.fillMaxWidth().background(Hud.BgDeep.copy(alpha = 0.85f)).padding(horizontal = 8.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            tabs.forEach { t ->
                val sel = current == t.route
                val col = if (t.route == "sos") Hud.Red else Hud.Cyan
                Column(
                    Modifier.weight(1f)
                        .then(if (sel) Modifier.hudFrame(col, cut = 6.dp, fill = col.copy(alpha = 0.12f)) else Modifier)
                        .clickable { nav.navigateTab(if (t.route == "feed") "feed?f=${FeedFilter.TODO.name}" else t.route) }
                        .padding(vertical = 7.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(t.icon, t.label, tint = if (sel) col else col.copy(alpha = 0.5f), modifier = Modifier.size(22.dp))
                    Text(t.label, color = if (sel) col else Hud.TextDim, fontSize = 9.sp, fontFamily = Hud.Mono, letterSpacing = 1.5.sp)
                }
            }
        }
    }
}

@Composable
private fun CriticalBanner(banner: com.centinela.alertas.data.Alert?, modifier: Modifier, onDismiss: () -> Unit, onOpen: (com.centinela.alertas.data.Alert) -> Unit) {
    AnimatedVisibility(
        visible = banner != null,
        enter = slideInVertically { -it } + fadeIn(), exit = slideOutVertically { -it } + fadeOut(),
        modifier = modifier
    ) {
        banner?.let { b ->
            Row(
                Modifier.padding(12.dp).fillMaxWidth().hudFrame(Hud.Red, fill = Color(0xEE2A0610))
                    .clickable { onOpen(b) }
                    .padding(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                BlinkDot(Hud.Red, 10.dp, 400)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text("⚠ ALERTA CRÍTICA · ${b.category.label}", color = Hud.Red, fontSize = 10.sp,
                        fontFamily = Hud.Mono, letterSpacing = 2.sp, fontWeight = FontWeight.Bold)
                    Text(b.title, color = Hud.Text, fontSize = 13.sp, fontFamily = Hud.Mono, maxLines = 2, overflow = TextOverflow.Ellipsis)
                }
                Icon(Icons.Filled.Close, "Cerrar", tint = Hud.TextDim, modifier = Modifier.size(36.dp).clickable(onClick = onDismiss).padding(8.dp))
            }
        }
    }
}
