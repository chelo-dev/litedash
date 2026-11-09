package com.irvingguerra.litedash.feature.music

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothClass
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.Build
import android.os.Environment
import android.os.storage.StorageManager
import android.provider.MediaStore
import androidx.core.content.ContextCompat
import androidx.core.content.IntentCompat
import java.io.File

/** Perfil Bluetooth "A2DP sink" (la pantalla recibe la música del celular); no es público en el SDK. */
private const val A2DP_SINK = 11

/**
 * Celular conectado por Bluetooth y memoria USB puesta en la pantalla. Avisa con el
 * nombre (o null al quitarse) y solo escucha entre [start] y [stop]. Del Bluetooth solo
 * cuentan los celulares y lo que se conecta para música (A2DP): un adaptador OBD no.
 */
internal class DeviceWatcher(
    private val context: Context,
    private val onBluetooth: (String?) -> Unit,
    private val onUsb: (String?) -> Unit,
) {
    // El celular que se avisó (para no borrarlo cuando se desconecta otro aparato).
    private var phone: String? = null

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(c: Context, i: Intent) {
            when (i.action) {
                BluetoothDevice.ACTION_ACL_CONNECTED -> {
                    val d = IntentCompat.getParcelableExtra(i, BluetoothDevice.EXTRA_DEVICE, BluetoothDevice::class.java)
                    if (isPhone(d)) setPhone(deviceName(d))
                }
                BluetoothDevice.ACTION_ACL_DISCONNECTED -> left(i)
                A2DP_CHANGED, A2DP_SINK_CHANGED -> {
                    val d = IntentCompat.getParcelableExtra(i, BluetoothDevice.EXTRA_DEVICE, BluetoothDevice::class.java)
                    if (i.getIntExtra(BluetoothProfile.EXTRA_STATE, -1) == BluetoothProfile.STATE_CONNECTED) setPhone(deviceName(d)) else left(i)
                }
            }
        }
    }

    // Las memorias las escucha [Drives]: cualquier cambio (también el indexado) vuelve a nombrar la memoria.
    private val drives: (DriveEvent) -> Unit = { onUsb(Drives.mounted(context)) }

    private fun setPhone(name: String?) {
        if (name == null) return
        phone = name
        onBluetooth(name)
    }

    // Se fue un aparato: si era el celular, ya no hay.
    private fun left(i: Intent) {
        val name = deviceName(IntentCompat.getParcelableExtra(i, BluetoothDevice.EXTRA_DEVICE, BluetoothDevice::class.java))
        if (name != null && name == phone) {
            phone = null
            onBluetooth(null)
        }
    }

    fun start() {
        val filter = IntentFilter().apply {
            addAction(BluetoothDevice.ACTION_ACL_CONNECTED)
            addAction(BluetoothDevice.ACTION_ACL_DISCONNECTED)
            addAction(A2DP_CHANGED)
            addAction(A2DP_SINK_CHANGED)
        }
        // Son avisos del sistema: los receptores deben aceptar avisos de fuera de la app.
        ContextCompat.registerReceiver(context, receiver, filter, ContextCompat.RECEIVER_EXPORTED)
        // [Drives] avisa también cuando Android termina de indexar la memoria: la biblioteca que se leyó
        // a medias se relee. Empezar a escuchar no sube [Drives.revision]: si mientras no se escuchaba
        // cambió la memoria, la biblioteca lo nota por su identificador (va en la llave de su caché);
        // abrir o cerrar la música no la hace releer.
        Drives.watch(context, drives)
        onUsb(Drives.mounted(context))
        connectedPhone()
    }

    fun stop() {
        runCatching { context.unregisterReceiver(receiver) }
        Drives.unwatch(drives)
    }

    /**
     * El celular ya conectado al abrir la pieza: se pregunta al perfil de música
     * (A2DP; en las pantallas, el "sink"). Desde Android 12 hace falta el permiso
     * BLUETOOTH_CONNECT (se da en el Taller); sin él, solo se sabrá al reconectarse.
     */
    private fun connectedPhone() {
        val adapter = context.getSystemService(BluetoothManager::class.java)?.adapter ?: return
        if (!adapter.isEnabled || !canSeeDevices()) return
        for (profile in intArrayOf(A2DP_SINK, BluetoothProfile.A2DP)) {
            runCatching {
                adapter.getProfileProxy(context, object : BluetoothProfile.ServiceListener {
                    override fun onServiceConnected(p: Int, proxy: BluetoothProfile) {
                        setPhone(runCatching { proxy.connectedDevices.firstOrNull() }.getOrNull()?.let(::deviceName))
                        adapter.closeProfileProxy(p, proxy)
                    }

                    override fun onServiceDisconnected(p: Int) = Unit
                }, profile)
            }
        }
    }

    /** Desde Android 12, el nombre del celular (y los avisos de conexión) piden BLUETOOTH_CONNECT. */
    private fun canSeeDevices(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.S ||
            context.checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED

    @SuppressLint("MissingPermission") // se revisa con canSeeDevices()
    private fun isPhone(d: BluetoothDevice?): Boolean =
        d != null && canSeeDevices() && runCatching { d.bluetoothClass?.majorDeviceClass == BluetoothClass.Device.Major.PHONE }.getOrDefault(false)

    @SuppressLint("MissingPermission") // se revisa con canSeeDevices()
    private fun deviceName(d: BluetoothDevice?): String? {
        if (d == null || !canSeeDevices()) return null
        return runCatching { d.name?.takeIf { it.isNotBlank() } ?: d.address }.getOrNull()
    }

    private companion object {
        /** El celular se conectó (o se fue) para música. */
        const val A2DP_CHANGED = "android.bluetooth.a2dp.profile.action.CONNECTION_STATE_CHANGED"
        /** Lo mismo en las pantallas que reciben la música del celular (no es público en el SDK). */
        const val A2DP_SINK_CHANGED = "android.bluetooth.a2dp-sink.profile.action.CONNECTION_STATE_CHANGED"
    }
}

