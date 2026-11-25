package com.lunar.rockets.domain

import com.lunar.rockets.domain.RocketEvent.Exploded
import com.lunar.rockets.domain.RocketEvent.Launched
import com.lunar.rockets.domain.RocketEvent.MissionChanged
import com.lunar.rockets.domain.RocketEvent.SpeedDecreased
import com.lunar.rockets.domain.RocketEvent.SpeedIncreased
import org.junit.jupiter.api.Test
import java.time.Instant
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class RocketTest {

    private val channel = UUID.fromString("193270a9-c9cf-404a-8f83-838e71d9ae67")
    private val t0 = Instant.parse("2022-02-02T18:39:05Z")

    private fun time(n: Long): Instant = t0.plusSeconds(n)

    private fun msg(n: Long, event: RocketEvent) = ReceivedMessage(channel, n, time(n), event)

    private fun pending() = Rocket.pending(channel)

    private fun launched() = pending().applyMessage(msg(1, Launched("Falcon-9", 500, "ARTEMIS")))

    private fun exploded() = launched().applyMessage(msg(2, Exploded("PRESSURE_VESSEL_FAILURE")))

    private fun assertOnlyCursorAdvanced(before: Rocket, after: Rocket, n: Long) {
        assertEquals(before.copy(lastAppliedMessageNumber = n, updatedAt = time(n)), after)
    }

    @Test
    fun `new rocket is pending with cursor 0`() {
        val rocket = pending()
        assertEquals(RocketStatus.PENDING, rocket.status)
        assertEquals(0, rocket.lastAppliedMessageNumber)
    }

    @Test
    fun `pending + Launched launches the rocket`() {
        assertEquals(
            Rocket(
                channel = channel,
                status = RocketStatus.LAUNCHED,
                type = "Falcon-9",
                mission = "ARTEMIS",
                speed = 500,
                explosionReason = null,
                launchedAt = time(1),
                updatedAt = time(1),
                lastAppliedMessageNumber = 1,
            ),
            launched(),
        )
    }

    @Test
    fun `pending ignores everything except Launched`() {
        listOf(SpeedIncreased(100), SpeedDecreased(100), MissionChanged("APOLLO"), Exploded("BOOM")).forEach { event ->
            val before = pending()
            assertOnlyCursorAdvanced(before, before.applyMessage(msg(1, event)), 1)
        }
    }

    @Test
    fun `launched + SpeedIncreased adds to speed`() {
        assertEquals(3500, launched().applyMessage(msg(2, SpeedIncreased(3000))).speed)
    }

    @Test
    fun `launched + SpeedDecreased subtracts from speed`() {
        assertEquals(300, launched().applyMessage(msg(2, SpeedDecreased(200))).speed)
    }

    @Test
    fun `speed is not clamped at zero`() {
        assertEquals(-100, launched().applyMessage(msg(2, SpeedDecreased(600))).speed)
    }

    @Test
    fun `launched + MissionChanged changes mission`() {
        assertEquals("SHUTTLE_MIR", launched().applyMessage(msg(2, MissionChanged("SHUTTLE_MIR"))).mission)
    }

    @Test
    fun `launched + Exploded explodes the rocket and keeps the rest`() {
        val before = launched()
        assertEquals(
            before.copy(
                status = RocketStatus.EXPLODED,
                explosionReason = "PRESSURE_VESSEL_FAILURE",
                lastAppliedMessageNumber = 2,
                updatedAt = time(2),
            ),
            exploded(),
        )
    }

    @Test
    fun `second Launched is ignored`() {
        val before = launched()
        assertOnlyCursorAdvanced(before, before.applyMessage(msg(2, Launched("Atlas", 9999, "GEMINI"))), 2)
    }

    @Test
    fun `exploded ignores every event`() {
        listOf(
            Launched("Atlas", 9999, "GEMINI"),
            SpeedIncreased(100),
            SpeedDecreased(100),
            MissionChanged("APOLLO"),
            Exploded("AGAIN"),
        ).forEach { event ->
            val before = exploded()
            assertOnlyCursorAdvanced(before, before.applyMessage(msg(3, event)), 3)
        }
    }

    @Test
    fun `rejects a message that is not the next one`() {
        val rocket = launched()
        assertFailsWith<IllegalArgumentException> { rocket.applyMessage(msg(3, SpeedIncreased(1))) }
        assertFailsWith<IllegalArgumentException> { rocket.applyMessage(msg(1, SpeedIncreased(1))) }
    }

    @Test
    fun `rejects a message for another channel`() {
        val other = ReceivedMessage(UUID.randomUUID(), 1, t0, Launched("Falcon-9", 500, "ARTEMIS"))
        assertFailsWith<IllegalArgumentException> { pending().applyMessage(other) }
    }

    @Test
    fun `applies a full flight in order`() {
        val messages = listOf(
            msg(1, Launched("Falcon-9", 500, "ARTEMIS")),
            msg(2, SpeedIncreased(3000)),
            msg(3, SpeedDecreased(1000)),
            msg(4, MissionChanged("SHUTTLE_MIR")),
            msg(5, Exploded("PRESSURE_VESSEL_FAILURE")),
            msg(6, SpeedIncreased(5000)),
        )

        val rocket = messages.fold(pending()) { state, message -> state.applyMessage(message) }

        assertEquals(
            Rocket(
                channel = channel,
                status = RocketStatus.EXPLODED,
                type = "Falcon-9",
                mission = "SHUTTLE_MIR",
                speed = 2500,
                explosionReason = "PRESSURE_VESSEL_FAILURE",
                launchedAt = time(1),
                updatedAt = time(6),
                lastAppliedMessageNumber = 6,
            ),
            rocket,
        )
    }
}
