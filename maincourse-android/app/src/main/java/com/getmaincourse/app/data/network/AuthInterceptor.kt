package com.getmaincourse.app.data.network

import com.getmaincourse.app.data.session.SessionProvider
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import okhttp3.Interceptor
import okhttp3.Response

internal const val ANONYMOUS_HEADER = "X-MainCourse-Anonymous"
private const val AUTHORIZATION_HEADER = "Authorization"

class SessionEvents {
    private val mutableExpired = MutableSharedFlow<Unit>(extraBufferCapacity = 1)

    val expired: SharedFlow<Unit> = mutableExpired.asSharedFlow()

    fun notifyExpired() {
        mutableExpired.tryEmit(Unit)
    }
}

class AuthInterceptor(
    private val sessionProvider: SessionProvider,
    private val sessionEvents: SessionEvents,
) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val original = chain.request()
        val anonymous = original.header(ANONYMOUS_HEADER).equals("true", ignoreCase = true)
        val bearer = sessionProvider.session.value?.token
            ?.takeUnless(String::isBlank)
            ?.let { "Bearer $it" }
            ?.takeUnless { anonymous }
        val request = original.newBuilder()
            .removeHeader(ANONYMOUS_HEADER)
            .removeHeader(AUTHORIZATION_HEADER)
            .apply {
                if (bearer != null) header(AUTHORIZATION_HEADER, bearer)
            }
            .build()

        return chain.proceed(request).also { response ->
            if (bearer != null && response.code == 401) sessionEvents.notifyExpired()
        }
    }
}
