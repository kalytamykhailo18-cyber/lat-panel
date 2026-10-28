package com.player.tv.data

/** Xtream Codes credentials. DEVICE accounts come from the panel, MANUAL ones are typed by the user. */
data class Account(
    val serverUrl: String,
    val username: String,
    val password: String,
    val source: Source,
) {
    enum class Source { DEVICE, MANUAL }
}

data class UserInfo(
    val status: String,
    val expiresAt: Long?,           // epoch seconds, null = unlimited
    val maxConnections: Int?,
    val activeConnections: Int?,
    val allowedFormats: List<String>,
)

data class Category(val id: String, val name: String)

data class LiveChannel(
    val streamId: Int,
    val number: Int,
    val name: String,
    val logo: String?,
    val categoryId: String?,
)

data class Movie(
    val streamId: Int,
    val name: String,
    val poster: String?,
    val rating: String?,
    val categoryId: String?,
    val extension: String,
    val added: Long = 0,            // epoch seconds; newest first on the home screen
)

data class MovieInfo(
    val movie: Movie,
    val plot: String?,
    val genre: String?,
    val cast: String?,
    val director: String?,
    val releaseDate: String?,
    val duration: String?,
    val backdrop: String?,
)

data class Series(
    val seriesId: Int,
    val name: String,
    val cover: String?,
    val rating: String?,
    val categoryId: String?,
    val lastModified: Long = 0,     // epoch seconds; recently updated first on the home screen
    val plot: String? = null,
)

data class SeriesInfo(
    val series: Series,
    val plot: String?,
    val genre: String?,
    val cast: String?,
    val releaseDate: String?,
    val backdrop: String?,
    val seasons: List<Season>,
)

data class Season(val number: Int, val episodes: List<Episode>)

data class Episode(
    val id: String,
    val number: Int,
    val title: String,
    val extension: String,
    val plot: String?,
    val image: String?,
    val duration: String?,
)

data class EpgEntry(val title: String, val description: String?, val start: Long, val end: Long)

/** Entry of the "continue watching" row. [route] opens the movie or series detail screen. */
data class RecentItem(
    val key: String,
    val title: String,
    val image: String?,
    val route: String,
    val positionMs: Long = 0,
    val durationMs: Long = 0,
)

/** Operator DNS for the manual login, in the order to try them, and the time limit per DNS. */
data class DnsList(val servers: List<String>, val timeoutMs: Long) {
    companion object {
        const val DEFAULT_TIMEOUT_MS = 1000L
    }
}

data class PanelMessage(val id: Int, val title: String, val body: String)

data class PanelStatus(
    val status: Status,
    val pollSeconds: Int,
    val account: Account?,
    val messages: List<PanelMessage>,
) {
    enum class Status { PENDING, ACTIVE, DISABLED, EXPIRED }
}

/** Errors shown to the user; [kind] selects the message. */
class AppException(val kind: Kind, message: String? = null, cause: Throwable? = null) : Exception(message, cause) {
    enum class Kind { NETWORK, AUTH, SERVER, KEY_MISMATCH }
}
