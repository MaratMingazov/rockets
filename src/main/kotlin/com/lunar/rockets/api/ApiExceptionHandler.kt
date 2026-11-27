package com.lunar.rockets.api

import com.lunar.rockets.codec.InvalidMessageException
import com.lunar.rockets.service.RocketNotFoundException
import jakarta.servlet.http.HttpServletRequest
import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.http.ProblemDetail
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler

/**
 * Maps exceptions to RFC 9457 Problem Details.
 *
 * Extends [ResponseEntityExceptionHandler], so Spring MVC's own exceptions (malformed JSON, type mismatch,
 * unknown path, wrong method, ...) keep their proper `4xx` statuses; [handleUnexpected] only catches
 * whatever is left and turns it into a `500` without exposing internal details.
 */
@RestControllerAdvice
class ApiExceptionHandler : ResponseEntityExceptionHandler() {

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

    @ExceptionHandler(Exception::class)
    fun handleUnexpected(e: Exception, request: HttpServletRequest): ProblemDetail {
        log.error("Unexpected error on {} {}", request.method, request.requestURI, e)
        return ProblemDetail.forStatusAndDetail(HttpStatus.INTERNAL_SERVER_ERROR, "Internal error")
    }
}
