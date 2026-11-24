package com.lunar.rockets.service

import com.lunar.rockets.domain.ReceivedMessage
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service


@Service
class LoggingMessageService : MessageService {

    private val log = LoggerFactory.getLogger(javaClass)

    override fun receive(message: ReceivedMessage) {
        log.info("Received {}", message)
    }
}
