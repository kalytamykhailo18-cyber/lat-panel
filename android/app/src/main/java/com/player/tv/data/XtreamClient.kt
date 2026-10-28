package com.player.tv.data

import android.util.Base64
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.ConcurrentHashMap

/**
 * Xtream Codes player API client for one account. Lists are cached in memory for the session;
 * [clearCache] forces a reload.
 */
class XtreamClient(private val http: OkHttpClient, val account: Account, initialUserInfo: UserInfo? = null) {

    private val base: HttpUrl = normalizeServerUrl(account.serverUrl)?.toHttpUrlOrNull()
        ?: throw AppException(AppException.Kind.AUTH, "Invalid server URL")

    private val cache = ConcurrentHashMap<String, Any>()

    var userInfo: UserInfo? = initialUserInfo
        private set

    fun clearCache() = cache.clear()

    suspend fun authenticate(): UserInfo {
        val root = call(null) as? JsonObject ?: throw AppException(AppException.Kind.AUTH)
        val user = root.obj("user_info") ?: throw AppException(AppException.Kind.AUTH)
        if (user.int("auth") == 0) throw AppException(AppException.Kind.AUTH)
        val status = user.str("status") ?: "Active"
        if (!status.equals("Active", ignoreCase = true)) throw AppException(AppException.Kind.AUTH, status)
        val info = UserInfo(
            status = status,
            expiresAt = user.long("exp_date")?.takeIf { it > 0 },
            maxConnections = user.int("max_connections"),
            activeConnections = user.int("active_cons"),
            allowedFormats = user.arr("allowed_output_formats")?.mapNotNull { (it as? kotlinx.serialization.json.JsonPrimitive)?.content }.orEmpty(),
        )
        userInfo = info
        return info
    }

    // ---- Live ----------------------------------------------------------------------------------

    suspend fun liveCategories(): List<Category> = cached("live_cat") { categories("get_live_categories") }

    suspend fun liveChannels(categoryId: String? = null): List<LiveChannel> = cached("live:$categoryId") {
        call("get_live_streams", categoryId?.let { mapOf("category_id" to it) }.orEmpty()).objects()
            .mapNotNull { o ->
                val id = o.int("stream_id") ?: return@mapNotNull null
                LiveChannel(id, o.int("num") ?: 0, o.str("name") ?: "#$id", o.str("stream_icon"), o.str("category_id"))
            }
            .sortedBy { it.number }
    }

    suspend fun shortEpg(streamId: Int): List<EpgEntry> = epg(streamId, 2)

    /** Upcoming programs from now on (the current one first); used by the guide grid. */
    suspend fun epg(streamId: Int, limit: Int): List<EpgEntry> =
        (call("get_short_epg", mapOf("stream_id" to streamId.toString(), "limit" to limit.toString())) as? JsonObject)
            ?.arr("epg_listings")?.objects().orEmpty()
            .mapNotNull { o ->
                val start = o.long("start_timestamp") ?: return@mapNotNull null
                val end = o.long("stop_timestamp") ?: return@mapNotNull null
                EpgEntry(decodeBase64(o.str("title")) ?: return@mapNotNull null, decodeBase64(o.str("description")), start, end)
            }

    /** HLS when the user prefers it or when the provider does not offer MPEG-TS for this account. */
    fun liveUrl(channel: LiveChannel, preferHls: Boolean): String {
        val formats = userInfo?.allowedFormats.orEmpty()
        val hls = preferHls || (formats.isNotEmpty() && formats.none { it.equals("ts", ignoreCase = true) })
        return streamUrl("live", channel.streamId.toString(), if (hls) "m3u8" else "ts")
    }

    // ---- Movies --------------------------------------------------------------------------------

    suspend fun movieCategories(): List<Category> = cached("vod_cat") { categories("get_vod_categories") }

    suspend fun movies(categoryId: String? = null): List<Movie> = cached("vod:$categoryId") {
        call("get_vod_streams", categoryId?.let { mapOf("category_id" to it) }.orEmpty()).objects().mapNotNull(::parseMovie)
    }

    suspend fun movieInfo(streamId: Int): MovieInfo = cached("vod_info:$streamId") {
        val listed = cache.values.asSequence()
            .filterIsInstance<List<*>>().flatMap { it.asSequence() }
            .filterIsInstance<Movie>().firstOrNull { it.streamId == streamId }
        val root = call("get_vod_info", mapOf("vod_id" to streamId.toString())) as? JsonObject
        val info = root?.obj("info") ?: JsonObject(emptyMap())
        val data = root?.obj("movie_data")
        if (listed == null && data == null) throw AppException(AppException.Kind.SERVER, "Movie not found")
        MovieInfo(
            movie = Movie(
                streamId = streamId,
                name = listed?.name ?: data?.str("name") ?: info.str("name") ?: "",
                poster = listed?.poster ?: info.str("movie_image") ?: info.str("cover_big"),
                rating = listed?.rating ?: info.str("rating"),
                categoryId = listed?.categoryId ?: data?.str("category_id"),
                extension = data?.str("container_extension") ?: listed?.extension ?: "mp4",
            ),
            plot = info.str("plot") ?: info.str("description"),
            genre = info.str("genre"),
            cast = info.str("cast") ?: info.str("actors"),
            director = info.str("director"),
            releaseDate = info.str("releasedate") ?: info.str("release_date"),
            duration = info.str("duration"),
            backdrop = info.firstString("backdrop_path"),
        )
    }

    fun movieUrl(movie: Movie): String = streamUrl("movie", movie.streamId.toString(), movie.extension)

