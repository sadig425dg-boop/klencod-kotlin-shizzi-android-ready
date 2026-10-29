package com.shizzi.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.shizzi.app.ui.theme.ShizziPink
import com.shizzi.app.ui.theme.ShizziPinkDark
import com.shizzi.app.ui.theme.ShizziViolet
import com.shizzi.app.ui.theme.ShizziVioletDark

/** Pink/violet neon gradient pill button with a soft glow, matching the reference UI. */
@Composable
fun GlowButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    primary: Boolean = true,
    enabled: Boolean = true,
) {
    val gradient = if (primary) {
        Brush.horizontalGradient(listOf(ShizziPinkDark, ShizziPink))
    } else {
        Brush.horizontalGradient(listOf(ShizziVioletDark, ShizziViolet))
    }
    val glowColor = if (primary) ShizziPink else ShizziViolet

    TextButton(
        onClick = onClick,
        enabled = enabled,
        shape = RoundedCornerShape(28.dp),
        colors = ButtonDefaults.textButtonColors(contentColor = Color.White),
        modifier = modifier
            .fillMaxWidth()
            .height(56.dp)
            .shadow(elevation = if (enabled) 18.dp else 0.dp, shape = RoundedCornerShape(28.dp), ambientColor = glowColor, spotColor = glowColor)
            .clip(RoundedCornerShape(28.dp))
            .background(if (enabled) gradient else Brush.horizontalGradient(listOf(Color(0xFF2A2B33), Color(0xFF2A2B33))))
            .padding(horizontal = 8.dp),
    ) {
        Text(text = text, fontWeight = FontWeight.Bold, fontSize = androidx.compose.ui.unit.TextUnit.Unspecified)
    }
}
