/*
 * ConnectBot Terminal
 * Copyright 2025 Kenny Root
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.connectbot.terminal

import android.graphics.Paint
import android.graphics.Path
import android.graphics.Typeface
import android.text.TextPaint
import android.util.Log
import androidx.annotation.VisibleForTesting
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.splineBasedDecay
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.drag
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.pointer.PointerEvent
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.LocalViewConfiguration
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Gesture type for unified gesture handling state machine.
 */
private enum class GestureType {
    Undetermined,
    Scroll,
    Selection,
    Zoom,
    HandleDrag
}

/**
 * The rate at which the cursor blinks in milliseconds when enabled.
 */
private const val CURSOR_BLINK_RATE_MS = 500L

/**
 * Amount of time to wait for second touch to detect multitouch gesture in milliseconds.
 */
private const val WAIT_FOR_SECOND_TOUCH_MS = 40L

/**
 * Text selection magnifier loupe size in dp.
 */
private const val MAGNIFIER_SIZE_DP = 100

/**
 * How much to scale up the text in the magnifier loupe.
 */
private const val MAGNIFIER_SCALE = 2.5f

/**
 * Delay in milliseconds before showing the IME (Input Method Editor).
 */
private const val IME_SHOW_DELAY_MS = 100L

/**
 * Delay in milliseconds to allow UI to settle before requesting focus.
 */
private const val UI_SETTLE_DELAY_MS = 100L

/**
 * Delay in milliseconds before showing the soft keyboard.
 */
private const val KEYBOARD_SHOW_DELAY_MS = 50L

/**
 * Border width for the terminal display in dp.
 */
private val TERMINAL_BORDER_WIDTH = 2.dp

/**
 * Minimum zoom scale for pinch-to-zoom gesture.
 */
private const val MIN_ZOOM_SCALE = 0.5f

/**
 * Maximum zoom scale for pinch-to-zoom gesture.
 */
private const val MAX_ZOOM_SCALE = 3f

/**
 * Height of the horizontal scroll indicator in dp.
 */
private val HORIZONTAL_SCROLL_INDICATOR_HEIGHT = 4.dp

/**
 * Horizontal padding for the scroll indicator track.
 */
private val HORIZONTAL_SCROLL_INDICATOR_PADDING = 16.dp

/**
 * Alpha for the scroll indicator track background.
 */
private const val SCROLL_INDICATOR_TRACK_ALPHA = 0.3f

/**
 * Alpha for the scroll indicator thumb.
 */
private const val SCROLL_INDICATOR_THUMB_ALPHA = 0.6f

/**
 * Minimum thumb width as a fraction of track width.
 */
private const val SCROLL_INDICATOR_MIN_THUMB_FRACTION = 0.1f

/**
 * Size of the copy button when selection is active in dp.
 */
private val COPY_BUTTON_SIZE = 48.dp

/**
 * Vertical offset for the copy button above the selection in dp.
 */
private val COPY_BUTTON_OFFSET = 48.dp

/**
 * Background color for selected text (blue highlight).
 */
private val SELECTION_HIGHLIGHT_COLOR = Color(0xFF4A90E2)

/**
 * Touch radius in pixels for detecting selection handle touches.
 */
private const val HANDLE_HIT_RADIUS = 50f

/**
 * Vertical offset in dp to position the magnifier above the finger.
 */
private val MAGNIFIER_VERTICAL_OFFSET = 40.dp

/**
 * Center offset multiplier for magnifier positioning.
 */
private const val MAGNIFIER_CENTER_OFFSET_MULTIPLIER = 1.2f

/**
 * Border width for the magnifier loupe in dp.
 */
private val MAGNIFIER_BORDER_WIDTH = 2.dp

/**
 * Background alpha for the magnifier loupe (0.0 = transparent, 1.0 = opaque).
 */
private const val MAGNIFIER_BACKGROUND_ALPHA = 0.9f

/**
 * Number of rows to display on each side of the touch point in the magnifier.
 */
private const val MAGNIFIER_ROW_RANGE = 3

/**
 * Width of selection handles (teardrop shape) in dp.
 */
private val SELECTION_HANDLE_WIDTH = 24.dp

/**
 * Alpha value for the block cursor.
 */
private const val CURSOR_BLOCK_ALPHA = 0.7f

/**
 * Alpha value for the underline and bar cursors.
 */
private const val CURSOR_LINE_ALPHA = 0.9f

/**
 * Percentage of cell height for underline cursor (0.0 to 1.0).
 */
private const val CURSOR_UNDERLINE_HEIGHT_RATIO = 0.15f

/**
 * Percentage of cell width for bar cursor (0.0 to 1.0).
 */
private const val CURSOR_BAR_WIDTH_RATIO = 0.15f

/**
 * Convergence threshold for binary search when finding optimal font size.
 */
private const val FONT_SIZE_SEARCH_EPSILON = 0.1f

/**
 * Number of wavelengths per character width for the curly underline pattern.
 */
private const val CURLY_UNDERLINE_CYCLES_PER_CHAR = 2f

/**
 * Amplitude (height) of the curly underline pattern in pixels.
 */
private const val CURLY_UNDERLINE_AMPLITUDE = 1.5f

/**
 * Spacing between the two lines in a double underline in pixels.
 */
private const val DOUBLE_UNDERLINE_SPACING = 2f

/**
 * Terminal - A Jetpack Compose terminal screen component.
 *
 * This component:
 * - Renders terminal output using Canvas
 * - Handles terminal resize based on available space
 * - Displays cursor
 * - Supports colors, bold, italic, underline, etc.
 *
 * @param terminalEmulator The terminal emulator containing terminal state
 * @param modifier Modifier for the composable
 * @param typeface Typeface for terminal text (default: Typeface.MONOSPACE)
 * @param initialFontSize Initial font size for terminal text (can be changed with pinch-to-zoom)
 * @param minFontSize Minimum font size for pinch-to-zoom
 * @param maxFontSize Maximum font size for pinch-to-zoom
 * @param backgroundColor Default background color
 * @param foregroundColor Default foreground color
 * @param keyboardEnabled Enable keyboard input handling (default: false for display-only mode).
 *                        When false, no keyboard input (hardware or soft) is accepted.
 * @param showSoftKeyboard Whether to show the soft keyboard/IME (default: true when keyboardEnabled=true).
 *                         Only applies when keyboardEnabled=true. Hardware keyboard always works when keyboardEnabled=true.
 * @param focusRequester Focus requester for keyboard input (if enabled)
 * @param onTerminalTap Callback for a simple tap event on the terminal (when no selection is active)
 * @param onImeVisibilityChanged Callback invoked when IME visibility changes (true = shown, false = hidden)
 * @param forcedSize Force terminal to specific dimensions (rows, cols). When set, font size is calculated to fit.
 * @param onSelectionControllerAvailable Optional callback providing access to the SelectionController for controlling selection mode
 * @param onHyperlinkClick Callback when user taps on an OSC8 hyperlink. Receives the URL as parameter.
 * @param virtualWidthColumns Optional number of columns for virtual width. When set, the terminal
 *                            renders with this many columns (which may exceed the physical screen width)
 *                            and single-finger horizontal drag pans the view. Height is still calculated
 *                            from available space. When null (default), terminal sizes to available width.
 * @param horizontalScrollIndicatorBottomOffset Offset from the bottom for the horizontal scroll indicator.
 * @param onMouseClick Callback when user taps on terminal and mouse mode is enabled. Receives row, col (0-indexed), and button (0=left).
 * @param onMouseScroll Callback when user drags vertically and mouse mode is enabled. Receives row, col (0-indexed), and scrollUp (true=up, false=down).
 * @param onShowKeyboardPanel Callback when user swipes up from bottom of screen to show keyboard panel.
 * @param onHideKeyboardPanel Callback when user swipes down from top of screen to hide keyboard panel.
 */