/** Una memoria USB (o tarjeta) conectada a la pantalla. */
class Drive(
    /** Nombre que muestra Android ("Memoria USB SanDisk"); genérico en Android 6. */
    val name: String,
    /** Nombre del volumen en MediaStore (API 29+) o null. */
    val volume: String?,
    /** Carpeta raíz de la memoria (para filtrar por ruta antes de la API 29); null si no se sabe. */
    val path: String?,
    /**
     * Identificador estable de la memoria (para recordar lo último que sonó): su UUID; sin él,
     * su carpeta raíz (dos memorias sin UUID tienen el mismo nombre); y si tampoco, su nombre.
     */
    val id: String,
)

/** Lo que pasó con una memoria; se entrega en el hilo principal. */
sealed interface DriveEvent {
    /** Se puso (o se montó) [drive]. */
    data class Added(val drive: Drive) : DriveEvent

    /** Se quitó (o se desmontó) [drive]. */
    data class Removed(val drive: Drive) : DriveEvent

    /** Android terminó de indexar la memoria en [path] (ACTION_MEDIA_SCANNER_FINISHED). */
    data class Indexed(val path: String?) : DriveEvent

    /** Android vio una memoria que no pudo montar (ACTION_MEDIA_UNMOUNTABLE). Sin uso hasta la SPEC 02. */
    data class Unmountable(val path: String?) : DriveEvent
}

/**
 * Memorias USB (y tarjetas) conectadas a la pantalla: las usan la pieza y el reproductor propio.
 * Es el único que escucha los avisos de Android de las memorias, y solo mientras alguien lo
 * pidió con [watch] (apagado no registra nada).
 */
object Drives {
    /** Sube con cada aviso de una memoria mientras alguien escucha (la biblioteca la usa para no releerla). */
    @Volatile
    var revision = 0
        private set

