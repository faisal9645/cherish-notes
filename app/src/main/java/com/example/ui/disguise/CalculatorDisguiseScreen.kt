package com.example.ui.disguise

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.isImeVisible
import androidx.compose.ui.input.pointer.changedToUp
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Backspace
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.rounded.SwapVert
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.security.SecurityPreferences
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.util.Locale
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.log10
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.math.tan

/**
 * Authentic Samsung One UI Calculator Disguise Screen.
 * Faithful recreation of the official Samsung Galaxy Calculator app:
 * - Pure AMOLED Dark Mode theme with One UI buttons & toolbar
 * - Full calculation engine with live result preview & order of operations
 * - Expandable Scientific Mode (trig, powers, logs, rad/deg)
 * - Complete Unit Converter (Length, Area, Weight, Temp, Volume, Speed, Time, Data)
 * - History Bottom Sheet
 * - Stealth Passcode / PIN unlock & long-press '=' unlock
 */
@OptIn(ExperimentalMaterial3Api::class, androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
fun CalculatorDisguiseScreen(
    securityPreferences: SecurityPreferences,
    onSecretGestureTriggered: (revealFrom: Offset?) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    val coroutineScope = rememberCoroutineScope()

    // Calculator states
    var expression by remember { mutableStateOf("0") }
    var historyEquation by remember { mutableStateOf("") }
    var isEvaluated by remember { mutableStateOf(false) }
    var isDegMode by remember { mutableStateOf(true) }
    var isScientificActive by remember { mutableStateOf(false) }

    val history = remember { mutableStateListOf<Pair<String, String>>() }
    var showHistorySheet by remember { mutableStateOf(false) }
    var showUnitConverterSheet by remember { mutableStateOf(false) }

    // Secret unlock transition state
    var isUnlockingSecret by remember { mutableStateOf(false) }
    val unlockAnimProgress = remember { Animatable(0f) }
    var equalsCenter by remember { mutableStateOf<Offset?>(null) }
    var toolbarCenter by remember { mutableStateOf<Offset?>(null) }

    // Focus & keyboard clearance: software keyboard will never be shown in Calculator decoy
    val keyboardController = androidx.compose.ui.platform.LocalSoftwareKeyboardController.current
    val focusManager = androidx.compose.ui.platform.LocalFocusManager.current
    val isImeVisible = WindowInsets.isImeVisible
    LaunchedEffect(Unit) {
        focusManager.clearFocus(force = true)
        keyboardController?.hide()
        com.example.notifications.NotificationHelper.clearNotifications(context)
    }
    LaunchedEffect(isImeVisible) {
        if (isImeVisible) {
            focusManager.clearFocus(force = true)
            keyboardController?.hide()
        }
    }

    fun triggerSecretUnlock(revealFrom: Offset?) {
        if (isUnlockingSecret) return
        isUnlockingSecret = true

        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
        try {
            val vibrator = context.getSystemService(Vibrator::class.java)
            if (vibrator != null && vibrator.hasVibrator()) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    vibrator.vibrate(
                        VibrationEffect.createWaveform(longArrayOf(0, 45, 75, 60), -1)
                    )
                } else {
                    @Suppress("DEPRECATION")
                    vibrator.vibrate(120)
                }
            }
        } catch (_: Exception) {}

        coroutineScope.launch {
            unlockAnimProgress.snapTo(0f)
            unlockAnimProgress.animateTo(
                targetValue = 1f,
                animationSpec = tween(550, easing = FastOutSlowInEasing)
            )
        }

        // Phone lock / biometric prompt if enabled
        if (securityPreferences.isRequirePhoneLockAfterHold()) {
            val activity = context as? androidx.fragment.app.FragmentActivity
            if (activity != null && com.example.security.BiometricHelper.isDeviceLockOrBiometricAvailable(context)) {
                com.example.security.BiometricHelper.showDeviceLockOrBiometricPrompt(
                    activity = activity,
                    title = "Confirm Phone Lock / Fingerprint",
                    subtitle = "Verify device credentials to open secret space",
                    onSuccess = {
                        coroutineScope.launch {
                            delay(250)
                            onSecretGestureTriggered(revealFrom ?: equalsCenter)
                            delay(400)
                            isUnlockingSecret = false
                        }
                    },
                    onError = {
                        isUnlockingSecret = false
                    }
                )
                return
            }
        }

        coroutineScope.launch {
            delay(520)
            onSecretGestureTriggered(revealFrom ?: equalsCenter)
            delay(400)
            isUnlockingSecret = false
        }
    }

    fun textToPhoneKeypad(text: String): String {
        val sb = StringBuilder()
        for (ch in text.lowercase()) {
            when (ch) {
                'a', 'b', 'c' -> sb.append('2')
                'd', 'e', 'f' -> sb.append('3')
                'g', 'h', 'i' -> sb.append('4')
                'j', 'k', 'l' -> sb.append('5')
                'm', 'n', 'o' -> sb.append('6')
                'p', 'q', 'r', 's' -> sb.append('7')
                't', 'u', 'v' -> sb.append('8')
                'w', 'x', 'y', 'z' -> sb.append('9')
                in '0'..'9' -> sb.append(ch)
            }
        }
        return sb.toString()
    }

    fun isSecretPasscode(input: String): Boolean {
        val clean = input.trim().replace(",", "").replace(" ", "")
        if (clean.isBlank()) return false
        if (securityPreferences.verifyCalculatorPasscode(clean)) return true
        val saved = securityPreferences.getCalculatorPasscode().trim()
        val keypadSaved = textToPhoneKeypad(saved)
        if (keypadSaved.isNotBlank() && clean == keypadSaved) return true
        return false
    }

    // Android back button / back gesture: immediately closes / minimizes calculator
    BackHandler(enabled = true) {
        if (showHistorySheet) {
            showHistorySheet = false
        } else if (showUnitConverterSheet) {
            showUnitConverterSheet = false
        } else {
            expression = "0"
            historyEquation = ""
            isEvaluated = false
            var ctx: Context? = context
            while (ctx is ContextWrapper) {
                if (ctx is Activity) break
                ctx = ctx.baseContext
            }
            val activity = ctx as? Activity
            activity?.moveTaskToBack(true)
            activity?.finishAffinity() ?: activity?.finish()
        }
    }

    // Official Samsung One UI Palette (Dynamic Day / Night Mode)
    val themeMode by securityPreferences.themeMode.collectAsState(initial = 0)
    val isSystemDark = isSystemInDarkTheme()
    val isDark = when (themeMode) {
        1 -> false
        2, 3 -> true
        else -> isSystemDark
    }

    // Dynamic System Bar Icons matching Samsung Calculator & permanent keyboard suppression
    val window = (context as? Activity)?.window
    DisposableEffect(isDark) {
        if (window != null) {
            val insetsController = androidx.core.view.WindowCompat.getInsetsController(window, window.decorView)
            insetsController.isAppearanceLightStatusBars = !isDark
            insetsController.isAppearanceLightNavigationBars = !isDark
            window.setSoftInputMode(android.view.WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN)
        }
        onDispose {}
    }

    val bgMain = if (isDark) Color(0xFF000000) else Color(0xFFFFFFFF)
    val numberBtnBg = if (isDark) Color(0xFF262628) else Color(0xFFF2F2F4)
    val numberBtnText = if (isDark) Color.White else Color(0xFF111111)
    val greenBtnBg = Color(0xFF2E7D32) // Official Samsung forest green in both Day and Night modes
    val greenBtnText = Color.White
    val clearRedText = Color(0xFFFF6565) // Coral red for "C"
    val equalsText = if (isDark) Color(0xFF101010) else Color.White
    val vibrantGreen = if (isDark) Color(0xFF2EB85C) else Color(0xFF249448)
    val dividerColor = if (isDark) Color(0xFF1E1E20) else Color(0xFFE5E5EA)
    val iconMuted = if (isDark) Color(0xFF8E8E93) else Color(0xFF5A5A5E)
    val primaryText = if (isDark) Color.White else Color(0xFF111111)
    val secondaryText = if (isDark) Color(0xFF8E8E93) else Color(0xFF767678)
    val scientificKeyBg = if (isDark) Color(0xFF18181A) else Color(0xFFF2F2F4)
    val scientificKeyText = if (isDark) Color(0xFFE5E5EA) else Color(0xFF1C1C1E)
    val sheetBg = if (isDark) Color(0xFF1C1C1E) else Color(0xFFFFFFFF)
    val sheetItemDivider = if (isDark) Color(0xFF2C2C2E) else Color(0xFFE5E5EA)

    // Live preview evaluation
    val liveResult = remember(expression, isDegMode) {
        if (!isEvaluated && expression.any { it in "+−×÷^%!√" || it.isLetter() }) {
            evaluateSamsungExpression(expression, isDegMode).getOrNull()
        } else null
    }

    fun onDigit(digit: String) {
        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
        if (isEvaluated) {
            expression = digit
            historyEquation = ""
            isEvaluated = false
        } else {
            if (expression == "0") {
                expression = digit
            } else {
                expression += digit
            }
        }
    }

    fun onDecimal() {
        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
        if (isEvaluated) {
            expression = "0."
            historyEquation = ""
            isEvaluated = false
            return
        }
        val lastToken = expression.split(" ", "(", ")", "+", "−", "×", "÷").lastOrNull().orEmpty()
        if (!lastToken.contains(".")) {
            if (expression.isEmpty() || expression.endsWith(" ") || expression.endsWith("(")) {
                expression += "0."
            } else {
                expression += "."
            }
        }
    }

    fun onOperator(op: String) {
        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
        if (isEvaluated) {
            isEvaluated = false
            historyEquation = ""
            expression = "$expression $op "
            return
        }
        val trimmed = expression.trimEnd()
        if (trimmed.endsWith("+") || trimmed.endsWith("−") || trimmed.endsWith("×") || trimmed.endsWith("÷")) {
            // Replace trailing operator
            val withoutOp = trimmed.dropLast(1).trimEnd()
            expression = if (withoutOp.isNotEmpty()) "$withoutOp $op " else "$op "
        } else {
            expression = "$expression $op "
        }
    }

    fun onParenthesis() {
        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
        if (isEvaluated) {
            expression = "("
            historyEquation = ""
            isEvaluated = false
            return
        }
        val openCount = expression.count { it == '(' }
        val closeCount = expression.count { it == ')' }
        val trimmed = expression.trimEnd()
        val lastChar = trimmed.lastOrNull()

        if (openCount > closeCount && lastChar != null && (lastChar.isDigit() || lastChar == ')' || lastChar == '%' || lastChar == 'π' || lastChar == 'e')) {
            expression += ")"
        } else {
            if (lastChar != null && (lastChar.isDigit() || lastChar == ')' || lastChar == '%' || lastChar == 'π' || lastChar == 'e')) {
                expression += " × ("
            } else {
                expression += if (expression == "0") "(" else "("
            }
        }
    }

    fun onPercentage() {
        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
        if (isEvaluated) {
            expression = "$expression%"
            isEvaluated = false
            return
        }
        val trimmed = expression.trimEnd()
        if (trimmed.isNotEmpty() && !trimmed.endsWith("+") && !trimmed.endsWith("−") && !trimmed.endsWith("×") && !trimmed.endsWith("÷")) {
            expression += "%"
        }
    }

    fun onPlusMinus() {
        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
        if (isEvaluated) {
            expression = if (expression.startsWith("−")) expression.removePrefix("−") else "−$expression"
            return
        }
        // Toggle sign of current trailing number
        val regex = Regex("""(.*?)([−\-]?)(\d+(?:\.\d+)?)$""")
        val match = regex.find(expression)
        if (match != null) {
            val prefix = match.groupValues[1]
            val sign = match.groupValues[2]
            val num = match.groupValues[3]
            expression = if (sign.isNotEmpty()) {
                "$prefix$num"
            } else {
                if (prefix.endsWith(" ")) "${prefix}−$num" else "$prefix(−$num)"
            }
        } else {
            if (expression == "0") expression = "−" else expression = "−$expression"
        }
    }

    fun onClear() {
        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
        expression = "0"
        historyEquation = ""
        isEvaluated = false
    }

    fun onBackspace() {
        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
        if (isEvaluated) {
            expression = "0"
            historyEquation = ""
            isEvaluated = false
            return
        }
        if (expression.length > 1) {
            // Drop trailing spaces and operators cleanly
            var s = expression
            if (s.endsWith(" ")) s = s.trimEnd()
            s = s.dropLast(1).trimEnd()
            expression = if (s.isEmpty() || s == "−") "0" else s
        } else {
            expression = "0"
        }
    }

    fun onScientificFn(fn: String) {
        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
        if (isEvaluated) {
            expression = if (fn in listOf("π", "e")) fn else "$fn("
            historyEquation = ""
            isEvaluated = false
            return
        }
        if (expression == "0") {
            expression = if (fn in listOf("π", "e")) fn else "$fn("
        } else {
            val trimmed = expression.trimEnd()
            val lastChar = trimmed.lastOrNull()
            if (lastChar != null && (lastChar.isDigit() || lastChar == ')' || lastChar == 'π' || lastChar == 'e')) {
                expression = "$trimmed × $fn("
            } else {
                expression = "$trimmed $fn("
            }
        }
    }

    fun onEquals() {
        haptic.performHapticFeedback(HapticFeedbackType.LongPress)

        // Secret passcode match check
        val passCandidate = expression.replace(" ", "").replace(",", "").trim()
        if (isSecretPasscode(passCandidate)) {
            expression = "0"
            historyEquation = ""
            triggerSecretUnlock(equalsCenter)
            return
        }

        val evalResult = evaluateSamsungExpression(expression, isDegMode)
        evalResult.onSuccess { res ->
            history.add(0, expression to res)
            historyEquation = expression
            expression = res
            isEvaluated = true
        }.onFailure {
            historyEquation = expression
            expression = it.message ?: "Error"
            isEvaluated = true
        }
    }

    val displayScrollState = rememberScrollState()
    LaunchedEffect(expression) {
        displayScrollState.animateScrollTo(displayScrollState.maxValue)
    }

    val sciIconRotation by animateFloatAsState(
        targetValue = if (isScientificActive) 90f else 0f,
        animationSpec = spring(dampingRatio = 0.75f, stiffness = Spring.StiffnessMediumLow),
        label = "sci_icon_rot"
    )

    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .background(bgMain)
            .circleReveal(equalsCenter ?: toolbarCenter) { unlockAnimProgress.value }
    ) {
        val screenHeight = maxHeight
        val isCompactScreen = screenHeight < 720.dp
        val isVerySmallScreen = screenHeight < 600.dp
        val keypadSpacing = if (isVerySmallScreen) 6.dp else if (isCompactScreen) 8.dp else 12.dp
        val scientificKeyHeight = if (isVerySmallScreen) 28.dp else if (isCompactScreen) 32.dp else 36.dp
        val horizontalPad = if (isCompactScreen) 14.dp else 20.dp

        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(horizontal = horizontalPad)
                .padding(bottom = if (isCompactScreen) 4.dp else 8.dp),
            verticalArrangement = Arrangement.Bottom
        ) {
            // Expanding top space: absorbs all upper space, pinning the calculator display & keypad
            // directly to the bottom of the screen like the original Samsung One UI Calculator
            Spacer(modifier = Modifier.weight(1f))

            // Display Section (Samsung One UI layout) with horizontal scroll
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 6.dp, vertical = if (isCompactScreen) 4.dp else 8.dp),
                horizontalAlignment = Alignment.End
            ) {
                // Secondary line: past evaluated equation or empty
                AnimatedVisibility(
                    visible = historyEquation.isNotEmpty(),
                    enter = fadeIn() + expandVertically(),
                    exit = fadeOut() + shrinkVertically()
                ) {
                    Text(
                        text = historyEquation,
                        fontSize = if (isCompactScreen) 16.sp else 20.sp,
                        color = secondaryText,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        textAlign = TextAlign.End,
                        fontFamily = FontFamily.SansSerif
                    )
                }

                Spacer(modifier = Modifier.height(if (isCompactScreen) 2.dp else 6.dp))

                // Primary line: current formula or final answer with dynamic font sizing & horizontal scroll
                val primaryFontSize = when {
                    isCompactScreen -> when {
                        expression.length > 15 -> 24.sp
                        expression.length > 11 -> 30.sp
                        expression.length > 8 -> 36.sp
                        else -> 46.sp
                    }
                    else -> when {
                        expression.length > 15 -> 30.sp
                        expression.length > 11 -> 40.sp
                        expression.length > 8 -> 48.sp
                        else -> 58.sp
                    }
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(displayScrollState),
                    horizontalArrangement = Arrangement.End
                ) {
                    Text(
                        text = expression,
                        fontSize = primaryFontSize,
                        fontWeight = FontWeight.Normal,
                        color = primaryText,
                        maxLines = 1,
                        textAlign = TextAlign.End,
                        fontFamily = FontFamily.SansSerif
                    )
                }

                // Live result preview (shows '= 60' in real time)
                AnimatedVisibility(
                    visible = liveResult != null && !isEvaluated,
                    enter = fadeIn(),
                    exit = fadeOut()
                ) {
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "= ${liveResult.orEmpty()}",
                        fontSize = if (isCompactScreen) 20.sp else 24.sp,
                        fontWeight = FontWeight.Normal,
                        color = secondaryText,
                        textAlign = TextAlign.End,
                        fontFamily = FontFamily.SansSerif
                    )
                }
            }

            // Samsung One UI Toolbar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 4.dp, vertical = 4.dp)
                    .onGloballyPositioned { coordinates ->
                        toolbarCenter = coordinates.boundsInRoot().center
                    },
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // History icon (tap opens history sheet)
                    IconButton(
                        onClick = { showHistorySheet = true },
                        modifier = Modifier.size(38.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.History,
                            contentDescription = "History",
                            tint = iconMuted,
                            modifier = Modifier.size(22.dp)
                        )
                    }

                    // Unit Converter icon
                    IconButton(
                        onClick = { showUnitConverterSheet = true },
                        modifier = Modifier.size(38.dp)
                    ) {
                        RulerIcon(color = iconMuted)
                    }

                    // Scientific calculator toggle with rotation animation
                    IconButton(
                        onClick = {
                            isScientificActive = !isScientificActive
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        },
                        modifier = Modifier
                            .size(38.dp)
                            .rotate(sciIconRotation)
                    ) {
                        ScientificSquareIcon(
                            color = if (isScientificActive) vibrantGreen else iconMuted
                        )
                    }
                }

                // Green backspace button on right
                val hasContent = expression != "0" || historyEquation.isNotEmpty()
                IconButton(
                    onClick = { onBackspace() },
                    modifier = Modifier
                        .size(38.dp)
                        .pointerInput(Unit) {
                            detectTapGestures(
                                onTap = { onBackspace() },
                                onLongPress = {
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    onClear()
                                }
                            )
                        }
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Rounded.Backspace,
                        contentDescription = "Backspace",
                        tint = if (hasContent) vibrantGreen else iconMuted.copy(alpha = 0.35f),
                        modifier = Modifier.size(24.dp)
                    )
                }
            }

            HorizontalDivider(
                color = dividerColor,
                thickness = 0.8.dp,
                modifier = Modifier.padding(vertical = if (isCompactScreen) 6.dp else 10.dp)
            )

            // Scientific Keypad Panel (Animated Expansion)
            AnimatedVisibility(
                visible = isScientificActive,
                enter = fadeIn() + expandVertically(),
                exit = fadeOut() + shrinkVertically()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = if (isCompactScreen) 6.dp else 10.dp),
                    verticalArrangement = Arrangement.spacedBy(if (isCompactScreen) 6.dp else 8.dp)
                ) {
                    // Scientific Row 1: RAD/DEG, sin, cos, tan
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(if (isCompactScreen) 6.dp else 8.dp)
                    ) {
                        ScientificKey(
                            text = if (isDegMode) "deg" else "rad",
                            isActive = isDegMode,
                            activeColor = vibrantGreen,
                            bgColor = scientificKeyBg,
                            textColor = scientificKeyText,
                            keyHeight = scientificKeyHeight,
                            modifier = Modifier.weight(1f),
                            onClick = {
                                isDegMode = !isDegMode
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            }
                        )
                        ScientificKey(text = "sin", bgColor = scientificKeyBg, textColor = scientificKeyText, keyHeight = scientificKeyHeight, modifier = Modifier.weight(1f), onClick = { onScientificFn("sin") })
                        ScientificKey(text = "cos", bgColor = scientificKeyBg, textColor = scientificKeyText, keyHeight = scientificKeyHeight, modifier = Modifier.weight(1f), onClick = { onScientificFn("cos") })
                        ScientificKey(text = "tan", bgColor = scientificKeyBg, textColor = scientificKeyText, keyHeight = scientificKeyHeight, modifier = Modifier.weight(1f), onClick = { onScientificFn("tan") })
                    }

                    // Scientific Row 2: ln, log, 1/x, e^x
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(if (isCompactScreen) 6.dp else 8.dp)
                    ) {
                        ScientificKey(text = "ln", bgColor = scientificKeyBg, textColor = scientificKeyText, keyHeight = scientificKeyHeight, modifier = Modifier.weight(1f), onClick = { onScientificFn("ln") })
                        ScientificKey(text = "log", bgColor = scientificKeyBg, textColor = scientificKeyText, keyHeight = scientificKeyHeight, modifier = Modifier.weight(1f), onClick = { onScientificFn("log") })
                        ScientificKey(text = "1/x", bgColor = scientificKeyBg, textColor = scientificKeyText, keyHeight = scientificKeyHeight, modifier = Modifier.weight(1f), onClick = { onScientificFn("1/") })
                        ScientificKey(text = "eˣ", bgColor = scientificKeyBg, textColor = scientificKeyText, keyHeight = scientificKeyHeight, modifier = Modifier.weight(1f), onClick = { onScientificFn("e^") })
                    }

                    // Scientific Row 3: x², xʸ, |x|, π
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(if (isCompactScreen) 6.dp else 8.dp)
                    ) {
                        ScientificKey(
                            text = "x²",
                            bgColor = scientificKeyBg,
                            textColor = scientificKeyText,
                            keyHeight = scientificKeyHeight,
                            modifier = Modifier.weight(1f),
                            onClick = {
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                expression += "^2"
                            }
                        )
                        ScientificKey(
                            text = "xʸ",
                            bgColor = scientificKeyBg,
                            textColor = scientificKeyText,
                            keyHeight = scientificKeyHeight,
                            modifier = Modifier.weight(1f),
                            onClick = { onOperator("^") }
                        )
                        ScientificKey(text = "|x|", bgColor = scientificKeyBg, textColor = scientificKeyText, keyHeight = scientificKeyHeight, modifier = Modifier.weight(1f), onClick = { onScientificFn("abs") })
                        ScientificKey(
                            text = "π",
                            bgColor = scientificKeyBg,
                            textColor = scientificKeyText,
                            keyHeight = scientificKeyHeight,
                            modifier = Modifier.weight(1f),
                            onClick = {
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                if (expression == "0" || isEvaluated) {
                                    expression = "π"
                                    isEvaluated = false
                                } else expression += "π"
                            }
                        )
                    }

                    // Scientific Row 4: √, e, !, ( )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(if (isCompactScreen) 6.dp else 8.dp)
                    ) {
                        ScientificKey(text = "√", bgColor = scientificKeyBg, textColor = scientificKeyText, keyHeight = scientificKeyHeight, modifier = Modifier.weight(1f), onClick = { onScientificFn("√") })
                        ScientificKey(
                            text = "e",
                            bgColor = scientificKeyBg,
                            textColor = scientificKeyText,
                            keyHeight = scientificKeyHeight,
                            modifier = Modifier.weight(1f),
                            onClick = {
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                if (expression == "0" || isEvaluated) {
                                    expression = "e"
                                    isEvaluated = false
                                } else expression += "e"
                            }
                        )
                        ScientificKey(
                            text = "!",
                            bgColor = scientificKeyBg,
                            textColor = scientificKeyText,
                            keyHeight = scientificKeyHeight,
                            modifier = Modifier.weight(1f),
                            onClick = {
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                expression += "!"
                            }
                        )
                        ScientificKey(text = "( )", bgColor = scientificKeyBg, textColor = scientificKeyText, keyHeight = scientificKeyHeight, modifier = Modifier.weight(1f), onClick = { onParenthesis() })
                    }
                }
            }

            // Samsung One UI Keypad Grid: 4 columns x 5 rows
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = if (isCompactScreen) 8.dp else 16.dp),
                verticalArrangement = Arrangement.spacedBy(keypadSpacing)
            ) {
                // Row 1: C (green with coral red C), ( ), %, ÷
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(keypadSpacing)
                ) {
                    SamsungRoundKey(
                        text = "C",
                        bgColor = greenBtnBg,
                        textColor = clearRedText,
                        modifier = Modifier.weight(1f),
                        onClick = { onClear() }
                    )
                    SamsungRoundKey(
                        text = "( )",
                        bgColor = greenBtnBg,
                        textColor = greenBtnText,
                        modifier = Modifier.weight(1f),
                        onClick = { onParenthesis() }
                    )
                    SamsungRoundKey(
                        text = "%",
                        bgColor = greenBtnBg,
                        textColor = greenBtnText,
                        modifier = Modifier.weight(1f),
                        onClick = { onPercentage() }
                    )
                    SamsungRoundKey(
                        text = "÷",
                        bgColor = greenBtnBg,
                        textColor = greenBtnText,
                        modifier = Modifier.weight(1f),
                        onClick = { onOperator("÷") }
                    )
                }

                // Row 2: 7, 8, 9, ×
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(keypadSpacing)
                ) {
                    SamsungRoundKey(text = "7", bgColor = numberBtnBg, textColor = numberBtnText, modifier = Modifier.weight(1f), onClick = { onDigit("7") })
                    SamsungRoundKey(text = "8", bgColor = numberBtnBg, textColor = numberBtnText, modifier = Modifier.weight(1f), onClick = { onDigit("8") })
                    SamsungRoundKey(text = "9", bgColor = numberBtnBg, textColor = numberBtnText, modifier = Modifier.weight(1f), onClick = { onDigit("9") })
                    SamsungRoundKey(text = "×", bgColor = greenBtnBg, textColor = greenBtnText, modifier = Modifier.weight(1f), onClick = { onOperator("×") })
                }

                // Row 3: 4, 5, 6, −
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(keypadSpacing)
                ) {
                    SamsungRoundKey(text = "4", bgColor = numberBtnBg, textColor = numberBtnText, modifier = Modifier.weight(1f), onClick = { onDigit("4") })
                    SamsungRoundKey(text = "5", bgColor = numberBtnBg, textColor = numberBtnText, modifier = Modifier.weight(1f), onClick = { onDigit("5") })
                    SamsungRoundKey(text = "6", bgColor = numberBtnBg, textColor = numberBtnText, modifier = Modifier.weight(1f), onClick = { onDigit("6") })
                    SamsungRoundKey(text = "−", bgColor = greenBtnBg, textColor = greenBtnText, modifier = Modifier.weight(1f), onClick = { onOperator("−") })
                }

                // Row 4: 1, 2, 3, +
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(keypadSpacing)
                ) {
                    SamsungRoundKey(text = "1", bgColor = numberBtnBg, textColor = numberBtnText, modifier = Modifier.weight(1f), onClick = { onDigit("1") })
                    SamsungRoundKey(text = "2", bgColor = numberBtnBg, textColor = numberBtnText, modifier = Modifier.weight(1f), onClick = { onDigit("2") })
                    SamsungRoundKey(text = "3", bgColor = numberBtnBg, textColor = numberBtnText, modifier = Modifier.weight(1f), onClick = { onDigit("3") })
                    SamsungRoundKey(text = "+", bgColor = greenBtnBg, textColor = greenBtnText, modifier = Modifier.weight(1f), onClick = { onOperator("+") })
                }

                // Row 5: +/-, 0, ., = (Black = on green circle)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(keypadSpacing)
                ) {
                    SamsungRoundKey(
                        text = "+/-",
                        bgColor = numberBtnBg,
                        textColor = numberBtnText,
                        modifier = Modifier.weight(1f),
                        onClick = { onPlusMinus() }
                    )
                    SamsungRoundKey(text = "0", bgColor = numberBtnBg, textColor = numberBtnText, modifier = Modifier.weight(1f), onClick = { onDigit("0") })
                    SamsungRoundKey(
                        text = ".",
                        bgColor = numberBtnBg,
                        textColor = numberBtnText,
                        modifier = Modifier.weight(1f),
                        onClick = { onDecimal() }
                    )
                    val equalsHoldSec = securityPreferences.getEqualsHoldDuration()
                    SamsungRoundKey(
                        text = "=",
                        bgColor = greenBtnBg,
                        textColor = equalsText,
                        modifier = Modifier
                            .weight(1f)
                            .onGloballyPositioned { coordinates ->
                                equalsCenter = coordinates.boundsInRoot().center
                            },
                        customHoldDurationMs = equalsHoldSec * 1000L,
                        onClick = { onEquals() },
                        onLongClick = {
                            triggerSecretUnlock(equalsCenter)
                        }
                    )
                }
            }
        }
    }

    // Calculation History Bottom Sheet
    if (showHistorySheet) {
        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ModalBottomSheet(
            onDismissRequest = { showHistorySheet = false },
            sheetState = sheetState,
            containerColor = sheetBg
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 8.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("History", fontSize = 20.sp, fontWeight = FontWeight.SemiBold, color = primaryText)
                    if (history.isNotEmpty()) {
                        TextButton(onClick = { history.clear() }) {
                            Text("Clear history", color = vibrantGreen)
                        }
                    }
                }
                Spacer(modifier = Modifier.height(14.dp))

                if (history.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 48.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            "No history",
                            color = iconMuted,
                            fontSize = 15.sp
                        )
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(340.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        items(history) { (eq, res) ->
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        expression = res
                                        historyEquation = eq
                                        isEvaluated = true
                                        showHistorySheet = false
                                    }
                                    .padding(vertical = 6.dp),
                                horizontalAlignment = Alignment.End
                            ) {
                                Text(eq, fontSize = 16.sp, color = secondaryText)
                                Text("= $res", fontSize = 24.sp, fontWeight = FontWeight.Normal, color = primaryText)
                            }
                            HorizontalDivider(color = sheetItemDivider, thickness = 0.8.dp)
                        }
                    }
                }
                Spacer(modifier = Modifier.height(16.dp))
            }
        }
    }

    // Comprehensive Samsung Unit Converter Bottom Sheet
    if (showUnitConverterSheet) {
        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        var selectedCategoryIndex by remember { mutableStateOf(0) }
        val categories = listOf("Length", "Area", "Weight", "Temperature", "Volume", "Speed", "Time", "Data")

        val currentVal = expression.replace(",", "").replace(" ", "").toDoubleOrNull() ?: 1.0

        ModalBottomSheet(
            onDismissRequest = { showUnitConverterSheet = false },
            sheetState = sheetState,
            containerColor = sheetBg
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 8.dp)
            ) {
                Text(
                    "Unit converter",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = primaryText
                )
                Spacer(modifier = Modifier.height(12.dp))

                ScrollableTabRow(
                    selectedTabIndex = selectedCategoryIndex,
                    containerColor = sheetBg,
                    contentColor = vibrantGreen,
                    edgePadding = 0.dp
                ) {
                    categories.forEachIndexed { index, title ->
                        Tab(
                            selected = selectedCategoryIndex == index,
                            onClick = { selectedCategoryIndex = index },
                            text = {
                                Text(
                                    title,
                                    color = if (selectedCategoryIndex == index) vibrantGreen else iconMuted,
                                    fontSize = 14.sp
                                )
                            }
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Unit Conversion Matrix
                val rows = remember(selectedCategoryIndex, currentVal) {
                    getUnitConversions(selectedCategoryIndex, currentVal)
                }

                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(360.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(rows) { (unitName, convertedVal) ->
                        UnitRow(unit = unitName, value = convertedVal, isDark = isDark)
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))
            }
        }
    }
}

