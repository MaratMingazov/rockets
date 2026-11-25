package com.lunar.rockets.service

import com.lunar.rockets.domain.Rocket
import com.lunar.rockets.domain.RocketQuery
import com.lunar.rockets.domain.RocketStatus
import com.lunar.rockets.persistence.RocketRepository
import org.springframework.stereotype.Service
import java.util.UUID

@Service
class DefaultRocketQueryService(private val rockets: RocketRepository) : RocketQueryService {

    override fun get(channel: UUID): Rocket =
        rockets.findByChannel(channel)?.takeIf { it.status != RocketStatus.PENDING }
            ?: throw RocketNotFoundException(channel)

    override fun list(query: RocketQuery): List<Rocket> = rockets.findAllVisible(query)
}
