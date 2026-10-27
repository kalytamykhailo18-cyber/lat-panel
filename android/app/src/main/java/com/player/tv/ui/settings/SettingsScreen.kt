package com.player.tv.ui.settings

import android.text.format.DateFormat
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.player.tv.ui.components.Button
import androidx.tv.material3.MaterialTheme
import com.player.tv.ui.components.OutlinedButton
import androidx.tv.material3.Text
import com.player.tv.BuildConfig
import com.player.tv.R
import com.player.tv.data.Account
import com.player.tv.data.Graph
import com.player.tv.ui.components.AppBackground
import com.player.tv.ui.components.Pill
import com.player.tv.ui.components.ScreenHeader
import com.player.tv.ui.components.requestWhenReady
import com.player.tv.ui.components.screenInfo
import com.player.tv.ui.theme.Brand
import java.util.Date

/** TV: information on the left, options and actions on the right. Smaller screens: one scrolling column. */
@Composable
fun SettingsScreen(onSwitchToManual: () -> Unit, onSwitchToDevice: () -> Unit) {
    val context = LocalContext.current
    val xtream = Graph.xtream
    val account = xtream?.account
    val user = xtream?.userInfo
    var liveHls by remember { mutableStateOf(Graph.store.liveHls) }
    val firstFocus = remember { FocusRequester() }
    LaunchedEffect(Unit) { firstFocus.requestWhenReady() }
    val screen = screenInfo()

    AppBackground {
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = screen.gutter, vertical = if (screen.compact) 16.dp else 32.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            ScreenHeader(stringResource(R.string.home_settings))
            val info: @Composable () -> Unit = {
                    Section(stringResource(R.string.settings_account)) {
                        InfoRow(stringResource(R.string.settings_mode), stringResource(
                            if (account?.source == Account.Source.MANUAL) R.string.settings_mode_manual else R.string.settings_mode_device))
                        // The provider URL (DNS) is never shown: the app only works with the operator's servers.
                        account?.let { InfoRow(stringResource(R.string.settings_username), it.username) }
                        user?.let {
                            InfoRow(stringResource(R.string.settings_status), accountStatusLabel(it.status))
                            InfoRow(
                                stringResource(R.string.settings_expires),
                                it.expiresAt?.let { exp -> DateFormat.getMediumDateFormat(context).format(Date(exp * 1000)) }
                                    ?: stringResource(R.string.home_no_expiry),
                            )
                            if (it.maxConnections != null) {
                                InfoRow(stringResource(R.string.settings_connections), "${it.activeConnections ?: 0} / ${it.maxConnections}")
                            }
                        }
                    }
                    Section(stringResource(R.string.settings_device)) {
                        InfoRow(stringResource(R.string.settings_device_id), Graph.identity.deviceId)
                        InfoRow(stringResource(R.string.settings_version), BuildConfig.VERSION_NAME)
                    }
            }
            val options: @Composable () -> Unit = {
                    Section(stringResource(R.string.settings_playback)) {
                        Text(stringResource(R.string.settings_live_format), color = Brand.TextSecondary, style = MaterialTheme.typography.bodyMedium)
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Pill(stringResource(R.string.settings_format_ts), selected = !liveHls,
                                onClick = { liveHls = false; Graph.store.liveHls = false }, modifier = Modifier.focusRequester(firstFocus))
                            Pill(stringResource(R.string.settings_format_hls), selected = liveHls,
                                onClick = { liveHls = true; Graph.store.liveHls = true })
                        }
                    }
                    Section(stringResource(R.string.settings_actions)) {
                        Button(onClick = {
                            Graph.xtream?.clearCache()
                            Toast.makeText(context, R.string.settings_reloaded, Toast.LENGTH_SHORT).show()
                        }) { Text(stringResource(R.string.settings_reload)) }

                        if (account?.source == Account.Source.MANUAL) {
                            OutlinedButton(onClick = onSwitchToDevice) { Text(stringResource(R.string.settings_switch_device)) }
                            OutlinedButton(onClick = {
                                Graph.store.manualAccount = null
                                onSwitchToDevice()
                            }) { Text(stringResource(R.string.settings_sign_out_manual)) }
                        } else {
                            OutlinedButton(onClick = onSwitchToManual) { Text(stringResource(R.string.settings_switch_manual)) }
                        }
                    }
            }
            if (screen.expanded) {
                Row(horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(20.dp)) { info() }
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(20.dp)) { options() }
                }
            } else {
                // Options first: they are what people come here to change.
                options()
                info()
            }
        }
    }
}

/** Xtream returns the account status in English; the common values are shown translated. */
@Composable
private fun accountStatusLabel(status: String): String = when (status.lowercase()) {
    "active" -> stringResource(R.string.status_active)
    "expired" -> stringResource(R.string.status_expired)
    "banned" -> stringResource(R.string.status_banned)
    "disabled" -> stringResource(R.string.status_disabled)
    else -> status
}

@Composable
private fun Section(title: String, content: @Composable () -> Unit) {
    Column(
        Modifier.fillMaxWidth().background(Brand.Surface, RoundedCornerShape(16.dp)).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(title, style = MaterialTheme.typography.titleMedium, color = Brand.TextPrimary)
        content()
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
    Row {
        Text(label, modifier = Modifier.width(if (screenInfo().compact) 120.dp else 170.dp), color = Brand.TextSecondary, style = MaterialTheme.typography.bodyMedium)
        Text(value, color = Brand.TextPrimary, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}
