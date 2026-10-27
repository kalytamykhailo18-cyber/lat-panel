package com.player.tv.data

import android.os.Build
import com.player.tv.BuildConfig
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

/** Client for the panel endpoints in api/v1/ (see panel/docs/API.md). */
class PanelApi(private val http: OkHttpClient, private val deviceId: String, private val deviceKey: String) {

    private val base = BuildConfig.PANEL_URL.trimEnd('/') + "/api/v1/"
    private val endpoint = base + "device.php"

    /**
     * Ordered list of the operator's DNS for the manual login. The app tries them one by one
     * itself (see Graph.signInManual), so the checks come from the customer's own connection.
     */
    suspend fun dnsServers(): DnsList {
        val payload = buildJsonObject {
            put("device_id", deviceId)
            put("device_key", deviceKey)
        }
        val request = Request.Builder()
            .url(base + "dns.php")
            .post(payload.toString().toRequestBody("application/json".toMediaType()))
            .build()
        val body = http.fetch(request) { response, _ ->
            throw if (response.code == 403) AppException(AppException.Kind.KEY_MISMATCH)
            else AppException(AppException.Kind.SERVER, "HTTP ${response.code}")
        }
        val root = runCatching { parseJson(body) as? JsonObject }.getOrNull()
            ?: throw AppException(AppException.Kind.SERVER, "Panel returned invalid JSON")
        return DnsList(
            servers = root.arr("servers").orEmpty().mapNotNull { (it as? kotlinx.serialization.json.JsonPrimitive)?.content?.takeIf(String::isNotBlank) },
            timeoutMs = (root.long("timeout_ms") ?: DnsList.DEFAULT_TIMEOUT_MS).coerceIn(300, 10_000),
        )
    }

    suspend fun check(): PanelStatus {
        val payload = buildJsonObject {
            put("device_id", deviceId)
            put("device_key", deviceKey)
            put("model", "${Build.MANUFACTURER} ${Build.MODEL}".trim())
            put("app_version", BuildConfig.VERSION_NAME)
        }
        val request = Request.Builder()
            .url(endpoint)
            .post(payload.toString().toRequestBody("application/json".toMediaType()))
            .build()

        val body = http.fetch(request) { _, errorBody ->
            val error = runCatching { (parseJson(errorBody) as JsonObject).str("error") }.getOrNull()
            if (error == "key_mismatch") throw AppException(AppException.Kind.KEY_MISMATCH)
            throw AppException(AppException.Kind.SERVER, error)
        }

        val root = try {
            parseJson(body) as? JsonObject
        } catch (e: SerializationException) {
            null
        } ?: throw AppException(AppException.Kind.SERVER, "Panel returned invalid JSON")

        val status = when (root.str("status")) {
            "active" -> PanelStatus.Status.ACTIVE
            "disabled" -> PanelStatus.Status.DISABLED
            "expired" -> PanelStatus.Status.EXPIRED
            else -> PanelStatus.Status.PENDING
        }
        val account = root.obj("account")?.let { a ->
            val url = a.str("server_url") ?: return@let null
            Account(url, a.str("username").orEmpty(), a.str("password").orEmpty(), Account.Source.DEVICE)
        }
        val messages = root.arr("messages")?.objects().orEmpty().mapNotNull { m ->
            val id = m.int("id") ?: return@mapNotNull null
            PanelMessage(id, m.str("title").orEmpty(), m.str("body").orEmpty())
        }
        return PanelStatus(
            status = if (status == PanelStatus.Status.ACTIVE && account == null) PanelStatus.Status.PENDING else status,
            pollSeconds = (root.int("poll_seconds") ?: 10).coerceIn(5, 3600),
            account = account,
            messages = messages,
        )
    }
}
