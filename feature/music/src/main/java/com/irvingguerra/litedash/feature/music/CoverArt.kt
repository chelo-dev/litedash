package com.irvingguerra.litedash.feature.music

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Handler
import android.os.Looper
import android.util.LruCache
import com.irvingguerra.litedash.core.ui.Https
import org.json.JSONObject
import java.io.File
import java.net.URLEncoder
import java.security.MessageDigest
import java.util.Collections
import java.util.LinkedHashSet
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors

/**
 * Carátula de respaldo por título+artista: iTunes Search (gratis, sin llave). Cubre el caso de la
 * música por Bluetooth donde el celular manda el título y el artista pero no la carátula (AVRCP
 * no la trae). Se cachea en disco (por si vuelve a sonar sin red) y en memoria (sin re-buscar en
 * cada cambio de posición).
 *
 * - [cached] es un vistazo O(1) en memoria, seguro en el hilo principal (sin I/O).
 * - [fetch] busca en un hilo (disco → iTunes → descargar) y avisa en el principal. Una misma
 *   canción se busca una sola vez; si no se encontró, se recuerda para no insistir sin parar.
 */
internal object CoverArt {
    private const val TAG = "cover-art"

    /** Un solo hilo: las carátulas son pocas y no deben pelearse por la red. */
    private val io by lazy {
        Executors.newSingleThreadExecutor { r -> Thread(r, TAG).apply { isDaemon = true } }
    }
    private val main = Handler(Looper.getMainLooper())

    /** Carátulas ya resueltas, por canción. 8 MB bastan para unas cuantas carátulas de 600×600. */
    private val memory = object : LruCache<String, Bitmap>(8 * 1024 * 1024) {
        override fun sizeOf(key: String, value: Bitmap): Int = value.byteCount
    }

    /** Canciones ya buscadas sin carátula (para no repetir la petición en cada lectura). */
    private val missing = LinkedHashSet<String>()

    /** Búsquedas en vuelo, para no encolar la misma canción varias veces. */
    private val inFlight = Collections.newSetFromMap(ConcurrentHashMap<String, Boolean>())

    /** La carátula ya resuelta de esta canción, o null si aún no se busca (o no se encontró). */
    fun cached(title: String, artist: String): Bitmap? = cacheKey(title, artist)?.let(memory::get)

    /** Busca la carátula y avisa en el hilo principal con [onReady] (null si no hay). */
    fun fetch(context: Context, title: String, artist: String, onReady: (Bitmap?) -> Unit) {
        val key = cacheKey(title, artist) ?: return
        memory.get(key)?.let { onReady(it); return }
        if (key in missing || !inFlight.add(key)) return
        val app = context.applicationContext
        io.execute {
            val bmp = read(app, key, title, artist)
            inFlight.remove(key)
            main.post {
                if (bmp != null) memory.put(key, bmp) else rememberMissing(key)
                onReady(bmp)
            }
        }
    }

    private fun rememberMissing(key: String) {
        if (!missing.add(key)) return
        while (missing.size > 512) missing.remove(missing.first())
    }

    /** Disco primero (rápido, sirve sin red); si no, iTunes. Devuelve el bitmap decodificado. */
    private fun read(context: Context, key: String, title: String, artist: String): Bitmap? {
        val file = file(context, key)
        if (file.length() > 0) BitmapFactory.decodeFile(file.path)?.let { return it }
        val url = search(context, title, artist) ?: return null
        val bytes = runCatching { Https.get(context, url).inputStream.use { it.readBytes() } }.getOrNull()
            ?: return null
        val bmp = BitmapFactory.decodeByteArray(bytes, 0, bytes.size) ?: return null
        runCatching { file.parentFile?.mkdirs(); file.writeBytes(bytes) }
        return bmp
    }

    /** URL de la carátula de 600×600 que iTunes da para la primera coincidencia, o null. */
    private fun search(context: Context, title: String, artist: String): String? = runCatching {
        val term = URLEncoder.encode("$artist $title", "UTF-8")
        val url = "https://itunes.apple.com/search?term=$term&media=music&entity=song&limit=1"
        val text = Https.get(context, url, "application/json").inputStream.use { String(it.readBytes(), Charsets.UTF_8) }
        JSONObject(text).optJSONArray("results")?.optJSONObject(0)?.optString("artworkUrl100")
            ?.takeIf { it.isNotEmpty() }
            ?.replace("100x100", "600x600")
    }.getOrNull()

    private fun file(context: Context, key: String): File = File(File(context.cacheDir, TAG), "$key.jpg")

    /** Clave estable por canción (título+artista, sin importar mayúsculas o signos). */
    private fun cacheKey(title: String, artist: String): String? {
        val raw = "$artist — $title".trim().lowercase()
        if (raw.isBlank()) return null
        return MessageDigest.getInstance("SHA-256").digest(raw.toByteArray())
            .joinToString("") { "%02x".format(it) }
    }
}
