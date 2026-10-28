package com.player.tv.ui.player

import android.content.Context
import android.graphics.Color
import androidx.annotation.OptIn
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.Format
import androidx.media3.common.MimeTypes
import androidx.media3.common.TrackSelectionOverride
import androidx.media3.common.Tracks
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.extractor.DefaultExtractorsFactory
import androidx.media3.extractor.ts.DefaultTsPayloadReaderFactory
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.CaptionStyleCompat
import androidx.media3.ui.PlayerView
import androidx.media3.ui.SubtitleView
import com.player.tv.BuildConfig
import com.player.tv.data.Graph
import java.util.Locale

/** One entry of the audio / subtitle menus. */
data class TrackOption(val label: String, val selected: Boolean, internal val group: Tracks.Group?, internal val index: Int)

/** ExoPlayer setup shared by the full-screen player and the live preview. */
@OptIn(UnstableApi::class)
object Players {

    fun create(context: Context): ExoPlayer {
        val dataSource = OkHttpDataSource.Factory(Graph.http).setUserAgent(BuildConfig.USER_AGENT)
        // Many IPTV TS streams start without an IDR frame or with odd access units.
        val extractors = DefaultExtractorsFactory().setTsExtractorFlags(
            DefaultTsPayloadReaderFactory.FLAG_ALLOW_NON_IDR_KEYFRAMES or DefaultTsPayloadReaderFactory.FLAG_DETECT_ACCESS_UNITS
        )
        val renderers = DefaultRenderersFactory(context)
            .setEnableDecoderFallback(true)
            .setExtensionRendererMode(DefaultRenderersFactory.EXTENSION_RENDERER_MODE_PREFER)
        return ExoPlayer.Builder(context, renderers)
            .setMediaSourceFactory(DefaultMediaSourceFactory(dataSource, extractors))
            .setAudioAttributes(AudioAttributes.Builder().setUsage(C.USAGE_MEDIA).setContentType(C.AUDIO_CONTENT_TYPE_MOVIE).build(), true)
            .build()
            .also(::applySubtitlePreference)
    }

    /** Subtitles off, or on in the preferred language when the content has it (Ajustes > Reproductor). */
    fun applySubtitlePreference(player: ExoPlayer) {
        val language = Graph.store.subtitleLanguage
        player.trackSelectionParameters = player.trackSelectionParameters.buildUpon().apply {
            if (language == "off") {
                setTrackTypeDisabled(C.TRACK_TYPE_TEXT, true)
            } else {
                setTrackTypeDisabled(C.TRACK_TYPE_TEXT, false)
                setPreferredTextLanguage(language)
            }
        }.build()
    }

    /** White text with an outline on a soft dark box: readable on any image, size from Ajustes. */
    fun styleSubtitles(view: PlayerView) {
        view.subtitleView?.apply {
            setApplyEmbeddedStyles(false)
            setStyle(CaptionStyleCompat(Color.WHITE, 0x80000000.toInt(), Color.TRANSPARENT, CaptionStyleCompat.EDGE_TYPE_OUTLINE, Color.BLACK, null))
            setFractionalTextSize(SubtitleView.DEFAULT_TEXT_SIZE_FRACTION * SIZE_FACTORS[Graph.store.subtitleSize.coerceIn(SIZE_FACTORS.indices)])
        }
    }

    private val SIZE_FACTORS = listOf(0.8f, 1f, 1.35f)

    fun tracks(player: ExoPlayer, type: Int, fallbackLabel: (Int) -> String): List<TrackOption> {
        val options = mutableListOf<TrackOption>()
        player.currentTracks.groups.filter { it.type == type }.forEach { group ->
            for (i in 0 until group.length) {
                if (!group.isTrackSupported(i)) continue
                val label = trackLabel(group.getTrackFormat(i)) ?: fallbackLabel(options.size + 1)
                options += TrackOption(label, group.isTrackSelected(i), group, i)
            }
        }
        // Same language twice (e.g. two qualities of one audio): number them so they can be told apart.
        val counts = options.groupingBy { it.label }.eachCount()
        val seen = mutableMapOf<String, Int>()
        return options.map { o ->
            if (counts.getValue(o.label) == 1) o
            else {
                val n = (seen[o.label] ?: 0) + 1
                seen[o.label] = n
                o.copy(label = "${o.label} $n")
            }
        }
    }

    /**
     * Applies a choice from the Audio / Subtitles menu. The language also becomes the preferred
     * one, so the choice carries over to the next channel or episode while the player is open.
     */
    fun select(player: ExoPlayer, type: Int, option: TrackOption?) {
        player.trackSelectionParameters = player.trackSelectionParameters.buildUpon().apply {
            if (option?.group == null) {
                clearOverridesOfType(type)
                setTrackTypeDisabled(type, true)
            } else {
                setTrackTypeDisabled(type, false)
                setOverrideForType(TrackSelectionOverride(option.group.mediaTrackGroup, option.index))
                option.group.getTrackFormat(option.index).language?.takeIf { it != "und" }?.let {
                    if (type == C.TRACK_TYPE_TEXT) setPreferredTextLanguage(it) else setPreferredAudioLanguage(it)
                }
            }
        }.build()
    }

    /** Overrides point at the previous stream's tracks; the preferred languages stay. */
    fun clearOverrides(player: ExoPlayer) {
        player.trackSelectionParameters = player.trackSelectionParameters.buildUpon()
            .clearOverridesOfType(C.TRACK_TYPE_AUDIO)
            .clearOverridesOfType(C.TRACK_TYPE_TEXT)
            .build()
    }

    val SCREEN_MODES = listOf(AspectRatioFrameLayout.RESIZE_MODE_FIT, AspectRatioFrameLayout.RESIZE_MODE_FILL, AspectRatioFrameLayout.RESIZE_MODE_ZOOM)

    fun applyScreenMode(view: PlayerView, mode: Int = Graph.store.screenMode) {
        view.resizeMode = SCREEN_MODES[mode.coerceIn(SCREEN_MODES.indices)]
    }

    private fun trackLabel(format: Format): String? {
        val language = format.language?.takeIf { it.isNotBlank() && it != "und" }?.let {
            Locale.forLanguageTag(it).getDisplayLanguage(Locale.getDefault()).replaceFirstChar { c -> c.titlecase(Locale.getDefault()) }
        }
        // Streams often label tracks with the English name ("Spanish; Castilian"): already said by the language.
        val english = format.language?.let { Locale.forLanguageTag(it).getDisplayLanguage(Locale.ENGLISH) }.orEmpty()
        val label = format.label?.takeIf { it.isNotBlank() && !(english.isNotEmpty() && it.startsWith(english, ignoreCase = true)) }
        val channels = when (format.channelCount) {
            Format.NO_VALUE, 0 -> null
            1 -> "Mono"
            2 -> "Estéreo"
            6 -> "5.1"
            8 -> "7.1"
            else -> "${format.channelCount} ch"
        }
        val name = when {
            language != null && label != null && !label.contains(language, ignoreCase = true) && !language.contains(label, ignoreCase = true) -> "$language ($label)"
            language != null -> language
            else -> label
        } ?: return null
        val mime = format.sampleMimeType.orEmpty()
        return when {
            mime.startsWith("audio/") && channels != null -> "$name · $channels"
            mime == MimeTypes.APPLICATION_CEA608 || mime == MimeTypes.APPLICATION_CEA708 -> "$name (CC)"   // captions inside the video
            else -> name
        }
    }
}
