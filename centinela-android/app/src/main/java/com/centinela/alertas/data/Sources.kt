package com.centinela.alertas.data

import android.util.Xml
import androidx.core.text.HtmlCompat
import com.centinela.alertas.util.Geo
import org.json.JSONArray
import org.json.JSONObject
import org.xmlpull.v1.XmlPullParser
import java.net.URLEncoder
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

interface AlertSource {
    val name: String
    fun isActive(settings: Settings): Boolean
    suspend fun fetch(settings: Settings): List<Alert>
}

private fun html(s: String?): String =
    if (s.isNullOrBlank() || s == "null") "" else HtmlCompat.fromHtml(s, HtmlCompat.FROM_HTML_MODE_COMPACT).toString().trim()

private fun JSONObject.str(key: String): String? =
    if (isNull(key)) null else optString(key).takeIf { it.isNotBlank() && it != "null" }

private fun JSONArray?.strings(): List<String> =
    if (this == null) emptyList() else (0 until length()).mapNotNull { optString(it).takeIf { s -> s.isNotBlank() && s != "null" } }

/* ─────────────────────────── SISMOS · USGS ─────────────────────────── */
class UsgsSource : AlertSource {
    override val name = "USGS"
    override fun isActive(settings: Settings) = Category.SISMO in settings.enabledCategories

    override suspend fun fetch(settings: Settings): List<Alert> {
        val iso = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US).apply { timeZone = TimeZone.getTimeZone("UTC") }
        val start = iso.format(System.currentTimeMillis() - 7L * 86_400_000)
        val center = settings.location ?: settings.region.center
        val sb = StringBuilder("https://earthquake.usgs.gov/fdsnws/event/1/query?format=geojson&orderby=time&limit=200")
        sb.append("&starttime=").append(start)
        sb.append("&minmagnitude=").append(settings.minMagnitude)
        if (settings.radiusKm > 0) {
            sb.append("&latitude=").append(center.lat).append("&longitude=").append(center.lon)
            sb.append("&maxradiuskm=").append(settings.radiusKm.coerceAtMost(20000))
        }
        val root = JSONObject(Http.get(sb.toString()))
        val features = root.getJSONArray("features")
        return (0 until features.length()).map { i ->
            val f = features.getJSONObject(i)
            val p = f.getJSONObject("properties")
            val c = f.getJSONObject("geometry").getJSONArray("coordinates")
            quakeAlert(
                id = "usgs:" + (f.str("id") ?: "${c.getDouble(1)}${c.getDouble(0)}${p.optLong("time")}"),
                source = "USGS", mag = p.optDouble("mag", 0.0), place = p.str("place") ?: "Ubicación desconocida",
                time = p.optLong("time"), lat = c.getDouble(1), lon = c.getDouble(0), depth = c.optDouble(2, 0.0),
                url = p.str("url"), settings = settings, pager = p.str("alert"), tsunami = p.optInt("tsunami", 0) == 1,
                felt = p.str("felt"), magType = p.str("magType"),
            )
        }
    }
}

/* ─────────────────── BUSCADOS / DESAPARECIDOS · FBI ─────────────────── */
class FbiSource : AlertSource {
    override val name = "FBI"
    override fun isActive(settings: Settings) =
        Category.BUSCADO in settings.enabledCategories || Category.DESAPARECIDO in settings.enabledCategories

