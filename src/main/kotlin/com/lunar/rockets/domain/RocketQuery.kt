package com.lunar.rockets.domain

enum class RocketSortField { SPEED, TYPE, MISSION, STATUS, LAUNCHED_AT }

enum class SortOrder { ASC, DESC }

data class RocketQuery(
    val status: RocketStatus? = null,
    val minSpeed: Long? = null,
    val maxSpeed: Long? = null,
    val sort: RocketSortField = RocketSortField.LAUNCHED_AT,
    val order: SortOrder = SortOrder.DESC,
) {
    init {
        require(status != RocketStatus.PENDING) { "PENDING rockets are not queryable" }
    }
}
