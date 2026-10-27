package com.player.tv.ui.components

import androidx.compose.foundation.background
import androidx.activity.compose.LocalOnBackPressedDispatcherOwner
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.ui.composed
import androidx.compose.ui.graphics.graphicsLayer
import androidx.tv.material3.Icon
import kotlinx.coroutines.delay
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.getValue
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.Dp
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.player.tv.R
import com.player.tv.data.AppException
import com.player.tv.ui.theme.Brand

/** State of something loaded asynchronously. */
sealed interface Loadable<out T> {
    data object Loading : Loadable<Nothing>
    data class Failed(val kind: AppException.Kind) : Loadable<Nothing>
    data class Ready<T>(val data: T) : Loadable<T>
}

fun <T, R> Loadable<T>.map(transform: (T) -> R): Loadable<R> = when (this) {
    is Loadable.Ready -> Loadable.Ready(transform(data))
    is Loadable.Failed -> this
    Loadable.Loading -> Loadable.Loading
}

fun Throwable.errorKind(): AppException.Kind = (this as? AppException)?.kind ?: AppException.Kind.SERVER

/** Runs [block] and wraps the outcome; coroutine cancellation is rethrown. */
suspend fun <T> loadable(block: suspend () -> T): Loadable<T> = try {
    Loadable.Ready(block())
} catch (e: kotlinx.coroutines.CancellationException) {
    throw e
} catch (e: Exception) {
    Loadable.Failed(e.errorKind())
}

@Composable
fun errorMessage(kind: AppException.Kind): String = stringResource(
    when (kind) {
        AppException.Kind.NETWORK -> R.string.error_network
        AppException.Kind.AUTH -> R.string.error_auth
        AppException.Kind.SERVER -> R.string.error_server
        AppException.Kind.KEY_MISMATCH -> R.string.error_key_mismatch
    }
)

/** Dark background with a soft brand glow, used by every screen. */
@Composable
fun AppBackground(modifier: Modifier = Modifier, content: @Composable BoxScope.() -> Unit) {
    Box(
        modifier
            .fillMaxSize()
            .background(Brand.Background)
            .background(Brush.radialGradient(listOf(Brand.BackgroundGlow, Brand.Background), radius = 1600f)),
        content = content,
    )
}

/** Spinning arc: tells the user something is in progress (static text looks frozen). */
@Composable
fun Spinner(modifier: Modifier = Modifier, size: Dp = 40.dp, color: Color = Brand.Primary, strokeWidth: Dp = 4.dp) {
    val transition = rememberInfiniteTransition(label = "spinner")
    val angle by transition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(durationMillis = 900, easing = LinearEasing)),
        label = "angle",
    )
    Canvas(modifier.size(size)) {
        val stroke = strokeWidth.toPx()
        drawArc(
            color = color,
            startAngle = angle,
            sweepAngle = 270f,
            useCenter = false,
            topLeft = Offset(stroke / 2, stroke / 2),
            size = Size(this.size.width - stroke, this.size.height - stroke),
            style = Stroke(width = stroke, cap = StrokeCap.Round),
        )
    }
}

/** Full-area loading state: spinner plus what is being loaded. */
@Composable
fun LoadingView(modifier: Modifier = Modifier, message: String? = null) {
    Column(
        modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spinner()
        Text(
            message ?: stringResource(R.string.loading),
            color = Brand.TextSecondary,
            style = MaterialTheme.typography.titleMedium,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 32.dp),
        )
    }
}

@Composable
fun ErrorView(kind: AppException.Kind, onRetry: () -> Unit, modifier: Modifier = Modifier) {
    val focus = remember { FocusRequester() }
    Column(
        modifier.fillMaxSize().padding(32.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(errorMessage(kind), color = Brand.TextPrimary, textAlign = TextAlign.Center, style = MaterialTheme.typography.titleMedium)
        Button(onClick = onRetry, modifier = Modifier.focusRequester(focus)) { Text(stringResource(R.string.retry)) }
    }
    LaunchedEffect(Unit) { focus.requestWhenReady() }
}

@Composable
fun EmptyView(text: String, modifier: Modifier = Modifier) {
    Box(modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
        Text(text, color = Brand.TextSecondary, style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center)
    }
}

/**
 * Round "←" for mouse and touch users; the remote uses its own Back key. Not focusable, so the
 * D-pad never stops on it. It triggers the system back action, so screen-level BackHandlers
 * (e.g. the exit dialog) and navigation behave exactly like the remote's Back key.
 */
@Composable
fun BackButton(modifier: Modifier = Modifier) {
    val dispatcher = LocalOnBackPressedDispatcherOwner.current?.onBackPressedDispatcher
    val hover = remember { MutableInteractionSource() }
    val hovered by hover.collectIsHoveredAsState()
    val bg by animateColorAsState(if (hovered) Brand.Primary else Brand.SurfaceHigh, tween(150), label = "backBg")
    Box(
        modifier
            .size(48.dp)
            .clip(CircleShape)
            .background(bg)
            .hoverable(hover)
            .pointerInput(dispatcher) { detectTapGestures(onTap = { dispatcher?.onBackPressed() }) },
        contentAlignment = Alignment.Center,
    ) {
        Icon(AppIcons.Back, contentDescription = null, tint = Brand.TextPrimary, modifier = Modifier.size(24.dp))
    }
}

/**
 * Fades between loading / error / content instead of cutting. Only a change of *kind*
 * animates (new data of the same kind updates in place, keeping focus and scroll).
 */
@Composable
fun <T> AnimatedLoadable(
    state: Loadable<T>,
    modifier: Modifier = Modifier,
    loading: @Composable () -> Unit = { LoadingView() },
    failed: @Composable (AppException.Kind) -> Unit,
    content: @Composable (T) -> Unit,
) {
    AnimatedContent(
        targetState = state,
        modifier = modifier,
        contentKey = { it::class },
        transitionSpec = { fadeIn(tween(260, delayMillis = 60)) togetherWith fadeOut(tween(160)) },
        label = "loadable",
    ) { s ->
        when (s) {
            Loadable.Loading -> loading()
            is Loadable.Failed -> failed(s.kind)
            is Loadable.Ready -> content(s.data)
        }
    }
}

/** Fades and lifts an item into place when it first appears; [index] staggers a group. */
fun Modifier.enterAnimation(index: Int = 0): Modifier = composed {
    val progress = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        delay(index * 70L)
        progress.animateTo(1f, tween(420, easing = FastOutSlowInEasing))
    }
    graphicsLayer {
        alpha = progress.value
        translationY = (1f - progress.value) * 24.dp.toPx()
    }
}

/** Screen title with the back button in front of it. */
@Composable
fun ScreenHeader(title: String, modifier: Modifier = Modifier) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
        BackButton()
        Text(title, style = MaterialTheme.typography.headlineSmall, color = Brand.TextPrimary, maxLines = 1)
    }
}

@Composable
fun Logo(modifier: Modifier = Modifier.size(56.dp)) {
    androidx.compose.foundation.Image(
        painter = androidx.compose.ui.res.painterResource(R.drawable.logo),
        contentDescription = null,
        modifier = modifier,
    )
}

/** Requests focus after the next frame, once the target has been laid out. */
suspend fun FocusRequester.requestWhenReady() {
    androidx.compose.runtime.withFrameNanos { }
    safeRequest()
}

/** requestFocus throws if the target is not composed yet (e.g. lazy list still empty). */
fun FocusRequester.safeRequest() {
    runCatching { requestFocus() }
}
