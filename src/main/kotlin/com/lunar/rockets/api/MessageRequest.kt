package com.lunar.rockets.api

import tools.jackson.databind.JsonNode
import java.time.Instant
import java.util.UUID

data class MessageRequest(
    val metadata: MessageMetadata,
    val message: JsonNode,
)

data class MessageMetadata(
    val channel: UUID,
    val messageNumber: Long,
    val messageTime: Instant,
    val messageType: String,
)
