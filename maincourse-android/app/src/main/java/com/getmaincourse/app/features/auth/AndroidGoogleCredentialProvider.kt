package com.getmaincourse.app.features.auth

import android.app.Activity
import androidx.credentials.Credential
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.GetCredentialException
import androidx.credentials.exceptions.NoCredentialException
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.android.libraries.identity.googleid.GoogleIdTokenParsingException
import kotlinx.coroutines.CancellationException

class AndroidGoogleCredentialProvider(
    private val activity: Activity,
    private val credentialManager: CredentialManager = CredentialManager.create(activity),
) : GoogleCredentialProvider {
    override suspend fun credential(nonce: String): String {
        return try {
            parseCredential(credentialManager.getCredential(activity, buildRequest(nonce)).credential)
        } catch (failure: CancellationException) {
            throw failure
        } catch (failure: NoCredentialException) {
            throw mapFailure(failure)
        } catch (failure: GetCredentialException) {
            throw mapFailure(failure)
        } catch (_: IllegalArgumentException) {
            throw GoogleSignInException()
        }
    }

    companion object {
        internal fun buildRequest(nonce: String): GetCredentialRequest {
            val option = GetSignInWithGoogleOption.Builder(GoogleSignInConfiguration.SERVER_CLIENT_ID)
                .setNonce(nonce)
                .build()
            return GetCredentialRequest(listOf(option))
        }

        internal fun parseCredential(credential: Credential): String {
            if (credential !is CustomCredential ||
                credential.type != GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL
            ) {
                throw GoogleSignInException()
            }
            val token = try {
                GoogleIdTokenCredential.createFrom(credential.data).idToken
            } catch (_: GoogleIdTokenParsingException) {
                throw GoogleSignInException()
            }
            return token.takeIf(String::isNotBlank) ?: throw GoogleSignInException()
        }

        internal fun mapFailure(failure: GetCredentialException): Exception =
            if (failure is GetCredentialCancellationException) {
                GoogleSignInCancelledException()
            } else {
                GoogleSignInException()
            }
    }
}
