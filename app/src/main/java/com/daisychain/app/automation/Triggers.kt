package com.daisychain.app.automation

import org.json.JSONObject
import java.time.DayOfWeek
import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime

/** What can start an automation. [dynamic] ones need the app to listen while it runs in the background. */
enum class TriggerType(val id: String, val dynamic: Boolean) {
    Time("time", false),
    ChargerConnected("charger_on", true),
    ChargerDisconnected("charger_off", true),
    BatteryBelow("battery_below", true),
    BatteryAbove("battery_above", true),
    WifiConnected("wifi_on", true),
    WifiDisconnected("wifi_off", true),
    BluetoothConnected("bt_on", true),
    BluetoothDisconnected("bt_off", true),
    AirplaneOn("airplane_on", true),
    AirplaneOff("airplane_off", true),
    Nfc("nfc", false),
    ;

    companion object {
        fun of(id: String): TriggerType? = entries.firstOrNull { it.id == id }
    }
}

/** The settings of a trigger. Only the fields its type uses matter. */
data class TriggerParams(
    val hour: Int = 8,
    val minute: Int = 0,
    /** Days of the week, bit 0 = Monday ... bit 6 = Sunday. */
    val days: Int = ALL_DAYS,
    val level: Int = 20,
    /** Wi-Fi network or Bluetooth device name; empty = any. */
    val name: String = "",
    /** The secret in the address written on an NFC tag. */
    val token: String = "",
) {
    fun toJson(): String = JSONObject()
        .put("hour", hour).put("minute", minute).put("days", days).put("level", level).put("name", name).put("token", token)
        .toString()

    companion object {
        const val ALL_DAYS = 127

        fun fromJson(text: String): TriggerParams = runCatching {
            val j = JSONObject(text)
            TriggerParams(
                hour = j.optInt("hour", 8), minute = j.optInt("minute", 0), days = j.optInt("days", ALL_DAYS),
                level = j.optInt("level", 20), name = j.optString("name", ""), token = j.optString("token", ""),
            )
        }.getOrDefault(TriggerParams())
    }
}

object TriggerLogic {
    /** The next moment (in milliseconds) after [nowMillis] at [hour]:[minute] on one of the [days]; null when no day is chosen. */
    fun nextTimeFire(nowMillis: Long, zone: ZoneId, hour: Int, minute: Int, days: Int): Long? {
        if (days and TriggerParams.ALL_DAYS == 0) return null
        val now: ZonedDateTime = Instant.ofEpochMilli(nowMillis).atZone(zone)
        for (offset in 0..7) {
            val candidate = now.plusDays(offset.toLong()).withHour(hour.coerceIn(0, 23)).withMinute(minute.coerceIn(0, 59)).withSecond(0).withNano(0)
            val dayBit = 1 shl (candidate.dayOfWeek.value - DayOfWeek.MONDAY.value)
            if (candidate.toInstant().toEpochMilli() > nowMillis && days and dayBit != 0) return candidate.toInstant().toEpochMilli()
        }
        return null
    }

    /** The battery went down through [level] (it was above, it is now at or under). */
    fun crossedBelow(previous: Int?, current: Int, level: Int) = previous != null && previous > level && current <= level

    /** The battery went up through [level] (it was under, it is now at or above). */
    fun crossedAbove(previous: Int?, current: Int, level: Int) = previous != null && previous < level && current >= level

    /** A network or device name matches the wanted one (empty wanted = anything). */
    fun nameMatches(wanted: String, actual: String?): Boolean = wanted.isBlank() || actual?.contains(wanted.trim(), ignoreCase = true) == true
}