/**
 * Authentic circular key for Samsung One UI Calculator with spring press animation and responsive font scaling.
 */
@Composable
private fun SamsungRoundKey(
    text: String,
    bgColor: Color,
    textColor: Color,
    modifier: Modifier = Modifier,
    customHoldDurationMs: Long? = null,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)? = null
) {
    var isPressed by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.92f else 1.0f,
        animationSpec = spring(
            dampingRatio = 0.65f,
            stiffness = Spring.StiffnessMediumLow
        ),
        label = "round_btn_scale"
    )

    val currentOnClick by androidx.compose.runtime.rememberUpdatedState(onClick)
    val currentOnLongClick by androidx.compose.runtime.rememberUpdatedState(onLongClick)

    BoxWithConstraints(
        modifier = modifier
            .scale(scale)
            .aspectRatio(1f)
            .clip(CircleShape)
            .background(if (isPressed) bgColor.copy(alpha = 0.82f) else bgColor)
            .pointerInput(customHoldDurationMs, currentOnLongClick) {
                if (customHoldDurationMs != null && customHoldDurationMs > 0L && currentOnLongClick != null) {
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        down.consume()
                        isPressed = true
                        try {
                            val upOrCancel = withTimeoutOrNull(customHoldDurationMs) {
                                waitForUpOrCancellation()
                            }
                            if (upOrCancel == null) {
                                isPressed = false
                                currentOnLongClick?.invoke()
                                waitForUpOrCancellation()
                            } else if (upOrCancel.changedToUp()) {
                                upOrCancel.consume()
                                isPressed = false
                                currentOnClick()
                            } else {
                                isPressed = false
                            }
                        } finally {
                            isPressed = false
                        }
                    }
                } else {
                    detectTapGestures(
                        onPress = {
                            isPressed = true
                            tryAwaitRelease()
                            isPressed = false
                        },
                        onTap = { currentOnClick() },
                        onLongPress = { currentOnLongClick?.invoke() }
                    )
                }
            },
        contentAlignment = Alignment.Center
    ) {
        val btnW = maxWidth
        val fontSize = when {
            text.length > 2 -> (btnW.value * 0.32f).coerceIn(13f, 20f).sp
            text == "=" || text == "C" || text == "%" -> (btnW.value * 0.44f).coerceIn(19f, 28f).sp
            else -> (btnW.value * 0.46f).coerceIn(21f, 29f).sp
        }

        Text(
            text = text,
            fontSize = fontSize,
            fontWeight = FontWeight.Normal,
            color = textColor,
            fontFamily = FontFamily.SansSerif,
            maxLines = 1,
            softWrap = false
        )
    }
}

