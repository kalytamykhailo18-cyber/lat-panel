package com.player.tv.data

import android.content.Context
import androidx.core.content.edit
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * Local persistence. Device-ID sessions and manual Xtream sessions are stored under separate
 * keys so the two sign-in methods never overwrite each other.
 */
class SessionStore(context: Context) {

    enum class Mode { DEVICE, MANUAL }

    private companion object {
        const val MAX_RECENTS = 15
    }

    private val prefs = context.getSharedPreferences("session", Context.MODE_PRIVATE)

    var mode: Mode
        get() = if (prefs.getString("mode", null) == "manual") Mode.MANUAL else Mode.DEVICE
        set(value) = prefs.edit { putString("mode", value.name.lowercase()) }

    /** Last credentials received from the panel; lets the app start if the panel is briefly down. */
    var deviceAccount: Account?
        get() = readAccount("device", Account.Source.DEVICE)
        set(value) = writeAccount("device", value)

    var manualAccount: Account?
        get() = readAccount("manual", Account.Source.MANUAL)
        set(value) = writeAccount("manual", value)

    var liveHls: Boolean
        get() = prefs.getBoolean("live_hls", false)
        set(value) = prefs.edit { putBoolean("live_hls", value) }

    /** Subtitles at start of playback: "off", or a language code tried first ("es", "en"). */
    var subtitleLanguage: String
        get() = prefs.getString("sub_lang", "off")!!
        set(value) = prefs.edit { putString("sub_lang", value) }

    /** 0 small, 1 normal, 2 large. */
    var subtitleSize: Int
        get() = prefs.getInt("sub_size", 1)
        set(value) = prefs.edit { putInt("sub_size", value) }

    /** Picture format in the player: 0 fit, 1 fill (stretch), 2 zoom (crop). Kept for every channel and movie. */
    var screenMode: Int
        get() = prefs.getInt("screen_mode", 0)
        set(value) = prefs.edit { putInt("screen_mode", value) }

    // ---- Parental control (per device, independent of the account) ----

    var parentalEnabled: Boolean
        get() = prefs.getBoolean("parental_on", false)
        set(value) = prefs.edit { putBoolean("parental_on", value) }

    var parentalPin: String
        get() = prefs.getString("parental_pin", "0000")!!
        set(value) = prefs.edit { putString("parental_pin", value) }

    /** Locked categories as "live:ID", "vod:ID" or "series:ID". */
    var lockedCategories: Set<String>
        get() = prefs.getStringSet("parental_locked", emptySet())!!.toSet()
        set(value) = prefs.edit { putStringSet("parental_locked", value) }

    fun clearRecents(account: Account) = prefs.edit { remove(recentsKey(account)) }

    fun clearFavorites(account: Account) = prefs.edit { remove(favoritesKey(account)) }

    fun isMessageSeen(id: Int): Boolean = id.toString() in prefs.getStringSet("seen_messages", emptySet())!!

    fun markMessageSeen(id: Int) {
        val seen = prefs.getStringSet("seen_messages", emptySet())!!.toMutableSet()
        seen += id.toString()
        prefs.edit { putStringSet("seen_messages", seen) }
    }

    // Favorites are kept per account so two providers' channel ids never mix.
    fun favorites(account: Account): Set<Int> =
        prefs.getStringSet(favoritesKey(account), emptySet())!!.mapNotNull { it.toIntOrNull() }.toSet()

    fun toggleFavorite(account: Account, streamId: Int): Boolean {
        val set = prefs.getStringSet(favoritesKey(account), emptySet())!!.toMutableSet()
        val added = set.add(streamId.toString())
        if (!added) set.remove(streamId.toString())
        prefs.edit { putStringSet(favoritesKey(account), set) }
        return added
    }

    fun resumePosition(key: String): Long = prefs.getLong("resume:$key", 0L)

    fun saveResumePosition(key: String, positionMs: Long) = prefs.edit {
        if (positionMs > 0) putLong("resume:$key", positionMs) else remove("resume:$key")
    }

    /** "Continue watching", most recent first, per account. */
    fun recents(account: Account): List<RecentItem> {
        val raw = prefs.getString(recentsKey(account), null) ?: return emptyList()
        return runCatching {
            (parseJson(raw) as JsonArray).filterIsInstance<JsonObject>().mapNotNull { o ->
                RecentItem(
                    key = o.str("key") ?: return@mapNotNull null,
                    title = o.str("title").orEmpty(),
                    image = o.str("image"),
                    route = o.str("route") ?: return@mapNotNull null,
                    positionMs = o.long("pos") ?: 0,
                    durationMs = o.long("dur") ?: 0,
                )
            }
        }.getOrDefault(emptyList())
    }

    fun saveRecent(account: Account, item: RecentItem) = writeRecents(account, listOf(item) + recents(account).filter { it.key != item.key })

    fun removeRecent(account: Account, key: String) = writeRecents(account, recents(account).filter { it.key != key })

    private fun writeRecents(account: Account, items: List<RecentItem>) {
        val array = buildJsonArray {
            items.take(MAX_RECENTS).forEach { r ->
                add(buildJsonObject {
                    put("key", r.key); put("title", r.title); put("image", r.image); put("route", r.route)
                    put("pos", r.positionMs); put("dur", r.durationMs)
                })
            }
        }
        prefs.edit { putString(recentsKey(account), array.toString()) }
    }

    private fun recentsKey(account: Account) = "recents:${account.serverUrl}|${account.username}"

    private fun favoritesKey(account: Account) = "fav:${account.serverUrl}|${account.username}"

    private fun readAccount(prefix: String, source: Account.Source): Account? {
        val url = prefs.getString("${prefix}_url", null) ?: return null
        return Account(url, prefs.getString("${prefix}_user", "")!!, prefs.getString("${prefix}_pass", "")!!, source)
    }

    private fun writeAccount(prefix: String, account: Account?) = prefs.edit {
        if (account == null) {
            remove("${prefix}_url"); remove("${prefix}_user"); remove("${prefix}_pass")
        } else {
            putString("${prefix}_url", account.serverUrl)
            putString("${prefix}_user", account.username)
            putString("${prefix}_pass", account.password)
        }
    }
}
