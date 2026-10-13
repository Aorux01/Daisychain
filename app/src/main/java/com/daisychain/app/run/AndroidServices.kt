package com.daisychain.app.run

import android.Manifest
import android.annotation.SuppressLint
import android.content.ContentUris
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.location.Geocoder
import android.location.Location
import android.location.LocationManager
import android.media.MediaRecorder
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.Uri
import android.net.wifi.WifiManager
import android.os.Build
import android.os.Bundle
import android.os.CancellationSignal
import android.provider.CalendarContract
import android.provider.ContactsContract
import android.provider.MediaStore
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.telephony.SmsManager
import android.widget.Toast
import androidx.core.content.ContextCompat
import com.daisychain.engine.run.ActionFailed
import com.daisychain.engine.run.CalendarEventInfo
import com.daisychain.engine.run.ContactInfo
import com.daisychain.engine.run.ImageEdit
import com.daisychain.engine.run.ImageResult
import com.daisychain.engine.run.LocationInfo
import com.daisychain.engine.run.PickedFile
import com.daisychain.engine.spec.t
import com.google.android.gms.tasks.Task
import com.google.mlkit.common.model.DownloadConditions
import com.google.mlkit.nl.languageid.LanguageIdentification
import com.google.mlkit.nl.translate.TranslateLanguage
import com.google.mlkit.nl.translate.Translation
import com.google.mlkit.nl.translate.TranslatorOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import com.google.zxing.BarcodeFormat
import com.google.zxing.qrcode.QRCodeWriter
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeout
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.Locale
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

private fun Context.has(permission: String) = ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED

private fun needs(en: String, fr: String) = ActionFailed(t(en, fr))

private suspend fun <T> Task<T>.await(): T = suspendCancellableCoroutine { continuation ->
    addOnSuccessListener { continuation.resume(it) }
    addOnFailureListener { continuation.resumeWithException(it) }
}

/** Where the phone is. */
internal object LocationHelper {
    @SuppressLint("MissingPermission")
    suspend fun current(context: Context): LocationInfo {
        if (!context.has(Manifest.permission.ACCESS_FINE_LOCATION) && !context.has(Manifest.permission.ACCESS_COARSE_LOCATION)) {
            throw needs("Location permission is required", "L'autorisation de localisation est requise")
        }
        val manager = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
        val candidates = buildList {
            if (Build.VERSION.SDK_INT >= 31) add(LocationManager.FUSED_PROVIDER)
            add(LocationManager.GPS_PROVIDER)
            add(LocationManager.NETWORK_PROVIDER)
        }.filter { runCatching { manager.isProviderEnabled(it) }.getOrDefault(false) }
        if (candidates.isEmpty()) throw needs("Turn on location in the phone settings", "Activez la localisation dans les réglages du téléphone")

        // A recent known position is good enough and instant.
        candidates.mapNotNull { runCatching { manager.getLastKnownLocation(it) }.getOrNull() }
            .filter { System.currentTimeMillis() - it.time < 120_000 }
            .maxByOrNull { it.time }?.let { return it.toInfo() }

        if (Build.VERSION.SDK_INT < 30) {
            return candidates.firstNotNullOfOrNull { runCatching { manager.getLastKnownLocation(it) }.getOrNull() }?.toInfo()
                ?: throw needs("No location available yet", "Aucune position disponible pour l'instant")
        }
        return withTimeout(20_000) {
            suspendCancellableCoroutine { continuation ->
                val signal = CancellationSignal()
                continuation.invokeOnCancellation { signal.cancel() }
                manager.getCurrentLocation(candidates.first(), signal, context.mainExecutor) { location ->
                    if (location == null) continuation.resumeWithException(needs("No location available", "Aucune position disponible"))
                    else continuation.resume(location.toInfo())
                }
            }
        }
    }

    private fun Location.toInfo() = LocationInfo(latitude, longitude, if (hasAccuracy()) accuracy else null, if (hasAltitude()) altitude else null)

    @Suppress("DEPRECATION")
    fun reverse(context: Context, latitude: Double, longitude: Double): Map<String, String> {
        if (!Geocoder.isPresent()) throw needs("Addresses are not available on this phone", "Les adresses ne sont pas disponibles sur ce téléphone")
        val address = try {
            Geocoder(context, Locale.getDefault()).getFromLocation(latitude, longitude, 1)?.firstOrNull()
        } catch (e: java.io.IOException) {
            throw needs("Could not look up the address (no connection?)", "Impossible de trouver l'adresse (pas de connexion ?)")
        } ?: throw needs("No address found here", "Aucune adresse trouvée ici")
        return mapOf(
            "street" to listOfNotNull(address.subThoroughfare, address.thoroughfare).joinToString(" "),
            "city" to address.locality.orEmpty(),
            "region" to address.adminArea.orEmpty(),
            "postalCode" to address.postalCode.orEmpty(),
            "country" to address.countryName.orEmpty(),
            "name" to address.featureName.orEmpty(),
        )
    }