/**
 * Scientific pill key in One UI style with spring touch feedback and responsive height.
 */
@Composable
private fun ScientificKey(
    text: String,
    bgColor: Color,
    modifier: Modifier = Modifier,
    keyHeight: androidx.compose.ui.unit.Dp = 36.dp,
    isActive: Boolean = false,
    activeColor: Color = Color(0xFF2EB85C),
    textColor: Color = Color(0xFFE5E5EA),
    onClick: () -> Unit
) {
    var isPressed by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.93f else 1.0f,
        animationSpec = spring(
            dampingRatio = 0.7f,
            stiffness = Spring.StiffnessMedium
        ),
        label = "sci_key_scale"
    )

    Box(
        modifier = modifier
            .scale(scale)
            .height(keyHeight)
            .clip(RoundedCornerShape(10.dp))
            .background(
                if (isActive) activeColor.copy(alpha = 0.25f)
                else if (isPressed) bgColor.copy(alpha = 0.8f)
                else bgColor
            )
            .pointerInput(Unit) {
                detectTapGestures(
                    onPress = {
                        isPressed = true
                        tryAwaitRelease()
                        isPressed = false
                    },
                    onTap = { onClick() }
                )
            },
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            fontSize = if (keyHeight < 32.dp) 12.sp else 14.sp,
            fontWeight = if (isActive) FontWeight.SemiBold else FontWeight.Normal,
            color = if (isActive) activeColor else textColor,
            fontFamily = FontFamily.SansSerif
        )
    }
}

