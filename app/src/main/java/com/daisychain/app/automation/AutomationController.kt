package com.daisychain.app.automation

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.content.ContextCompat
import com.daisychain.app.DaisychainApp
import com.daisychain.app.data.db.AutomationEntity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.time.ZoneId

/** Keeps the phone in step with the automations: alarms for the timed ones, a listening service for the others. */
object AutomationController {
    private const val EXTRA_ID = "automation_id"

    /** Plans the alarms and starts or stops the listening service so they match [automations]. */
    fun sync(context: Context, automations: List<AutomationEntity>) {
        val enabled = automations.filter { it.enabled }
        scheduleTimed(context, automations)
        val needsService = enabled.any { TriggerType.of(it.triggerType)?.dynamic == true }
        val intent = Intent(context, AutomationService::class.java)
        if (needsService) {
            runCatching { ContextCompat.startForegroundService(context, intent) }
        } else {
            context.stopService(intent)
        }
    }

    private fun pending(context: Context, id: Long, create: Boolean): PendingIntent? {
        val intent = Intent(context, AutomationAlarmReceiver::class.java).putExtra(EXTRA_ID, id)
        val flags = PendingIntent.FLAG_IMMUTABLE or if (create) PendingIntent.FLAG_UPDATE_CURRENT else PendingIntent.FLAG_NO_CREATE
        return PendingIntent.getBroadcast(context, id.toInt(), intent, flags)
    }

    /** Whether alarms can be set to the exact minute (Android 12+ asks the user for that). */
    fun canScheduleExact(context: Context): Boolean =
        Build.VERSION.SDK_INT < 31 || (context.getSystemService(Context.ALARM_SERVICE) as AlarmManager).canScheduleExactAlarms()

    private fun scheduleTimed(context: Context, automations: List<AutomationEntity>) {
        val alarms = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        for (a in automations) {
            val timed = TriggerType.of(a.triggerType) == TriggerType.Time
            if (!timed || !a.enabled) {
                pending(context, a.id, create = false)?.let { alarms.cancel(it); it.cancel() }
                continue
            }
            val p = TriggerParams.fromJson(a.param)
            val at = TriggerLogic.nextTimeFire(System.currentTimeMillis(), ZoneId.systemDefault(), p.hour, p.minute, p.days) ?: continue
            val pending = pending(context, a.id, create = true)!!
            if (canScheduleExact(context)) alarms.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pending)
            else alarms.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pending)
        }
    }

    /** An alarm went off: run that automation, then plan the next one. */
    fun onAlarm(context: Context, intent: Intent, pendingResult: BroadcastReceiver.PendingResult) {
        val id = intent.getLongExtra(EXTRA_ID, -1)
        val app = context.applicationContext as DaisychainApp
        CoroutineScope(Dispatchers.Default).launch {
            try {
                app.shortcutRepository.automation(id)?.takeIf { it.enabled }?.let { AutomationRunner.fire(context, it) }
                sync(context, app.shortcutRepository.enabledAutomations())
            } finally {
                pendingResult.finish()
            }
        }
    }

    /** After a reboot or an update: everything that was planned is gone, plan it again. */
    fun restore(context: Context, pendingResult: BroadcastReceiver.PendingResult) {
        val app = context.applicationContext as DaisychainApp
        CoroutineScope(Dispatchers.Default).launch {
            try {
                sync(context, app.shortcutRepository.enabledAutomations())
            } finally {
                pendingResult.finish()
            }
        }
    }
}

class AutomationAlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) = AutomationController.onAlarm(context, intent, goAsync())
}

class AutomationBootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) = AutomationController.restore(context, goAsync())
}
