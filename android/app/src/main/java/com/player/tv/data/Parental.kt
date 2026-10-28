package com.player.tv.data

/**
 * Parental control: categories locked with a 4-digit PIN. Entering the PIN for a locked category
 * opens it until the app is closed. Locked categories are also left out of "All", search and the
 * home rows, so their content never shows up by another way.
 */
object Parental {
    enum class Type(val prefix: String) { LIVE("live"), VOD("vod"), SERIES("series") }

    private val unlocked = mutableSetOf<String>()

    fun key(type: Type, categoryId: String) = "${type.prefix}:$categoryId"

    fun isLocked(type: Type, categoryId: String?): Boolean {
        if (categoryId == null || !Graph.store.parentalEnabled) return false
        val key = key(type, categoryId)
        return key in Graph.store.lockedCategories && key !in unlocked
    }

    /** Lock state as configured, ignoring this session's unlocks (for the settings screen). */
    fun isConfiguredLocked(type: Type, categoryId: String) = key(type, categoryId) in Graph.store.lockedCategories

    fun setLocked(type: Type, categoryId: String, locked: Boolean) {
        val key = key(type, categoryId)
        Graph.store.lockedCategories = Graph.store.lockedCategories.let { if (locked) it + key else it - key }
        if (locked) unlocked -= key
    }

    fun checkPin(pin: String) = pin == Graph.store.parentalPin

    fun unlock(type: Type, categoryId: String) {
        unlocked += key(type, categoryId)
    }

    /** Forget this session's unlocks (PIN changed, control switched off and on). */
    fun relock() = unlocked.clear()

    /** Category names providers commonly use for adult content; locked by default. */
    fun looksAdult(name: String) = ADULT.containsMatchIn(name)

    /** First activation: lock the adult categories found in the catalogs. */
    fun lockAdult(categories: Map<Type, List<Category>>) {
        categories.forEach { (type, list) -> list.filter { looksAdult(it.name) }.forEach { setLocked(type, it.id, true) } }
    }

    private val ADULT = Regex("""(?i)\b(adult[oa]?s?|xxx|porn\w*|er[oó]tic\w*|\+18|18\+|hot)\b|\+18|18\+""")
}

fun <T> List<T>.allowed(type: Parental.Type, categoryOf: (T) -> String?): List<T> =
    if (!Graph.store.parentalEnabled) this else filterNot { Parental.isLocked(type, categoryOf(it)) }
