package com.player.tv.ui.player

import android.os.Bundle
import android.view.KeyEvent
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.FrameLayout
import androidx.activity.ComponentActivity
import androidx.annotation.OptIn
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.platform.ComposeView
import androidx.lifecycle.lifecycleScope
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.extractor.DefaultExtractorsFactory
import androidx.media3.extractor.ts.DefaultTsPayloadReaderFactory
import androidx.media3.ui.PlayerView
import com.player.tv.BuildConfig
import com.player.tv.R
import com.player.tv.data.Graph
import com.player.tv.ui.theme.PlayerTheme
import com.player.tv.ui.vod.formatDuration
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Full-screen ExoPlayer. Live: Up/Down or CH+/CH- zap through the category, OK shows the
 * channel banner with the program guide, and dropped streams reconnect automatically.
 * VOD: standard Media3 controls, position remembered, series continue with the next episode.
 */
@OptIn(UnstableApi::class)
class PlayerActivity : ComponentActivity() {

    private lateinit var player: ExoPlayer
    private lateinit var playerView: PlayerView
    private val overlayState = mutableStateOf(OverlayState())

    private var bannerJob: Job? = null
    private var epgJob: Job? = null
    private var retryJob: Job? = null
    private var retries = 0
    private var numberJob: Job? = null
    private var noticeJob: Job? = null
    private var typedNumber = ""

