package com.lunar.rockets.service

import com.lunar.rockets.domain.Rocket
import com.lunar.rockets.domain.RocketQuery
import java.util.UUID

interface RocketQueryService {

    /** @throws RocketNotFoundException if the rocket is unknown or not launched yet (`PENDING`) */
    fun get(channel: UUID): Rocket

    fun list(query: RocketQuery): List<Rocket>
}

class RocketNotFoundException(val channel: UUID) : RuntimeException("Rocket $channel not found")
