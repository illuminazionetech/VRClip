package com.illuminazionetech.vrclip.ui.common

import android.content.ClipData
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.Clipboard
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.text.AnnotatedString
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * Plain-text convenience over Compose's suspending [Clipboard], so click handlers can copy text
 * without managing a coroutine themselves.
 */
class TextClipboard(private val clipboard: Clipboard, private val scope: CoroutineScope) {
    fun setText(text: String) {
        scope.launch { clipboard.setClipEntry(ClipEntry(ClipData.newPlainText(null, text))) }
    }

    fun setText(text: AnnotatedString) = setText(text.text)

    suspend fun getText(): String? =
        clipboard
            .getClipEntry()
            ?.clipData
            ?.takeIf { it.itemCount > 0 }
            ?.getItemAt(0)
            ?.text
            ?.toString()

    fun readText(onResult: (String?) -> Unit) {
        scope.launch { onResult(getText()) }
    }
}

@Composable
fun rememberTextClipboard(): TextClipboard {
    val clipboard = LocalClipboard.current
    val scope = rememberCoroutineScope()
    return remember(clipboard, scope) { TextClipboard(clipboard, scope) }
}
