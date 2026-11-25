package com.lunar.rockets.service

import com.lunar.rockets.TestcontainersConfiguration
import com.lunar.rockets.domain.ReceivedMessage
import com.lunar.rockets.persistence.RocketEventRepository
import com.lunar.rockets.persistence.RocketRepository
import org.junit.jupiter.api.RepeatedTest
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Import
import java.util.UUID
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlin.random.Random
import kotlin.test.assertEquals

/**
 * Many threads deliver shuffled messages with duplicates at the same time, like the test program does
 * with `--concurrency-level`. Whatever the interleaving, the result must equal in-order delivery.
 * This is the test that verifies the per-rocket lock (see "Why the lock" in README).
 */
@SpringBootTest
@Import(TestcontainersConfiguration::class)
class RocketMessageServiceConcurrencyTest {

    @Autowired
    lateinit var service: MessageService

    @Autowired
    lateinit var rockets: RocketRepository

    @Autowired
    lateinit var events: RocketEventRepository

    private fun sendConcurrently(messages: List<ReceivedMessage>, threads: Int = 8) {
        val queue = ConcurrentLinkedQueue(messages)
        val start = CountDownLatch(1)
        val pool = Executors.newFixedThreadPool(threads)
        try {
            val workers = (1..threads).map {
                pool.submit {
                    start.await()
                    while (true) {
                        service.receive(queue.poll() ?: break)
                    }
                }
            }
            start.countDown()
            workers.forEach { it.get(60, TimeUnit.SECONDS) }
        } finally {
            pool.shutdownNow()
        }
    }

    private fun assertDeliveredInOrder(channel: UUID, messages: List<ReceivedMessage>) {
        assertEquals(Flights.expected(channel, messages), rockets.findByChannel(channel))
        assertEquals(messages.sortedBy { it.messageNumber }, events.findAfter(channel, 0))
    }

    @RepeatedTest(3)
    fun `one rocket, concurrent shuffled messages with duplicates`() {
        val random = Random(7)
        val channel = UUID.randomUUID()
        val messages = Flights.flight(channel, 200, random, explodeAt = 180)

        sendConcurrently(Flights.withDuplicatesShuffled(messages, random))

        assertDeliveredInOrder(channel, messages)
    }

    @Test
    fun `several rockets at once do not interfere`() {
        val random = Random(11)
        val flights = (1..5).associate { index ->
            val channel = UUID.randomUUID()
            channel to Flights.flight(channel, 100, random, explodeAt = if (index % 2 == 0) 90 else null)
        }

        sendConcurrently(Flights.withDuplicatesShuffled(flights.values.flatten(), random))

        flights.forEach { (channel, messages) -> assertDeliveredInOrder(channel, messages) }
    }
}
