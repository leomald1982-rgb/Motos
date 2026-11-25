package com.centinela.alertas.util

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object TimeFmt {
    fun ago(time: Long, now: Long = System.currentTimeMillis()): String {
        if (time <= 0) return "VIGENTE"
        val s = (now - time) / 1000
        return when {
            s < 0 -> "AHORA"
            s < 60 -> "HACE ${s}s"
            s < 3600 -> "HACE ${s / 60} MIN"
            s < 86400 -> "HACE ${s / 3600} H"
            s < 86400 * 30 -> "HACE ${s / 86400} D"
            else -> full(time)
        }
    }

    fun full(time: Long): String =
        SimpleDateFormat("dd MMM yyyy · HH:mm", Locale("es")).format(Date(time)).uppercase()

    fun clock(time: Long): String = SimpleDateFormat("HH:mm:ss", Locale.US).format(Date(time))

    fun date(time: Long): String = SimpleDateFormat("EEE dd MMM yyyy", Locale("es")).format(Date(time)).uppercase()
}
