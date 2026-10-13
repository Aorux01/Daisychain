package com.daisychain.engine.run

import com.daisychain.engine.spec.LocalizedText

/** A value flowing between blocks while a shortcut runs. */
sealed interface RunValue {
    data object Nothing : RunValue
    data class Text(val value: String) : RunValue
    data class Number(val value: Double) : RunValue
    data class Bool(val value: Boolean) : RunValue
    data class Date(val epochMillis: Long) : RunValue
    data class Items(val values: List<RunValue>) : RunValue

    /** Named values, e.g. from a "Dictionary" block or a JSON object. */
    data class Dict(val entries: Map<String, RunValue>) : RunValue

    /**
     * A file: its bytes are either here ([bytes]) or still on the phone ([uri], read when needed).
     * A text or an image becomes a file when it is given a name (see the "Set name" block).
     */
    class File(val name: String, val mime: String, val bytes: ByteArray? = null, val uri: String? = null) : RunValue

    /** An image (encoded bytes, e.g. a JPEG) fetched from the web or produced by a block. */
    class Image(val bytes: ByteArray, val mime: String) : RunValue

    /** The value as the user would read it. */
    fun display(): String = when (this) {
        Nothing -> ""
        is Text -> value
        is Number -> if (value % 1.0 == 0.0) value.toLong().toString() else value.toString()
        is Bool -> value.toString()
        is Date -> java.text.DateFormat.getDateTimeInstance().format(java.util.Date(epochMillis))
        is Items -> values.joinToString(", ") { it.display() }
        is Dict -> entries.entries.joinToString("\n") { (k, v) -> "$k: ${v.display()}" }
        is Image -> "[image]"
        is File -> name
    }
}

/** A block could not do its job; [text] is shown to the user. */
class ActionFailed(val text: LocalizedText) : Exception(text.resolve("en"))

/** Thrown by "Stop this shortcut" / "Stop and output" to end the run quietly; [output] is what the shortcut returns. */
class StopShortcut(val output: RunValue? = null) : Exception()

/** Lets a shortcut run another one by name. */
interface ShortcutLibrary {
    suspend fun find(name: String): List<com.daisychain.engine.model.ActionInstance>?
    suspend fun names(): List<String> = emptyList()
    suspend fun idOf(name: String): Long? = null
}

/** The run stopped because the block [uuid] failed. */
class RunFailure(val uuid: String, val text: LocalizedText) : Exception(text.resolve("en"))

/** What blocks may ask of the phone. Android implementation lives in the app; tests use a fake. */
interface DeviceApi {
    fun batteryPercent(): Int
    fun setVolumePercent(percent: Int)
    fun setBrightnessPercent(percent: Int)
    fun showNotification(title: String, body: String)
    fun openApp(packageName: String)
    fun clipboardText(): String
    fun setClipboardText(text: String)
    fun openUrl(url: String)
    fun vibrate(millis: Long)

    /** [on] null toggles the flashlight. */
    fun setFlashlight(on: Boolean?)
    fun shareText(text: String)
    fun dial(number: String)

    /** Reads the text aloud and returns when finished. */
    suspend fun speak(text: String)

    /** Downloads [url] (GET). Fails with [ActionFailed] when the network or the server does. */
    suspend fun httpGet(url: String): HttpResponse

    /** Saves an image to the phone's gallery. */
    fun saveImage(bytes: ByteArray, mime: String)

    // Everything below has a default so a fake device only needs the parts a test uses.
    suspend fun httpRequest(request: HttpRequest): HttpResponse = httpGet(request.url)
    fun deviceDetail(key: String): String = unsupported()
    fun localIpAddress(): String? = unsupported()
    fun playNotificationSound(): Unit = unsupported()
    fun goHome(): Unit = unsupported()
    fun openSettings(target: String): Unit = unsupported()
    fun openShortcut(id: Long): Unit = unsupported()
    fun composeSms(number: String, body: String): Unit = unsupported()
    fun composeEmail(to: String, subject: String, body: String): Unit = unsupported()
    fun setAlarm(hour: Int, minute: Int, label: String): Unit = unsupported()
    fun startTimer(seconds: Int, label: String): Unit = unsupported()
    fun scheduleNotification(title: String, body: String, atMillis: Long): Unit = unsupported()
    fun openMaps(query: String): Unit = unsupported()
    fun addCalendarEvent(title: String, startMillis: Long, endMillis: Long, location: String, notes: String): Unit = unsupported()
    fun addContact(name: String, phone: String, email: String): Unit = unsupported()
    fun mediaControl(action: String): Unit = unsupported()
    fun setWallpaper(bytes: ByteArray, target: String): Unit = unsupported()
    fun installedApps(): List<InstalledApp> = unsupported()
    fun sendIntent(spec: IntentSpec): Unit = unsupported()
    fun setDoNotDisturb(mode: String): Unit = unsupported()

    /** [mode] is sound, vibrate or silent. */
    fun setRingerMode(mode: String): Unit = unsupported()

