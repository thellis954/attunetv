package com.attunetv.launcher

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.util.Calendar
import java.util.TimeZone

class IcsTest {

    private val ny = TimeZone.getTimeZone("America/New_York")

    @Before fun tz() = TimeZone.setDefault(ny)

    private fun at(y: Int, m: Int, d: Int, h: Int = 0, min: Int = 0) =
        Calendar.getInstance(ny).apply { clear(); set(y, m - 1, d, h, min) }.timeInMillis

    private fun cal(vararg events: String) =
        "BEGIN:VCALENDAR\r\nVERSION:2.0\r\n" + events.joinToString("") { "BEGIN:VEVENT\r\n$it\r\nEND:VEVENT\r\n" } + "END:VCALENDAR\r\n"

    @Test fun singleUtcEvent() {
        val ics = cal("UID:a\r\nDTSTART:20261106T200000Z\r\nDTEND:20261106T210000Z\r\nSUMMARY:Dentist\\, cleaning")
        val ev = Ics.events(ics, at(2026, 11, 6), at(2026, 11, 7))
        assertEquals(1, ev.size)
        assertEquals("Dentist, cleaning", ev[0].title)
        assertEquals(at(2026, 11, 6, 15), ev[0].start) // 20:00Z = 3pm EST
    }

    @Test fun allDayAndFolding() {
        val ics = cal("UID:b\r\nDTSTART;VALUE=DATE:20261108\r\nDTEND;VALUE=DATE:20261109\r\nSUMMARY:Soccer tour\r\n nament")
        val ev = Ics.events(ics, at(2026, 11, 6), at(2026, 11, 14))
        assertEquals("Soccer tournament", ev.single().title)
        assertTrue(ev.single().allDay)
        assertEquals(at(2026, 11, 8), ev.single().start)
    }

    @Test fun tzidAndAlarmIgnored() {
        val ics = cal("UID:c\r\nDTSTART;TZID=America/Los_Angeles:20261107T090000\r\nDTEND;TZID=America/Los_Angeles:20261107T100000\r\nSUMMARY:Call\r\nBEGIN:VALARM\r\nTRIGGER:-PT10M\r\nSUMMARY:alarm\r\nEND:VALARM")
        val ev = Ics.events(ics, at(2026, 11, 6), at(2026, 11, 14)).single()
        assertEquals("Call", ev.title)
        assertEquals(at(2026, 11, 7, 12), ev.start) // 9am PT = noon ET
    }

    @Test fun weeklyByDayWithExdateAndOverride() {
        val ics = cal(
            "UID:w\r\nDTSTART;TZID=America/New_York:20261005T180000\r\nDTEND;TZID=America/New_York:20261005T190000\r\n" +
                "RRULE:FREQ=WEEKLY;BYDAY=MO,WE\r\nEXDATE;TZID=America/New_York:20261111T180000\r\nSUMMARY:Practice",
            "UID:w\r\nRECURRENCE-ID;TZID=America/New_York:20261109T180000\r\nDTSTART;TZID=America/New_York:20261109T190000\r\n" +
                "DTEND;TZID=America/New_York:20261109T200000\r\nSUMMARY:Practice (late)",
        )
        val ev = Ics.events(ics, at(2026, 11, 6), at(2026, 11, 20))
        assertEquals(
            listOf(
                "Practice (late)" to at(2026, 11, 9, 19),
                "Practice" to at(2026, 11, 16, 18),
                "Practice" to at(2026, 11, 18, 18),
            ),
            ev.map { it.title to it.start },
        )
    }

    @Test fun countLimitsSeries() {
        val ics = cal("UID:d\r\nDTSTART:20261101T150000Z\r\nRRULE:FREQ=DAILY;COUNT=7\r\nSUMMARY:Pill")
        val ev = Ics.events(ics, at(2026, 11, 6), at(2026, 11, 20))
        assertEquals(2, ev.size) // Nov 6 and 7 are the last two of seven
    }

    @Test fun monthlyNthWeekdayAndUntil() {
        val ics = cal("UID:m\r\nDTSTART;TZID=America/New_York:20260901T190000\r\nRRULE:FREQ=MONTHLY;BYDAY=2TU;UNTIL=20261231T000000Z\r\nSUMMARY:HOA")
        val ev = Ics.events(ics, at(2026, 11, 1), at(2027, 3, 1))
        assertEquals(listOf(at(2026, 11, 10, 19), at(2026, 12, 8, 19)), ev.map { it.start })
    }

    @Test fun cancelledSkipped() {
        val ics = cal("UID:x\r\nDTSTART:20261107T150000Z\r\nSTATUS:CANCELLED\r\nSUMMARY:Nope")
        assertTrue(Ics.events(ics, at(2026, 11, 6), at(2026, 11, 14)).isEmpty())
    }
}
