package com.irvingguerra.litedash.feature.music

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.irvingguerra.litedash.core.ui.Button
import com.irvingguerra.litedash.core.ui.Card
import com.irvingguerra.litedash.core.ui.Radius
import com.irvingguerra.litedash.core.ui.Colors
import com.irvingguerra.litedash.core.ui.Icon
import com.irvingguerra.litedash.core.ui.IconButton
import com.irvingguerra.litedash.core.ui.Icons
import com.irvingguerra.litedash.core.ui.Label
import com.irvingguerra.litedash.core.ui.LocalColors
import com.irvingguerra.litedash.core.ui.Typography

// Partes que comparten la pieza de música y la música en grande.

/** Carátula cuadrada con esquinas [radius]; sin carátula, [icon] (la música o la radio). */
@Composable
internal fun Artwork(song: Song?, modifier: Modifier, radius: Dp, icon: ImageVector = Icons.music) {
    val c = LocalColors.current
    BoxWithConstraints(modifier.clip(RoundedCornerShape(radius)).background(c.surfaceHigh), contentAlignment = Alignment.Center) {
        val bmp = song?.artwork
        if (bmp != null) {
            Image(bmp.asImageBitmap(), null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
        } else {
            Icon(icon, c.textMuted, (minOf(maxWidth, maxHeight) * 0.4f).coerceAtMost(56.dp))
        }
    }
}

/**
 * Anterior, play/pausa y siguiente; [compact] deja solo play/pausa. Con [loading]
 * (conectando) se ve pausa, porque tocarlo cancela la carga. [lens] es la vista que
 * controla: la música (por omisión) o la radio.
 */
@Composable
internal fun Controls(
    repo: MusicRepository,
    playing: Boolean,
    modifier: Modifier = Modifier,
    loading: Boolean = false,
    compact: Boolean = false,
    size: Dp = 56.dp,
    playSize: Dp = 56.dp,
    gap: Dp = 0.dp,
    lens: MusicRepository.Lens = repo.music,
) {
    val c = LocalColors.current
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        if (!compact) IconButton(Icons.previous, { lens.previous() }, size = size)
        if (!compact) Spacer(Modifier.width(gap))
        IconButton(if (playing || loading) Icons.pause else Icons.play, { lens.toggle() }, size = playSize, background = c.primary, color = c.onPrimary)
        if (!compact) Spacer(Modifier.width(gap))
        if (!compact) IconButton(Icons.next, { lens.next() }, size = size)
    }
}

/** La pista de avance: fondo y la parte recorrida ([fraction] de 0 a 1). */
@Composable
internal fun ProgressTrack(fraction: Float, height: Dp, modifier: Modifier = Modifier) {
    val c = LocalColors.current
    Box(modifier.fillMaxWidth().height(height).clip(RoundedCornerShape(height / 2)).background(c.line)) {
        Box(Modifier.fillMaxHeight().fillMaxWidth(fraction).background(c.primary))
    }
}

/**
 * Qué decir debajo del título cuando la app no manda artista: en Bluetooth, el celular
 * conectado (o que lo conecte); en lo demás, "sin datos".
 */
@Composable
internal fun subtitleFor(repo: MusicRepository, song: Song): String = when {
    song.artist.isNotBlank() -> song.artist
    song.source == SourceKind.BLUETOOTH -> repo.bluetoothDevice ?: stringResource(R.string.bluetooth_connect_phone)
    else -> stringResource(R.string.no_song_info)
}

/** Nombre corto de la fuente para los botones. */
@Composable
fun SourceKind.label(): String = stringResource(
    when (this) {
        SourceKind.USB -> R.string.source_usb
        SourceKind.BLUETOOTH -> R.string.source_bluetooth
        SourceKind.RADIO -> R.string.source_radio
    },
)

/**
 * Sub-pestañas de fuente como en un estéreo: USB (siempre: el reproductor de LiteDash), tu app de
 * música (si elegiste una) y Bluetooth (si hay app enlazada). Una sola activa: tocarla calla la que
 * sonaba y la pone a sonar; se marca por lo que eligió el usuario, no por lo último que sonó. Tocar
 * una cierra la biblioteca ([onSelect]) y la pone a sonar; USB sin nada que tocar abre su biblioteca
 * ([onBrowse]). Con la biblioteca abierta ([browsing]) se marca USB.
 */
