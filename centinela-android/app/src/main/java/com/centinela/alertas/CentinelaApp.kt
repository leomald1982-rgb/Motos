package com.centinela.alertas

import android.app.Application
import com.centinela.alertas.data.AlertRepository
import com.centinela.alertas.data.SettingsStore
import com.centinela.alertas.work.AlertWorker
import com.centinela.alertas.work.Notifier

class CentinelaApp : Application() {
    lateinit var store: SettingsStore
        private set
    lateinit var repository: AlertRepository
        private set

    override fun onCreate() {
        super.onCreate()
        store = SettingsStore(this)
        repository = AlertRepository(this, store)
        Notifier.createChannels(this)
        AlertWorker.schedule(this, 15)
    }
}