    // ---- Series --------------------------------------------------------------------------------

    suspend fun seriesCategories(): List<Category> = cached("series_cat") { categories("get_series_categories") }

    suspend fun series(categoryId: String? = null): List<Series> = cached("series:$categoryId") {
        call("get_series", categoryId?.let { mapOf("category_id" to it) }.orEmpty()).objects().mapNotNull(::parseSeries)
    }

    suspend fun seriesInfo(seriesId: Int): SeriesInfo = cached("series_info:$seriesId") {
        val root = call("get_series_info", mapOf("series_id" to seriesId.toString())) as? JsonObject
            ?: throw AppException(AppException.Kind.SERVER)
        val info = root.obj("info") ?: JsonObject(emptyMap())
        val listed = cache.values.asSequence()
            .filterIsInstance<List<*>>().flatMap { it.asSequence() }
            .filterIsInstance<Series>().firstOrNull { it.seriesId == seriesId }
        val series = Series(seriesId, info.str("name") ?: listed?.name ?: "", info.str("cover") ?: listed?.cover,
            info.str("rating") ?: listed?.rating, info.str("category_id") ?: listed?.categoryId)
        val episodesBySeason = root.obj("episodes")?.entries.orEmpty().mapNotNull { (season, list) ->
            val number = season.toIntOrNull() ?: return@mapNotNull null
            val episodes = list.objects().mapNotNull { e ->
                val id = e.str("id") ?: return@mapNotNull null
                val details = e.obj("info")
                Episode(
                    id = id,
                    number = e.int("episode_num") ?: 0,
                    title = e.str("title") ?: "",
                    extension = e.str("container_extension") ?: "mp4",
                    plot = details?.str("plot"),
                    image = details?.str("movie_image"),
                    duration = details?.str("duration"),
                )
            }.sortedBy { it.number }
            Season(number, episodes)
        }.sortedBy { it.number }
        SeriesInfo(
            series = series,
            plot = info.str("plot"),
            genre = info.str("genre"),
            cast = info.str("cast"),
            releaseDate = info.str("releaseDate") ?: info.str("release_date"),
            backdrop = info.firstString("backdrop_path"),
            seasons = episodesBySeason,
        )
    }

    fun episodeUrl(episode: Episode): String = streamUrl("series", episode.id, episode.extension)

    // ---- Internals -----------------------------------------------------------------------------

    private suspend fun categories(action: String): List<Category> =
        call(action).objects().mapNotNull { o ->
            Category(o.str("category_id") ?: return@mapNotNull null, o.str("category_name") ?: "—")
        }

    private fun parseMovie(o: JsonObject): Movie? {
        val id = o.int("stream_id") ?: return null
        return Movie(id, o.str("name") ?: "#$id", o.str("stream_icon"), o.str("rating"), o.str("category_id"),
            o.str("container_extension") ?: "mp4", o.long("added") ?: 0)
    }

    private fun parseSeries(o: JsonObject): Series? {
        val id = o.int("series_id") ?: return null
        return Series(id, o.str("name") ?: "#$id", o.str("cover"), o.str("rating"), o.str("category_id"),
            o.long("last_modified") ?: 0, o.str("plot"))
    }

    private suspend fun call(action: String?, params: Map<String, String> = emptyMap()): JsonElement {
        val url = base.newBuilder()
            .addPathSegment("player_api.php")
            .addQueryParameter("username", account.username)
            .addQueryParameter("password", account.password)
            .apply {
                action?.let { addQueryParameter("action", it) }
                params.forEach { (k, v) -> addQueryParameter(k, v) }
            }
            .build()
        val body = http.fetch(Request.Builder().url(url).build())
        if (body.isBlank()) throw AppException(if (action == null) AppException.Kind.AUTH else AppException.Kind.SERVER)
        return try {
            withContext(Dispatchers.Default) { parseJson(body) }   // full catalogs can be megabytes
        } catch (e: SerializationException) {
            throw AppException(if (action == null) AppException.Kind.AUTH else AppException.Kind.SERVER, "Invalid JSON", e)
        }
    }

    private fun streamUrl(kind: String, id: String, extension: String): String =
        base.newBuilder()
            .addPathSegment(kind)
            .addPathSegment(account.username)
            .addPathSegment(account.password)
            .addPathSegment("$id.$extension")
            .build()
            .toString()

    @Suppress("UNCHECKED_CAST")
    private suspend fun <T : Any> cached(key: String, load: suspend () -> T): T =
        cache[key] as? T ?: withContext(Dispatchers.Default) { load() }.also { cache[key] = it }

    private fun decodeBase64(text: String?): String? =
        text?.let { runCatching { String(Base64.decode(it, Base64.DEFAULT)) }.getOrDefault(it) }?.trim()?.takeIf { it.isNotEmpty() }

    companion object {
        /** Accepts "host:port", "http://host:port/" or a pasted player_api.php / get.php URL. */
        fun normalizeServerUrl(input: String): String? {
            var url = input.trim()
            if (url.isEmpty()) return null
            if (!url.startsWith("http://", true) && !url.startsWith("https://", true)) url = "http://$url"
            val parsed = url.toHttpUrlOrNull() ?: return null
            val segments = parsed.encodedPathSegments.filter { it.isNotEmpty() }
                .let { if (it.lastOrNull()?.endsWith(".php") == true) it.dropLast(1) else it }
            return parsed.newBuilder().query(null).fragment(null)
                .encodedPath("/" + segments.joinToString("/"))
                .build().toString().trimEnd('/')
        }
    }
}
