package com.centinela.alertas.util

import android.content.Context
import android.content.Intent
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.net.Uri
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.os.Build
import com.centinela.alertas.data.Contact
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.sin

/** Sirena generada por software (barrido 600–1400 Hz) a volumen de alarma. */
class Siren {
    @Volatile private var running = false
    private var thread: Thread? = null
    val isOn get() = running

    fun start() {
        if (running) return
        running = true
        thread = Thread {
            val rate = 44100
            val buf = ShortArray(rate / 20)
            val track = AudioTrack.Builder()
                .setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ALARM)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build())
                .setAudioFormat(AudioFormat.Builder().setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .setSampleRate(rate).setChannelMask(AudioFormat.CHANNEL_OUT_MONO).build())
                .setBufferSizeInBytes(buf.size * 4)
                .setTransferMode(AudioTrack.MODE_STREAM)
                .build()
            track.play()
            var phase = 0.0; var t = 0.0
            while (running) {
                for (i in buf.indices) {
                    val f = 1000 + 400 * sin(2 * PI * t * 0.8) // barrido
                    phase += 2 * PI * f / rate
                    if (phase > 2 * PI) phase -= 2 * PI
                    buf[i] = (sin(phase) * Short.MAX_VALUE * 0.9).toInt().toShort()
                    t += 1.0 / rate
                }
                track.write(buf, 0, buf.size)
            }
            track.stop(); track.release()
        }.apply { isDaemon = true; start() }
    }

    fun stop() { running = false; thread = null }
}

/** Linterna en patrón Morse S.O.S. */
class SosTorch(private val ctx: Context) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var job: Job? = null
    val isOn get() = job?.isActive == true

    private val cm get() = ctx.getSystemService(CameraManager::class.java)
    private val camId: String? by lazy {
        runCatching {
            cm.cameraIdList.firstOrNull { cm.getCameraCharacteristics(it).get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true }
        }.getOrNull()
    }
    val available get() = camId != null

    fun start() {
        val id = camId ?: return
        if (isOn) return
        job = scope.launch {
            val dot = 200L; val dash = 600L
            val pattern = listOf(dot, dot, dot, dash, dash, dash, dot, dot, dot)
            while (isActive) {
                for ((i, d) in pattern.withIndex()) {
                    runCatching { cm.setTorchMode(id, true) }; delay(d)
                    runCatching { cm.setTorchMode(id, false) }; delay(if (i % 3 == 2) 600 else 200)
                }
                delay(1400)
            }
        }.also { it.invokeOnCompletion { runCatching { cm.setTorchMode(id, false) } } }
    }

    fun stop() { job?.cancel(); job = null }
}

object Emergency {
    fun dial(ctx: Context, number: String) {
        ctx.startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:$number")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }

    fun sms(ctx: Context, contacts: List<Contact>, body: String) {
        val to = contacts.joinToString(";") { it.phone.filter { c -> c.isDigit() || c == '+' } }
        val i = Intent(Intent.ACTION_SENDTO, Uri.parse("smsto:$to")).putExtra("sms_body", body)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        ctx.startActivity(i)
    }

    fun share(ctx: Context, text: String, title: String = "Compartir") {
        val i = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, text)
        ctx.startActivity(Intent.createChooser(i, title).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }

    fun open(ctx: Context, url: String) {
        runCatching { ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
    }

    fun vibrate(ctx: Context, pattern: LongArray = longArrayOf(0, 300, 120, 300)) {
        val v: Vibrator? = if (Build.VERSION.SDK_INT >= 31)
            ctx.getSystemService(VibratorManager::class.java)?.defaultVibrator
        else @Suppress("DEPRECATION") ctx.getSystemService(Vibrator::class.java)
        runCatching { v?.vibrate(VibrationEffect.createWaveform(pattern, -1)) }
    }
}