    /** Turns "wifi", "bluetooth", "mobile_data" or "airplane" on or off; [on] null switches it. Needs the shell rights Shizuku lends. */
    suspend fun setRadio(radio: String, on: Boolean?): Unit = unsupported()

    /** Name (ssid), password and security of this phone's hotspot, when they can be read. */
    suspend fun hotspotDetails(): Map<String, String> = unsupported()

    /** Runs a command line with shell rights (like `adb shell`) and returns what it printed. */
    suspend fun runPrivileged(command: String): String = unsupported()

    // Places, network, people
    suspend fun currentLocation(): LocationInfo = unsupported()
    fun reverseGeocode(latitude: Double, longitude: Double): Map<String, String> = unsupported()
    fun networkDetails(): Map<String, String> = unsupported()
    fun findEvents(fromMillis: Long, toMillis: Long, limit: Int): List<CalendarEventInfo> = unsupported()
    fun findContacts(query: String, limit: Int): List<ContactInfo> = unsupported()
    fun sendSms(number: String, body: String): Unit = unsupported()

    // Files and media
    fun readUri(uri: String): ByteArray = unsupported()
    fun writeUri(uri: String, bytes: ByteArray): Unit = unsupported()
    fun tempWrite(name: String, bytes: ByteArray, append: Boolean): Unit = unsupported()
    fun tempRead(name: String): ByteArray? = unsupported()
    fun tempList(): List<String> = unsupported()
    fun tempDelete(name: String): Boolean = unsupported()
    fun latestPhotos(count: Int): List<PickedFile> = unsupported()
    fun editImage(bytes: ByteArray, edit: ImageEdit): ImageResult = unsupported()
    fun imageSize(bytes: ByteArray): Pair<Int, Int> = unsupported()
    fun startRecording(): Unit = unsupported()
    fun stopRecording(): ByteArray = unsupported()

    // Smart features
    suspend fun dictate(languageTag: String): String = unsupported()
    suspend fun recognizeText(bytes: ByteArray): String = unsupported()
    suspend fun translate(text: String, sourceTag: String?, targetTag: String): String = unsupported()
    suspend fun detectLanguage(text: String): String = unsupported()
    fun makeQrCode(text: String, size: Int): ByteArray = unsupported()
    suspend fun scanCodes(bytes: ByteArray): List<String> = unsupported()
}

/** What the user picked or created in a system screen (file picker, camera...). */
class PickedFile(val uri: String, val name: String, val mime: String)

class LocationInfo(val latitude: Double, val longitude: Double, val accuracy: Float? = null, val altitude: Double? = null)

class CalendarEventInfo(
    val title: String,
    val startMillis: Long,
    val endMillis: Long,
    val location: String,
    val notes: String,
    val allDay: Boolean,
)

class ContactInfo(val name: String, val phones: List<String>, val emails: List<String>)

/** An edit of an image: [op] is resize, rotate, crop, convert, text. [values] are numbers (width, height, degrees...). */
class ImageEdit(val op: String, val values: Map<String, Double> = emptyMap(), val text: String = "", val format: String = "jpeg")

class ImageResult(val bytes: ByteArray, val mime: String)

class HttpRequest(
    val method: String,
    val url: String,
    val headers: Map<String, String> = emptyMap(),
    val body: ByteArray? = null,
    val contentType: String? = null,
)

class HttpResponse(val bytes: ByteArray, val contentType: String?, val finalUrl: String? = null)

class InstalledApp(val name: String, val packageName: String)

/** An Android intent described with plain values, so a block can talk to any other app. */
class IntentSpec(
    val action: String,
    val data: String = "",
    val packageName: String = "",
    val mimeType: String = "",
    val extras: Map<String, String> = emptyMap(),
    val broadcast: Boolean = false,
)

/** What blocks may ask of the user while running. Each call waits for the answer. */
interface RunUi {
    suspend fun showResult(text: String, image: RunValue.Image? = null, allowCopy: Boolean = true)

    /** Returns false when the user pressed Cancel (only offered when [allowCancel]). */
    suspend fun alert(title: String, message: String, allowCancel: Boolean = false): Boolean

    /** Returns the index of the chosen item. */
    suspend fun choose(prompt: String, items: List<String>): Int
    suspend fun ask(prompt: String): String

    // System screens. Each returns null when the user backs out.
    suspend fun pickFile(mimeTypes: List<String>): PickedFile? = unsupported()
    suspend fun saveFile(name: String, mime: String): PickedFile? = unsupported()
    suspend fun pickPhoto(): PickedFile? = unsupported()
    suspend fun takePhoto(): PickedFile? = unsupported()
    suspend fun takeVideo(): PickedFile? = unsupported()

    /** Shows "recording" with a Stop button; returns when the user stops. */
    suspend fun waitWhileRecording(): Unit = unsupported()
    suspend fun showWebPage(url: String): Unit = unsupported()
}

/** Told which block is running, so the editor can light it up. */
interface RunProgress {
    fun onStart(uuid: String)
    fun onFinish(uuid: String)
}
