package com.lunar.rockets.api

import com.lunar.rockets.domain.RocketQuery
import com.lunar.rockets.domain.RocketSortField
import com.lunar.rockets.domain.RocketStatus
import com.lunar.rockets.domain.SortOrder

class InvalidRequestParameterException(message: String) : RuntimeException(message)

private val SORT_FIELDS = mapOf(
    "speed" to RocketSortField.SPEED,
    "type" to RocketSortField.TYPE,
    "mission" to RocketSortField.MISSION,
    "status" to RocketSortField.STATUS,
    "launchedAt" to RocketSortField.LAUNCHED_AT,
)

private val ORDERS = mapOf("asc" to SortOrder.ASC, "desc" to SortOrder.DESC)

private val STATUSES = mapOf(
    "LAUNCHED" to RocketStatus.LAUNCHED,
    "EXPLODED" to RocketStatus.EXPLODED,
)

fun rocketQuery(sort: String?, order: String?, status: String?, minSpeed: Long?, maxSpeed: Long?): RocketQuery {
    val defaults = RocketQuery()
    return RocketQuery(
        status = status?.let { parse("status", it, STATUSES) },
        minSpeed = minSpeed,
        maxSpeed = maxSpeed,
        sort = sort?.let { parse("sort", it, SORT_FIELDS) } ?: defaults.sort,
        order = order?.let { parse("order", it, ORDERS) } ?: defaults.order,
    )
}

private fun <T> parse(name: String, value: String, allowed: Map<String, T>): T =
    allowed[value] ?: throw InvalidRequestParameterException(
        "Unknown $name '$value', allowed: ${allowed.keys.joinToString(", ")}",
    )
