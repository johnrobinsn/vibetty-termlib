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

import android.content.Context
import android.util.Log
import android.view.KeyEvent
import android.view.View
import android.view.inputmethod.BaseInputConnection
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputConnection
import android.view.inputmethod.InputMethodManager

/**
 * A minimal invisible View that provides proper IME input handling for terminal emulation.
 *
 * This view creates a custom InputConnection that:
 * - Handles backspace via deleteSurroundingText by sending KEYCODE_DEL
 * - Handles enter/return keys properly via sendKeyEvent
 * - Configures the keyboard as password-type to show number rows
 * - Disables text suggestions and autocorrect
 * - Manages IME visibility using InputMethodManager for reliable show/hide
 *
 * Based on the ConnectBot v1.9.13 TerminalView implementation.
 */
internal class ImeInputView(
    context: Context,
    private val keyboardHandler: KeyboardHandler,
    /**
     * When true, enables special handling for voice keyboards:
     * - Tracks composing text and notifies via onComposingTextChanged
     * - Maintains a fake text buffer so IMEs can query/delete text
     * - Applies workaround for Google Voice Keyboard's unusual backspace behavior
     *
     * When false, uses simple passthrough behavior (original pre-feature behavior).
     */
    private val enableVoiceInputSupport: Boolean = false,
    private val onComposingTextChanged: (String?) -> Unit = {},
    private val onTextCommitted: (String) -> Unit = { text ->
        // Default: send directly to terminal (original behavior)
        keyboardHandler.onTextInput(text.toByteArray(Charsets.UTF_8))
    }
) : View(context) {

    private val inputMethodManager = context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager

    init {
        isFocusable = true
        isFocusableInTouchMode = true
    }

    /**
     * Show the IME forcefully. This is more reliable than SoftwareKeyboardController.
     */
    @Suppress("DEPRECATION")
    fun showIme() {
        if (requestFocus()) {
            inputMethodManager.showSoftInput(this, InputMethodManager.SHOW_FORCED)
        }
    }

    /**
     * Hide the IME.
     */
    fun hideIme() {
        inputMethodManager.hideSoftInputFromWindow(windowToken, 0)
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        // Always hide IME when view is detached to prevent SHOW_FORCED from keeping keyboard
        // open after the app/activity is destroyed
        hideIme()
    }

    override fun onCreateInputConnection(outAttrs: EditorInfo): InputConnection {
        // Configure IME options
        outAttrs.imeOptions = outAttrs.imeOptions or
                EditorInfo.IME_FLAG_NO_EXTRACT_UI or
                EditorInfo.IME_FLAG_NO_ENTER_ACTION or
                EditorInfo.IME_ACTION_NONE

        // Configure keyboard type:
        // - TYPE_TEXT_VARIATION_PASSWORD: Shows password-style keyboard with number rows
        // - TYPE_TEXT_VARIATION_VISIBLE_PASSWORD: Keeps text visible (we handle display ourselves)
        // - TYPE_TEXT_FLAG_NO_SUGGESTIONS: Disables autocomplete/suggestions
        // - TYPE_NULL: No special input processing
        outAttrs.inputType = EditorInfo.TYPE_NULL or
                EditorInfo.TYPE_TEXT_VARIATION_PASSWORD or
                EditorInfo.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD or
                EditorInfo.TYPE_TEXT_FLAG_NO_SUGGESTIONS

        return TerminalInputConnection(this, false)
    }

    override fun onCheckIsTextEditor(): Boolean = true

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        Log.d("ImeInputView", "dispatchKeyEvent: code=${event.keyCode} action=${event.action}")
        return super.dispatchKeyEvent(event)
    }

    /**
     * Custom InputConnection that handles backspace and other special keys for terminal input.
     *
     * When enableVoiceInputSupport is true:
     * - Maintains a fake text buffer so IMEs (especially voice keyboards) know there's text
     *   that can be deleted
     * - Tracks composing text for voice input overlay
     * - Applies workaround for Google Voice Keyboard's unusual backspace behavior
     *
     * When enableVoiceInputSupport is false:
     * - Uses simple passthrough behavior (original pre-feature behavior)
     * - No fake buffer, no composing text tracking
     */
    private inner class TerminalInputConnection(
        targetView: View,
        fullEditor: Boolean
    ) : BaseInputConnection(targetView, fullEditor) {

        // Track current composing text for finishComposingText (only used when enableVoiceInputSupport)
        private var currentComposingText: String? = null

        // Fake text buffer so IME knows there's text to delete (only used when enableVoiceInputSupport)
        // We track committed text here so deleteSurroundingText works properly.
        // Limited to last 1000 chars to prevent unbounded growth.
        private val fakeBuffer = StringBuilder()
        private val maxBufferSize = 1000

        // Timestamp of the last text commit to avoid spurious backspace from voice keyboard
        private var lastCommitTime = 0L

        override fun getTextBeforeCursor(n: Int, flags: Int): CharSequence {
            if (!enableVoiceInputSupport) {
                return super.getTextBeforeCursor(n, flags) ?: ""
            }
            val len = minOf(n, fakeBuffer.length)
            val result = if (len > 0) fakeBuffer.substring(fakeBuffer.length - len) else ""
            Log.d("ImeInputView", "getTextBeforeCursor($n): returning '${result.takeLast(20)}' (len=${result.length})")
            return result
        }

        override fun getTextAfterCursor(n: Int, flags: Int): CharSequence {
            if (!enableVoiceInputSupport) {
                return super.getTextAfterCursor(n, flags) ?: ""
            }
            // Cursor is always at the end in a terminal
            return ""
        }

        override fun deleteSurroundingText(leftLength: Int, rightLength: Int): Boolean {
            Log.d("ImeInputView", "deleteSurroundingText: left=$leftLength, right=$rightLength")

            // Handle backspace by sending DEL key events
            val deleteCount = if (leftLength == 0 && rightLength == 0) 1 else leftLength

            for (i in 0 until deleteCount) {
                sendKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DEL))
                sendKeyEvent(KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_DEL))
                // Shrink fake buffer (only when voice input support enabled)
                if (enableVoiceInputSupport && fakeBuffer.isNotEmpty()) {
                    fakeBuffer.deleteCharAt(fakeBuffer.length - 1)
                }
            }

            return true
        }

        override fun sendKeyEvent(event: KeyEvent): Boolean {
            Log.d("ImeInputView", "sendKeyEvent: ${event.keyCode} action=${event.action}")
            // Let the view's key listener handle the event
            return this@ImeInputView.dispatchKeyEvent(event)
        }

        override fun setComposingText(text: CharSequence?, newCursorPosition: Int): Boolean {
            Log.d("ImeInputView", "setComposingText: '$text'")

            if (!enableVoiceInputSupport) {
                // Original behavior: delegate to BaseInputConnection (does nothing for terminal)
                return super.setComposingText(text, newCursorPosition)
            }

            // Voice input support: track composing text for overlay display
            currentComposingText = text?.toString()
            onComposingTextChanged(currentComposingText)
            return true
        }

        override fun finishComposingText(): Boolean {
            Log.d("ImeInputView", "finishComposingText: composing='$currentComposingText'")

            if (!enableVoiceInputSupport) {
                // Original behavior: delegate to BaseInputConnection
                return super.finishComposingText()
            }

            // Voice input support: commit any pending composing text
            val textToCommit = currentComposingText
            currentComposingText = null
            onComposingTextChanged(null)

            if (!textToCommit.isNullOrEmpty()) {
                Log.d("ImeInputView", "finishComposingText: committing '$textToCommit'")
                addToFakeBuffer(textToCommit)
                onTextCommitted(textToCommit)
                // Mark that we just committed text so we don't treat the next
                // finishComposingText as a backspace (voice keyboard sends extra calls)
                lastCommitTime = System.currentTimeMillis()
            } else if (fakeBuffer.isNotEmpty() && System.currentTimeMillis() - lastCommitTime > 500) {
                // HACK: Google Voice Keyboard calls finishComposingText repeatedly for backspace
                // instead of deleteSurroundingText. When composing is null but we have buffer,
                // treat this as a backspace request.
                // Guard: skip if we just committed text (< 500ms ago) since voice keyboard
                // fires extra finishComposingText calls after committing.
                Log.d("ImeInputView", "finishComposingText: treating as backspace (voice keyboard hack)")
                sendKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DEL))
                sendKeyEvent(KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_DEL))
                if (fakeBuffer.isNotEmpty()) {
                    fakeBuffer.deleteCharAt(fakeBuffer.length - 1)
                }
            }
            return true
        }

        override fun commitText(text: CharSequence?, newCursorPosition: Int): Boolean {
            Log.d("ImeInputView", "commitText: '$text', composing='$currentComposingText'")

            if (!enableVoiceInputSupport) {
                // Original behavior: send text directly to terminal
                if (!text.isNullOrEmpty()) {
                    keyboardHandler.onTextInput(text.toString().toByteArray(Charsets.UTF_8))
                }
                return true
            }

            // Voice input support: clear composing state and use callback
            currentComposingText = null
            onComposingTextChanged(null)

            if (text.isNullOrEmpty()) {
                return true
            }

            // Add to fake buffer so IME knows there's text to delete
            addToFakeBuffer(text.toString())

            // Send committed text via callback
            onTextCommitted(text.toString())
            return true
        }

        private fun addToFakeBuffer(text: String) {
            if (!enableVoiceInputSupport) return
            fakeBuffer.append(text)
            // Trim if too long
            if (fakeBuffer.length > maxBufferSize) {
                fakeBuffer.delete(0, fakeBuffer.length - maxBufferSize)
            }
        }
    }
}