    @Suppress("DEPRECATION")
    fun network(context: Context): Map<String, String> {
        val connectivity = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val caps = connectivity.getNetworkCapabilities(connectivity.activeNetwork)
        val type = when {
            caps == null -> "none"
            caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> "wifi"
            caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> "cellular"
            caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> "ethernet"
            caps.hasTransport(NetworkCapabilities.TRANSPORT_VPN) -> "vpn"
            else -> "other"
        }
        val details = linkedMapOf("type" to type)
        if (type == "wifi") {
            val wifi = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
            val info = wifi.connectionInfo
            // The name of the Wi-Fi is hidden by Android unless the app may see the location.
            val ssid = info?.ssid?.trim('"').orEmpty()
            details["ssid"] = if (ssid == "<unknown ssid>" || ssid.isEmpty()) {
                if (context.has(Manifest.permission.ACCESS_FINE_LOCATION)) "" else throw needs(
                    "Allow location to read the Wi-Fi name",
                    "Autorisez la localisation pour lire le nom du Wi-Fi",
                )
            } else ssid
            details["signal"] = info?.rssi?.toString().orEmpty()
            details["speed"] = info?.linkSpeed?.toString().orEmpty()
        }
        return details
    }
}

/** Calendar, contacts, SMS. */
internal object PeopleHelper {
    fun events(context: Context, from: Long, to: Long, limit: Int): List<CalendarEventInfo> {
        if (!context.has(Manifest.permission.READ_CALENDAR)) throw needs("Calendar permission is required", "L'autorisation d'accès au calendrier est requise")
        val uri = CalendarContract.Instances.CONTENT_URI.buildUpon().also {
            ContentUris.appendId(it, from)
            ContentUris.appendId(it, to)
        }.build()
        val projection = arrayOf(
            CalendarContract.Instances.TITLE, CalendarContract.Instances.BEGIN, CalendarContract.Instances.END,
            CalendarContract.Instances.EVENT_LOCATION, CalendarContract.Instances.DESCRIPTION, CalendarContract.Instances.ALL_DAY,
        )
        val result = mutableListOf<CalendarEventInfo>()
        context.contentResolver.query(uri, projection, null, null, "${CalendarContract.Instances.BEGIN} ASC")?.use { c ->
            while (c.moveToNext() && result.size < limit) {
                result += CalendarEventInfo(
                    title = c.getString(0).orEmpty(), startMillis = c.getLong(1), endMillis = c.getLong(2),
                    location = c.getString(3).orEmpty(), notes = c.getString(4).orEmpty(), allDay = c.getInt(5) == 1,
                )
            }
        }
        return result
    }

    fun contacts(context: Context, query: String, limit: Int): List<ContactInfo> {
        if (!context.has(Manifest.permission.READ_CONTACTS)) throw needs("Contacts permission is required", "L'autorisation d'accès aux contacts est requise")
        val resolver = context.contentResolver
        val uri = if (query.isBlank()) ContactsContract.Contacts.CONTENT_URI
        else Uri.withAppendedPath(ContactsContract.Contacts.CONTENT_FILTER_URI, Uri.encode(query))
        val result = mutableListOf<ContactInfo>()
        resolver.query(uri, arrayOf(ContactsContract.Contacts._ID, ContactsContract.Contacts.DISPLAY_NAME), null, null, "${ContactsContract.Contacts.DISPLAY_NAME} ASC")?.use { c ->
            while (c.moveToNext() && result.size < limit) {
                val id = c.getLong(0)
                fun column(content: Uri, idColumn: String, valueColumn: String): List<String> = buildList {
                    resolver.query(content, arrayOf(valueColumn), "$idColumn = ?", arrayOf(id.toString()), null)?.use { q ->
                        while (q.moveToNext()) q.getString(0)?.let { add(it) }
                    }
                }
                result += ContactInfo(
                    name = c.getString(1).orEmpty(),
                    phones = column(ContactsContract.CommonDataKinds.Phone.CONTENT_URI, ContactsContract.CommonDataKinds.Phone.CONTACT_ID, ContactsContract.CommonDataKinds.Phone.NUMBER),
                    emails = column(ContactsContract.CommonDataKinds.Email.CONTENT_URI, ContactsContract.CommonDataKinds.Email.CONTACT_ID, ContactsContract.CommonDataKinds.Email.ADDRESS),
                )
            }
        }
        return result
    }

