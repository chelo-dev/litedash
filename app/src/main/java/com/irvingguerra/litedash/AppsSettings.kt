// Ajustes → Apps: música, radio, navegación y las favoritas de la barra.
package com.irvingguerra.litedash

import com.irvingguerra.litedash.feature.apps.AppIcon
import com.irvingguerra.litedash.feature.apps.rememberApps
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import com.irvingguerra.litedash.core.ui.Radius
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.irvingguerra.litedash.core.ui.IconButton
import com.irvingguerra.litedash.core.ui.Icons
import com.irvingguerra.litedash.core.ui.Label
import com.irvingguerra.litedash.core.ui.LocalColors
import com.irvingguerra.litedash.core.ui.TextInput
import com.irvingguerra.litedash.core.ui.Typography

/** Sección Apps: la de música (con el reproductor USB), la radio, la de navegación (con Casa y Trabajo) y las favoritas de la barra. */
@Composable
internal fun AppsSettings(d: Dependencies) {
    val c = LocalColors.current
    when (tabs(listOf(R.string.music, R.string.tab_radio, R.string.tab_navigation, R.string.tab_favorites, R.string.tab_split), APPS_TABS)) {
        0 -> {
            SettingsCard { UsbPlayerCard(d) }
            SettingsCard(stringResource(R.string.music_app_title)) { MusicSettings(d) }
            SettingsCard(stringResource(R.string.bt_pair), stringResource(R.string.bt_pair_help)) { BluetoothSettings(d) }
        }
        1 -> RadioSettings(d)
        2 -> {
            SettingsCard { MapSettings(d) }
            SettingsCard(stringResource(R.string.places), stringResource(R.string.places_help)) {
                Label(stringResource(R.string.place_home), Typography.label, c.textMuted)
                TextInput(d.prefs.homeAddress ?: "", { d.prefs.homeAddress = it.take(160).ifBlank { null } }, stringResource(R.string.place_hint), Modifier.fillMaxWidth())
                Label(stringResource(R.string.place_work), Typography.label, c.textMuted)
                TextInput(d.prefs.workAddress ?: "", { d.prefs.workAddress = it.take(160).ifBlank { null } }, stringResource(R.string.place_hint), Modifier.fillMaxWidth())
            }
        }
        3 -> SettingsCard(stringResource(R.string.tab_favorites), stringResource(R.string.favorites_help)) { Favorites(d) }
        else -> SettingsCard { SplitSettings(d) }
    }
}

/** Las favoritas de la barra, en su orden: un toque en la x la quita. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Favorites(d: Dependencies) {
    val apps = rememberApps(d.apps).orEmpty()
    val favorites = remember(apps, d.prefs.dock, d.prefs.musicApp) { dockFavorites(d, apps) }
    FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        for (app in favorites) {
            Row(
                Modifier.clip(RoundedCornerShape(Radius.inner)).background(LocalColors.current.surfaceHigh).padding(start = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                AppIcon(d.apps, app, 32.dp)
                Label(app.name, Typography.body, maxLines = 1, modifier = Modifier.padding(horizontal = 10.dp).widthIn(max = 160.dp))
                IconButton(Icons.close, { d.prefs.dock = favorites.map { it.key } - app.key }, size = 44.dp)
            }
        }
    }
}

/** Las pestañas de Ajustes → Apps (Música 0, Radio 1…). */
private const val APPS_TABS = "apps"