    override suspend fun fetch(settings: Settings): List<Alert> {
        val fmt = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US).apply { timeZone = TimeZone.getTimeZone("America/New_York") }
        val out = mutableListOf<Alert>()
        for (page in 1..2) {
            val json = JSONObject(Http.get("https://api.fbi.gov/wanted/v1/list?pageSize=50&page=$page&sort_on=publication&sort_order=desc"))
            val items = json.getJSONArray("items")
            for (i in 0 until items.length()) {
                val it = items.getJSONObject(i)
                val subjects = it.optJSONArray("subjects").strings()
                val path = it.str("path") ?: ""
                val poster = it.str("poster_classification") ?: ""
                val missing = poster == "missing" || path.contains("missing", true) || path.contains("kidnap", true) ||
                    subjects.any { s -> s.contains("Missing", true) || s.contains("Kidnap", true) }
                val descRaw = it.str("description") ?: ""
                val victimOnly = !missing && (it.str("person_classification") == "Victim" ||
                    descRaw.contains("Victim", true) || subjects.any { s -> s.contains("Homicides", true) })
                if (victimOnly) continue // casos de víctimas de homicidio: no son alertas de búsqueda
                val cat = if (missing) Category.DESAPARECIDO else Category.BUSCADO
                if (cat !in settings.enabledCategories) continue
                val title = it.str("title") ?: continue
                val top10 = poster == "ten"
                val caution = html(it.str("caution"))
                val warning = it.str("warning_message")
                val reward = it.str("reward_text")
                val image = it.optJSONArray("images")?.optJSONObject(0)?.let { img -> img.str("large") ?: img.str("thumb") }
                val time = runCatching { fmt.parse(it.str("publication") ?: "")?.time }.getOrNull() ?: 0L
                val sev = when {
                    top10 || warning?.contains("ARMED", true) == true || warning?.contains("DANGEROUS", true) == true -> Severity.CRITICA
                    cat == Category.DESAPARECIDO || reward != null -> Severity.ADVERTENCIA
                    else -> Severity.INFO
                }
                val desc = html(it.str("description")).replace("\r", "").replace("\n", " · ")
                val details = buildString {
                    if (top10) appendLine("★ LISTA DE LOS 10 MÁS BUSCADOS DEL FBI")
                    warning?.let { w -> appendLine("⚠ $w"); appendLine() }
                    if (desc.isNotBlank()) appendLine(desc)
                    appendLine()
                    it.str("sex")?.let { s -> appendLine("Sexo: $s") }
                    it.str("race_raw")?.let { s -> appendLine("Raza: $s") }
                    it.str("hair_raw")?.let { s -> appendLine("Cabello: $s") }
                    it.str("eyes_raw")?.let { s -> appendLine("Ojos: $s") }
                    it.str("height_min")?.let { h -> appendLine("Estatura: ${"%.0f".format(h.toDouble() * 2.54)} cm") }
                    it.str("weight")?.let { s -> appendLine("Peso: $s") }
                    it.optJSONArray("dates_of_birth_used").strings().takeIf { l -> l.isNotEmpty() }?.let { l -> appendLine("Fecha de nacimiento: ${l.joinToString()}") }
                    it.str("place_of_birth")?.let { s -> appendLine("Lugar de nacimiento: $s") }
                    it.str("nationality")?.let { s -> appendLine("Nacionalidad: $s") }
                    it.optJSONArray("aliases").strings().takeIf { l -> l.isNotEmpty() }?.let { l -> appendLine("Alias: ${l.take(6).joinToString()}") }
                    it.str("scars_and_marks")?.let { s -> appendLine("Señas particulares: $s") }
                    it.optJSONArray("field_offices").strings().takeIf { l -> l.isNotEmpty() }?.let { l -> appendLine("Oficina: ${l.joinToString { o -> o.uppercase() }}") }
                    if (subjects.isNotEmpty()) appendLine("Clasificación: ${subjects.joinToString()}")
                    if (caution.isNotBlank()) { appendLine(); appendLine(caution) }
                    html(it.str("remarks")).takeIf { r -> r.isNotBlank() }?.let { r -> appendLine(); appendLine(r) }
                    html(it.str("details")).takeIf { r -> r.isNotBlank() }?.let { r -> appendLine(); appendLine(r) }
                }.trim()
                out += Alert(
                    id = "fbi:" + (it.str("uid") ?: title),
                    category = cat,
                    title = title,
                    summary = if (desc.isNotBlank()) desc else subjects.joinToString(),
                    details = details,
                    time = time,
                    source = if (top10) "FBI · TOP 10" else "FBI",
                    url = it.str("url"),
                    imageUrl = image,
                    severity = sev,
                    reward = reward,
                    isPerson = true,
                )
            }
        }
        return out
    }
}

