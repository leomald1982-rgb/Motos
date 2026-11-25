package com.centinela.alertas

import com.centinela.alertas.data.Category
import com.centinela.alertas.data.FbiSource
import com.centinela.alertas.data.InterpolSource
import com.centinela.alertas.data.NewsSource
import com.centinela.alertas.data.Settings
import com.centinela.alertas.data.UsgsSource
import com.centinela.alertas.data.SsnSource
import com.centinela.alertas.data.EmscSource
import com.centinela.alertas.data.dedupeQuakes
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** Pruebas contra las fuentes reales (requieren red). */
@RunWith(RobolectricTestRunner::class)
@org.robolectric.annotation.Config(application = android.app.Application::class)
class SourcesLiveTest {
    private val s = Settings(lastLat = 19.43, lastLon = -99.13)

    @Test fun usgs() = runBlocking {
        val l = UsgsSource().fetch(s)
        println("USGS ${l.size}: " + l.take(3).joinToString { "${it.title} | ${it.summary}" })
        assertTrue(l.isNotEmpty()); assertTrue(l.all { it.category == Category.SISMO && it.magnitude != null })
    }

    @Test fun ssnEmsc() = runBlocking {
        val ssn = SsnSource().fetch(s); val emsc = EmscSource().fetch(s); val usgs = UsgsSource().fetch(s)
        println("SSN ${ssn.size}: " + ssn.take(2).joinToString { "${it.title} @${it.time}" })
        println("EMSC ${emsc.size}: " + emsc.take(2).joinToString { it.title })
        println("DEDUPE ${(ssn + usgs + emsc).size} -> ${dedupeQuakes(ssn + usgs + emsc).size}")
        assertTrue(ssn.isNotEmpty()); assertTrue(emsc.isNotEmpty())
    }

    @Test fun fbi() = runBlocking {
        val l = FbiSource().fetch(s)
        println("FBI ${l.size} BUS=${l.count { it.category == Category.BUSCADO }} DES=${l.count { it.category == Category.DESAPARECIDO }}")
        l.take(4).forEach { println("  ${it.category} ${it.severity} ${it.title} | ${it.summary.take(80)} | ${it.imageUrl}") }
        assertTrue(l.isNotEmpty())
    }

    @Test fun news() = runBlocking {
        val l = NewsSource().fetch(s)
        Category.entries.forEach { c -> println("NEWS $c ${l.count { it.category == c }}: " + l.filter { it.category == c }.take(2).joinToString { it.title }) }
        assertTrue(l.isNotEmpty())
    }

    @Test fun interpol() = runBlocking {
        val r = runCatching { InterpolSource().fetch(s) }
        println("INTERPOL " + r.fold({ "${it.size} " + it.take(2).joinToString { a -> a.title } }, { "ERR ${it.message}" }))
    }
}
