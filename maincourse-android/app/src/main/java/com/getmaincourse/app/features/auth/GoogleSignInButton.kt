package com.getmaincourse.app.features.auth

import androidx.compose.foundation.Image
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.getmaincourse.app.R

@Composable
fun GoogleSignInButton(
    isLoading: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val label = stringResource(R.string.auth_continue_google)
    Button(
        onClick = onClick,
        enabled = !isLoading,
        modifier = modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("auth_google")
            .semantics { contentDescription = label }
            .border(1.dp, GoogleButtonBorder, GoogleButtonShape),
        shape = GoogleButtonShape,
        colors = ButtonDefaults.buttonColors(
            containerColor = GoogleButtonBackground,
            contentColor = GoogleButtonText,
            disabledContainerColor = GoogleButtonBackground,
            disabledContentColor = GoogleButtonText.copy(alpha = 0.38f),
        ),
        contentPadding = PaddingValues(horizontal = 12.dp),
    ) {
        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            Image(
                painter = painterResource(R.drawable.google_g_logo),
                contentDescription = null,
                modifier = Modifier.align(Alignment.CenterStart).size(20.dp),
            )
            if (isLoading) {
                CircularProgressIndicator(
                    modifier = Modifier.size(18.dp),
                    color = GoogleButtonText,
                    strokeWidth = 2.dp,
                )
            } else {
                Text(
                    text = label,
                    style = TextStyle(
                        fontFamily = FontFamily(Font(R.font.google_sans_medium, FontWeight.Medium)),
                        fontWeight = FontWeight.Medium,
                        fontSize = 14.sp,
                        lineHeight = 20.sp,
                    ),
                )
            }
        }
    }
}

private val GoogleButtonBackground = Color(0xFFFFFFFF)
private val GoogleButtonBorder = Color(0xFF747775)
private val GoogleButtonText = Color(0xFF1F1F1F)
private val GoogleButtonShape = RoundedCornerShape(4.dp)