/**
 * Ruler Icon matching Samsung Calculator toolbar.
 */
@Composable
private fun RulerIcon(color: Color, modifier: Modifier = Modifier) {
    Canvas(modifier = modifier.size(20.dp)) {
        val w = size.width
        val h = size.height
        val strokeW = 1.6f.dp.toPx()

        drawRoundRect(
            color = color,
            topLeft = Offset(1f, h * 0.25f),
            size = Size(w - 2f, h * 0.50f),
            cornerRadius = CornerRadius(3f.dp.toPx()),
            style = Stroke(width = strokeW)
        )

        val tickCount = 4
        val step = (w - 6f) / (tickCount + 1)
        for (i in 1..tickCount) {
            val x = 3f + i * step
            drawLine(
                color = color,
                start = Offset(x, h * 0.25f),
                end = Offset(x, h * (if (i % 2 == 0) 0.52f else 0.42f)),
                strokeWidth = strokeW
            )
        }
    }
}

/**
 * Scientific icon matching Samsung Calculator toolbar (square with math symbols).
 */
@Composable
private fun ScientificSquareIcon(color: Color, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .size(20.dp)
            .clip(RoundedCornerShape(3.dp))
            .background(Color.Transparent),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            drawRoundRect(
                color = color,
                topLeft = Offset(0f, 0f),
                size = size,
                cornerRadius = CornerRadius(3f.dp.toPx()),
                style = Stroke(width = 1.4f.dp.toPx())
            )
        }
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                "√π",
                fontSize = 7.sp,
                fontWeight = FontWeight.Bold,
                color = color,
                lineHeight = 7.sp
            )
            Text(
                "e=",
                fontSize = 6.5.sp,
                fontWeight = FontWeight.Bold,
                color = color,
                lineHeight = 7.sp
            )
        }
    }
}

