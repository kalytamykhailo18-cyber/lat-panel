package com.player.tv.ui.player

import android.content.Context
import android.content.Intent
import com.player.tv.data.RecentItem

data class PlayItem(
    val title: String,
    val url: String,
    val isLive: Boolean,
    val subtitle: String? = null,
    val logo: String? = null,
    val number: Int = 0,
    val epgStreamId: Int? = null,   // live: stream id for the program guide
    val resumeKey: String? = null,  // VOD: key under which the position is remembered
    val recent: RecentItem? = null, // VOD: shown in "continue watching" while unfinished
)

/**
 * What the player should play. Live TV passes the whole category so the remote can zap
 * channels; series pass the season so the next episode starts automatically.
 * Kept in memory: lists can be thousands of items, too large for an Intent.
 */
object PlayQueue {
    var items: List<PlayItem> = emptyList()
        private set
    var index: Int = 0
    var startPositionMs: Long = 0L
        private set

    fun start(context: Context, items: List<PlayItem>, index: Int, startPositionMs: Long = 0L) {
        if (items.isEmpty()) return
        this.items = items
        this.index = index.coerceIn(items.indices)
        this.startPositionMs = startPositionMs
        context.startActivity(Intent(context, PlayerActivity::class.java))
    }
}
