package com.lunar.rockets.api

import com.lunar.rockets.domain.Rocket
import com.lunar.rockets.domain.RocketStatus
import java.time.Instant
import java.util.UUID

data class RocketResponse(
    val channel: UUID,
    val type: String,
    val mission: String,
    val speed: Long,
    val status: String,
    val explosionReason: String?,
    val launchedAt: Instant,
    val updatedAt: Instant,
)

data class RocketsResponse(val rockets: List<RocketResponse>)

fun Rocket.toResponse(): RocketResponse {
    check(status != RocketStatus.PENDING) { "PENDING rocket $channel must not be exposed" }
    return RocketResponse(
        channel = channel,
        type = checkNotNull(type),
        mission = checkNotNull(mission),
        speed = checkNotNull(speed),
        status = status.name,
        explosionReason = explosionReason,
        launchedAt = checkNotNull(launchedAt),
        updatedAt = checkNotNull(updatedAt),
    )
}
