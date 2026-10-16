package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxColors
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.ChipColors
import androidx.compose.material3.ChipElevation
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonColors
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.SelectableChipColors
import androidx.compose.material3.SelectableChipElevation
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderColors
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchColors
import androidx.compose.material3.SwitchDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import com.example.security.SecurityPreferences
import kotlin.math.roundToInt

/** Light haptic taps for toggles, pickers and sliders, only while "Haptic Feedback" is on in Settings. */
class ControlHaptics internal constructor(
    private val haptic: HapticFeedback,
    private val prefs: SecurityPreferences,
) {
    fun toggle(on: Boolean) = play(if (on) HapticFeedbackType.ToggleOn else HapticFeedbackType.ToggleOff)
    fun tick() = play(HapticFeedbackType.SegmentTick)
    fun fineTick() = play(HapticFeedbackType.SegmentFrequentTick)

    private fun play(type: HapticFeedbackType) {
        if (prefs.isHapticFeedbackEnabled()) haptic.performHapticFeedback(type)
    }
}

@Composable
fun rememberControlHaptics(): ControlHaptics {
    val haptic = LocalHapticFeedback.current
    val context = LocalContext.current
    return remember(haptic) { ControlHaptics(haptic, SecurityPreferences.getInstance(context)) }
}

/** Material's Switch with a light tap as it flips. Same parameters as Switch. */
@Composable
fun CherishSwitch(
    checked: Boolean,
    onCheckedChange: ((Boolean) -> Unit)?,
    modifier: Modifier = Modifier,
    thumbContent: (@Composable () -> Unit)? = null,
    enabled: Boolean = true,
    colors: SwitchColors = SwitchDefaults.colors(),
    interactionSource: MutableInteractionSource? = null,
) {
    val haptics = rememberControlHaptics()
    Switch(
        checked = checked,
        onCheckedChange = onCheckedChange?.let { change -> { on: Boolean -> haptics.toggle(on); change(on) } },
        modifier = modifier,
        thumbContent = thumbContent,
        enabled = enabled,
        colors = colors,
        interactionSource = interactionSource,
    )
}

/** Material's Checkbox with a light tap as it's ticked. Same parameters as Checkbox. */
@Composable
fun CherishCheckbox(
    checked: Boolean,
    onCheckedChange: ((Boolean) -> Unit)?,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    colors: CheckboxColors = CheckboxDefaults.colors(),
    interactionSource: MutableInteractionSource? = null,
) {
    val haptics = rememberControlHaptics()
    Checkbox(
        checked = checked,
        onCheckedChange = onCheckedChange?.let { change -> { on: Boolean -> haptics.toggle(on); change(on) } },
        modifier = modifier,
        enabled = enabled,
        colors = colors,
        interactionSource = interactionSource,
    )
}

/** Material's RadioButton with a light tick when a new option is chosen. Same parameters. */
@Composable
fun CherishRadioButton(
    selected: Boolean,
    onClick: (() -> Unit)?,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    colors: RadioButtonColors = RadioButtonDefaults.colors(),
    interactionSource: MutableInteractionSource? = null,
) {
    val haptics = rememberControlHaptics()
    RadioButton(
        selected = selected,
        onClick = onClick?.let { click -> { if (!selected) haptics.tick(); click() } },
        modifier = modifier,
        enabled = enabled,
        colors = colors,
        interactionSource = interactionSource,
    )
}

/** Material's FilterChip with a light tick when tapped. Same parameters as FilterChip. */
@Composable
fun CherishFilterChip(
    selected: Boolean,
    onClick: () -> Unit,
    label: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    leadingIcon: (@Composable () -> Unit)? = null,
    trailingIcon: (@Composable () -> Unit)? = null,
    shape: Shape = FilterChipDefaults.shape,
    colors: SelectableChipColors = FilterChipDefaults.filterChipColors(),
    elevation: SelectableChipElevation? = FilterChipDefaults.filterChipElevation(),
    border: BorderStroke? = FilterChipDefaults.filterChipBorder(enabled, selected),
    interactionSource: MutableInteractionSource? = null,
) {
    val haptics = rememberControlHaptics()
    FilterChip(
        selected = selected,
        onClick = { haptics.tick(); onClick() },
        label = label,
        modifier = modifier,
        enabled = enabled,
        leadingIcon = leadingIcon,
        trailingIcon = trailingIcon,
        shape = shape,
        colors = colors,
        elevation = elevation,
        border = border,
        interactionSource = interactionSource,
    )
}

/** Material's AssistChip with a light tick when tapped. Same parameters as AssistChip. */
@Composable
fun CherishAssistChip(
    onClick: () -> Unit,
    label: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    leadingIcon: (@Composable () -> Unit)? = null,
    trailingIcon: (@Composable () -> Unit)? = null,
    shape: Shape = AssistChipDefaults.shape,
    colors: ChipColors = AssistChipDefaults.assistChipColors(),
    elevation: ChipElevation? = AssistChipDefaults.assistChipElevation(),
    border: BorderStroke? = AssistChipDefaults.assistChipBorder(enabled),
    interactionSource: MutableInteractionSource? = null,
) {
    val haptics = rememberControlHaptics()
    AssistChip(
        onClick = { haptics.tick(); onClick() },
        label = label,
        modifier = modifier,
        enabled = enabled,
        leadingIcon = leadingIcon,
        trailingIcon = trailingIcon,
        shape = shape,
        colors = colors,
        elevation = elevation,
        border = border,
        interactionSource = interactionSource,
    )
}

/**
 * Material's Slider with fine ticks as it moves, like a dial: at each step of a stepped slider, or
 * every tenth of the way along a smooth one. Same parameters as Slider.
 */
@Composable
fun CherishSlider(
    value: Float,
    onValueChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    valueRange: ClosedFloatingPointRange<Float> = 0f..1f,
    steps: Int = 0,
    onValueChangeFinished: (() -> Unit)? = null,
    colors: SliderColors = SliderDefaults.colors(),
    interactionSource: MutableInteractionSource? = null,
) {
    val haptics = rememberControlHaptics()
    val segments = if (steps > 0) steps + 1 else 10
    fun segmentOf(v: Float): Int {
        val span = valueRange.endInclusive - valueRange.start
        if (span <= 0f) return 0
        return (((v - valueRange.start) / span) * segments).roundToInt()
    }
    val lastSegment = remember { mutableIntStateOf(segmentOf(value)) }
    Slider(
        value = value,
        onValueChange = { v ->
            val segment = segmentOf(v)
            if (segment != lastSegment.intValue) {
                lastSegment.intValue = segment
                haptics.fineTick()
            }
            onValueChange(v)
        },
        modifier = modifier,
        enabled = enabled,
        valueRange = valueRange,
        steps = steps,
        onValueChangeFinished = onValueChangeFinished,
        colors = colors,
        interactionSource = interactionSource ?: remember { MutableInteractionSource() },
    )
}