/* ─────────────── NOTIFICACIONES ROJAS / AMARILLAS · INTERPOL ─────────────── */
class InterpolSource : AlertSource {
    override val name = "INTERPOL"
    override fun isActive(settings: Settings) =
        Category.BUSCADO in settings.enabledCategories || Category.DESAPARECIDO in settings.enabledCategories

    private val headers = mapOf(
        "Accept" to "application/json",
        "Origin" to "https://www.interpol.int",
        "Referer" to "https://www.interpol.int/",
    )

    override suspend fun fetch(settings: Settings): List<Alert> {
        val out = mutableListOf<Alert>()
        val nat = settings.region.code
        var lastError: Exception? = null
        if (Category.BUSCADO in settings.enabledCategories) {
            runCatching { out += fetchKind("red", nat, Category.BUSCADO) }.onFailure { lastError = it as? Exception }
        }
        if (Category.DESAPARECIDO in settings.enabledCategories) {
            runCatching { out += fetchKind("yellow", nat, Category.DESAPARECIDO) }.onFailure { lastError = it as? Exception }
        }
        if (out.isEmpty() && lastError != null) throw lastError!!
        return out
    }

    private suspend fun fetchKind(kind: String, nationality: String, cat: Category): List<Alert> {
        val url = "https://ws-public.interpol.int/notices/v1/$kind?nationality=$nationality&resultPerPage=40"
        val root = JSONObject(Http.get(url, headers))
        val notices = root.optJSONObject("_embedded")?.optJSONArray("notices") ?: return emptyList()
        return (0 until notices.length()).mapNotNull { i ->
            val n = notices.getJSONObject(i)
            val entity = n.str("entity_id") ?: return@mapNotNull null
            val name = listOfNotNull(n.str("forename"), n.str("name")).joinToString(" ").ifBlank { "SIN NOMBRE" }
            val dob = n.str("date_of_birth")
            val nats = n.optJSONArray("nationalities").strings()
            val thumb = n.optJSONObject("_links")?.optJSONObject("thumbnail")?.str("href")
            val red = kind == "red"
            val anchor = entity.replace("/", "-")
            Alert(
                id = "interpol:$kind:$entity",
                category = cat,
                title = name.uppercase(),
                summary = buildString {
                    append(if (red) "Notificación ROJA de Interpol" else "Notificación AMARILLA (persona desaparecida)")
                    dob?.let { append(" · Nac. $it") }
                    if (nats.isNotEmpty()) append(" · ${nats.joinToString()}")
                },
                details = buildString {
                    appendLine(if (red) "Persona buscada internacionalmente para su detención y extradición." else "Persona reportada como desaparecida a nivel internacional.")
                    appendLine()
                    appendLine("Ficha: $entity")
                    dob?.let { appendLine("Fecha de nacimiento: $it") }
                    if (nats.isNotEmpty()) appendLine("Nacionalidad: ${nats.joinToString()}")
                    appendLine()
                    append("Si tienes información, NO intentes intervenir. Comunícate con la policía local o con Interpol.")
                },
                time = 0L,
                source = "INTERPOL",
                url = if (red) "https://www.interpol.int/How-we-work/Notices/Red-Notices/View-Red-Notices#$anchor"
                else "https://www.interpol.int/How-we-work/Notices/Yellow-Notices/View-Yellow-Notices#$anchor",
                imageUrl = thumb,
                severity = if (red) Severity.ADVERTENCIA else Severity.ADVERTENCIA,
                isPerson = true,
            )
        }
    }
}

/* ──────────────────────── NOTICIAS · RSS ──────────────────────── */
class NewsSource : AlertSource {
    override val name = "NOTICIAS"
    override fun isActive(settings: Settings) =
        settings.enabledCategories.any { it != Category.SISMO }

    private data class Query(val category: Category, val es: String, val en: String)

