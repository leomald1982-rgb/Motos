package com.centinela.alertas.data

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.doublePreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import org.json.JSONArray
import org.json.JSONObject

private val Context.dataStore by preferencesDataStore(name = "centinela")

class SettingsStore(private val context: Context) {
    private object K {
        val enabled = stringSetPreferencesKey("enabled")
        val notify = stringSetPreferencesKey("notify")
        val minMag = floatPreferencesKey("min_mag")
        val notifyMag = floatPreferencesKey("notify_mag")
        val radius = intPreferencesKey("radius")
        val region = stringPreferencesKey("region")
        val keyword = stringPreferencesKey("keyword")
        val notifOn = booleanPreferencesKey("notif_on")
        val live = booleanPreferencesKey("live")
        val liveSec = intPreferencesKey("live_sec")
        val bgMin = intPreferencesKey("bg_min")
        val contacts = stringPreferencesKey("contacts")
        val sosMsg = stringPreferencesKey("sos_msg")
        val lat = doublePreferencesKey("lat")
        val lon = doublePreferencesKey("lon")
        val seen = stringSetPreferencesKey("seen")
        val saved = stringPreferencesKey("saved")
        val onboarded = booleanPreferencesKey("onboarded")
    }

    private fun Set<String>.toCats() = mapNotNull { runCatching { Category.valueOf(it) }.getOrNull() }.toSet()

    val settings: Flow<Settings> = context.dataStore.data.map { p -> p.toSettings() }

    private fun Preferences.toSettings(): Settings {
        val d = Settings()
        return Settings(
            enabledCategories = this[K.enabled]?.toCats() ?: d.enabledCategories,
            notifyCategories = this[K.notify]?.toCats() ?: d.notifyCategories,
            minMagnitude = this[K.minMag] ?: d.minMagnitude,
            notifyMagnitude = this[K.notifyMag] ?: d.notifyMagnitude,
            radiusKm = this[K.radius] ?: d.radiusKm,
            regionCode = this[K.region] ?: d.regionCode,
            localKeyword = this[K.keyword] ?: d.localKeyword,
            notificationsEnabled = this[K.notifOn] ?: d.notificationsEnabled,
            liveMode = this[K.live] ?: d.liveMode,
            liveRefreshSec = this[K.liveSec] ?: d.liveRefreshSec,
            backgroundMinutes = this[K.bgMin] ?: d.backgroundMinutes,
            contacts = this[K.contacts]?.let { parseContacts(it) } ?: d.contacts,
            sosMessage = this[K.sosMsg] ?: d.sosMessage,
            lastLat = this[K.lat],
            lastLon = this[K.lon],
        )
    }

    suspend fun current(): Settings = settings.first()

    suspend fun update(block: (Settings) -> Settings) {
        context.dataStore.edit { p ->
            val s = block(p.toSettings())
            p[K.enabled] = s.enabledCategories.map { it.name }.toSet()
            p[K.notify] = s.notifyCategories.map { it.name }.toSet()
            p[K.minMag] = s.minMagnitude
            p[K.notifyMag] = s.notifyMagnitude
            p[K.radius] = s.radiusKm
            p[K.region] = s.regionCode
            p[K.keyword] = s.localKeyword
            p[K.notifOn] = s.notificationsEnabled
            p[K.live] = s.liveMode
            p[K.liveSec] = s.liveRefreshSec
            p[K.bgMin] = s.backgroundMinutes
            p[K.contacts] = JSONArray().apply {
                s.contacts.forEach { put(JSONObject().put("n", it.name).put("p", it.phone)) }
            }.toString()
            p[K.sosMsg] = s.sosMessage
            if (s.lastLat != null && s.lastLon != null) { p[K.lat] = s.lastLat; p[K.lon] = s.lastLon }
        }
    }

    private fun parseContacts(s: String): List<Contact> = runCatching {
        val a = JSONArray(s)
        (0 until a.length()).map { a.getJSONObject(it).let { o -> Contact(o.getString("n"), o.getString("p")) } }
    }.getOrDefault(emptyList())

    /* ---- ids vistos (para detectar alertas nuevas) ---- */
    suspend fun seenIds(): Set<String> = context.dataStore.data.first()[K.seen] ?: emptySet()

    suspend fun addSeen(ids: Collection<String>, current: Collection<String>) {
        context.dataStore.edit { p ->
            // Conserva solo ids de alertas aún vigentes + nuevas, para que el set no crezca sin límite.
            val keep = (p[K.seen] ?: emptySet()).intersect(current.toSet())
            p[K.seen] = (keep + ids).toList().takeLast(3000).toSet()
        }
    }

    /* ---- onboarding ---- */
    val onboarded: Flow<Boolean> = context.dataStore.data.map { it[K.onboarded] ?: false }
    suspend fun setOnboarded() { context.dataStore.edit { it[K.onboarded] = true } }

    /* ---- alertas guardadas ---- */
    val saved: Flow<List<Alert>> = context.dataStore.data.map { p ->
        p[K.saved]?.let { s ->
            runCatching { JSONArray(s).let { a -> (0 until a.length()).map { Alert.fromJson(a.getJSONObject(it)) } } }.getOrNull()
        } ?: emptyList()
    }

    suspend fun toggleSaved(alert: Alert) {
        context.dataStore.edit { p ->
            val list = p[K.saved]?.let { s ->
                runCatching { JSONArray(s).let { a -> (0 until a.length()).map { Alert.fromJson(a.getJSONObject(it)) } } }.getOrNull()
            }?.toMutableList() ?: mutableListOf()
            if (list.any { it.id == alert.id }) list.removeAll { it.id == alert.id } else list.add(0, alert)
            p[K.saved] = JSONArray().apply { list.forEach { put(it.toJson()) } }.toString()
        }
    }
}
