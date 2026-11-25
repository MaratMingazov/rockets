package com.lunar.rockets.service

import com.lunar.rockets.domain.ReceivedMessage
import com.lunar.rockets.persistence.RocketEventRepository
import com.lunar.rockets.persistence.RocketRepository
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/**
 * Stores each message and applies messages to the rocket state strictly in `messageNumber` order,
 * ignoring duplicates. One transaction per message, serialised per rocket by a row lock.
 * See "Version 1" and "Why the lock" in README.
 */
@Service
class RocketMessageService(
    private val rockets: RocketRepository,
    private val events: RocketEventRepository,
) : MessageService {

    private val log = LoggerFactory.getLogger(javaClass)

    @Transactional(timeout = TRANSACTION_TIMEOUT_SECONDS)
    override fun receive(message: ReceivedMessage) {
        val locked = rockets.lockOrCreate(message.channel)
        val next = locked.lastAppliedMessageNumber + 1

        // Already applied: the cursor covers it, so this is a duplicate. Nothing to write.
        if (message.messageNumber < next) {
            log.debug("Duplicate of applied message {} #{}", message.channel, message.messageNumber)
            return
        }

        // Already buffered: a stored, unapplied message is never the next one (it would have been applied
        // by the transaction that stored it or that filled the gap before it), so there is nothing to apply.
        if (!events.insertIfAbsent(message)) {
            log.debug("Duplicate of buffered message {} #{}", message.channel, message.messageNumber)
            return
        }

        // Arrived early: a gap before it. It waits in rocket_events and will be applied by the request
        // that fills the gap. The state is unchanged, so no UPDATE.
        if (message.messageNumber > next) {
            log.debug("Buffered message {} #{}, waiting for #{}", message.channel, message.messageNumber, next)
            return
        }

        // It is the next one: apply the typed object from the request, no read-back from the database.
        var rocket = locked.applyMessage(message)

        // Then drain messages that arrived early and were waiting for this one, until the next gap.
        for (buffered in events.findAfter(rocket.channel, rocket.lastAppliedMessageNumber)) {
            if (buffered.messageNumber != rocket.lastAppliedMessageNumber + 1) break
            rocket = rocket.applyMessage(buffered)
        }

        rockets.update(rocket)
    }

    private companion object {
        const val TRANSACTION_TIMEOUT_SECONDS = 5
    }
}
