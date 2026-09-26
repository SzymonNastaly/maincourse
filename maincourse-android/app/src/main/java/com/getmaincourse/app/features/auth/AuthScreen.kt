package com.getmaincourse.app.features.auth

import android.text.Annotation
import android.text.Spanned
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.autofill.ContentType
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentType
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.getmaincourse.app.R
import com.getmaincourse.app.ui.theme.MainCourseColors
import com.getmaincourse.app.ui.theme.MainCourseShapes

@Composable
private fun localizedTagline() = LocalResources.current.getText(R.string.onboarding_tagline).let { text ->
    buildAnnotatedString {
        append(text.toString())
        if (text is Spanned) {
            text.getSpans(0, text.length, Annotation::class.java)
                .filter { it.key == "emphasis" && it.value == "accent" }
                .forEach { span ->
                    addStyle(SpanStyle(color = MainCourseColors.Accent), text.getSpanStart(span), text.getSpanEnd(span))
                }
        }
    }
}

@Composable
fun AuthScreen(
    busy: Boolean,
    error: String?,
    onSignIn: (email: String, password: String) -> Unit,
    onSignUp: (name: String?, email: String, password: String, confirmation: String) -> Unit,
    initialMode: AuthMode = AuthMode.SIGN_IN,
) {
    var mode by rememberSaveable { mutableStateOf(initialMode) }
    var name by rememberSaveable { mutableStateOf("") }
    var email by rememberSaveable { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var confirmation by remember { mutableStateOf("") }
    var passwordVisible by remember { mutableStateOf(false) }
    var submitted by remember { mutableStateOf(false) }
    val focusManager = LocalFocusManager.current
    val signingUp = mode == AuthMode.SIGN_UP
    val nameInvalid = submitted && signingUp && name.isBlank()
    val emailInvalid = submitted && !email.isValidEmail()
    val passwordInvalid = submitted && password.isEmpty()
    val passwordShort = submitted && signingUp && password.length < 12
    val confirmationInvalid = submitted && signingUp && confirmation != password
    val submit = {
        submitted = true
        val valid = email.isValidEmail() && password.isNotEmpty() &&
            (!signingUp || (name.isNotBlank() && password.length >= 12 && confirmation == password))
        if (valid && !busy) {
            focusManager.clearFocus()
            if (signingUp) {
                onSignUp(name.trim().takeIf(String::isNotEmpty), email.trim(), password, confirmation)
            } else {
                onSignIn(email.trim(), password)
            }
        }
    }

    Column(
        modifier = Modifier.fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding().imePadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp, vertical = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Column(
            modifier = Modifier.widthIn(max = 440.dp).fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Image(
                painterResource(R.drawable.brand_mark),
                contentDescription = null,
                modifier = Modifier.size(80.dp).testTag("auth_logo"),
            )
            Text(
                text = localizedTagline(),
                modifier = Modifier.padding(top = 16.dp, bottom = 28.dp),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
            )
            Surface(
                modifier = Modifier.fillMaxWidth().testTag("auth_form"),
                shape = MainCourseShapes.Panel,
                color = MainCourseColors.Surface,
                tonalElevation = 1.dp,
                shadowElevation = 1.dp,
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(
                            stringResource(if (signingUp) R.string.auth_signup_title else R.string.auth_signin_title),
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Text(
                            stringResource(if (signingUp) R.string.auth_signup_body else R.string.auth_signin_body),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MainCourseColors.Body,
                        )
                    }
                    if (error != null) {
                        Surface(color = MainCourseColors.DangerTint, shape = MainCourseShapes.Control) {
                            Text(
                                error,
                                modifier = Modifier.fillMaxWidth().padding(12.dp),
                                color = MainCourseColors.Danger,
                                style = MaterialTheme.typography.bodyMedium,
                            )
                        }
                    }
                    if (signingUp) {
                        OutlinedTextField(
                            value = name,
                            onValueChange = { name = it.take(50) },
                            modifier = Modifier.fillMaxWidth().testTag("auth_name")
                                .semantics { contentType = ContentType.PersonFirstName },
                            label = { Text(stringResource(R.string.auth_name)) },
                            isError = nameInvalid,
                            supportingText = if (nameInvalid) {
                                { Text(stringResource(R.string.auth_name_required)) }
                            } else null,
                            singleLine = true,
                            enabled = !busy,
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                            keyboardActions = KeyboardActions(onNext = { focusManager.moveFocus(FocusDirection.Down) }),
                            shape = MainCourseShapes.Card,
                        )
                    }
                    OutlinedTextField(
                        value = email,
                        onValueChange = { email = it },
                        modifier = Modifier.fillMaxWidth().testTag("auth_email")
                            .semantics { contentType = ContentType.Username },
                        label = { Text(stringResource(R.string.auth_email)) },
                        isError = emailInvalid,
                        supportingText = if (emailInvalid) {
                            { Text(stringResource(R.string.auth_email_required)) }
                        } else null,
                        singleLine = true,
                        enabled = !busy,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email, imeAction = ImeAction.Next),
                        keyboardActions = KeyboardActions(onNext = { focusManager.moveFocus(FocusDirection.Down) }),
                        shape = MainCourseShapes.Card,
                    )
                    OutlinedTextField(
                        value = password,
                        onValueChange = { password = it },
                        modifier = Modifier.fillMaxWidth().testTag("auth_password").semantics {
                            contentType = if (signingUp) ContentType.NewPassword else ContentType.Password
                        },
                        label = { Text(stringResource(R.string.auth_password)) },
                        isError = passwordInvalid || passwordShort,
                        supportingText = when {
                            passwordInvalid -> ({ Text(stringResource(R.string.auth_password_required)) })
                            passwordShort -> ({ Text(stringResource(R.string.auth_password_too_short)) })
                            else -> null
                        },
                        singleLine = true,
                        enabled = !busy,
                        visualTransformation = if (passwordVisible) {
                            VisualTransformation.None
                        } else {
                            PasswordVisualTransformation()
                        },
                        trailingIcon = {
                            TextButton(onClick = { passwordVisible = !passwordVisible }) {
                                Text(
                                    stringResource(
                                        if (passwordVisible) R.string.auth_hide_password else R.string.auth_show_password,
                                    ),
                                )
                            }
                        },
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Password,
                            imeAction = if (signingUp) ImeAction.Next else ImeAction.Done,
                        ),
                        keyboardActions = if (signingUp) {
                            KeyboardActions(onNext = { focusManager.moveFocus(FocusDirection.Down) })
                        } else {
                            KeyboardActions(onDone = { submit() })
                        },
                        shape = MainCourseShapes.Card,
                    )
                    if (signingUp) {
                        OutlinedTextField(
                            value = confirmation,
                            onValueChange = { confirmation = it },
                            modifier = Modifier.fillMaxWidth().testTag("auth_password_confirmation")
                                .semantics { contentType = ContentType.NewPassword },
                            label = { Text(stringResource(R.string.auth_password_confirmation)) },
                            isError = confirmationInvalid,
                            supportingText = if (confirmationInvalid) {
                                { Text(stringResource(R.string.auth_passwords_do_not_match)) }
                            } else null,
                            singleLine = true,
                            enabled = !busy,
                            visualTransformation = PasswordVisualTransformation(),
                            keyboardOptions = KeyboardOptions(
                                keyboardType = KeyboardType.Password,
                                imeAction = ImeAction.Done,
                            ),
                            keyboardActions = KeyboardActions(onDone = { submit() }),
                            shape = MainCourseShapes.Card,
                        )
                    }
                    Button(
                        onClick = submit,
                        enabled = !busy,
                        modifier = Modifier.fillMaxWidth().testTag("auth_submit"),
                        shape = MainCourseShapes.Control,
                    ) {
                        if (busy) {
                            CircularProgressIndicator(
                                Modifier.size(18.dp).testTag("auth_email_progress"),
                                strokeWidth = 2.dp,
                            )
                            Spacer(Modifier.size(8.dp))
                            Text(stringResource(R.string.auth_submitting))
                        } else {
                            Text(stringResource(if (signingUp) R.string.auth_create_account else R.string.auth_sign_in))
                        }
                    }
                    TextButton(
                        onClick = {
                            mode = if (signingUp) AuthMode.SIGN_IN else AuthMode.SIGN_UP
                            submitted = false
                            password = ""
                            confirmation = ""
                        },
                        enabled = !busy,
                        modifier = Modifier.align(Alignment.CenterHorizontally),
                    ) {
                        Text(stringResource(if (signingUp) R.string.auth_have_account else R.string.auth_need_account))
                    }
                }
            }
        }
    }
}

enum class AuthMode { SIGN_IN, SIGN_UP }

private fun String.isValidEmail(): Boolean {
    val trimmed = trim()
    return trimmed.contains('@') && !trimmed.startsWith('@') && !trimmed.endsWith('@')
}
