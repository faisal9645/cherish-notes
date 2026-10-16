package com.example.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.RowScope
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonColors
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ButtonElevation
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.modifier.modifierLocalConsumer
import androidx.compose.ui.modifier.modifierLocalOf
import androidx.compose.ui.modifier.modifierLocalProvider
import androidx.compose.ui.semantics.Role

/** How far a card or button shrinks while held; the spring brings it back with a little give. */
private const val CARD_PRESSED_SCALE = 0.96f
private const val BUTTON_PRESSED_SCALE = 0.96f
private val pressSpring = spring<Float>(dampingRatio = 0.55f, stiffness = 700f)

/** Set by [pressScaleTarget]: tells it whether a [springClickable] further in the chain is held. */
private val LocalPressTarget = modifierLocalOf<MutableState<Boolean>?> { null }

/**
 * The part of a card that shrinks while it's pressed. Put it before the card's first visual
 * modifier (clip, background, border, shadow) so the whole card shrinks, not only what's inside;
 * the [springClickable] later in the same chain drives it.
 */
@OptIn(ExperimentalComposeUiApi::class)
fun Modifier.pressScaleTarget(pressedScale: Float = CARD_PRESSED_SCALE): Modifier = composed {
    val pressed = remember { mutableStateOf(false) }
    val scale by animateFloatAsState(
        targetValue = if (pressed.value) pressedScale else 1f,
        animationSpec = pressSpring,
        label = "press_scale"
    )
    this
        .graphicsLayer {
            scaleX = scale
            scaleY = scale
        }
        .modifierLocalProvider(LocalPressTarget) { pressed }
}

/**
 * `clickable` with a tap that feels physical: the element shrinks a touch while held and springs
 * back, with the usual ripple. With a [pressScaleTarget] earlier in the chain, that whole card
 * shrinks; without one, this element does.
 */
@OptIn(ExperimentalComposeUiApi::class)
fun Modifier.springClickable(
    enabled: Boolean = true,
    onClickLabel: String? = null,
    role: Role? = null,
    onClick: () -> Unit
): Modifier = composed {
    val interactionSource = remember { MutableInteractionSource() }
    var target by remember { mutableStateOf<MutableState<Boolean>?>(null) }
    val isPressed by interactionSource.collectIsPressedAsState()
    val held = isPressed && enabled
    SideEffect { target?.value = held }
    val ownScale by animateFloatAsState(
        targetValue = if (held && target == null) CARD_PRESSED_SCALE else 1f,
        animationSpec = pressSpring,
        label = "spring_click_scale"
    )
    this
        .modifierLocalConsumer { target = LocalPressTarget.current }
        // Anything clickable inside this element shrinks itself, not this element's card
        .modifierLocalProvider(LocalPressTarget) { null }
        .graphicsLayer {
            scaleX = ownScale
            scaleY = ownScale
        }
        .clickable(
            interactionSource = interactionSource,
            indication = LocalIndication.current,
            enabled = enabled,
            onClickLabel = onClickLabel,
            role = role,
            onClick = onClick
        )
}

/** Shrinks this element while [interactionSource] is pressed (for Material buttons). */
fun Modifier.pressScale(
    interactionSource: InteractionSource,
    enabled: Boolean = true,
    pressedScale: Float = BUTTON_PRESSED_SCALE
): Modifier = composed {
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed && enabled) pressedScale else 1f,
        animationSpec = pressSpring,
        label = "button_press_scale"
    )
    graphicsLayer {
        scaleX = scale
        scaleY = scale
    }
}

/** Material's Button that shrinks a touch while pressed. Same parameters as Button. */
@Composable
fun CherishButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    shape: Shape = ButtonDefaults.shape,
    colors: ButtonColors = ButtonDefaults.buttonColors(),
    elevation: ButtonElevation? = ButtonDefaults.buttonElevation(),
    border: BorderStroke? = null,
    contentPadding: PaddingValues = ButtonDefaults.ContentPadding,
    interactionSource: MutableInteractionSource? = null,
    content: @Composable RowScope.() -> Unit
) {
    val source = interactionSource ?: remember { MutableInteractionSource() }
    Button(
        onClick = onClick,
        modifier = modifier.pressScale(source, enabled),
        enabled = enabled,
        shape = shape,
        colors = colors,
        elevation = elevation,
        border = border,
        contentPadding = contentPadding,
        interactionSource = source,
        content = content
    )
}

/** Material's FilledTonalButton that shrinks a touch while pressed. Same parameters. */
@Composable
fun CherishTonalButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    shape: Shape = ButtonDefaults.filledTonalShape,
    colors: ButtonColors = ButtonDefaults.filledTonalButtonColors(),
    elevation: ButtonElevation? = ButtonDefaults.filledTonalButtonElevation(),
    border: BorderStroke? = null,
    contentPadding: PaddingValues = ButtonDefaults.ContentPadding,
    interactionSource: MutableInteractionSource? = null,
    content: @Composable RowScope.() -> Unit
) {
    val source = interactionSource ?: remember { MutableInteractionSource() }
    FilledTonalButton(
        onClick = onClick,
        modifier = modifier.pressScale(source, enabled),
        enabled = enabled,
        shape = shape,
        colors = colors,
        elevation = elevation,
        border = border,
        contentPadding = contentPadding,
        interactionSource = source,
        content = content
    )
}

/** Material's OutlinedButton that shrinks a touch while pressed. Same parameters. */
@Composable
fun CherishOutlinedButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    shape: Shape = ButtonDefaults.outlinedShape,
    colors: ButtonColors = ButtonDefaults.outlinedButtonColors(),
    elevation: ButtonElevation? = null,
    border: BorderStroke? = ButtonDefaults.outlinedButtonBorder(enabled),
    contentPadding: PaddingValues = ButtonDefaults.ContentPadding,
    interactionSource: MutableInteractionSource? = null,
    content: @Composable RowScope.() -> Unit
) {
    val source = interactionSource ?: remember { MutableInteractionSource() }
    OutlinedButton(
        onClick = onClick,
        modifier = modifier.pressScale(source, enabled),
        enabled = enabled,
        shape = shape,
        colors = colors,
        elevation = elevation,
        border = border,
        contentPadding = contentPadding,
        interactionSource = source,
        content = content
    )
}
