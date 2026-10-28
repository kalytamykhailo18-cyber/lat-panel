package com.player.tv.ui.player

import android.os.Bundle
import android.view.KeyEvent
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.FrameLayout
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.annotation.OptIn
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.platform.ComposeView
import androidx.lifecycle.lifecycleScope
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.Tracks
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import com.player.tv.R
import com.player.tv.data.Graph
import com.player.tv.ui.theme.PlayerTheme
import com.player.tv.ui.vod.formatDuration
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Full-screen ExoPlayer.
 * Live: Up/Down or CH+/CH- zap through the category; OK shows the channel banner with the
 * option bar (channel list, Audio, Subtitles, Screen, Favorite); Left opens the channel list
 * over the picture; dropped streams reconnect automatically.
 * VOD: own controls (progress, Audio, Subtitles, Speed, Screen, next episode), position
 * remembered, series continue with the next episode.
 * Back always closes the top layer first: menu, channel list, controls, then the player.
 */
@OptIn(UnstableApi::class)
class PlayerActivity : ComponentActivity() {

    private lateinit var player: ExoPlayer
    private lateinit var playerView: PlayerView
    private lateinit var overlayView: ComposeView
    private val overlayState = mutableStateOf(OverlayState())

    private var bannerJob: Job? = null
    private var epgJob: Job? = null
    private var retryJob: Job? = null
    private var retries = 0
    private var numberJob: Job? = null
    private var noticeJob: Job? = null
    private var hideJob: Job? = null
    private var progressJob: Job? = null
    private var listEpgJob: Job? = null
    private var typedNumber = ""
    private var menuTracks: List<TrackOption> = emptyList()
    private var swallowKeyUp = -1   // release of the key that opened the controls
    private val nowShowing = HashMap<Int, String?>()   // channel list: stream id -> program on now

    private val current: PlayItem get() = PlayQueue.items[PlayQueue.index]