@Composable
private fun UnitRow(unit: String, value: String, isDark: Boolean = true) {
    val subColor = if (isDark) Color(0xFF8E8E93) else Color(0xFF767678)
    val mainColor = if (isDark) Color.White else Color(0xFF111111)
    val divColor = if (isDark) Color(0xFF2C2C2E) else Color(0xFFE5E5EA)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 10.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(unit, fontSize = 16.sp, color = subColor)
        Text(value, fontSize = 18.sp, fontWeight = FontWeight.Medium, color = mainColor)
    }
    HorizontalDivider(color = divColor, thickness = 0.8.dp)
}

/**
 * Unit conversion calculator helper.
 */
private fun getUnitConversions(categoryIndex: Int, value: Double): List<Pair<String, String>> {
    val df = DecimalFormat("#,##0.####", DecimalFormatSymbols(Locale.US))
    return when (categoryIndex) {
        0 -> { // Length (base: meter)
            val m = value
            listOf(
                "Meter (m)" to df.format(m),
                "Centimeter (cm)" to df.format(m * 100.0),
                "Millimeter (mm)" to df.format(m * 1000.0),
                "Kilometer (km)" to df.format(m / 1000.0),
                "Inch (in)" to df.format(m * 39.3701),
                "Foot (ft)" to df.format(m * 3.28084),
                "Yard (yd)" to df.format(m * 1.09361),
                "Mile (mi)" to df.format(m / 1609.34)
            )
        }
        1 -> { // Area (base: m²)
            val m2 = value
            listOf(
                "Square meter (m²)" to df.format(m2),
                "Square foot (ft²)" to df.format(m2 * 10.7639),
                "Acre (ac)" to df.format(m2 / 4046.86),
                "Hectare (ha)" to df.format(m2 / 10000.0),
                "Square kilometer (km²)" to df.format(m2 / 1_000_000.0),
                "Square inch (in²)" to df.format(m2 * 1550.0)
            )
        }
        2 -> { // Weight (base: kg)
            val kg = value
            listOf(
                "Kilogram (kg)" to df.format(kg),
                "Gram (g)" to df.format(kg * 1000.0),
                "Milligram (mg)" to df.format(kg * 1_000_000.0),
                "Pound (lb)" to df.format(kg * 2.20462),
                "Ounce (oz)" to df.format(kg * 35.274),
                "Tonne (t)" to df.format(kg / 1000.0)
            )
        }
        3 -> { // Temperature
            val c = value
            listOf(
                "Celsius (°C)" to df.format(c),
                "Fahrenheit (°F)" to df.format((c * 9.0 / 5.0) + 32.0),
                "Kelvin (K)" to df.format(c + 273.15)
            )
        }
        4 -> { // Volume (base: L)
            val l = value
            listOf(
                "Liter (L)" to df.format(l),
                "Milliliter (mL)" to df.format(l * 1000.0),
                "Gallon (US gal)" to df.format(l / 3.78541),
                "Quart (US qt)" to df.format(l / 0.946353),
                "Pint (US pt)" to df.format(l / 0.473176),
                "Fluid ounce (fl oz)" to df.format(l * 33.814),
                "Cubic meter (m³)" to df.format(l / 1000.0)
            )
        }
        5 -> { // Speed (base: km/h)
            val kmh = value
            listOf(
                "Kilometer/hour (km/h)" to df.format(kmh),
                "Meter/second (m/s)" to df.format(kmh / 3.6),
                "Mile/hour (mph)" to df.format(kmh / 1.60934),
                "Knot (kn)" to df.format(kmh / 1.852)
            )
        }
        6 -> { // Time (base: s)
            val s = value
            listOf(
                "Second (s)" to df.format(s),
                "Minute (min)" to df.format(s / 60.0),
                "Hour (h)" to df.format(s / 3600.0),
                "Day (d)" to df.format(s / 86400.0),
                "Week (wk)" to df.format(s / 604800.0),
                "Millisecond (ms)" to df.format(s * 1000.0)
            )
        }
        7 -> { // Data (base: MB)
            val mb = value
            listOf(
                "Megabyte (MB)" to df.format(mb),
                "Kilobyte (KB)" to df.format(mb * 1024.0),
                "Gigabyte (GB)" to df.format(mb / 1024.0),
                "Terabyte (TB)" to df.format(mb / (1024.0 * 1024.0)),
                "Byte (B)" to df.format(mb * 1024.0 * 1024.0)
            )
        }
        else -> emptyList()
    }
}

