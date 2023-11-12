package todoist

import com.fasterxml.jackson.databind.ObjectMapper
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication
import org.springframework.stereotype.Component
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.reactive.function.client.WebClient
import mu.KotlinLogging
import org.springframework.web.reactive.function.BodyInserters
import java.util.*
import kotlin.collections.ArrayList

private val logger = KotlinLogging.logger {}

@SpringBootApplication
class DemoApplication

fun main(args: Array<String>) {
    runApplication<DemoApplication>(*args)
}

@Component
class WebClientProvider {
    @Value("\${todoist.api.key}")
    val apiKey: String? = null

    @Value("\${todoist.url}")
    lateinit var todoistUrl: String

    fun createSyncClient(): WebClient {
        return WebClient.builder()
            .baseUrl("$todoistUrl/sync/v9")
            .defaultHeader("Authorization", "Bearer $apiKey")
            .defaultHeader("Content-Type", "application/x-www-form-urlencoded")
            .build()
    }
}

@RestController
class MessageController constructor(private val webClientProvider: WebClientProvider) {
    @PostMapping("/webhookEvent")
    fun webhookEvent(@RequestBody body: TodoistEvent) {
        logger.info { "Received event $body" }

        if (!body.event_data.labels.contains("follow-up")) {
            // Further processing is only required if this is a follow-up task
            return
        }
        if (body.event_data.parent_id == null) {
            // A follow-up task without parent is misconfigured and can be ignored
            return
        }

        // Fetch item to get parent details as well
        val webclient = webClientProvider.createSyncClient()
        val response = webclient
            .post()
            .uri("/items/get")
            .body(BodyInserters.fromValue("item_id=${body.event_data.id}"))
            .retrieve()
            .bodyToMono(GetItemResponse::class.java)
            .block()

        if (response == null) {
            return
        }
        // The logic can only handle a single parent
        if (response.ancestors.size != 1) {
            return
        }

        val commands = ArrayList<Command>()
        // Change parent of item
        val parent = response.ancestors[0]
        val moveArguments = if (parent.parent_id != null) {
            mapOf("id" to response.item.id, "parent_id" to parent.parent_id)
        } else if (response.item.section_id != null) {
            mapOf("id" to response.item.id, "section_id" to response.item.section_id)
        } else { // Project id is never null
            mapOf("id" to response.item.id, "project_id" to response.item.project_id)
        }

        // Move item outside of parent
        commands.add(Command(
            type = "item_move",
            args = moveArguments
        ))

        // Remove 'follow-up' label
        val filteredLabels = response.item.labels.filter { label -> label != "follow-up" }
        commands.add(Command(
            type = "item_update",
            args = mapOf(
                "id" to response.item.id,
                "labels" to filteredLabels
            )
        ))

        // Uncomplete the item
        commands.add(Command(
            type = "item_uncomplete",
            args = mapOf("id" to response.item.id)
        ))

        val objectMapper = ObjectMapper()
        val serializedCommands = "commands=${objectMapper.writeValueAsString(commands)}"

        webclient
            .post()
            .uri("/sync")
            .body(BodyInserters.fromValue(serializedCommands))
            .retrieve()
            .bodyToMono(String::class.java)
            .block()
    }
}
