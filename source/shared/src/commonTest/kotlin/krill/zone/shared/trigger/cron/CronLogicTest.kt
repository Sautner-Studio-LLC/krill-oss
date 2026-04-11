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