    private val queries = listOf(
        Query(Category.FUGA,
            "\"fuga de reos\" OR \"se fugan\" OR \"fuga del penal\" OR \"evasión de presos\" OR \"reos prófugos\"",
            "\"prison escape\" OR \"inmates escaped\" OR \"escaped inmate\" OR \"jail escape\""),
        Query(Category.DESAPARECIDO,
            "\"Alerta Amber\" OR \"ficha de búsqueda\" OR \"Protocolo Alba\" OR \"persona desaparecida\" OR \"se busca a\"",
            "\"Amber Alert\" OR \"missing person\" OR \"missing child\" OR \"Silver Alert\""),
        Query(Category.BUSCADO,
            "\"más buscados\" OR \"orden de aprehensión\" OR \"ficha roja\" OR \"recompensa por información\" OR \"prófugo de la justicia\"",
            "\"most wanted\" OR \"fugitive\" OR \"manhunt\" OR \"wanted by police\""),
    )

    override suspend fun fetch(settings: Settings): List<Alert> {
        val region = settings.region
        val out = mutableListOf<Alert>()
        var failures = 0
        for (q in queries) {
            if (q.category !in settings.enabledCategories) continue
            val base = if (region.english) q.en else q.es
            val local = settings.localKeyword.trim()
            val term = buildString {
                append("(").append(base).append(")")
                if (local.isNotEmpty()) append(" ").append(local)
                append(" when:7d")
            }
            val url = "https://news.google.com/rss/search?q=" + URLEncoder.encode(term, "UTF-8") +
                "&hl=${region.hl}&gl=${region.gl}&ceid=${URLEncoder.encode(region.ceid, "UTF-8")}"
            try {
                out += parseRss(Http.get(url), q.category).take(30)
            } catch (e: Exception) {
                failures++
                if (failures == queries.size) throw e
            }
        }
        return out
    }

    private fun parseRss(xml: String, cat: Category): List<Alert> {
        val parser = Xml.newPullParser()
        parser.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false)
        parser.setInput(xml.reader())
        val dateFmt = SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss zzz", Locale.US)
        val items = mutableListOf<Alert>()
        var inItem = false
        var title = ""; var link = ""; var pub = ""; var source = ""; var desc = ""; var guid = ""
        var tag: String? = null
        while (parser.eventType != XmlPullParser.END_DOCUMENT) {
            when (parser.eventType) {
                XmlPullParser.START_TAG -> {
                    tag = parser.name
                    if (tag == "item") { inItem = true; title = ""; link = ""; pub = ""; source = ""; desc = ""; guid = "" }
                }
                XmlPullParser.TEXT -> if (inItem) {
                    val t = parser.text
                    when (tag) {
                        "title" -> title += t
                        "link" -> link += t
                        "pubDate" -> pub += t
                        "source" -> source += t
                        "description" -> desc += t
                        "guid" -> guid += t
                    }
                }
                XmlPullParser.END_TAG -> {
                    if (parser.name == "item") {
                        inItem = false
                        val cleanTitle = title.substringBeforeLast(" - ").trim().ifBlank { title }
                        val src = source.ifBlank { title.substringAfterLast(" - ", "Noticias") }.trim()
                        val time = runCatching { dateFmt.parse(pub.trim())?.time }.getOrNull() ?: 0L
                        val lower = cleanTitle.lowercase()
                        val sev = when {
                            lower.contains("amber") || lower.contains("alba") || lower.contains("armado") ||
                                lower.contains("peligros") || lower.contains("armed") || lower.contains("dangerous") ||
                                (cat == Category.FUGA && (lower.contains("masiva") || lower.contains("reos") || lower.contains("inmates"))) -> Severity.CRITICA
                            cat == Category.FUGA || cat == Category.DESAPARECIDO -> Severity.ADVERTENCIA
                            else -> Severity.INFO
                        }
                        items += Alert(
                            id = "news:" + (guid.ifBlank { link }).trim().hashCode().toUInt().toString(16),
                            category = cat,
                            title = cleanTitle,
                            summary = "Reporte de prensa · $src",
                            details = "$cleanTitle\n\nFuente: $src\n\nEsta alerta proviene de un medio de comunicación. Verifica la información en la nota original y con autoridades oficiales antes de compartirla.",
                            time = time,
                            source = src.uppercase().take(28),
                            url = link.trim(),
                            severity = sev,
                        )
                    }
                    tag = null
                }
            }
            parser.next()
        }
        return items
    }
}
