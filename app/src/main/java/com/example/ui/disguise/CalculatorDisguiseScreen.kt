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
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.togetherWith
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onSizeChanged
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
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.isImeVisible
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.Backspace
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.rounded.ArrowDownward
import androidx.compose.material.icons.rounded.ArrowDropDown
import androidx.compose.material.icons.rounded.ArrowUpward
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.changedToUp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
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
 * Exact replication of official Samsung Galaxy Calculator app:
 * - Accurate Day & Night palettes: circular light grey (#F2F3F5) / dark (#212123) buttons,
 *   green operators (#278829 / #2BB532), coral C (#FF6565), solid green = button (#35A324 / #2BB532).
 * - Exact expression styling: operators colored Samsung green, numbers black/white.
 * - Live evaluation result preview without '= ' prefix.
 * - Exact One UI spring touch physics and micro-animations on all keys.
 * - Portrait inline history view (Screenshot 4) with bold green answer and capsule 'Clear history' button.
 * - Full-screen Unit Converter (Screenshot 1) with category chips, unit dropdowns, and 4x4 keypad.
 * - Scientific calculator panel with trig, logs, powers.
 * - Stealth passcode / PIN unlock & hold '=' unlock.
 * - Permanent software keyboard suppression in decoy mode.
 */
/** The calculator's past results, kept on the phone so they're still there next time (like Samsung's). */
private object CalculatorHistoryStore {
    private const val PREFS = "calculator_history"
    private const val KEY = "entries"
    private var loaded = false

    val entries = androidx.compose.runtime.mutableStateListOf<Pair<String, String>>()

    fun load(context: Context) {
        if (loaded) return
        loaded = true
        val raw = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY, null) ?: return
        runCatching {
            val array = org.json.JSONArray(raw)
            val list = (0 until array.length()).map { i ->
                val o = array.getJSONObject(i)
                o.getString("e") to o.getString("r")
            }
            entries.clear()
            entries.addAll(list)
        }
    }

    fun save(context: Context) {
        val array = org.json.JSONArray()
        entries.take(100).forEach { (e, r) -> array.put(org.json.JSONObject().put("e", e).put("r", r)) }
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(KEY, array.toString()).apply()
    }
}

/** The display line, with the "=" count it belongs to (a new count animates in as a result). */
private data class CalcLine(val text: androidx.compose.ui.text.AnnotatedString, val generation: Int)

/**
 * The sum as shown: each number's whole part in groups of three ("12345.678" -> "12,345.678"),
 * as Samsung's calculator does while typing. Only for showing; the sum itself has no separators.
 */
private fun groupDigitsForDisplay(expression: String): String {
    val source = expression.replace(",", "")
    val out = StringBuilder(source.length + 8)
    var i = 0
    while (i < source.length) {
        val c = source[i]
        if (c.isDigit()) {
            // A run of digits: group it unless it's the part after a decimal point
            val start = i
            while (i < source.length && source[i].isDigit()) i++
            val run = source.substring(start, i)
            val afterPoint = start > 0 && source[start - 1] == '.'
            if (afterPoint || run.length <= 3) {
                out.append(run)
            } else {
                val firstGroup = run.length % 3
                if (firstGroup > 0) out.append(run, 0, firstGroup)
                var j = firstGroup
                while (j < run.length) {
                    if (j > 0) out.append(',')
                    out.append(run, j, j + 3)
                    j += 3
                }
            }
        } else {
            out.append(c)
            i++
        }
    }
    return out.toString()
}

/** A thin cursor after the last digit, blinking about twice a second like a text field's. */
@Composable
private fun BlinkingCursor(color: Color, height: androidx.compose.ui.unit.Dp) {
    val transition = androidx.compose.animation.core.rememberInfiniteTransition(label = "calc_cursor")
    val alpha by transition.animateFloat(
        initialValue = 1f,
        targetValue = 0f,
        animationSpec = androidx.compose.animation.core.infiniteRepeatable(
            animation = androidx.compose.animation.core.keyframes {
                durationMillis = 1000
                1f at 0
                1f at 499
                0f at 500
                0f at 999
            },
            repeatMode = androidx.compose.animation.core.RepeatMode.Restart
        ),
        label = "calc_cursor_alpha"
    )
    Box(
        modifier = Modifier
            .padding(start = 2.dp)
            .width(2.dp)
            .height(height)
            .graphicsLayer { this.alpha = alpha }
            .background(color)
    )
}

@OptIn(ExperimentalMaterial3Api::class, androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
fun CalculatorDisguiseScreen(
    securityPreferences: SecurityPreferences,
    onSecretGestureTriggered: (revealFrom: Offset?) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val activity = remember(context) {
        var c: Context? = context
        while (c is ContextWrapper) { if (c is Activity) break; c = c.baseContext }
        c as? Activity
    }
    val haptic = LocalHapticFeedback.current
    val coroutineScope = rememberCoroutineScope()

    // Calculator states
    var expression by remember { mutableStateOf("0") }
    var historyEquation by remember { mutableStateOf("") }
    var isEvaluated by remember { mutableStateOf(false) }
    // Goes up with each "=": the result slides up into place, the sum moves out above it
    var evalCount by remember { mutableIntStateOf(0) }
    var isDegMode by remember { mutableStateOf(true) }
    var isScientificActive by remember { mutableStateOf(false) }

    // Kept across opens and restarts, like a real calculator
    val history = CalculatorHistoryStore.entries
    LaunchedEffect(Unit) { CalculatorHistoryStore.load(context) }
    var showHistoryInline by remember { mutableStateOf(false) }
    // The keypad's measured height, so the inline history takes exactly the same space and the
    // display above never moves when you open history or the unit converter
    val density = androidx.compose.ui.platform.LocalDensity.current
    var keypadHeightPx by remember { mutableIntStateOf(0) }
    var showUnitConverterScreen by remember { mutableStateOf(false) }

    // Secret unlock transition state
    var isUnlockingSecret by remember { mutableStateOf(false) }
    val unlockAnimProgress = remember { Animatable(0f) }
    var equalsCenter by remember { mutableStateOf<Offset?>(null) }
    var toolbarCenter by remember { mutableStateOf<Offset?>(null) }

    // Focus & keyboard suppression: keyboard will never be shown in Calculator decoy
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
                    title = "Confirm it's you",
                    subtitle = "Use your fingerprint or screen lock",
                    onSuccess = {
                        coroutineScope.launch {
                            delay(250)
                            onSecretGestureTriggered(revealFrom ?: equalsCenter)
                            delay(400)
                            isUnlockingSecret = false
                        }
                    },
                    onError = {
                        // Cancelled: close the reveal circle so the calculator shows again (not a
                        // blank cut-out onto the hidden app)
                        isUnlockingSecret = false
                        coroutineScope.launch { unlockAnimProgress.snapTo(0f) }
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

    // Android back button / back gesture: close the converter or history first; otherwise
    // cleanly exit the calculator app, returning to launcher or previous app.
    BackHandler(enabled = true) {
        if (showUnitConverterScreen) {
            showUnitConverterScreen = false
        } else if (showHistoryInline) {
            showHistoryInline = false
        } else {
            expression = "0"
            historyEquation = ""
            isEvaluated = false
            var ctx: Context? = context
            while (ctx is ContextWrapper) {
                if (ctx is Activity) break
                ctx = ctx.baseContext
            }
            val act = (ctx as? Activity) ?: activity
            if (act != null) {
                val moved = act.moveTaskToBack(true)
                if (!moved) {
                    act.finish()
                }
            }
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

    // Dynamic System Bar Icons matching Samsung Calculator
    val window = (context as? Activity)?.window
    DisposableEffect(isDark) {
        if (window != null) {
            val insetsController = androidx.core.view.WindowCompat.getInsetsController(window, window.decorView)
            insetsController.isAppearanceLightStatusBars = !isDark
            insetsController.isAppearanceLightNavigationBars = !isDark
        }
        onDispose {}
    }

    // The calculator never raises the keyboard; the chat underneath still needs it to resize the
    // layout, so the soft-input mode is put back to adjustResize when the calculator leaves.
    DisposableEffect(window) {
        window?.setSoftInputMode(android.view.WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN)
        // A basic calculator shows upright; its five key rows don't fit a short landscape screen
        val previousOrientation = activity?.requestedOrientation
        activity?.requestedOrientation = android.content.pm.ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        onDispose {
            window?.setSoftInputMode(android.view.WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
            previousOrientation?.let { activity?.requestedOrientation = it }
        }
    }

    // Precise Samsung One UI Colors (matching Screenshots 1, 2, 3, 4)
    val bgMain = if (isDark) Color(0xFF000000) else Color(0xFFFFFFFF)
    val keyCircleBg = if (isDark) Color(0xFF212123) else Color(0xFFF2F3F5)
    val numberBtnText = if (isDark) Color(0xFFFFFFFF) else Color(0xFF1E1E1E)
    val operatorTextColor = if (isDark) Color(0xFF2BB532) else Color(0xFF278829) // Samsung Forest Green in Day
    val clearRedText = Color(0xFFFF6565) // Samsung Coral Red for "C"
    val equalsCircleBg = if (isDark) Color(0xFF2BB532) else Color(0xFF35A324) // Solid Green Circle for "="
    val equalsText = Color.White
    val dividerColor = if (isDark) Color(0xFF1E1E20) else Color(0xFFE5E5EA)
    val iconMuted = if (isDark) Color(0xFF8E8E93) else Color(0xFF767678)
    val primaryText = if (isDark) Color(0xFFFFFFFF) else Color(0xFF1E1E1E)
    val secondaryText = if (isDark) Color(0xFF8E8E93) else Color(0xFF767678)
    val scientificKeyBg = if (isDark) Color(0xFF18181A) else Color(0xFFF2F3F5)
    val scientificKeyText = if (isDark) Color(0xFFE5E5EA) else Color(0xFF1E1E1E)
    val chipActiveBg = if (isDark) Color(0xFF212123) else Color(0xFFF0F1F3)

    // Live preview evaluation (e.g. "1260")
    val liveResult = remember(expression, isDegMode) {
        if (!isEvaluated && expression.any { it in "+−×÷^%!√" || it.isLetter() }) {
            evaluateSamsungExpression(expression, isDegMode).getOrNull()
        } else null
    }

    fun onDigit(digit: String) {
        haptic.performHapticFeedback(HapticFeedbackType.KeyboardTap)
        if (isEvaluated) {
            expression = digit
            historyEquation = ""
            isEvaluated = false
        } else {
            // Samsung's limits: 15 digits in a number, 10 after its decimal point
            val number = expression.takeLastWhile { it.isDigit() || it == '.' }
            val digitsInNumber = number.count { it.isDigit() }
            val afterPoint = number.substringAfter('.', "").length
            if (number.contains('.') && afterPoint >= 10) {
                android.widget.Toast.makeText(context, "Can't enter more than 10 digits after decimal point.", android.widget.Toast.LENGTH_SHORT).show()
                return
            }
            if (digitsInNumber >= 15) {
                android.widget.Toast.makeText(context, "Can't enter more than 15 digits.", android.widget.Toast.LENGTH_SHORT).show()
                return
            }
            if (expression == "0") {
                expression = digit
            } else {
                expression += digit
            }
        }
    }

    fun onDecimal() {
        haptic.performHapticFeedback(HapticFeedbackType.KeyboardTap)
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
        haptic.performHapticFeedback(HapticFeedbackType.KeyboardTap)
        if (isEvaluated) {
            isEvaluated = false
            historyEquation = ""
            expression = "$expression $op "
            return
        }
        val trimmed = expression.trimEnd()
        if (trimmed.endsWith("+") || trimmed.endsWith("−") || trimmed.endsWith("×") || trimmed.endsWith("÷")) {
            val withoutOp = trimmed.dropLast(1).trimEnd()
            expression = if (withoutOp.isNotEmpty()) "$withoutOp $op " else "$op "
        } else {
            expression = "$expression $op "
        }
    }

    fun onParenthesis() {
        haptic.performHapticFeedback(HapticFeedbackType.KeyboardTap)
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
        haptic.performHapticFeedback(HapticFeedbackType.KeyboardTap)
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
        haptic.performHapticFeedback(HapticFeedbackType.KeyboardTap)
        if (isEvaluated) {
            expression = if (expression.startsWith("−")) expression.removePrefix("−") else "−$expression"
            return
        }
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
        haptic.performHapticFeedback(HapticFeedbackType.KeyboardTap)
        expression = "0"
        historyEquation = ""
        isEvaluated = false
    }

    fun onBackspace() {
        haptic.performHapticFeedback(HapticFeedbackType.KeyboardTap)
        if (isEvaluated) {
            expression = "0"
            historyEquation = ""
            isEvaluated = false
            return
        }
        if (expression.length > 1) {
            var s = expression
            if (s.endsWith(" ")) s = s.trimEnd()
            s = s.dropLast(1).trimEnd()
            expression = if (s.isEmpty() || s == "−") "0" else s
        } else {
            expression = "0"
        }
    }

    fun onScientificFn(fn: String) {
        haptic.performHapticFeedback(HapticFeedbackType.KeyboardTap)
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
        val res = evalResult.getOrNull()
        if (res == null || res == "Invalid input") {
            // Like Samsung: the sum stays as typed and a short message says why
            val message = if (evalResult.exceptionOrNull() is ArithmeticException) "Can't divide by 0." else "Invalid format used."
            android.widget.Toast.makeText(context, message, android.widget.Toast.LENGTH_SHORT).show()
            return
        }
        history.add(0, expression to res)
        if (history.size > 100) history.removeAt(history.lastIndex)
        CalculatorHistoryStore.save(context)
        historyEquation = expression
        // Kept without separators (they're added when shown), so it carries on cleanly
        expression = res.replace(",", "")
        isEvaluated = true
        evalCount++
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

        if (showUnitConverterScreen) {
            // Full-screen Samsung Unit Converter (Screenshot 1)
            SamsungUnitConverterScreen(
                isDark = isDark,
                bgMain = bgMain,
                keyCircleBg = keyCircleBg,
                numberBtnText = numberBtnText,
                operatorTextColor = operatorTextColor,
                clearRedText = clearRedText,
                primaryText = primaryText,
                secondaryText = secondaryText,
                dividerColor = dividerColor,
                chipActiveBg = chipActiveBg,
                onClose = { showUnitConverterScreen = false }
            )
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .statusBarsPadding()
                    .navigationBarsPadding()
                    .padding(horizontal = horizontalPad)
                    .padding(bottom = if (isCompactScreen) 4.dp else 8.dp),
                verticalArrangement = Arrangement.Bottom
            ) {
                // Expanding top space: absorbs all upper space, pinning calculator display & keypad
                // directly to the bottom like the official Samsung Galaxy Calculator
                Spacer(modifier = Modifier.weight(1f))

                // Display Section (Samsung One UI layout) with horizontal scroll
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 6.dp, vertical = if (isCompactScreen) 4.dp else 8.dp),
                    horizontalAlignment = Alignment.End
                ) {
                    // Secondary line: past evaluated equation
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

                    // Primary line: current expression with green operators & numbers in primaryText (Screenshot 3 & 4)
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

                    // Numbers show with thousands separators as they're typed ("1,234"), like Samsung
                    val shownExpression = remember(expression) { groupDigitsForDisplay(expression) }
                    val annotatedExpression = remember(shownExpression, primaryText, operatorTextColor) {
                        buildAnnotatedString {
                            for (ch in shownExpression) {
                                if (ch in "+−×÷%()^√") {
                                    withStyle(SpanStyle(color = operatorTextColor, fontWeight = FontWeight.Normal)) {
                                        append(ch)
                                    }
                                } else {
                                    withStyle(SpanStyle(color = primaryText, fontWeight = FontWeight.Normal)) {
                                        append(ch)
                                    }
                                }
                            }
                        }
                    }
                    // The digits shrink smoothly as the sum grows, instead of jumping a size
                    val animatedFontSize by animateFloatAsState(
                        targetValue = primaryFontSize.value,
                        animationSpec = tween(180, easing = FastOutSlowInEasing),
                        label = "calc_font_size"
                    )

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(displayScrollState),
                        horizontalArrangement = Arrangement.End,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // On "=" the result rises into place and the sum moves up out of the way; typing
                        // within one sum just updates it (the key only changes with each "=")
                        androidx.compose.animation.AnimatedContent(
                            targetState = CalcLine(annotatedExpression, evalCount),
                            contentKey = { it.generation },
                            transitionSpec = {
                                (androidx.compose.animation.slideInVertically(tween(260, easing = FastOutSlowInEasing)) { it / 2 } +
                                    fadeIn(tween(220))) togetherWith
                                    (androidx.compose.animation.slideOutVertically(tween(220, easing = FastOutSlowInEasing)) { -it / 2 } +
                                        fadeOut(tween(160)))
                            },
                            label = "calc_result"
                        ) { line ->
                            Text(
                                text = line.text,
                                fontSize = animatedFontSize.sp,
                                fontWeight = FontWeight.Normal,
                                maxLines = 1,
                                textAlign = TextAlign.End,
                                fontFamily = FontFamily.SansSerif
                            )
                        }
                        // Samsung's green cursor, blinking while a sum is being typed
                        if (!isEvaluated) {
                            BlinkingCursor(color = operatorTextColor, height = (animatedFontSize * 1.05f).dp)
                        }
                    }

                    // Live result preview (clean number without '= ', e.g. "1260" - Screenshot 3)
                    AnimatedVisibility(
                        visible = liveResult != null && !isEvaluated,
                        enter = fadeIn(),
                        exit = fadeOut()
                    ) {
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = liveResult.orEmpty(),
                            fontSize = if (isCompactScreen) 20.sp else 24.sp,
                            fontWeight = FontWeight.Normal,
                            color = secondaryText,
                            textAlign = TextAlign.End,
                            fontFamily = FontFamily.SansSerif
                        )
                    }
                }

                // Samsung One UI Toolbar (Screenshot 3 & 4)
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
                        // History icon (toggles portrait inline history view)
                        IconButton(
                            onClick = {
                                showHistoryInline = !showHistoryInline
                                haptic.performHapticFeedback(HapticFeedbackType.KeyboardTap)
                            },
                            modifier = Modifier.size(38.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.History,
                                contentDescription = "History",
                                tint = if (showHistoryInline) operatorTextColor else iconMuted,
                                modifier = Modifier.size(22.dp)
                            )
                        }

                        // Unit Converter icon (opens Screenshot 1 converter)
                        IconButton(
                            onClick = {
                                showUnitConverterScreen = true
                                haptic.performHapticFeedback(HapticFeedbackType.KeyboardTap)
                            },
                            modifier = Modifier.size(38.dp)
                        ) {
                            RulerIcon(color = iconMuted)
                        }

                        // Scientific calculator toggle with rotation animation
                        IconButton(
                            onClick = {
                                isScientificActive = !isScientificActive
                                haptic.performHapticFeedback(HapticFeedbackType.KeyboardTap)
                            },
                            modifier = Modifier
                                .size(38.dp)
                                .rotate(sciIconRotation)
                        ) {
                            ScientificSquareIcon(
                                color = if (isScientificActive) operatorTextColor else iconMuted
                            )
                        }
                    }

                    // Green backspace button on right
                    val hasContent = expression != "0" || historyEquation.isNotEmpty()
                    // A tap deletes one; held, it keeps deleting, faster the longer it's held (as
                    // Samsung's does; "C" is what clears everything)
                    val backspaceAction by androidx.compose.runtime.rememberUpdatedState({ onBackspace() })
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .size(38.dp)
                            .clip(CircleShape)
                            .pointerInput(Unit) {
                                awaitEachGesture {
                                    awaitFirstDown()
                                    backspaceAction()
                                    var gap = 380L
                                    while (true) {
                                        val ended = withTimeoutOrNull(gap) {
                                            waitForUpOrCancellation()
                                            true
                                        } ?: false
                                        if (ended) break
                                        backspaceAction()
                                        gap = (gap * 0.75).toLong().coerceAtLeast(45L)
                                    }
                                }
                            }
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.Backspace,
                            contentDescription = "Backspace",
                            tint = if (hasContent) operatorTextColor else iconMuted.copy(alpha = 0.35f),
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }

                HorizontalDivider(
                    color = dividerColor,
                    thickness = 0.8.dp,
                    modifier = Modifier.padding(vertical = if (isCompactScreen) 6.dp else 10.dp)
                )

                // Scientific Keypad Panel (Animated Expansion). Stays as it is when history opens, so
                // opening history doesn't change the height below the display (no shift).
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
                                activeColor = operatorTextColor,
                                bgColor = scientificKeyBg,
                                textColor = scientificKeyText,
                                keyHeight = scientificKeyHeight,
                                modifier = Modifier.weight(1f),
                                onClick = {
                                    isDegMode = !isDegMode
                                    haptic.performHapticFeedback(HapticFeedbackType.KeyboardTap)
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
                                    haptic.performHapticFeedback(HapticFeedbackType.KeyboardTap)
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
                                    haptic.performHapticFeedback(HapticFeedbackType.KeyboardTap)
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
                                    haptic.performHapticFeedback(HapticFeedbackType.KeyboardTap)
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
                                    haptic.performHapticFeedback(HapticFeedbackType.KeyboardTap)
                                    expression += "!"
                                }
                            )
                            ScientificKey(text = "( )", bgColor = scientificKeyBg, textColor = scientificKeyText, keyHeight = scientificKeyHeight, modifier = Modifier.weight(1f), onClick = { onParenthesis() })
                        }
                    }
                }

                if (showHistoryInline) {
                    // Inline History View (Screenshot 4)
                    // The left 3 columns are replaced by the History list and 'Clear history' pill button,
                    // while the right column keeps the 5 operator keys (÷, ×, −, +, =). It takes exactly
                    // the keypad's height, so switching to it doesn't move the display.
                    val historyHeight = if (keypadHeightPx > 0) with(density) { keypadHeightPx.toDp() }
                        else if (isCompactScreen) 300.dp else 340.dp
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(historyHeight)
                            .padding(bottom = if (isCompactScreen) 8.dp else 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(keypadSpacing)
                    ) {
                        // Left 3 columns: history list and clear button
                        Column(
                            modifier = Modifier
                                .weight(3f)
                                .fillMaxHeight(),
                            verticalArrangement = Arrangement.SpaceBetween
                        ) {
                            if (history.isEmpty()) {
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .fillMaxWidth(),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = "No history",
                                        color = iconMuted,
                                        fontSize = 15.sp,
                                        fontFamily = FontFamily.SansSerif
                                    )
                                }
                            } else {
                                LazyColumn(
                                    modifier = Modifier
                                        .weight(1f)
                                        .fillMaxWidth()
                                        .padding(end = 8.dp),
                                    verticalArrangement = Arrangement.spacedBy(14.dp),
                                    reverseLayout = false
                                ) {
                                    items(history) { (eq, res) ->
                                        Column(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .clickable {
                                                    expression = res.replace(",", "")
                                                    historyEquation = eq
                                                    isEvaluated = true
                                                    showHistoryInline = false
                                                }
                                                .padding(vertical = 4.dp),
                                            horizontalAlignment = Alignment.End
                                        ) {
                                            Text(
                                                text = eq,
                                                fontSize = 16.sp,
                                                color = secondaryText,
                                                textAlign = TextAlign.End,
                                                fontFamily = FontFamily.SansSerif
                                            )
                                            Spacer(modifier = Modifier.height(2.dp))
                                            Text(
                                                text = "= $res",
                                                fontSize = 20.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = operatorTextColor,
                                                textAlign = TextAlign.End,
                                                fontFamily = FontFamily.SansSerif
                                            )
                                        }
                                    }
                                }
                            }

                            // Capsule 'Clear history' button at bottom-left (Screenshot 4)
                            Surface(
                                shape = RoundedCornerShape(20.dp),
                                color = keyCircleBg,
                                modifier = Modifier
                                    .padding(top = 8.dp)
                                    .clickable {
                                        history.clear()
                                        CalculatorHistoryStore.save(context)
                                        haptic.performHapticFeedback(HapticFeedbackType.KeyboardTap)
                                    }
                            ) {
                                Text(
                                    text = "Clear history",
                                    color = numberBtnText,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Medium,
                                    modifier = Modifier.padding(horizontal = 22.dp, vertical = 10.dp),
                                    fontFamily = FontFamily.SansSerif
                                )
                            }
                        }

                        // Thin vertical divider line
                        Box(
                            modifier = Modifier
                                .width(0.8.dp)
                                .fillMaxHeight()
                                .background(dividerColor)
                        )

                        // Right 1 column: operators (÷, ×, −, +, =)
                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight(),
                            verticalArrangement = Arrangement.SpaceBetween
                        ) {
                            SamsungRoundKey(
                                text = "÷",
                                bgColor = keyCircleBg,
                                textColor = operatorTextColor,
                                onClick = { onOperator("÷") }
                            )
                            SamsungRoundKey(
                                text = "×",
                                bgColor = keyCircleBg,
                                textColor = operatorTextColor,
                                onClick = { onOperator("×") }
                            )
                            SamsungRoundKey(
                                text = "−",
                                bgColor = keyCircleBg,
                                textColor = operatorTextColor,
                                onClick = { onOperator("−") }
                            )
                            SamsungRoundKey(
                                text = "+",
                                bgColor = keyCircleBg,
                                textColor = operatorTextColor,
                                onClick = { onOperator("+") }
                            )
                            val isEqualsHoldUnlockEnabled = securityPreferences.isEqualsHoldUnlockEnabled()
                            val equalsHoldSec = securityPreferences.getEqualsHoldDuration()
                            SamsungRoundKey(
                                text = "=",
                                bgColor = equalsCircleBg,
                                textColor = equalsText,
                                modifier = Modifier.onGloballyPositioned { coordinates ->
                                    equalsCenter = coordinates.boundsInRoot().center
                                },
                                customHoldDurationMs = if (isEqualsHoldUnlockEnabled) equalsHoldSec * 1000L else null,
                                onClick = { onEquals() },
                                onLongClick = if (isEqualsHoldUnlockEnabled) {
                                    { triggerSecretUnlock(equalsCenter) }
                                } else null
                            )
                        }
                    }
                } else {
                    // Standard Samsung One UI Keypad Grid: 4 columns x 5 rows (Screenshot 3)
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .onSizeChanged { keypadHeightPx = it.height }
                            .padding(bottom = if (isCompactScreen) 8.dp else 16.dp),
                        verticalArrangement = Arrangement.spacedBy(keypadSpacing)
                    ) {
                        // Row 1: C (coral text), ( ), %, ÷ (all keys light grey / dark background, green text)
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(keypadSpacing)
                        ) {
                            SamsungRoundKey(
                                text = "C",
                                bgColor = keyCircleBg,
                                textColor = clearRedText,
                                modifier = Modifier.weight(1f),
                                onClick = { onClear() }
                            )
                            SamsungRoundKey(
                                text = "( )",
                                bgColor = keyCircleBg,
                                textColor = operatorTextColor,
                                modifier = Modifier.weight(1f),
                                onClick = { onParenthesis() }
                            )
                            SamsungRoundKey(
                                text = "%",
                                bgColor = keyCircleBg,
                                textColor = operatorTextColor,
                                modifier = Modifier.weight(1f),
                                onClick = { onPercentage() }
                            )
                            SamsungRoundKey(
                                text = "÷",
                                bgColor = keyCircleBg,
                                textColor = operatorTextColor,
                                modifier = Modifier.weight(1f),
                                onClick = { onOperator("÷") }
                            )
                        }

                        // Row 2: 7, 8, 9, ×
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(keypadSpacing)
                        ) {
                            SamsungRoundKey(text = "7", bgColor = keyCircleBg, textColor = numberBtnText, modifier = Modifier.weight(1f), onClick = { onDigit("7") })
                            SamsungRoundKey(text = "8", bgColor = keyCircleBg, textColor = numberBtnText, modifier = Modifier.weight(1f), onClick = { onDigit("8") })
                            SamsungRoundKey(text = "9", bgColor = keyCircleBg, textColor = numberBtnText, modifier = Modifier.weight(1f), onClick = { onDigit("9") })
                            SamsungRoundKey(text = "×", bgColor = keyCircleBg, textColor = operatorTextColor, modifier = Modifier.weight(1f), onClick = { onOperator("×") })
                        }

                        // Row 3: 4, 5, 6, −
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(keypadSpacing)
                        ) {
                            SamsungRoundKey(text = "4", bgColor = keyCircleBg, textColor = numberBtnText, modifier = Modifier.weight(1f), onClick = { onDigit("4") })
                            SamsungRoundKey(text = "5", bgColor = keyCircleBg, textColor = numberBtnText, modifier = Modifier.weight(1f), onClick = { onDigit("5") })
                            SamsungRoundKey(text = "6", bgColor = keyCircleBg, textColor = numberBtnText, modifier = Modifier.weight(1f), onClick = { onDigit("6") })
                            SamsungRoundKey(text = "−", bgColor = keyCircleBg, textColor = operatorTextColor, modifier = Modifier.weight(1f), onClick = { onOperator("−") })
                        }

                        // Row 4: 1, 2, 3, +
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(keypadSpacing)
                        ) {
                            SamsungRoundKey(text = "1", bgColor = keyCircleBg, textColor = numberBtnText, modifier = Modifier.weight(1f), onClick = { onDigit("1") })
                            SamsungRoundKey(text = "2", bgColor = keyCircleBg, textColor = numberBtnText, modifier = Modifier.weight(1f), onClick = { onDigit("2") })
                            SamsungRoundKey(text = "3", bgColor = keyCircleBg, textColor = numberBtnText, modifier = Modifier.weight(1f), onClick = { onDigit("3") })
                            SamsungRoundKey(text = "+", bgColor = keyCircleBg, textColor = operatorTextColor, modifier = Modifier.weight(1f), onClick = { onOperator("+") })
                        }

                        // Row 5: +/-, 0, ., = (solid green circle for =)
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(keypadSpacing)
                        ) {
                            SamsungRoundKey(
                                text = "+/-",
                                bgColor = keyCircleBg,
                                textColor = numberBtnText,
                                modifier = Modifier.weight(1f),
                                onClick = { onPlusMinus() }
                            )
                            SamsungRoundKey(text = "0", bgColor = keyCircleBg, textColor = numberBtnText, modifier = Modifier.weight(1f), onClick = { onDigit("0") })
                            SamsungRoundKey(
                                text = ".",
                                bgColor = keyCircleBg,
                                textColor = numberBtnText,
                                modifier = Modifier.weight(1f),
                                onClick = { onDecimal() }
                            )
                            val isEqualsHoldUnlockEnabled = securityPreferences.isEqualsHoldUnlockEnabled()
                            val equalsHoldSec = securityPreferences.getEqualsHoldDuration()
                            SamsungRoundKey(
                                text = "=",
                                bgColor = equalsCircleBg,
                                textColor = equalsText,
                                modifier = Modifier
                                    .weight(1f)
                                    .onGloballyPositioned { coordinates ->
                                        equalsCenter = coordinates.boundsInRoot().center
                                    },
                                customHoldDurationMs = if (isEqualsHoldUnlockEnabled) equalsHoldSec * 1000L else null,
                                onClick = { onEquals() },
                                onLongClick = if (isEqualsHoldUnlockEnabled) {
                                    { triggerSecretUnlock(equalsCenter) }
                                } else null
                            )
                        }
                    }
                }
            }
        }

        // Satisfying secret opening transition overlay (matches Notes app unlock loader)
        if (isUnlockingSecret) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = unlockAnimProgress.value * 0.35f)),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Surface(
                        shape = CircleShape,
                        color = Color.White,
                        shadowElevation = 14.dp,
                        modifier = Modifier
                            .size(72.dp)
                            .scale(0.82f + (unlockAnimProgress.value * 0.28f))
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(
                                    Brush.linearGradient(
                                        listOf(
                                            Color(0xFFFF5376),
                                            Color(0xFFFF758C),
                                            Color(0xFFFF9E7D)
                                        )
                                    )
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.LockOpen,
                                contentDescription = "Cherish Unlocked",
                                tint = Color.White,
                                modifier = Modifier.size(34.dp)
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                    Surface(
                        shape = RoundedCornerShape(20.dp),
                        color = if (isDark) Color(0xFF1E1E22).copy(alpha = 0.95f) else Color.White.copy(alpha = 0.95f),
                        shadowElevation = 6.dp,
                        modifier = Modifier.graphicsLayer {
                            alpha = unlockAnimProgress.value
                        }
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 20.dp, vertical = 9.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                color = Color(0xFFFF5376),
                                strokeWidth = 2.2.dp
                            )
                            Text(
                                text = "Opening Cherish...",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = if (isDark) Color.White else Color(0xFF1E293B)
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * Full-screen Samsung Unit Converter (Screenshot 1).
 * Features:
 * - Top bar: `<  Unit converter`
 * - Category pill row: Area, Length, Temperature, Volume, Mass, Data, Speed, Time
 * - Two conversion rows with dropdown selectors and huge number font + small unit abbreviation
 * - Keypad 4x4: 7 8 9 ⌫ / 4 5 6 C / 1 2 3 ↑ / +/- 0 . ↓
 */
@Composable
private fun SamsungUnitConverterScreen(
    isDark: Boolean,
    bgMain: Color,
    keyCircleBg: Color,
    numberBtnText: Color,
    operatorTextColor: Color,
    clearRedText: Color,
    primaryText: Color,
    secondaryText: Color,
    dividerColor: Color,
    chipActiveBg: Color,
    onClose: () -> Unit
) {
    val haptic = LocalHapticFeedback.current
    var selectedCategoryIndex by remember { mutableIntStateOf(0) }
    val categories = listOf("Area", "Length", "Temperature", "Volume", "Mass", "Data", "Speed", "Time")

    val unitSpecs = remember(selectedCategoryIndex) {
        getCategoryUnitSpecs(selectedCategoryIndex)
    }

    var topUnitIndex by remember(selectedCategoryIndex) { mutableIntStateOf(0) }
    var bottomUnitIndex by remember(selectedCategoryIndex) { mutableIntStateOf(if (unitSpecs.size > 1) 1 else 0) }

    var activeRow by remember { mutableIntStateOf(0) } // 0 = top, 1 = bottom
    var topRawInput by remember { mutableStateOf("10") }
    var bottomRawInput by remember { mutableStateOf("0.1") }

    var showTopDropdown by remember { mutableStateOf(false) }
    var showBottomDropdown by remember { mutableStateOf(false) }

    BackHandler(enabled = true) {
        if (showTopDropdown) {
            showTopDropdown = false
        } else if (showBottomDropdown) {
            showBottomDropdown = false
        } else {
            onClose()
        }
    }

    fun recomputeConversion(sourceRow: Int) {
        val topSpec = unitSpecs.getOrNull(topUnitIndex) ?: return
        val bottomSpec = unitSpecs.getOrNull(bottomUnitIndex) ?: return

        if (sourceRow == 0) {
            val v = topRawInput.toDoubleOrNull() ?: 0.0
            val converted = convertUnits(selectedCategoryIndex, v, topSpec, bottomSpec)
            bottomRawInput = formatUnitResult(converted)
        } else {
            val v = bottomRawInput.toDoubleOrNull() ?: 0.0
            val converted = convertUnits(selectedCategoryIndex, v, bottomSpec, topSpec)
            topRawInput = formatUnitResult(converted)
        }
    }

    LaunchedEffect(selectedCategoryIndex, topUnitIndex, bottomUnitIndex) {
        recomputeConversion(activeRow)
    }

    fun onConverterDigit(d: String) {
        haptic.performHapticFeedback(HapticFeedbackType.KeyboardTap)
        if (activeRow == 0) {
            topRawInput = if (topRawInput == "0") d else topRawInput + d
            recomputeConversion(0)
        } else {
            bottomRawInput = if (bottomRawInput == "0") d else bottomRawInput + d
            recomputeConversion(1)
        }
    }

    fun onConverterDecimal() {
        haptic.performHapticFeedback(HapticFeedbackType.KeyboardTap)
        if (activeRow == 0) {
            if (!topRawInput.contains(".")) {
                topRawInput = if (topRawInput.isEmpty()) "0." else "$topRawInput."
                recomputeConversion(0)
            }
        } else {
            if (!bottomRawInput.contains(".")) {
                bottomRawInput = if (bottomRawInput.isEmpty()) "0." else "$bottomRawInput."
                recomputeConversion(1)
            }
        }
    }

    fun onConverterClear() {
        haptic.performHapticFeedback(HapticFeedbackType.KeyboardTap)
        if (activeRow == 0) {
            topRawInput = "0"
            recomputeConversion(0)
        } else {
            bottomRawInput = "0"
            recomputeConversion(1)
        }
    }

    fun onConverterBackspace() {
        haptic.performHapticFeedback(HapticFeedbackType.KeyboardTap)
        if (activeRow == 0) {
            topRawInput = if (topRawInput.length > 1) topRawInput.dropLast(1) else "0"
            recomputeConversion(0)
        } else {
            bottomRawInput = if (bottomRawInput.length > 1) bottomRawInput.dropLast(1) else "0"
            recomputeConversion(1)
        }
    }

    fun onConverterPlusMinus() {
        haptic.performHapticFeedback(HapticFeedbackType.KeyboardTap)
        if (activeRow == 0) {
            topRawInput = if (topRawInput.startsWith("-")) topRawInput.removePrefix("-") else if (topRawInput == "0") "0" else "-$topRawInput"
            recomputeConversion(0)
        } else {
            bottomRawInput = if (bottomRawInput.startsWith("-")) bottomRawInput.removePrefix("-") else if (bottomRawInput == "0") "0" else "-$bottomRawInput"
            recomputeConversion(1)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(bgMain)
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(horizontal = 20.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            // Header: <  Unit converter (Screenshot 1)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = onClose,
                    modifier = Modifier.size(38.dp)
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                        contentDescription = "Back",
                        tint = primaryText,
                        modifier = Modifier.size(24.dp)
                    )
                }
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "Unit converter",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = primaryText,
                    fontFamily = FontFamily.SansSerif
                )
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Categories horizontal pill scroll
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                categories.forEachIndexed { idx, title ->
                    val isSelected = selectedCategoryIndex == idx
                    Surface(
                        shape = RoundedCornerShape(18.dp),
                        color = if (isSelected) chipActiveBg else Color.Transparent,
                        modifier = Modifier.clickable {
                            selectedCategoryIndex = idx
                            topUnitIndex = 0
                            bottomUnitIndex = 1
                            topRawInput = "10"
                            haptic.performHapticFeedback(HapticFeedbackType.KeyboardTap)
                        }
                    ) {
                        Text(
                            text = title,
                            color = if (isSelected) primaryText else secondaryText,
                            fontSize = 14.sp,
                            fontWeight = if (isSelected) FontWeight.Medium else FontWeight.Normal,
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                            fontFamily = FontFamily.SansSerif
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Unit 1 Row: Dropdown selector on left + large number on right (Screenshot 1)
            val topSpec = unitSpecs.getOrNull(topUnitIndex) ?: UnitSpec("Are", "a", 100.0)
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { activeRow = 0 }
                    .padding(vertical = 8.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .clickable { showTopDropdown = true }
                                .padding(vertical = 4.dp)
                        ) {
                            Text(
                                text = topSpec.name,
                                fontSize = 16.sp,
                                color = primaryText,
                                fontFamily = FontFamily.SansSerif
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Icon(
                                imageVector = Icons.Rounded.ArrowDropDown,
                                contentDescription = "Select unit",
                                tint = primaryText,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        DropdownMenu(
                            expanded = showTopDropdown,
                            onDismissRequest = { showTopDropdown = false }
                        ) {
                            unitSpecs.forEachIndexed { index, spec ->
                                DropdownMenuItem(
                                    text = { Text("${spec.name} (${spec.symbol})") },
                                    onClick = {
                                        topUnitIndex = index
                                        showTopDropdown = false
                                        recomputeConversion(activeRow)
                                    }
                                )
                            }
                        }
                    }

                    Row(
                        verticalAlignment = Alignment.Bottom,
                        horizontalArrangement = Arrangement.End
                    ) {
                        Text(
                            text = topRawInput,
                            fontSize = 32.sp,
                            fontWeight = FontWeight.Normal,
                            color = if (activeRow == 0) primaryText else secondaryText,
                            fontFamily = FontFamily.SansSerif
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = topSpec.symbol,
                            fontSize = 18.sp,
                            color = secondaryText,
                            modifier = Modifier.padding(bottom = 4.dp),
                            fontFamily = FontFamily.SansSerif
                        )
                    }
                }
                HorizontalDivider(color = dividerColor, thickness = 0.8.dp, modifier = Modifier.padding(top = 10.dp))
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Unit 2 Row: Dropdown selector on left + large number on right (Screenshot 1)
            val bottomSpec = unitSpecs.getOrNull(bottomUnitIndex) ?: UnitSpec("Hectare", "ha", 10000.0)
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { activeRow = 1 }
                    .padding(vertical = 8.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .clickable { showBottomDropdown = true }
                                .padding(vertical = 4.dp)
                        ) {
                            Text(
                                text = bottomSpec.name,
                                fontSize = 16.sp,
                                color = primaryText,
                                fontFamily = FontFamily.SansSerif
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Icon(
                                imageVector = Icons.Rounded.ArrowDropDown,
                                contentDescription = "Select unit",
                                tint = primaryText,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        DropdownMenu(
                            expanded = showBottomDropdown,
                            onDismissRequest = { showBottomDropdown = false }
                        ) {
                            unitSpecs.forEachIndexed { index, spec ->
                                DropdownMenuItem(
                                    text = { Text("${spec.name} (${spec.symbol})") },
                                    onClick = {
                                        bottomUnitIndex = index
                                        showBottomDropdown = false
                                        recomputeConversion(activeRow)
                                    }
                                )
                            }
                        }
                    }

                    Row(
                        verticalAlignment = Alignment.Bottom,
                        horizontalArrangement = Arrangement.End
                    ) {
                        Text(
                            text = bottomRawInput,
                            fontSize = 32.sp,
                            fontWeight = FontWeight.Normal,
                            color = if (activeRow == 1) primaryText else secondaryText,
                            fontFamily = FontFamily.SansSerif
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = bottomSpec.symbol,
                            fontSize = 18.sp,
                            color = secondaryText,
                            modifier = Modifier.padding(bottom = 4.dp),
                            fontFamily = FontFamily.SansSerif
                        )
                    }
                }
                HorizontalDivider(color = dividerColor, thickness = 0.8.dp, modifier = Modifier.padding(top = 10.dp))
            }
        }

        // Dedicated 4x4 Keypad at bottom (Screenshot 1):
        // Row 1: 7, 8, 9, ⌫
        // Row 2: 4, 5, 6, C
        // Row 3: 1, 2, 3, ↑
        // Row 4: +/-, 0, ., ↓
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Keypad Row 1
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                SamsungRoundKey(text = "7", bgColor = keyCircleBg, textColor = numberBtnText, modifier = Modifier.weight(1f), onClick = { onConverterDigit("7") })
                SamsungRoundKey(text = "8", bgColor = keyCircleBg, textColor = numberBtnText, modifier = Modifier.weight(1f), onClick = { onConverterDigit("8") })
                SamsungRoundKey(text = "9", bgColor = keyCircleBg, textColor = numberBtnText, modifier = Modifier.weight(1f), onClick = { onConverterDigit("9") })
                SamsungRoundKey(
                    icon = {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.Backspace,
                            contentDescription = "Backspace",
                            tint = operatorTextColor,
                            modifier = Modifier.size(24.dp)
                        )
                    },
                    bgColor = keyCircleBg,
                    textColor = operatorTextColor,
                    modifier = Modifier.weight(1f),
                    onClick = { onConverterBackspace() }
                )
            }

            // Keypad Row 2
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                SamsungRoundKey(text = "4", bgColor = keyCircleBg, textColor = numberBtnText, modifier = Modifier.weight(1f), onClick = { onConverterDigit("4") })
                SamsungRoundKey(text = "5", bgColor = keyCircleBg, textColor = numberBtnText, modifier = Modifier.weight(1f), onClick = { onConverterDigit("5") })
                SamsungRoundKey(text = "6", bgColor = keyCircleBg, textColor = numberBtnText, modifier = Modifier.weight(1f), onClick = { onConverterDigit("6") })
                SamsungRoundKey(text = "C", bgColor = keyCircleBg, textColor = clearRedText, modifier = Modifier.weight(1f), onClick = { onConverterClear() })
            }

            // Keypad Row 3
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                SamsungRoundKey(text = "1", bgColor = keyCircleBg, textColor = numberBtnText, modifier = Modifier.weight(1f), onClick = { onConverterDigit("1") })
                SamsungRoundKey(text = "2", bgColor = keyCircleBg, textColor = numberBtnText, modifier = Modifier.weight(1f), onClick = { onConverterDigit("2") })
                SamsungRoundKey(text = "3", bgColor = keyCircleBg, textColor = numberBtnText, modifier = Modifier.weight(1f), onClick = { onConverterDigit("3") })
                SamsungRoundKey(
                    icon = {
                        Icon(
                            imageVector = Icons.Rounded.ArrowUpward,
                            contentDescription = "Up",
                            tint = operatorTextColor,
                            modifier = Modifier.size(26.dp)
                        )
                    },
                    bgColor = keyCircleBg,
                    textColor = operatorTextColor,
                    modifier = Modifier.weight(1f),
                    onClick = {
                        activeRow = 0
                        haptic.performHapticFeedback(HapticFeedbackType.KeyboardTap)
                    }
                )
            }

            // Keypad Row 4
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                SamsungRoundKey(text = "+/-", bgColor = keyCircleBg, textColor = numberBtnText, modifier = Modifier.weight(1f), onClick = { onConverterPlusMinus() })
                SamsungRoundKey(text = "0", bgColor = keyCircleBg, textColor = numberBtnText, modifier = Modifier.weight(1f), onClick = { onConverterDigit("0") })
                SamsungRoundKey(text = ".", bgColor = keyCircleBg, textColor = numberBtnText, modifier = Modifier.weight(1f), onClick = { onConverterDecimal() })
                SamsungRoundKey(
                    icon = {
                        Icon(
                            imageVector = Icons.Rounded.ArrowDownward,
                            contentDescription = "Down",
                            tint = operatorTextColor,
                            modifier = Modifier.size(26.dp)
                        )
                    },
                    bgColor = keyCircleBg,
                    textColor = operatorTextColor,
                    modifier = Modifier.weight(1f),
                    onClick = {
                        activeRow = 1
                        haptic.performHapticFeedback(HapticFeedbackType.KeyboardTap)
                    }
                )
            }
        }
    }
}

