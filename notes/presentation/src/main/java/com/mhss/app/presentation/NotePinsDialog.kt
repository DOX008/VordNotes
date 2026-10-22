package com.mhss.app.presentation

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.res.stringResource
import com.mhss.app.ui.R
import com.mhss.app.ui.components.notes.NotePin
import com.mhss.app.ui.theme.Orange

@Composable
fun NotePinsDialog(
    selectedPins: List<String>,
    important: Boolean,
    onToggleImportant: () -> Unit,
    onTogglePin: (NotePin) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        shape = androidx.compose.foundation.shape.RoundedCornerShape(25.dp),
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.notes_pins_dialog_title)) },
        text = {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 420.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    PinRow(
                        iconPainter = painterResource(R.drawable.ic_pin_filled),
                        tint = Orange,
                        label = stringResource(R.string.notes_pin_important),
                        checked = important,
                        onClick = onToggleImportant
                    )

                    NotePin.entries.forEach { pin ->
                        PinRow(
                            iconPainter = painterResource(pin.iconRes),
                            tint = pin.color,
                            label = pin.label,
                            checked = pin.id in selectedPins,
                            onClick = { onTogglePin(pin) }
                        )
                    }

                    Spacer(Modifier.size(44.dp))
                }

                // Synlig hint som ligger utanför scroll-innehållet
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .background(
                            brush = Brush.verticalGradient(
                                colors = listOf(
                                    Color.Transparent,
                                    MaterialTheme.colorScheme.surface.copy(alpha = 0.85f)
                                )
                            )
                        )
                        .padding(top = 24.dp, bottom = 8.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            painter = painterResource(android.R.drawable.arrow_down_float),
                            contentDescription = stringResource(R.string.notes_more_options),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier
                                .size(18.dp)
                                .alpha(0.9f)
                        )
                        Text(
                            text = stringResource(R.string.notes_scroll_for_more),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 2.dp)
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.complete)) }
        }
    )
}

@Composable
private fun PinRow(
    iconPainter: androidx.compose.ui.graphics.painter.Painter,
    tint: Color,
    label: String,
    checked: Boolean,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 6.dp, horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            painter = iconPainter,
            contentDescription = label,
            tint = tint,
            modifier = Modifier.size(26.dp)
        )
        Spacer(Modifier.width(12.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.weight(1f)
        )
        Checkbox(checked = checked, onCheckedChange = { onClick() })
    }
}