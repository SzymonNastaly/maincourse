package com.getmaincourse.app.features.auth

import android.os.Bundle
import androidx.credentials.CustomCredential
import androidx.credentials.PasswordCredential
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.NoCredentialException
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class AndroidGoogleCredentialProviderTest {
    @Test
    fun buildsExplicitGoogleOptionWithServerAudienceAndRawNonce() {
        val option = AndroidGoogleCredentialProvider.buildRequest("raw-nonce")
            .credentialOptions.single() as GetSignInWithGoogleOption

        assertEquals(GoogleSignInConfiguration.SERVER_CLIENT_ID, option.serverClientId)
        assertEquals("raw-nonce", option.nonce)
    }

    @Test
    fun parsesTokenFromRealGoogleSdkBundle() {
        val sdkCredential = GoogleIdTokenCredential(
            id = "cook@example.com",
            idToken = "eyJhbGciOiJSUzI1NiJ9.eyJzdWIiOiIxIn0.c2ln",
            displayName = "Cook",
            givenName = "Cook",
            familyName = null,
            profilePictureUri = null,
            phoneNumber = null,
        )
        val credential = CustomCredential(
            GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL,
            sdkCredential.data,
        )

        assertEquals(
            "eyJhbGciOiJSUzI1NiJ9.eyJzdWIiOiIxIn0.c2ln",
            AndroidGoogleCredentialProvider.parseCredential(credential),
        )
    }

    @Test
    fun rejectsUnexpectedAndMalformedGoogleCredentialsWithoutLeakingPayload() {
        val unexpected = captureFailure { AndroidGoogleCredentialProvider.parseCredential(PasswordCredential("id", "secret")) }
        val malformed = captureFailure {
            AndroidGoogleCredentialProvider.parseCredential(
                CustomCredential(GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL, Bundle()),
            )
        }
        val validToken = "eyJhbGciOiJSUzI1NiJ9.eyJzdWIiOiIxIn0.c2ln"
        val sdkBundle = GoogleIdTokenCredential("id", validToken, null, null, null, null, null).data
        val tokenKey = sdkBundle.keySet().single { sdkBundle.getString(it) == validToken }
        val emptyTokenBundle = Bundle(sdkBundle).apply { putString(tokenKey, "") }
        val empty = captureFailure {
            AndroidGoogleCredentialProvider.parseCredential(
                CustomCredential(GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL, emptyTokenBundle),
            )
        }

        assertEquals("Google sign-in is unavailable. Please try again.", unexpected.message)
        assertEquals("Google sign-in is unavailable. Please try again.", malformed.message)
        assertEquals("Google sign-in is unavailable. Please try again.", empty.message)
        assertFalse(malformed.stackTraceToString().contains("secret-provider-payload"))
    }

    @Test
    fun mapsDismissalSeparatelyFromUnavailableCredentials() {
        assertTrue(AndroidGoogleCredentialProvider.mapFailure(GetCredentialCancellationException()) is GoogleSignInCancelledException)
        assertTrue(AndroidGoogleCredentialProvider.mapFailure(NoCredentialException()) is GoogleSignInException)
    }

    private fun captureFailure(block: () -> Unit): GoogleSignInException = try {
        block()
        fail("Expected GoogleSignInException")
        error("unreachable")
    } catch (failure: GoogleSignInException) {
        failure
    }
}
