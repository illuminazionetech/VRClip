package com.illuminazionetech.vrclip.ui.component

import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.ModalBottomSheetDefaults
import androidx.compose.material3.ModalBottomSheetProperties
import androidx.compose.material3.SheetState
import androidx.compose.material3.SheetValue
import androidx.compose.material3.Text
import androidx.compose.material3.rememberBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/**
 * A [SheetState] that starts expanded, for sheets whose visibility is driven by composition
 * instead of a show animation.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun rememberExpandedSheetState(): SheetState =
    rememberBottomSheetState(
        initialValue = SheetValue.Expanded,
        enabledValues = setOf(SheetValue.Hidden, SheetValue.Expanded),
    )

/** A hidden [SheetState], optionally with the half-expanded stop. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun rememberHiddenSheetState(skipPartiallyExpanded: Boolean = true): SheetState =
    rememberBottomSheetState(
        initialValue = SheetValue.Hidden,
        enabledValues =
            if (skipPartiallyExpanded) setOf(SheetValue.Hidden, SheetValue.Expanded)
            else setOf(SheetValue.Hidden, SheetValue.PartiallyExpanded, SheetValue.Expanded),
    )

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VRClipModalBottomSheet(
    modifier: Modifier = Modifier,
    sheetState: SheetState = rememberExpandedSheetState(),
    onDismissRequest: () -> Unit,
    contentPadding: PaddingValues = PaddingValues(horizontal = 28.dp),
    properties: ModalBottomSheetProperties = ModalBottomSheetDefaults.properties,
    containerColor: Color = MaterialTheme.colorScheme.surfaceContainerLow,
    content: @Composable ColumnScope.() -> Unit = {},
) {
    ModalBottomSheet(
        modifier = modifier,
        onDismissRequest = onDismissRequest,
        sheetState = sheetState,
        properties = properties,
        containerColor = containerColor,
        tonalElevation = 0.dp,
        // Only the top corners are rounded: the sheet is docked to the bottom edge.
        shape = BottomSheetDefaults.ExpandedShape,
    ) {
        Column(modifier = Modifier.padding(contentPadding)) {
            content()
            Spacer(modifier = Modifier.height(28.dp))
        }
    }
}

@Composable
fun DrawerSheetSubtitle(
    modifier: Modifier = Modifier,
    text: String,
    color: Color = MaterialTheme.colorScheme.primary,
) {
    Text(
        text = text,
        modifier = modifier.fillMaxWidth().padding(start = 4.dp, top = 16.dp, bottom = 8.dp),
        color = color,
        style = MaterialTheme.typography.labelLarge,
    )
}
