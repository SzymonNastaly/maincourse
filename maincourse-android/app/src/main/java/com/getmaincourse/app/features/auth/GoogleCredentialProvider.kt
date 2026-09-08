package com.getmaincourse.app.features.auth

interface GoogleCredentialProvider {
    suspend fun credential(nonce: String): String
}

class GoogleSignInCancelledException : Exception()

class GoogleSignInException : Exception("Google sign-in is unavailable. Please try again.")
