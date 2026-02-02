package com.xrclip.ui.page.settings.interaction

import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import com.xrclip.R
import com.xrclip.ui.component.DialogSingleChoiceItem
import com.xrclip.ui.component.XRClipDialog
import com.xrclip.util.NONE
import com.xrclip.util.USE_PREVIOUS_SELECTION

@Composable
fun DownloadTypeCustomizationDialog(
    modifier: Modifier = Modifier,
    onDismissRequest: () -> Unit,
    selectedItem: Int,
    onSelect: (Int) -> Unit,
) {
    XRClipDialog(
        modifier = modifier,
        onDismissRequest = onDismissRequest,
        confirmButton = null,
        title = { Text(text = stringResource(id = R.string.download_type)) },
        text = {
            LazyColumn(modifier = Modifier.padding()) {
                item {
                    DialogSingleChoiceItem(
                        text = stringResource(id = R.string.use_previous_selection),
                        selected = selectedItem == USE_PREVIOUS_SELECTION,
                    ) {
                        onSelect(USE_PREVIOUS_SELECTION)
                    }
                }

                item {
                    DialogSingleChoiceItem(
                        text = stringResource(id = R.string.none),
                        selected = selectedItem == NONE,
                    ) {
                        onSelect(NONE)
                    }
                }
            }
        },
    )
}

@Preview
@Composable
private fun Preview() {
    DownloadTypeCustomizationDialog(onDismissRequest = {}, selectedItem = NONE) {}
}
