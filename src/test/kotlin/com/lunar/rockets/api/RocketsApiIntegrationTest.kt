package com.lunar.rockets.api

import com.lunar.rockets.TestcontainersConfiguration
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.context.annotation.Import
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.post
import java.util.UUID

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration::class)
class RocketsApiIntegrationTest {

    @Autowired
    lateinit var mockMvc: MockMvc

    private val channel = UUID.randomUUID()

    private fun postMessage(number: Int, type: String, message: String) {
        mockMvc.post("/messages") {
            contentType = MediaType.APPLICATION_JSON
            content = """
                {"metadata":{"channel":"$channel","messageNumber":$number,
                 "messageTime":"2022-02-02T19:39:0$number+01:00","messageType":"$type"},
                 "message":$message}
            """
        }.andExpect { status { isAccepted() } }
    }

    @Test
    fun `a rocket is hidden while pending and visible once launched`() {
        postMessage(2, "RocketSpeedIncreased", """{"by":3000}""")

        mockMvc.get("/rockets/$channel").andExpect { status { isNotFound() } }
        mockMvc.get("/rockets").andExpect { jsonPath("$.rockets[?(@.channel == '$channel')]") { isEmpty() } }

        postMessage(1, "RocketLaunched", """{"type":"Falcon-9","launchSpeed":500,"mission":"ARTEMIS"}""")

        mockMvc.get("/rockets/$channel").andExpect {
            status { isOk() }
            jsonPath("$.status") { value("LAUNCHED") }
            jsonPath("$.speed") { value(3500) }
            jsonPath("$.launchedAt") { value("2022-02-02T18:39:01Z") }
            jsonPath("$.updatedAt") { value("2022-02-02T18:39:02Z") }
        }
        mockMvc.get("/rockets?minSpeed=3500&maxSpeed=3500").andExpect {
            jsonPath("$.rockets[?(@.channel == '$channel')].speed") { value(3500) }
        }
    }
}