/**
 * Authentic circular key for Samsung One UI Calculator with spring press physics and responsive font scaling.
 */
@Composable
private fun SamsungRoundKey(
    text: String? = null,
    icon: (@Composable () -> Unit)? = null,
    bgColor: Color,
    textColor: Color,
    modifier: Modifier = Modifier,
    customHoldDurationMs: Long? = null,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)? = null
) {
    var isPressed by remember { mutableStateOf(false) }
    // One UI key feel: it presses in at once without a wobble, then settles back with a light spring
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.92f else 1.0f,
        animationSpec = if (isPressed) {
            spring(dampingRatio = 1f, stiffness = Spring.StiffnessHigh)
        } else {
            spring(dampingRatio = 0.62f, stiffness = Spring.StiffnessMedium)
        },
        label = "round_btn_scale"
    )
    // The key darkens as it's pressed and fades back after, rather than switching shade at once
    val pressShade by animateFloatAsState(
        targetValue = if (isPressed) 1f else 0f,
        animationSpec = tween(durationMillis = if (isPressed) 60 else 220, easing = FastOutSlowInEasing),
        label = "round_btn_shade"
    )

    val currentOnClick by androidx.compose.runtime.rememberUpdatedState(onClick)
    val currentOnLongClick by androidx.compose.runtime.rememberUpdatedState(onLongClick)

    BoxWithConstraints(
        modifier = modifier
            .scale(scale)
            .aspectRatio(1f)
            .clip(CircleShape)
            .background(bgColor.copy(alpha = bgColor.alpha * (1f - 0.22f * pressShade)))
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
        if (icon != null) {
            icon()
        } else if (text != null) {
            val fontSize = when {
                text.length > 2 -> (btnW.value * 0.30f).coerceIn(13f, 18f).sp // for "+/-"
                text == "( )" -> (btnW.value * 0.32f).coerceIn(16f, 22f).sp // for "( )"
                text == "=" || text == "C" || text == "%" -> (btnW.value * 0.42f).coerceIn(20f, 28f).sp
                text in listOf("+", "−", "×", "÷") -> (btnW.value * 0.44f).coerceIn(24f, 32f).sp
                else -> (btnW.value * 0.42f).coerceIn(23f, 30f).sp
            }

            Text(
                text = text,
                fontSize = fontSize,
                fontWeight = if (text in listOf("=", "+", "−", "×", "÷")) FontWeight.Medium else FontWeight.Normal,
                color = textColor,
                fontFamily = FontFamily.SansSerif,
                maxLines = 1,
                softWrap = false
            )
        }
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
    activeColor: Color = Color(0xFF278829),
    textColor: Color = Color(0xFF1E1E1E),
    onClick: () -> Unit
) {
    var isPressed by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.94f else 1.0f,
        animationSpec = if (isPressed) {
            spring(dampingRatio = 1f, stiffness = Spring.StiffnessHigh)
        } else {
            spring(dampingRatio = 0.65f, stiffness = Spring.StiffnessMedium)
        },
        label = "sci_key_scale"
    )
    val pressShade by animateFloatAsState(
        targetValue = if (isPressed) 1f else 0f,
        animationSpec = tween(durationMillis = if (isPressed) 60 else 220, easing = FastOutSlowInEasing),
        label = "sci_key_shade"
    )
    val currentOnClick by androidx.compose.runtime.rememberUpdatedState(onClick)

    Box(
        modifier = modifier
            .scale(scale)
            .height(keyHeight)
            .clip(RoundedCornerShape(10.dp))
            .background(
                if (isActive) activeColor.copy(alpha = 0.25f)
                else bgColor.copy(alpha = bgColor.alpha * (1f - 0.2f * pressShade))
            )
            .pointerInput(Unit) {
                detectTapGestures(
                    onPress = {
                        isPressed = true
                        tryAwaitRelease()
                        isPressed = false
                    },
                    onTap = { currentOnClick() }
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

private data class UnitSpec(val name: String, val symbol: String, val factorToBase: Double)

private fun getCategoryUnitSpecs(catIndex: Int): List<UnitSpec> {
    return when (catIndex) {
        0 -> listOf( // Area (base: m²)
            UnitSpec("Are", "a", 100.0),
            UnitSpec("Hectare", "ha", 10000.0),
            UnitSpec("Square meter", "m²", 1.0),
            UnitSpec("Square kilometer", "km²", 1_000_000.0),
            UnitSpec("Acre", "ac", 4046.8564224),
            UnitSpec("Square foot", "ft²", 0.09290304),
            UnitSpec("Square inch", "in²", 0.00064516),
            UnitSpec("Square yard", "yd²", 0.83612736)
        )
        1 -> listOf( // Length (base: m)
            UnitSpec("Meter", "m", 1.0),
            UnitSpec("Centimeter", "cm", 0.01),
            UnitSpec("Millimeter", "mm", 0.001),
            UnitSpec("Kilometer", "km", 1000.0),
            UnitSpec("Inch", "in", 0.0254),
            UnitSpec("Foot", "ft", 0.3048),
            UnitSpec("Yard", "yd", 0.9144),
            UnitSpec("Mile", "mi", 1609.344)
        )
        2 -> listOf( // Temperature
            UnitSpec("Celsius", "°C", 1.0),
            UnitSpec("Fahrenheit", "°F", 1.0),
            UnitSpec("Kelvin", "K", 1.0)
        )
        3 -> listOf( // Volume (base: L)
            UnitSpec("Liter", "L", 1.0),
            UnitSpec("Milliliter", "mL", 0.001),
            UnitSpec("Gallon (US)", "gal", 3.78541),
            UnitSpec("Quart", "qt", 0.946353),
            UnitSpec("Pint", "pt", 0.473176),
            UnitSpec("Fluid ounce", "fl oz", 0.0295735),
            UnitSpec("Cubic meter", "m³", 1000.0)
        )
        4 -> listOf( // Mass (base: kg)
            UnitSpec("Kilogram", "kg", 1.0),
            UnitSpec("Gram", "g", 0.001),
            UnitSpec("Milligram", "mg", 0.000001),
            UnitSpec("Pound", "lb", 0.45359237),
            UnitSpec("Ounce", "oz", 0.0283495),
            UnitSpec("Tonne", "t", 1000.0)
        )
        5 -> listOf( // Data (base: MB)
            UnitSpec("Megabyte", "MB", 1.0),
            UnitSpec("Kilobyte", "KB", 1.0 / 1024.0),
            UnitSpec("Gigabyte", "GB", 1024.0),
            UnitSpec("Terabyte", "TB", 1024.0 * 1024.0),
            UnitSpec("Byte", "B", 1.0 / (1024.0 * 1024.0))
        )
        6 -> listOf( // Speed (base: km/h)
            UnitSpec("Kilometer/hour", "km/h", 1.0),
            UnitSpec("Meter/second", "m/s", 3.6),
            UnitSpec("Mile/hour", "mph", 1.60934),
            UnitSpec("Knot", "kn", 1.852)
        )
        7 -> listOf( // Time (base: s)
            UnitSpec("Second", "s", 1.0),
            UnitSpec("Minute", "min", 60.0),
            UnitSpec("Hour", "h", 3600.0),
            UnitSpec("Day", "d", 86400.0),
            UnitSpec("Week", "wk", 604800.0)
        )
        else -> emptyList()
    }
}

private fun convertUnits(catIndex: Int, value: Double, from: UnitSpec, to: UnitSpec): Double {
    if (from.symbol == to.symbol) return value
    if (catIndex == 2) { // Temperature
        val c = when (from.symbol) {
            "°C" -> value
            "°F" -> (value - 32.0) * 5.0 / 9.0
            "K" -> value - 273.15
            else -> value
        }
        return when (to.symbol) {
            "°C" -> c
            "°F" -> (c * 9.0 / 5.0) + 32.0
            "K" -> c + 273.15
            else -> c
        }
    }
    val inBase = value * from.factorToBase
    return inBase / to.factorToBase
}

private fun formatUnitResult(v: Double): String {
    if (v.isNaN() || v.isInfinite()) return "0"
    val absV = abs(v)
    if (absV == 0.0) return "0"
    val df = if (absV >= 100000 || (absV < 0.0001 && absV > 0.0)) {
        DecimalFormat("0.####E0", DecimalFormatSymbols(Locale.US))
    } else {
        DecimalFormat("#,##0.######", DecimalFormatSymbols(Locale.US))
    }
    return df.format(v)
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
            // A result carried on is shown with thousands separators ("2,000"); strip them, and all
            // spaces, so continuing from it ("2,000 + 1") parses
            .replace(",", "")
            .replace(" ", "")

        // Expand scientific notation from an earlier result ("1.5e12", "2e-3") to plain digits, so
        // its 'e' is never mistaken for Euler's number
        clean = Regex("""(\d+(?:\.\d+)?)[eE]([+-]?\d+)""").replace(clean) { match ->
            java.math.BigDecimal(match.groupValues[1]).scaleByPowerOfTen(match.groupValues[2].toInt()).toPlainString()
        }

        var trimmed = clean
        while (trimmed.endsWith("+") || trimmed.endsWith("-") || trimmed.endsWith("*") || trimmed.endsWith("/") || trimmed.endsWith("^")) {
            trimmed = trimmed.dropLast(1)
        }
        if (trimmed.isBlank()) return@runCatching "0"

        val percentAddSub = Regex("""(\d+(?:\.\d+)?)([\+\-])(\d+(?:\.\d+)?)%""")
        while (percentAddSub.containsMatchIn(trimmed)) {
            trimmed = percentAddSub.replace(trimmed) { match ->
                val base = match.groupValues[1]
                val op = match.groupValues[2]
                val pct = match.groupValues[3]
                "$base$op($base*$pct/100)"
            }
        }
        trimmed = trimmed.replace(Regex("""(\d+(?:\.\d+)?)%"""), "($1/100)")

        val parser = SamsungExpressionParser(trimmed, isDeg)
        val value = parser.parse()

        formatSamsungResult(value)
    }
}

private fun formatSamsungResult(value: Double): String {
    if (value.isNaN()) return "Invalid input"
    if (value.isInfinite()) return if (value > 0) "Infinity" else "-Infinity"

    // Clear floating-point dust: sin(180°) reads 0 (not 1.2e-16), 0.1 + 0.2 reads 0.3
    var v = value
    v = if (abs(v) < 1e-13) 0.0 else java.math.BigDecimal(v).round(java.math.MathContext(12)).toDouble()
    if (v == 0.0) return "0"

    val absVal = abs(v)
    if (absVal >= 1e12 || absVal < 1e-9) {
        val df = DecimalFormat("0.######E0", DecimalFormatSymbols(Locale.US))
        return df.format(v).replace("E", "e")
    }

    val df = DecimalFormat("#,##0.##########", DecimalFormatSymbols(Locale.US))
    var res = df.format(v)
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
        } else if (ch == 'π'.code) {
            nextChar()
            x = Math.PI
        } else if (ch == '√'.code) {
            nextChar()
            val arg = parseFactor()
            x = if (arg < 0) throw IllegalArgumentException("Invalid input") else sqrt(arg)
        } else if (ch in 'a'.code..'z'.code) {
            while (ch in 'a'.code..'z'.code) nextChar()
            val fn = expr.substring(startPos, this.pos)
            // Euler's number and pi are values, not functions: they take no argument
            if (fn == "e") {
                x = Math.E
            } else if (fn == "pi") {
                x = Math.PI
            } else {
                val arg = parseFactor()
                x = when (fn) {
                    "sqrt" -> if (arg < 0) throw IllegalArgumentException("Invalid input") else sqrt(arg)
                    "sin" -> sin(if (isDeg) Math.toRadians(arg) else arg)
                    "cos" -> cos(if (isDeg) Math.toRadians(arg) else arg)
                    "tan" -> tan(if (isDeg) Math.toRadians(arg) else arg)
                    "ln" -> if (arg <= 0) throw IllegalArgumentException("Invalid input") else ln(arg)
                    "log" -> if (arg <= 0) throw IllegalArgumentException("Invalid input") else log10(arg)
                    "abs" -> abs(arg)
                    "exp" -> exp(arg)
                    else -> throw IllegalArgumentException("Unknown function: $fn")
                }
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

        // Implicit multiplication when a value sits straight against the next one, with no operator:
        // 2π, 2(3), (1)(2), 2√9, 2sin(30). A letter here begins another value or function.
        while (ch == '('.code || ch == 'π'.code || ch == '√'.code || ch in 'a'.code..'z'.code) {
            x *= parseFactor()
        }

        return x
    }
}
