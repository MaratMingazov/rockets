package com.lunar.rockets.api

import com.lunar.rockets.service.RocketQueryService
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

@RestController
@RequestMapping("/rockets")
class RocketsController(private val rockets: RocketQueryService) {

    @GetMapping("/{channel}")
    fun get(@PathVariable channel: UUID): RocketResponse = rockets.get(channel).toResponse()

    @GetMapping
    fun list(
        @RequestParam(required = false) sort: String?,
        @RequestParam(required = false) order: String?,
        @RequestParam(required = false) status: String?,
        @RequestParam(required = false) minSpeed: Long?,
        @RequestParam(required = false) maxSpeed: Long?,
    ): RocketsResponse {
        val query = rocketQuery(sort, order, status, minSpeed, maxSpeed)
        return RocketsResponse(rockets.list(query).map { it.toResponse() })
    }
}
