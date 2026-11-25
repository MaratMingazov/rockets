package com.lunar.rockets.persistence

import com.lunar.rockets.codec.RocketEventCodec
import com.lunar.rockets.domain.ReceivedMessage
import org.springframework.jdbc.core.RowMapper
import org.springframework.jdbc.core.simple.JdbcClient
import org.springframework.stereotype.Repository
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional
import tools.jackson.databind.ObjectMapper
import java.util.UUID

@Repository
class RocketEventRepository(
    private val jdbc: JdbcClient,
    private val codec: RocketEventCodec,
    private val objectMapper: ObjectMapper,
) {

    /**
     * Stores [message] unless a message with the same `(channel, messageNumber)` is already stored.
     *
     * Must run under the rocket's lock (see "Why the lock" in README), hence [Propagation.MANDATORY].
     *
     * @return `true` if inserted, `false` if it was a duplicate
     */
    @Transactional(propagation = Propagation.MANDATORY)
    fun insertIfAbsent(message: ReceivedMessage): Boolean =
        jdbc.sql(
            """
            INSERT INTO rocket_events (channel, message_number, message_type, message_time, payload)
            VALUES (:channel, :messageNumber, :messageType, :messageTime, CAST(:payload AS jsonb))
            ON CONFLICT (channel, message_number) DO NOTHING
            """,
        )
            .param("channel", message.channel)
            .param("messageNumber", message.messageNumber)
            .param("messageType", codec.typeOf(message.event))
            .param("messageTime", message.messageTime.toUtcOffsetDateTime())
            .param("payload", objectMapper.writeValueAsString(codec.toJson(message.event)))
            .update() == 1

    /**
     * Returns all stored messages of [channel] with a number greater than [messageNumber], ordered by number.
     * Gaps are not handled here — the caller decides where to stop.
     */
    fun findAfter(channel: UUID, messageNumber: Long): List<ReceivedMessage> =
        jdbc.sql(
            """
            SELECT channel, message_number, message_type, message_time, payload
            FROM rocket_events
            WHERE channel = :channel AND message_number > :messageNumber
            ORDER BY message_number
            """,
        )
            .param("channel", channel)
            .param("messageNumber", messageNumber)
            .query(messageMapper)
            .list()

    private val messageMapper = RowMapper { rs, _ ->
        ReceivedMessage(
            channel = rs.getObject("channel", UUID::class.java),
            messageNumber = rs.getLong("message_number"),
            messageTime = checkNotNull(rs.getInstant("message_time")),
            event = codec.parse(rs.getString("message_type"), objectMapper.readTree(rs.getString("payload"))),
        )
    }
}
