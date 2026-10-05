package com.edrive.app.ui.components

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.edrive.app.ui.theme.EColors

/** eDrive loqosu — seyf qapısı motivi (web versiyası ilə eyni). */
@Composable
fun VaultMark(size: Dp, animated: Boolean = false, modifier: Modifier = Modifier) {
    val rotation = if (animated) {
        val t = rememberInfiniteTransition(label = "dial")
        t.animateFloat(0f, 360f, infiniteRepeatable(tween(6000), RepeatMode.Restart), label = "rot").value
    } else 0f
    Canvas(modifier.size(size)) {
        val s = this.size.minDimension
        val stroke = s * 0.06f
        val inset = s * 0.09f + stroke / 2
        drawRoundRect(
            color = EColors.Surface, topLeft = Offset(inset, inset),
            size = Size(s - inset * 2, s - inset * 2), cornerRadius = CornerRadius(s * 0.22f),
        )
        drawRoundRect(
            color = EColors.Accent, topLeft = Offset(inset, inset), size = Size(s - inset * 2, s - inset * 2),
            cornerRadius = CornerRadius(s * 0.22f), style = Stroke(stroke),
        )
        rotate(rotation) {
            drawArc(
                color = EColors.Accent, startAngle = 0f, sweepAngle = 300f, useCenter = false,
                topLeft = Offset(s * 0.3f, s * 0.3f), size = Size(s * 0.4f, s * 0.4f), style = Stroke(stroke),
            )
        }
        drawCircle(EColors.Accent, radius = s * 0.065f)
    }
}

@Composable
fun Avatar(name: String, size: Dp = 32.dp) {
    Box(
        Modifier.size(size).clip(CircleShape)
            .background(androidx.compose.ui.graphics.Brush.linearGradient(listOf(Color(0xFF2BB47A), Color(0xFF1D7A8C)))),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            name.take(1).uppercase(), color = EColors.AccentInk,
            fontWeight = FontWeight.Bold, fontSize = (size.value * 0.44f).sp,
        )
    }
}

@Composable
fun StatusDot(color: Color, modifier: Modifier = Modifier) {
    Box(modifier.size(8.dp).clip(CircleShape).background(color))
}

@Composable
fun EField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    leading: ImageVector,
    modifier: Modifier = Modifier,
    password: Boolean = false,
    imeAction: ImeAction = ImeAction.Next,
    supporting: String? = null,
    isError: Boolean = false,
    enabled: Boolean = true,
) {
    var visible by remember { mutableStateOf(false) }
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        leadingIcon = { Icon(leading, null, tint = EColors.Faint) },
        trailingIcon = if (password) {
            {
                IconButton(onClick = { visible = !visible }) {
                    Icon(if (visible) Icons.Outlined.VisibilityOff else Icons.Outlined.Visibility, "Parolu göstər", tint = EColors.Faint)
                }
            }
        } else null,
        visualTransformation = if (password && !visible) PasswordVisualTransformation() else VisualTransformation.None,
        keyboardOptions = KeyboardOptions(
            keyboardType = if (password) KeyboardType.Password else KeyboardType.Text,
            imeAction = imeAction, autoCorrectEnabled = false,
        ),
        singleLine = true,
        isError = isError,
        enabled = enabled,
        supportingText = supporting?.let { { Text(it) } },
        textStyle = if (password && !visible) androidx.compose.ui.text.TextStyle(fontFamily = FontFamily.Monospace, letterSpacing = 2.sp)
        else androidx.compose.ui.text.TextStyle.Default,
        shape = RoundedCornerShape(12.dp),
        colors = OutlinedTextFieldDefaults.colors(
            unfocusedContainerColor = EColors.Bg2, focusedContainerColor = EColors.Bg2,
            unfocusedBorderColor = EColors.Line2, focusedBorderColor = EColors.Accent,
            focusedLabelColor = EColors.Accent, unfocusedLabelColor = EColors.Muted,
            cursorColor = EColors.Accent,
        ),
        modifier = modifier.fillMaxWidth(),
    )
}

@Composable
fun PrimaryButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, loading: Boolean = false, enabled: Boolean = true, icon: ImageVector? = null) {
    Button(
        onClick = onClick,
        enabled = enabled && !loading,
        shape = RoundedCornerShape(12.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = EColors.Accent, contentColor = EColors.AccentInk,
            disabledContainerColor = EColors.Surface3, disabledContentColor = EColors.Faint,
        ),
        modifier = modifier.fillMaxWidth().height(52.dp),
    ) {
        if (loading) {
            CircularProgressIndicator(Modifier.size(20.dp), color = EColors.AccentInk, strokeWidth = 2.dp)
        } else {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                if (icon != null) Icon(icon, null, Modifier.size(20.dp))
                Text(text, style = androidx.compose.material3.MaterialTheme.typography.labelLarge, fontSize = 15.sp)
            }
        }
    }
}

@Composable
fun Chip(text: String, modifier: Modifier = Modifier) {
    Text(
        text, fontFamily = FontFamily.Monospace, fontSize = 11.sp, color = EColors.Muted,
        modifier = modifier.clip(RoundedCornerShape(50)).background(EColors.Bg2)
            .border(1.dp, EColors.Line, RoundedCornerShape(50)).padding(horizontal = 10.dp, vertical = 4.dp),
    )
}
