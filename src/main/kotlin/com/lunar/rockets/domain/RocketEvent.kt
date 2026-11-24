package com.lunar.rockets.domain

sealed interface RocketEvent {
    data class Launched(val type: String, val launchSpeed: Long, val mission: String) : RocketEvent
    data class SpeedIncreased(val by: Long) : RocketEvent
    data class SpeedDecreased(val by: Long) : RocketEvent
    data class Exploded(val reason: String) : RocketEvent
    data class MissionChanged(val newMission: String) : RocketEvent
}
