package com.xrclip.player

import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.xrclip.R

/** Picker for [ProjectionMode], with a "Auto-detect" entry (`null`) at the top. Shared by the
 * player screen's toolbar and the downloads list's per-video context menu. */
@Composable
fun ProjectionMenu(current: ProjectionMode?, onDismiss: () -> Unit, onSelect: (ProjectionMode?) -> Unit) {
    DropdownMenu(expanded = true, onDismissRequest = onDismiss) {
        DropdownMenuItem(
            text = { Text(stringResource(R.string.player_projection_auto)) },
            leadingIcon = { RadioButton(selected = current == null, onClick = null) },
            onClick = { onSelect(null) },
        )
        ProjectionMode.entries.forEach { mode ->
            DropdownMenuItem(
                text = { Text(mode.displayName()) },
                leadingIcon = { RadioButton(selected = current == mode, onClick = null) },
                onClick = { onSelect(mode) },
            )
        }
    }
}
