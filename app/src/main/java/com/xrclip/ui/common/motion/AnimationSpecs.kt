package com.xrclip.ui.common.motion

import android.view.animation.PathInterpolator
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.PathEasing
import androidx.compose.animation.core.tween
import androidx.compose.ui.graphics.Path
import com.xrclip.ui.common.DURATION_ENTER

fun PathInterpolator.toEasing(): Easing {
    return Easing { f -> this.getInterpolation(f) }
}

private val path =
    Path().apply {
        moveTo(0f, 0f)
        cubicTo(0.05F, 0F, 0.133333F, 0.06F, 0.166666F, 0.4F)
        cubicTo(0.208333F, 0.82F, 0.25F, 1F, 1F, 1F)
    }

val EmphasizeEasing = CubicBezierEasing(0.2f, 1.4f, 0.4f, 1.0f) // Expressive bouncy easing
val EmphasizeEasingVariant = CubicBezierEasing(0.34f, 1.56f, 0.64f, 1.0f)
val EmphasizedDecelerate = CubicBezierEasing(0.05f, 0.7f, 0.1f, 1f)
val EmphasizedAccelerate = CubicBezierEasing(0.3f, 0f, 1f, 1f)

private val standardDecelerate = CubicBezierEasing(.0f, .0f, 0f, 1f)

private val motionEasingStandard = CubicBezierEasing(0.4F, 0.0F, 0.2F, 1F)

private val tweenSpec = tween<Float>(durationMillis = DURATION_ENTER, easing = EmphasizeEasing)
