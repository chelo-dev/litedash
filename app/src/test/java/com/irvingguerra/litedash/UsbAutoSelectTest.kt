package com.irvingguerra.litedash

import com.irvingguerra.litedash.UsbAutoSelect.Action
import com.irvingguerra.litedash.UsbAutoSelect.Trigger
import org.junit.Assert.assertEquals
import org.junit.Test

class UsbAutoSelectTest {
    /** Lo de siempre: ajuste prendido, una memoria, nada sonando y el reproductor sin canción de ella. */
    private fun decide(
        enabled: Boolean = true,
        drives: Int = 1,
        ownBusy: Boolean = false,
        othersPlaying: Boolean = false,
        trigger: Trigger = Trigger.ADDED,
        ownHasDrive: Boolean = false,
        cued: Boolean = false,
    ) = UsbAutoSelect.decide(enabled, drives, ownBusy, othersPlaying, trigger, ownHasDrive, cued)

    @Test
    fun rule1SettingOff() = assertEquals(Action.NONE, decide(enabled = false))

    @Test
    fun rule2NotExactlyOneDrive() {
        assertEquals(Action.NONE, decide(drives = 0))
        assertEquals(Action.NONE, decide(drives = 2))
    }

    @Test
    fun rule3OwnPlayerPlayingOrLoading() = assertEquals(Action.NONE, decide(ownBusy = true))

    @Test
    fun rule4OtherSourcePlaying() = assertEquals(Action.NONE, decide(othersPlaying = true))

    @Test
    fun rule5IndexedRecuesTheCuedDrive() =
        assertEquals(Action.RECUE, decide(trigger = Trigger.INDEXED, ownHasDrive = true, cued = true))

    @Test
    fun rule6KeepsWhatTheUserPaused() {
        assertEquals(Action.NONE, decide(trigger = Trigger.INDEXED, ownHasDrive = true, cued = false))
        assertEquals(Action.NONE, decide(trigger = Trigger.ADDED, ownHasDrive = true, cued = true))
        assertEquals(Action.NONE, decide(trigger = Trigger.START, ownHasDrive = true))
    }

    @Test
    fun rule7Selects() {
        for (trigger in Trigger.entries) assertEquals(Action.SELECT, decide(trigger = trigger))
    }
}
