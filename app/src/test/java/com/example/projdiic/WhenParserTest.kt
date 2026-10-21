package com.example.projdiic

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar
import java.util.TimeZone

class WhenParserTest {

    private val utc = TimeZone.getTimeZone("UTC")

    // Friday 16 October 2026, 14:50.
    private val now = at(16, 14, 50)

    private fun at(day: Int, hour: Int, minute: Int = 0, month: Int = Calendar.OCTOBER): Long =
        Calendar.getInstance(utc).apply {
            clear()
            set(2026, month, day, hour, minute)
        }.timeInMillis

    private fun endOf(day: Int, month: Int = Calendar.OCTOBER): Long = at(day, 0, 0, month) + 24 * 3_600_000L - 1

    private fun parse(text: String) = WhenParser.parse(text, now, utc)

    @Test
    fun aTaskWithoutATimeIsLeftAlone() {
        val parsed = parse("Reply to the group chat")

        assertEquals("Reply to the group chat", parsed.title)
        assertNull(parsed.window)
    }

    @Test
    fun tomorrowIsTheTomorrowChip() {
        val parsed = parse("Go to doctor tomorrow")

        assertEquals("Go to doctor", parsed.title)
        assertEquals(WhenChoices.TOMORROW, parsed.window?.key)
        assertEquals(at(17, 8), parsed.window?.start)
        assertEquals(endOf(17), parsed.window?.end)
        assertFalse(parsed.window!!.hasTime)
    }

    @Test
    fun tonightAndTodayAreTheirChips() {
        val tonight = parse("call mum tonight")
        val today = parse("pay rent today")

        assertEquals("call mum", tonight.title)
        assertEquals(WhenChoices.TONIGHT, tonight.window?.key)
        assertEquals(at(16, 19), tonight.window?.start)
        assertEquals(WhenChoices.TODAY, today.window?.key)
        assertEquals(now, today.window?.start)
        assertEquals(endOf(16), today.window?.end)
    }

    @Test
    fun aClockTimeIsWhenItIsDue() {
        val parsed = parse("Dentist at 3pm")

        assertEquals("Dentist", parsed.title)
        assertEquals(WhenChoices.CUSTOM, parsed.window?.key)
        assertEquals(now, parsed.window?.start)
        assertEquals(at(16, 15), parsed.window?.end)
        assertTrue(parsed.window!!.hasTime)
    }

    @Test
    fun readsTheWaysSpeechRecognitionWritesTimes() {
        assertEquals(at(16, 15, 30), parse("Dentist at 3:30 p.m.").window?.end)
        assertEquals(at(16, 17, 45), parse("Meeting 17:45").window?.end)
        assertEquals(at(16, 17), parse("Call Ana at 5").window?.end)
        assertEquals(at(17, 12), parse("Lunch with Rui at noon").window?.end) // noon has gone: tomorrow
    }

    @Test
    fun aDayAndATimeTogether() {
        val parsed = parse("Dentist tomorrow at 3 pm")

        assertEquals("Dentist", parsed.title)
        assertEquals(at(17, 8), parsed.window?.start)
        assertEquals(at(17, 15), parsed.window?.end)
    }

    @Test
    fun aTimeThatHasPassedMeansTomorrow() {
        assertEquals(at(17, 9), parse("Gym at 9am").window?.end)
    }

    @Test
    fun onAWeekdayWaitsForThatDay() {
        val parsed = parse("Gym on Monday")

        assertEquals("Gym", parsed.title)
        assertEquals(at(19, 8), parsed.window?.start)
        assertEquals(endOf(19), parsed.window?.end)
    }

    @Test
    fun byAWeekdayIsADeadlineFromNow() {
        val parsed = parse("Return the library book by Wednesday")

        assertEquals("Return the library book", parsed.title)
        assertEquals(now, parsed.window?.start)
        assertEquals(endOf(21), parsed.window?.end)
    }

    @Test
    fun todaysWeekdayMeansToday() {
        val parsed = parse("Clean my room friday")

        assertEquals(now, parsed.window?.start)
        assertEquals(endOf(16), parsed.window?.end)
    }

    @Test
    fun weeksAndWeekends() {
        val thisWeek = parse("Finish essay this week")
        val nextWeek = parse("Plan trip next week")
        val weekend = parse("Laundry this weekend")

        assertEquals("Finish essay", thisWeek.title)
        assertEquals(WhenChoices.THIS_WEEK, thisWeek.window?.key)
        assertEquals(endOf(18), thisWeek.window?.end)
        assertEquals(at(19, 8), nextWeek.window?.start)
        assertEquals(endOf(25), nextWeek.window?.end)
        assertEquals("Laundry", weekend.title)
        assertEquals(at(17, 8), weekend.window?.start)
        assertEquals(endOf(18), weekend.window?.end)
    }

    @Test
    fun thisMonth() {
        val rent = parse("Pay rent this month")

        assertEquals("Pay rent", rent.title)
        assertEquals(WhenChoices.THIS_MONTH, rent.window?.key)
        assertEquals(endOf(31), rent.window?.end)
    }

    @Test
    fun inSomeTime() {
        val hours = parse("Stretch in 2 hours")
        val halfHour = parse("Text Joana in half an hour")
        val days = parse("Water plants in three days")

        assertEquals("Stretch", hours.title)
        assertEquals(now + 2 * 3_600_000L, hours.window?.start)
        assertEquals(now + 30 * 60_000L, halfHour.window?.start)
        assertEquals(at(19, 8), days.window?.start)
        assertEquals(endOf(19), days.window?.end)
    }

    @Test
    fun datesInTheFormsPeopleSayThem() {
        listOf("Submit form on October 25th", "Submit form on the 25th of October", "Submit form oct 25").forEach {
            val parsed = parse(it)
            assertEquals(it, "Submit form", parsed.title)
            assertEquals(it, at(25, 8), parsed.window?.start)
            assertEquals(it, endOf(25), parsed.window?.end)
        }
    }

    @Test
    fun aDayOfTheMonthThatHasPassedMeansNextMonth() {
        val parsed = parse("Pay bills on the 3rd")

        assertEquals("Pay bills", parsed.title)
        assertEquals(at(3, 8, month = Calendar.NOVEMBER), parsed.window?.start)
    }

    @Test
    fun wordsInsideOtherWordsAndPlainNumbersAreNotTimes() {
        assertNull(parse("Read the Tomorrowland review").window)
        assertNull(parse("Buy 3 apples").window)
        assertNull(parse("I may finish chapter 2").window)
    }

    @Test
    fun keepsWordsThatAreNotAboutTime() {
        assertEquals("Turn the lights on", parse("Turn the lights on tomorrow").title)
        assertEquals("Laundry", parse("Laundry on the weekend").title)
        assertNull(parse("Meet at 25").window)
    }

    @Test
    fun aTaskThatIsOnlyATimeKeepsItsWords() {
        val parsed = parse("tomorrow")

        assertEquals("tomorrow", parsed.title)
        assertEquals(WhenChoices.TOMORROW, parsed.window?.key)
    }
}
