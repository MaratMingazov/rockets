package com.lunar.rockets.api

import com.lunar.rockets.codec.InvalidMessageException
import com.lunar.rockets.codec.RocketEventCodec
import com.lunar.rockets.domain.ReceivedMessage

fun MessageRequest.toReceivedMessage(codec: RocketEventCodec): ReceivedMessage {
    if (metadata.messageNumber < 1) {
        throw InvalidMessageException("'messageNumber' must be at least 1, was ${metadata.messageNumber}")
    }
    return ReceivedMessage(
        channel = metadata.channel,
        messageNumber = metadata.messageNumber,
        messageTime = metadata.messageTime,
        event = codec.parse(metadata.messageType, message),
    )
}