@Composable
internal fun SourceRow(
    repo: MusicRepository,
    song: Song?,
    browsing: Boolean,
    onSelect: () -> Unit,
    onBrowse: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val c = LocalColors.current
    val active = repo.active
    val usb = browsing || active?.let(repo::isOwn) == true || song != null && repo.isOwn(song.pkg)
    val bluetooth = repo.bluetoothApp()
    // Si tu app de música es la de Bluetooth, sale una vez (como Bluetooth).
    val app = repo.preferred?.takeIf { it != bluetooth && !repo.isOwn(it) }
    // Sin nada tocado, manda tu app de música; si no hay, lo que suene.
    val pick = active ?: app ?: song?.pkg
    Row(
        modifier.fillMaxWidth().clip(RoundedCornerShape(50)).background(c.surfaceHigh).padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Tab(SourceKind.USB.label(), usb, c) {
            onSelect()
            repo.playOwn(onEmpty = onBrowse)
        }
        if (app != null) Tab(repo.appName(app), !usb && pick == app, c) {
            onSelect()
            repo.switchTo(app)
        }
        if (bluetooth != null) Tab(SourceKind.BLUETOOTH.label(), !usb && pick == bluetooth, c) {
            onSelect()
            repo.switchTo(bluetooth)
        }
    }
}

/** Sub-pestaña de fuente, redonda; la fila la reparte a lo ancho y una sola queda marcada. */
@Composable
private fun RowScope.Tab(text: String, selected: Boolean, c: Colors, onClick: () -> Unit) {
    Box(
        Modifier
            .weight(1f)
            .clip(RoundedCornerShape(50))
            .background(if (selected) c.primary else Color.Transparent)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center,
    ) { Label(text, Typography.small.copy(fontWeight = FontWeight.SemiBold), if (selected) c.onPrimary else c.text, 1) }
}

/** La vista cuenta como usuaria de [repo] mientras está en pantalla (escucha solo entonces). */
@Composable
internal fun UseRepo(repo: MusicRepository) {
    DisposableEffect(repo) {
        repo.use()
        onDispose { repo.release() }
    }
}

/**
 * La base de las piezas Música y Radio. Se acomoda a su tamaño: chica es una fila con carátula
 * y play (o los tres botones si cabe); alta y no tan ancha, carátula arriba y lo demás abajo.
 * Tocarla (fuera de los botones) la amplía con [onExpand]. [revision] cambia al volver a la
 * app, para notar si el usuario acaba de dar el permiso. [text] es lo escrito y [controls] los
 * botones (con [Modifier] y si van compactos).
 */
@Composable
internal fun MediaTile(
    repo: MusicRepository,
    revision: Int,
    onExpand: () -> Unit,
    modifier: Modifier,
    song: Song?,
    icon: ImageVector,
    title: String,
    explain: String,
    text: @Composable (tall: Boolean) -> Unit,
    controls: @Composable (Modifier, compact: Boolean) -> Unit,
) {
    UseRepo(repo)
    val permission = remember(revision) { repo.hasPermission().also { if (it) repo.recheck() } }
    Card(modifier.fillMaxSize(), onClick = if (permission) onExpand else null) {
        if (!permission) return@Card NoPermission(repo, icon, title, explain)
        BoxWithConstraints(Modifier.fillMaxSize().padding(10.dp)) {
            val tall = maxHeight > 150.dp
            if (tall && maxWidth < maxHeight * 1.5f) {
                // Una columna del tablero: carátula arriba, el resto abajo y centrado.
                return@BoxWithConstraints Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
                    Artwork(song, Modifier.fillMaxWidth().weight(1f), Radius.inner, icon)
                    Column(Modifier.fillMaxWidth().padding(start = 6.dp, end = 6.dp, top = 14.dp)) { text(true) }
                    controls(Modifier.padding(top = 8.dp), false)
                }
            }
            // Angosta (la fila de abajo en 7"): solo play/pausa, para que el título se lea.
            val compact = !tall && maxWidth < 400.dp
            // Carátula cuadrada con esquinas, dentro de la tarjeta, si deja lugar al título.
            val art = minOf(maxHeight, 240.dp)
            val withArtwork = maxWidth - art >= if (compact) 150.dp else 220.dp
            Row(Modifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {
                if (withArtwork) Artwork(song, Modifier.size(art), Radius.inner, icon)
                Column(Modifier.weight(1f).padding(horizontal = 12.dp), verticalArrangement = Arrangement.Center) {
                    text(tall)
                    if (tall) {
                        Spacer(Modifier.height(12.dp))
                        controls(Modifier, false)
                    }
                }
                if (!tall) controls(Modifier, compact)
            }
        }
    }
}

