package com.galaxyrio.gracelauncher.data

import org.json.JSONArray
import org.json.JSONObject

const val FavoritesCategoryId = "favorites"
const val MusicCategoryId = "music"

enum class CategoryCondition { Always, Headphones, Time }

data class AppCategory(
    val id: String,
    /** Built-in names are localized until the user renames them. */
    val name: String? = null,
    val itemKeys: List<String> = emptyList(),
    val condition: CategoryCondition = CategoryCondition.Always,
    val startMinute: Int = 8 * 60,
    val endMinute: Int = 18 * 60,
    val showOnHome: Boolean = false,
) {
    val key: String get() = "category:$id"

    fun isVisible(headphones: Boolean, minuteOfDay: Int): Boolean = when (condition) {
        CategoryCondition.Always -> true
        CategoryCondition.Headphones -> headphones
        CategoryCondition.Time -> if (startMinute < endMinute) minuteOfDay in startMinute until endMinute
            else minuteOfDay >= startMinute || minuteOfDay < endMinute
    }

    fun normalized() = copy(name = name?.trim()?.takeIf(String::isNotEmpty),
        itemKeys = itemKeys.filter { it.isNotBlank() && !it.startsWith("category:") &&
            !it.startsWith("widget:") && !it.startsWith("grace:") }.distinct(),
        startMinute = startMinute.coerceIn(0, 1439), endMinute = endMinute.coerceIn(0, 1439))

    companion object {
        fun defaults(favorites: List<String> = emptyList()) = listOf(
            AppCategory(FavoritesCategoryId, itemKeys = favorites, showOnHome = true),
            AppCategory(MusicCategoryId, condition = CategoryCondition.Headphones, showOnHome = true),
        )

        fun encode(categories: List<AppCategory>): String = JSONArray().apply {
            categories.distinctBy { it.id }.forEach { category -> category.normalized().let {
                put(JSONObject().put("id", it.id).put("name", it.name ?: JSONObject.NULL)
                    .put("items", JSONArray(it.itemKeys)).put("condition", it.condition.name)
                    .put("startMinute", it.startMinute).put("endMinute", it.endMinute).put("showOnHome", it.showOnHome))
            } }
        }.toString()

        fun decode(json: String?): List<AppCategory>? = json?.let { runCatching {
            val array = JSONArray(it)
            val categories = (0 until array.length()).map { index ->
                val value = array.getJSONObject(index)
                val keys = value.optJSONArray("items") ?: JSONArray()
                AppCategory(value.getString("id"), value.optString("name").takeUnless { it.isBlank() || it == "null" },
                    (0 until keys.length()).mapNotNull { i -> keys.opt(i) as? String },
                    CategoryCondition.entries.firstOrNull { entry -> entry.name == value.optString("condition") } ?: CategoryCondition.Always,
                    value.optInt("startMinute", 480), value.optInt("endMinute", 1080), value.optBoolean("showOnHome", false)).normalized()
            }.filter { category -> category.id.isNotBlank() }.distinctBy { category -> category.id }
            if (categories.any { category -> category.id == FavoritesCategoryId }) categories
            else listOf(AppCategory(FavoritesCategoryId, showOnHome = true)) + categories
        }.getOrNull() }
    }
}

/** Replace legacy app/shortcut rows with one category, keeping widgets and direct groups. */
internal fun migrateHomeCategories(settings: LauncherSettings, legacyFavorites: List<String>): LauncherSettings {
    if (settings.appCategories != null) return settings
    val members = legacyFavorites.filterNot { it.startsWith("folder:") }.distinct()
    val memberSet = members.toSet()
    val previous = settings.homeLayout.homeOrder ?: (legacyHomeOrder(settings) + legacyFavorites)
    val orderedMembers = (previous.filter { it in memberSet } + members).distinct()
    val categories = AppCategory.defaults(orderedMembers)
    val order = (previous.map { if (it in memberSet) categories.first().key else it } + categories.map { it.key }).distinct()
    return settings.copy(appCategories = categories, homeLayout = settings.homeLayout.copy(homeOrder = order))
}

internal data class CategoryHomeContent(val items: List<PopupItem>, val owners: Map<String, String>)

/** Expand only active categories; the first visible occurrence owns each row. */
internal fun expandHomeCategories(
    entries: List<PopupItem>, categories: List<AppCategory>, headphones: Boolean, minuteOfDay: Int,
    available: (String) -> Boolean,
): CategoryHomeContent {
    val byKey = categories.associateBy { it.key }
    val owners = linkedMapOf<String, String>()
    val items = buildList {
        entries.forEach { entry ->
            val category = byKey[entry.key]
            if (category == null) {
                if (owners.putIfAbsent(entry.key, entry.key) == null) add(entry)
            } else if (category.isVisible(headphones, minuteOfDay)) {
                category.itemKeys.forEach { key ->
                    if (available(key) && owners.putIfAbsent(key, entry.key) == null) add(PopupItem(key))
                }
            }
        }
    }
    return CategoryHomeContent(items, owners)
}
