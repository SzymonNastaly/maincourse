package com.getmaincourse.app.data.network

import com.getmaincourse.app.R
import java.lang.reflect.Type
import java.io.IOException
import okhttp3.Request
import okio.Timeout
import retrofit2.Call
import retrofit2.CallAdapter
import retrofit2.Callback
import retrofit2.HttpException
import retrofit2.Response
import retrofit2.Retrofit

/** Preserves HttpException/status semantics while rendering errors from native resources. */
class LocalizedApiException(response: Response<*>, private val strings: ApiStrings) : HttpException(response) {
    val problem = try {
        ApiProblem.parse(response.errorBody()?.string().orEmpty())
    } catch (_: IOException) {
        ApiProblem()
    }
    fun userMessage(): String {
        if (ApiErrorCode.fromWire(problem.errorCode) != null) return problem.message(strings)

        // Proxies and older servers may omit the contract or return non-JSON bodies.
        val resource = when (code()) {
            401 -> R.string.api_error_unauthorized
            403 -> R.string.api_error_forbidden
            404 -> R.string.api_error_not_found
            400, 422 -> R.string.api_error_invalid_request
            429 -> R.string.api_error_rate_limited
            in 500..599 -> R.string.api_error_server_unavailable
            else -> R.string.api_error_request_failed
        }
        return strings.text(resource)
    }
}

class ApiErrorCallAdapterFactory(private val strings: ApiStrings) : CallAdapter.Factory() {
    override fun get(returnType: Type, annotations: Array<Annotation>, retrofit: Retrofit): CallAdapter<*, *> {
        @Suppress("UNCHECKED_CAST")
        val delegate = retrofit.nextCallAdapter(this, returnType, annotations) as CallAdapter<Any, Any>
        return object : CallAdapter<Any, Any> {
            override fun responseType(): Type = delegate.responseType()
            override fun adapt(call: Call<Any>): Any = delegate.adapt(ErrorCall(call, strings))
        }
    }

    private class ErrorCall<T>(private val delegate: Call<T>, private val strings: ApiStrings) : Call<T> {
        override fun enqueue(callback: Callback<T>) = delegate.enqueue(object : Callback<T> {
            override fun onResponse(call: Call<T>, response: Response<T>) {
                if (response.isSuccessful) callback.onResponse(this@ErrorCall, response)
                else callback.onFailure(this@ErrorCall, LocalizedApiException(response, strings))
            }
            override fun onFailure(call: Call<T>, failure: Throwable) = callback.onFailure(this@ErrorCall, failure)
        })
        override fun execute(): Response<T> = delegate.execute().also {
            if (!it.isSuccessful) throw LocalizedApiException(it, strings)
        }
        override fun clone(): Call<T> = ErrorCall(delegate.clone(), strings)
        override fun cancel() = delegate.cancel()
        override fun isExecuted(): Boolean = delegate.isExecuted
        override fun isCanceled(): Boolean = delegate.isCanceled
        override fun request(): Request = delegate.request()
        override fun timeout(): Timeout = delegate.timeout()
    }
}
