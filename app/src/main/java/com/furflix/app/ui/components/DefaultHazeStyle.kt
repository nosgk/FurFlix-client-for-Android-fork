package com.furflix.app.ui.components

import androidx.compose.ui.graphics.Color
import com.furflix.app.ui.theme.Dimens
import dev.chrisbanes.haze.blur.HazeBlurStyle
import dev.chrisbanes.haze.blur.HazeColorEffect

val DefaultHazeStyle = HazeBlurStyle {
    blurRadius(Dimens.hazeBlurRadius)
    colorEffects(listOf(HazeColorEffect.tint(Color.Black.copy(alpha = Dimens.hazeTintAlpha))))
}
