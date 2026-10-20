package com.mhss.app.mybrain.presentation.main.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CornerBasedShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mhss.app.ui.theme.MyBrainTheme
import kotlinx.coroutines.delay

@Composable
fun SpaceCardGrid(
    title: String,
    subtitle: String = "",
    image: Int,
    backgroundColor: Color,
    modifier: Modifier = Modifier,
    isPulsing: Boolean = false,
    cardShape: CornerBasedShape = RoundedCornerShape(28.dp),
    imageAlignment: Alignment = Alignment.TopStart,
    iconSize: Dp = 70.dp,
    onClick: () -> Unit = {}
) {
    var heartbeatPhase by remember { mutableStateOf(false) }

    LaunchedEffect(isPulsing) {
        if (isPulsing) {
            while (true) {
                heartbeatPhase = true
                delay(120)
                heartbeatPhase = false
                delay(120)
                heartbeatPhase = true
                delay(120)
                heartbeatPhase = false
                delay(2500)
            }
        }
    }

    val scale by animateFloatAsState(
        targetValue = if (heartbeatPhase) 1.03f else 1f,
        animationSpec = tween(120),
        label = "cardScale"
    )

    val interactionSource = remember { MutableInteractionSource() }

    // Hämtar färger dynamiskt från temat för att ge perfekt kontrast i både ljust och mörkt läge
    val contentColor = MaterialTheme.colorScheme.onSurface
    val borderColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.15f)

    Box(
        modifier = modifier
            .scale(scale)
            .clip(cardShape)
            .background(
                Brush.linearGradient(
                    listOf(
                        backgroundColor.copy(alpha = 0.45f),
                        backgroundColor.copy(alpha = 0.25f)
                    )
                )
            )
            .border(
                width = 1.dp,
                color = borderColor,
                shape = cardShape
            )
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick
            )
            .padding(14.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Box(
                modifier = Modifier.fillMaxWidth(),
                contentAlignment = imageAlignment
            ) {
                Image(
                    painter = painterResource(id = image),
                    contentDescription = title,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.size(iconSize)
                )
            }

            Column {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontSize = 18.sp,
                        color = contentColor
                    ),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                if (subtitle.isNotEmpty()) {
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodySmall.copy(
                            color = contentColor.copy(alpha = 0.75f)
                        ),
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

@Composable
fun SpaceCardWide(
    title: String,
    subtitle: String = "",
    image: Int,
    backgroundColor: Color,
    modifier: Modifier = Modifier,
    cardShape: CornerBasedShape = RoundedCornerShape(32.dp),
    iconSize: Dp = 80.dp,
    onClick: () -> Unit = {}
) {
    val interactionSource = remember { MutableInteractionSource() }

    // Hämtar färger dynamiskt från temat för att ge perfekt kontrast i både ljust och mörkt läge
    val contentColor = MaterialTheme.colorScheme.onSurface
    val borderColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.15f)

    Box(
        modifier = modifier
            .clip(cardShape)
            .background(
                Brush.linearGradient(
                    listOf(
                        backgroundColor.copy(alpha = 0.45f),
                        backgroundColor.copy(alpha = 0.25f)
                    )
                )
            )
            .border(
                width = 1.dp,
                color = borderColor,
                shape = cardShape
            )
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick
            )
            .padding(18.dp)
    ) {
        Image(
            painter = painterResource(id = image),
            contentDescription = title,
            contentScale = ContentScale.Fit,
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .size(iconSize)
        )

        Column(
            modifier = Modifier
                .align(Alignment.CenterStart)
                .padding(end = iconSize + 24.dp),
            horizontalAlignment = Alignment.Start,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium.copy(
                    fontSize = 18.sp,
                    color = contentColor
                ),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            if (subtitle.isNotEmpty()) {
                Spacer(Modifier.height(2.dp))
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall.copy(
                        color = contentColor.copy(alpha = 0.75f)
                    ),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
fun SpaceCardGridPreview() {
    MyBrainTheme {
        SpaceCardGrid(
            title = "Health Care",
            subtitle = "Write notes / Care units",
            image = com.mhss.app.ui.R.drawable.notes_img,
            backgroundColor = Color(0xFF3F51B5)
        )
    }
}

@Preview(showBackground = true, widthDp = 360)
@Composable
fun SpaceCardWidePreview() {
    MyBrainTheme {
        SpaceCardWide(
            title = "Bookmarks",
            subtitle = "Medical information",
            image = com.mhss.app.ui.R.drawable.bookmarks_img,
            backgroundColor = Color(0xFF3F51B5),
            modifier = Modifier
                .fillMaxWidth()
                .height(120.dp)
        )
    }
}
