package com.illuminazionetech.vrclip.ui.common

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.toPath
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Matrix
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.graphics.shapes.Morph
import androidx.graphics.shapes.RoundedPolygon

/**
 * A Material 3 Expressive shape filled with [color] behind [content]. When [shape] changes it
 * morphs into the new one with the motion scheme's slow spatial spring, turning by [rotation]
 * degrees; with [idleSpin] it also turns slowly on its own, which gives empty states a sign of
 * life. Everything happens in the draw phase, so animating it does not recompose the content.
 */
@Composable
fun MorphingShapeBox(
    shape: RoundedPolygon,
    color: Color,
    modifier: Modifier = Modifier,
    rotation: Float = 0f,
    idleSpin: Boolean = false,
    content: @Composable BoxScope.() -> Unit = {},
) {
    var from by remember { mutableStateOf(shape) }
    var to by remember { mutableStateOf(shape) }
    val progress = remember { Animatable(1f) }
    val turn = remember { Animatable(rotation) }
    val spatial = MaterialTheme.motionScheme.slowSpatialSpec<Float>()

    LaunchedEffect(shape) {
        if (shape != to) {
            from = to
            to = shape
            progress.snapTo(0f)
            progress.animateTo(1f, spatial)
        }
    }
    LaunchedEffect(rotation) { turn.animateTo(rotation, spatial) }

    val spin =
        if (idleSpin) {
            val transition = rememberInfiniteTransition(label = "shapeSpin")
            transition.animateFloat(
                initialValue = 0f,
                targetValue = 360f,
                animationSpec =
                    infiniteRepeatable(tween(36_000, easing = LinearEasing), RepeatMode.Restart),
                label = "shapeSpinAngle",
            )
        } else null

    val morph = remember(from, to) { Morph(from, to) }
    Box(
        modifier =
            modifier.drawWithCache {
                val path = Path()
                val matrix = Matrix()
                onDrawBehind {
                    morph.toPath(progress.value, path)
                    matrix.reset()
                    matrix.scale(size.width, size.height)
                    path.transform(matrix)
                    rotate(turn.value + (spin?.value ?: 0f)) { drawPath(path, color) }
                }
            },
        contentAlignment = Alignment.Center,
        content = content,
    )
}
