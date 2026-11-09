package com.irvingguerra.litedash

import android.annotation.SuppressLint
import android.content.Context
import android.os.Handler
import android.os.Looper
import com.irvingguerra.litedash.feature.music.DriveEvent
import com.irvingguerra.litedash.feature.music.Drives
import com.irvingguerra.litedash.feature.music.MusicRepository
import com.irvingguerra.litedash.feature.player.Library
import com.irvingguerra.litedash.feature.player.Player
import com.irvingguerra.litedash.feature.player.PlayerSource
import com.irvingguerra.litedash.feature.player.PlayerStore
import com.irvingguerra.litedash.feature.player.Track

/**
 * "Elegir USB al conectar una memoria" (Ajustes → Apps → Música → Reproductor USB): con una sola
 * memoria y nada sonando, deja la USB elegida con su canción en pausa. Nunca suena, pausa ni avisa.
 * Solo vigila las memorias entre [start] y [stop] (los llama [OwnPlayer] según el ajuste).
 * Guarda el contexto de la aplicación (vive lo mismo que el proceso): no es una fuga.
 */
@SuppressLint("StaticFieldLeak")
internal object UsbAutoSelect {
    /** Qué hacer: nada, elegir la memoria o volver a cargar la cola (ya indexada) con la misma canción. */
    enum class Action { NONE, SELECT, RECUE }

    /**
     * Por qué se evalúa: [START] = empieza a vigilar (arranque de LiteDash o se prendió el ajuste),
     * [ADDED] = llegó una memoria, [INDEXED] = Android terminó de indexarla.
     */
    enum class Trigger { START, ADDED, INDEXED }

    /**
     * Qué hacer con la situación del momento. [ownBusy] = el reproductor propio suena o carga (USB o
     * radio); [othersPlaying] = suena otra fuente; [ownHasDrive] = la canción actual del reproductor es
     * de esa memoria; [cued] = sigue solo cargada ([com.irvingguerra.litedash.feature.player.PlayerState.cued]).
     */
    fun decide(
        enabled: Boolean,
        drives: Int,
        ownBusy: Boolean,
        othersPlaying: Boolean,
        trigger: Trigger,
        ownHasDrive: Boolean,
        cued: Boolean,
    ): Action = when {
        !enabled -> Action.NONE
        // Con varias, cuál elegir es del selector (SPEC 03).
        drives != 1 -> Action.NONE
        ownBusy -> Action.NONE
        // Pausar lo que suena es de la SPEC 02.
        othersPlaying -> Action.NONE
        // Se eligió antes de que Android la indexara: ahora con la lista completa.
        trigger == Trigger.INDEXED && ownHasDrive && cued -> Action.RECUE
        // El usuario ya la tocó y la pausó: no se pierde la posición.
        ownHasDrive -> Action.NONE
        else -> Action.SELECT
    }

    // --- El vigilante: todo en el hilo principal. ---

    private val main = Handler(Looper.getMainLooper())
    private var app: Context? = null
    private var music: MusicRepository? = null

    // Sube con cada evaluación que lee la memoria y al parar: una lectura vieja ya no carga nada.
    private var runs = 0

    private val listener: (DriveEvent) -> Unit = { event ->
        if (event is DriveEvent.Added) evaluate(Trigger.ADDED)
    }

    /** Empieza a vigilar las memorias y evalúa la que ya esté puesta ([Trigger.START]). */
    fun start(context: Context, music: MusicRepository) {
        if (this.music != null) return
        app = context.applicationContext
        this.music = music
        Drives.watch(context, listener)
        evaluate(Trigger.START)
    }

    /** Deja de vigilar (y descarta una lectura en curso). */
    fun stop() {
        if (music == null) return
        Drives.unwatch(listener)
        runs++
        app = null
        music = null
    }

    private fun evaluate(trigger: Trigger) {
        val context = app ?: return
        val music = music ?: return
        // Sin permiso no hay cómo leer la memoria (la biblioteca lo pide al tocar USB).
        if (!Library.hasPermission(context)) return
        val drives = Drives.list(context)
        val s = Player.state
        val drive = drives.singleOrNull()
        val action = decide(
            // Solo vigila con el ajuste prendido.
            enabled = true,
            drives = drives.size,
            ownBusy = s.playing || s.buffering,
            othersPlaying = music.anythingPlaying(),
            trigger = trigger,
            ownHasDrive = drive != null && (s.current as? Track)?.drive == drive.id,
            cued = s.cued,
        )
        if (action != Action.NONE) load(context, music, action)
    }

    /**
     * Lee la memoria en un hilo y, si tiene canciones y nada cambió mientras (ni un toque a USB, ni
     * otra cosa pedida al reproductor), la deja cargada en pausa y marca USB. [Action.RECUE]
     * conserva la canción actual; [Action.SELECT], la última que sonó o la primera.
     */
    private fun load(context: Context, music: MusicRepository, action: Action) {
        val run = ++runs
        val generation = OwnPlayer.generation
        val asked = Player.requests
        val current = Player.state.current?.key
        val last = PlayerStore(context).last(PlayerSource.USB)
        Thread({
            val tracks = Library.find(context).second
            main.post {
                if (run != runs || tracks.isEmpty()) return@post
                if (OwnPlayer.generation != generation || Player.requests != asked || OwnPlayer.startingUsb) return@post
                val keep = if (action == Action.RECUE) current else last
                Player.cue(PlayerSource.USB, tracks, tracks.indexOfFirst { it.key == keep }.coerceAtLeast(0))
                music.selectOwn()
            }
        }, "usb-auto-select").start()
    }
}
