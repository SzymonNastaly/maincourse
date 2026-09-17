package com.getmaincourse.app.data.network

import java.io.IOException
import java.net.Inet6Address
import java.net.InetSocketAddress
import java.net.Proxy
import java.util.concurrent.TimeUnit
import okhttp3.Call
import okhttp3.EventListener
import okhttp3.Protocol
import retrofit2.Invocation

internal class ApiNetworkDiagnostics(private val logFailure: (String) -> Unit) : EventListener() {
    private var startedAt = System.nanoTime()
    @Volatile private var phase = "queued"
    @Volatile private var connectFailure = "none"

    override fun callStart(call: Call) {
        startedAt = System.nanoTime()
    }

    override fun dnsStart(call: Call, domainName: String) {
        phase = "dns"
    }

    override fun connectStart(call: Call, inetSocketAddress: InetSocketAddress, proxy: Proxy) {
        phase = "connect"
    }

    override fun secureConnectStart(call: Call) {
        phase = "tls"
    }

    override fun connectFailed(
        call: Call,
        inetSocketAddress: InetSocketAddress,
        proxy: Proxy,
        protocol: Protocol?,
        ioe: IOException,
    ) {
        val family = if (inetSocketAddress.address is Inet6Address) "IPv6" else "IPv4"
        connectFailure = "$family:${ioe.javaClass.simpleName}"
    }

    override fun requestHeadersStart(call: Call) {
        phase = "request_headers"
    }

    override fun requestBodyStart(call: Call) {
        phase = "request_body"
    }

    override fun responseHeadersStart(call: Call) {
        phase = "response_headers"
    }

    override fun responseBodyStart(call: Call) {
        phase = "response_body"
    }

    override fun callFailed(call: Call, ioe: IOException) {
        val request = call.request()
        // Retrofit method names identify operations without exposing token-bearing paths or arguments.
        val operation = request.tag(Invocation::class.java)?.method()?.name ?: "request"
        val elapsed = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startedAt)
        logFailure(
            "operation=$operation method=${request.method} host=${request.url.host} " +
                "phase=$phase elapsedMs=$elapsed failure=${ioe.javaClass.simpleName} " +
                "connectFailure=$connectFailure canceled=${call.isCanceled()}",
        )
    }
}
