package com.centinela.alertas

import android.Manifest
import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import com.centinela.alertas.ui.CentinelaRoot
import com.centinela.alertas.ui.MainViewModel
import com.centinela.alertas.ui.theme.CentinelaTheme
import com.centinela.alertas.work.Notifier

class MainActivity : ComponentActivity() {
    private val vm: MainViewModel by viewModels()
    private val pendingAlert = mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        pendingAlert.value = intent?.getStringExtra(Notifier.EXTRA_ALERT_ID)
        lifecycle.addObserver(object : DefaultLifecycleObserver {
            override fun onStart(owner: LifecycleOwner) { vm.startLive() }
            override fun onStop(owner: LifecycleOwner) { vm.stopLive() }
        })
        setContent {
            CentinelaTheme {
                CentinelaRoot(vm, pendingAlert.value) { pendingAlert.value = null }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        intent.getStringExtra(Notifier.EXTRA_ALERT_ID)?.let { pendingAlert.value = it }
    }

    companion object {
        val runtimePermissions: Array<String> = buildList {
            add(Manifest.permission.ACCESS_FINE_LOCATION)
            add(Manifest.permission.ACCESS_COARSE_LOCATION)
            if (Build.VERSION.SDK_INT >= 33) add(Manifest.permission.POST_NOTIFICATIONS)
        }.toTypedArray()
    }
}
