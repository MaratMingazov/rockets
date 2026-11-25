package com.lunar.rockets.api

import com.lunar.rockets.codec.RocketEventCodec
import com.lunar.rockets.domain.ReceivedMessage
import com.lunar.rockets.domain.RocketEvent
import com.lunar.rockets.service.MessageService
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
import org.springframework.test.web.servlet.post
import java.time.Instant
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@WebMvcTest(MessagesController::class)
@Import(RocketEventCodec::class, MessagesControllerTest.Config::class)
class MessagesControllerTest {

    @TestConfiguration
    class Config {
        @Bean
        fun recordingMessageService() = RecordingMessageService()
    }

    class RecordingMessageService : MessageService {
        val received = mutableListOf<ReceivedMessage>()

        override fun receive(message: ReceivedMessage) {
            received += message
        }
    }

    @Autowired
    lateinit var mockMvc: MockMvc

    @Autowired
    lateinit var messageService: RecordingMessageService

    @BeforeEach
    fun clearReceived() {
        messageService.received.clear()
    }

    private fun postMessage(body: String): ResultActionsDsl =
        mockMvc.post("/messages") {
            contentType = MediaType.APPLICATION_JSON
            content = body
        }

    private fun message(
        messageNumber: Any = 1,
        messageType: String = "RocketLaunched",
        message: String = """{"type":"Falcon-9","launchSpeed":500,"mission":"ARTEMIS"}""",
        channel: String = "\"193270a9-c9cf-404a-8f83-838e71d9ae67\"",
    ) = """
        {
          "metadata": {
            "channel": $channel,
            "messageNumber": $messageNumber,
            "messageTime": "2022-02-02T19:39:05.86337+01:00",
            "messageType": "$messageType"
          },
          "message": $message
        }
    """

    private fun ResultActionsDsl.andExpectBadRequest(detail: String? = null) = andExpect {
        status { isBadRequest() }
        content { contentType(MediaType.APPLICATION_PROBLEM_JSON) }
        jsonPath("$.status") { value(400) }
        if (detail != null) jsonPath("$.detail") { value(detail) }
    }

    @Test
    fun `accepts a valid message and passes the typed command to the service`() {
        postMessage(message()).andExpect {
            status { isAccepted() }
            content { string("") }
        }

        assertEquals(
            listOf(
                ReceivedMessage(
                    channel = UUID.fromString("193270a9-c9cf-404a-8f83-838e71d9ae67"),
                    messageNumber = 1,
                    messageTime = Instant.parse("2022-02-02T18:39:05.86337Z"),
                    event = RocketEvent.Launched(type = "Falcon-9", launchSpeed = 500, mission = "ARTEMIS"),
                ),
            ),
            messageService.received,
        )
    }

    @Test
    fun `rejects unknown messageType`() {
        postMessage(message(messageType = "RocketTeleported", message = "{}"))
            .andExpectBadRequest("Unknown messageType 'RocketTeleported'")
        assertTrue(messageService.received.isEmpty())
    }

    @Test
    fun `rejects invalid message content`() {
        postMessage(message(messageType = "RocketSpeedIncreased", message = """{"by":-1}"""))
            .andExpectBadRequest("'by' must not be negative, was -1")
        assertTrue(messageService.received.isEmpty())
    }

    @Test
    fun `rejects messageNumber below 1`() {
        postMessage(message(messageNumber = 0))
            .andExpectBadRequest("'messageNumber' must be at least 1, was 0")
        assertTrue(messageService.received.isEmpty())
    }

    @Test
    fun `rejects missing message`() {
        val body = """
            {"metadata":{"channel":"193270a9-c9cf-404a-8f83-838e71d9ae67","messageNumber":1,
             "messageTime":"2022-02-02T19:39:05+01:00","messageType":"RocketExploded"}}
        """
        postMessage(body).andExpectBadRequest()
        assertTrue(messageService.received.isEmpty())
    }

    @Test
    fun `rejects missing channel`() {
        val body = """
            {"metadata":{"messageNumber":1,"messageTime":"2022-02-02T19:39:05+01:00","messageType":"RocketExploded"},
             "message":{"reason":"PRESSURE_VESSEL_FAILURE"}}
        """
        postMessage(body).andExpectBadRequest()
        assertTrue(messageService.received.isEmpty())
    }

    @Test
    fun `rejects invalid channel`() {
        postMessage(message(channel = "\"not-a-uuid\"")).andExpectBadRequest()
        assertTrue(messageService.received.isEmpty())
    }

    @Test
    fun `rejects malformed JSON`() {
        postMessage("{oops").andExpectBadRequest()
        assertTrue(messageService.received.isEmpty())
    }
}
