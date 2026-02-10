package com.xrclip.ui.component

import androidx.compose.foundation.layout.*
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.ModalBottomSheetDefaults
import androidx.compose.material3.ModalBottomSheetProperties
import androidx.compose.material3.SheetState
import androidx.compose.material3.SheetValue
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import com.xrclip.ui.common.glassEffect

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun XRClipModalBottomSheet(
    modifier: Modifier = Modifier,
    sheetState: SheetState =
        SheetState(
            skipPartiallyExpanded = true,
            density = LocalDensity.current,
            initialValue = SheetValue.Expanded,
        ),
    onDismissRequest: () -> Unit,
    contentPadding: PaddingValues = PaddingValues(horizontal = 28.dp),
    properties: ModalBottomSheetProperties = ModalBottomSheetDefaults.properties,
    content: @Composable ColumnScope.() -> Unit = {},
) {
    ModalBottomSheet(
        modifier = modifier,
        onDismissRequest = onDismissRequest,
        sheetState = sheetState,
        properties = properties,
        containerColor = Color.Transparent,
        tonalElevation = 0.dp,
        dragHandle = null
    ) {
        Column(
            modifier = Modifier
                .padding(start = 12.dp, end = 12.dp, bottom = 12.dp)
                .glassEffect(shape = MaterialTheme.shapes.extraLarge)
                .padding(contentPadding)
        ) {
            Box(Modifier.fillMaxWidth().height(32.dp), contentAlignment = Alignment.Center) {
                BottomSheetDefaults.DragHandle()
            }
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