    @Suppress("DEPRECATION")
    fun sendSms(context: Context, number: String, body: String) {
        if (!context.has(Manifest.permission.SEND_SMS)) throw needs("SMS permission is required", "L'autorisation d'envoyer des SMS est requise")
        val manager = if (Build.VERSION.SDK_INT >= 31) context.getSystemService(SmsManager::class.java) else SmsManager.getDefault()
        manager.sendMultipartTextMessage(number, null, manager.divideMessage(body), null, null)
    }
}

/** Files, photos, images, recording. */
internal class MediaHelper(private val context: Context) {
    private val tempDir get() = File(context.cacheDir, "shortcut-temp").apply { mkdirs() }

    // Only a bare file name is accepted: no path, so a shortcut cannot reach outside the temporary folder.
    private fun tempFile(name: String): File {
        val clean = name.substringAfterLast('/').substringAfterLast('\\').trim()
        if (clean.isEmpty() || clean == "." || clean == "..") throw needs("Invalid file name", "Nom de fichier invalide")
        return File(tempDir, clean)
    }

    fun readUri(uri: String): ByteArray = try {
        context.contentResolver.openInputStream(Uri.parse(uri))?.use { it.readBytes() } ?: throw needs("The file cannot be opened", "Le fichier ne peut pas être ouvert")
    } catch (e: SecurityException) {
        throw needs("Daisychain may no longer read this file", "Daisychain ne peut plus lire ce fichier")
    } catch (e: java.io.FileNotFoundException) {
        throw needs("The file no longer exists", "Le fichier n'existe plus")
    }

    fun writeUri(uri: String, bytes: ByteArray) {
        context.contentResolver.openOutputStream(Uri.parse(uri), "wt")?.use { it.write(bytes) }
            ?: throw needs("The file cannot be written", "Le fichier ne peut pas être écrit")
    }

    fun tempWrite(name: String, bytes: ByteArray, append: Boolean) {
        val file = tempFile(name)
        if (append) file.appendBytes(bytes) else file.writeBytes(bytes)
    }

    fun tempRead(name: String): ByteArray? = tempFile(name).takeIf { it.isFile }?.readBytes()
    fun tempList(): List<String> = tempDir.listFiles()?.filter { it.isFile }?.map { it.name }?.sorted().orEmpty()
    fun tempDelete(name: String): Boolean = tempFile(name).delete()

    fun latestPhotos(count: Int): List<PickedFile> {
        val permission = if (Build.VERSION.SDK_INT >= 33) Manifest.permission.READ_MEDIA_IMAGES else Manifest.permission.READ_EXTERNAL_STORAGE
        if (!context.has(permission)) throw needs("Photos permission is required", "L'autorisation d'accès aux photos est requise")
        val photos = mutableListOf<PickedFile>()
        context.contentResolver.query(
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
            arrayOf(MediaStore.Images.Media._ID, MediaStore.Images.Media.DISPLAY_NAME, MediaStore.Images.Media.MIME_TYPE),
            null, null, "${MediaStore.Images.Media.DATE_ADDED} DESC",
        )?.use { c ->
            while (c.moveToNext() && photos.size < count) {
                val uri = ContentUris.withAppendedId(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, c.getLong(0))
                photos += PickedFile(uri.toString(), c.getString(1).orEmpty(), c.getString(2) ?: "image/jpeg")
            }
        }
        return photos
    }

    private fun decode(bytes: ByteArray, maxSide: Int = 4096): Bitmap {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        var sample = 1
        while (bounds.outWidth / sample > maxSide || bounds.outHeight / sample > maxSide) sample *= 2
        return BitmapFactory.decodeByteArray(bytes, 0, bytes.size, BitmapFactory.Options().apply { inSampleSize = sample })
            ?: throw needs("This image cannot be read", "Cette image ne peut pas être lue")
    }

    fun imageSize(bytes: ByteArray): Pair<Int, Int> {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        if (bounds.outWidth <= 0) throw needs("This image cannot be read", "Cette image ne peut pas être lue")
        return bounds.outWidth to bounds.outHeight
    }