/** Sin acceso a notificaciones: qué hace la pieza y el botón para activarlo. */
@Composable
private fun NoPermission(repo: MusicRepository, icon: ImageVector, title: String, explain: String) {
    val c = LocalColors.current
    Row(
        Modifier.fillMaxSize().padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Icon(icon, c.accent, 32.dp)
        Column(Modifier.weight(1f)) {
            Label(title, Typography.subtitle, maxLines = 1)
            Label(explain, Typography.small, c.textMuted, maxLines = 2)
        }
        Button(stringResource(R.string.enable), { repo.requestPermission() }, primary = true)
    }
}

/** Título de lo que suena y, debajo, el error, "Conectando…" o el artista y la app. */
@Composable
internal fun SongLines(repo: MusicRepository, song: Song, tall: Boolean) {
    val c = LocalColors.current
    Label(song.title, if (tall) Typography.title else Typography.subtitle, maxLines = 1)
    val error = song.error
    when {
        error != null -> Label(error, Typography.small, c.danger, maxLines = 1)
        song.buffering -> Label(stringResource(R.string.player_connecting), Typography.small, c.textMuted, maxLines = 1)
        else -> Label(
            // Sin canción (Bluetooth sin datos) el título ya es la app: no repetirla. El
            // reproductor propio no agrega "LiteDash".
            listOf(subtitleFor(repo, song), if (repo.isOwn(song.pkg)) "" else song.app)
                .filter { it.isNotBlank() && it != song.title }.distinct().joinToString(" · "),
            Typography.small, c.textMuted, maxLines = 1,
        )
    }
}

/**
 * La base de la música y la radio en grande: tarjeta a pantalla completa con margen. [content]
 * recibe si es ancha (carátula a un lado) y el ancho máximo de la carátula.
 */
@Composable
internal fun ExpandedCard(repo: MusicRepository, modifier: Modifier, content: @Composable ColumnScope.(wide: Boolean, artworkMax: Dp) -> Unit) {
    UseRepo(repo)
    Card(modifier.fillMaxSize()) {
        BoxWithConstraints(Modifier.fillMaxSize().padding(20.dp)) {
            val wide = maxWidth > 520.dp && maxWidth > maxHeight
            // La portada cuadrada, pero sin pasar del 40 % del ancho: el título y los botones
            // siguen cabiendo a su lado.
            val artworkMax = maxWidth * 0.4f
            Column(Modifier.fillMaxSize()) { content(wide, artworkMax) }
        }
    }
}

/** Carátula y [details] (título y botones): lado a lado si es [wide], si no uno arriba del otro. */
@Composable
internal fun ColumnScope.NowPlaying(song: Song?, icon: ImageVector, wide: Boolean, artworkMax: Dp, details: @Composable (Modifier) -> Unit) {
    if (wide) {
        Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
            Artwork(song, Modifier.widthIn(max = artworkMax).fillMaxHeight().aspectRatio(1f, matchHeightConstraintsFirst = false), Radius.card, icon)
            Spacer(Modifier.width(24.dp))
            details(Modifier.weight(1f))
        }
    } else {
        Column(Modifier.weight(1f).fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
            Artwork(song, Modifier.weight(1f, fill = false).aspectRatio(1f, matchHeightConstraintsFirst = true), Radius.card, icon)
            Spacer(Modifier.height(16.dp))
            details(Modifier.fillMaxWidth())
        }
    }
}

/** Título y subtítulo grandes, con dos líneas reservadas cada uno: los botones no se mueven. */
@Composable
internal fun BigTitle(title: String, subtitle: String, error: Boolean) {
    val c = LocalColors.current
    Box(Modifier.heightIn(min = 68.dp), contentAlignment = Alignment.BottomStart) {
        Label(title, Typography.title.copy(fontSize = 26.sp), maxLines = 2)
    }
    Spacer(Modifier.height(4.dp))
    Box(Modifier.heightIn(min = 48.dp)) {
        Label(subtitle, Typography.subtitle, if (error) c.danger else c.textMuted, maxLines = 2)
    }
}

/** Botón redondo del encabezado de la vista en grande (lista, abrir app, cerrar). */
@Composable
internal fun HeaderButton(icon: ImageVector, onClick: () -> Unit) {
    IconButton(icon, onClick, size = 48.dp, background = LocalColors.current.surfaceHigh)
}
