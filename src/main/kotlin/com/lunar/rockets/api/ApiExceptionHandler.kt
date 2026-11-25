package com.lunar.rockets.api

import com.lunar.rockets.codec.InvalidMessageException
import com.lunar.rockets.service.RocketNotFoundException
import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.http.ProblemDetail
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice

@RestControllerAdvice
class ApiExceptionHandler {

    private val log = LoggerFactory.getLogger(javaClass)

    @ExceptionHandler(InvalidMessageException::class)
    fun handleInvalidMessage(e: InvalidMessageException): ProblemDetail {
        log.warn("Rejected invalid message: {}", e.message)
        return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, e.message ?: "Invalid message")
    }

    @ExceptionHandler(InvalidRequestParameterException::class)
    fun handleInvalidParameter(e: InvalidRequestParameterException): ProblemDetail =
        ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, e.message ?: "Invalid request parameter")

    @ExceptionHandler(RocketNotFoundException::class)
    fun handleNotFound(e: RocketNotFoundException): ProblemDetail =
        ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, e.message ?: "Rocket not found")
}
