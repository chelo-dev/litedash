package com.irvingguerra.litedash.feature.music

import org.junit.Assert.assertEquals
import org.junit.Test

class DrivesDiffTest {
    private val sd = Drive(name = "Tarjeta SD", volume = "1234-abcd", path = "/storage/1234-ABCD", id = "1234-ABCD")
    private val usb = Drive(name = "Memoria USB", volume = "5678-ef01", path = "/storage/5678-EF01", id = "5678-EF01")

    @Test
    fun oneArrives() = assertEquals(listOf(DriveEvent.Added(usb)), Drives.diff(listOf(sd), listOf(sd, usb)))

    @Test
    fun oneLeaves() = assertEquals(listOf(DriveEvent.Removed(usb)), Drives.diff(listOf(sd, usb), listOf(sd)))

    @Test
    fun sameDriveRemovedAndPutBack() {
        assertEquals(listOf(DriveEvent.Removed(usb)), Drives.diff(listOf(usb), emptyList()))
        // Al volver es otra lectura de Android (otro objeto) pero la misma memoria.
        val back = Drive(name = usb.name, volume = usb.volume, path = usb.path, id = usb.id)
        assertEquals(listOf(DriveEvent.Added(back)), Drives.diff(emptyList(), listOf(back)))
        // Con la memoria puesta en las dos lecturas no pasó nada.
        assertEquals(emptyList<DriveEvent>(), Drives.diff(listOf(usb), listOf(back)))
    }

    @Test
    fun twoWithoutUuidAreDifferentDrives() {
        // Sin UUID, el id es la carpeta raíz: el mismo nombre no las confunde.
        val a = Drive(name = "Memoria USB", volume = null, path = "/mnt/media_rw/usb1", id = "/mnt/media_rw/usb1")
        val b = Drive(name = "Memoria USB", volume = null, path = "/mnt/media_rw/usb2", id = "/mnt/media_rw/usb2")
        assertEquals(listOf(DriveEvent.Added(b)), Drives.diff(listOf(a), listOf(a, b)))
        assertEquals(listOf(DriveEvent.Removed(a)), Drives.diff(listOf(a, b), listOf(b)))
    }
}
