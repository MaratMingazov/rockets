package com.lunar.rockets.persistence

import com.lunar.rockets.TestcontainersConfiguration
import com.lunar.rockets.codec.RocketEventCodec
import com.lunar.rockets.domain.Rocket
import com.lunar.rockets.domain.RocketQuery
import com.lunar.rockets.domain.RocketSortField
import com.lunar.rockets.domain.RocketStatus
import com.lunar.rockets.domain.SortOrder
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.autoconfigure.ImportAutoConfiguration
import org.springframework.boot.jackson.autoconfigure.JacksonAutoConfiguration
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase
import org.springframework.boot.jdbc.test.autoconfigure.JdbcTest
import org.springframework.context.annotation.Import
import java.time.Instant
import java.util.UUID
import kotlin.test.assertEquals

@JdbcTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ImportAutoConfiguration(JacksonAutoConfiguration::class)
@Import(
    TestcontainersConfiguration::class,
    RocketEventCodec::class,
    RocketRepository::class,
    RocketEventRepository::class,
)
class RocketQueryRepositoryTest {

    @Autowired
    lateinit var rockets: RocketRepository

    private val t0 = Instant.parse("2026-11-24T17:25:31.457818Z")

    private fun rocket(status: RocketStatus, type: String, mission: String, speed: Long, launchedAtOffset: Long) = Rocket(
        channel = UUID.randomUUID(),
        status = status,
        type = type,
        mission = mission,
        speed = speed,
        explosionReason = if (status == RocketStatus.EXPLODED) "PRESSURE_VESSEL_FAILURE" else null,
        launchedAt = t0.plusSeconds(launchedAtOffset),
        updatedAt = t0.plusSeconds(launchedAtOffset + 100),
        lastAppliedMessageNumber = 10,
    )

    private val visible = listOf(
        rocket(RocketStatus.LAUNCHED, "SATURN", "APOLLO", 9000, 3),
        rocket(RocketStatus.EXPLODED, "ATLAS", "GEMINI", 500, 1),
        rocket(RocketStatus.LAUNCHED, "TITAN", "ARTEMIS", 15000, 4),
        rocket(RocketStatus.LAUNCHED, "DELTA", "SKYLAB", 9000, 2),
        rocket(RocketStatus.EXPLODED, "ELECTRON", "MERCURY", -200, 5),
    )

    private val pendingChannel = UUID.randomUUID()

    @BeforeEach
    fun store() {
        visible.forEach {
            rockets.lockOrCreate(it.channel)
            rockets.update(it)
        }
        rockets.lockOrCreate(pendingChannel)
    }

    private fun expectedOrder(rockets: List<Rocket>, sort: RocketSortField, order: SortOrder): List<Rocket> {
        val primary: Comparator<Rocket> = when (sort) {
            RocketSortField.SPEED -> compareBy { it.speed }
            RocketSortField.TYPE -> compareBy { it.type }
            RocketSortField.MISSION -> compareBy { it.mission }
            RocketSortField.STATUS -> compareBy { it.status.name }
            RocketSortField.LAUNCHED_AT -> compareBy { it.launchedAt }
        }
        val directed = if (order == SortOrder.DESC) primary.reversed() else primary
        return rockets.sortedWith(directed.thenBy { it.channel.toString() })
    }

    @Test
    fun `sorts by every field in both directions, channel breaks ties, pending excluded`() {
        RocketSortField.entries.forEach { sort ->
            SortOrder.entries.forEach { order ->
                assertEquals(
                    expectedOrder(visible, sort, order),
                    rockets.findAllVisible(RocketQuery(sort = sort, order = order)),
                    "sort=$sort order=$order",
                )
            }
        }
    }

    @Test
    fun `default query is newest launch first`() {
        assertEquals(visible.sortedByDescending { it.launchedAt }, rockets.findAllVisible(RocketQuery()))
    }

    @Test
    fun `filters by status`() {
        assertEquals(
            visible.filter { it.status == RocketStatus.EXPLODED }.map { it.channel }.toSet(),
            rockets.findAllVisible(RocketQuery(status = RocketStatus.EXPLODED)).map { it.channel }.toSet(),
        )
    }

    @Test
    fun `filters by speed range, bounds inclusive`() {
        val found = rockets.findAllVisible(
            RocketQuery(minSpeed = 500, maxSpeed = 9000, sort = RocketSortField.SPEED, order = SortOrder.ASC),
        )
        assertEquals(listOf(500L, 9000L, 9000L), found.map { it.speed })
    }

    @Test
    fun `combines filters`() {
        val found = rockets.findAllVisible(RocketQuery(status = RocketStatus.LAUNCHED, minSpeed = 10000))
        assertEquals(listOf("TITAN"), found.map { it.type })
    }
}
