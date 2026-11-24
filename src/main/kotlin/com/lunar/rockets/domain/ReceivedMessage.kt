package com.lunar.rockets.domain

import java.time.Instant
import java.util.UUID

data class ReceivedMessage(
    val channel: UUID,
    val messageNumber: Long,
    val messageTime: Instant,
    val event: RocketEvent,
)
