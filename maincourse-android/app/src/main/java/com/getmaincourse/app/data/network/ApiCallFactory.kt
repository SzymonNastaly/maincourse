package com.getmaincourse.app.data.network

import com.getmaincourse.app.data.session.SessionProvider
import okhttp3.Call
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import okio.BufferedSink

class ApiCallFactory(
    sessionProvider: SessionProvider,
    sessionEvents: SessionEvents,
    client: OkHttpClient = OkHttpClient(),
    logFailure: (String) -> Unit = {},
) : Call.Factory {
    // OkHttp 5 races connection attempts before sending a request, even when replay is disabled.
    private val mutations = client.newBuilder()
        .fastFallback(true)
        .retryOnConnectionFailure(false)
        .addInterceptor(AuthInterceptor(sessionProvider, sessionEvents))
        .eventListenerFactory { ApiNetworkDiagnostics(logFailure) }
        .build()
    private val reads = mutations.newBuilder()
        .retryOnConnectionFailure(true)
        .build()

    override fun newCall(request: Request): Call =
        if (request.method == "GET" || request.method == "HEAD") {
            reads.newCall(request)
        } else {
            // Retry-After responses can trigger follow-ups even with connection recovery disabled.
            // A one-shot body also prevents those replays; DELETE needs an explicit empty body.
            val body = request.body ?: if (request.method == "DELETE") ByteArray(0).toRequestBody() else null
            val singleAttempt = if (body == null) request else request.newBuilder()
                .method(request.method, SingleAttemptBody(body))
                .build()
            mutations.newCall(singleAttempt)
        }
}

private class SingleAttemptBody(private val body: RequestBody) : RequestBody() {
    override fun isOneShot() = true
    override fun isDuplex() = body.isDuplex()
    override fun contentType() = body.contentType()
    override fun contentLength() = body.contentLength()
    override fun writeTo(sink: BufferedSink) = body.writeTo(sink)
}
