package com.centinela.alertas.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.clickable
import com.centinela.alertas.data.Alert
import com.centinela.alertas.data.Category
import com.centinela.alertas.data.Severity
import com.centinela.alertas.ui.UiState
import com.centinela.alertas.ui.components.AlertCard
import com.centinela.alertas.ui.components.HudChip
import com.centinela.alertas.ui.components.hudFrame
import com.centinela.alertas.ui.theme.Hud

enum class FeedFilter(val label: String) { TODO("TODO"), SIS("SISMOS"), DES("DESAPARECIDOS"), FUG("FUGAS"), BUS("BUSCADOS"), CRIT("CRÍTICAS"), SAVED("★ GUARDADAS") }

fun FeedFilter.category(): Category? = when (this) {
    FeedFilter.SIS -> Category.SISMO
    FeedFilter.DES -> Category.DESAPARECIDO
    FeedFilter.FUG -> Category.FUGA
    FeedFilter.BUS -> Category.BUSCADO
    else -> null
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FeedScreen(
    state: UiState,
    saved: List<Alert>,
    initialFilter: FeedFilter,
    onRefresh: () -> Unit,
    onOpen: (Alert) -> Unit,
) {
    var filter by rememberSaveable(initialFilter) { mutableStateOf(initialFilter) }
    var query by rememberSaveable { mutableStateOf("") }
    val savedIds = remember(saved) { saved.map { it.id }.toSet() }

    val list = remember(state.alerts, saved, filter, query) {
        val base = if (filter == FeedFilter.SAVED) saved else state.alerts
        base.filter { a ->
            (filter.category()?.let { a.category == it } ?: true) &&
                (filter != FeedFilter.CRIT || a.severity == Severity.CRITICA) &&
                (query.isBlank() || a.title.contains(query, true) || a.summary.contains(query, true) || a.details.contains(query, true))
        }
    }

    Column(Modifier.fillMaxSize()) {
        // Buscador
        Row(
            Modifier.padding(horizontal = 14.dp, vertical = 8.dp).fillMaxWidth().hudFrame(cut = 8.dp).padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Filled.Search, null, tint = Hud.Cyan, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(10.dp))
            Box(Modifier.weight(1f)) {
                if (query.isEmpty()) Text("BUSCAR NOMBRE, LUGAR, PALABRA…", color = Hud.TextDim, fontSize = 12.sp, fontFamily = Hud.Mono)
                BasicTextField(
                    value = query, onValueChange = { query = it }, singleLine = true,
                    textStyle = TextStyle(color = Hud.Text, fontSize = 13.sp, fontFamily = Hud.Mono),
                    cursorBrush = SolidColor(Hud.Cyan), modifier = Modifier.fillMaxWidth()
                )
            }
            if (query.isNotEmpty()) Icon(Icons.Filled.Close, "Limpiar", tint = Hud.TextDim,
                modifier = Modifier.size(18.dp).clickable { query = "" })
        }
        LazyRow(contentPadding = PaddingValues(horizontal = 14.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(FeedFilter.entries) { f ->
                val accent = f.category()?.let { Hud.category(it) } ?: when (f) {
                    FeedFilter.CRIT -> Hud.Red; FeedFilter.SAVED -> Hud.Amber; else -> Hud.Cyan
                }
                HudChip(f.label, filter == f, accent) { filter = f }
            }
        }
        Text("◢ ${list.size} REGISTROS", color = Hud.TextDim, fontSize = 10.sp, fontFamily = Hud.Mono, letterSpacing = 2.sp,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp))
        PullToRefreshBox(isRefreshing = state.loading, onRefresh = onRefresh, modifier = Modifier.fillMaxSize()) {
            LazyColumn(
                Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = 14.dp, end = 14.dp, bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                if (list.isEmpty()) item {
                    Text(
                        when {
                            state.loading -> "ADQUIRIENDO SEÑAL…"
                            filter == FeedFilter.SAVED -> "NO HAS GUARDADO ALERTAS. ABRE UNA Y PULSA ★ GUARDAR."
                            else -> "SIN REGISTROS PARA ESTE FILTRO. DESLIZA HACIA ABAJO PARA ESCANEAR."
                        },
                        color = Hud.TextDim, fontSize = 12.sp, fontFamily = Hud.Mono, modifier = Modifier.padding(top = 40.dp)
                    )
                }
                items(list, key = { it.id }) { a -> AlertCard(a, state.location, a.id in savedIds) { onOpen(a) } }
            }
        }
    }
}
