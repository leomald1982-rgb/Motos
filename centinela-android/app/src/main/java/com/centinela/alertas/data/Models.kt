package com.centinela.alertas.data

import org.json.JSONObject

enum class Category(val label: String, val code: String) {
    SISMO("SISMOS", "SIS"),
    DESAPARECIDO("DESAPARECIDOS", "DES"),
    FUGA("FUGAS DE PRISIÓN", "FUG"),
    BUSCADO("BUSCADOS", "BUS");
}

enum class Severity(val label: String, val weight: Int) {
    INFO("INFO", 1),
    ADVERTENCIA("ADVERTENCIA", 3),
    CRITICA("CRÍTICA", 8);
}

data class GeoPoint(val lat: Double, val lon: Double)

data class Alert(
    val id: String,
    val category: Category,
    val title: String,
    val summary: String,
    val details: String = "",
    val time: Long,
    val source: String,
    val url: String? = null,
    val imageUrl: String? = null,
    val lat: Double? = null,
    val lon: Double? = null,
    val magnitude: Double? = null,
    val depthKm: Double? = null,
    val severity: Severity = Severity.INFO,
    val reward: String? = null,
    val isPerson: Boolean = false,
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("id", id); put("category", category.name); put("title", title)
        put("summary", summary); put("details", details); put("time", time)
        put("source", source); put("url", url ?: ""); put("imageUrl", imageUrl ?: "")
        lat?.let { put("lat", it) }; lon?.let { put("lon", it) }
        magnitude?.let { put("magnitude", it) }; depthKm?.let { put("depthKm", it) }
        put("severity", severity.name); put("reward", reward ?: ""); put("isPerson", isPerson)
    }

    companion object {
        fun fromJson(o: JSONObject): Alert = Alert(
            id = o.getString("id"),
            category = Category.valueOf(o.getString("category")),
            title = o.optString("title"),
            summary = o.optString("summary"),
            details = o.optString("details"),
            time = o.optLong("time"),
            source = o.optString("source"),
            url = o.optString("url").ifBlank { null },
            imageUrl = o.optString("imageUrl").ifBlank { null },
            lat = if (o.has("lat")) o.getDouble("lat") else null,
            lon = if (o.has("lon")) o.getDouble("lon") else null,
            magnitude = if (o.has("magnitude")) o.getDouble("magnitude") else null,
            depthKm = if (o.has("depthKm")) o.getDouble("depthKm") else null,
            severity = runCatching { Severity.valueOf(o.optString("severity")) }.getOrDefault(Severity.INFO),
            reward = o.optString("reward").ifBlank { null },
            isPerson = o.optBoolean("isPerson"),
        )
    }
}

data class Contact(val name: String, val phone: String)

/** Configuración regional: idioma de noticias, país para Interpol y números de emergencia. */
data class Region(
    val code: String,
    val name: String,
    val hl: String,
    val gl: String,
    val ceid: String,
    val english: Boolean = false,
    val emergency: String = "911",
    val anonymousTip: String? = null,
    val center: GeoPoint,
)

object Regions {
    val all = listOf(
        Region("MX", "México", "es-419", "MX", "MX:es-419", emergency = "911", anonymousTip = "089", center = GeoPoint(19.43, -99.13)),
        Region("US", "Estados Unidos", "en-US", "US", "US:en", english = true, emergency = "911", center = GeoPoint(39.8, -98.6)),
        Region("ES", "España", "es", "ES", "ES:es", emergency = "112", anonymousTip = "091", center = GeoPoint(40.42, -3.70)),
        Region("CO", "Colombia", "es-419", "CO", "CO:es-419", emergency = "123", center = GeoPoint(4.71, -74.07)),
        Region("AR", "Argentina", "es-419", "AR", "AR:es-419", emergency = "911", center = GeoPoint(-34.6, -58.38)),
        Region("CL", "Chile", "es-419", "CL", "CL:es-419", emergency = "133", center = GeoPoint(-33.45, -70.66)),
        Region("PE", "Perú", "es-419", "PE", "PE:es-419", emergency = "105", center = GeoPoint(-12.05, -77.04)),
        Region("GT", "Guatemala", "es-419", "GT", "GT:es-419", emergency = "110", center = GeoPoint(14.63, -90.51)),
        Region("EC", "Ecuador", "es-419", "EC", "EC:es-419", emergency = "911", center = GeoPoint(-0.18, -78.47)),
        Region("VE", "Venezuela", "es-419", "VE", "VE:es-419", emergency = "911", center = GeoPoint(10.48, -66.9)),
    )

    fun byCode(code: String) = all.firstOrNull { it.code == code } ?: all.first()
}

data class Settings(
    val enabledCategories: Set<Category> = Category.entries.toSet(),
    val notifyCategories: Set<Category> = Category.entries.toSet(),
    val minMagnitude: Float = 3.5f,
    val notifyMagnitude: Float = 5.0f,
    val radiusKm: Int = 1500,        // 0 = mundial
    val regionCode: String = "MX",
    val localKeyword: String = "",
    val notificationsEnabled: Boolean = true,
    val liveMode: Boolean = true,
    val liveRefreshSec: Int = 60,
    val backgroundMinutes: Int = 15,
    val contacts: List<Contact> = emptyList(),
    val sosMessage: String = "¡EMERGENCIA! Necesito ayuda urgente. Mi ubicación es:",
    val lastLat: Double? = null,
    val lastLon: Double? = null,
) {
    val region: Region get() = Regions.byCode(regionCode)
    val location: GeoPoint? get() = if (lastLat != null && lastLon != null) GeoPoint(lastLat, lastLon) else null
}

data class SourceStatus(val name: String, val ok: Boolean, val count: Int, val message: String? = null)
