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
    modifier: Modifier,
    isSubmitting: Boolean,
    error: String?,
    startsInSignUpMode: Boolean = false,
    onSignIn: (SignInRequest) -> Unit,
    onSignUp: (SignUpRequest) -> Unit,
) {
    var signup by rememberSaveable { mutableStateOf(startsInSignUpMode) }
    var name by rememberSaveable { mutableStateOf("") }
    var email by rememberSaveable { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var submitted by remember { mutableStateOf(false) }
    val focusManager = LocalFocusManager.current

    val busy = isSubmitting
    val nameInvalid = submitted && signup && name.isBlank()
    val emailInvalid = submitted && !email.isValidEmail()
    val passwordInvalid = submitted && password.isEmpty()
    val passwordShort = submitted && signup && password.length < 12
    val submit = {
        submitted = true
        val valid = email.isValidEmail() && password.isNotEmpty() &&
            (!signup || (name.isNotBlank() && password.length >= 12))
        if (valid && !busy) {
            if (signup) {
                onSignUp(
                    SignUpRequest(
                        name = name.trim(),
                        email = email.trim(),
                        password = password,
                        passwordConfirmation = password,
                        deviceName = "Android",
                    ),
                )
            } else {
                onSignIn(SignInRequest(email.trim(), password, "Android"))
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
            if (signup) {
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
                    contentType = if (signup) ContentType.NewPassword else ContentType.Password
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
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = {
                    focusManager.clearFocus()
                    submit()
                }),
                shape = MainCourseShapes.Card,
            )
            Button(
                onClick = submit,
                enabled = !busy,
                modifier = Modifier.fillMaxWidth().testTag("auth_submit"),
                shape = MainCourseShapes.Control,
            ) {
                if (busy) {
                    CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                    Spacer(Modifier.size(8.dp))
                    Text(stringResource(R.string.auth_submitting))
                } else {
                    Text(stringResource(if (signup) R.string.auth_create_account else R.string.auth_sign_in))
                }
            }
            TextButton(
                onClick = {
                    signup = !signup
                    submitted = false
                    password = ""
                },
                enabled = !busy,
                modifier = Modifier.align(Alignment.CenterHorizontally),
            ) {
                Text(stringResource(if (signup) R.string.auth_have_account else R.string.auth_need_account))
            }
        }
    }
}

private fun String.isValidEmail(): Boolean {
    val trimmed = trim()
    return trimmed.contains('@') && !trimmed.startsWith('@') && !trimmed.endsWith('@')
}
