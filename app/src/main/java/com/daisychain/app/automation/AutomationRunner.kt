package com.daisychain.app.automation

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.daisychain.app.DaisychainApp
import com.daisychain.app.R
import com.daisychain.app.data.db.AutomationEntity
import com.daisychain.app.data.db.ShortcutEntity
import com.daisychain.app.run.AndroidDevice
import com.daisychain.app.run.HomeShortcuts
import com.daisychain.engine.edit.ActionOps
import com.daisychain.engine.model.ActionsJson
import com.daisychain.engine.run.ActionFailed
import com.daisychain.engine.run.RunFailure
import com.daisychain.engine.run.RunProgress
import com.daisychain.engine.run.RunUi
import com.daisychain.engine.run.ShortcutRunner
import com.daisychain.engine.spec.t
import kotlinx.coroutines.flow.first
import java.util.Locale

/** Notifications from automations. */
internal object AutomationNotifications {
    const val CHANNEL_ASK = "automation_ask"
    const val CHANNEL_SERVICE = "automation_service"

    fun ensureChannels(context: Context) {
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.createNotificationChannel(NotificationChannel(CHANNEL_ASK, context.getString(R.string.automation_channel), NotificationManager.IMPORTANCE_HIGH))
        manager.createNotificationChannel(NotificationChannel(CHANNEL_SERVICE, context.getString(R.string.automation_service_channel), NotificationManager.IMPORTANCE_MIN))
    }

    fun canNotify(context: Context): Boolean =
        Build.VERSION.SDK_INT < 33 || ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

    fun post(context: Context, id: Int, title: String, text: String, tap: PendingIntent? = null) {
        if (!canNotify(context)) return
        ensureChannels(context)
        val builder = NotificationCompat.Builder(context, CHANNEL_ASK)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setAutoCancel(true)
        tap?.let { builder.setContentIntent(it) }
        (context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager).notify(id, builder.build())
    }
}

/**
 * Starts the shortcut of an automation. Either it asks first with a notification (tapping it runs the shortcut on screen,
 * where every block works), or it runs by itself in the background: then questions become notifications, and blocks that
 * need the screen are refused by Android.
 */
object AutomationRunner {
    suspend fun fire(context: Context, automation: AutomationEntity) {
        val app = context.applicationContext as DaisychainApp
        val shortcut = app.shortcutRepository.observeShortcut(automation.shortcutId).first() ?: return
        if (automation.askFirst) askFirst(context, automation, shortcut) else runInBackground(context, shortcut)
    }

    private fun askFirst(context: Context, automation: AutomationEntity, shortcut: ShortcutEntity) {
        val tap = PendingIntent.getActivity(
            context, automation.id.toInt(), HomeShortcuts.runIntent(context, shortcut.id).addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        AutomationNotifications.post(
            context, 10_000 + automation.id.toInt(),
            context.getString(R.string.automation_ask_title, shortcut.name),
            context.getString(R.string.automation_ask_text),
            tap,
        )
    }

    private suspend fun runInBackground(context: Context, shortcut: ShortcutEntity) {
        val app = context.applicationContext as DaisychainApp
        val actions = ActionOps.sanitize(ActionsJson.decode(shortcut.actionsJson))
        val noProgress = object : RunProgress {
            override fun onStart(uuid: String) {}
            override fun onFinish(uuid: String) {}
        }
        val language = Locale.getDefault().language
        try {
            ShortcutRunner(AndroidDevice(context.applicationContext), BackgroundUi(context, shortcut.name), 0, app.shortcutRepository.library)
                .run(actions, noProgress)
        } catch (e: RunFailure) {
            AutomationNotifications.post(context, 20_000 + shortcut.id.toInt(), context.getString(R.string.automation_failed_title, shortcut.name), e.text.resolve(language))
        }
    }

    /** Answers for a run nobody is watching: results and alerts become notifications. */
    private class BackgroundUi(private val context: Context, private val name: String) : RunUi {
        override suspend fun showResult(text: String, image: com.daisychain.engine.run.RunValue.Image?, allowCopy: Boolean) {
            AutomationNotifications.post(context, 30_000 + name.hashCode() % 1000, name, text)
        }

        override suspend fun alert(title: String, message: String, allowCancel: Boolean): Boolean {
            AutomationNotifications.post(context, 31_000 + name.hashCode() % 1000, title.ifEmpty { name }, message)
            return true
        }

        override suspend fun choose(prompt: String, items: List<String>): Int = needsScreen()
        override suspend fun ask(prompt: String): String = needsScreen()

        private fun needsScreen(): Nothing = throw ActionFailed(
            t("This step needs the screen: set the automation to ask first", "Cette étape a besoin de l'écran : réglez l'automatisation sur « demander d'abord »"),
        )
    }
}
