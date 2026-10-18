package com.mhss.app.presentation

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyItemScope
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.mhss.app.domain.model.DiaryEntry
import com.mikepenz.markdown.m3.Markdown
import com.mikepenz.markdown.m3.markdownColor
import com.mikepenz.markdown.m3.markdownTypography
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable
fun LazyItemScope.DiaryEntryItem(
    modifier: Modifier = Modifier,
    entry: DiaryEntry,
    timeText: String = run {
        val formatter = DateTimeFormatter.ofPattern("HH:mm", Locale.getDefault())
        Instant.ofEpochMilli(entry.createdDate)
            .atZone(ZoneId.systemDefault())
            .toLocalTime()
            .format(formatter)
    },
    isPlaying: Boolean,
    onPlayPauseClick: () -> Unit,
    onClick: (DiaryEntry) -> Unit
) {
    Box(
        modifier = modifier
            .padding(horizontal = 12.dp)
            .animateItem()
            .clip(RoundedCornerShape(20.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f))
    ) {
        Column(
            modifier = Modifier
                .clickable { onClick(entry) }
                .padding(12.dp)
                .fillMaxWidth()
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    painter = painterResource(entry.mood.iconRes),
                    contentDescription = stringResource(entry.mood.titleRes),
                    tint = entry.mood.color,
                    modifier = Modifier.size(30.dp)
                )

                Spacer(Modifier.width(8.dp))

                Text(
                    text = entry.title,
                    style = MaterialTheme.typography.bodyLarge.copy(
                        fontWeight = FontWeight.Bold
                    ),
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
            }

            Spacer(Modifier.height(12.dp))

            if (entry.content.isNotBlank()) {
                Markdown(
                    content = entry.content,
                    colors = markdownColor(linkText = Color.Blue),
                    typography = markdownTypography(
                        text = MaterialTheme.typography.bodyMedium,
                        h1 = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
                        h2 = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                        h3 = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                        h4 = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        h5 = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                        h6 = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Bold),
                        code = MaterialTheme.typography.bodySmall,
                        paragraph = MaterialTheme.typography.bodyMedium,
                    )
                )

                Spacer(Modifier.height(8.dp))
            }

            Spacer(Modifier.height(8.dp))

            Text(
                text = timeText,
                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.align(Alignment.End)
            )
        }
    }
}