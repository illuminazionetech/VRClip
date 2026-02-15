package com.xrclip.ui.component

import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Cancel
import androidx.compose.material.icons.rounded.ContentPaste
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.xrclip.R
import com.xrclip.ui.common.HapticFeedback.slightHapticFeedback

@Composable
fun PasteFromClipBoardButton(onPaste: (String) -> Unit = {}) {
    val clipboardManager = LocalClipboardManager.current
    PasteButton(onClick = { clipboardManager.getText()?.let { onPaste(it.toString()) } })
}

@Composable
fun PasteButton(onClick: () -> Unit = {}) {
    IconButton(onClick = onClick) {
        Icon(Icons.Rounded.ContentPaste, stringResource(R.string.paste))
    }
}

@Composable
fun AddButton(onClick: () -> Unit, enabled: Boolean = true) {
    IconButton(onClick = onClick, enabled = enabled) {
        Icon(imageVector = Icons.Rounded.Add, contentDescription = stringResource(R.string.add))
    }
}

@Composable
fun ClearButton(onClick: () -> Unit) {
    IconButton(onClick = onClick) {
        Icon(
            modifier = Modifier.size(24.dp),
            imageVector = Icons.Rounded.Cancel,
            contentDescription = stringResource(id = R.string.clear),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
fun BackButton(onClick: () -> Unit) {
    val view = LocalView.current
    IconButton(
        modifier = Modifier,
        onClick = {
            onClick()
            view.slightHapticFeedback()
        },
    ) {
        Icon(
            imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
            contentDescription = stringResource(R.string.back),
        )
    }
}
