package com.irvingguerra.litedash

/**
 * "Elegir USB al conectar una memoria" (Ajustes → Apps → Música → Reproductor USB): con una sola
 * memoria y nada sonando, deja la USB elegida con su canción en pausa. Nunca suena, pausa ni avisa.
 */
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
}
