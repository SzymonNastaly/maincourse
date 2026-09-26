package com.getmaincourse.app.data.network

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

/** Captures the problem once; UI resolves it later without retaining a resource context. */
class LocalizedApiException(response: Response<*>) : HttpException(response) {
    val problem = try {
        ApiProblem.parse(response.errorBody()?.string().orEmpty())
    } catch (_: IOException) {
        ApiProblem()
    }
}

class ApiErrorCallAdapterFactory : CallAdapter.Factory() {
    override fun get(returnType: Type, annotations: Array<Annotation>, retrofit: Retrofit): CallAdapter<*, *> {
        @Suppress("UNCHECKED_CAST")
        val delegate = retrofit.nextCallAdapter(this, returnType, annotations) as CallAdapter<Any, Any>
        return object : CallAdapter<Any, Any> {
            override fun responseType(): Type = delegate.responseType()
            override fun adapt(call: Call<Any>): Any = delegate.adapt(ErrorCall(call))
        }
    }

    private class ErrorCall<T>(private val delegate: Call<T>) : Call<T> {
        override fun enqueue(callback: Callback<T>) = delegate.enqueue(object : Callback<T> {
            override fun onResponse(call: Call<T>, response: Response<T>) {
                if (response.isSuccessful) callback.onResponse(this@ErrorCall, response)
                else callback.onFailure(this@ErrorCall, LocalizedApiException(response))
            }
            override fun onFailure(call: Call<T>, failure: Throwable) = callback.onFailure(this@ErrorCall, failure)
        })
        override fun execute(): Response<T> = delegate.execute().also {
            if (!it.isSuccessful) throw LocalizedApiException(it)
        }
        override fun clone(): Call<T> = ErrorCall(delegate.clone())
        override fun cancel() = delegate.cancel()
        override fun isExecuted(): Boolean = delegate.isExecuted
        override fun isCanceled(): Boolean = delegate.isCanceled
        override fun request(): Request = delegate.request()
        override fun timeout(): Timeout = delegate.timeout()
    }
}
