package com.lunar.rockets.codec

import com.lunar.rockets.domain.RocketEvent
import org.junit.jupiter.api.Test
import tools.jackson.module.kotlin.jacksonObjectMapper
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class RocketEventCodecTest {

    private val objectMapper = jacksonObjectMapper()
    private val codec = RocketEventCodec(objectMapper)

    private fun parse(messageType: String, json: String) = codec.parse(messageType, objectMapper.readTree(json))

    private fun assertInvalid(messageType: String, json: String, expectedText: String) {
        val e = assertFailsWith<InvalidMessageException> { parse(messageType, json) }
        assertContains(e.message.orEmpty(), expectedText)
    }

    @Test
    fun `parses RocketLaunched`() {
        assertEquals(
            RocketEvent.Launched(type = "Falcon-9", launchSpeed = 500, mission = "ARTEMIS"),
            parse("RocketLaunched", """{"type":"Falcon-9","launchSpeed":500,"mission":"ARTEMIS"}"""),
        )
    }

    @Test
    fun `parses RocketSpeedIncreased`() {
        assertEquals(RocketEvent.SpeedIncreased(3000), parse("RocketSpeedIncreased", """{"by":3000}"""))
    }

    @Test
    fun `parses RocketSpeedDecreased`() {
        assertEquals(RocketEvent.SpeedDecreased(2500), parse("RocketSpeedDecreased", """{"by":2500}"""))
    }

    @Test
    fun `parses RocketExploded`() {
        assertEquals(
            RocketEvent.Exploded("PRESSURE_VESSEL_FAILURE"),
            parse("RocketExploded", """{"reason":"PRESSURE_VESSEL_FAILURE"}"""),
        )
    }

    @Test
    fun `parses RocketMissionChanged`() {
        assertEquals(
            RocketEvent.MissionChanged("SHUTTLE_MIR"),
            parse("RocketMissionChanged", """{"newMission":"SHUTTLE_MIR"}"""),
        )
    }

    @Test
    fun `ignores unknown fields`() {
        assertEquals(RocketEvent.SpeedIncreased(10), parse("RocketSpeedIncreased", """{"by":10,"extra":true}"""))
    }

    @Test
    fun `rejects unknown messageType`() {
        assertInvalid("RocketTeleported", """{}""", "Unknown messageType 'RocketTeleported'")
    }

    @Test
    fun `rejects missing string field`() {
        assertInvalid("RocketExploded", """{}""", "Invalid 'RocketExploded' message")
    }

    @Test
    fun `rejects missing numeric field`() {
        assertInvalid("RocketSpeedIncreased", """{}""", "Invalid 'RocketSpeedIncreased' message")
    }

    @Test
    fun `rejects null field`() {
        assertInvalid("RocketMissionChanged", """{"newMission":null}""", "Invalid 'RocketMissionChanged' message")
    }

    @Test
    fun `rejects wrong field type`() {
        assertInvalid("RocketSpeedDecreased", """{"by":"fast"}""", "Invalid 'RocketSpeedDecreased' message")
    }

    @Test
    fun `rejects payload that is not an object`() {
        assertInvalid("RocketSpeedIncreased", """[1,2,3]""", "Invalid 'RocketSpeedIncreased' message")
    }

    @Test
    fun `rejects negative speed change`() {
        assertInvalid("RocketSpeedDecreased", """{"by":-5}""", "'by' must not be negative")
    }

    @Test
    fun `rejects negative launch speed`() {
        assertInvalid(
            "RocketLaunched",
            """{"type":"Falcon-9","launchSpeed":-1,"mission":"ARTEMIS"}""",
            "'launchSpeed' must not be negative",
        )
    }

    @Test
    fun `rejects blank strings`() {
        assertInvalid("RocketExploded", """{"reason":"  "}""", "'reason' must not be blank")
    }

    @Test
    fun `round-trips every event type`() {
        val events = listOf(
            RocketEvent.Launched("Falcon-9", 500, "ARTEMIS"),
            RocketEvent.SpeedIncreased(3000),
            RocketEvent.SpeedDecreased(2500),
            RocketEvent.Exploded("PRESSURE_VESSEL_FAILURE"),
            RocketEvent.MissionChanged("SHUTTLE_MIR"),
        )
        events.forEach { event ->
            assertEquals(event, codec.parse(codec.typeOf(event), codec.toJson(event)))
        }
    }
}
