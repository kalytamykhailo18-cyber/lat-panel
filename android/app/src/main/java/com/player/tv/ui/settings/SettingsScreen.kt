package com.player.tv.ui.settings

import android.os.Build
import android.text.format.DateFormat
import android.widget.Toast
import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Icon
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.SelectableSurfaceDefaults
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import com.player.tv.BuildConfig
import com.player.tv.R
import com.player.tv.data.Account
import com.player.tv.data.Category
import com.player.tv.data.Graph
import com.player.tv.data.Parental
import com.player.tv.ui.components.AppIcons
import com.player.tv.ui.components.Button
import com.player.tv.ui.components.OutlinedButton
import com.player.tv.ui.components.Pill
import com.player.tv.ui.components.PinDialog
import com.player.tv.ui.components.requestWhenReady
import com.player.tv.ui.components.tapToClick
import com.player.tv.ui.theme.Brand
import kotlinx.coroutines.launch
import java.util.Date

private enum class Section(@StringRes val title: Int, val icon: () -> ImageVector) {
    ACCOUNT(R.string.settings_account, { AppIcons.Person }),
    PLAYER(R.string.settings_playback, { AppIcons.Play }),
    PARENTAL(R.string.parental_title, { AppIcons.Lock }),
    CONTENT(R.string.settings_content, { AppIcons.Refresh }),
    DEVICE(R.string.settings_device, { AppIcons.Device }),
}

/** Sections on the left (chosen as focus moves over them), their options on the right. */
@Composable
fun SettingsScreen(onSwitchToManual: () -> Unit, onSwitchToDevice: () -> Unit) {
    var section by remember { mutableStateOf(Section.ACCOUNT) }
    val firstFocus = remember { FocusRequester() }
    LaunchedEffect(Unit) { firstFocus.requestWhenReady() }

    Row(Modifier.fillMaxSize().padding(start = 20.dp, top = 24.dp, end = 32.dp, bottom = 24.dp), horizontalArrangement = Arrangement.spacedBy(28.dp)) {
        Column(Modifier.width(250.dp).fillMaxHeight().focusRestorer(firstFocus), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(stringResource(R.string.home_settings), style = MaterialTheme.typography.headlineSmall, color = Brand.TextPrimary, modifier = Modifier.padding(start = 8.dp, bottom = 10.dp))
            Section.entries.forEach { s ->
                val selected = s == section
                Surface(
                    selected = selected,
                    onClick = { section = s },
                    modifier = Modifier.fillMaxWidth()
                        .then(if (s == Section.ACCOUNT) Modifier.focusRequester(firstFocus) else Modifier)
                        .onFocusChanged { if (it.isFocused) section = s }
                        .tapToClick({ section = s }),
                    shape = SelectableSurfaceDefaults.shape(RoundedCornerShape(10.dp)),
                    scale = SelectableSurfaceDefaults.scale(focusedScale = 1.02f),
                    colors = SelectableSurfaceDefaults.colors(
                        containerColor = Color.Transparent,
                        contentColor = Brand.TextSecondary,
                        selectedContainerColor = Brand.SurfaceHigh,
                        selectedContentColor = Brand.TextPrimary,
                        focusedContainerColor = Brand.Primary,
                        focusedContentColor = Color.White,
                        focusedSelectedContainerColor = Brand.Primary,
                        focusedSelectedContentColor = Color.White,
                    ),
                ) {
                    Row(Modifier.padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                        Icon(s.icon(), contentDescription = null, modifier = Modifier.size(22.dp))
                        Text(stringResource(s.title), style = MaterialTheme.typography.titleMedium, fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal)
                    }
                }
            }
        }
        Box(Modifier.weight(1f).fillMaxHeight()) {
            when (section) {
                Section.ACCOUNT -> AccountSection(onSwitchToManual, onSwitchToDevice)
                Section.PLAYER -> PlayerSection()
                Section.PARENTAL -> ParentalSection()
                Section.CONTENT -> ContentSection()
                Section.DEVICE -> DeviceSection()
            }
        }
    }
}

