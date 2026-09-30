package io.github.m1nexoff.themetransitions

import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.MotionDurationScale
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.changedToDownIgnoreConsumed
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntSize
import io.github.m1nexoff.themetransitions.engine.Run
import io.github.m1nexoff.themetransitions.engine.TransitionEngine
import io.github.m1nexoff.themetransitions.engine.drawEffects
import io.github.m1nexoff.themetransitions.engine.drawShakeBackdrop
import io.github.m1nexoff.themetransitions.engine.drawThemes
import io.github.m1nexoff.themetransitions.engine.shakeOffset
import io.github.m1nexoff.themetransitions.reveal.RunSetup
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.launch
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TimeSource

// A touch older than this did not start the switch; the switch opens from the centre instead.
private val TOUCH_MEMORY = 5.seconds

/**
 * Switches [content] between themes through an animated [transition]: by default a circle that grows
 * from where the user last touched, usually the theme toggle they just tapped.
 *
 * Put it at the root, around your theme, and build the theme from the state [content] is given, never
 * from [targetState] directly: [content] keeps the old state for one frame after a switch, while that
 * frame is frozen to be revealed over the new one.
 *
 * ```
 * AnimatedTheme(
 *     targetState = darkTheme,
 *     transition = Transitions.circle(),
 * ) { dark ->
 *     AppTheme(dark) { App() }
 * }
 * ```
 *
 * [content] is composed once, in one state at a time; the outgoing theme is a bitmap, not a second
 * composition, so all of its state survives every switch. It must paint its own opaque background,
 * since that bitmap is what the transition reveals the new theme through.
 *
 * [transition] is read at the moment of each switch, so it can depend on the theme being switched to:
 * `Transitions.flood(color = theme.primary)`. A switch made while a transition is still running freezes
 * what is on screen — the older transition included — and plays the new one over it. With a
 * [MotionDurationScale] of 0 — on Android, animations turned off in the system settings — the theme
 * changes at once. So does it when the box is resized mid-transition.
 *
 * @param targetState the theme to show. Anything compared by `equals`: a Boolean, an enum, a palette
 * object.
 * @param modifier applied to the box that holds [content] and draws the transition over it.
 * @param transition how to switch. See [Transitions].
 * @param content the UI, drawn in the state it is given.
 */
@Composable
public fun <T> AnimatedTheme(
    targetState: T,
    modifier: Modifier = Modifier,
    transition: ThemeTransition = Transitions.circle(),
    content: @Composable (T) -> Unit,
) {
    val target by rememberUpdatedState(targetState)
    val nextTransition by rememberUpdatedState(transition)
    val density by rememberUpdatedState(LocalDensity.current)
    var shown by remember { mutableStateOf(targetState) }
    val engine = remember { TransitionEngine() }
    val lastTouch = remember { TouchMemory() }
    // The content alone, and the themes as they stand on screen — the live one and any old frame —
    // without effects. A switch freezes the second.
    val live = rememberGraphicsLayer()
    val themes = rememberGraphicsLayer()

    LaunchedEffect(engine) {
        launch { engine.runFrames() }
        // One switch at a time as far as the freeze, since each has to capture the screen the one before
        // left; the transitions themselves then overlap.
        snapshotFlow { target }.conflate().collect { next ->
            if (next == shown) return@collect
            val chosen = nextTransition
            val reveal = chosen.reveal
            val size = themes.size
            val animationsOff = currentCoroutineContext()[MotionDurationScale]?.scaleFactor == 0f
            if (animationsOff || reveal == null || reveal.durationMillis == 0 || size == IntSize.Zero) {
                engine.clear()
                shown = next
                return@collect
            }
            val center = Offset(size.width / 2f, size.height / 2f)
            val origin = reveal.origin?.resolve(size, lastTouch.recent()) ?: center
            val run = Run(chosen, RunSetup(size, origin, density))
            engine.begin(run)
            withFrameNanos {}
            // A resize, or animations turned off, may have dropped the run meanwhile.
            if (engine.current === run && themes.size == size) engine.freeze(run, themes.toImageBitmap())
            shown = next
        }
    }

    Box(
        modifier
            .onSizeChanged { if (engine.active) engine.clear() }
            .pointerInput(Unit) {
                awaitPointerEventScope {
                    while (true) {
                        awaitPointerEvent(PointerEventPass.Initial).changes
                            .firstOrNull { it.changedToDownIgnoreConsumed() }
                            ?.let { lastTouch.remember(it.position) }
                    }
                }
            }
            .drawWithContent {
                val current = engine.current
                val retiring = engine.retiring
                if (current != null || retiring.isNotEmpty()) {
                    val shake = shakeOffset(current, retiring)
                    if (shake != Offset.Zero) drawShakeBackdrop(shake, current, retiring)
                }
                drawContent()
            }
            .graphicsLayer {
                val current = engine.current
                val retiring = engine.retiring
                val shake = if (current == null && retiring.isEmpty()) Offset.Zero else shakeOffset(current, retiring)
                translationX = shake.x
                translationY = shake.y
            }
            .drawWithContent {
                val current = engine.current
                val retiring = engine.retiring
                // Read here so every tick redraws, whatever the reveal and effects read themselves.
                current?.clockMillis
                for (i in retiring.indices) retiring[i].clockMillis
                live.record { this@drawWithContent.drawContent() }
                themes.record { drawThemes(live, current, retiring) }
                drawLayer(themes)
                if (current != null || retiring.isNotEmpty()) drawEffects(current, retiring)
            }
            // The draw above runs every frame of a transition; without this boundary each of those would
            // re-record the whole screen below as well.
            .graphicsLayer(),
    ) {
        content(shown)
    }
}

// Written on every touch and read only when a switch starts, so it is deliberately not snapshot state:
// nothing should recompose or redraw because a finger went down.
private class TouchMemory {
    private var position: Offset? = null
    private var at: TimeSource.Monotonic.ValueTimeMark? = null

    fun remember(position: Offset) {
        this.position = position
        at = TimeSource.Monotonic.markNow()
    }

    fun recent(): Offset? {
        val at = at ?: return null
        return if (at.elapsedNow() < TOUCH_MEMORY) position else null
    }
}
