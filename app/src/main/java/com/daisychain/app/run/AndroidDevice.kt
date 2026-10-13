package com.daisychain.app.run

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.AlarmManager
import android.app.PendingIntent
import android.app.WallpaperManager
import android.graphics.BitmapFactory
import android.media.RingtoneManager
import android.provider.AlarmClock
import android.provider.CalendarContract
import android.provider.ContactsContract
import android.view.KeyEvent
import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.ClipboardManager
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.media.AudioManager
import android.net.Uri
import android.os.BatteryManager
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.provider.MediaStore
import android.provider.Settings
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.daisychain.app.R
import com.daisychain.engine.run.HttpRequest
import com.daisychain.engine.run.InstalledApp
import com.daisychain.engine.run.IntentSpec
import com.daisychain.app.MainActivity
import com.daisychain.app.run.ScheduledNotificationReceiver
import com.daisychain.engine.run.CalendarEventInfo
import com.daisychain.engine.run.ContactInfo
import com.daisychain.engine.run.ImageEdit
import com.daisychain.engine.run.ImageResult
import com.daisychain.engine.run.LocationInfo
import com.daisychain.engine.run.PickedFile
import com.daisychain.engine.run.ActionFailed
import com.daisychain.engine.run.DeviceApi
import com.daisychain.engine.run.HttpResponse
import com.daisychain.engine.spec.t
import java.net.Inet4Address
import java.net.NetworkInterface
import java.util.Locale
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext

/** Real phone features used by blocks. */
class AndroidDevice(private val context: Context) : DeviceApi {

    override fun batteryPercent(): Int {
        val status = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        val level = status?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
        val scale = status?.getIntExtra(BatteryManager.EXTRA_SCALE, -1) ?: -1
        if (level < 0 || scale <= 0) throw ActionFailed(t("Battery level unavailable", "Niveau de batterie indisponible"))
        return level * 100 / scale
    }

    override fun setVolumePercent(percent: Int) {
        val audio = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        val max = audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
        audio.setStreamVolume(AudioManager.STREAM_MUSIC, percent * max / 100, 0)
    }