    @Suppress("DEPRECATION")
    fun edit(bytes: ByteArray, edit: ImageEdit): ImageResult {
        var bitmap = decode(bytes)
        val v = edit.values
        when (edit.op) {
            "resize" -> {
                val w = v["width"]?.toInt()
                val h = v["height"]?.toInt()
                val (tw, th) = when {
                    w != null && h != null -> w to h
                    w != null -> w to (bitmap.height * w.toDouble() / bitmap.width).toInt()
                    h != null -> (bitmap.width * h.toDouble() / bitmap.height).toInt() to h
                    else -> bitmap.width to bitmap.height
                }
                bitmap = Bitmap.createScaledBitmap(bitmap, tw.coerceAtLeast(1), th.coerceAtLeast(1), true)
            }
            "rotate" -> bitmap = Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, Matrix().apply { postRotate((v["degrees"] ?: 90.0).toFloat()) }, true)
            "crop" -> {
                val x = ((v["x"] ?: 0.0) * bitmap.width).toInt().coerceIn(0, bitmap.width - 1)
                val y = ((v["y"] ?: 0.0) * bitmap.height).toInt().coerceIn(0, bitmap.height - 1)
                val w = ((v["w"] ?: 0.5) * bitmap.width).toInt().coerceIn(1, bitmap.width - x)
                val h = ((v["h"] ?: 0.5) * bitmap.height).toInt().coerceIn(1, bitmap.height - y)
                bitmap = Bitmap.createBitmap(bitmap, x, y, w, h)
            }
            "text" -> {
                bitmap = bitmap.copy(Bitmap.Config.ARGB_8888, true)
                val canvas = Canvas(bitmap)
                val size = bitmap.height * ((v["size"] ?: 6.0).toFloat() / 100f)
                val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = Color.WHITE
                    textSize = size
                    textAlign = Paint.Align.CENTER
                    setShadowLayer(size / 8, 0f, 0f, Color.BLACK)
                }
                val y = when (v["position"]?.toInt()) {
                    0 -> size * 1.4f
                    1 -> bitmap.height / 2f + size / 3
                    else -> bitmap.height - size * 0.8f
                }
                canvas.drawText(edit.text, bitmap.width / 2f, y, paint)
            }
        }
        val out = ByteArrayOutputStream()
        val quality = (v["quality"] ?: 90.0).toInt().coerceIn(10, 100)
        val mime = when (edit.format) {
            "png" -> { bitmap.compress(Bitmap.CompressFormat.PNG, 100, out); "image/png" }
            "webp" -> {
                bitmap.compress(if (Build.VERSION.SDK_INT >= 30) Bitmap.CompressFormat.WEBP_LOSSY else Bitmap.CompressFormat.WEBP, quality, out)
                "image/webp"
            }
            else -> { bitmap.compress(Bitmap.CompressFormat.JPEG, quality, out); "image/jpeg" }
        }
        return ImageResult(out.toByteArray(), mime)
    }

    private var recorder: MediaRecorder? = null
    private var recordingFile: File? = null

    @Suppress("DEPRECATION")
    fun startRecording() {
        if (!context.has(Manifest.permission.RECORD_AUDIO)) throw needs("Microphone permission is required", "L'autorisation du micro est requise")
        val file = File(context.cacheDir, "recording-${System.currentTimeMillis()}.m4a")
        val media = if (Build.VERSION.SDK_INT >= 31) MediaRecorder(context) else MediaRecorder()
        media.setAudioSource(MediaRecorder.AudioSource.MIC)
        media.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
        media.setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
        media.setOutputFile(file.absolutePath)
        media.prepare()
        media.start()
        recorder = media
        recordingFile = file
    }

    fun stopRecording(): ByteArray {
        val media = recorder ?: throw needs("Nothing is being recorded", "Aucun enregistrement en cours")
        val file = recordingFile ?: throw needs("Nothing is being recorded", "Aucun enregistrement en cours")
        recorder = null
        recordingFile = null
        try {
            media.stop()
        } catch (e: RuntimeException) {
            // Stopped before any sound was captured.
        } finally {
            media.release()
        }
        return file.readBytes().also { file.delete() }
    }
}

