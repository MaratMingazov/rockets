package com.lunar.rockets.api

import com.lunar.rockets.codec.RocketEventCodec
import com.lunar.rockets.service.MessageService
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController

@RestController
class MessagesController(
    private val messageService: MessageService,
    private val codec: RocketEventCodec,
) {

    @PostMapping("/messages")
    @ResponseStatus(HttpStatus.ACCEPTED)
    fun receive(@RequestBody request: MessageRequest) {
        messageService.receive(request.toReceivedMessage(codec))
    }
}
