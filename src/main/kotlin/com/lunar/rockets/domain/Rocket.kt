package com.lunar.rockets.domain

import java.time.Instant
import java.util.UUID

enum class RocketStatus { PENDING, LAUNCHED, EXPLODED }

data class Rocket(
    val channel: UUID,
    val status: RocketStatus,
    val type: String?,
    val mission: String?,
    val speed: Long?,
    val explosionReason: String?,
    val launchedAt: Instant?,
    val updatedAt: Instant?,
    val lastAppliedMessageNumber: Long,
) {

    /**
     * Returns the state after applying [message], which must be the next one in order
     * (`lastAppliedMessageNumber + 1`). The cursor and [updatedAt] always advance, even if the event
     * changes nothing else. See "Rocket state" in README for the transition diagram.
     */
    fun applyMessage(message: ReceivedMessage): Rocket {
        require(message.channel == channel) {
            "Message for channel ${message.channel} applied to rocket $channel"
        }
        require(message.messageNumber == lastAppliedMessageNumber + 1) {
            "Rocket $channel expects message #${lastAppliedMessageNumber + 1}, got #${message.messageNumber}"
        }
        val advanced = copy(lastAppliedMessageNumber = message.messageNumber, updatedAt = message.messageTime)
        val event = message.event
        return when (status) {
            RocketStatus.PENDING -> when (event) {
                is RocketEvent.Launched -> advanced.copy(
                    status = RocketStatus.LAUNCHED,
                    type = event.type,
                    mission = event.mission,
                    speed = event.launchSpeed,
                    launchedAt = message.messageTime,
                )
                else -> advanced
            }
            RocketStatus.LAUNCHED -> when (event) {
                is RocketEvent.Launched -> advanced
                is RocketEvent.SpeedIncreased -> advanced.copy(speed = currentSpeed() + event.by)
                is RocketEvent.SpeedDecreased -> advanced.copy(speed = currentSpeed() - event.by)
                is RocketEvent.MissionChanged -> advanced.copy(mission = event.newMission)
                is RocketEvent.Exploded -> advanced.copy(
                    status = RocketStatus.EXPLODED,
                    explosionReason = event.reason,
                )
            }
            RocketStatus.EXPLODED -> advanced
        }
    }

    private fun currentSpeed(): Long = checkNotNull(speed) { "Launched rocket $channel has no speed" }

    companion object {
        fun pending(channel: UUID) = Rocket(
            channel = channel,
            status = RocketStatus.PENDING,
            type = null,
            mission = null,
            speed = null,
            explosionReason = null,
            launchedAt = null,
            updatedAt = null,
            lastAppliedMessageNumber = 0,
        )
    }
}