    private var state: OverlayState
        get() = overlayState.value
        set(value) {
            overlayState.value = value
            layersBack.isEnabled = value.controlsVisible || value.menu != null || value.channelList
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (PlayQueue.items.isEmpty()) {   // process was recreated without a queue
            finish()
            return
        }
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        player = Players.create(this).apply { addListener(listener) }
        playerView = PlayerView(this).apply {
            player = this@PlayerActivity.player
            useController = false
            setShowBuffering(PlayerView.SHOW_BUFFERING_ALWAYS)
            isFocusable = true
            isFocusableInTouchMode = true
        }
        Players.styleSubtitles(playerView)
        Players.applyScreenMode(playerView)
        overlayView = ComposeView(this).apply {
            isFocusable = true
            setContent {
                PlayerTheme {
                    PlayerOverlay(overlayState.value, PlayQueue.items, onTap = ::onScreenTap, onSwipe = ::zap, onBack = ::finish,
                        onRetry = ::retryAfterFailure, onMenuChoice = ::chooseOption, vodActions = vodActions, liveActions = liveActions)
                }
            }
        }
        setContentView(object : FrameLayout(this) {
            override fun dispatchKeyEvent(event: KeyEvent): Boolean = handleKey(event) || super.dispatchKeyEvent(event)
        }.apply {
            addView(playerView, ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
            addView(overlayView, ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        })
        playerView.requestFocus()
        onBackPressedDispatcher.addCallback(this, layersBack)

        state = state.copy(favorites = favorites())
        play(PlayQueue.index, PlayQueue.startPositionMs)
    }

    private fun play(index: Int, startPositionMs: Long = 0L) {
        saveResumePosition()
        PlayQueue.index = index
        val item = current
        retryJob?.cancel()
        retries = 0

        val mediaItem = MediaItem.Builder()
            .setUri(item.url)
            .apply { if (item.url.substringBefore('?').endsWith(".m3u8")) setMimeType(MimeTypes.APPLICATION_M3U8) }
            .build()
        Players.clearOverrides(player)
        player.setMediaItem(mediaItem, startPositionMs)
        player.setPlaybackSpeed(1f)
        player.prepare()
        player.playWhenReady = true

        // Zapping keeps the live option bar or the channel list open; anything else starts clean.
        val keepLive = item.isLive && state.item?.isLive == true
        state = state.copy(
            item = item,
            index = index,
            loading = true,
            failed = false,
            message = null,
            epg = emptyList(),
            menu = null,
            showBanner = false,
            controlsVisible = keepLive && state.controlsVisible,
            channelList = keepLive && state.channelList,
            vod = VodState(hasNext = !item.isLive && index < PlayQueue.items.lastIndex),
        )
        if (startPositionMs > 0) showNotice(getString(R.string.player_resuming, formatDuration(startPositionMs)))
        if (item.isLive) {
            showBanner()
            loadEpg(item)
        } else {
            showControls()
        }
    }

    private fun zap(step: Int) {
        val size = PlayQueue.items.size
        if (size > 1) play(Math.floorMod(PlayQueue.index + step, size))
        if (state.controlsVisible) scheduleHide()
    }

    private fun onScreenTap() {
        when {
            state.menu != null -> closeMenu()
            state.channelList -> closeChannelList()
            state.controlsVisible -> hideControls()
            !state.failed -> showControls()
        }
    }

    /** Short banner while zapping: no buttons, the remote keeps zapping. */
    private fun showBanner() {
        if (state.controlsVisible || state.channelList) return
        state = state.copy(showBanner = true)
        bannerJob?.cancel()
        bannerJob = lifecycleScope.launch {
            delay(5000)
            state = state.copy(showBanner = false)
        }
    }

    private fun loadEpg(item: PlayItem) {
        epgJob?.cancel()
        val streamId = item.epgStreamId ?: return
        epgJob = lifecycleScope.launch {
            delay(300)   // skip channels the user zaps past quickly
            val epg = runCatching { Graph.xtream?.shortEpg(streamId) }.getOrNull().orEmpty()
            if (current == item) state = state.copy(epg = epg)
        }
    }

    // ---- Controls and menus (live and VOD) ------------------------------------------------------

    private fun showControls(focusSeekBar: Boolean = false) {
        if (state.failed) return
        if (!state.controlsVisible) {
            bannerJob?.cancel()
            state = state.copy(controlsVisible = true, showBanner = false, channelList = false,
                vod = state.vod.copy(focusSeekBar = focusSeekBar))
            overlayView.requestFocus()   // the remote's keys go to the controls from now on
        }
        if (!current.isLive) {
            updateProgress()
            progressJob?.cancel()
            progressJob = lifecycleScope.launch {
                while (isActive) {
                    delay(500)
                    updateProgress()
                }
            }
        }
        scheduleHide()
    }

    private fun hideControls() {
        hideJob?.cancel()
        progressJob?.cancel()
        state = state.copy(controlsVisible = false, menu = null, channelList = false)
        playerView.requestFocus()
    }

    /**
     * Hide after a while without keys: the controls after a few seconds (VOD only while
     * playing), the channel list after 15 s. An open menu stays until a choice or Back.
     */
    private fun scheduleHide() {
        hideJob?.cancel()
        if (state.menu != null) return
        hideJob = lifecycleScope.launch {
            if (state.channelList) {
                delay(15_000)
                closeChannelList()
            } else if (state.controlsVisible) {
                delay(if (current.isLive) 6000 else 5000)
                if (current.isLive || player.isPlaying) hideControls()
            }
        }
    }

    private fun openMenu(menu: PlayerMenu) {
        hideJob?.cancel()
        state = state.copy(menu = buildMenu(menu))
    }

    private fun buildMenu(menu: PlayerMenu): MenuState {
        val live = current.isLive
        val stillLoading = player.currentTracks.isEmpty
        return when (menu) {
            PlayerMenu.SPEED -> {
                val speed = player.playbackParameters.speed
                MenuState(menu, SPEEDS.map(::speedLabel), SPEEDS.indexOfFirst { it == speed })
            }
            PlayerMenu.SCREEN -> MenuState(
                menu,
                listOf(getString(R.string.screen_fit), getString(R.string.screen_fill), getString(R.string.screen_zoom)),
                Graph.store.screenMode.coerceIn(Players.SCREEN_MODES.indices),
                getString(R.string.screen_note),
            )
            PlayerMenu.AUDIO -> {
                menuTracks = Players.tracks(player, C.TRACK_TYPE_AUDIO) { getString(R.string.player_track_n, it) }
                MenuState(menu, menuTracks.map { it.label }, menuTracks.indexOfFirst { it.selected }, when {
                    stillLoading -> getString(R.string.player_tracks_loading)
                    menuTracks.size <= 1 -> getString(if (live) R.string.player_live_no_audio_tracks else R.string.player_no_audio_tracks)
                    else -> getString(R.string.player_choice_kept)
                })
            }
            PlayerMenu.SUBTITLES -> {
                menuTracks = Players.tracks(player, C.TRACK_TYPE_TEXT) { getString(R.string.player_track_n, it) }
                val selected = menuTracks.indexOfFirst { it.selected }
                MenuState(menu, listOf(getString(R.string.player_subtitles_off)) + menuTracks.map { it.label },
                    if (selected >= 0) selected + 1 else 0, when {
                        stillLoading -> getString(R.string.player_tracks_loading)
                        menuTracks.isEmpty() -> getString(if (live) R.string.player_live_no_subtitles else R.string.player_no_subtitles)
                        else -> getString(R.string.player_choice_kept)
                    })
            }
        }
    }

    private fun chooseOption(index: Int) {
        when (state.menu?.menu) {
            PlayerMenu.SPEED -> SPEEDS.getOrNull(index)?.let { player.setPlaybackSpeed(it) }
            PlayerMenu.SCREEN -> {
                Graph.store.screenMode = index
                Players.applyScreenMode(playerView, index)
            }
            PlayerMenu.AUDIO -> menuTracks.getOrNull(index)?.let { Players.select(player, C.TRACK_TYPE_AUDIO, it) }
            PlayerMenu.SUBTITLES -> Players.select(player, C.TRACK_TYPE_TEXT, if (index == 0) null else menuTracks.getOrNull(index - 1))
            null -> {}
        }
        closeMenu()
        if (!current.isLive) updateProgress()
    }

    private fun closeMenu() {
        state = state.copy(menu = null)
        scheduleHide()
    }

    // ---- VOD controls --------------------------------------------------------------------------

    private fun updateProgress() {
        state = state.copy(
            vod = state.vod.copy(
                positionMs = player.currentPosition.coerceAtLeast(0),
                durationMs = player.duration.takeIf { it != C.TIME_UNSET }?.coerceAtLeast(0) ?: 0,
                bufferedMs = player.bufferedPosition.coerceAtLeast(0),
                playing = player.playWhenReady,
                speedLabel = speedLabel(player.playbackParameters.speed),
            )
        )
    }

    private val vodActions = object : VodActions {
        override fun playPause() {
            player.playWhenReady = !player.playWhenReady
            updateProgress()
            scheduleHide()
        }

        override fun seekBy(deltaMs: Long) {
            val duration = player.duration.takeIf { it != C.TIME_UNSET } ?: Long.MAX_VALUE
            seekTo((player.currentPosition + deltaMs).coerceIn(0, duration))
        }

        override fun seekTo(positionMs: Long) {
            player.seekTo(positionMs)
            updateProgress()
            scheduleHide()
        }

        override fun openMenu(menu: PlayerMenu) = this@PlayerActivity.openMenu(menu)

        override fun chooseOption(index: Int) = this@PlayerActivity.chooseOption(index)

        override fun next() {
            val next = PlayQueue.index + 1
            if (next < PlayQueue.items.size) {
                current.resumeKey?.let { Graph.store.saveResumePosition(it, 0) }
                play(next)
                showNotice(getString(R.string.player_next_episode, current.title))
            }
        }
    }

    private fun speedLabel(speed: Float) = if (speed == 1f) getString(R.string.player_speed_normal) else "${speed.toString().removeSuffix(".0")}x"

    // ---- Live controls -------------------------------------------------------------------------

    private fun favorites(): Set<Int> = Graph.xtream?.account?.let { Graph.store.favorites(it) }.orEmpty()

    private fun openChannelList() {
        bannerJob?.cancel()
        state = state.copy(channelList = true, controlsVisible = false, showBanner = false, menu = null)
        overlayView.requestFocus()
        scheduleHide()
    }

    private fun closeChannelList() {
        hideJob?.cancel()
        listEpgJob?.cancel()
        state = state.copy(channelList = false)
        playerView.requestFocus()
    }

    private val liveActions = object : LiveActions {
        override fun openChannels() = openChannelList()

        override fun openMenu(menu: PlayerMenu) = this@PlayerActivity.openMenu(menu)

        override fun chooseOption(index: Int) = this@PlayerActivity.chooseOption(index)

        override fun toggleFavorite() {
            val account = Graph.xtream?.account ?: return
            val streamId = current.epgStreamId ?: return
            val added = Graph.store.toggleFavorite(account, streamId)
            state = state.copy(favorites = favorites())
            showNotice(getString(if (added) R.string.favorite_added else R.string.favorite_removed))
            scheduleHide()
        }

        override fun playChannel(index: Int) {
            if (index == PlayQueue.index && !state.failed) closeChannelList()
            else {
                play(index)
                scheduleHide()
            }
        }

        override fun focusChannel(index: Int) {
            scheduleHide()
            val streamId = PlayQueue.items.getOrNull(index)?.epgStreamId ?: return
            if (streamId in nowShowing) {
                nowShowing[streamId]?.let { state = state.copy(listNow = state.listNow + (index to it)) }
                return
            }
            listEpgJob?.cancel()
            listEpgJob = lifecycleScope.launch {
                delay(300)   // only for the row the focus stops on
                val nowSec = System.currentTimeMillis() / 1000
                val entries = runCatching { Graph.xtream?.epg(streamId, 2) }.getOrNull().orEmpty()
                val title = (entries.firstOrNull { nowSec in it.start until it.end } ?: entries.firstOrNull())?.title
                nowShowing[streamId] = title
                if (title != null) state = state.copy(listNow = state.listNow + (index to title))
            }
        }
    }

    // ---- Notices, errors ----------------------------------------------------------------------

    /** Short message top-right for a few seconds (resume point, next episode, favorites). */
    private fun showNotice(text: String) {
        state = state.copy(notice = text)
        noticeJob?.cancel()
        noticeJob = lifecycleScope.launch {
            delay(3500)
            state = state.copy(notice = null)
        }
    }

    /** After a playback failure: try the same item again from where it stopped. */
    private fun retryAfterFailure() {
        retries = 0
        state = state.copy(failed = false, loading = true, message = null)
        if (current.isLive) player.seekToDefaultPosition()
        player.prepare()
        player.play()
    }

    private val listener = object : Player.Listener {
        override fun onIsPlayingChanged(isPlaying: Boolean) {
            if (isPlaying) {
                retries = 0
                state = state.copy(message = null, loading = false, failed = false)
            }
            if (state.controlsVisible && !current.isLive) {
                updateProgress()
                if (isPlaying) scheduleHide()
            }
        }

        override fun onTracksChanged(tracks: Tracks) {
            // A menu opened while the stream was starting fills in as soon as the tracks are known.
            val open = state.menu?.menu
            if (open == PlayerMenu.AUDIO || open == PlayerMenu.SUBTITLES) state = state.copy(menu = buildMenu(open))
        }

        override fun onPlaybackStateChanged(playbackState: Int) {
            if (playbackState == Player.STATE_READY && state.loading) {
                state = state.copy(loading = false)
            }
            if (playbackState == Player.STATE_ENDED && !current.isLive) {
                val next = PlayQueue.index + 1
                if (next < PlayQueue.items.size) {
                    current.resumeKey?.let { Graph.store.saveResumePosition(it, 0) }
                    play(next)
                    showNotice(getString(R.string.player_next_episode, current.title))
                } else {
                    finish()
                }
            }
        }

        override fun onPlayerError(error: PlaybackException) {
            if (error.errorCode == PlaybackException.ERROR_CODE_BEHIND_LIVE_WINDOW) {
                player.seekToDefaultPosition()
                player.prepare()
                return
            }
            val maxRetries = if (current.isLive) 5 else 1
            if (retries < maxRetries) {
                retries++
                state = state.copy(message = getString(R.string.player_reconnecting))
                retryJob = lifecycleScope.launch {
                    delay(2000L * retries)
                    val position = if (current.isLive) C.TIME_UNSET else player.currentPosition
                    if (position == C.TIME_UNSET) player.seekToDefaultPosition() else player.seekTo(position)
                    player.prepare()
                    player.play()
                }
            } else {
                // Give the user a way out: retry, another channel, or back (tap or remote).
                // An open channel list stays, to pick another channel right away.
                val keepList = state.channelList
                if (state.controlsVisible || state.menu != null) hideControls()
                state = state.copy(message = null, loading = false, failed = true, channelList = keepList, showBanner = false)
                if (keepList) overlayView.requestFocus()
            }
        }
    }

    // ---- Keys -----------------------------------------------------------------------------------

    /** Live: digits typed on the remote jump to that channel number after a short pause. */
    private fun typeDigit(digit: Int) {
        if (typedNumber.length >= 4) typedNumber = ""
        typedNumber += digit
        state = state.copy(typedNumber = typedNumber)
        numberJob?.cancel()
        numberJob = lifecycleScope.launch {
            delay(1500)
            val number = typedNumber.toIntOrNull() ?: 0
            typedNumber = ""
            state = state.copy(typedNumber = null)
            val index = PlayQueue.items.indexOfFirst { it.number == number }
            if (index < 0) showNotice(getString(R.string.player_no_channel_number, number))
            else if (index != PlayQueue.index) play(index)
        }
    }

    /** Back closes the top layer: open menu, channel list, controls. With none, it leaves the player. */
    private val layersBack = object : OnBackPressedCallback(false) {
        override fun handleOnBackPressed() {
            when {
                state.menu != null -> closeMenu()
                state.channelList -> closeChannelList()
                else -> hideControls()
            }
        }
    }

    /** Remote keys, seen before the views get them. Returns true when consumed. */
    private fun handleKey(event: KeyEvent): Boolean {
        if (!::player.isInitialized) return false
        val down = event.action == KeyEvent.ACTION_DOWN
        if (!down && event.keyCode == swallowKeyUp) {
            swallowKeyUp = -1
            return true
        }
        return if (current.isLive) handleLiveKey(event.keyCode, down) else handleVodKey(event.keyCode, down)
    }

    /**
     * Live. Nothing on screen: Up/Down zap, OK / Right / Menu / Info show the option bar, Left
     * opens the channel list. Option bar: Left/Right move between its buttons, Up/Down keep
     * zapping. Digits and CH+/CH- work everywhere. After a failure OK retries.
     */
    private fun handleLiveKey(code: Int, down: Boolean): Boolean {
        if (code in KeyEvent.KEYCODE_0..KeyEvent.KEYCODE_9) {
            if (down) typeDigit(code - KeyEvent.KEYCODE_0)
            return true
        }
        when (code) {
            KeyEvent.KEYCODE_CHANNEL_UP -> { if (down) zap(+1); return true }
            KeyEvent.KEYCODE_CHANNEL_DOWN -> { if (down) zap(-1); return true }
        }
        if (state.menu != null || state.channelList) {
            if (down) scheduleHide()
            return false   // the menu / list moves with the D-pad
        }
        when (code) {
            KeyEvent.KEYCODE_DPAD_UP -> { if (down) zap(+1); return true }
            KeyEvent.KEYCODE_DPAD_DOWN -> { if (down) zap(-1); return true }
        }
        if (state.controlsVisible) {
            if (down) scheduleHide()
            return false
        }
        when (code) {
            KeyEvent.KEYCODE_DPAD_LEFT -> {
                if (down) { swallowKeyUp = code; openChannelList() }
                return true
            }
            KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER, KeyEvent.KEYCODE_NUMPAD_ENTER -> {
                if (down) { swallowKeyUp = code; if (state.failed) retryAfterFailure() else showControls() }
                return true
            }
            KeyEvent.KEYCODE_DPAD_RIGHT, KeyEvent.KEYCODE_MENU, KeyEvent.KEYCODE_INFO -> {
                if (down && !state.failed) { swallowKeyUp = code; showControls() }
                return true
            }
        }
        return false
    }

    /** VOD. Controls hidden: OK / Up / Down show them, Left / Right seek 10 s. Shown: the D-pad moves between them. */
    private fun handleVodKey(code: Int, down: Boolean): Boolean {
        if (state.failed) return false   // onKeyDown: OK retries
        when (code) {
            KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE, KeyEvent.KEYCODE_MEDIA_PLAY, KeyEvent.KEYCODE_MEDIA_PAUSE -> {
                if (down) {
                    when (code) {
                        KeyEvent.KEYCODE_MEDIA_PLAY -> player.play()
                        KeyEvent.KEYCODE_MEDIA_PAUSE -> player.pause()
                        else -> player.playWhenReady = !player.playWhenReady
                    }
                    showControls()
                }
                return true
            }
            KeyEvent.KEYCODE_MEDIA_FAST_FORWARD -> { if (down) { vodActions.seekBy(30_000); showControls(true) }; return true }
            KeyEvent.KEYCODE_MEDIA_REWIND -> { if (down) { vodActions.seekBy(-10_000); showControls(true) }; return true }
            KeyEvent.KEYCODE_MEDIA_NEXT -> { if (down) vodActions.next(); return true }
        }
        if (!state.controlsVisible) {
            when (code) {
                KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER, KeyEvent.KEYCODE_NUMPAD_ENTER, KeyEvent.KEYCODE_DPAD_UP,
                KeyEvent.KEYCODE_DPAD_DOWN, KeyEvent.KEYCODE_MENU, KeyEvent.KEYCODE_INFO -> {
                    if (down) {
                        swallowKeyUp = code
                        showControls()
                    }
                    return true
                }
                KeyEvent.KEYCODE_DPAD_LEFT, KeyEvent.KEYCODE_DPAD_RIGHT -> {
                    if (down) {
                        swallowKeyUp = code
                        vodActions.seekBy(if (code == KeyEvent.KEYCODE_DPAD_LEFT) -10_000 else 10_000)
                        showControls(focusSeekBar = true)
                    }
                    return true
                }
            }
            return false
        }
        if (down) scheduleHide()
        return false
    }

    // VOD after a failure: OK retries (the error panel says so).
    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        if (::player.isInitialized && state.failed &&
            (keyCode == KeyEvent.KEYCODE_DPAD_CENTER || keyCode == KeyEvent.KEYCODE_ENTER)
        ) {
            retryAfterFailure()
            return true
        }
        return super.onKeyDown(keyCode, event)
    }

    private fun saveResumePosition() {
        if (!::player.isInitialized || PlayQueue.items.isEmpty()) return
        val key = current.resumeKey ?: return
        val duration = player.duration
        val position = player.currentPosition
        if (duration <= 0 || position <= 0) return
        val finished = position > duration * 0.95
        Graph.store.saveResumePosition(key, if (finished || position < 10_000) 0 else position)

        val recent = current.recent ?: return
        val account = Graph.xtream?.account ?: return
        val isLastInQueue = PlayQueue.index == PlayQueue.items.lastIndex
        if (finished && isLastInQueue) {
            Graph.store.removeRecent(account, recent.key)
        } else if (position >= 10_000) {
            Graph.store.saveRecent(account, recent.copy(positionMs = position, durationMs = duration))
        }
    }

    override fun onStart() {
        super.onStart()
        if (!::player.isInitialized) return
        if (current.isLive && player.playbackState == Player.STATE_IDLE) {
            player.seekToDefaultPosition()   // rejoin live edge after returning from background
            player.prepare()
        }
        player.playWhenReady = true
    }

    override fun onStop() {
        super.onStop()
        if (!::player.isInitialized) return
        saveResumePosition()
        // Live: release the connection (providers limit simultaneous connections).
        if (current.isLive) player.stop() else player.pause()
    }

    override fun onDestroy() {
        super.onDestroy()
        if (::player.isInitialized) player.release()
    }

    private companion object {
        val SPEEDS = listOf(0.5f, 0.75f, 1f, 1.25f, 1.5f, 2f)
    }
}