@Composable
fun Terminal(
    terminalEmulator: TerminalEmulator,
    modifier: Modifier = Modifier,
    typeface: Typeface = Typeface.MONOSPACE,
    initialFontSize: TextUnit = 11.sp,
    minFontSize: TextUnit = 6.sp,
    maxFontSize: TextUnit = 30.sp,
    backgroundColor: Color = Color.Black,
    foregroundColor: Color = Color.White,
    keyboardEnabled: Boolean = false,
    showSoftKeyboard: Boolean = true,
    focusRequester: FocusRequester = remember { FocusRequester() },
    onTerminalTap: () -> Unit = {},
    onImeVisibilityChanged: (Boolean) -> Unit = {},
    forcedSize: Pair<Int, Int>? = null,
    modifierManager: ModifierManager? = null,
    onSelectionControllerAvailable: ((SelectionController) -> Unit)? = null,
    onHyperlinkClick: (String) -> Unit = {},
    virtualWidthColumns: Int? = null,
    horizontalScrollIndicatorBottomOffset: Dp = 0.dp,
    backtickAsEscape: Boolean = false,
    enableComposingOverlay: Boolean = false,
    onMouseClick: ((row: Int, col: Int, button: Int) -> Unit)? = null,
    onMouseScroll: ((row: Int, col: Int, scrollUp: Boolean) -> Unit)? = null,
    onShowKeyboardPanel: () -> Unit = {},
    onHideKeyboardPanel: () -> Unit = {}
) {
    TerminalWithAccessibility(
        terminalEmulator = terminalEmulator,
        modifier = modifier,
        typeface = typeface,
        initialFontSize = initialFontSize,
        minFontSize = minFontSize,
        maxFontSize = maxFontSize,
        backgroundColor = backgroundColor,
        foregroundColor = foregroundColor,
        keyboardEnabled = keyboardEnabled,
        showSoftKeyboard = showSoftKeyboard,
        focusRequester = focusRequester,
        onTerminalTap = onTerminalTap,
        onImeVisibilityChanged = onImeVisibilityChanged,
        forcedSize = forcedSize,
        modifierManager = modifierManager,
        onSelectionControllerAvailable = onSelectionControllerAvailable,
        onHyperlinkClick = onHyperlinkClick,
        virtualWidthColumns = virtualWidthColumns,
        horizontalScrollIndicatorBottomOffset = horizontalScrollIndicatorBottomOffset,
        backtickAsEscape = backtickAsEscape,
        enableComposingOverlay = enableComposingOverlay,
        onMouseClick = onMouseClick,
        onMouseScroll = onMouseScroll,
        onShowKeyboardPanel = onShowKeyboardPanel,
        onHideKeyboardPanel = onHideKeyboardPanel
    )
}

/**
 * Used for testing accessibility.
 *
 * @see Terminal
 */
