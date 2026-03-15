package krill.zone.shared

import kotlinx.serialization.*

@Serializable
enum class FeatureState {
    PLANNING, DEVELOPMENT, TESTING, INCUBATING, READY, ROADMAP
}
@Serializable
enum class NodeBehavior {
    SHOW_MENU, SHOW_DIALOG, CREATE_NODE, CHANGE_SCREEN, EXECUTE, NONE
}

@Serializable
data class FeatureContent(
    val name: String = "",
    val title: String = "",
    val description: String = "",
    val shortDescription: String = "",
    val state: FeatureState = FeatureState.ROADMAP,

    val category: String = "",
    val subcategory: String = "",
    val order: Int = -1,
) {
    override fun toString(): String {
        return "FeatureContent(name=\"$name\", order=$order, title=\"$title\", description=\"$description\", state=FeatureState.$state, shortDescription=\"$shortDescription\",  category=\"$category\", subcategory=\"$subcategory\")"
    }
}