/**
 * Robust mathematical expression parser for Samsung Calculator.
 * Accurately supports BODMAS order of operations, trigonometry (DEG/RAD),
 * logarithms, powers, factorials, percentages, and clean number formatting.
 */
fun evaluateSamsungExpression(rawExpr: String, isDeg: Boolean): Result<String> {
    return runCatching {
        var clean = rawExpr
            .replace("×", "*")
            .replace("÷", "/")
            .replace("−", "-")
            .replace("π", Math.PI.toString())
            .replace("e", Math.E.toString())

        // Trim trailing operators if user is typing
        var trimmed = clean.trim()
        while (trimmed.endsWith("+") || trimmed.endsWith("-") || trimmed.endsWith("*") || trimmed.endsWith("/") || trimmed.endsWith("^")) {
            trimmed = trimmed.dropLast(1).trim()
        }
        if (trimmed.isBlank()) return@runCatching "0"

        // Handle percentages in addition/subtraction: e.g. 100 + 10% -> 100 + (100 * 10 / 100)
        val percentAddSub = Regex("""(\d+(?:\.\d+)?)\s*([\+\-])\s*(\d+(?:\.\d+)?)%""")
        while (percentAddSub.containsMatchIn(trimmed)) {
            trimmed = percentAddSub.replace(trimmed) { match ->
                val base = match.groupValues[1]
                val op = match.groupValues[2]
                val pct = match.groupValues[3]
                "$base $op ($base * $pct / 100)"
            }
        }
        // Handle remaining percentage: 50% -> (50 / 100)
        trimmed = trimmed.replace(Regex("""(\d+(?:\.\d+)?)%"""), "($1 / 100)")

        // Parse and calculate
        val parser = SamsungExpressionParser(trimmed, isDeg)
        val value = parser.parse()

        formatSamsungResult(value)
    }
}