    // Todo en el hilo principal: ahí llaman [watch]/[unwatch] y ahí llegan los avisos.
    private val listeners = mutableListOf<(DriveEvent) -> Unit>()
    private var app: Context? = null
    // Las memorias del último aviso, para saber cuál llegó o se fue.
    private var known: List<Drive> = emptyList()
    // La memoria que llegó al último (para el aviso "Memoria USB conectada").
    @Volatile
    private var lastAdded: String? = null

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(c: Context, i: Intent) {
            val app = app ?: return
            revision++
            val now = list(app)
            val events = diff(known, now).toMutableList()
            known = now
            events.lastOrNull { it is DriveEvent.Added }?.let { lastAdded = (it as DriveEvent.Added).drive.id }
            when (i.action) {
                Intent.ACTION_MEDIA_SCANNER_FINISHED -> events += DriveEvent.Indexed(i.data?.path)
                Intent.ACTION_MEDIA_UNMOUNTABLE -> events += DriveEvent.Unmountable(i.data?.path)
            }
            for (event in events) listeners.toList().forEach { it(event) }
        }
    }

    /**
     * Avisar a [listener] lo que pase con las memorias (en el hilo principal). El primer usuario
     * registra el receptor; no se relee nada al empezar: lo ya puesto no se avisa como [DriveEvent.Added].
     */
    fun watch(context: Context, listener: (DriveEvent) -> Unit) {
        if (listener in listeners) return
        listeners += listener
        if (listeners.size > 1) return
        val app = context.applicationContext
        this.app = app
        known = list(app)
        // Son avisos del sistema: el receptor debe aceptar avisos de fuera de la app.
        ContextCompat.registerReceiver(app, receiver, mediaFilter(), ContextCompat.RECEIVER_EXPORTED)
    }

    /** Dejar de avisar a [listener]; con el último, se quita el receptor. */
    fun unwatch(listener: (DriveEvent) -> Unit) {
        if (!listeners.remove(listener) || listeners.isNotEmpty()) return
        app?.let { runCatching { it.unregisterReceiver(receiver) } }
        app = null
        known = emptyList()
    }

    /**
     * Los avisos de Android al poner o quitar una memoria, al terminar de indexarla (al reconectar
     * tarda unos segundos) y al no poder montarla.
     */
    private fun mediaFilter() = IntentFilter().apply {
        addAction(Intent.ACTION_MEDIA_MOUNTED)
        addAction(Intent.ACTION_MEDIA_UNMOUNTED)
        addAction(Intent.ACTION_MEDIA_EJECT)
        addAction(Intent.ACTION_MEDIA_BAD_REMOVAL)
        addAction(Intent.ACTION_MEDIA_SCANNER_FINISHED)
        addAction(Intent.ACTION_MEDIA_UNMOUNTABLE)
        addDataScheme("file")
    }

    /** Memorias montadas ahora (sin la del propio equipo). */
    fun list(context: Context): List<Drive> = runCatching {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            val volumes = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) MediaStore.getExternalVolumeNames(context) else emptySet()
            context.getSystemService(StorageManager::class.java).storageVolumes
                .filter { it.isRemovable && it.state == Environment.MEDIA_MOUNTED }
                .map { v ->
                    val uuid = v.uuid
                    val path = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) v.directory?.path else uuid?.let { "/storage/$it" }
                    Drive(
                        name = v.getDescription(context),
                        volume = uuid?.lowercase()?.takeIf { it in volumes },
                        path = path,
                        id = uuid ?: path ?: v.getDescription(context),
                    )
                }
        } else {
            // Android 6 no lista los volúmenes: las memorias se montan como carpetas de /storage
            // (solo las removibles: ahí también hay enlaces a la memoria interna).
            File("/storage").listFiles().orEmpty()
                .filter { it.name != "emulated" && it.name != "self" && Environment.getExternalStorageState(it) == Environment.MEDIA_MOUNTED }
                .filter { runCatching { Environment.isExternalStorageRemovable(it) }.getOrDefault(false) }
                .map { Drive(name = context.getString(R.string.usb_drive), volume = null, path = it.path, id = it.name) }
        }
    }.getOrDefault(emptyList())

    /**
     * Nombre de la memoria que llegó al último y sigue puesta (no la SD o la partición de mapas
     * que ya estaba); si no se sabe, la primera conectada; null si no hay.
     */
    fun mounted(context: Context): String? {
        val drives = list(context)
        return (drives.firstOrNull { it.id == lastAdded } ?: drives.firstOrNull())?.name
    }

    /**
     * Qué memorias se fueron y cuáles llegaron entre [before] y [after], comparando por [Drive.id]
     * (el nombre se repite entre memorias). Primero las que se fueron, luego las que llegaron.
     */
    internal fun diff(before: List<Drive>, after: List<Drive>): List<DriveEvent> {
        val beforeIds = before.mapTo(HashSet()) { it.id }
        val afterIds = after.mapTo(HashSet()) { it.id }
        return before.filter { it.id !in afterIds }.map { DriveEvent.Removed(it) } +
            after.filter { it.id !in beforeIds }.map { DriveEvent.Added(it) }
    }
}
