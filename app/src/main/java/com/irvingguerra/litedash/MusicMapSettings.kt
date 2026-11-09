package com.irvingguerra.litedash

import androidx.compose.foundation.layout.Column
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.irvingguerra.litedash.core.ui.Button
import com.irvingguerra.litedash.core.ui.Label
import com.irvingguerra.litedash.core.ui.LocalColors
import com.irvingguerra.litedash.core.ui.SwitchRow
import com.irvingguerra.litedash.core.ui.TextInput
import com.irvingguerra.litedash.core.ui.Typography
import com.irvingguerra.litedash.feature.apps.App
import com.irvingguerra.litedash.feature.apps.normalize
import com.irvingguerra.litedash.feature.apps.rememberApps
import com.irvingguerra.litedash.feature.music.SourceKind
import com.irvingguerra.litedash.feature.music.label
import com.irvingguerra.litedash.feature.player.Library
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Ajustes → Apps → Música → "Tu app de música": la que sale con su nombre junto a USB en la
 * música en grande y que despierta play; de las instaladas, o cualquiera con "Otra app"
 * (algunos reproductores no se anuncian como app de música). "Quitar" = ninguna: la música
 * muestra lo que suene.
 */
@Composable
fun MusicSettings(d: Dependencies) {
    val apps = rememberApps(d.apps).orEmpty()
    val chosen = d.prefs.musicApp
    // Preguntarle a Android cuáles son de música es una consulta entre procesos: fuera del hilo principal.
    val detected by produceState(emptySet<String>(), d.music) { value = withContext(Dispatchers.IO) { d.music.musicApps() } }
    val musicApps = remember(apps, chosen, detected) {
        val musicPackages = detected + MUSIC_PACKAGES + listOfNotNull(chosen)
        apps.filter { it.pkg in musicPackages }.distinctBy { it.pkg }
    }
    var other by remember { mutableStateOf(false) }
    FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        for (app in musicApps) Button(app.name, { d.music.choose(app.pkg) }, primary = chosen == app.pkg)
        Button(stringResource(R.string.music_other_app), { other = !other }, primary = other)
        if (chosen != null) Button(stringResource(R.string.source_remove), { d.music.choose(null) })
    }
    if (other) AppSearch(apps, chosen) { d.music.choose(it); other = false }
    Label(stringResource(R.string.music_help), Typography.small, LocalColors.current.textMuted)
}


/**
 * El reproductor USB de LiteDash (siempre activo, sin switch: es el de fábrica), si la pantalla no
 * deja leer la memoria, la carpeta de la música (selector de Android; sin carpeta, busca sola en
 * todas las memorias), y elegir USB al conectar una memoria (apagado de fábrica).
 */
@Composable
fun UsbPlayerCard(d: Dependencies) {
    val context = LocalContext.current
    val c = LocalColors.current
    var folder by remember { mutableStateOf(Library.folderName(context)) }
    val pick = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null) {
            Library.setFolder(context, uri)
            folder = Library.folderName(context)
        }
    }
    Label(stringResource(R.string.usb_player), Typography.subtitle, maxLines = 1)
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Column(Modifier.weight(1f)) {
            Label(stringResource(R.string.usb_folder), Typography.body)
            Label(folder ?: stringResource(R.string.usb_folder_auto), Typography.small, c.textMuted, 2)
        }
        Button(stringResource(R.string.source_choose), { runCatching { pick.launch(null) } })
        if (folder != null) Button(stringResource(R.string.source_remove), { Library.clearFolder(context); folder = null })
    }
    Label(stringResource(R.string.usb_player_help), Typography.small, c.textMuted)
    SwitchRow(stringResource(R.string.usb_auto_select), d.prefs.usbAutoSelect, { on ->
        d.prefs.usbAutoSelect = on
        OwnPlayer.refresh(d.music, d.prefs)
    }, stringResource(R.string.usb_auto_select_help))
}

/**
 * Pestaña "Radio" de Ajustes → Apps: la app de radio FM de la pantalla (lo principal; muchas
 * veces no hay internet en el auto) y, opcional, la radio por internet de LiteDash.
 */
