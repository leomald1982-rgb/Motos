package com.centinela.alertas.work

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioAttributes
import android.media.RingtoneManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.centinela.alertas.MainActivity
import com.centinela.alertas.data.Alert
import com.centinela.alertas.data.Category
import com.centinela.alertas.data.Severity

object Notifier {
    const val CH_CRITICAL = "centinela_critical"
    const val CH_ALERTS = "centinela_alerts"
    const val EXTRA_ALERT_ID = "alert_id"
    private const val GROUP = "centinela_group"

    fun createChannels(ctx: Context) {
        val nm = ctx.getSystemService(NotificationManager::class.java)
        val critical = NotificationChannel(CH_CRITICAL, "Alertas críticas", NotificationManager.IMPORTANCE_HIGH).apply {
            description = "Sismos fuertes, fugas masivas, Alerta Amber y fugitivos peligrosos"
            enableVibration(true)
            vibrationPattern = longArrayOf(0, 600, 200, 600, 200, 900)
            enableLights(true)
            lightColor = 0xFF00E5FF.toInt()
            setSound(
                RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM),
                AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ALARM)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build()
            )
        }
        val normal = NotificationChannel(CH_ALERTS, "Alertas", NotificationManager.IMPORTANCE_DEFAULT).apply {
            description = "Nuevas alertas de seguridad"
        }
        nm.createNotificationChannels(listOf(critical, normal))
    }

    private fun canPost(ctx: Context) = Build.VERSION.SDK_INT < 33 ||
        ContextCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

    private fun icon(c: Category) = when (c) {
        Category.SISMO -> "◉ SISMO"
        Category.DESAPARECIDO -> "◉ DESAPARECIDO"
        Category.FUGA -> "◉ FUGA"
        Category.BUSCADO -> "◉ BUSCADO"
    }

    fun notify(ctx: Context, alerts: List<Alert>) {
        if (alerts.isEmpty() || !canPost(ctx)) return
        val nm = NotificationManagerCompat.from(ctx)
        val top = alerts.sortedByDescending { it.severity.weight }.take(6)
        for (a in top) {
            val intent = Intent(ctx, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
                putExtra(EXTRA_ALERT_ID, a.id)
            }
            val pi = PendingIntent.getActivity(ctx, a.id.hashCode(), intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
            val critical = a.severity == Severity.CRITICA
            val n = NotificationCompat.Builder(ctx, if (critical) CH_CRITICAL else CH_ALERTS)
                .setSmallIcon(com.centinela.alertas.R.drawable.ic_stat_alert)
                .setColor(0xFF00E5FF.toInt())
                .setContentTitle("${icon(a.category)} · ${a.severity.label}")
                .setContentText(a.title)
                .setStyle(NotificationCompat.BigTextStyle().bigText("${a.title}\n${a.summary}"))
                .setSubText(a.source)
                .setWhen(a.time)
                .setShowWhen(true)
                .setPriority(if (critical) NotificationCompat.PRIORITY_MAX else NotificationCompat.PRIORITY_DEFAULT)
                .setCategory(if (critical) NotificationCompat.CATEGORY_ALARM else NotificationCompat.CATEGORY_EVENT)
                .setGroup(GROUP)
                .setAutoCancel(true)
                .setContentIntent(pi)
                .build()
            runCatching { nm.notify(a.id.hashCode(), n) }
        }
        if (alerts.size > 1) {
            val summary = NotificationCompat.Builder(ctx, CH_ALERTS)
                .setSmallIcon(com.centinela.alertas.R.drawable.ic_stat_alert)
                .setColor(0xFF00E5FF.toInt())
                .setContentTitle("CENTINELA · ${alerts.size} alertas nuevas")
                .setStyle(NotificationCompat.InboxStyle().also { s -> top.forEach { s.addLine(it.title) } })
                .setGroup(GROUP).setGroupSummary(true).setAutoCancel(true)
                .build()
            runCatching { nm.notify(1, summary) }
        }
    }

    fun test(ctx: Context) {
        notify(ctx, listOf(
            Alert(id = "test:${System.currentTimeMillis()}", category = Category.SISMO,
                title = "PRUEBA DE SISTEMA — Canal de alertas operativo",
                summary = "Si ves y escuchas esto, las alertas críticas funcionan.",
                time = System.currentTimeMillis(), source = "CENTINELA", severity = Severity.CRITICA)
        ))
    }
}