@VisibleForTesting
@Composable
fun TerminalWithAccessibility(
    terminalEmulator: TerminalEmulator,
    modifier: Modifier = Modifier,
    typeface: Typeface = Typeface.MONOSPACE,
    initialFontSize: TextUnit = 11.sp,
    minFontSize: TextUnit = 6.sp,
    maxFontSize: TextUnit = 30.sp,
    backgroundColor: Color = Color.Black,
    foregroundColor: Color = Color.White,
    keyboardEnabled: Boolean = false,
    showSoftKeyboard: Boolean = true,
    focusRequester: FocusRequester = remember { FocusRequester() },
    onTerminalTap: () -> Unit = {},
    onImeVisibilityChanged: (Boolean) -> Unit = {},
    forcedSize: Pair<Int, Int>? = null,
    modifierManager: ModifierManager? = null,
    forceAccessibilityEnabled: Boolean? = null,
    onSelectionControllerAvailable: ((SelectionController) -> Unit)? = null,
    onHyperlinkClick: (String) -> Unit = {},
    virtualWidthColumns: Int? = null,
    horizontalScrollIndicatorBottomOffset: Dp = 0.dp,
    backtickAsEscape: Boolean = false,
    enableComposingOverlay: Boolean = false,
    onMouseClick: ((row: Int, col: Int, button: Int) -> Unit)? = null,
    onMouseScroll: ((row: Int, col: Int, scrollUp: Boolean) -> Unit)? = null,
    onShowKeyboardPanel: () -> Unit = {},
    onHideKeyboardPanel: () -> Unit = {}
) {
    if (terminalEmulator !is TerminalEmulatorImpl) {
        Box(
            modifier = Modifier.fillMaxSize()
        ) {
            Text("Unknown TerminalEmulator type")
        }
        return
    }

    val terminalEmulator: TerminalEmulatorImpl = terminalEmulator

    val density = LocalDensity.current
    val clipboardManager = LocalClipboardManager.current
    val keyboardController = LocalSoftwareKeyboardController.current

    // Track accessibility state - only enable accessibility features when needed
    val systemAccessibilityEnabled by rememberAccessibilityState()
    val accessibilityEnabled = forceAccessibilityEnabled ?: systemAccessibilityEnabled

    // Observe terminal state via StateFlow
    val screenState = rememberTerminalScreenState(terminalEmulator)

    // Keyboard handler (will be updated with selectionController after it's created)
    val keyboardHandler = remember(terminalEmulator) {
        KeyboardHandler(terminalEmulator, modifierManager)
    }

    // Font size and zoom state
    var zoomScale by remember(terminalEmulator) { mutableStateOf(1f) }
    var zoomOffset by remember(terminalEmulator) { mutableStateOf(Offset.Zero) }
    var zoomOrigin by remember(terminalEmulator) { mutableStateOf(TransformOrigin.Center) }
    var isZooming by remember(terminalEmulator) { mutableStateOf(false) }
    var calculatedFontSize by remember(terminalEmulator) { mutableStateOf(initialFontSize) }

    // Horizontal pan state for virtual width
    var horizontalPanOffset by remember(terminalEmulator) { mutableStateOf(0f) }
    var maxHorizontalPan by remember(terminalEmulator) { mutableStateOf(0f) }

    // Flag to prevent scroll sync feedback loop during user drag
    var isUserScrolling by remember(terminalEmulator) { mutableStateOf(false) }

    // Generation counter to track current gesture - prevents stale fling completions from affecting new gestures
    var scrollGestureGeneration by remember(terminalEmulator) { mutableStateOf(0) }

    // Magnifying glass state
    var showMagnifier by remember(terminalEmulator) { mutableStateOf(false) }
    var magnifierPosition by remember(terminalEmulator) { mutableStateOf(Offset.Zero) }

    // Cursor blink state
    var cursorBlinkVisible by remember(terminalEmulator) { mutableStateOf(true) }

    // IME text field state (hidden BasicTextField for capturing IME input)
    val imeFocusRequester = remember { FocusRequester() }

    // Review Mode state for accessibility
    var isReviewMode by remember(terminalEmulator) { mutableStateOf(false) }
    val reviewFocusRequester = remember { FocusRequester() }

    // Manage focus and IME visibility
    // Determine if IME should be shown:
    // 1. keyboardEnabled is true (master switch)
    // 2. showSoftKeyboard is true (user wants IME visible)
    val shouldShowIme = keyboardEnabled && showSoftKeyboard

    // Keep reference to ImeInputView for controlling IME
    var imeInputView by remember { mutableStateOf<ImeInputView?>(null) }

    // Composing text overlay state (for voice input)
    var composingText by remember(terminalEmulator) { mutableStateOf<String?>(null) }
    var showComposingOverlay by remember(terminalEmulator) { mutableStateOf(false) }

    // Delayed overlay visibility - only show if composing continues for >300ms
    // This skips quick swipe gestures but catches voice input
    LaunchedEffect(composingText, enableComposingOverlay) {
        if (composingText != null && enableComposingOverlay) {
            delay(300L) // Wait 300ms before showing
            if (composingText != null) {
                showComposingOverlay = true
            }
        } else {
            showComposingOverlay = false
        }
    }

    // Cleanup IME when component is disposed
    DisposableEffect(imeInputView) {
        onDispose {
            Log.d("Terminal", "Disposing Terminal - hiding IME")
            imeInputView?.hideIme()
        }
    }

    // React to IME state changes
    LaunchedEffect(shouldShowIme, imeInputView) {
        Log.d("Terminal", "IME state changed: shouldShowIme=$shouldShowIme (imeInputView=$imeInputView)")

        imeInputView?.let { view ->
            if (shouldShowIme) {
                Log.d("Terminal", "Showing IME via InputMethodManager")
                delay(IME_SHOW_DELAY_MS)
                view.showIme()
                Log.d("Terminal", "IME show completed")
                onImeVisibilityChanged(true)
            } else {
                Log.d("Terminal", "Hiding IME via InputMethodManager")
                view.hideIme()
                Log.d("Terminal", "IME hide completed")
                onImeVisibilityChanged(false)
            }
        }
    }

    // Manage focus based on Review Mode
    LaunchedEffect(isReviewMode) {
        if (isReviewMode) {
            // Entering Review Mode: hide keyboard, focus on accessibility overlay
            keyboardController?.hide()
            delay(UI_SETTLE_DELAY_MS)
            try {
                reviewFocusRequester.requestFocus()
            } catch (_: IllegalStateException) {
                // Focus requester not attached yet, ignore
            }
        } else {
            // Exiting Review Mode: return focus to input field if keyboard enabled
            if (keyboardEnabled && shouldShowIme) {
                delay(UI_SETTLE_DELAY_MS)
                imeFocusRequester.requestFocus()
                delay(KEYBOARD_SHOW_DELAY_MS)
                keyboardController?.show()
            }
        }
    }

    // Cursor blink animation
    LaunchedEffect(
        screenState.snapshot.cursorVisible,
        screenState.snapshot.cursorBlink,
        screenState.snapshot.cursorRow,
        screenState.snapshot.cursorCol
    ) {
        if (screenState.snapshot.cursorVisible) {
            cursorBlinkVisible = true
            if (screenState.snapshot.cursorBlink) {
                // Show cursor immediately when it moves or becomes visible
                while (true) {
                    delay(CURSOR_BLINK_RATE_MS)
                    cursorBlinkVisible = !cursorBlinkVisible
                }
            }
        } else {
            cursorBlinkVisible = false
        }
    }

    // Create TextPaint for measuring and drawing (base size)
    val textPaint = remember(typeface, calculatedFontSize) {
        TextPaint().apply {
            this.typeface = typeface
            textSize = with(density) { calculatedFontSize.toPx() }
            isAntiAlias = true
        }
    }

    // Base character dimensions (unzoomed)
    val baseCharWidth = remember(textPaint) {
        textPaint.measureText("M")
    }

    val baseCharHeight = remember(textPaint) {
        val metrics = textPaint.fontMetrics
        metrics.descent - metrics.ascent
    }

    val baseCharBaseline = remember(textPaint) {
        -textPaint.fontMetrics.ascent
    }

    // Scroll animation state
    val scrollOffset = remember(terminalEmulator) { Animatable(0f) }
    val maxScroll = remember(screenState.snapshot.scrollback.size, baseCharHeight) {
        screenState.snapshot.scrollback.size * baseCharHeight
    }

    // Selection manager
    val selectionManager = remember(terminalEmulator) {
        SelectionManager()
    }

    // Selection controller - expose API for external control
    val selectionController = remember(terminalEmulator, selectionManager) {
        object : SelectionController {
            override val isSelectionActive: Boolean
                get() = selectionManager.mode != SelectionMode.NONE

            override fun startSelection(mode: SelectionMode) {
                if (selectionManager.mode == SelectionMode.NONE) {
                    // Start at cursor position or center of screen
                    val row = screenState.snapshot.cursorRow.coerceIn(0, screenState.snapshot.rows - 1)
                    val col = screenState.snapshot.cursorCol.coerceIn(0, screenState.snapshot.cols - 1)
                    selectionManager.startSelection(row, col, mode)
                }
            }

            override fun toggleSelection() {
                if (selectionManager.mode == SelectionMode.NONE) {
                    startSelection()
                } else {
                    clearSelection()
                }
            }

            override fun moveSelectionUp() {
                selectionManager.moveSelectionUp(screenState.snapshot.rows)
            }

            override fun moveSelectionDown() {
                selectionManager.moveSelectionDown(screenState.snapshot.rows)
            }

            override fun moveSelectionLeft() {
                selectionManager.moveSelectionLeft(screenState.snapshot.cols)
            }

            override fun moveSelectionRight() {
                selectionManager.moveSelectionRight(screenState.snapshot.cols)
            }

            override fun toggleSelectionMode() {
                selectionManager.toggleMode(screenState.snapshot.cols)
            }

            override fun finishSelection() {
                selectionManager.endSelection()
            }

            override fun copySelection(): String {
                val text = selectionManager.getSelectedText(screenState.snapshot, screenState.scrollbackPosition)
                if (text.isNotEmpty()) {
                    clipboardManager.setText(AnnotatedString(text))
                    selectionManager.clearSelection()
                }
                return text
            }

            override fun clearSelection() {
                selectionManager.clearSelection()
            }
        }
    }

    // Provide selection controller to caller and keyboard handler
    LaunchedEffect(selectionController) {
        keyboardHandler.selectionController = selectionController
        onSelectionControllerAvailable?.invoke(selectionController)
    }

    // Coroutine scope for animations
    val coroutineScope = rememberCoroutineScope()

    // Setup keyboard input callback to reset scroll position
    LaunchedEffect(screenState, scrollOffset) {
        keyboardHandler.onInputProcessed = {
            Log.d("Terminal", "KEYBOARD INPUT: scrolling to bottom from scrollbackPos=${screenState.scrollbackPosition}")
            screenState.scrollToBottom()
            coroutineScope.launch {
                scrollOffset.snapTo(0f)
            }
        }
    }

    // Setup pan jump callback for Ctrl+Alt+Arrow (hardware keyboard)
    SideEffect {
        keyboardHandler.onPanJumpRequest = { toRight ->
            horizontalPanOffset = if (toRight) maxHorizontalPan else 0f
        }
    }

    // Update backtick-to-Escape setting for hardware keyboard
    SideEffect {
        keyboardHandler.backtickAsEscape = backtickAsEscape
    }

    val viewConfiguration = LocalViewConfiguration.current

    var availableWidth by remember { mutableStateOf(0) }
    var availableHeight by remember { mutableStateOf(0) }

    Box(
        modifier = modifier
            .fillMaxSize()
            .onSizeChanged {
                availableWidth = it.width
                availableHeight = it.height
            }
            .then(
                if (keyboardEnabled) {
                    Modifier
                        .focusable()
                        .onPreviewKeyEvent { event ->
                            // In Review Mode, let accessibility system handle navigation keys
                            if (isReviewMode) {
                                // Allow arrow keys, Page Up/Down to navigate accessibility tree
                                when (event.key) {
                                    Key.DirectionUp,
                                    Key.DirectionDown,
                                    Key.DirectionLeft,
                                    Key.DirectionRight,
                                    Key.PageUp,
                                    Key.PageDown -> false // Don't consume - let system handle
                                    else -> {
                                        // Any other key exits Review Mode and goes to shell
                                        isReviewMode = false
                                        keyboardHandler.onKeyEvent(event)
                                    }
                                }
                            } else {
                                // Input Mode: send all keys to shell
                                keyboardHandler.onKeyEvent(event)
                            }
                        }
                } else {
                    Modifier
                }
            )
    ) {

        // Calculate font size if forcedSize is specified
        if (forcedSize != null) {
            val (forcedRows, forcedCols) = forcedSize
            LaunchedEffect(availableWidth, availableHeight, forcedRows, forcedCols) {
                if (availableWidth == 0 || availableHeight == 0) {
                    return@LaunchedEffect
                }

                val optimalSize = findOptimalFontSize(
                    targetRows = forcedRows,
                    targetCols = forcedCols,
                    availableWidth = availableWidth,
                    availableHeight = availableHeight,
                    minSize = minFontSize.value,
                    maxSize = maxFontSize.value,
                    typeface = typeface,
                    density = density.density
                )
                calculatedFontSize = optimalSize.sp
            }
        } else {
            // When not forcing size, reset the font size to the initial value.
            LaunchedEffect(initialFontSize) {
                if (calculatedFontSize != initialFontSize) {
                    calculatedFontSize = initialFontSize
                }
            }
        }

        // Resize terminal when dimensions change
        LaunchedEffect(terminalEmulator, availableWidth, availableHeight, forcedSize, baseCharWidth, baseCharHeight) {
            if (availableWidth == 0 || availableHeight == 0) {
                return@LaunchedEffect
            }

            // Use base dimensions for terminal sizing (not zoomed dimensions)
            // Use the larger of virtualWidthColumns and physical width (virtual is a minimum, not a cap)
            val physicalCols = charsPerDimension(availableWidth, baseCharWidth)
            val newCols = forcedSize?.second
                ?: virtualWidthColumns?.let { maxOf(it, physicalCols) }
                ?: physicalCols
            val newRows =
                forcedSize?.first ?: charsPerDimension(availableHeight, baseCharHeight)

            val dimensions = terminalEmulator.dimensions
            if (newRows != dimensions.rows || newCols != dimensions.columns) {
                terminalEmulator.resize(newRows, newCols)
            }
        }

        // Use base dimensions for terminal sizing (not zoomed dimensions)
        // Use the larger of virtualWidthColumns and physical width (virtual is a minimum, not a cap)
        val physicalCols = charsPerDimension(availableWidth, baseCharWidth)
        val newCols = forcedSize?.second
            ?: virtualWidthColumns?.let { maxOf(it, physicalCols) }
            ?: physicalCols
        val newRows =
            forcedSize?.first ?: charsPerDimension(availableHeight, baseCharHeight)

        // Auto-scroll to bottom when new content arrives (if user is at bottom)
        // Key only on content size changes - NOT on isUserScrolling to avoid re-running when gesture ends
        LaunchedEffect(screenState.snapshot.lines.size, screenState.snapshot.scrollback.size) {
            // Don't auto-scroll during user interaction or if user has scrolled up
            if (isUserScrolling || screenState.scrollbackPosition != 0) return@LaunchedEffect
            // User is at bottom and new content arrived - stay at bottom
            // (scrollbackPosition is already 0, so nothing to do)
        }

        // Sync scrollOffset when scrollbackPosition changes externally (but not during user scrolling)
        // Key only on scrollbackPosition - NOT on isUserScrolling to avoid re-running when gesture ends
        LaunchedEffect(screenState.scrollbackPosition) {
            // Skip sync during user drag to prevent feedback loop
            if (isUserScrolling) return@LaunchedEffect

            val targetOffset = screenState.scrollbackPosition * baseCharHeight
            if (!scrollOffset.isRunning && scrollOffset.value != targetOffset) {
                scrollOffset.snapTo(targetOffset)
            }
        }

        // Calculate actual terminal dimensions in pixels
        val terminalWidthPx = newCols * baseCharWidth
        val terminalHeightPx = newRows * baseCharHeight

        // Calculate max horizontal pan offset (only used when virtualWidthColumns is set)
        val calculatedMaxPan = (terminalWidthPx - availableWidth).coerceAtLeast(0f)
        val isHorizontalPanEnabled = virtualWidthColumns != null && calculatedMaxPan > 0f

        // Update maxHorizontalPan state synchronously so key events can access it
        if (maxHorizontalPan != calculatedMaxPan) {
            maxHorizontalPan = calculatedMaxPan
        }

        // Clamp horizontal pan offset when maxHorizontalPan changes (e.g., due to font size change)
        LaunchedEffect(calculatedMaxPan) {
            if (horizontalPanOffset > calculatedMaxPan) {
                horizontalPanOffset = calculatedMaxPan
            }
        }

        // Note: Horizontal auto-pan is disabled because TUI applications often park the cursor
        // at a fixed position (e.g., column 0) while rendering input text elsewhere using
        // escape sequences. This makes cursor-based auto-pan unreliable.
        // Users can manually pan horizontally using single-finger drag.

        // Draw terminal content with context menu overlay
        Box(
            modifier = (if (forcedSize != null && !isZooming && zoomScale == 1f) {
                // Add border outside the terminal content (only when not zooming)
                Modifier
                    .size(
                        width = with(density) { terminalWidthPx.toDp() },
                        height = with(density) { terminalHeightPx.toDp() }
                    )
                    .border(
                        width = TERMINAL_BORDER_WIDTH,
                        color = Color(0xFF4CAF50).copy(alpha = 0.6f)
                    )
            } else {
                Modifier.fillMaxSize()
            }).pointerInput(terminalEmulator, baseCharHeight) {
                val touchSlopSquared =
                    viewConfiguration.touchSlop * viewConfiguration.touchSlop
                coroutineScope {
                    awaitEachGesture {
                        var gestureType: GestureType = GestureType.Undetermined
                        var currentScrollOffset = 0f  // Local tracking during drag (avoids async issues)
                        var thisGestureGeneration = 0  // Tracks which generation this gesture belongs to
                        var gestureMaxScroll = 0f  // Will be set when entering scroll mode
                        val down = awaitFirstDown(requireUnconsumed = false)
                        var lastDragPosition = down.position  // Track last position for swipe-up detection
                        var mouseScrollAccumulator = 0f  // Accumulate drag for mouse wheel events

                        // 1. Check if touching a selection handle first
                        if (selectionManager.mode != SelectionMode.NONE && !selectionManager.isSelecting) {
                            val range = selectionManager.selectionRange
                            if (range != null) {
                                // Adjust touch position for horizontal pan offset
                                val adjustedTouchPos = Offset(
                                    down.position.x + horizontalPanOffset,
                                    down.position.y
                                )
                                val (touchingStart, touchingEnd) = isTouchingHandle(
                                    adjustedTouchPos,
                                    range,
                                    baseCharWidth,
                                    baseCharHeight
                                )
                                if (touchingStart || touchingEnd) {
                                    gestureType = GestureType.HandleDrag
                                    // Handle drag
                                    showMagnifier = true
                                    magnifierPosition = down.position

                                    drag(down.id) { change ->
                                        // Account for horizontal pan offset when calculating column
                                        val newCol =
                                            ((change.position.x + horizontalPanOffset) / baseCharWidth).toInt()
                                                .coerceIn(0, screenState.snapshot.cols - 1)
                                        val newRow =
                                            (change.position.y / baseCharHeight).toInt()
                                                .coerceIn(0, screenState.snapshot.rows - 1)

                                        if (touchingStart) {
                                            selectionManager.updateSelectionStart(
                                                newRow,
                                                newCol
                                            )
                                        } else {
                                            selectionManager.updateSelectionEnd(
                                                newRow,
                                                newCol
                                            )
                                        }

                                        magnifierPosition = change.position
                                        change.consume()
                                    }

                                    showMagnifier = false
                                    // Don't auto-show menu again after dragging handle
                                    return@awaitEachGesture
                                }
                            }
                        }

                        // 2. Long press detection for selection
                        // Instead of using launch{} which causes race conditions,
                        // we track the start time and check elapsed time in the event loop
                        var longPressDetected = false
                        val longPressStartTime = System.currentTimeMillis()
                        val longPressTimeoutMs = viewConfiguration.longPressTimeoutMillis

                        // 3. Check for multi-touch (zoom)
                        val secondPointer = withTimeoutOrNull(
                            WAIT_FOR_SECOND_TOUCH_MS
                        ) {
                            awaitPointerEvent().changes.firstOrNull { it.id != down.id && it.pressed }
                        }

                        if (secondPointer != null) {
                            gestureType = GestureType.Zoom

                            // Handle zoom using Compose's built-in gesture calculations
                            isZooming = true

                            val centerX = (down.position.x + secondPointer.position.x) / 2f
                            val centerY = (down.position.y + secondPointer.position.y) / 2f
                            zoomOrigin = TransformOrigin(
                                pivotFractionX = centerX / size.width,
                                pivotFractionY = centerY / size.height
                            )

                            while (true) {
                                val event = awaitPointerEvent()
                                if (event.changes.all { !it.pressed }) break

                                if (event.changes.size > 1) {
                                    val gestureZoom = event.calculateZoom()
                                    val gesturePan = event.calculatePan()

                                    val oldScale = zoomScale
                                    val newScale =
                                        (oldScale * gestureZoom).coerceIn(
                                            MIN_ZOOM_SCALE,
                                            MAX_ZOOM_SCALE
                                        )

                                    zoomOffset += gesturePan
                                    zoomScale = newScale

                                    event.changes.forEach { it.consume() }
                                }
                            }

                            // Gesture ended - reset
                            isZooming = false
                            zoomScale = 1f
                            zoomOffset = Offset.Zero

                            return@awaitEachGesture
                        }

                        // 4. Track velocity for scroll fling
                        val velocityTracker = VelocityTracker()
                        velocityTracker.addPosition(down.uptimeMillis, down.position)

                        // 5. Event loop for single-touch gestures
                        while (true) {
                            val event: PointerEvent =
                                awaitPointerEvent(PointerEventPass.Main)
                            if (event.changes.all { !it.pressed }) break

                            val change = event.changes.first()
                            velocityTracker.addPosition(
                                change.uptimeMillis,
                                change.position
                            )
                            val dragAmount = change.positionChange()

                            // Determine gesture if still undetermined
                            if (gestureType == GestureType.Undetermined && !longPressDetected) {
                                val totalOffset = change.position - down.position
                                if (totalOffset.getDistanceSquared() > touchSlopSquared) {
                                    // Movement exceeded touch slop - this is a scroll gesture
                                    gestureType = GestureType.Scroll
                                    isUserScrolling = true
                                    scrollGestureGeneration++  // Increment to invalidate any pending fling completions
                                    thisGestureGeneration = scrollGestureGeneration
                                    // Capture maxScroll NOW when entering scroll mode (not at gesture start)
                                    // This ensures we use the scrollback size at the moment scrolling begins
                                    gestureMaxScroll = screenState.snapshot.scrollback.size * baseCharHeight
                                    // Initialize local scroll tracking from current position only
                                    // Don't add totalOffset.y here - let the scroll handler add dragAmount.y
                                    currentScrollOffset = screenState.scrollbackPosition * baseCharHeight
                                    Log.d("Terminal", "SCROLL START: gen=$thisGestureGeneration, scrollbackPos=${screenState.scrollbackPosition}, currentScrollOffset=$currentScrollOffset, gestureMaxScroll=$gestureMaxScroll, totalOffset=$totalOffset")
                                    // Apply initial horizontal offset
                                    if (isHorizontalPanEnabled) {
                                        horizontalPanOffset = (horizontalPanOffset - totalOffset.x)
                                            .coerceIn(0f, maxHorizontalPan)
                                    }
                                    // Clear any active selection when scrolling starts
                                    if (selectionManager.mode != SelectionMode.NONE) {
                                        selectionManager.clearSelection()
                                    }
                                    // Don't continue - let the scroll handler run to apply dragAmount
                                } else {
                                    // Still within touch slop - check for long press timeout
                                    val elapsedTime = System.currentTimeMillis() - longPressStartTime
                                    if (elapsedTime >= longPressTimeoutMs && selectionManager.mode == SelectionMode.NONE) {
                                        // Long press detected - start selection
                                        longPressDetected = true
                                        gestureType = GestureType.Selection

                                        // Account for horizontal pan offset when calculating column
                                        val col = ((down.position.x + horizontalPanOffset) / baseCharWidth).toInt()
                                            .coerceIn(0, screenState.snapshot.cols - 1)
                                        val row = (down.position.y / baseCharHeight).toInt()
                                            .coerceIn(0, screenState.snapshot.rows - 1)

                                        selectionManager.startSelection(row, col)
                                        showMagnifier = true
                                        magnifierPosition = down.position
                                    }
                                }
                            }

                            // Handle based on gesture type
                            when (gestureType) {
                                GestureType.Selection -> {
                                    if (selectionManager.isSelecting) {
                                        // Account for horizontal pan offset when calculating column
                                        val dragCol =
                                            ((change.position.x + horizontalPanOffset) / baseCharWidth).toInt()
                                                .coerceIn(0, screenState.snapshot.cols - 1)
                                        val dragRow =
                                            (change.position.y / baseCharHeight).toInt()
                                                .coerceIn(0, screenState.snapshot.rows - 1)
                                        selectionManager.updateSelection(
                                            dragRow,
                                            dragCol
                                        )
                                        magnifierPosition = change.position
                                    }
                                }

                                GestureType.Scroll -> {
                                    // Mark that user is scrolling to prevent sync feedback
                                    isUserScrolling = true

                                    // Only update vertical scroll if there's meaningful vertical movement
                                    // This prevents horizontal panning from accidentally affecting scroll position
                                    if (kotlin.math.abs(dragAmount.y) > 0.5f) {
                                        if (onMouseScroll != null) {
                                            // Mouse mode: send mouse wheel events instead of scrollback
                                            // Accumulate drag and fire scroll events per line of movement
                                            mouseScrollAccumulator += dragAmount.y
                                            val scrollLines = (mouseScrollAccumulator / baseCharHeight).toInt()
                                            if (scrollLines != 0) {
                                                // Calculate position for scroll event (use current drag position)
                                                val adjustedX = change.position.x + horizontalPanOffset
                                                val scrollCol = (adjustedX / baseCharWidth).toInt()
                                                    .coerceIn(0, screenState.snapshot.cols - 1)
                                                val scrollRow = (change.position.y / baseCharHeight).toInt()
                                                    .coerceIn(0, screenState.snapshot.rows - 1)
                                                // Invert direction: drag down = scroll up, drag up = scroll down
                                                // (matches natural scrolling - drag down to see content above)
                                                val scrollUp = scrollLines > 0
                                                repeat(kotlin.math.abs(scrollLines)) {
                                                    onMouseScroll(scrollRow, scrollCol, scrollUp)
                                                }
                                                mouseScrollAccumulator -= scrollLines * baseCharHeight
                                            }
                                        } else {
                                            // Normal mode: scroll terminal buffer
                                            // Update local scroll tracking (avoids async issues with Animatable)
                                            // Drag up (negative dragAmount.y) = view newer content = decrease scrollback
                                            // Drag down (positive dragAmount.y) = view older content = increase scrollback
                                            // Use gestureMaxScroll (captured at gesture start) to avoid mid-gesture changes from TUI output
                                            currentScrollOffset = (currentScrollOffset + dragAmount.y)
                                                .coerceIn(0f, gestureMaxScroll)

                                            // Update terminal buffer scrollback position
                                            val scrolledLines = (currentScrollOffset / baseCharHeight).toInt()
                                            if (scrolledLines != screenState.scrollbackPosition) {
                                                screenState.scrollBy(scrolledLines - screenState.scrollbackPosition)
                                            }
                                        }
                                    }

                                    // Update horizontal pan offset (if virtual width enabled)
                                    // Drag right (negative dragAmount.x) = see content on the left (decrease panOffset)
                                    // Drag left (positive dragAmount.x) = see content on the right (increase panOffset)
                                    if (isHorizontalPanEnabled) {
                                        horizontalPanOffset = (horizontalPanOffset - dragAmount.x)
                                            .coerceIn(0f, maxHorizontalPan)
                                    }
                                }

                                else -> {}
                            }

                            lastDragPosition = change.position
                            change.consume()
                        }

                        // 6. Gesture ended - cleanup
                        when (gestureType) {
                            GestureType.Scroll -> {
                                // Detect swipe up from bottom 30% of screen to show keyboard panel
                                val isSwipeUpFromBottom = down.position.y > size.height * 0.7f &&
                                    (down.position.y - lastDragPosition.y) > viewConfiguration.touchSlop * 1.5f
                                if (isSwipeUpFromBottom) {
                                    isUserScrolling = false
                                    onShowKeyboardPanel()
                                    return@awaitEachGesture
                                }

                                // Detect swipe down from top 30% of screen to hide keyboard panel
                                val isSwipeDownFromTop = down.position.y < size.height * 0.3f &&
                                    (lastDragPosition.y - down.position.y) > viewConfiguration.touchSlop * 1.5f
                                if (isSwipeDownFromTop) {
                                    isUserScrolling = false
                                    onHideKeyboardPanel()
                                    return@awaitEachGesture
                                }

                                val velocity = velocityTracker.calculateVelocity()
                                // Only apply vertical fling if gesture had significant vertical component
                                // This prevents horizontal panning from accidentally triggering vertical scroll
                                val hasVerticalIntent = kotlin.math.abs(velocity.y) > kotlin.math.abs(velocity.x) ||
                                    kotlin.math.abs(velocity.y) > 100f

                                // Horizontal flick detection - jump to edges instantly
                                // Flick right (positive velocity.x) = jump to left edge
                                // Flick left (negative velocity.x) = jump to right edge
                                val horizontalFlickThreshold = 1500f  // pixels per second
                                val hasHorizontalFlick = isHorizontalPanEnabled &&
                                    kotlin.math.abs(velocity.x) > horizontalFlickThreshold &&
                                    kotlin.math.abs(velocity.x) > kotlin.math.abs(velocity.y) * 2

                                if (hasHorizontalFlick) {
                                    horizontalPanOffset = if (velocity.x > 0) {
                                        // Flicking right - jump to left edge
                                        0f
                                    } else {
                                        // Flicking left - jump to right edge
                                        maxHorizontalPan
                                    }
                                }

                                Log.d("Terminal", "SCROLL END: gen=$thisGestureGeneration, scrollbackPos=${screenState.scrollbackPosition}, currentScrollOffset=$currentScrollOffset, velocity=$velocity, hasVerticalIntent=$hasVerticalIntent, gestureMaxScroll=$gestureMaxScroll")

                                // Skip vertical fling animation when mouse scroll mode is enabled
                                // (vertical drag sends mouse wheel events, not scrollback)
                                if (onMouseScroll != null) {
                                    isUserScrolling = false
                                } else {
                                    val gestureGen = thisGestureGeneration  // Capture for coroutine
                                    val flingMaxScroll = gestureMaxScroll  // Capture maxScroll from gesture start
                                    coroutineScope.launch {
                                        // Sync scrollOffset from local tracking before fling
                                        Log.d("Terminal", "SCROLL FLING: gen=$gestureGen, snapping scrollOffset to $currentScrollOffset")
                                        scrollOffset.snapTo(currentScrollOffset)

                                        if (hasVerticalIntent) {
                                            var targetValue = currentScrollOffset
                                            scrollOffset.animateDecay(
                                                initialVelocity = velocity.y,
                                                animationSpec = splineBasedDecay(density)
                                            ) {
                                                targetValue = value
                                                // Update terminal buffer during animation
                                                val scrolledLines =
                                                    (value / baseCharHeight).toInt()
                                                screenState.scrollBy(scrolledLines - screenState.scrollbackPosition)
                                            }

                                            // Clamp final position if needed (use flingMaxScroll captured at gesture start)
                                            if (targetValue < 0f) {
                                                Log.d("Terminal", "FLING CLAMP: targetValue=$targetValue < 0, scrolling to bottom")
                                                scrollOffset.snapTo(0f)
                                                screenState.scrollToBottom()
                                            } else if (targetValue > flingMaxScroll) {
                                                Log.d("Terminal", "FLING CLAMP: targetValue=$targetValue > flingMaxScroll=$flingMaxScroll, scrolling to top")
                                                scrollOffset.snapTo(flingMaxScroll)
                                                screenState.scrollToTop()
                                            }
                                        }

                                        // Clear user scrolling flag after fling completes - but only if this is still the current gesture
                                        if (gestureGen == scrollGestureGeneration) {
                                            Log.d("Terminal", "SCROLL COMPLETE: gen=$gestureGen, setting isUserScrolling=false, scrollbackPos=${screenState.scrollbackPosition}")
                                            isUserScrolling = false
                                        } else {
                                            Log.d("Terminal", "SCROLL COMPLETE: gen=$gestureGen STALE (current=$scrollGestureGeneration), not clearing isUserScrolling")
                                        }
                                    }
                                }
                            }

                            GestureType.Selection -> {
                                showMagnifier = false
                                if (selectionManager.isSelecting) {
                                    selectionManager.endSelection()
                                }
                            }

                            GestureType.Undetermined -> {
                                // This is a tap. If a selection is active, clear it.
                                // Otherwise, check for hyperlink, mouse click, or forward the tap.
                                if (selectionManager.mode != SelectionMode.NONE) {
                                    selectionManager.clearSelection()
                                } else {
                                    // Check if tap is on a hyperlink
                                    // Apply horizontal pan offset for virtual width
                                    val adjustedX = down.position.x + horizontalPanOffset
                                    val tapCol = (adjustedX / baseCharWidth).toInt()
                                        .coerceIn(0, screenState.snapshot.cols - 1)
                                    val tapRow = (down.position.y / baseCharHeight).toInt()
                                        .coerceIn(0, screenState.snapshot.rows - 1)
                                    val line = screenState.getVisibleLine(tapRow)
                                    val hyperlinkUrl = line.getHyperlinkUrlAt(tapCol)

                                    if (hyperlinkUrl != null) {
                                        // User tapped on a hyperlink
                                        onHyperlinkClick(hyperlinkUrl)
                                    } else if (onMouseClick != null) {
                                        // Mouse mode enabled - send tap as mouse click
                                        onMouseClick(tapRow, tapCol, 0)  // 0 = left button
                                    } else {
                                        // Request focus when terminal is tapped to show keyboard
                                        if (keyboardEnabled) {
                                            focusRequester.requestFocus()
                                        }
                                        onTerminalTap()
                                    }
                                }
                            }

                            else -> {}
                        }
                    }
                }
            }
        ) {
            Canvas(
                modifier = Modifier
                    .fillMaxSize()
                    .clearAndSetSemantics {
                        // Hide Canvas from accessibility tree - AccessibilityOverlay provides semantic structure
                    }
                    .graphicsLayer {
                        // Apply horizontal pan offset (for virtual width) plus zoom offset
                        translationX = zoomOffset.x * zoomScale - horizontalPanOffset
                        translationY = zoomOffset.y * zoomScale
                        scaleX = zoomScale
                        scaleY = zoomScale
                        transformOrigin = zoomOrigin
                    }
            ) {
                // Fill background - use full terminal width when virtual width is enabled
                drawRect(
                    color = backgroundColor,
                    size = if (isHorizontalPanEnabled) {
                        Size(terminalWidthPx, size.height)
                    } else {
                        size
                    }
                )

                // Draw each line (zoom/pan applied via graphicsLayer)
                for (row in 0 until screenState.snapshot.rows) {
                    val line = screenState.getVisibleLine(row)
                    drawLine(
                        line = line,
                        row = row,
                        charWidth = baseCharWidth,
                        charHeight = baseCharHeight,
                        charBaseline = baseCharBaseline,
                        textPaint = textPaint,
                        defaultFg = foregroundColor,
                        defaultBg = backgroundColor,
                        selectionManager = selectionManager
                    )
                }

                // Draw cursor (only when viewing current screen, not scrollback)
                if (screenState.snapshot.cursorVisible && screenState.scrollbackPosition == 0 && cursorBlinkVisible) {
                    drawCursor(
                        row = screenState.snapshot.cursorRow,
                        col = screenState.snapshot.cursorCol,
                        charWidth = baseCharWidth,
                        charHeight = baseCharHeight,
                        foregroundColor = foregroundColor,
                        cursorShape = screenState.snapshot.cursorShape
                    )
                }

                // Draw selection handles
                if (selectionManager.mode != SelectionMode.NONE && !selectionManager.isSelecting) {
                    val range = selectionManager.selectionRange
                    if (range != null) {
                        // Start handle
                        val startPosition = range.getStartPosition()
                        drawSelectionHandle(
                            row = startPosition.first,
                            col = startPosition.second,
                            charWidth = baseCharWidth,
                            charHeight = baseCharHeight,
                            pointingDown = false,
                        )

                        // End handle
                        val endPosition = range.getEndPosition()
                        drawSelectionHandle(
                            row = endPosition.first,
                            col = endPosition.second,
                            charWidth = baseCharWidth,
                            charHeight = baseCharHeight,
                            pointingDown = true,
                        )
                    }
                }
            }

            if (accessibilityEnabled) {
                AccessibilityOverlay(
                    screenState = screenState,
                    charHeight = baseCharHeight,
                    modifier = Modifier
                        .fillMaxSize()
                        .focusRequester(reviewFocusRequester)
                        .focusable(),
                    onToggleReviewMode = { isReviewMode = !isReviewMode },
                    isReviewMode = isReviewMode
                )

                if (!isReviewMode && keyboardEnabled) {
                    LiveOutputRegion(
                        screenState = screenState,
                        enabled = true,
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }
        }

        // Magnifying glass
        if (showMagnifier) {
            MagnifyingGlass(
                position = magnifierPosition,
                screenState = screenState,
                baseCharWidth = baseCharWidth,
                baseCharHeight = baseCharHeight,
                baseCharBaseline = baseCharBaseline,
                textPaint = textPaint,
                backgroundColor = backgroundColor,
                foregroundColor = foregroundColor,
                selectionManager = selectionManager
            )
        }

        // Copy button when text is selected
        if (selectionManager.mode != SelectionMode.NONE && !selectionManager.isSelecting) {
            val range = selectionManager.selectionRange
            if (range != null) {
                // Position copy button above the selection
                val endPosition = range.getEndPosition()
                val buttonX = endPosition.second * baseCharWidth
                val buttonY = endPosition.first * baseCharHeight - with(density) { COPY_BUTTON_OFFSET.toPx() }

                Box(
                    modifier = Modifier
                        .offset(
                            x = with(density) { buttonX.toDp() },
                            y = with(density) { buttonY.coerceAtLeast(0f).toDp() }
                        )
                ) {
                    FloatingActionButton(
                        onClick = {
                            val selectedText =
                                selectionManager.getSelectedText(screenState.snapshot, screenState.scrollbackPosition)
                            clipboardManager.setText(AnnotatedString(selectedText))
                            selectionManager.clearSelection()
                        },
                        modifier = Modifier.size(COPY_BUTTON_SIZE),
                        containerColor = Color.White,
                        contentColor = Color.Black
                    ) {
                        Text("Copy", style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
        }

        // Horizontal scroll indicator (for virtual width)
        if (isHorizontalPanEnabled) {
            HorizontalScrollIndicator(
                scrollFraction = if (maxHorizontalPan > 0f) horizontalPanOffset / maxHorizontalPan else 0f,
                viewportFraction = availableWidth.toFloat() / terminalWidthPx,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = horizontalScrollIndicatorBottomOffset)
            )
        }

        // Hidden AndroidView with custom InputConnection for IME (software keyboard) input
        // This provides proper backspace, enter key, and keyboard type handling
        // Must have non-zero size for Android to accept IME focus
        if (keyboardEnabled) {
            AndroidView(
                factory = { context ->
                    ImeInputView(
                        context = context,
                        keyboardHandler = keyboardHandler,
                        enableVoiceInputSupport = enableComposingOverlay,
                        onComposingTextChanged = { text ->
                            composingText = text
                        },
                        onTextCommitted = { text ->
                            // Clear composing state
                            composingText = null
                            showComposingOverlay = false
                            // Send to terminal
                            keyboardHandler.onTextInput(text.toByteArray(Charsets.UTF_8))
                        }
                    ).apply {
                        // Set up key event handling
                        setOnKeyListener { _, _, event ->
                            keyboardHandler.onKeyEvent(
                                androidx.compose.ui.input.key.KeyEvent(event)
                            )
                        }
                        // Store reference for IME control
                        imeInputView = this
                    }
                },
                modifier = Modifier
                    .size(1.dp)
                    .focusable()
                    .focusRequester(focusRequester)
            )
        }

        // Composing text overlay (for voice input)
        // Shows uncommitted text in a small panel above the keyboard area
        if (showComposingOverlay && composingText != null) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 8.dp)
                    .background(Color(0xE6333333), RoundedCornerShape(8.dp))
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            ) {
                Text(
                    text = composingText ?: "",
                    color = Color.White,
                    fontSize = 14.sp
                )
            }
        }
    }

}

/**
 * Draw a single terminal line.
 */
private fun DrawScope.drawLine(
    line: TerminalLine,
    row: Int,
    charWidth: Float,
    charHeight: Float,
    charBaseline: Float,
    textPaint: TextPaint,
    defaultFg: Color,
    defaultBg: Color,
    selectionManager: SelectionManager
) {
    val y = row * charHeight
    var x = 0f

    line.cells.forEachIndexed { col, cell ->
        val cellWidth = charWidth * cell.width

        // Check if this cell is selected
        val isSelected = selectionManager.isCellSelected(row, col)

        // Check if this cell is part of a hyperlink
        val isHyperlink = line.getHyperlinkUrlAt(col) != null

        // Determine colors (handle reverse video and selection)
        val fgColor = if (cell.reverse) cell.bgColor else cell.fgColor
        val bgColor = if (cell.reverse) cell.fgColor else cell.bgColor

        // Draw background (with selection highlight)
        val finalBgColor = if (isSelected) SELECTION_HIGHLIGHT_COLOR else bgColor
        if (finalBgColor != defaultBg || isSelected) {
            drawRect(
                color = finalBgColor,
                topLeft = Offset(x, y),
                size = Size(cellWidth, charHeight)
            )
        }

        // Draw character
        if (cell.char != ' ' || cell.combiningChars.isNotEmpty()) {
            val text = buildString {
                append(cell.char)
                cell.combiningChars.forEach { append(it) }
            }

            // Configure text paint for this cell
            textPaint.color = fgColor.toArgb()
            textPaint.isFakeBoldText = cell.bold
            textPaint.textSkewX = if (cell.italic) -0.25f else 0f
            // Underline if cell has underline OR if it's a hyperlink
            textPaint.isUnderlineText = cell.underline == 1 || isHyperlink
            textPaint.isStrikeThruText = cell.strike

            // Draw text
            drawContext.canvas.nativeCanvas.drawText(
                text,
                x,
                y + charBaseline,
                textPaint
            )

            // Draw double underline if needed
            if (cell.underline == 2) {
                drawDoubleUnderline(
                    x = x,
                    y = y + charBaseline,
                    width = cellWidth,
                    color = fgColor
                )
            }

            // Draw curly underline if needed
            if (cell.underline == 3) {
                drawCurlyUnderline(
                    x = x,
                    y = y + charBaseline,
                    width = cellWidth,
                    charWidth = charWidth,
                    color = fgColor
                )
            }
        }

        x += cellWidth
    }
}

/**
 * Draw a double underline (two parallel lines).
 *
 * @param x Start x position
 * @param y Baseline y position
 * @param width Width to draw the underline
 * @param color Color of the underline
 */
private fun DrawScope.drawDoubleUnderline(
    x: Float,
    y: Float,
    width: Float,
    color: Color
) {
    val paint = Paint().apply {
        this.color = color.toArgb()
        style = Paint.Style.STROKE
        strokeWidth = 1f
        isAntiAlias = true
    }

    val baseY = y + 2f
    val canvas = drawContext.canvas.nativeCanvas

    // Draw first line
    canvas.drawLine(x, baseY, x + width, baseY, paint)

    // Draw second line below the first
    canvas.drawLine(x, baseY + DOUBLE_UNDERLINE_SPACING, x + width, baseY + DOUBLE_UNDERLINE_SPACING, paint)
}

/**
 * Draw a curly/zigzag underline pattern (like spell-check underlines).
 * The pattern repeats seamlessly across characters by aligning with the character grid.
 * Each character starts at the same phase (top of wave) ensuring perfect continuity.
 *
 * @param x Start x position
 * @param y Baseline y position
 * @param width Width to draw the underline
 * @param charWidth Base character width (used to calculate wavelength)
 * @param color Color of the underline
 */
private fun DrawScope.drawCurlyUnderline(
    x: Float,
    y: Float,
    width: Float,
    charWidth: Float,
    color: Color
) {
    val path = Path()
    val wavelength = charWidth / CURLY_UNDERLINE_CYCLES_PER_CHAR
    val amplitude = CURLY_UNDERLINE_AMPLITUDE
    val halfWave = wavelength / 2

    val baseY = y + 3f

    // Start at the top of the wave (phase = 0)
    path.moveTo(x, baseY - amplitude)

    // Draw zigzag pattern across the width
    var currentX = x
    var goingDown = true // Start by going down from the top

    while (currentX < x + width) {
        currentX += halfWave
        if (currentX > x + width) {
            currentX = x + width
        }

        val nextY = if (goingDown) baseY + amplitude else baseY - amplitude
        path.lineTo(currentX, nextY)

        goingDown = !goingDown
    }

    // Draw the path
    drawContext.canvas.nativeCanvas.drawPath(
        path,
        Paint().apply {
            this.color = color.toArgb()
            style = Paint.Style.STROKE
            strokeWidth = 1f
            isAntiAlias = true
        }
    )
}

/**
 * Check if a touch position is near a selection handle.
 * Returns (touchingStart, touchingEnd).
 */
private fun isTouchingHandle(
    touchPos: Offset,
    range: SelectionRange,
    charWidth: Float,
    charHeight: Float,
    hitRadius: Float = HANDLE_HIT_RADIUS
): Pair<Boolean, Boolean> {
    val startPos = Offset(
        range.startCol * charWidth + charWidth / 2,
        range.startRow * charHeight
    )
    val endPos = Offset(
        range.endCol * charWidth + charWidth / 2,
        range.endRow * charHeight + charHeight
    )

    val distToStart = (touchPos - startPos).getDistance()
    val distToEnd = (touchPos - endPos).getDistance()

    return Pair(
        distToStart < hitRadius,
        distToEnd < hitRadius
    )
}

/**
 * Magnifying glass for text selection.
 */
@Composable
private fun MagnifyingGlass(
    position: Offset,
    screenState: TerminalScreenState,
    baseCharWidth: Float,
    baseCharHeight: Float,
    baseCharBaseline: Float,
    textPaint: TextPaint,
    backgroundColor: Color,
    foregroundColor: Color,
    selectionManager: SelectionManager
) {
    val magnifierSize = MAGNIFIER_SIZE_DP.dp
    val magnifierScale = MAGNIFIER_SCALE
    val density = LocalDensity.current
    val magnifierSizePx = with(density) { magnifierSize.toPx() }

    // Position magnifying glass well above the finger (so it's visible)
    val verticalOffset = with(density) { MAGNIFIER_VERTICAL_OFFSET.toPx() }
    val magnifierPos = Offset(
        x = (position.x - magnifierSizePx / 2).coerceIn(0f, Float.MAX_VALUE),
        y = (position.y - verticalOffset - magnifierSizePx).coerceAtLeast(0f)
    )

    // The actual touch point that should be centered in the magnifier
    val centerOffset = Offset(
        x = position.x - (magnifierSizePx / magnifierScale) * MAGNIFIER_CENTER_OFFSET_MULTIPLIER,
        y = position.y - (magnifierSizePx / magnifierScale) * MAGNIFIER_CENTER_OFFSET_MULTIPLIER,
    )

    Box(
        modifier = Modifier
            .offset(
                x = with(density) { magnifierPos.x.toDp() },
                y = with(density) { magnifierPos.y.toDp() }
            )
            .size(magnifierSize)
            .border(
                width = MAGNIFIER_BORDER_WIDTH,
                color = Color.Gray,
                shape = CircleShape
            )
            .background(
                color = Color.White.copy(alpha = MAGNIFIER_BACKGROUND_ALPHA),
                shape = CircleShape
            )
            .clip(CircleShape)
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            // Fill background
            drawRect(
                color = backgroundColor,
                size = size
            )

            // Apply magnification and translate to center the touch point
            translate(-centerOffset.x * magnifierScale, -centerOffset.y * magnifierScale) {
                scale(magnifierScale, magnifierScale) {
                    // Calculate which rows to draw
                    val centerRow = (position.y / baseCharHeight).toInt().coerceIn(0, screenState.snapshot.rows - 1)

                    // Draw a few rows around the touch point
                    for (rowOffset in -MAGNIFIER_ROW_RANGE..MAGNIFIER_ROW_RANGE) {
                        val row = (centerRow + rowOffset).coerceIn(0, screenState.snapshot.rows - 1)
                        val line = screenState.getVisibleLine(row)
                        drawLine(
                            line = line,
                            row = row,
                            charWidth = baseCharWidth,
                            charHeight = baseCharHeight,
                            charBaseline = baseCharBaseline,
                            textPaint = textPaint,
                            defaultFg = foregroundColor,
                            defaultBg = backgroundColor,
                            selectionManager = selectionManager
                        )
                    }
                }
            }
        }
    }
}

/**
 * Draw a selection handle (teardrop shape).
 */
private fun DrawScope.drawSelectionHandle(
    row: Int,
    col: Int,
    charWidth: Float,
    charHeight: Float,
    pointingDown: Boolean,
    color: Color = Color.White,
) {
    val handleWidthPx = SELECTION_HANDLE_WIDTH.toPx()

    // Position handle at the character position
    val charX = col * charWidth
    val charY = row * charHeight

    // Center the handle horizontally on the character
    val handleX = charX + charWidth / 2

    // Position vertically based on direction
    val handleY = if (pointingDown) {
        charY + charHeight // Bottom of character
    } else {
        charY // Top of character
    }

    val circleRadius = handleWidthPx / 2
    val circleY = if (pointingDown) {
        handleY + circleRadius
    } else {
        handleY - circleRadius
    }

    drawCircle(
        color = color,
        radius = circleRadius,
        center = Offset(handleX, circleY)
    )
}

/**
 * Draw the cursor with shape support (block, underline, bar).
 */
private fun DrawScope.drawCursor(
    row: Int,
    col: Int,
    charWidth: Float,
    charHeight: Float,
    foregroundColor: Color,
    cursorShape: CursorShape = CursorShape.BLOCK
) {
    val x = col * charWidth
    val y = row * charHeight

    when (cursorShape) {
        CursorShape.BLOCK -> {
            // Block cursor - full cell rectangle outline
            drawRect(
                color = foregroundColor,
                topLeft = Offset(x, y),
                size = Size(charWidth, charHeight),
                alpha = CURSOR_BLOCK_ALPHA
            )
        }

        CursorShape.UNDERLINE -> {
            // Underline cursor - line at bottom of cell
            val underlineHeight = charHeight * CURSOR_UNDERLINE_HEIGHT_RATIO
            drawRect(
                color = foregroundColor,
                topLeft = Offset(x, y + charHeight - underlineHeight),
                size = Size(charWidth, underlineHeight),
                alpha = CURSOR_LINE_ALPHA
            )
        }

        CursorShape.BAR_LEFT -> {
            // Bar cursor - vertical line at left of cell
            val barWidth = charWidth * CURSOR_BAR_WIDTH_RATIO
            drawRect(
                color = foregroundColor,
                topLeft = Offset(x, y),
                size = Size(barWidth, charHeight),
                alpha = CURSOR_LINE_ALPHA
            )
        }
    }
}

/**
 * Calculate pixel dimensions for a specific row/column count at a given font size.
 *
 * @param rows Number of rows
 * @param cols Number of columns
 * @param fontSize Font size in sp
 * @param typeface The typeface to use for measurement
 * @param density Screen density for sp to px conversion
 * @return Pair of (width in pixels, height in pixels)
 */
private fun calculateDimensions(
    rows: Int,
    cols: Int,
    fontSize: Float,
    typeface: Typeface,
    density: Float
): Pair<Float, Float> {
    val textPaint = TextPaint().apply {
        this.typeface = typeface
        textSize = fontSize * density
    }

    val charWidth = textPaint.measureText("M")
    val metrics = textPaint.fontMetrics
    val charHeight = metrics.descent - metrics.ascent

    val width = cols * charWidth
    val height = rows * charHeight

    return Pair(width, height)
}

/**
 * Find the optimal font size that allows the terminal to fit within the available space
 * while maintaining the exact target rows and columns.
 *
 * Uses binary search to efficiently find the largest font size that fits.
 *
 * @param targetRows Target number of rows
 * @param targetCols Target number of columns
 * @param availableWidth Available width in pixels
 * @param availableHeight Available height in pixels
 * @param minSize Minimum font size in sp
 * @param maxSize Maximum font size in sp
 * @param typeface The typeface to use for measurement
 * @param density Screen density for sp to px conversion
 * @return Optimal font size in sp
 */
private fun findOptimalFontSize(
    targetRows: Int,
    targetCols: Int,
    availableWidth: Int,
    availableHeight: Int,
    minSize: Float,
    maxSize: Float,
    typeface: Typeface,
    density: Float
): Float {
    var minSizeCurrent = minSize
    var maxSizeCurrent = maxSize

    // Binary search for optimal font size
    while (maxSizeCurrent - minSizeCurrent > FONT_SIZE_SEARCH_EPSILON) {
        val midSize = (minSizeCurrent + maxSizeCurrent) / 2f
        val (width, height) = calculateDimensions(
            rows = targetRows,
            cols = targetCols,
            fontSize = midSize,
            typeface = typeface,
            density = density
        )

        if (width <= availableWidth && height <= availableHeight) {
            // This size fits, try larger
            minSizeCurrent = midSize
        } else {
            // This size doesn't fit, try smaller
            maxSizeCurrent = midSize
        }
    }

    // Return the largest size that fits
    return minSizeCurrent.coerceIn(minSize, maxSize)
}

private fun charsPerDimension(pixels: Int, charPixels: Float) =
    (pixels / charPixels).toInt().coerceAtLeast(1)

/**
 * A thin horizontal scrollbar indicator showing the current horizontal pan position.
 * This is visual only and not interactive.
 *
 * @param scrollFraction Current scroll position as fraction (0.0 = left, 1.0 = right)
 * @param viewportFraction Visible portion as fraction of total width (0.0 to 1.0)
 * @param modifier Modifier for positioning
 */
@Composable
private fun HorizontalScrollIndicator(
    scrollFraction: Float,
    viewportFraction: Float,
    modifier: Modifier = Modifier
) {
    val density = LocalDensity.current

    // Thumb width as fraction of track
    val thumbWidthFraction = viewportFraction.coerceIn(SCROLL_INDICATOR_MIN_THUMB_FRACTION, 1f)

    BoxWithConstraints(
        modifier = modifier
            .fillMaxWidth()
            .height(HORIZONTAL_SCROLL_INDICATOR_HEIGHT)
            .padding(horizontal = HORIZONTAL_SCROLL_INDICATOR_PADDING)
    ) {
        val trackWidthPx = constraints.maxWidth.toFloat()
        val thumbWidthPx = trackWidthPx * thumbWidthFraction
        val availableTravelPx = trackWidthPx - thumbWidthPx
        val thumbOffsetPx = scrollFraction * availableTravelPx

        // Track background
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Color.Gray.copy(alpha = SCROLL_INDICATOR_TRACK_ALPHA),
                    RoundedCornerShape(2.dp)
                )
        )

        // Thumb - positioned with offset
        Box(
            modifier = Modifier
                .fillMaxHeight()
                .width(with(density) { thumbWidthPx.toDp() })
                .offset { IntOffset(x = thumbOffsetPx.toInt(), y = 0) }
                .background(
                    Color.Gray.copy(alpha = SCROLL_INDICATOR_THUMB_ALPHA),
                    RoundedCornerShape(2.dp)
                )
        )
    }
}
