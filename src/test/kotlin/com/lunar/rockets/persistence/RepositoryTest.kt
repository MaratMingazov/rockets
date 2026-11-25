package com.lunar.rockets.persistence

import com.lunar.rockets.TestcontainersConfiguration
import com.lunar.rockets.codec.RocketEventCodec
import com.lunar.rockets.domain.ReceivedMessage
import com.lunar.rockets.domain.Rocket
import com.lunar.rockets.domain.RocketEvent
import com.lunar.rockets.domain.RocketStatus
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.autoconfigure.ImportAutoConfiguration
import org.springframework.boot.jackson.autoconfigure.JacksonAutoConfiguration
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase
import org.springframework.boot.jdbc.test.autoconfigure.JdbcTest
import org.springframework.context.annotation.Import
import org.springframework.transaction.IllegalTransactionStateException
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@JdbcTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ImportAutoConfiguration(JacksonAutoConfiguration::class)
@Import(
    TestcontainersConfiguration::class,
    RocketEventCodec::class,
    RocketRepository::class,
    RocketEventRepository::class,
)
class RepositoryTest {

    @Autowired
    lateinit var rockets: RocketRepository

    @Autowired
    lateinit var events: RocketEventRepository

    private val channel = UUID.randomUUID()
    private val time = Instant.parse("2026-11-24T17:25:31.457818Z")

    private fun msg(n: Long, event: RocketEvent, channel: UUID = this.channel) =
        ReceivedMessage(channel, n, time.plusSeconds(n), event)

    @Test
    fun `lockOrCreate creates a pending rocket`() {
        assertEquals(Rocket.pending(channel), rockets.lockOrCreate(channel))
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    fun `write methods fail without an active transaction`() {
        assertFailsWith<IllegalTransactionStateException> { rockets.lockOrCreate(channel) }
        assertFailsWith<IllegalTransactionStateException> { rockets.update(Rocket.pending(channel)) }
        assertFailsWith<IllegalTransactionStateException> {
            events.insertIfAbsent(msg(1, RocketEvent.SpeedIncreased(1)))
        }
    }

    @Test
    fun `findByChannel returns null for an unknown rocket and the stored one otherwise`() {
        assertEquals(null, rockets.findByChannel(channel))

        val pending = rockets.lockOrCreate(channel)
        assertEquals(pending, rockets.findByChannel(channel))

        val launched = pending.applyMessage(msg(1, RocketEvent.Launched("Falcon-9", 500, "ARTEMIS")))
        rockets.update(launched)
        assertEquals(launched, rockets.findByChannel(channel))
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    fun `findByChannel works without a transaction`() {
        assertEquals(null, rockets.findByChannel(channel))
    }

    @Test
    fun `lockOrCreate returns the existing rocket instead of resetting it`() {
        val launched = rockets.lockOrCreate(channel).applyMessage(msg(1, RocketEvent.Launched("Falcon-9", 500, "ARTEMIS")))
        rockets.update(launched)

        assertEquals(launched, rockets.lockOrCreate(channel))
    }

    @Test
    fun `update round-trips every field including microseconds`() {
        val rocket = Rocket(
            channel = channel,
            status = RocketStatus.EXPLODED,
            type = "Falcon-9",
            mission = "ARTEMIS",
            speed = -42,
            explosionReason = "PRESSURE_VESSEL_FAILURE",
            launchedAt = time,
            updatedAt = time.plusSeconds(10),
            lastAppliedMessageNumber = 7,
        )
        rockets.lockOrCreate(channel)

        rockets.update(rocket)

        assertEquals(rocket, rockets.lockOrCreate(channel))
    }

    @Test
    fun `update keeps null fields null`() {
        val pending = rockets.lockOrCreate(channel)
        rockets.update(pending.copy(lastAppliedMessageNumber = 1))

        assertEquals(pending.copy(lastAppliedMessageNumber = 1), rockets.lockOrCreate(channel))
    }

    @Test
    fun `insertIfAbsent stores a message once`() {
        rockets.lockOrCreate(channel)
        val message = msg(1, RocketEvent.SpeedIncreased(3000))

        assertTrue(events.insertIfAbsent(message))
        assertFalse(events.insertIfAbsent(message))
        assertFalse(events.insertIfAbsent(message.copy(event = RocketEvent.SpeedDecreased(1))))

        assertEquals(listOf(message), events.findAfter(channel, 0))
    }

    @Test
    fun `findAfter returns parsed messages after the number, in order, for that channel only`() {
        val other = UUID.randomUUID()
        rockets.lockOrCreate(channel)
        rockets.lockOrCreate(other)
        val all = listOf(
            msg(1, RocketEvent.Launched("Falcon-9", 500, "ARTEMIS")),
            msg(2, RocketEvent.SpeedIncreased(3000)),
            msg(3, RocketEvent.SpeedDecreased(2500)),
            msg(5, RocketEvent.MissionChanged("SHUTTLE_MIR")),
            msg(6, RocketEvent.Exploded("PRESSURE_VESSEL_FAILURE")),
        )
        all.reversed().forEach { events.insertIfAbsent(it) }
        events.insertIfAbsent(msg(3, RocketEvent.SpeedIncreased(1), channel = other))

        assertEquals(all.drop(1), events.findAfter(channel, 1))
        assertEquals(emptyList(), events.findAfter(channel, 6))
    }
}
