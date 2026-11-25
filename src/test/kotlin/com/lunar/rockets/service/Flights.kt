package com.lunar.rockets.service

import com.lunar.rockets.domain.ReceivedMessage
import com.lunar.rockets.domain.Rocket
import com.lunar.rockets.domain.RocketEvent
import java.time.Instant
import java.util.UUID
import kotlin.random.Random

object Flights {

    private val t0: Instant = Instant.parse("2026-11-24T17:25:31.457818Z")
    private val missions = listOf("ARTEMIS", "APOLLO", "GEMINI", "SKYLAB", "SHUTTLE_MIR")

    fun message(channel: UUID, n: Long, event: RocketEvent) =
        ReceivedMessage(channel, n, t0.plusMillis(n * 500), event)

    /**
     * A realistic flight of [size] messages numbered 1..size: launch, then mostly speed changes,
     * some mission changes and, if [explodeAt] is set, an explosion followed by more (ignored) messages.
     */
    fun flight(channel: UUID, size: Int, random: Random, explodeAt: Int? = null): List<ReceivedMessage> =
        (1..size).map { n ->
            val event = when {
                n == 1 -> RocketEvent.Launched("Falcon-9", 500, missions.random(random))
                n == explodeAt -> RocketEvent.Exploded("PRESSURE_VESSEL_FAILURE")
                random.nextInt(100) < 45 -> RocketEvent.SpeedIncreased(random.nextLong(1, 50) * 100)
                random.nextInt(100) < 90 -> RocketEvent.SpeedDecreased(random.nextLong(1, 50) * 100)
                else -> RocketEvent.MissionChanged(missions.random(random))
            }
            message(channel, n.toLong(), event)
        }

    /** Adds identical copies of about [ratio] of the messages and shuffles everything. */
    fun withDuplicatesShuffled(messages: List<ReceivedMessage>, random: Random, ratio: Double = 0.2) =
        (messages + messages.filter { random.nextDouble() < ratio }).shuffled(random)

    /** The state the service must end up with: each message applied once, in number order. */
    fun expected(channel: UUID, messages: List<ReceivedMessage>): Rocket =
        messages.distinctBy { it.messageNumber }
            .sortedBy { it.messageNumber }
            .fold(Rocket.pending(channel)) { rocket, message -> rocket.applyMessage(message) }
}
