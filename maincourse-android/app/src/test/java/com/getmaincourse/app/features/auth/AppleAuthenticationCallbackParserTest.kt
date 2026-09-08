package com.getmaincourse.app.features.auth

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AppleAuthenticationCallbackParserTest {
    @Test
    fun acceptsExactReleaseSuccessAndFixedErrors() {
        assertEquals(
            AppleAuthenticationCallback.Success(HANDLE, CODE),
            AppleAuthenticationCallbackParser.parse(
                "https://app.getmaincourse.com/android/auth/apple?transaction_id=$HANDLE&exchange_code=$CODE",
                isDebugBuild = false,
            ),
        )

        AppleAuthenticationError.entries.forEach { error ->
            assertEquals(
                AppleAuthenticationCallback.Error(HANDLE, error),
                AppleAuthenticationCallbackParser.parse(
                    "https://app.getmaincourse.com/android/auth/apple?transaction_id=$HANDLE&error=${error.wireValue}",
                    isDebugBuild = false,
                ),
            )
        }
    }

    @Test
    fun debugSchemeIsAcceptedOnlyByDebugBuilds() {
        val callback = "com.getmaincourse.app.debug:/oauth/apple?transaction_id=$HANDLE&exchange_code=$CODE"

        assertEquals(
            AppleAuthenticationCallback.Success(HANDLE, CODE),
            AppleAuthenticationCallbackParser.parse(callback, isDebugBuild = true),
        )
        assertNull(AppleAuthenticationCallbackParser.parse(callback, isDebugBuild = false))
    }

    @Test
    fun rejectsMalformedOriginsPathsAndUrlComponents() {
        listOf(
            "http://app.getmaincourse.com/android/auth/apple?transaction_id=$HANDLE&exchange_code=$CODE",
            "https://other.example/android/auth/apple?transaction_id=$HANDLE&exchange_code=$CODE",
            "https://app.getmaincourse.com/android/auth/apple/extra?transaction_id=$HANDLE&exchange_code=$CODE",
            "https://user@app.getmaincourse.com/android/auth/apple?transaction_id=$HANDLE&exchange_code=$CODE",
            "https://app.getmaincourse.com:443/android/auth/apple?transaction_id=$HANDLE&exchange_code=$CODE",
            "https://app.getmaincourse.com/android/auth/apple?transaction_id=$HANDLE&exchange_code=$CODE#fragment",
            "com.getmaincourse.app.debug://host/oauth/apple?transaction_id=$HANDLE&exchange_code=$CODE",
            "com.getmaincourse.app.debug:/wrong?transaction_id=$HANDLE&exchange_code=$CODE",
        ).forEach { uri ->
            assertNull(uri, AppleAuthenticationCallbackParser.parse(uri, isDebugBuild = true))
        }
    }

    @Test
    fun rejectsDuplicateUnknownMissingConflictingAndInvalidQueryValues() {
        listOf(
            "transaction_id=$HANDLE&transaction_id=$HANDLE&exchange_code=$CODE",
            "transaction_id=$HANDLE&exchange_code=$CODE&extra=value",
            "exchange_code=$CODE",
            "transaction_id=$HANDLE",
            "transaction_id=$HANDLE&exchange_code=$CODE&error=cancelled",
            "transaction_id=short&exchange_code=$CODE",
            "transaction_id=$HANDLE&exchange_code=${"A".repeat(44)}",
            "transaction_id=$HANDLE&error=unknown_error",
            "transaction_id=$HANDLE&error=cancelled&",
            "transaction_id=$HANDLE%41&error=cancelled",
        ).forEach { query ->
            assertNull(
                query,
                AppleAuthenticationCallbackParser.parse(
                    "https://app.getmaincourse.com/android/auth/apple?$query",
                    isDebugBuild = true,
                ),
            )
        }
    }

    @Test
    fun rejectsOversizedCallbackBeforeParsingItsContents() {
        val callback = "https://app.getmaincourse.com/android/auth/apple?transaction_id=$HANDLE&error=" +
            "cancelled".repeat(300)

        assertNull(AppleAuthenticationCallbackParser.parse(callback, isDebugBuild = true))
    }

    private companion object {
        const val HANDLE = "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA"
        const val CODE = "BBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBB"
    }
}
