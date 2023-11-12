package todoist

import java.util.UUID

data class TodoistEvent(val event_data: Item)

data class Item(
    val id: String,
    val parent_id: String?,
    val section_id: String?,
    val project_id: String,
    val labels: List<String>
)

data class GetItemResponse(
    val ancestors: List<Item>,
    val item: Item
)

data class Command(
    val type: String,
    val uuid: String = UUID.randomUUID().toString(),
    val args: Map<String, Any?>
)