@Composable
private fun SectionColumn(title: String, content: @Composable () -> Unit) {
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(top = 48.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        Text(title, style = MaterialTheme.typography.headlineSmall, color = Brand.TextPrimary)
        content()
    }
}

@Composable
private fun Card(content: @Composable () -> Unit) {
    Column(
        Modifier.fillMaxWidth().background(Brand.Surface, RoundedCornerShape(16.dp)).padding(22.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) { content() }
}

@Composable
private fun InfoRow(label: String, value: String) {
    Row {
        Text(label, modifier = Modifier.width(200.dp), color = Brand.TextSecondary, style = MaterialTheme.typography.bodyLarge)
        Text(value, color = Brand.TextPrimary, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun OptionGroup(label: String, options: List<String>, selected: Int, onSelect: (Int) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(label, color = Brand.TextSecondary, style = MaterialTheme.typography.bodyLarge)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            options.forEachIndexed { i, text -> Pill(text, selected = i == selected, onClick = { onSelect(i) }) }
        }
    }
}

@Composable
private fun AccountSection(onSwitchToManual: () -> Unit, onSwitchToDevice: () -> Unit) {
    val context = LocalContext.current
    val account = Graph.xtream?.account
    val user = Graph.xtream?.userInfo
    SectionColumn(stringResource(R.string.settings_account)) {
        Card {
            InfoRow(stringResource(R.string.settings_mode), stringResource(
                if (account?.source == Account.Source.MANUAL) R.string.settings_mode_manual else R.string.settings_mode_device))
            // The provider URL (DNS) is never shown: the app only works with the operator's servers.
            account?.let { InfoRow(stringResource(R.string.settings_username), it.username) }
            user?.let {
                InfoRow(stringResource(R.string.settings_status), accountStatusLabel(it.status))
                InfoRow(
                    stringResource(R.string.settings_expires),
                    it.expiresAt?.let { exp -> DateFormat.getMediumDateFormat(context).format(Date(exp * 1000)) } ?: stringResource(R.string.home_no_expiry),
                )
                if (it.maxConnections != null) InfoRow(stringResource(R.string.settings_connections), "${it.activeConnections ?: 0} / ${it.maxConnections}")
            }
        }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
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
}

@Composable
private fun PlayerSection() {
    var liveHls by remember { mutableStateOf(Graph.store.liveHls) }
    val languages = listOf("off", "es", "en")
    var language by remember { mutableIntStateOf(languages.indexOf(Graph.store.subtitleLanguage).coerceAtLeast(0)) }
    var size by remember { mutableIntStateOf(Graph.store.subtitleSize) }
    SectionColumn(stringResource(R.string.settings_playback)) {
        Card {
            OptionGroup(stringResource(R.string.settings_live_format),
                listOf(stringResource(R.string.settings_format_ts), stringResource(R.string.settings_format_hls)), if (liveHls) 1 else 0) {
                liveHls = it == 1
                Graph.store.liveHls = liveHls
            }
        }
        Card {
            OptionGroup(stringResource(R.string.settings_subtitles),
                listOf(stringResource(R.string.player_subtitles_off), stringResource(R.string.language_es), stringResource(R.string.language_en)), language) {
                language = it
                Graph.store.subtitleLanguage = languages[it]
            }
            OptionGroup(stringResource(R.string.settings_subtitle_size),
                listOf(stringResource(R.string.size_small), stringResource(R.string.size_normal), stringResource(R.string.size_large)), size) {
                size = it
                Graph.store.subtitleSize = it
            }
            Text(stringResource(R.string.settings_subtitles_hint), color = Brand.TextSecondary, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

private enum class PinStep { CREATE, CONFIRM, MANAGE, DISABLE, CHANGE_OLD, CHANGE_NEW }

@Composable
private fun ParentalSection() {
    val context = LocalContext.current
    var enabled by remember { mutableStateOf(Graph.store.parentalEnabled) }
    var managing by remember { mutableStateOf(false) }
    var step by remember { mutableStateOf<PinStep?>(null) }
    var newPin by remember { mutableStateOf("") }
    val scope = rememberCoroutineScope()

    SectionColumn(stringResource(R.string.parental_title)) {
        Card {
            Text(stringResource(R.string.parental_description), color = Brand.TextSecondary, style = MaterialTheme.typography.bodyLarge)
            InfoRow(stringResource(R.string.settings_status), stringResource(if (enabled) R.string.parental_on else R.string.parental_off))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                when {
                    !enabled -> Button(onClick = { step = PinStep.CREATE }) { Text(stringResource(R.string.parental_enable)) }
                    !managing -> Button(onClick = { step = PinStep.MANAGE }) { Text(stringResource(R.string.parental_manage)) }
                    else -> {
                        OutlinedButton(onClick = { step = PinStep.CHANGE_OLD }) { Text(stringResource(R.string.parental_change_pin)) }
                        OutlinedButton(onClick = { step = PinStep.DISABLE }) { Text(stringResource(R.string.parental_disable)) }
                    }
                }
            }
        }
        if (enabled && managing) LockedCategories()
    }

    val done = { step = null }
    when (step) {
        PinStep.CREATE -> PinDialog(stringResource(R.string.parental_create_pin), onDismiss = done, onSubmit = { pin ->
            newPin = pin
            step = PinStep.CONFIRM
            true
        })
        PinStep.CONFIRM -> PinDialog(stringResource(R.string.parental_confirm_pin), onDismiss = done, onSubmit = { pin ->
            (pin == newPin).also { ok ->
                if (ok) {
                    Graph.store.parentalPin = pin
                    Graph.store.parentalEnabled = true
                    Parental.relock()
                    enabled = true
                    managing = true
                    step = null
                    scope.launch { lockAdultCategories() }
                    Toast.makeText(context, R.string.parental_enabled_toast, Toast.LENGTH_LONG).show()
                }
            }
        })
        PinStep.MANAGE -> PinDialog(stringResource(R.string.parental_enter_pin), onDismiss = done, onSubmit = { pin ->
            Parental.checkPin(pin).also { if (it) { managing = true; step = null } }
        })
        PinStep.DISABLE -> PinDialog(stringResource(R.string.parental_enter_pin), onDismiss = done, onSubmit = { pin ->
            Parental.checkPin(pin).also {
                if (it) {
                    Graph.store.parentalEnabled = false
                    enabled = false
                    managing = false
                    step = null
                }
            }
        })
        PinStep.CHANGE_OLD -> PinDialog(stringResource(R.string.parental_current_pin), onDismiss = done, onSubmit = { pin ->
            Parental.checkPin(pin).also { if (it) step = PinStep.CHANGE_NEW }
        })
        PinStep.CHANGE_NEW -> PinDialog(stringResource(R.string.parental_new_pin), onDismiss = done, onSubmit = { pin ->
            Graph.store.parentalPin = pin
            Parental.relock()
            step = null
            Toast.makeText(context, R.string.parental_pin_changed, Toast.LENGTH_SHORT).show()
            true
        })
        null -> {}
    }
}

/** First activation: categories that look like adult content start locked. */
private suspend fun lockAdultCategories() {
    val xtream = Graph.xtream ?: return
    runCatching {
        Parental.lockAdult(
            mapOf(
                Parental.Type.LIVE to xtream.liveCategories(),
                Parental.Type.VOD to xtream.movieCategories(),
                Parental.Type.SERIES to xtream.seriesCategories(),
            )
        )
    }
}

@Composable
private fun LockedCategories() {
    var type by remember { mutableStateOf(Parental.Type.LIVE) }
    var categories by remember(type) { mutableStateOf<List<Category>?>(null) }
    var lockedKeys by remember { mutableStateOf(Graph.store.lockedCategories) }
    LaunchedEffect(type) {
        val xtream = Graph.xtream ?: return@LaunchedEffect
        categories = runCatching {
            when (type) {
                Parental.Type.LIVE -> xtream.liveCategories()
                Parental.Type.VOD -> xtream.movieCategories()
                Parental.Type.SERIES -> xtream.seriesCategories()
            }
        }.getOrDefault(emptyList())
    }
    Card {
        Text(stringResource(R.string.parental_categories), style = MaterialTheme.typography.titleMedium, color = Brand.TextPrimary)
        Text(stringResource(R.string.parental_categories_hint), style = MaterialTheme.typography.bodyMedium, color = Brand.TextSecondary)
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Pill(stringResource(R.string.home_live), type == Parental.Type.LIVE, { type = Parental.Type.LIVE })
            Pill(stringResource(R.string.home_movies), type == Parental.Type.VOD, { type = Parental.Type.VOD })
            Pill(stringResource(R.string.home_series), type == Parental.Type.SERIES, { type = Parental.Type.SERIES })
        }
        val list = categories
        if (list == null) {
            Text(stringResource(R.string.loading_categories), color = Brand.TextSecondary)
        } else {
            LazyColumn(Modifier.fillMaxWidth().height(360.dp).padding(top = 4.dp).focusRestorer(),
                verticalArrangement = Arrangement.spacedBy(6.dp), contentPadding = PaddingValues(vertical = 4.dp)) {
                items(list, key = { it.id }) { category ->
                    val locked = Parental.key(type, category.id) in lockedKeys
                    val toggle = {
                        Parental.setLocked(type, category.id, !locked)
                        lockedKeys = Graph.store.lockedCategories
                    }
                    Surface(
                        selected = locked,
                        onClick = toggle,
                        modifier = Modifier.fillMaxWidth().tapToClick(toggle),
                        shape = SelectableSurfaceDefaults.shape(RoundedCornerShape(8.dp)),
                        scale = SelectableSurfaceDefaults.scale(focusedScale = 1.01f),
                        colors = SelectableSurfaceDefaults.colors(
                            containerColor = Brand.SurfaceHigh,
                            contentColor = Brand.TextPrimary,
                            selectedContainerColor = Brand.SurfaceHigh,
                            selectedContentColor = Brand.TextPrimary,
                            focusedContainerColor = Brand.Primary,
                            focusedContentColor = Color.White,
                            focusedSelectedContainerColor = Brand.Primary,
                            focusedSelectedContentColor = Color.White,
                        ),
                    ) {
                        Row(Modifier.padding(horizontal = 16.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text(category.name, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(stringResource(if (locked) R.string.parental_locked else R.string.parental_unlocked), style = MaterialTheme.typography.labelLarge,
                                color = if (locked) Brand.Warning else Brand.TextSecondary)
                            if (locked) Icon(AppIcons.Lock, contentDescription = null, tint = Brand.Warning, modifier = Modifier.padding(start = 8.dp).size(18.dp))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ContentSection() {
    val context = LocalContext.current
    SectionColumn(stringResource(R.string.settings_content)) {
        Card {
            Text(stringResource(R.string.settings_content_hint), color = Brand.TextSecondary, style = MaterialTheme.typography.bodyLarge)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Button(onClick = {
                    Graph.xtream?.clearCache()
                    Toast.makeText(context, R.string.settings_reloaded, Toast.LENGTH_SHORT).show()
                }) { Text(stringResource(R.string.settings_reload)) }
                OutlinedButton(onClick = {
                    Graph.xtream?.account?.let(Graph.store::clearRecents)
                    Toast.makeText(context, R.string.settings_history_cleared, Toast.LENGTH_SHORT).show()
                }) { Text(stringResource(R.string.settings_clear_history)) }
                OutlinedButton(onClick = {
                    Graph.xtream?.account?.let(Graph.store::clearFavorites)
                    Toast.makeText(context, R.string.settings_favorites_cleared, Toast.LENGTH_SHORT).show()
                }) { Text(stringResource(R.string.settings_clear_favorites)) }
            }
        }
    }
}

@Composable
private fun DeviceSection() {
    SectionColumn(stringResource(R.string.settings_device)) {
        Card {
            InfoRow(stringResource(R.string.settings_device_id), Graph.identity.deviceId)
            InfoRow(stringResource(R.string.settings_model), "${Build.MANUFACTURER} ${Build.MODEL}")
            InfoRow(stringResource(R.string.settings_android), Build.VERSION.RELEASE)
            InfoRow(stringResource(R.string.settings_version), BuildConfig.VERSION_NAME)
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
