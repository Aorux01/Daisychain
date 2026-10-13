package com.daisychain.app.ui.automation

import android.Manifest
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.outlined.Bolt
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimeInput
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.daisychain.app.R
import com.daisychain.app.automation.AutomationController
import com.daisychain.app.automation.TriggerParams
import com.daisychain.app.automation.TriggerType
import com.daisychain.app.data.db.AutomationEntity
import com.daisychain.app.data.db.ShortcutEntity
import com.daisychain.app.ui.common.ShortcutPickList
import com.daisychain.app.ui.theme.Teal
import java.time.DayOfWeek
import java.time.format.TextStyle
import java.util.Locale
import java.util.UUID

@Composable
private fun triggerLabel(type: TriggerType): String = stringResource(
    when (type) {
        TriggerType.Time -> R.string.trigger_time
        TriggerType.ChargerConnected -> R.string.trigger_charger_on
        TriggerType.ChargerDisconnected -> R.string.trigger_charger_off
        TriggerType.BatteryBelow -> R.string.trigger_battery_below
        TriggerType.BatteryAbove -> R.string.trigger_battery_above
        TriggerType.WifiConnected -> R.string.trigger_wifi_on
        TriggerType.WifiDisconnected -> R.string.trigger_wifi_off
        TriggerType.BluetoothConnected -> R.string.trigger_bt_on
        TriggerType.BluetoothDisconnected -> R.string.trigger_bt_off
        TriggerType.AirplaneOn -> R.string.trigger_airplane_on
        TriggerType.AirplaneOff -> R.string.trigger_airplane_off
        TriggerType.Nfc -> R.string.trigger_nfc
    },
)

/** "Battery below 20%", "At 07:30 on Mon, Tue"... */
@Composable
private fun summary(a: AutomationEntity): String {
    val type = TriggerType.of(a.triggerType) ?: return a.triggerType
    val p = TriggerParams.fromJson(a.param)
    val base = triggerLabel(type)
    return when (type) {
        TriggerType.Time -> {
            val time = "%02d:%02d".format(p.hour, p.minute)
            val days = if (p.days == TriggerParams.ALL_DAYS) stringResource(R.string.summary_every_day)
            else DayOfWeek.entries.filterIndexed { i, _ -> p.days and (1 shl i) != 0 }.joinToString(", ") { it.getDisplayName(TextStyle.SHORT, Locale.getDefault()) }
            "$base $time · $days"
        }
        TriggerType.BatteryBelow, TriggerType.BatteryAbove -> "$base ${p.level}%"
        TriggerType.WifiConnected, TriggerType.WifiDisconnected, TriggerType.BluetoothConnected, TriggerType.BluetoothDisconnected ->
            if (p.name.isBlank()) base else "$base « ${p.name} »"
        else -> base
    }
}

