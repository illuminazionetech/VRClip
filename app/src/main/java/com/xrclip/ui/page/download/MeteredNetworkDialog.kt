package com.xrclip.ui.page.download

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.SignalCellularConnectedNoInternet4Bar
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import com.xrclip.R
import com.xrclip.ui.component.BottomButtonShape
import com.xrclip.ui.component.MiddleButtonShape
import com.xrclip.ui.component.XRClipDialogButtonVariant
import com.xrclip.ui.component.XRClipDialogVariant
import com.xrclip.ui.component.TopButtonShape

@Composable
@Preview
fun MeteredNetworkDialog(
    onDismissRequest: () -> Unit = {},
    onAllowOnceConfirm: () -> Unit = {},
    onAllowAlwaysConfirm: () -> Unit = {},
) {
    XRClipDialogVariant(
        onDismissRequest = onDismissRequest,
        icon = {
            Icon(
                imageVector = Icons.Rounded.SignalCellularConnectedNoInternet4Bar,
                contentDescription = null,
            )
        },
        //        text = {
        //            Text(
        //                text = stringResource(id = R.string.download_disabled_with_cellular),
        //                modifier = Modifier.padding(horizontal = 24.dp)
        //            )
        //        },
        title = { Text(text = stringResource(id = R.string.download_with_cellular_request)) },
        buttons = {
            XRClipDialogButtonVariant(
                text = stringResource(id = R.string.allow_always),
                shape = TopButtonShape,
            ) {
                onAllowAlwaysConfirm()
            }
            XRClipDialogButtonVariant(
                text = stringResource(id = R.string.allow_once),
                shape = MiddleButtonShape,
            ) {
                onAllowOnceConfirm()
            }
            XRClipDialogButtonVariant(
                text = stringResource(id = R.string.dont_allow),
                shape = BottomButtonShape,
            ) {
                onDismissRequest()
            }
        },
    )
}
