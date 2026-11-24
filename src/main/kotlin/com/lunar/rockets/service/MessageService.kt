package com.lunar.rockets.service

import com.lunar.rockets.domain.ReceivedMessage

interface MessageService {
    fun receive(message: ReceivedMessage)
}