private fun formatSamsungResult(value: Double): String {
    if (value.isNaN()) return "Invalid input"
    if (value.isInfinite()) return if (value > 0) "Infinity" else "-Infinity"
    if (abs(value) == 0.0) return "0"

    val absVal = abs(value)
    if (absVal >= 1e12 || (absVal < 1e-9 && absVal > 0.0)) {
        val df = DecimalFormat("0.######E0", DecimalFormatSymbols(Locale.US))
        return df.format(value).replace("E", "e")
    }

    val df = DecimalFormat("#,##0.##########", DecimalFormatSymbols(Locale.US))
    var res = df.format(value)
    if (res == "-0" || res == "-0.0") res = "0"
    return res
}

/**
 * Recursive descent expression parser.
 */
private class SamsungExpressionParser(private val expr: String, private val isDeg: Boolean) {
    private var pos = -1
    private var ch = 0

    private fun nextChar() {
        ch = if (++pos < expr.length) expr[pos].code else -1
    }

    private fun eat(charToEat: Int): Boolean {
        while (ch == ' '.code) nextChar()
        if (ch == charToEat) {
            nextChar()
            return true
        }
        return false
    }

    fun parse(): Double {
        nextChar()
        val x = parseExpression()
        if (pos < expr.length) {
            while (ch == ' '.code) nextChar()
            if (pos < expr.length) throw IllegalArgumentException("Unexpected character")
        }
        return x
    }