@Composable
fun AutomationsScreen(vm: AutomationsViewModel = viewModel(factory = AutomationsViewModel.Factory)) {
    val automations by vm.automations.collectAsStateWithLifecycle()
    val shortcuts by vm.shortcuts.collectAsStateWithLifecycle()
    var editing by remember { mutableStateOf<AutomationEntity?>(null) }
    var creating by remember { mutableStateOf(false) }

    Column(Modifier.fillMaxSize().safeDrawingPadding()) {
        Row(Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, top = 16.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.nav_automations), style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
            Button(onClick = { creating = true }, enabled = shortcuts.isNotEmpty(), colors = ButtonDefaults.buttonColors(containerColor = Teal)) {
                Icon(Icons.Filled.Add, null, Modifier.size(18.dp))
                Text(stringResource(R.string.new_shortcut), Modifier.padding(start = 6.dp))
            }
        }
        if (automations.isEmpty()) {
            Column(Modifier.fillMaxSize().padding(32.dp), verticalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterVertically), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(stringResource(R.string.automation_empty_title), style = MaterialTheme.typography.titleMedium)
                Text(stringResource(R.string.automation_empty_message), color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        } else {
            LazyColumn(contentPadding = PaddingValues(horizontal = 20.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                items(automations, key = { it.id }) { a ->
                    Surface(shape = RoundedCornerShape(20.dp), color = MaterialTheme.colorScheme.surface, modifier = Modifier.fillMaxWidth().clickable { editing = a }) {
                        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Outlined.Bolt, null, tint = MaterialTheme.colorScheme.primary)
                            Column(Modifier.weight(1f).padding(horizontal = 14.dp)) {
                                Text(a.name.ifBlank { summary(a) }, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                                Text(
                                    summary(a) + " → " + (shortcuts.firstOrNull { it.id == a.shortcutId }?.name ?: "?"),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            Switch(checked = a.enabled, onCheckedChange = { vm.setEnabled(a, it) })
                        }
                    }
                }
            }
        }
    }

    if (creating || editing != null) {
        AutomationEditor(
            initial = editing,
            shortcuts = shortcuts,
            onDismiss = { creating = false; editing = null },
            onSave = { vm.save(it); creating = false; editing = null },
            onDelete = { vm.delete(it); creating = false; editing = null },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun AutomationEditor(
    initial: AutomationEntity?,
    shortcuts: List<ShortcutEntity>,
    onDismiss: () -> Unit,
    onSave: (AutomationEntity) -> Unit,
    onDelete: (AutomationEntity) -> Unit,
) {
    val context = LocalContext.current
    val startParams = remember { initial?.let { TriggerParams.fromJson(it.param) } ?: TriggerParams(token = UUID.randomUUID().toString().take(12)) }
    var type by remember { mutableStateOf(initial?.let { TriggerType.of(it.triggerType) } ?: TriggerType.Time) }
    var name by remember { mutableStateOf(initial?.name.orEmpty()) }
    var shortcutId by remember { mutableStateOf(initial?.shortcutId ?: shortcuts.firstOrNull()?.id) }
    var askFirst by remember { mutableStateOf(initial?.askFirst ?: true) }
    var days by remember { mutableIntStateOf(startParams.days) }
    var level by remember { mutableFloatStateOf(startParams.level.toFloat()) }
    var targetName by remember { mutableStateOf(startParams.name) }
    var choosingShortcut by remember { mutableStateOf(false) }
    val time = rememberTimePickerState(startParams.hour, startParams.minute, is24Hour = true)

    // Notifications are how an automation asks first and reports what happened.
    val notificationPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(if (initial == null) R.string.automation_new else R.string.automation_edit)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = name, onValueChange = { name = it }, singleLine = true, modifier = Modifier.fillMaxWidth(),
                    label = { Text(stringResource(R.string.automation_name)) },
                )
                Text(stringResource(R.string.automation_when), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                Column(Modifier.heightIn(max = 220.dp).verticalScroll(rememberScrollState())) {
                    TriggerType.entries.forEach { t ->
                        Row(Modifier.fillMaxWidth().selectable(type == t, role = Role.RadioButton) { type = t }, verticalAlignment = Alignment.CenterVertically) {
                            RadioButton(selected = type == t, onClick = null, modifier = Modifier.padding(10.dp))
                            Text(triggerLabel(t))
                        }
                    }
                }
                when (type) {
                    TriggerType.Time -> {
                        TimeInput(state = time)
                        Text(stringResource(R.string.automation_time_days), style = MaterialTheme.typography.labelLarge)
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            DayOfWeek.entries.forEachIndexed { i, day ->
                                FilterChip(
                                    selected = days and (1 shl i) != 0,
                                    onClick = { days = days xor (1 shl i) },
                                    label = { Text(day.getDisplayName(TextStyle.NARROW, Locale.getDefault())) },
                                )
                            }
                        }
                        if (!AutomationController.canScheduleExact(context)) {
                            Text(stringResource(R.string.automation_exact_alarm_hint), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            OutlinedButton(onClick = {
                                if (Build.VERSION.SDK_INT >= 31) {
                                    context.startActivity(Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, Uri.parse("package:${context.packageName}")))
                                }
                            }) { Text(stringResource(R.string.automation_exact_alarm_button)) }
                        }
                    }
                    TriggerType.BatteryBelow, TriggerType.BatteryAbove -> {
                        Text(stringResource(R.string.automation_battery_level, level.toInt()))
                        Slider(value = level, onValueChange = { level = it.toInt().toFloat() }, valueRange = 5f..95f)
                    }
                    TriggerType.WifiConnected, TriggerType.WifiDisconnected ->
                        OutlinedTextField(value = targetName, onValueChange = { targetName = it }, singleLine = true, modifier = Modifier.fillMaxWidth(), label = { Text(stringResource(R.string.automation_wifi_name)) })
                    TriggerType.BluetoothConnected, TriggerType.BluetoothDisconnected ->
                        OutlinedTextField(value = targetName, onValueChange = { targetName = it }, singleLine = true, modifier = Modifier.fillMaxWidth(), label = { Text(stringResource(R.string.automation_bt_name)) })
                    TriggerType.Nfc -> {
                        val address = "daisychain://nfc/${startParams.token}"
                        Text(stringResource(R.string.automation_nfc_help), style = MaterialTheme.typography.bodySmall)
                        Text(address, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                        OutlinedButton(onClick = {
                            (context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager).setPrimaryClip(ClipData.newPlainText("Daisychain", address))
                        }) { Text(stringResource(R.string.copy)) }
                    }
                    else -> Unit
                }
                if (type.dynamic) {
                    Text(stringResource(R.string.automation_background_hint), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Text(stringResource(R.string.automation_run), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                OutlinedButton(onClick = { choosingShortcut = true }, modifier = Modifier.fillMaxWidth()) {
                    Text(shortcuts.firstOrNull { it.id == shortcutId }?.name ?: stringResource(R.string.automation_choose_shortcut))
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(stringResource(R.string.automation_ask_first))
                        Text(stringResource(R.string.automation_ask_first_help), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Switch(checked = askFirst, onCheckedChange = { askFirst = it })
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = shortcutId != null && !(type == TriggerType.Time && days == 0),
                onClick = {
                    if (Build.VERSION.SDK_INT >= 33) notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
                    val params = TriggerParams(time.hour, time.minute, days, level.toInt(), targetName.trim(), startParams.token)
                    onSave(
                        (initial ?: AutomationEntity(name = "", triggerType = "", param = "", shortcutId = 0)).copy(
                            name = name.trim(), triggerType = type.id, param = params.toJson(), shortcutId = shortcutId!!, askFirst = askFirst,
                        ),
                    )
                },
            ) { Text(stringResource(R.string.save)) }
        },
        dismissButton = {
            Row {
                if (initial != null) TextButton(onClick = { onDelete(initial) }) { Text(stringResource(R.string.delete), color = MaterialTheme.colorScheme.error) }
                TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
            }
        },
    )

    if (choosingShortcut) {
        AlertDialog(
            onDismissRequest = { choosingShortcut = false },
            title = { Text(stringResource(R.string.automation_choose_shortcut)) },
            text = { Column(Modifier.heightIn(max = 420.dp)) { ShortcutPickList(shortcuts) { shortcutId = it.id; choosingShortcut = false } } },
            confirmButton = {},
            dismissButton = { TextButton(onClick = { choosingShortcut = false }) { Text(stringResource(R.string.cancel)) } },
        )
    }
}