    private val current: PlayItem get() = PlayQueue.items[PlayQueue.index]

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (PlayQueue.items.isEmpty()) {   // process was recreated without a queue
            finish()
            return
        }
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        player = buildPlayer()
        playerView = PlayerView(this).apply {
            player = this@PlayerActivity.player
            setShowBuffering(PlayerView.SHOW_BUFFERING_ALWAYS)
            controllerShowTimeoutMs = 4000
            setShowSubtitleButton(true)
            isFocusable = true
            isFocusableInTouchMode = true
            setControllerVisibilityListener(PlayerView.ControllerVisibilityListener { visibility ->
                overlayState.value = overlayState.value.copy(controlsVisible = visibility == android.view.View.VISIBLE)
            })
        }
        val overlayView = ComposeView(this).apply {
            setContent { PlayerTheme { PlayerOverlay(overlayState.value, onTap = ::showBanner, onSwipe = ::zap, onBack = ::finish, onRetry = ::retryAfterFailure) } }
        }
        setContentView(FrameLayout(this).apply {
            addView(playerView, ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
            addView(overlayView, ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        })
        playerView.requestFocus()

        play(PlayQueue.index, PlayQueue.startPositionMs)
    }

    private fun buildPlayer(): ExoPlayer {
        val dataSource = OkHttpDataSource.Factory(Graph.http).setUserAgent(BuildConfig.USER_AGENT)
        // Many IPTV TS streams start without an IDR frame or with odd access units.
        val extractors = DefaultExtractorsFactory().setTsExtractorFlags(
            DefaultTsPayloadReaderFactory.FLAG_ALLOW_NON_IDR_KEYFRAMES or DefaultTsPayloadReaderFactory.FLAG_DETECT_ACCESS_UNITS
        )
        val renderers = DefaultRenderersFactory(this)
            .setEnableDecoderFallback(true)
            .setExtensionRendererMode(DefaultRenderersFactory.EXTENSION_RENDERER_MODE_PREFER)
        return ExoPlayer.Builder(this, renderers)
            .setMediaSourceFactory(DefaultMediaSourceFactory(dataSource, extractors))
            .setAudioAttributes(AudioAttributes.Builder().setUsage(C.USAGE_MEDIA).setContentType(C.AUDIO_CONTENT_TYPE_MOVIE).build(), true)
            .build()
            .apply { addListener(listener) }
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
        player.setMediaItem(mediaItem, startPositionMs)
        player.prepare()
        player.playWhenReady = true

        playerView.useController = !item.isLive
        overlayState.value = OverlayState(item = item, loading = true)
        if (startPositionMs > 0) showNotice(getString(R.string.player_resuming, formatDuration(startPositionMs)))
        if (item.isLive) {
            showBanner()
            loadEpg(item)
        }
    }

    private fun zap(step: Int) {
        val size = PlayQueue.items.size
        if (size > 1) play(Math.floorMod(PlayQueue.index + step, size))
    }

    private fun showBanner() {
        overlayState.value = overlayState.value.copy(showBanner = true)
        bannerJob?.cancel()
        bannerJob = lifecycleScope.launch {
            delay(5000)
            overlayState.value = overlayState.value.copy(showBanner = false)
        }
    }

    private fun loadEpg(item: PlayItem) {
        epgJob?.cancel()
        val streamId = item.epgStreamId ?: return
        epgJob = lifecycleScope.launch {
            delay(300)   // skip channels the user zaps past quickly
            val epg = runCatching { Graph.xtream?.shortEpg(streamId) }.getOrNull().orEmpty()
            if (current == item) overlayState.value = overlayState.value.copy(epg = epg)
        }
    }

    /** Short message top-right for a few seconds (resume point, next episode). */
    private fun showNotice(text: String) {
        overlayState.value = overlayState.value.copy(notice = text)
        noticeJob?.cancel()
        noticeJob = lifecycleScope.launch {
            delay(3500)
            overlayState.value = overlayState.value.copy(notice = null)
        }
    }

    /** After a playback failure: try the same item again from where it stopped. */
    private fun retryAfterFailure() {
        retries = 0
        playerView.useController = !current.isLive
        overlayState.value = overlayState.value.copy(failed = false, loading = true, message = null)
        if (current.isLive) player.seekToDefaultPosition()
        player.prepare()
        player.play()
    }

    private val listener = object : Player.Listener {
        override fun onIsPlayingChanged(isPlaying: Boolean) {
            if (isPlaying) {
                retries = 0
                overlayState.value = overlayState.value.copy(message = null, loading = false, failed = false)
            }
        }

        override fun onPlaybackStateChanged(state: Int) {
            if (state == Player.STATE_READY && overlayState.value.loading) {
                overlayState.value = overlayState.value.copy(loading = false)
            }
            if (state == Player.STATE_ENDED && !current.isLive) {
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
                overlayState.value = overlayState.value.copy(message = getString(R.string.player_reconnecting))
                retryJob = lifecycleScope.launch {
                    delay(2000L * retries)
                    val position = if (current.isLive) C.TIME_UNSET else player.currentPosition
                    if (position == C.TIME_UNSET) player.seekToDefaultPosition() else player.seekTo(position)
                    player.prepare()
                    player.play()
                }
            } else {
                // Give the user a way out: retry or go back (tap, or OK / Back on the remote).
                playerView.useController = false   // so OK reaches onKeyDown instead of the VOD controls
                playerView.hideController()
                overlayState.value = overlayState.value.copy(message = null, loading = false, failed = true)
            }
        }
    }

    /** Live: digits typed on the remote jump to that channel number after a short pause. */
    private fun typeDigit(digit: Int) {
        if (typedNumber.length >= 4) typedNumber = ""
        typedNumber += digit
        overlayState.value = overlayState.value.copy(typedNumber = typedNumber)
        numberJob?.cancel()
        numberJob = lifecycleScope.launch {
            delay(1500)
            val number = typedNumber.toIntOrNull()
            typedNumber = ""
            overlayState.value = overlayState.value.copy(typedNumber = null)
            val index = PlayQueue.items.indexOfFirst { it.number == number }
            if (index >= 0 && index != PlayQueue.index) play(index)
        }
    }

    // Live only: PlayerView has no controller then, so D-pad keys reach the activity.
    // VOD keys are consumed earlier by PlayerView's own controls.
    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        if (::player.isInitialized && overlayState.value.failed &&
            (keyCode == KeyEvent.KEYCODE_DPAD_CENTER || keyCode == KeyEvent.KEYCODE_ENTER)
        ) {
            retryAfterFailure()
            return true
        }
        if (::player.isInitialized && current.isLive) {
            if (keyCode in KeyEvent.KEYCODE_0..KeyEvent.KEYCODE_9) {
                typeDigit(keyCode - KeyEvent.KEYCODE_0)
                return true
            }
            when (keyCode) {
                KeyEvent.KEYCODE_DPAD_UP, KeyEvent.KEYCODE_CHANNEL_UP -> { zap(+1); return true }
                KeyEvent.KEYCODE_DPAD_DOWN, KeyEvent.KEYCODE_CHANNEL_DOWN -> { zap(-1); return true }
                KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER, KeyEvent.KEYCODE_INFO,
                KeyEvent.KEYCODE_DPAD_LEFT, KeyEvent.KEYCODE_DPAD_RIGHT -> { showBanner(); return true }
            }
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
}
