package todoist

import com.fasterxml.jackson.databind.ObjectMapper
import com.github.tomakehurst.wiremock.WireMockServer
import com.github.tomakehurst.wiremock.client.WireMock.*
import com.github.tomakehurst.wiremock.core.WireMockConfiguration.options
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.*
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.web.server.LocalServerPort
import org.springframework.http.HttpStatus
import org.springframework.web.client.RestTemplate

@SpringBootTest(
    classes = [DemoApplication::class],
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT
)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class MainTest {
    @LocalServerPort
    var port = 0

    val wireMockServer = WireMockServer(options().port(8080))

    @BeforeAll
    fun beforeAll() {
        wireMockServer.start()

        val mapper = ObjectMapper()
        val response = GetItemResponse(
            ancestors = listOf(
                Item("Parent ID", null, null, "Project ID", listOf())
            ),
            Item("Item ID", "Parent ID", null, "Project ID", listOf())
        )
        wireMockServer.stubFor(
            post("/sync/v9/items/get")
                .willReturn(okJson(mapper.writeValueAsString(response)))
        )
        wireMockServer.stubFor(
            post("/sync/v9/sync")
                .willReturn(ok())
        )
    }

    @AfterAll
    fun afterAll() {
        wireMockServer.stop()
    }

    @BeforeEach
    fun beforeEach() {
        wireMockServer.resetRequests()
    }

    @Test
    fun `Event without follow-up label is ignored`() {
        // Given
        val event = TodoistEvent(
            event_data = Item(
                id = "Test ID",
                parent_id = null,
                section_id = null,
                project_id = "Test Project ID",
                labels = listOf()
            )
        )

        // When
        val response = RestTemplate().postForEntity(
            "http://localhost:$port/webhookEvent",
            event,
            String::class.java
        )

        // Then
        assertThat(response.statusCode).isEqualTo(HttpStatus.OK)

        wireMockServer.verify(0, postRequestedFor(urlEqualTo("/sync/v9/sync")))
    }

    @Test
    fun `Follow-up item with parent is updated after completion`() {
        // Given
        val event = TodoistEvent(
            event_data = Item(
                id = "Test ID",
                parent_id = "Parent ID",
                section_id = null,
                project_id = "Test Project ID",
                labels = listOf("follow-up")
            )
        )

        // When
        val response = RestTemplate().postForEntity(
            "http://localhost:$port/webhookEvent",
            event,
            String::class.java
        )

        // Then
        assertThat(response.statusCode).isEqualTo(HttpStatus.OK)

        wireMockServer.verify(1, postRequestedFor(urlEqualTo("/sync/v9/sync")))
        wireMockServer.verify(
            postRequestedFor(urlEqualTo("/sync/v9/sync"))
                .withHeader("Authorization", containing("Bearer"))
        )
        wireMockServer.verify(
            postRequestedFor(urlEqualTo("/sync/v9/sync")).withRequestBody(containing("item_move"))
        )
        wireMockServer.verify(
            postRequestedFor(urlEqualTo("/sync/v9/sync")).withRequestBody(containing("item_update"))
        )
        wireMockServer.verify(
            postRequestedFor(urlEqualTo("/sync/v9/sync")).withRequestBody(containing("item_uncomplete"))
        )
    }
}
