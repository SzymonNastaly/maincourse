package com.getmaincourse.app.data.network

import com.getmaincourse.app.R
import com.getmaincourse.app.features.recipes.SharedImageReadException
import com.getmaincourse.app.ui.UiMessage
import java.io.IOException
import java.io.InterruptedIOException
import java.net.ConnectException
import java.net.SocketException
import java.net.UnknownHostException
import javax.net.ssl.SSLException
import kotlinx.coroutines.CancellationException
import retrofit2.HttpException

fun Throwable.userMessage(fallback: UiMessage): UiMessage = when (this) {
    is CancellationException -> throw this
    is LocalizedApiException -> UiMessage.Api(problem, code())
    is SharedImageReadException -> userMessage
    is InterruptedIOException -> UiMessage.Resource(R.string.error_connection_timeout)
    is UnknownHostException -> UiMessage.Resource(R.string.error_server_not_found)
    is ConnectException -> UiMessage.Resource(R.string.error_connection_failed)
    is SSLException -> UiMessage.Resource(R.string.error_secure_connection)
    is SocketException -> UiMessage.Resource(R.string.error_connection_interrupted)
    is HttpException -> UiMessage.Api(readProblem(), code())
    is ApiFailure -> UiMessage.Api(ApiProblem(errorCode, limit?.let { ApiProblem.Parameters(it.toLong()) }), status)
    else -> fallback
}

private fun HttpException.readProblem(): ApiProblem = try {
    ApiProblem.parse(response()?.errorBody()?.string().orEmpty())
} catch (_: IOException) {
    ApiProblem()
}
