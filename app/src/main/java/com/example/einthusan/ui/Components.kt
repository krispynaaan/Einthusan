package com.example.einthusan.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Backspace
import androidx.compose.material.icons.filled.SpaceBar
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged // FIX: Added this import
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.*

@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
fun VirtualKeyboard(
    onKeyPress: (Char) -> Unit,
    onBackspace: () -> Unit,
    onSpace: () -> Unit,
    onClear: () -> Unit
) {
    val keys = listOf(
        "A", "B", "C", "D", "E", "F",
        "G", "H", "I", "J", "K", "L",
        "M", "N", "O", "P", "Q", "R",
        "S", "T", "U", "V", "W", "X",
        "Y", "Z", "1", "2", "3", "4",
        "5", "6", "7", "8", "9", "0"
    )

    Column(
        modifier = Modifier.width(300.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // Keys Grid
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            keys.forEach { key ->
                KeyboardKey(
                    label = key,
                    onClick = { onKeyPress(key.first()) }
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Action Keys Row
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            // Space Key
            ActionKey(
                icon = Icons.Default.SpaceBar,
                label = "Space",
                onClick = onSpace,
                modifier = Modifier.weight(1f)
            )
            // Backspace Key
            ActionKey(
                icon = Icons.AutoMirrored.Filled.Backspace,
                label = "Back",
                onClick = onBackspace,
                modifier = Modifier.weight(1f)
            )
        }
        
        Spacer(modifier = Modifier.height(8.dp))
        
        // Clear Button
        Button(
            onClick = onClear,
            colors = ButtonDefaults.colors(
                containerColor = Color.DarkGray.copy(alpha = 0.5f),
                contentColor = Color.White
            ),
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Clear Search")
        }
    }
}

@Composable
fun KeyboardKey(
    label: String,
    onClick: () -> Unit
) {
    var isFocused by remember { mutableStateOf(false) }

    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(40.dp)
            .clip(CircleShape)
            .background(
                if (isFocused) Color.White else Color.DarkGray.copy(alpha = 0.4f)
            )
            .onFocusChanged { isFocused = it.isFocused }
            .clickable(onClick = onClick)
            .focusable()
    ) {
        Text(
            text = label,
            color = if (isFocused) Color.Black else Color.White,
            fontWeight = FontWeight.Bold,
            fontSize = 14.sp
        )
    }
}

@Composable
fun ActionKey(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    var isFocused by remember { mutableStateOf(false) }

    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .height(40.dp)
            .clip(MaterialTheme.shapes.small)
            .background(
                if (isFocused) Color.White else Color.DarkGray.copy(alpha = 0.4f)
            )
            .onFocusChanged { isFocused = it.isFocused }
            .clickable(onClick = onClick)
            .focusable()
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = if (isFocused) Color.Black else Color.White,
            modifier = Modifier.size(20.dp)
        )
    }
}