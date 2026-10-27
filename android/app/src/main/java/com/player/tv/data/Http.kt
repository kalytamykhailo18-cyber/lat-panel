package com.player.tv.data

import kotlinx.coroutines.suspendCancellableCoroutine
import okhttp3.Call
import okhttp3.Callback
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import java.io.IOException
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/** Executes the request and returns the body; cancelling the coroutine cancels the HTTP call. */
internal suspend fun OkHttpClient.fetch(request: Request, errorBody: ((Response, String) -> Nothing)? = null): String =
    suspendCancellableCoroutine { cont ->
        val call = newCall(request)
        cont.invokeOnCancellation { call.cancel() }
        call.enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                cont.resumeWithException(AppException(AppException.Kind.NETWORK, e.message, e))
            }

            override fun onResponse(call: Call, response: Response) {
                try {
                    val body = response.use { it.body?.string().orEmpty() }
                    if (!response.isSuccessful) {
                        errorBody?.invoke(response, body)
                        val kind = if (response.code == 401 || response.code == 403) AppException.Kind.AUTH else AppException.Kind.SERVER
                        throw AppException(kind, "HTTP ${response.code}")
                    }
                    cont.resume(body)
                } catch (e: AppException) {
                    cont.resumeWithException(e)
                } catch (e: IOException) {
                    cont.resumeWithException(AppException(AppException.Kind.NETWORK, e.message, e))
                }
            }
        })
    }
