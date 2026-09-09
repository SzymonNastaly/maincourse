package com.getmaincourse.app.features.auth

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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentType
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.getmaincourse.app.R
import com.getmaincourse.app.data.model.SignInRequest
import com.getmaincourse.app.data.model.SignUpRequest
import com.getmaincourse.app.ui.theme.MainCourseColors
import com.getmaincourse.app.ui.theme.MainCourseShapes

@Composable
fun AuthScreen(
    busy: Boolean,
    error: String?,
    onSignIn: (email: String, password: String) -> Unit,
    onSignUp: (name: String?, email: String, password: String, confirmation: String) -> Unit,
) {
    AuthForm(
        modifier = Modifier,
        busy = busy,
        error = error,
        onSignIn = onSignIn,
        onSignUp = onSignUp,
    )
}

@Composable
private fun AuthForm(
    modifier: Modifier,
    busy: Boolean,
    error: String?,
    startsInSignUpMode: Boolean = false,
    nameRequired: Boolean = false,
    onSignIn: (email: String, password: String) -> Unit,
    onSignUp: (name: String?, email: String, password: String, confirmation: String) -> Unit,
) {
    var mode by rememberSaveable {
        mutableStateOf(if (startsInSignUpMode) AuthMode.SIGN_UP else AuthMode.SIGN_IN)
    }
    var name by rememberSaveable { mutableStateOf("") }
    var email by rememberSaveable { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var confirmation by remember { mutableStateOf("") }
    var submitted by remember { mutableStateOf(false) }
    val focusManager = LocalFocusManager.current

    val signingUp = mode == AuthMode.SIGN_UP
    val nameInvalid = submitted && signingUp && nameRequired && name.isBlank()
    val emailInvalid = submitted && !email.isValidEmail()
    val passwordInvalid = submitted && password.isEmpty()
    val passwordShort = submitted && signingUp && password.length < 12
    val confirmationInvalid = submitted && signingUp && confirmation != password
    val submit = {
        submitted = true
        val valid = email.isValidEmail() && password.isNotEmpty() &&
            (!signingUp || ((!nameRequired || name.isNotBlank()) &&
                password.length >= 12 && confirmation == password))
        if (valid && !busy) {
            if (signingUp) {
                onSignUp(name.trim().takeIf(String::isNotEmpty), email.trim(), password, confirmation)
            } else {
                onSignIn(email.trim(), password)
            }
        }
    }

    Column(
        modifier = modifier.fillMaxSize().navigationBarsPadding().imePadding()
            .verticalScroll(rememberScrollState()).padding(horizontal = 24.dp, vertical = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Column(
            modifier = Modifier.widthIn(max = 440.dp).fillMaxWidth().testTag("auth_form"),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Image(painterResource(R.drawable.brand_mark), contentDescription = null, modifier = Modifier.size(72.dp))
            Text(stringResource(R.string.auth_welcome), style = MaterialTheme.typography.headlineMedium)
            Text(
                stringResource(R.string.auth_welcome_body),
                style = MaterialTheme.typography.bodyLarge,
                color = MainCourseColors.Body,
            )
            if (error != null) {
                Text(error, color = MainCourseColors.Danger, style = MaterialTheme.typography.bodyMedium)
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
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Password,
                    imeAction = if (signingUp) ImeAction.Next else ImeAction.Done,
                ),
                keyboardActions = if (signingUp) {
                    KeyboardActions(onNext = { focusManager.moveFocus(FocusDirection.Down) })
                } else {
                    KeyboardActions(onDone = {
                        focusManager.clearFocus()
                        submit()
                    })
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
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = {
                        focusManager.clearFocus()
                        submit()
                    }),
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

@Suppress("UNUSED_PARAMETER")
@Composable
fun AuthScreen(
    modifier: Modifier,
    authenticationMethod: AuthenticationMethod?,
    error: String?,
    startsInSignUpMode: Boolean = false,
    onSignIn: (SignInRequest) -> Unit,
    onSignUp: (SignUpRequest) -> Unit,
    onGoogleSignIn: () -> Unit,
    onAppleSignIn: () -> Unit,
    onCancelAppleSignIn: () -> Unit,
    appleCanCancel: Boolean,
) {
    AuthForm(
        modifier = modifier,
        busy = authenticationMethod != null,
        error = error,
        startsInSignUpMode = startsInSignUpMode,
        nameRequired = true,
        onSignIn = { email, password -> onSignIn(SignInRequest(email, password, "Android")) },
        onSignUp = { name, email, password, confirmation ->
            onSignUp(SignUpRequest(name, email, password, confirmation, "Android"))
        },
    )
}

enum class AuthMode { SIGN_IN, SIGN_UP }

enum class AuthenticationMethod {
    EMAIL,
    GOOGLE,
    APPLE,
}

private fun String.isValidEmail(): Boolean {
    val trimmed = trim()
    return trimmed.contains('@') && !trimmed.startsWith('@') && !trimmed.endsWith('@')
}
