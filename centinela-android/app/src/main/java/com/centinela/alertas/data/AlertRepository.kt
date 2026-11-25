package com.centinela.alertas.data

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import org.json.JSONArray
import java.io.File

data class FetchResult(val alerts: List<Alert>, val status: List<SourceStatus>, val fresh: List<Alert>)

class AlertRepository(private val context: Context, private val store: SettingsStore) {

    private val sources: List<AlertSource> = listOf(SsnSource(), UsgsSource(), EmscSource(), FbiSource(), InterpolSource(), NewsSource())
    val sourceNames: List<String> = sources.map { it.name }

    private val cacheFile get() = File(context.filesDir, "alerts_cache.json")

    suspend fun loadCache(): List<Alert> = withContext(Dispatchers.IO) {
        runCatching {
            val a = JSONArray(cacheFile.readText())
            (0 until a.length()).map { Alert.fromJson(a.getJSONObject(it)) }
        }.getOrDefault(emptyList())
    }

    private suspend fun saveCache(list: List<Alert>) = withContext(Dispatchers.IO) {
        runCatching { cacheFile.writeText(JSONArray().apply { list.forEach { put(it.toJson()) } }.toString()) }
    }

    /**
     * Consulta todas las fuentes activas en paralelo. Si una falla se conservan sus alertas en caché.
     * Devuelve además las alertas "nuevas" (no vistas antes) para generar notificaciones.
     */
    suspend fun refresh(): FetchResult = coroutineScope {
        val settings = store.current()
        val previous = loadCache()
        val prevById = previous.associateBy { it.id }
        val now = System.currentTimeMillis()

        val results = sources.map { src ->
            async {
                if (!src.isActive(settings)) return@async Triple(src.name, null as List<Alert>?, SourceStatus(src.name, true, 0, "INACTIVO"))
                try {
                    val list = src.fetch(settings)
                    Triple(src.name, list, SourceStatus(src.name, true, list.size))
                } catch (e: Exception) {
                    Triple(src.name, null, SourceStatus(src.name, false, 0, e.message ?: e.javaClass.simpleName))
                }
            }
        }.awaitAll()

        val merged = LinkedHashMap<String, Alert>()
        for ((name, list, status) in results) {
            val items = list ?: if (!status.ok) previous.filter { it.sourceKey() == name } else emptyList()
            for (a in items) {
                // Fuentes sin fecha (Interpol): se fecha al momento en que Centinela la detecta por primera vez.
                val fixed = if (a.time <= 0L) a.copy(time = prevById[a.id]?.time?.takeIf { it > 0 } ?: now) else a
                if (fixed.category in settings.enabledCategories) merged.putIfAbsent(fixed.id, fixed)
            }
        }
        // Prioridad SSN > USGS > EMSC (orden de las fuentes) al fusionar el mismo sismo.
        val quakes = dedupeQuakes(merged.values.filter { it.category == Category.SISMO })
        val all = (quakes + merged.values.filter { it.category != Category.SISMO }).sortedByDescending { it.time }.take(600)
        saveCache(all)

        val seen = store.seenIds()
        val fresh = if (seen.isEmpty()) emptyList() else all.filter { it.id !in seen }
        store.addSeen(all.map { it.id }, all.map { it.id })
        FetchResult(all, results.map { it.third }, fresh)
    }

    private fun Alert.sourceKey(): String = when {
        id.startsWith("usgs:") -> "USGS"
        id.startsWith("ssn:") -> "SSN MX"
        id.startsWith("emsc:") -> "EMSC"
        id.startsWith("fbi:") -> "FBI"
        id.startsWith("interpol:") -> "INTERPOL"
        else -> "NOTICIAS"
    }

    companion object {
        /** ¿Debe esta alerta nueva generar una notificación según la configuración? */
        fun shouldNotify(a: Alert, s: Settings): Boolean {
            if (!s.notificationsEnabled || a.category !in s.notifyCategories) return false
            if (System.currentTimeMillis() - a.time > 3 * 86_400_000L) return false // evita avisar de cosas viejas
            return when (a.category) {
                Category.SISMO -> (a.magnitude ?: 0.0) >= s.notifyMagnitude
                else -> a.severity != Severity.INFO
            }
        }
    }
}
