package com.daisychain.app.automation

import android.annotation.SuppressLint
import android.app.Service
import android.bluetooth.BluetoothDevice
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ServiceInfo
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.net.wifi.WifiManager
import android.os.BatteryManager
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import com.daisychain.app.DaisychainApp
import com.daisychain.app.MainActivity
import com.daisychain.app.R
import com.daisychain.app.data.db.AutomationEntity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * Listens while the app is in the background so that automations such as "when the charger is connected" can start.
 * It only runs when at least one such automation is switched on, and shows a quiet notification while it does.
 */
class AutomationService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var lastBattery: Int? = null
    private var wifiName: String? = null
    private var wifiUp = false
    private lateinit var connectivity: ConnectivityManager

    private val receiver = object : BroadcastReceiver() {
        @SuppressLint("MissingPermission")
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.action) {
                Intent.ACTION_POWER_CONNECTED -> fire(TriggerType.ChargerConnected)
                Intent.ACTION_POWER_DISCONNECTED -> fire(TriggerType.ChargerDisconnected)
                Intent.ACTION_BATTERY_CHANGED -> onBattery(intent)
                Intent.ACTION_AIRPLANE_MODE_CHANGED ->
                    fire(if (intent.getBooleanExtra("state", false)) TriggerType.AirplaneOn else TriggerType.AirplaneOff)
                BluetoothDevice.ACTION_ACL_CONNECTED, BluetoothDevice.ACTION_ACL_DISCONNECTED -> {
                    @Suppress("DEPRECATION")
                    val device = intent.getParcelableExtra<BluetoothDevice>(BluetoothDevice.EXTRA_DEVICE)
                    val name = runCatching { device?.name }.getOrNull()
                    val type = if (intent.action == BluetoothDevice.ACTION_ACL_CONNECTED) TriggerType.BluetoothConnected else TriggerType.BluetoothDisconnected
                    fire(type) { TriggerLogic.nameMatches(TriggerParams.fromJson(it.param).name, name) }
                }
            }
        }
    }

    private val wifiCallback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) {
            val name = currentSsid()
            if (wifiUp && name == wifiName) return
            wifiUp = true
            wifiName = name
            fire(TriggerType.WifiConnected) { TriggerLogic.nameMatches(TriggerParams.fromJson(it.param).name, name) }
        }

        override fun onLost(network: Network) {
            val name = wifiName
            wifiUp = false
            wifiName = null
            fire(TriggerType.WifiDisconnected) { TriggerLogic.nameMatches(TriggerParams.fromJson(it.param).name, name) }
        }
    }

    @Suppress("DEPRECATION")
    private fun currentSsid(): String? {
        val wifi = applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
        return wifi.connectionInfo?.ssid?.trim('"')?.takeIf { it.isNotEmpty() && it != "<unknown ssid>" }
    }

    private fun fire(type: TriggerType, matches: (AutomationEntity) -> Boolean = { true }) {
        val repo = (application as DaisychainApp).shortcutRepository
        scope.launch {
            repo.automationsOfType(type.id).filter { it.enabled && matches(it) }.forEach { AutomationRunner.fire(applicationContext, it) }
        }
    }

    private fun onBattery(intent: Intent) {
        val level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
        val scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
        if (level < 0 || scale <= 0) return
        val current = level * 100 / scale
        val previous = lastBattery
        lastBattery = current
        if (previous == null || previous == current) return
        val repo = (application as DaisychainApp).shortcutRepository
        scope.launch {
            repo.automationsOfType(TriggerType.BatteryBelow.id).filter { it.enabled }
                .filter { TriggerLogic.crossedBelow(previous, current, TriggerParams.fromJson(it.param).level) }
                .forEach { AutomationRunner.fire(applicationContext, it) }
            repo.automationsOfType(TriggerType.BatteryAbove.id).filter { it.enabled }
                .filter { TriggerLogic.crossedAbove(previous, current, TriggerParams.fromJson(it.param).level) }
                .forEach { AutomationRunner.fire(applicationContext, it) }
        }
    }

    override fun onCreate() {
        super.onCreate()
        AutomationNotifications.ensureChannels(this)
        val notification = NotificationCompat.Builder(this, AutomationNotifications.CHANNEL_SERVICE)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(getString(R.string.automation_service_title))
            .setContentText(getString(R.string.automation_service_text))
            .setOngoing(true)
            .setContentIntent(
                android.app.PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java), android.app.PendingIntent.FLAG_IMMUTABLE),
            )
            .build()
        val type = if (Build.VERSION.SDK_INT >= 34) ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE else 0
        ServiceCompat.startForeground(this, 1, notification, type)

        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_POWER_CONNECTED)
            addAction(Intent.ACTION_POWER_DISCONNECTED)
            addAction(Intent.ACTION_BATTERY_CHANGED)
            addAction(Intent.ACTION_AIRPLANE_MODE_CHANGED)
            addAction(BluetoothDevice.ACTION_ACL_CONNECTED)
            addAction(BluetoothDevice.ACTION_ACL_DISCONNECTED)
        }
        if (Build.VERSION.SDK_INT >= 33) registerReceiver(receiver, filter, Context.RECEIVER_EXPORTED) else registerReceiver(receiver, filter)
        connectivity = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        connectivity.registerNetworkCallback(NetworkRequest.Builder().addTransportType(NetworkCapabilities.TRANSPORT_WIFI).build(), wifiCallback)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int) = START_STICKY

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        runCatching { unregisterReceiver(receiver) }
        runCatching { connectivity.unregisterNetworkCallback(wifiCallback) }
        scope.cancel()
        super.onDestroy()
    }
}