    override fun setBrightnessPercent(percent: Int) {
        if (!Settings.System.canWrite(context)) {
            // Take the user straight to the switch that grants the permission.
            context.startActivity(
                Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS, Uri.parse("package:${context.packageName}"))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            )
            throw ActionFailed(
                t(
                    "Allow \"Modify system settings\" for Daisychain, then run again",
                    "Autorisez « Modifier les paramètres système » pour Daisychain, puis relancez",
                ),
            )
        }
        val resolver = context.contentResolver
        Settings.System.putInt(resolver, Settings.System.SCREEN_BRIGHTNESS_MODE, Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL)
        Settings.System.putInt(resolver, Settings.System.SCREEN_BRIGHTNESS, percent * 255 / 100)
    }

    override fun showNotification(title: String, body: String) {
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            throw ActionFailed(t("Notification permission is required", "L'autorisation de notification est requise"))
        }
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL, context.getString(R.string.notification_channel), NotificationManager.IMPORTANCE_DEFAULT),
        )
        val notification = NotificationCompat.Builder(context, CHANNEL)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(title.ifEmpty { context.getString(R.string.app_name) })
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setAutoCancel(true)
            .build()
        manager.notify(System.currentTimeMillis().toInt(), notification)
    }

    override fun openApp(packageName: String) {
        val intent = context.packageManager.getLaunchIntentForPackage(packageName)
            ?: throw ActionFailed(t("App not found: $packageName", "Application introuvable : $packageName"))
        context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }

    override fun clipboardText(): String {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        return clipboard.primaryClip?.takeIf { it.itemCount > 0 }?.getItemAt(0)?.coerceToText(context)?.toString().orEmpty()
    }

    override fun setClipboardText(text: String) {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText("Daisychain", text))
    }

    override fun openUrl(url: String) {
        val fixed = if (url.contains("://")) url else "https://$url"
        try {
            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(fixed)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        } catch (e: ActivityNotFoundException) {
            throw ActionFailed(t("Cannot open this URL", "Impossible d'ouvrir cette URL"))
        }
    }

    override fun vibrate(millis: Long) {
        val vibrator = if (Build.VERSION.SDK_INT >= 31) {
            (context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager).defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        }
        vibrator.vibrate(VibrationEffect.createOneShot(millis.coerceAtLeast(1), VibrationEffect.DEFAULT_AMPLITUDE))
    }

    override fun setFlashlight(on: Boolean?) {
        val manager = context.getSystemService(Context.CAMERA_SERVICE) as CameraManager
        val cameraId = manager.cameraIdList.firstOrNull {
            val c = manager.getCameraCharacteristics(it)
            c.get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true &&
                c.get(CameraCharacteristics.LENS_FACING) == CameraCharacteristics.LENS_FACING_BACK
        } ?: throw ActionFailed(t("No flashlight on this device", "Pas de lampe torche sur cet appareil"))
        torchOn = on ?: !torchOn
        manager.setTorchMode(cameraId, torchOn)
    }

    override fun shareText(text: String) {
        val send = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, text)
        context.startActivity(Intent.createChooser(send, null).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }

    override fun dial(number: String) {
        context.startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:" + Uri.encode(number))).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }

    override suspend fun speak(text: String) {
        if (text.isBlank()) return
        suspendCancellableCoroutine { continuation ->
            var engine: TextToSpeech? = null
            engine = TextToSpeech(context) { status ->
                val tts = engine
                if (status != TextToSpeech.SUCCESS || tts == null) {
                    tts?.shutdown()
                    if (continuation.isActive) continuation.resumeWithException(ActionFailed(t("Speech is not available", "La synthèse vocale n'est pas disponible")))
                    return@TextToSpeech
                }
                tts.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                    override fun onStart(utteranceId: String?) {}
                    override fun onDone(utteranceId: String?) { tts.shutdown(); if (continuation.isActive) continuation.resume(Unit) }

                    @Deprecated("Deprecated in Java")
                    override fun onError(utteranceId: String?) { tts.shutdown(); if (continuation.isActive) continuation.resume(Unit) }
                })
                tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, "daisychain")
            }
            continuation.invokeOnCancellation { engine?.stop(); engine?.shutdown() }
        }
    }

    override suspend fun httpGet(url: String): HttpResponse = httpRequest(HttpRequest("GET", url))

    override suspend fun httpRequest(request: HttpRequest): HttpResponse = withContext(Dispatchers.IO) {
        try {
            val connection = (URL(request.url).openConnection() as HttpURLConnection).apply {
                connectTimeout = 15_000
                readTimeout = 30_000
                instanceFollowRedirects = true
                requestMethod = request.method
                setRequestProperty("User-Agent", "Daisychain")
                request.headers.forEach { (k, v) -> setRequestProperty(k, v) }
                request.contentType?.let { setRequestProperty("Content-Type", it) }
                request.body?.let { body ->
                    doOutput = true
                    outputStream.use { it.write(body) }
                }
            }
            try {
                val code = connection.responseCode
                if (code !in 200..299) {
                    throw ActionFailed(t("The server answered with error $code", "Le serveur a répondu avec l'erreur $code"))
                }
                val bytes = connection.inputStream.use { input ->
                    val out = ByteArrayOutputStream()
                    val buffer = ByteArray(16 * 1024)
                    while (true) {
                        val n = input.read(buffer)
                        if (n < 0) break
                        out.write(buffer, 0, n)
                        if (out.size() > MAX_DOWNLOAD) throw ActionFailed(t("The download is too large", "Le téléchargement est trop volumineux"))
                    }
                    out.toByteArray()
                }
                HttpResponse(bytes, connection.contentType, connection.url.toString())
            } finally {
                connection.disconnect()
            }
        } catch (e: ActionFailed) {
            throw e
        } catch (e: IOException) {
            throw ActionFailed(t("Network error: check your connection", "Erreur réseau : vérifiez votre connexion"))
        }
    }

    override fun saveImage(bytes: ByteArray, mime: String) {
        if (Build.VERSION.SDK_INT < 29) {
            throw ActionFailed(t("Saving to the gallery needs Android 10 or later", "L'enregistrement dans la galerie demande Android 10 ou plus"))
        }
        val extension = when (mime) {
            "image/png" -> "png"
            "image/gif" -> "gif"
            "image/webp" -> "webp"
            else -> "jpg"
        }
        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, "daisychain_${System.currentTimeMillis()}.$extension")
            put(MediaStore.Images.Media.MIME_TYPE, mime)
            put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/Daisychain")
        }
        val resolver = context.contentResolver
        val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
            ?: throw ActionFailed(t("Could not save the image", "Impossible d'enregistrer l'image"))
        resolver.openOutputStream(uri)?.use { it.write(bytes) }
            ?: throw ActionFailed(t("Could not save the image", "Impossible d'enregistrer l'image"))
    }

    // ---- details, sound, navigation

    override fun deviceDetail(key: String): String = when (key) {
        "manufacturer" -> Build.MANUFACTURER
        "name" -> Settings.Global.getString(context.contentResolver, "device_name") ?: Build.MODEL
        "androidVersion" -> Build.VERSION.RELEASE
        "sdk" -> Build.VERSION.SDK_INT.toString()
        "screen" -> context.resources.displayMetrics.let { "${it.widthPixels}\u00d7${it.heightPixels}" }
        "language" -> Locale.getDefault().displayLanguage
        "charging" -> {
            val status = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))?.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
            if (status == BatteryManager.BATTERY_STATUS_CHARGING || status == BatteryManager.BATTERY_STATUS_FULL) "Charging" else "Not charging"
        }
        else -> Build.MODEL
    }

    override fun localIpAddress(): String? = NetworkInterface.getNetworkInterfaces().toList()
        .filter { it.isUp && !it.isLoopback }
        .flatMap { it.inetAddresses.toList() }
        .firstOrNull { it is Inet4Address && !it.isLoopbackAddress }?.hostAddress

    override fun playNotificationSound() {
        RingtoneManager.getRingtone(context, RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION))?.play()
    }

    private fun launch(intent: Intent, failure: String = "No app can do this") {
        try {
            context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        } catch (e: ActivityNotFoundException) {
            throw ActionFailed(t(failure, "Aucune application ne peut le faire"))
        }
    }

    override fun goHome() = launch(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME))

    override fun openSettings(target: String) {
        val intent = when (target) {
            "internet_panel" -> Intent(if (Build.VERSION.SDK_INT >= 29) Settings.Panel.ACTION_INTERNET_CONNECTIVITY else Settings.ACTION_WIRELESS_SETTINGS)
            "wifi" -> Intent(Settings.ACTION_WIFI_SETTINGS)
            "bluetooth" -> Intent(Settings.ACTION_BLUETOOTH_SETTINGS)
            "airplane" -> Intent(Settings.ACTION_AIRPLANE_MODE_SETTINGS)
            "mobile_network" -> Intent(Settings.ACTION_NETWORK_OPERATOR_SETTINGS)
            "volume_panel" -> Intent(if (Build.VERSION.SDK_INT >= 29) Settings.Panel.ACTION_VOLUME else Settings.ACTION_SOUND_SETTINGS)
            "nfc_panel" -> Intent(if (Build.VERSION.SDK_INT >= 29) Settings.Panel.ACTION_NFC else Settings.ACTION_NFC_SETTINGS)
            "display" -> Intent(Settings.ACTION_DISPLAY_SETTINGS)
            "battery_saver" -> Intent(Settings.ACTION_BATTERY_SAVER_SETTINGS)
            "sound" -> Intent(Settings.ACTION_SOUND_SETTINGS)
            "location" -> Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS)
            "dnd_access" -> Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS)
            "notifications" -> Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
            "apps" -> Intent(Settings.ACTION_APPLICATION_SETTINGS)
            "accessibility" -> Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
            "date" -> Intent(Settings.ACTION_DATE_SETTINGS)
            "language" -> Intent(Settings.ACTION_LOCALE_SETTINGS)
            "storage" -> Intent(Settings.ACTION_INTERNAL_STORAGE_SETTINGS)
            "vpn" -> Intent(Settings.ACTION_VPN_SETTINGS)
            "hotspot" -> Intent().setClassName("com.android.settings", "com.android.settings.TetherSettings")
            else -> Intent(Settings.ACTION_SETTINGS)
        }
        launch(intent)
    }

    override fun openShortcut(id: Long) {
        launch(
            Intent(context, MainActivity::class.java)
                .putExtra(MainActivity.EXTRA_OPEN_SHORTCUT, id)
                .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP),
        )
    }

    // ---- messages, clock, maps, calendar, contacts

    override fun composeSms(number: String, body: String) =
        launch(Intent(Intent.ACTION_SENDTO, Uri.parse("smsto:" + Uri.encode(number))).putExtra("sms_body", body))

    override fun composeEmail(to: String, subject: String, body: String) = launch(
        Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:"))
            .putExtra(Intent.EXTRA_EMAIL, arrayOf(to))
            .putExtra(Intent.EXTRA_SUBJECT, subject)
            .putExtra(Intent.EXTRA_TEXT, body),
    )

    override fun setAlarm(hour: Int, minute: Int, label: String) = launch(
        Intent(AlarmClock.ACTION_SET_ALARM)
            .putExtra(AlarmClock.EXTRA_HOUR, hour)
            .putExtra(AlarmClock.EXTRA_MINUTES, minute)
            .putExtra(AlarmClock.EXTRA_MESSAGE, label)
            .putExtra(AlarmClock.EXTRA_SKIP_UI, true),
    )

    override fun startTimer(seconds: Int, label: String) = launch(
        Intent(AlarmClock.ACTION_SET_TIMER)
            .putExtra(AlarmClock.EXTRA_LENGTH, seconds)
            .putExtra(AlarmClock.EXTRA_MESSAGE, label)
            .putExtra(AlarmClock.EXTRA_SKIP_UI, true),
    )

    override fun scheduleNotification(title: String, body: String, atMillis: Long) {
        val alarms = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val intent = Intent(context, ScheduledNotificationReceiver::class.java)
            .putExtra(ScheduledNotificationReceiver.EXTRA_TITLE, title)
            .putExtra(ScheduledNotificationReceiver.EXTRA_BODY, body)
        val pending = PendingIntent.getBroadcast(
            context, (atMillis xor title.hashCode().toLong()).toInt(), intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        alarms.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, atMillis, pending)
    }

    override fun openMaps(query: String) = launch(Intent(Intent.ACTION_VIEW, Uri.parse("geo:0,0?q=" + Uri.encode(query))))

    override fun addCalendarEvent(title: String, startMillis: Long, endMillis: Long, location: String, notes: String) = launch(
        Intent(Intent.ACTION_INSERT, CalendarContract.Events.CONTENT_URI)
            .putExtra(CalendarContract.Events.TITLE, title)
            .putExtra(CalendarContract.Events.EVENT_LOCATION, location)
            .putExtra(CalendarContract.Events.DESCRIPTION, notes)
            .putExtra(CalendarContract.EXTRA_EVENT_BEGIN_TIME, startMillis)
            .putExtra(CalendarContract.EXTRA_EVENT_END_TIME, endMillis),
    )

    override fun addContact(name: String, phone: String, email: String) = launch(
        Intent(ContactsContract.Intents.Insert.ACTION).setType(ContactsContract.RawContacts.CONTENT_TYPE)
            .putExtra(ContactsContract.Intents.Insert.NAME, name)
            .putExtra(ContactsContract.Intents.Insert.PHONE, phone)
            .putExtra(ContactsContract.Intents.Insert.EMAIL, email),
    )

    // ---- media, wallpaper, apps, intents, Do Not Disturb

    override fun mediaControl(action: String) {
        val code = when (action) {
            "next" -> KeyEvent.KEYCODE_MEDIA_NEXT
            "previous" -> KeyEvent.KEYCODE_MEDIA_PREVIOUS
            "stop" -> KeyEvent.KEYCODE_MEDIA_STOP
            else -> KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE
        }
        val audio = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        audio.dispatchMediaKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, code))
        audio.dispatchMediaKeyEvent(KeyEvent(KeyEvent.ACTION_UP, code))
    }

    override fun setWallpaper(bytes: ByteArray, target: String) {
        val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
            ?: throw ActionFailed(t("This image cannot be read", "Cette image ne peut pas être lue"))
        val flags = when (target) {
            "home" -> WallpaperManager.FLAG_SYSTEM
            "lock" -> WallpaperManager.FLAG_LOCK
            else -> WallpaperManager.FLAG_SYSTEM or WallpaperManager.FLAG_LOCK
        }
        WallpaperManager.getInstance(context).setBitmap(bitmap, null, true, flags)
    }

    override fun installedApps(): List<InstalledApp> {
        val pm = context.packageManager
        return pm.queryIntentActivities(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER), 0)
            .map { InstalledApp(it.loadLabel(pm).toString(), it.activityInfo.packageName) }
            .distinctBy { it.packageName }
            .sortedBy { it.name.lowercase() }
    }

    override fun sendIntent(spec: IntentSpec) {
        val intent = Intent(spec.action)
        if (spec.data.isNotEmpty() && spec.mimeType.isNotEmpty()) intent.setDataAndType(Uri.parse(spec.data), spec.mimeType)
        else if (spec.data.isNotEmpty()) intent.data = Uri.parse(spec.data)
        else if (spec.mimeType.isNotEmpty()) intent.type = spec.mimeType
        if (spec.packageName.isNotEmpty()) intent.setPackage(spec.packageName)
        spec.extras.forEach { (k, v) ->
            when {
                v.equals("true", ignoreCase = true) || v.equals("false", ignoreCase = true) -> intent.putExtra(k, v.toBoolean())
                v.toLongOrNull() != null -> intent.putExtra(k, v.toLong())
                v.toDoubleOrNull() != null -> intent.putExtra(k, v.toDouble())
                else -> intent.putExtra(k, v)
            }
        }
        if (spec.broadcast) context.sendBroadcast(intent) else launch(intent)
    }

    override fun setDoNotDisturb(mode: String) {
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (!manager.isNotificationPolicyAccessGranted) {
            launch(Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS))
            throw ActionFailed(
                t(
                    "Allow Daisychain to control Do Not Disturb, then run again",
                    "Autorisez Daisychain à contrôler Ne pas déranger, puis relancez",
                ),
            )
        }
        manager.setInterruptionFilter(
            when (mode) {
                "off" -> NotificationManager.INTERRUPTION_FILTER_ALL
                "alarms" -> NotificationManager.INTERRUPTION_FILTER_ALARMS
                "silence" -> NotificationManager.INTERRUPTION_FILTER_NONE
                else -> NotificationManager.INTERRUPTION_FILTER_PRIORITY
            },
        )
    }

    override fun setRingerMode(mode: String) {
        val audio = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        val target = when (mode) {
            "sound" -> AudioManager.RINGER_MODE_NORMAL
            "silent" -> AudioManager.RINGER_MODE_SILENT
            else -> AudioManager.RINGER_MODE_VIBRATE
        }
        try {
            audio.ringerMode = target
        } catch (e: SecurityException) {
            launch(Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS))
            throw ActionFailed(t("Allow Daisychain to control Do Not Disturb, then run again", "Autorisez Daisychain à contrôler Ne pas déranger, puis relancez"))
        }
    }

    private fun radioIsOn(radio: String): Boolean {
        val key = when (radio) {
            "wifi" -> "wifi_on"
            "bluetooth" -> "bluetooth_on"
            "mobile_data" -> "mobile_data"
            else -> Settings.Global.AIRPLANE_MODE_ON
        }
        return Settings.Global.getInt(context.contentResolver, key, 0) != 0
    }

    override suspend fun setRadio(radio: String, on: Boolean?) {
        val enable = on ?: !radioIsOn(radio)
        val word = if (enable) "enable" else "disable"
        when (radio) {
            "wifi" -> PrivilegedShell.run(context, "cmd", "wifi", "set-wifi-enabled", if (enable) "enabled" else "disabled")
            "bluetooth" -> PrivilegedShell.run(context, "cmd", "bluetooth_manager", word)
            "mobile_data" -> PrivilegedShell.run(context, "svc", "data", word)
            "airplane" -> PrivilegedShell.run(context, "cmd", "connectivity", "airplane-mode", word)
            else -> throw ActionFailed(t("Unknown setting: $radio", "Réglage inconnu : $radio"))
        }
    }

    override suspend fun hotspotDetails(): Map<String, String> {
        val dump = PrivilegedShell.run(context, "dumpsys", "wifi")
        val ssid = Regex("mCurrentSoftApConfiguration:\\s*ssid = \"(.*)\"").find(dump)?.groupValues?.get(1).orEmpty()
        val pass = Regex("Passphrase = (\\S.*)").find(dump)?.groupValues?.get(1)?.trim()?.takeIf { !it.startsWith("<") }.orEmpty()
        return mapOf("ssid" to ssid, "password" to pass, "security" to if (pass.isEmpty()) "" else "WPA")
    }

    override suspend fun runPrivileged(command: String): String = PrivilegedShell.runLine(context, command)

    // ---- places, people, files, media, smart features (the details live in AndroidServices.kt)

    private val media = MediaHelper(context)

    override suspend fun currentLocation(): LocationInfo = LocationHelper.current(context)
    override fun reverseGeocode(latitude: Double, longitude: Double): Map<String, String> = LocationHelper.reverse(context, latitude, longitude)
    override fun networkDetails(): Map<String, String> = LocationHelper.network(context)
    override fun findEvents(fromMillis: Long, toMillis: Long, limit: Int): List<CalendarEventInfo> = PeopleHelper.events(context, fromMillis, toMillis, limit)
    override fun findContacts(query: String, limit: Int): List<ContactInfo> = PeopleHelper.contacts(context, query, limit)
    override fun sendSms(number: String, body: String) = PeopleHelper.sendSms(context, number, body)

    override fun readUri(uri: String): ByteArray = media.readUri(uri)
    override fun writeUri(uri: String, bytes: ByteArray) = media.writeUri(uri, bytes)
    override fun tempWrite(name: String, bytes: ByteArray, append: Boolean) = media.tempWrite(name, bytes, append)
    override fun tempRead(name: String): ByteArray? = media.tempRead(name)
    override fun tempList(): List<String> = media.tempList()
    override fun tempDelete(name: String): Boolean = media.tempDelete(name)
    override fun latestPhotos(count: Int): List<PickedFile> = media.latestPhotos(count)
    override fun editImage(bytes: ByteArray, edit: ImageEdit): ImageResult = media.edit(bytes, edit)
    override fun imageSize(bytes: ByteArray): Pair<Int, Int> = media.imageSize(bytes)
    override fun startRecording() = media.startRecording()
    override fun stopRecording(): ByteArray = media.stopRecording()

    override suspend fun dictate(languageTag: String): String = SmartHelper.dictate(context, languageTag)
    override suspend fun recognizeText(bytes: ByteArray): String = SmartHelper.recognizeText(bytes)
    override suspend fun translate(text: String, sourceTag: String?, targetTag: String): String = SmartHelper.translate(text, sourceTag, targetTag)
    override suspend fun detectLanguage(text: String): String = SmartHelper.detectLanguage(text)
    override fun makeQrCode(text: String, size: Int): ByteArray = SmartHelper.qrCode(text, size)
    override suspend fun scanCodes(bytes: ByteArray): List<String> = SmartHelper.scanCodes(bytes)

    private companion object {
        const val MAX_DOWNLOAD = 40 * 1024 * 1024
        const val CHANNEL = "shortcuts"
        var torchOn = false
    }
}
