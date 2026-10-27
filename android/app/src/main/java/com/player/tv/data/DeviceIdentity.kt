package com.player.tv.data

import android.annotation.SuppressLint
import android.content.Context
import android.provider.Settings
import java.security.MessageDigest
import java.util.UUID

/**
 * Stable device identity used for remote activation.
 *
 * The MAC address cannot be used: since Android 6 apps receive a fixed fake value
 * (02:00:00:00:00:00) and recent TVs randomize it. Instead both values are derived from
 * Settings.Secure.ANDROID_ID, which is unique per device + signing key and survives reboots,
 * app updates and reinstalls (it only changes after a factory reset).
 *
 * - [deviceId] is public: shown on screen and typed into the panel (formatted like a MAC).
 * - [deviceKey] is secret: sent to the panel, which binds it to the ID on first contact, so
 *   someone who only knows a customer's ID cannot pull that customer's credentials.
 */
class DeviceIdentity(context: Context) {

    private val seed: String = readSeed(context)

    val deviceId: String = sha256("device-id:$seed").take(12).uppercase().chunked(2).joinToString(":")

    val deviceKey: String = sha256("device-key:$seed")

    @SuppressLint("HardwareIds")
    private fun readSeed(context: Context): String {
        val androidId = Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID)
        if (!androidId.isNullOrBlank() && androidId !in BROKEN_ANDROID_IDS) return androidId

        // Fallback for devices without a usable ANDROID_ID: a random seed kept in app storage.
        val prefs = context.getSharedPreferences("identity", Context.MODE_PRIVATE)
        return prefs.getString("seed", null) ?: UUID.randomUUID().toString().also {
            prefs.edit().putString("seed", it).apply()
        }
    }

    private fun sha256(text: String): String =
        MessageDigest.getInstance("SHA-256").digest(text.toByteArray()).joinToString("") { "%02x".format(it) }

    private companion object {
        val BROKEN_ANDROID_IDS = setOf("9774d56d682e549c", "0000000000000000")
    }
}
