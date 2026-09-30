package io.github.m1nexoff.themetransitions.engine

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import io.github.m1nexoff.themetransitions.effect.DimRun
import io.github.m1nexoff.themetransitions.effect.EffectLayer
import io.github.m1nexoff.themetransitions.effect.ShakeRun

private val DrawnLayers = EffectLayer.entries.filter { it != EffectLayer.Content }

// The themes alone, no effects: the live one, and the old frame of whichever run still has one. A
// switch freezes exactly this, so a transition replaced midway carries on as part of the next one's
// frozen frame.
internal fun DrawScope.drawThemes(live: GraphicsLayer, current: Run?, retiring: List<Run>) {
    val owner = if (current != null && current.hasOld) current else retiring.lastOrNull { it.hasOld }
    if (owner == null) {
        drawLayer(live)
        return
    }
    with(owner.reveal) {
        drawLive(live)
        drawOld()
    }
}

// Retired runs first, so the current one's effects land on top within each layer.
internal fun DrawScope.drawEffects(current: Run?, retiring: List<Run>) {
    for (l in DrawnLayers.indices) {
        val layer = DrawnLayers[l]
        for (i in retiring.indices) with(retiring[i]) { drawEffects(layer) }
        if (current != null) with(current) { drawEffects(layer) }
    }
}

// The newest run that shakes sets the rhythm; the strongest sets the strength, so a retiring run's
// shake dies down rather than stopping dead.
internal fun shakeOffset(current: Run?, retiring: List<Run>): Offset {
    var strength = 0f
    for (i in retiring.indices) {
        val run = retiring[i]
        val shake = run.shake ?: continue
        strength = maxOf(strength, shake.strength(run, run.alpha))
    }
    val currentShake = current?.shake
    if (current != null && currentShake != null) strength = maxOf(strength, currentShake.strength(current, 1f))
    if (strength <= 0f) return Offset.Zero
    if (current != null && currentShake != null) return currentShake.offset(current, strength)
    val leader = retiring.lastOrNull { it.shake != null } ?: return Offset.Zero
    return leader.shake!!.offset(leader, strength)
}

// What the shake uncovers along two edges, dimmed like the rest. Only those strips: a full-screen
// backdrop under the whole content is two more layers of fill every frame.
internal fun DrawScope.drawShakeBackdrop(shake: Offset, current: Run?, retiring: List<Run>) {
    val backdrop = (current?.shake ?: retiring.lastOrNull { it.shake != null }?.shake)?.backdrop ?: return
    fun strip(left: Float, top: Float, right: Float, bottom: Float) {
        if (right <= left || bottom <= top) return
        val topLeft = Offset(left, top)
        val stripSize = Size(right - left, bottom - top)
        drawRect(backdrop, topLeft, stripSize)
        for (i in retiring.indices) dimStrip(retiring[i], topLeft, stripSize)
        if (current != null) dimStrip(current, topLeft, stripSize)
    }
    if (shake.x > 0f) strip(0f, 0f, shake.x, size.height) else strip(size.width + shake.x, 0f, size.width, size.height)
    if (shake.y > 0f) strip(0f, 0f, size.width, shake.y) else strip(0f, size.height + shake.y, size.width, size.height)
}

private fun DrawScope.dimStrip(run: Run, topLeft: Offset, size: Size) {
    for (i in run.effects.indices) {
        val dim = run.effects[i] as? DimRun ?: continue
        with(dim) { drawOn(topLeft, size, run, run.alpha) }
    }
}
