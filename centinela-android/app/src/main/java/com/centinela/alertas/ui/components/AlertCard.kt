package com.centinela.alertas.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.GppBad
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.PersonSearch
import androidx.compose.material.icons.filled.Waves
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.shape.CutCornerShape
import androidx.compose.foundation.layout.IntrinsicSize
import coil.compose.AsyncImage
import com.centinela.alertas.data.Alert
import com.centinela.alertas.data.Category
import com.centinela.alertas.data.GeoPoint
import com.centinela.alertas.ui.theme.Hud
import com.centinela.alertas.util.Geo
import com.centinela.alertas.util.TimeFmt

fun categoryIcon(c: Category): ImageVector = when (c) {
    Category.SISMO -> Icons.Filled.Waves
    Category.DESAPARECIDO -> Icons.Filled.PersonSearch
    Category.FUGA -> Icons.Filled.LockOpen
    Category.BUSCADO -> Icons.Filled.GppBad
}

@Composable
fun CategoryBadge(c: Category, size: Int = 44) {
    val col = Hud.category(c)
    Box(
        Modifier.size(size.dp).clip(CutCornerShape(8.dp)).background(col.copy(alpha = 0.12f))
            .border(1.dp, col.copy(alpha = 0.7f), CutCornerShape(8.dp)),
        contentAlignment = Alignment.Center
    ) { Icon(categoryIcon(c), null, tint = col, modifier = Modifier.size((size * 0.5f).dp)) }
}

@Composable
fun AlertCard(alert: Alert, me: GeoPoint?, saved: Boolean, onClick: () -> Unit) {
    val sev = Hud.severity(alert.severity)
    val cat = Hud.category(alert.category)
    Row(
        Modifier.fillMaxWidth().height(IntrinsicSize.Min)
            .hudFrame(sev.copy(alpha = 0.85f), cut = 10.dp, glow = alert.severity.weight > 3)
            .clickable(onClick = onClick)
            .padding(10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(Modifier.width(3.dp).fillMaxHeight().background(sev))
        Spacer(Modifier.width(10.dp))
        if (alert.imageUrl != null) {
            Box(Modifier.size(56.dp).clip(CutCornerShape(8.dp)).border(1.dp, cat, CutCornerShape(8.dp))) {
                AsyncImage(model = alert.imageUrl, contentDescription = alert.title, contentScale = ContentScale.Crop,
                    modifier = Modifier.matchParentSize())
            }
        } else if (alert.magnitude != null) {
            Box(Modifier.size(56.dp).clip(CutCornerShape(8.dp)).background(sev.copy(alpha = 0.12f))
                .border(1.dp, sev, CutCornerShape(8.dp)), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("M", color = sev.copy(alpha = 0.7f), fontSize = 9.sp, fontFamily = Hud.Mono)
                    Text("%.1f".format(alert.magnitude), color = sev, fontSize = 18.sp, fontWeight = FontWeight.Black, fontFamily = Hud.Mono)
                }
            }
        } else CategoryBadge(alert.category, 56)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Tag(alert.category.code, cat)
                Tag(alert.severity.label, sev)
                if (saved) Tag("★", Hud.Amber)
                Spacer(Modifier.weight(1f))
                Text(TimeFmt.ago(alert.time), color = Hud.TextDim, fontSize = 9.sp, fontFamily = Hud.Mono)
            }
            Spacer(Modifier.height(5.dp))
            Text(alert.title, color = Hud.Text, fontSize = 13.sp, fontWeight = FontWeight.Bold, fontFamily = Hud.Mono,
                maxLines = 2, overflow = TextOverflow.Ellipsis, lineHeight = 16.sp)
            Spacer(Modifier.height(3.dp))
            Text(alert.summary, color = Hud.TextDim, fontSize = 11.sp, fontFamily = Hud.Mono, maxLines = 2,
                overflow = TextOverflow.Ellipsis, lineHeight = 14.sp)
            Spacer(Modifier.height(4.dp))
            Row {
                Text("▸ ${alert.source}", color = cat.copy(alpha = 0.85f), fontSize = 9.sp, fontFamily = Hud.Mono, letterSpacing = 1.sp,
                    maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                if (me != null && alert.lat != null && alert.lon != null) {
                    val p = GeoPoint(alert.lat, alert.lon)
                    Text("${"%.0f".format(Geo.distanceKm(me, p))} KM ${Geo.cardinal(Geo.bearing(me, p))}",
                        color = Hud.CyanSoft, fontSize = 9.sp, fontFamily = Hud.Mono)
                }
            }
        }
    }
}

@Composable
fun Tag(text: String, color: Color) {
    Box(Modifier.border(1.dp, color.copy(alpha = 0.7f), CutCornerShape(3.dp)).background(color.copy(alpha = 0.12f))
        .padding(horizontal = 5.dp, vertical = 1.dp)) {
        Text(text, color = color, fontSize = 8.sp, fontWeight = FontWeight.Bold, fontFamily = Hud.Mono, letterSpacing = 1.sp)
    }
}