    private fun parseExpression(): Double {
        var x = parseTerm()
        while (true) {
            if (eat('+'.code)) x += parseTerm()
            else if (eat('-'.code)) x -= parseTerm()
            else return x
        }
    }

    private fun parseTerm(): Double {
        var x = parseFactor()
        while (true) {
            if (eat('*'.code)) x *= parseFactor()
            else if (eat('/'.code)) {
                val divisor = parseFactor()
                if (divisor == 0.0) throw ArithmeticException("Can't divide by zero")
                x /= divisor
            } else return x
        }
    }

    private fun parseFactor(): Double {
        if (eat('+'.code)) return parseFactor()
        if (eat('-'.code)) return -parseFactor()

        var x: Double
        val startPos = this.pos
        if (eat('('.code)) {
            x = parseExpression()
            eat(')'.code)
        } else if ((ch in '0'.code..'9'.code) || ch == '.'.code) {
            while ((ch in '0'.code..'9'.code) || ch == '.'.code) nextChar()
            x = expr.substring(startPos, this.pos).toDoubleOrNull() ?: 0.0
        } else if (ch in 'a'.code..'z'.code || ch == '√'.code) {
            while (ch in 'a'.code..'z'.code || ch == '√'.code) nextChar()
            val fn = expr.substring(startPos, this.pos)
            x = parseFactor()
            x = when (fn) {
                "sqrt", "√" -> if (x < 0) throw IllegalArgumentException("Invalid input") else sqrt(x)
                "sin" -> sin(if (isDeg) Math.toRadians(x) else x)
                "cos" -> cos(if (isDeg) Math.toRadians(x) else x)
                "tan" -> tan(if (isDeg) Math.toRadians(x) else x)
                "ln" -> if (x <= 0) throw IllegalArgumentException("Invalid input") else ln(x)
                "log" -> if (x <= 0) throw IllegalArgumentException("Invalid input") else log10(x)
                "abs" -> abs(x)
                "exp" -> exp(x)
                else -> throw IllegalArgumentException("Unknown function: $fn")
            }
        } else {
            throw IllegalArgumentException("Syntax error")
        }

        if (eat('^'.code)) x = x.pow(parseFactor())
        if (eat('!'.code)) {
            if (x < 0 || x > 170) throw IllegalArgumentException("Invalid input")
            var fact = 1.0
            val n = x.toInt()
            for (i in 2..n) fact *= i
            x = fact
        }

        return x
    }
}
