package com.centinela.alertas.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.centinela.alertas.CentinelaApp
import com.centinela.alertas.data.Alert
import com.centinela.alertas.data.AlertRepository
import com.centinela.alertas.data.Category
import com.centinela.alertas.data.GeoPoint
import com.centinela.alertas.data.Settings
import com.centinela.alertas.data.Severity
import com.centinela.alertas.data.SourceStatus
import com.centinela.alertas.util.Emergency
import com.centinela.alertas.util.LocationHelper
import com.centinela.alertas.work.AlertWorker
import com.centinela.alertas.work.Notifier
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

data class UiState(
    val alerts: List<Alert> = emptyList(),
    val loading: Boolean = false,
    val lastUpdate: Long = 0L,
    val status: List<SourceStatus> = emptyList(),
    val location: GeoPoint? = null,
    val banner: Alert? = null,
) {
    /** Índice de amenaza 0..1 a partir de las alertas de las últimas 24 h. */
    val threat: Float get() {
        val dayAgo = System.currentTimeMillis() - 86_400_000L
        val score = alerts.filter { it.time >= dayAgo }.sumOf { it.severity.weight }
        return (score / 120f).coerceIn(0f, 1f)
    }
    val threatLabel: String get() = when {
        threat >= 0.75f -> "CRÍTICO"
        threat >= 0.45f -> "ELEVADO"
        threat >= 0.2f -> "MODERADO"
        else -> "BAJO"
    }
    fun count(c: Category, hours: Int = 24) =
        alerts.count { it.category == c && it.time >= System.currentTimeMillis() - hours * 3_600_000L }
}

class MainViewModel(app: Application) : AndroidViewModel(app) {
    private val ca = app as CentinelaApp
    private val store = ca.store
    private val repo = ca.repository

    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state.asStateFlow()

    val settings: StateFlow<Settings> = store.settings.stateIn(viewModelScope, SharingStarted.Eagerly, Settings())
    val saved: StateFlow<List<Alert>> = store.saved.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    val onboarded: StateFlow<Boolean?> = store.onboarded.stateIn(viewModelScope, SharingStarted.Eagerly, null)

    private var liveJob: Job? = null

    init {
        viewModelScope.launch {
            val cached = repo.loadCache()
            _state.update { it.copy(alerts = cached) }
            refreshLocation()
            refresh()
        }
        // Si cambian filtros relevantes, vuelve a consultar.
        viewModelScope.launch {
            store.settings.map { listOf(it.enabledCategories, it.minMagnitude, it.radiusKm, it.regionCode, it.localKeyword) }
                .distinctUntilChanged().drop(1).collect { refresh() }
        }
        viewModelScope.launch {
            store.settings.map { it.backgroundMinutes }.distinctUntilChanged().drop(1).collect {
                AlertWorker.schedule(getApplication(), it, replace = true)
            }
        }
    }

    fun refreshLocation() = viewModelScope.launch {
        val loc = LocationHelper.current(getApplication()) ?: settings.value.location
        if (loc != null) {
            _state.update { it.copy(location = loc) }
            store.update { it.copy(lastLat = loc.lat, lastLon = loc.lon) }
        }
    }

    fun refresh() {
        if (_state.value.loading) return
        viewModelScope.launch {
            _state.update { it.copy(loading = true) }
            try {
                val r = repo.refresh()
                val s = store.current()
                val notifiable = r.fresh.filter { AlertRepository.shouldNotify(it, s) }
                val critical = notifiable.filter { it.severity == Severity.CRITICA }.maxByOrNull { it.time }
                _state.update {
                    it.copy(alerts = r.alerts, status = r.status, lastUpdate = System.currentTimeMillis(),
                        banner = critical ?: it.banner)
                }
                if (notifiable.isNotEmpty()) {
                    Notifier.notify(getApplication(), notifiable)
                    if (critical != null) Emergency.vibrate(getApplication(), longArrayOf(0, 500, 150, 500, 150, 800))
                }
            } finally {
                _state.update { it.copy(loading = false) }
            }
        }
    }

    /** Modo en vivo: sondeo frecuente mientras la app está en primer plano. */
    fun startLive() {
        if (liveJob?.isActive == true) return
        liveJob = viewModelScope.launch {
            while (isActive) {
                val s = settings.value
                delay(s.liveRefreshSec.coerceAtLeast(30) * 1000L)
                if (s.liveMode) refresh()
            }
        }
    }

    fun stopLive() { liveJob?.cancel(); liveJob = null }

    fun dismissBanner() = _state.update { it.copy(banner = null) }

    fun toggleSaved(a: Alert) = viewModelScope.launch { store.toggleSaved(a) }

    fun updateSettings(block: (Settings) -> Settings) = viewModelScope.launch { store.update(block) }

    fun finishOnboarding() = viewModelScope.launch { store.setOnboarded() }

    fun find(id: String): Alert? = _state.value.alerts.firstOrNull { it.id == id } ?: saved.value.firstOrNull { it.id == id }
}
