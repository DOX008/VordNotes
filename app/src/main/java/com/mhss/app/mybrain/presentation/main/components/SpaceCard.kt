package com.mhss.app.mybrain.presentation.main.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.mhss.app.ui.R
import com.mhss.app.ui.theme.MyBrainTheme
import com.mhss.app.ui.theme.Blue
import kotlinx.coroutines.delay

@Composable
fun SpaceCard(
    title: String,
    subtitleRes: Int,
    image: Int,
    backgroundColor: Color, // Återinförd för att SpacesScreen.kt ska kunna kompilera utan fel
    modifier: Modifier = Modifier,
    isPulsing: Boolean = false,
    onClick: () -> Unit = {}
) {
    val shape = RoundedCornerShape(15.dp)
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
        targetValue = if (heartbeatPhase) 1.08f else 1f,
        animationSpec = tween(durationMillis = 120),
        label = "heartbeat"
    )

    val interactionSource = remember { MutableInteractionSource() }

    // Textfärg och ram hämtas dynamikst från Material 3-temat för perfekt kontrast
    val contentColor = MaterialTheme.colorScheme.onSurface
    val borderColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.15f)

    Box(
        modifier = modifier
            .scale(scale)
            .clip(shape)
            .background(
                Brush.linearGradient(
                    listOf(
                        backgroundColor.copy(alpha = 0.45f), // Använder den medskickade färgen med exakt samma alfa som Grid-korten
                        backgroundColor.copy(alpha = 0.25f)
                    )
                )
            )
            .border(
                width = 1.dp,
                color = borderColor,
                shape = shape
            )
            .clickable(
                interactionSource = interactionSource,
                indication = null, // Matchar SpaceCardGrid (ingen M3 standard ripple)
                onClick = onClick
            )
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Mörk tonad cirkelbakgrund bakom ikonerna
            Box(
                modifier = Modifier
                    .size(90.dp)
                    .clip(CircleShape)
                    .background(Color.Black.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center
            ) {
                Image(
                    painter = painterResource(image),
                    contentDescription = null,
                    modifier = Modifier.size(80.dp)
                )
            }

            Spacer(modifier = Modifier.width(30.dp))

            Column(
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    color = contentColor
                )

                Spacer(modifier = Modifier.height(4.dp))

                Text(
                    text = stringResource(subtitleRes),
                    style = MaterialTheme.typography.bodySmall,
                    color = contentColor.copy(alpha = 0.65f),
                    maxLines = 2
                )
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
fun SpaceCardPreview() {
    MyBrainTheme {
        SpaceCard(
            title = "Notes",
            subtitleRes = R.string.notes_subtitle,
            image = R.drawable.notes_img,
            backgroundColor = Blue
        )
    }
}