/** Speech, text in images, translation, QR codes. */
internal object SmartHelper {
    suspend fun recognizeText(bytes: ByteArray): String {
        val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size) ?: throw needs("This image cannot be read", "Cette image ne peut pas être lue")
        val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
        try {
            return recognizer.process(InputImage.fromBitmap(bitmap, 0)).await().text
        } finally {
            recognizer.close()
        }
    }

    suspend fun detectLanguage(text: String): String {
        val identifier = LanguageIdentification.getClient()
        try {
            return identifier.identifyLanguage(text).await()
        } finally {
            identifier.close()
        }
    }

    suspend fun translate(text: String, sourceTag: String?, targetTag: String): String {
        val source = sourceTag ?: detectLanguage(text).takeIf { it != "und" }
            ?: throw needs("The language of the text could not be detected", "La langue du texte n'a pas pu être détectée")
        val from = TranslateLanguage.fromLanguageTag(source) ?: throw needs("Unsupported language: $source", "Langue non prise en charge : $source")
        val to = TranslateLanguage.fromLanguageTag(targetTag) ?: throw needs("Unsupported language: $targetTag", "Langue non prise en charge : $targetTag")
        if (from == to) return text
        val translator = Translation.getClient(TranslatorOptions.Builder().setSourceLanguage(from).setTargetLanguage(to).build())
        try {
            try {
                translator.downloadModelIfNeeded(DownloadConditions.Builder().build()).await()
            } catch (e: Exception) {
                throw needs("The language data could not be downloaded: check your connection", "Les données de langue n'ont pas pu être téléchargées : vérifiez votre connexion")
            }
            return translator.translate(text).await()
        } finally {
            translator.close()
        }
    }

    fun qrCode(text: String, size: Int): ByteArray {
        val matrix = QRCodeWriter().encode(text, BarcodeFormat.QR_CODE, size, size)
        val pixels = IntArray(size * size) { i -> if (matrix[i % size, i / size]) Color.BLACK else Color.WHITE }
        val bitmap = Bitmap.createBitmap(pixels, size, size, Bitmap.Config.ARGB_8888)
        return ByteArrayOutputStream().also { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }.toByteArray()
    }

    suspend fun scanCodes(bytes: ByteArray): List<String> {
        val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size) ?: throw needs("This image cannot be read", "Cette image ne peut pas être lue")
        val scanner = BarcodeScanning.getClient()
        try {
            return scanner.process(InputImage.fromBitmap(bitmap, 0)).await().mapNotNull { it.rawValue }
        } finally {
            scanner.close()
        }
    }

    /** Listens once and returns what was said. The phone's speech service does the work. */
    suspend fun dictate(context: Context, languageTag: String): String {
        if (!context.has(Manifest.permission.RECORD_AUDIO)) throw needs("Microphone permission is required", "L'autorisation du micro est requise")
        if (!SpeechRecognizer.isRecognitionAvailable(context)) throw needs("No speech service on this phone", "Aucun service vocal sur ce téléphone")
        return suspendCancellableCoroutine { continuation ->
            val recognizer = SpeechRecognizer.createSpeechRecognizer(context)
            continuation.invokeOnCancellation { recognizer.destroy() }
            recognizer.setRecognitionListener(object : RecognitionListener {
                override fun onResults(results: Bundle?) {
                    recognizer.destroy()
                    val text = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull().orEmpty()
                    if (continuation.isActive) continuation.resume(text)
                }

                override fun onError(error: Int) {
                    recognizer.destroy()
                    if (!continuation.isActive) return
                    continuation.resumeWithException(
                        when (error) {
                            SpeechRecognizer.ERROR_NO_MATCH, SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> needs("Nothing was heard", "Rien n'a été entendu")
                            SpeechRecognizer.ERROR_NETWORK, SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> needs("Speech recognition needs a connection", "La reconnaissance vocale demande une connexion")
                            else -> needs("Speech recognition failed (code $error)", "La reconnaissance vocale a échoué (code $error)")
                        },
                    )
                }

                override fun onReadyForSpeech(params: Bundle?) {
                    Toast.makeText(context, context.getString(com.daisychain.app.R.string.speak_now), Toast.LENGTH_SHORT).show()
                }

                override fun onBeginningOfSpeech() {}
                override fun onRmsChanged(rmsdB: Float) {}
                override fun onBufferReceived(buffer: ByteArray?) {}
                override fun onEndOfSpeech() {}
                override fun onPartialResults(partialResults: Bundle?) {}
                override fun onEvent(eventType: Int, params: Bundle?) {}
            })
            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
                .putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                .putExtra(RecognizerIntent.EXTRA_LANGUAGE, if (languageTag == "auto") Locale.getDefault().toLanguageTag() else languageTag)
                .putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, false)
            recognizer.startListening(intent)
        }
    }
}
