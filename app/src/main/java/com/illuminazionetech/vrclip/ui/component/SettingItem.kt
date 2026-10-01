package com.illuminazionetech.vrclip.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

@Composable
fun SettingTitle(text: String) {
    Text(
        modifier = Modifier.padding(top = 24.dp).padding(horizontal = 20.dp, vertical = 8.dp),
        text = text,
        style = MaterialTheme.typography.titleLarge,
        color = MaterialTheme.colorScheme.primary,
    )
}

/** Where an item sits inside a visually grouped list, which decides its corner radii. */
enum class GroupPosition {
    Single,
    First,
    Middle,
    Last,
}

private val GroupOuterCorner = 24.dp
private val GroupInnerCorner = 6.dp

fun GroupPosition.shape(): Shape =
    when (this) {
        GroupPosition.Single -> RoundedCornerShape(GroupOuterCorner)
        GroupPosition.First ->
            RoundedCornerShape(
                topStart = GroupOuterCorner,
                topEnd = GroupOuterCorner,
                bottomStart = GroupInnerCorner,
                bottomEnd = GroupInnerCorner,
            )
        GroupPosition.Middle -> RoundedCornerShape(GroupInnerCorner)
        GroupPosition.Last ->
            RoundedCornerShape(
                topStart = GroupInnerCorner,
                topEnd = GroupInnerCorner,
                bottomStart = GroupOuterCorner,
                bottomEnd = GroupOuterCorner,
            )
    }

/** Position of item [index] in a group of [count] items. */
fun groupPosition(index: Int, count: Int): GroupPosition =
    when {
        count <= 1 -> GroupPosition.Single
        index == 0 -> GroupPosition.First
        index == count - 1 -> GroupPosition.Last
        else -> GroupPosition.Middle
    }

/**
 * A top-level settings entry. Entries are grouped into sections: consecutive items share one
 * rounded block (large outer corners, small inner ones, 2dp apart), as in Material 3 Expressive
 * settings lists. The icon sits in a tonal circle.
 */
@Composable
fun SettingItem(
    title: String,
    description: String,
    icon: ImageVector?,
    position: GroupPosition = GroupPosition.Single,
    onClick: () -> Unit,
) {
    Surface(
        modifier = Modifier.padding(horizontal = 16.dp).padding(bottom = 2.dp),
        onClick = onClick,
        shape = position.shape(),
        color = MaterialTheme.colorScheme.surfaceContainer,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            icon?.let {
                Box(
                    modifier =
                        Modifier.size(40.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primaryContainer),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        modifier = Modifier.size(22.dp),
                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                    )
                }
                Spacer(Modifier.width(16.dp))
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    maxLines = 1,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    overflow = TextOverflow.Ellipsis,
                )
                if (description.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = description,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        style = MaterialTheme.typography.bodyMedium,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

/** Header above a group of settings. */
@Composable
fun SettingsGroupTitle(text: String) {
    Text(
        modifier = Modifier.padding(start = 32.dp, end = 32.dp, top = 20.dp, bottom = 8.dp),
        text = text,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
    )
}
