package com.centinela.alertas

import androidx.compose.runtime.Composable
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import com.centinela.alertas.data.Alert
import com.centinela.alertas.data.Category
import com.centinela.alertas.data.Contact
import com.centinela.alertas.data.EmscSource
import com.centinela.alertas.data.FbiSource
import com.centinela.alertas.data.GeoPoint
import com.centinela.alertas.data.NewsSource
import com.centinela.alertas.data.Settings
import com.centinela.alertas.data.SourceStatus
import com.centinela.alertas.data.SsnSource
import com.centinela.alertas.data.UsgsSource
import com.centinela.alertas.data.dedupeQuakes
import com.centinela.alertas.ui.UiState
import com.centinela.alertas.ui.components.HudBackground
import com.centinela.alertas.ui.screens.DetailScreen
import com.centinela.alertas.ui.screens.FeedFilter
import com.centinela.alertas.ui.screens.FeedScreen
import com.centinela.alertas.ui.screens.HudScreen
import com.centinela.alertas.ui.screens.OnboardingScreen
import com.centinela.alertas.ui.screens.SettingsScreen
import com.centinela.alertas.ui.screens.SosScreen
import com.centinela.alertas.ui.theme.CentinelaTheme
import com.github.takahirom.roborazzi.captureRoboImage
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(application = android.app.Application::class, qualifiers = "w400dp-h880dp-xxhdpi", sdk = [34])
class ScreensShotTest {
    @get:Rule val rule = createComposeRule()

    private val me = GeoPoint(19.43, -99.13)
    private val settings = Settings(lastLat = me.lat, lastLon = me.lon,
        contacts = listOf(Contact("Mamá", "+52 55 1234 5678"), Contact("Carlos", "+52 81 8765 4321")))

    private val alerts: List<Alert> by lazy {
        runBlocking {
            val q = dedupeQuakes(runCatching { SsnSource().fetch(settings) }.getOrDefault(emptyList()) +
                runCatching { UsgsSource().fetch(settings) }.getOrDefault(emptyList()) +
                runCatching { EmscSource().fetch(settings) }.getOrDefault(emptyList()))
            val other = runCatching { FbiSource().fetch(settings) }.getOrDefault(emptyList()) +
                runCatching { NewsSource().fetch(settings) }.getOrDefault(emptyList())
            (q + other).sortedByDescending { it.time }
        }
    }

    private fun state() = UiState(
        alerts = alerts, lastUpdate = System.currentTimeMillis(), location = me,
        status = listOf(SourceStatus("SSN MX", true, alerts.count { it.source.startsWith("SSN") }),
            SourceStatus("USGS", true, alerts.count { it.source == "USGS" }), SourceStatus("EMSC", true, alerts.count { it.source == "EMSC" }),
            SourceStatus("FBI", true, alerts.count { it.source.startsWith("FBI") }), SourceStatus("INTERPOL", false, 0, "HTTP 403"),
            SourceStatus("NOTICIAS", true, alerts.count { it.id.startsWith("news") }))
    )

    private fun shot(name: String, content: @Composable () -> Unit) {
        rule.mainClock.autoAdvance = false
        rule.setContent { CentinelaTheme { HudBackground { content() } } }
        rule.mainClock.advanceTimeBy(1700)
        rule.onRoot().captureRoboImage("build/shots/$name.png")
    }

    @Test fun hud() = shot("1_hud") { HudScreen(state(), settings, emptySet(), {}, {}, {}, {}, {}) }
    @Test fun feed() = shot("2_alertas") { FeedScreen(state(), emptyList(), FeedFilter.TODO, {}, {}) }
    @Test fun feedFugas() = shot("3_fugas") { FeedScreen(state(), emptyList(), FeedFilter.FUG, {}, {}) }
    @Test fun detailPerson() = shot("4_expediente") {
        DetailScreen(alerts.first { it.isPerson && it.category == Category.BUSCADO }, me, settings, false, {}, {})
    }
    @Test fun detailQuake() = shot("5_sismo") {
        DetailScreen(alerts.filter { it.category == Category.SISMO }.maxBy { it.magnitude ?: 0.0 }, me, settings, true, {}, {})
    }
    @Test fun sos() = shot("6_sos") { SosScreen(settings, me) {} }
    @Test fun ajustes() = shot("7_ajustes") { SettingsScreen(settings) {} }
    @Test fun onboarding() = shot("0_inicio") { OnboardingScreen {} }
}
