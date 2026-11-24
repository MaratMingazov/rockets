package com.lunar.rockets.codec

import com.lunar.rockets.domain.RocketEvent
import org.springframework.stereotype.Component
import tools.jackson.core.JacksonException
import tools.jackson.databind.JsonNode
import tools.jackson.databind.ObjectMapper

/**
 * Converts between the wire representation of a rocket message (`messageType` + `message` JSON)
 * and the typed [RocketEvent].
 *
 * Used in both directions and from two places, so the format is defined once:
 * - the API layer parses incoming messages;
 * - the persistence layer stores events as `message_type` + `payload` (JSONB) and parses them back.
 *
 * It knows nothing about HTTP: the caller decides what an [InvalidMessageException] means
 * (a client error for an incoming request, corrupted data when reading from the database).
 */
@Component
class RocketEventCodec(private val objectMapper: ObjectMapper) {

    /**
     * Parses and validates the `message` part of a rocket message.
     *
     * Unknown fields in [payload] are ignored, so new fields added by the sender do not break parsing.
     *
     * @param messageType value of `metadata.messageType`, e.g. `RocketLaunched`
     * @param payload the `message` object
     * @throws InvalidMessageException if [messageType] is unknown, [payload] does not match the expected shape
     *   (missing, `null` or wrongly typed fields, not an object), or a value is invalid
     *   (negative `by` / `launchSpeed`, blank string)
     */
    fun parse(messageType: String, payload: JsonNode): RocketEvent {
        val eventClass = EVENT_CLASSES[messageType]
            ?: throw InvalidMessageException("Unknown messageType '$messageType'")
        val event = try {
            objectMapper.treeToValue(payload, eventClass)
        } catch (e: JacksonException) {
            throw InvalidMessageException("Invalid '$messageType' message: ${e.originalMessage}", e)
        }
        validate(event)
        return event
    }

    /**
     * Returns the `messageType` name of [event]; the inverse of the type lookup in [parse].
     */
    fun typeOf(event: RocketEvent): String = when (event) {
        is RocketEvent.Launched -> ROCKET_LAUNCHED
        is RocketEvent.SpeedIncreased -> ROCKET_SPEED_INCREASED
        is RocketEvent.SpeedDecreased -> ROCKET_SPEED_DECREASED
        is RocketEvent.Exploded -> ROCKET_EXPLODED
        is RocketEvent.MissionChanged -> ROCKET_MISSION_CHANGED
    }

    /**
     * Serialises [event] to the `message` JSON object, so that `parse(typeOf(e), toJson(e)) == e`.
     */
    fun toJson(event: RocketEvent): JsonNode = objectMapper.valueToTree(event)

    private fun validate(event: RocketEvent) {
        when (event) {
            is RocketEvent.Launched -> {
                requireNotBlank("type", event.type)
                requireNotBlank("mission", event.mission)
                requireNotNegative("launchSpeed", event.launchSpeed)
            }
            is RocketEvent.SpeedIncreased -> requireNotNegative("by", event.by)
            is RocketEvent.SpeedDecreased -> requireNotNegative("by", event.by)
            is RocketEvent.Exploded -> requireNotBlank("reason", event.reason)
            is RocketEvent.MissionChanged -> requireNotBlank("newMission", event.newMission)
        }
    }

    private fun requireNotBlank(field: String, value: String) {
        if (value.isBlank()) throw InvalidMessageException("'$field' must not be blank")
    }

    private fun requireNotNegative(field: String, value: Long) {
        if (value < 0) throw InvalidMessageException("'$field' must not be negative, was $value")
    }

    companion object {
        const val ROCKET_LAUNCHED = "RocketLaunched"
        const val ROCKET_SPEED_INCREASED = "RocketSpeedIncreased"
        const val ROCKET_SPEED_DECREASED = "RocketSpeedDecreased"
        const val ROCKET_EXPLODED = "RocketExploded"
        const val ROCKET_MISSION_CHANGED = "RocketMissionChanged"

        private val EVENT_CLASSES: Map<String, Class<out RocketEvent>> = mapOf(
            ROCKET_LAUNCHED to RocketEvent.Launched::class.java,
            ROCKET_SPEED_INCREASED to RocketEvent.SpeedIncreased::class.java,
            ROCKET_SPEED_DECREASED to RocketEvent.SpeedDecreased::class.java,
            ROCKET_EXPLODED to RocketEvent.Exploded::class.java,
            ROCKET_MISSION_CHANGED to RocketEvent.MissionChanged::class.java,
        )
    }
}