@Composable
fun RadioSettings(d: Dependencies) {
    val apps = rememberApps(d.apps).orEmpty()
    var choosing by remember { mutableStateOf(false) }
    SettingsCard(stringResource(R.string.radio_fm_app), stringResource(R.string.radio_fm_app_help)) {
        SourceRow(d, SourceKind.RADIO, apps, choosing) { choosing = it }
    }
    SettingsCard { InternetRadioSwitch(d) }
}

/**
 * Renglón de una fuente de la pantalla: su nombre, la app enlazada (o "Sin enlazar"),
 * Elegir (abre el buscador debajo) y Quitar. [choosing] dice si el buscador está abierto.
 */
@Composable
private fun SourceRow(d: Dependencies, kind: SourceKind, apps: List<App>, choosing: Boolean, onChoosing: (Boolean) -> Unit) {
    val c = LocalColors.current
    val pkg = d.music.sourceApps[kind]
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Label(kind.label(), Typography.body, modifier = Modifier.weight(1f))
        Label(pkg?.let { p -> apps.firstOrNull { it.pkg == p }?.name ?: d.music.appName(p) } ?: stringResource(R.string.source_unlinked), Typography.small, c.textMuted, 1)
        Button(stringResource(R.string.source_choose), { onChoosing(!choosing) }, primary = choosing)
        if (pkg != null) Button(stringResource(R.string.source_remove), { d.music.setSource(kind, null) })
    }
    if (choosing) {
        Label(stringResource(R.string.source_choose_for, kind.label()), Typography.small, c.textMuted)
        AppSearch(apps, pkg) { d.music.setSource(kind, it); onChoosing(false) }
        Spacer(Modifier.height(6.dp))
    }
}

/** Interruptor de la radio por internet (Ajustes → Apps → Radio). */
@Composable
fun InternetRadioSwitch(d: Dependencies) {
    val set = rememberInternetRadioToggle(d)
    SwitchRow(stringResource(R.string.module_radio), d.prefs.internetRadio, set, stringResource(R.string.radio_internet_help))
}

/** Prender o apagar la radio por internet: al prender, desde Android 13 pide el permiso de avisos. */
@Composable
fun rememberInternetRadioToggle(d: Dependencies): (Boolean) -> Unit {
    val ask = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {}
    return { on ->
        d.prefs.internetRadio = on
        OwnPlayer.refresh(d.music, d.prefs)
        val notifications = Library.noticePermission
        if (on && notifications.isNotEmpty()) ask.launch(notifications.toTypedArray())
    }
}


/** Buscador de apps instaladas: escribir y tocar una la elige ([selected] va resaltada). */
@Composable
internal fun AppSearch(apps: List<App>, selected: String?, onPick: (String) -> Unit) {
    var query by remember { mutableStateOf("") }
    TextInput(query, { query = it }, stringResource(R.string.search_app), Modifier.fillMaxWidth(), icon = true)
    val q = normalize(query.trim())
    if (q.isNotEmpty()) {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            for (app in apps.filter { q in normalize(it.name) }.take(12)) {
                Button(app.name, { onPick(app.pkg) }, primary = selected == app.pkg)
            }
        }
    }
}

/** Sección "Mapa" de Ajustes: qué app de navegación abren el widget Mapa y el volante. */
@Composable
fun MapSettings(d: Dependencies) {
    val apps = rememberApps(d.apps).orEmpty()
    val mapApps = remember(apps) { NAVIGATION_PACKAGES.mapNotNull { pkg -> apps.firstOrNull { it.pkg == pkg } } }
    val selected = mapApp(d, apps)
    FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        for (app in mapApps) Button(app.name, { d.prefs.mapApp = app.key }, primary = app == selected)
    }
    Label(
        stringResource(if (mapApps.isEmpty()) R.string.map_no_apps else R.string.map_help),
        Typography.small,
        LocalColors.current.textMuted,
    )
}
