package com.mhss.app.presentation

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.mhss.app.domain.model.Bookmark

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun BookmarkItem(
    modifier: Modifier = Modifier,
    bookmark: Bookmark,
    onClick: (Bookmark) -> Unit,
    onInvalidUrl: () -> Unit
) {
    val uriHandler = LocalUriHandler.current

    val openBookmark = {
        if (bookmark.url.isValidUrl()) {
            uriHandler.openUri(
                if (!bookmark.url.startsWith("https://") && !bookmark.url.startsWith("http://"))
                    "http://${bookmark.url}"
                else
                    bookmark.url
            )
        } else {
            onInvalidUrl()
        }
    }

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(20.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f))
    ) {
        Column(
            modifier = Modifier
                .combinedClickable(
                    onClick = { openBookmark() },
                    onLongClick = { onClick(bookmark) }
                )
                .padding(12.dp)
                .fillMaxWidth()
        ) {
            if (bookmark.title.isNotBlank()) {
                Text(
                    text = bookmark.title,
                    style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.height(6.dp))
            }

            Text(
                text = bookmark.url,
                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            if (bookmark.description.isNotBlank()) {
                Spacer(Modifier.height(6.dp))
                Text(
                    text = bookmark.description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Spacer(Modifier.height(8.dp))

            IconButton(
                onClick = { onClick(bookmark) },
                modifier = Modifier.align(Alignment.End)
            ) {
                Icon(
                    imageVector = Icons.Default.Edit,
                    contentDescription = "Edit",
                    tint = MaterialTheme.colorScheme.onSurface
                )
            }
        }
    }
}