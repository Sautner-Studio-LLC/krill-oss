package krill.zone.shared.trigger.cron

import krill.zone.shared.*
import krill.zone.shared.krillapp.trigger.cron.*
import krill.zone.shared.node.*
import kotlin.test.*

/**
 * Test class for CronLogic
 * Verifies that cron expressions with seconds are properly parsed and computed.
 */
class CronLogicTest {

    private val cronLogic = DefaultCronLogic()

    @Test
    fun testEveryFiveSeconds() {
        // Given: a cron expression for every 5 seconds
        val node = createCronNode("*/5 * * * * *")

        
        // When: getting wait time
        val waitMillis = cronLogic.getWaitMillis(node)

        // Then: wait time should be positive and less than 5 seconds
        assertTrue(waitMillis >= 0)
        assertTrue(waitMillis <= 5000)
    }

    @Test
    fun testEveryMinuteWithSeconds() {
        // Given: a cron expression for every minute at second 0
        val node = createCronNode("0 */1 * * * *")

        // When: getting wait time
        val waitMillis = cronLogic.getWaitMillis(node)

        // Then: wait time should be positive and less than or equal to 60 seconds
        assertTrue(waitMillis >= 0)
        assertTrue(waitMillis <= 60000)
    }

    @Test
    fun testDailyAtSpecificTime() {
        // Given: a cron expression for daily at 3:30:15
        val node = createCronNode("15 30 3 * * *")

        // When: getting wait time
        val waitMillis = cronLogic.getWaitMillis(node)

        // Then: wait time should be positive (within next 24 hours)
        assertTrue(waitMillis >= 0)
        assertTrue(waitMillis <= 24 * 60 * 60 * 1000)
    }

    @Test
    fun testLegacyFiveFieldFormat() {
        // Given: a legacy 5-field cron expression (no seconds)
        val node = createCronNode("*/5 * * * *")

        // When: getting wait time
        val waitMillis = cronLogic.getWaitMillis(node)

        // Then: wait time should be positive
        assertTrue(waitMillis >= 0)
        assertTrue(waitMillis <= 5 * 60 * 1000) // 5 minutes
    }





    @Test
    fun testWeeklyWithSeconds() {
        // Given: a cron expression for weekly on Sunday at 12:00:30
        val node = createCronNode("30 0 12 * * 0")

        // When: getting wait time
        val waitMillis = cronLogic.getWaitMillis(node)

        // Then: wait time should be positive (within next 7 days)
        assertTrue(waitMillis >= 0)
        assertTrue(waitMillis <= 7 * 24 * 60 * 60 * 1000)
    }

    @Test
    fun testMonthlyWithSeconds() {
        // Given: a cron expression for monthly on the 15th at 09:45:20
        val node = createCronNode("20 45 9 15 * *")

        // When: getting wait time
        val waitMillis = cronLogic.getWaitMillis(node)

        // Then: wait time should be positive (within next ~31 days)
        assertTrue(waitMillis >= 0)
        assertTrue(waitMillis <= 31 * 24 * 60 * 60 * 1000L)
    }

    @Test
    fun testNextExecutionInstantWeekly() {
        // Given: weekly Monday 09:00 and a reference instant of some Monday 10:00
        // (Mon 2026-04-13 10:00 UTC = 1776060000000 ms)
        val mondayAt10 = 1776060000000L
        val next = cronLogic.nextExecutionInstant("0 0 9 * * MON", mondayAt10)
        // Then: should be strictly in the future and ≤ 8 days out
        assertTrue(next != null, "weekly expression should return a next instant")
        assertTrue(next > mondayAt10, "next must be strictly after from")
        assertTrue(next - mondayAt10 <= 8L * 24 * 60 * 60 * 1000, "next should be within a week")
    }

    @Test
    fun testNextExecutionInstantInvalidReturnsNull() {
        val next = cronLogic.nextExecutionInstant("this is not cron", 0L)
        assertEquals(null, next)
    }

    @Test
    fun testNextExecutionInstantUnsatisfiableReturnsNull() {
        // Feb 30 never occurs
        val next = cronLogic.nextExecutionInstant("0 0 0 30 2 *", 0L)
        assertEquals(null, next)
    }

    @Test
    fun testNextExecutionInstantFromPastReturnsFuture() {
        // "every 5 seconds" with fromEpochMillis = 0 should land very close to 0 + up to 5s
        val next = cronLogic.nextExecutionInstant("*/5 * * * * *", 0L)
        assertTrue(next != null, "every-5-seconds should return a next instant")
        assertTrue(next >= 0L, "next must be non-negative")
        assertTrue(next <= 5000L, "next must be within 5 seconds of from")
    }

    private fun createCronNode(expression: String): Node {
        return Node(
            id = "test-cron",
            parent = "root",
            host = "localhost",
            type = KrillApp.Trigger.CronTimer,
            state = NodeState.NONE,
            meta = CronMetaData(
                name = "test cron",
                expression = expression
            )
        )
    }
}

