package com.lunar.rockets.api

import com.lunar.rockets.domain.Rocket
import com.lunar.rockets.domain.RocketQuery
import com.lunar.rockets.domain.RocketSortField
import com.lunar.rockets.domain.RocketStatus
import com.lunar.rockets.domain.SortOrder
import com.lunar.rockets.service.RocketNotFoundException
import com.lunar.rockets.service.RocketQueryService
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Import
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.ResultActionsDsl
import org.springframework.test.web.servlet.get
import java.time.Instant
import java.util.UUID
import kotlin.test.assertEquals

@WebMvcTest(RocketsController::class)
@Import(RocketsControllerTest.Config::class)
class RocketsControllerTest {

    @TestConfiguration
    class Config {
        @Bean
        fun fakeRocketQueryService() = FakeRocketQueryService()
    }

    class FakeRocketQueryService : RocketQueryService {
        val rockets = mutableMapOf<UUID, Rocket>()
        var lastQuery: RocketQuery? = null

        override fun get(channel: UUID): Rocket = rockets[channel] ?: throw RocketNotFoundException(channel)

        override fun list(query: RocketQuery): List<Rocket> {
            lastQuery = query
            return rockets.values.toList()
        }
    }

    @Autowired
    lateinit var mockMvc: MockMvc

    @Autowired
    lateinit var service: FakeRocketQueryService

    private val channel = UUID.fromString("193270a9-c9cf-404a-8f83-838e71d9ae67")

    private val rocket = Rocket(
        channel = channel,
        status = RocketStatus.LAUNCHED,
        type = "Falcon-9",
        mission = "ARTEMIS",
        speed = 3500,
        explosionReason = null,
        launchedAt = Instant.parse("2022-02-02T18:39:05.86337Z"),
        updatedAt = Instant.parse("2022-02-02T18:40:00Z"),
        lastAppliedMessageNumber = 2,
    )

    @BeforeEach
    fun reset() {
        service.rockets.clear()
        service.lastQuery = null
    }

    private fun ResultActionsDsl.andExpectProblem(status: Int, detail: String? = null) = andExpect {
        status { isEqualTo(status) }
        content { contentType(MediaType.APPLICATION_PROBLEM_JSON) }
        if (detail != null) jsonPath("$.detail") { value(detail) }
    }

    @Test
    fun `returns the current state of a rocket`() {
        service.rockets[channel] = rocket

        mockMvc.get("/rockets/$channel").andExpect {
            status { isOk() }
            content { contentType(MediaType.APPLICATION_JSON) }
            jsonPath("$.channel") { value(channel.toString()) }
            jsonPath("$.type") { value("Falcon-9") }
            jsonPath("$.mission") { value("ARTEMIS") }
            jsonPath("$.speed") { value(3500) }
            jsonPath("$.status") { value("LAUNCHED") }
            jsonPath("$.explosionReason") { value(null) }
            jsonPath("$.launchedAt") { value("2022-02-02T18:39:05.863370Z") }
            jsonPath("$.updatedAt") { value("2022-02-02T18:40:00Z") }
            jsonPath("$.lastAppliedMessageNumber") { doesNotExist() }
        }
    }

    @Test
    fun `returns 404 for an unknown or pending rocket`() {
        mockMvc.get("/rockets/$channel").andExpectProblem(404, "Rocket $channel not found")
    }

    @Test
    fun `returns 400 for a channel that is not a UUID`() {
        mockMvc.get("/rockets/not-a-uuid").andExpectProblem(400)
    }

    @Test
    fun `lists rockets wrapped in an object with default sorting`() {
        service.rockets[channel] = rocket

        mockMvc.get("/rockets").andExpect {
            status { isOk() }
            jsonPath("$.rockets.length()") { value(1) }
            jsonPath("$.rockets[0].channel") { value(channel.toString()) }
        }

        assertEquals(RocketQuery(sort = RocketSortField.LAUNCHED_AT, order = SortOrder.DESC), service.lastQuery)
    }

    @Test
    fun `passes sorting and filters to the service`() {
        mockMvc.get("/rockets?sort=speed&order=asc&status=EXPLODED&minSpeed=100&maxSpeed=900")
            .andExpect { status { isOk() } }

        assertEquals(
            RocketQuery(
                status = RocketStatus.EXPLODED,
                minSpeed = 100,
                maxSpeed = 900,
                sort = RocketSortField.SPEED,
                order = SortOrder.ASC,
            ),
            service.lastQuery,
        )
    }

    @Test
    fun `rejects unknown sort field`() {
        mockMvc.get("/rockets?sort=name")
            .andExpectProblem(400, "Unknown sort 'name', allowed: speed, type, mission, status, launchedAt")
    }

    @Test
    fun `rejects unknown order`() {
        mockMvc.get("/rockets?order=random").andExpectProblem(400, "Unknown order 'random', allowed: asc, desc")
    }

    @Test
    fun `rejects PENDING as a status filter`() {
        mockMvc.get("/rockets?status=PENDING")
            .andExpectProblem(400, "Unknown status 'PENDING', allowed: LAUNCHED, EXPLODED")
    }

    @Test
    fun `rejects a non-numeric speed filter`() {
        mockMvc.get("/rockets?minSpeed=fast").andExpectProblem(400)
    }
}
