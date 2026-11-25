package com.centinela.alertas.data

import android.util.Xml
import com.centinela.alertas.util.Geo
import org.json.JSONObject
import org.xmlpull.v1.XmlPullParser
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone
import kotlin.math.abs

/** Construye una alerta sísmica homogénea para cualquier fuente. */
fun quakeAlert(
    id: String, source: String, mag: Double, place: String, time: Long, lat: Double, lon: Double, depth: Double,
    url: String?, settings: Settings, pager: String? = null, tsunami: Boolean = false, felt: String? = null, magType: String? = null,
): Alert {
    val sev = when {
        mag >= 6.0 || pager == "orange" || pager == "red" || tsunami -> Severity.CRITICA
        mag >= 4.5 || pager == "yellow" -> Severity.ADVERTENCIA
        else -> Severity.INFO
    }
    val dist = settings.location?.let { Geo.distanceKm(it, GeoPoint(lat, lon)) }
    return Alert(
        id = id,
        category = Category.SISMO,
        title = "SISMO M${"%.1f".format(mag)} — $place",
        summary = buildString {
            append("Magnitud ${"%.1f".format(mag)} · Profundidad ${"%.0f".format(depth)} km")
            dist?.let { append(" · a ${"%.0f".format(it)} km de ti") }
        },
        details = buildString {
            appendLine("Lugar: $place")
            appendLine("Magnitud: ${"%.1f".format(mag)} ${magType ?: ""}".trim())
            appendLine("Profundidad: ${"%.1f".format(depth)} km")
            appendLine("Coordenadas: ${"%.3f".format(lat)}, ${"%.3f".format(lon)}")
            appendLine("Fuente: $source")
            felt?.let { appendLine("Reportes de percepción: $it") }
            pager?.let { appendLine("Nivel PAGER: ${it.uppercase()}") }
            if (tsunami) appendLine("⚠ POSIBLE ALERTA DE TSUNAMI — aléjate de la costa y sigue a protección civil.")
            appendLine()
            append("Recomendación: aléjate de ventanas y objetos que puedan caer, revisa fugas de gas y daños estructurales, " +
                "ten a la mano tu mochila de emergencia y prepárate para réplicas.")
        },
        time = time, source = source, url = url, lat = lat, lon = lon, magnitude = mag, depthKm = depth, severity = sev,
    )
}

/** Elimina sismos duplicados reportados por varias agencias (mismo evento: <2 min y <150 km). */
fun dedupeQuakes(list: List<Alert>): List<Alert> {
    val out = mutableListOf<Alert>()
    for (q in list) {
        val dup = out.any { o ->
            abs(o.time - q.time) < 120_000 && o.lat != null && q.lat != null &&
                Geo.distanceKm(GeoPoint(o.lat, o.lon!!), GeoPoint(q.lat, q.lon!!)) < 150
        }
        if (!dup) out += q
    }
    return out
}

/* ─────────── Servicio Sismológico Nacional (México) ─────────── */
class SsnSource : AlertSource {
    override val name = "SSN MX"
    override fun isActive(settings: Settings) = Category.SISMO in settings.enabledCategories && settings.regionCode == "MX"

    override suspend fun fetch(settings: Settings): List<Alert> {
        val xml = Http.get("http://www.ssn.unam.mx/rss/ultimos-sismos.xml")
        val p = Xml.newPullParser()
        p.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false)
        p.setInput(xml.reader())
        val fmt = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).apply { timeZone = TimeZone.getTimeZone("America/Mexico_City") }
        val out = mutableListOf<Alert>()
        var tag: String? = null
        var title = ""; var desc = ""; var link = ""; var lat = ""; var lon = ""; var inItem = false
        while (p.eventType != XmlPullParser.END_DOCUMENT) {
            when (p.eventType) {
                XmlPullParser.START_TAG -> { tag = p.name; if (tag == "item") { inItem = true; title = ""; desc = ""; link = ""; lat = ""; lon = "" } }
                XmlPullParser.TEXT, XmlPullParser.CDSECT -> if (inItem) when (tag) {
                    "title" -> title += p.text; "description" -> desc += p.text; "link" -> link += p.text
                    "geo:lat" -> lat += p.text; "geo:long" -> lon += p.text
                }
                XmlPullParser.END_TAG -> {
                    if (p.name == "item") {
                        inItem = false
                        val mag = title.substringBefore(",").trim().toDoubleOrNull()
                        val la = lat.trim().toDoubleOrNull(); val lo = lon.trim().toDoubleOrNull()
                        val date = Regex("Fecha:\\s*([0-9-]+ [0-9:]+)").find(desc)?.groupValues?.get(1)
                        val depth = Regex("Profundidad:\\s*([0-9.]+)").find(desc)?.groupValues?.get(1)?.toDoubleOrNull() ?: 0.0
                        val time = date?.let { runCatching { fmt.parse(it)?.time }.getOrNull() }
                        if (mag != null && la != null && lo != null && time != null && mag >= settings.minMagnitude) {
                            val place = title.substringAfter(",").replace(Regex("\\s+"), " ").trim()
                            val inRadius = settings.radiusKm <= 0 || settings.location == null ||
                                Geo.distanceKm(settings.location!!, GeoPoint(la, lo)) <= settings.radiusKm
                            if (inRadius) out += quakeAlert("ssn:$date:$mag", "SSN MÉXICO", mag, place, time, la, lo, depth,
                                link.trim().replace(" ", "%20"), settings)
                        }
                    }
                    tag = null
                }
            }
            p.nextToken()
        }
        return out
    }
}

/* ─────────── EMSC (Centro Sismológico Euro-Mediterráneo, cobertura global) ─────────── */
class EmscSource : AlertSource {
    override val name = "EMSC"
    override fun isActive(settings: Settings) = Category.SISMO in settings.enabledCategories

    override suspend fun fetch(settings: Settings): List<Alert> {
        val iso = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US).apply { timeZone = TimeZone.getTimeZone("UTC") }
        val parse = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US).apply { timeZone = TimeZone.getTimeZone("UTC") }
        val center = settings.location ?: settings.region.center
        val sb = StringBuilder("https://www.seismicportal.eu/fdsnws/event/1/query?format=json&orderby=time&limit=200")
        sb.append("&start=").append(iso.format(System.currentTimeMillis() - 7L * 86_400_000))
        sb.append("&minmag=").append(settings.minMagnitude)
        if (settings.radiusKm > 0) {
            sb.append("&lat=").append(center.lat).append("&lon=").append(center.lon)
            sb.append("&maxradius=").append("%.2f".format(Locale.US, (settings.radiusKm / 111.2).coerceAtMost(180.0)))
        }
        val text = Http.get(sb.toString())
        if (text.isBlank()) return emptyList() // EMSC devuelve 204 vacío si no hay eventos
        val features = JSONObject(text).getJSONArray("features")
        return (0 until features.length()).mapNotNull { i ->
            val f = features.getJSONObject(i)
            val p = f.getJSONObject("properties")
            val time = runCatching { parse.parse(p.getString("time").take(19))?.time }.getOrNull() ?: return@mapNotNull null
            val region = p.optString("flynn_region").lowercase().split(" ").joinToString(" ") { it.replaceFirstChar { c -> c.uppercase() } }
            quakeAlert(
                "emsc:" + p.optString("unid"), "EMSC", p.optDouble("mag", 0.0), region, time,
                p.getDouble("lat"), p.getDouble("lon"), abs(p.optDouble("depth", 0.0)),
                "https://www.seismicportal.eu/eventdetails.html?unid=${p.optString("unid")}", settings, magType = p.optString("magtype"),
            )
        }
    }
}
