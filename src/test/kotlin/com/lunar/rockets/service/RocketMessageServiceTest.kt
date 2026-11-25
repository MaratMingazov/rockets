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
import org.springframework.transaction.support.TransactionTemplate
import java.time.Instant
import java.util.UUID
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

    @Autowired
    lateinit var tx: TransactionTemplate

    private val channel = UUID.randomUUID()
    private val t0 = Instant.parse("2026-11-24T17:25:31.457818Z")

    private fun msg(n: Long, event: RocketEvent) = ReceivedMessage(channel, n, t0.plusSeconds(n), event)

    private fun rocket(): Rocket = tx.execute { rockets.lockOrCreate(channel) }

    @Test
    fun `stores and applies messages that arrive in order`() {
        val messages = listOf(
            msg(1, RocketEvent.Launched("Falcon-9", 500, "ARTEMIS")),
            msg(2, RocketEvent.SpeedIncreased(3000)),
        )

        messages.forEach(service::receive)

        val rocket = rocket()
        assertEquals(RocketStatus.LAUNCHED, rocket.status)
        assertEquals(3500, rocket.speed)
        assertEquals(2, rocket.lastAppliedMessageNumber)
        assertEquals(messages, events.findAfter(channel, 0))
    }
}
