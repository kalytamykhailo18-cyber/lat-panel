package com.player.tv.data

import android.content.Context
import com.player.tv.BuildConfig
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

/** Manual dependency container; initialised once in App.onCreate. */
object Graph {
    lateinit var http: OkHttpClient
        private set
    lateinit var identity: DeviceIdentity
        private set
    lateinit var store: SessionStore
        private set
    lateinit var panel: PanelApi
        private set

    /** Client for the account currently signed in; null before sign-in. */
    var xtream: XtreamClient? = null
        private set

    fun init(context: Context) {
        http = OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .addInterceptor { chain ->
                chain.proceed(chain.request().newBuilder().header("User-Agent", BuildConfig.USER_AGENT).build())
            }
            .build()
        identity = DeviceIdentity(context)
        store = SessionStore(context)
        panel = PanelApi(http, identity.deviceId, identity.deviceKey)
    }

    /** Validates the account against the provider and makes it the current session. */
    suspend fun signIn(account: Account): UserInfo {
        val client = if (xtream?.account == account) xtream!! else XtreamClient(http, account)
        val info = client.authenticate()
        xtream = client
        when (account.source) {
            Account.Source.DEVICE -> {
                store.deviceAccount = account
                store.mode = SessionStore.Mode.DEVICE
            }
            Account.Source.MANUAL -> {
                store.manualAccount = account
                store.mode = SessionStore.Mode.MANUAL
            }
        }
        return info
    }

    /**
     * Manual Xtream login with only username and password. The operator's DNS are tried one at a
     * time, never in parallel (simultaneous logins trigger the providers' firewalls): first the
     * DNS that worked last time for this user, then the panel's list in its configured order.
     * Each DNS gets at most the panel's timeout (1 s by default) before moving to the next one.
     * [onProgress] reports (current, total) for the "searching your server" message.
     */
    suspend fun signInManual(username: String, password: String, onProgress: (Int, Int) -> Unit = { _, _ -> }): UserInfo {
        val remembered = store.manualAccount?.takeIf { it.username == username }?.serverUrl
        val fromPanel = try {
            panel.dnsServers()
        } catch (e: AppException) {
            if (remembered == null) throw e
            DnsList(emptyList(), DnsList.DEFAULT_TIMEOUT_MS)   // panel unreachable: the remembered DNS can still be tried
        }
        val servers = (listOfNotNull(remembered) + fromPanel.servers).distinct()
        val probeHttp = probeClient(fromPanel.timeoutMs)

        var rejected = false
        var lastError: AppException? = null
        servers.forEachIndexed { index, url ->
            onProgress(index + 1, servers.size)
            val account = Account(url, username, password, Account.Source.MANUAL)
            try {
                val info = XtreamClient(probeHttp, account).authenticate()
                xtream = XtreamClient(http, account, info)
                store.manualAccount = account
                store.mode = SessionStore.Mode.MANUAL
                return info
            } catch (e: AppException) {
                if (e.kind == AppException.Kind.AUTH) rejected = true
                lastError = e
            }
        }
        // Wrong credentials if any server answered; otherwise report why none could be reached.
        throw if (rejected || lastError == null) AppException(AppException.Kind.AUTH) else lastError!!
    }

    /** Strict time limit per DNS while searching: connection, response and total call. */
    private fun probeClient(timeoutMs: Long): OkHttpClient = http.newBuilder()
        .connectTimeout(timeoutMs, TimeUnit.MILLISECONDS)
        .readTimeout(timeoutMs, TimeUnit.MILLISECONDS)
        .callTimeout(timeoutMs, TimeUnit.MILLISECONDS)
        .build()

    /** Uses an account without contacting the provider (offline start with cached credentials). */
    fun useCached(account: Account) {
        if (xtream?.account != account) xtream = XtreamClient(http, account)
    }

    fun signOut() {
        xtream = null
    }

    fun requireXtream(): XtreamClient = xtream ?: error("Not signed in")
}
