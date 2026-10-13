package com.daisychain.engine

import com.daisychain.app.automation.TriggerLogic
import com.daisychain.app.automation.TriggerParams
import com.daisychain.app.automation.TriggerType
import java.time.ZoneId
import java.time.ZonedDateTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TriggerLogicTest {
    private val zone = ZoneId.of("Europe/Paris")
    private fun at(y: Int, m: Int, d: Int, h: Int, min: Int) = ZonedDateTime.of(y, m, d, h, min, 0, 0, zone).toInstant().toEpochMilli()

    @Test
    fun theNextTimeIsTodayWhenItIsStillAhead() {
        // Wednesday 7 October 2026, 06:00 -> 07:30 the same day
        assertEquals(at(2026, 10, 7, 7, 30), TriggerLogic.nextTimeFire(at(2026, 10, 7, 6, 0), zone, 7, 30, TriggerParams.ALL_DAYS))
    }

    @Test
    fun theNextTimeIsTomorrowWhenItHasPassed() {
        assertEquals(at(2026, 10, 8, 7, 30), TriggerLogic.nextTimeFire(at(2026, 10, 7, 8, 0), zone, 7, 30, TriggerParams.ALL_DAYS))
        // exactly at the time counts as passed
        assertEquals(at(2026, 10, 8, 7, 30), TriggerLogic.nextTimeFire(at(2026, 10, 7, 7, 30), zone, 7, 30, TriggerParams.ALL_DAYS))
    }

    @Test
    fun onlyTheChosenDaysCount() {
        val mondayOnly = 1 // bit 0 = Monday
        // Wednesday 7 Oct -> next Monday is 12 Oct
        assertEquals(at(2026, 10, 12, 9, 0), TriggerLogic.nextTimeFire(at(2026, 10, 7, 6, 0), zone, 9, 0, mondayOnly))
        val weekend = 32 or 64
        assertEquals(at(2026, 10, 10, 9, 0), TriggerLogic.nextTimeFire(at(2026, 10, 7, 6, 0), zone, 9, 0, weekend)) // Saturday
        assertNull(TriggerLogic.nextTimeFire(at(2026, 10, 7, 6, 0), zone, 9, 0, 0))
    }

    @Test
    fun theBatteryTriggersFireOnlyWhenTheLevelIsCrossed() {
        assertTrue(TriggerLogic.crossedBelow(21, 20, 20))
        assertFalse(TriggerLogic.crossedBelow(20, 19, 20)) // it was already at or under
        assertFalse(TriggerLogic.crossedBelow(null, 10, 20)) // first reading: nothing happened yet
        assertTrue(TriggerLogic.crossedAbove(79, 80, 80))
        assertFalse(TriggerLogic.crossedAbove(80, 81, 80))
    }

    @Test
    fun namesMatchLoosely() {
        assertTrue(TriggerLogic.nameMatches("", "anything"))
        assertTrue(TriggerLogic.nameMatches("home", "My Home Wi-Fi"))
        assertFalse(TriggerLogic.nameMatches("home", "Office"))
        assertFalse(TriggerLogic.nameMatches("home", null))
    }

    @Test
    fun settingsSurviveBeingSaved() {
        val params = TriggerParams(hour = 22, minute = 5, days = 31, level = 15, name = "Car", token = "abc")
        assertEquals(params, TriggerParams.fromJson(params.toJson()))
        assertEquals(TriggerParams(), TriggerParams.fromJson("not json"))
        assertEquals(TriggerType.Nfc, TriggerType.of("nfc"))
        assertNull(TriggerType.of("nope"))
    }
}
