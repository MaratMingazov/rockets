package com.lunar.rockets.service

import com.lunar.rockets.TestcontainersConfiguration
import com.lunar.rockets.domain.ReceivedMessage
import com.lunar.rockets.domain.Rocket
import com.lunar.rockets.domain.RocketEvent
import com.lunar.rockets.domain.RocketStatus
import com.lunar.rockets.persistence.RocketEventRepository
import com.lunar.rockets.persistence.RocketRepository
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Import
import java.util.UUID
import kotlin.random.Random
import kotlin.test.assertEquals

@SpringBootTest
@Import(TestcontainersConfiguration::class)
class RocketMessageServiceTest {

    @Autowired
    lateinit var service: MessageService

    @Autowired
    lateinit var rockets: RocketRepository

    @Autowired
    lateinit var events: RocketEventRepository

    private val channel = UUID.randomUUID()

    private fun msg(n: Long, event: RocketEvent) = Flights.message(channel, n, event)

    private val launch = msg(1, RocketEvent.Launched("Falcon-9", 500, "ARTEMIS"))

    private fun rocket(): Rocket = checkNotNull(rockets.findByChannel(channel)) { "Rocket $channel not found" }

    private fun send(vararg messages: ReceivedMessage) = messages.forEach(service::receive)

    private fun assertStoredEvents(expected: List<ReceivedMessage>) =
        assertEquals(expected.distinctBy { it.messageNumber }.sortedBy { it.messageNumber }, events.findAfter(channel, 0))

    @Test
    fun `stores and applies messages that arrive in order`() {
        val messages = listOf(launch, msg(2, RocketEvent.SpeedIncreased(3000)))

        send(*messages.toTypedArray())

        assertEquals(Flights.expected(channel, messages), rocket())
        assertEquals(3500, rocket().speed)
        assertStoredEvents(messages)
    }

    @Test
    fun `buffers messages arriving in reverse order and applies all when #1 arrives`() {
        val messages = Flights.flight(channel, 5, Random(1))

        messages.drop(1).reversed().forEach { message ->
            service.receive(message)
            assertEquals(Rocket.pending(channel), rocket())
        }
        service.receive(messages.first())

        assertEquals(Flights.expected(channel, messages), rocket())
        assertEquals(5, rocket().lastAppliedMessageNumber)
        assertStoredEvents(messages)
    }

    @Test
    fun `waits at a gap and drains the buffer when the gap is filled`() {
        val messages = Flights.flight(channel, 5, Random(2))
        val (missing, others) = messages.partition { it.messageNumber == 3L }

        send(*others.toTypedArray())
        assertEquals(2, rocket().lastAppliedMessageNumber)
        assertEquals(Flights.expected(channel, messages.take(2)), rocket())

        send(*missing.toTypedArray())
        assertEquals(Flights.expected(channel, messages), rocket())
        assertEquals(5, rocket().lastAppliedMessageNumber)
    }

    @Test
    fun `ignores a duplicate of an applied message`() {
        val increase = msg(2, RocketEvent.SpeedIncreased(100))

        send(launch, increase, increase)

        assertEquals(600, rocket().speed)
        assertEquals(2, rocket().lastAppliedMessageNumber)
        assertStoredEvents(listOf(launch, increase))
    }

    @Test
    fun `ignores a duplicate of a buffered message`() {
        val second = msg(2, RocketEvent.SpeedIncreased(10))
        val third = msg(3, RocketEvent.SpeedIncreased(100))

        send(launch, third, third, second)

        assertEquals(610, rocket().speed)
        assertEquals(3, rocket().lastAppliedMessageNumber)
        assertStoredEvents(listOf(launch, second, third))
    }

    @Test
    fun `keeps the first message when a duplicate number has different content`() {
        val first = msg(2, RocketEvent.SpeedIncreased(100))
        val conflicting = msg(2, RocketEvent.SpeedIncreased(999))

        send(launch, first, conflicting)

        assertEquals(600, rocket().speed)
        assertStoredEvents(listOf(launch, first))
    }

    @Test
    fun `keeps the first buffered message when a duplicate number has different content`() {
        val first = msg(3, RocketEvent.SpeedIncreased(100))
        val conflicting = msg(3, RocketEvent.SpeedIncreased(999))

        send(launch, first, conflicting, msg(2, RocketEvent.SpeedIncreased(10)))

        assertEquals(610, rocket().speed)
    }

    @Test
    fun `messages after an explosion only advance the cursor`() {
        send(
            launch,
            msg(2, RocketEvent.Exploded("PRESSURE_VESSEL_FAILURE")),
            msg(3, RocketEvent.SpeedIncreased(5000)),
            msg(4, RocketEvent.MissionChanged("APOLLO")),
        )

        val rocket = rocket()
        assertEquals(RocketStatus.EXPLODED, rocket.status)
        assertEquals("PRESSURE_VESSEL_FAILURE", rocket.explosionReason)
        assertEquals(500, rocket.speed)
        assertEquals("ARTEMIS", rocket.mission)
        assertEquals(4, rocket.lastAppliedMessageNumber)
    }

    @Test
    fun `shuffled messages with duplicates end in the same state as in-order delivery`() {
        val random = Random(42)
        val messages = Flights.flight(channel, 50, random, explodeAt = 40)

        Flights.withDuplicatesShuffled(messages, random).forEach(service::receive)

        assertEquals(Flights.expected(channel, messages), rocket())
        assertStoredEvents(messages)
    }
}
