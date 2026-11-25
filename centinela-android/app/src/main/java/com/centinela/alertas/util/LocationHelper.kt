package com.centinela.alertas.util

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationManager
import android.os.Build
import androidx.core.content.ContextCompat
import com.centinela.alertas.data.GeoPoint
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.resume

object LocationHelper {
    fun hasPermission(ctx: Context) =
        ContextCompat.checkSelfPermission(ctx, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(ctx, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED

    @SuppressLint("MissingPermission")
    fun lastKnown(ctx: Context): GeoPoint? {
        if (!hasPermission(ctx)) return null
        val lm = ctx.getSystemService(LocationManager::class.java) ?: return null
        val best = lm.getProviders(true).mapNotNull { runCatching { lm.getLastKnownLocation(it) }.getOrNull() }
            .maxByOrNull { it.time }
        return best?.let { GeoPoint(it.latitude, it.longitude) }
    }

    /** Ubicación actual (con fallback a la última conocida). */
    @SuppressLint("MissingPermission")
    suspend fun current(ctx: Context): GeoPoint? {
        if (!hasPermission(ctx)) return null
        val lm = ctx.getSystemService(LocationManager::class.java) ?: return null
        val provider = when {
            lm.isProviderEnabled(LocationManager.GPS_PROVIDER) -> LocationManager.GPS_PROVIDER
            lm.isProviderEnabled(LocationManager.NETWORK_PROVIDER) -> LocationManager.NETWORK_PROVIDER
            else -> return lastKnown(ctx)
        }
        if (Build.VERSION.SDK_INT >= 30) {
            val loc: Location? = withTimeoutOrNull(12_000) {
                suspendCancellableCoroutine { cont ->
                    val signal = android.os.CancellationSignal()
                    cont.invokeOnCancellation { signal.cancel() }
                    lm.getCurrentLocation(provider, signal, ctx.mainExecutor) { l -> if (cont.isActive) cont.resume(l) }
                }
            }
            if (loc != null) return GeoPoint(loc.latitude, loc.longitude)
        }
        return lastKnown(ctx)
    }

    fun mapsLink(p: GeoPoint) = "https://maps.google.com/?q=${"%.6f".format(java.util.Locale.US, p.lat)},${"%.6f".format(java.util.Locale.US, p.lon)}"
}
