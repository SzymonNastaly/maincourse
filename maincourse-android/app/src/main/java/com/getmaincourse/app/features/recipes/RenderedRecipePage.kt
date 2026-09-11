package com.getmaincourse.app.features.recipes

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.os.Handler
import android.os.Looper
import android.view.ViewGroup
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import com.getmaincourse.app.data.model.RecipePageContent
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json

@Composable
internal fun RenderedRecipePage(
    request: RecipeShareStatus.ReadingPage,
    onFinished: (Long, RecipePageContent?) -> Unit,
    modifier: Modifier = Modifier,
) {
    val currentOnFinished = rememberUpdatedState(onFinished)
    AndroidView(
        factory = { context -> RecipeExtractionWebView(context) },
        modifier = modifier,
        update = { webView ->
            webView.extract(request) { requestId, content ->
                currentOnFinished.value(requestId, content)
            }
        },
        onRelease = RecipeExtractionWebView::release,
    )
}

@SuppressLint("SetJavaScriptEnabled")
private class RecipeExtractionWebView(context: android.content.Context) : WebView(context) {
    private val handler = Handler(Looper.getMainLooper())
    private val extractionScript = context.assets.open(EXTRACTION_SCRIPT).bufferedReader().use { it.readText() }
    private var activeRequestId: Long? = null
    private var callback: ((Long, RecipePageContent?) -> Unit)? = null
    private var extractionScheduled = false
    private val timeout = Runnable { complete(null) }

    init {
        layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
        settings.javaScriptEnabled = true
        settings.domStorageEnabled = true
        settings.allowFileAccess = false
        settings.allowContentAccess = false
        settings.javaScriptCanOpenWindowsAutomatically = false
        settings.setSupportMultipleWindows(false)
        settings.setGeolocationEnabled(false)
        settings.mixedContentMode = android.webkit.WebSettings.MIXED_CONTENT_NEVER_ALLOW
        settings.cacheMode = android.webkit.WebSettings.LOAD_NO_CACHE
        android.webkit.CookieManager.getInstance().setAcceptThirdPartyCookies(this, false)
        webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean =
                request.url.scheme !in setOf("http", "https")

            override fun onPageStarted(view: WebView, url: String, favicon: Bitmap?) {
                extractionScheduled = false
            }

            override fun onPageFinished(view: WebView, url: String) {
                if (extractionScheduled || activeRequestId == null) return
                extractionScheduled = true
                handler.postDelayed({ evaluatePage() }, PAGE_SETTLE_MILLIS)
            }

            override fun onReceivedError(
                view: WebView,
                request: WebResourceRequest,
                error: WebResourceError,
            ) {
                if (request.isForMainFrame) complete(null)
            }

            override fun onReceivedHttpError(
                view: WebView,
                request: WebResourceRequest,
                errorResponse: android.webkit.WebResourceResponse,
            ) {
                if (request.isForMainFrame && errorResponse.statusCode >= 400) complete(null)
            }
        }
    }

    fun extract(
        request: RecipeShareStatus.ReadingPage,
        onFinished: (Long, RecipePageContent?) -> Unit,
    ) {
        if (activeRequestId == request.requestId) return
        activeRequestId = request.requestId
        callback = onFinished
        extractionScheduled = false
        handler.removeCallbacksAndMessages(null)
        handler.postDelayed(timeout, PAGE_TIMEOUT_MILLIS)
        loadUrl(request.url)
    }

    fun release() {
        callback = null
        activeRequestId = null
        handler.removeCallbacksAndMessages(null)
        stopLoading()
        loadUrl("about:blank")
        clearHistory()
        removeAllViews()
        destroy()
    }

    private fun evaluatePage() {
        if (activeRequestId == null) return
        evaluateJavascript(extractionScript) { result ->
            complete(parseRecipePageEvaluation(result))
        }
    }

    private fun complete(content: RecipePageContent?) {
        val requestId = activeRequestId ?: return
        val finished = callback
        activeRequestId = null
        callback = null
        handler.removeCallbacksAndMessages(null)
        stopLoading()
        finished?.invoke(requestId, content)
    }
}

internal fun parseRecipePageEvaluation(result: String?): RecipePageContent? {
    if (result.isNullOrBlank() || result == "null") return null
    return try {
        val pageJson = PAGE_JSON.decodeFromString<String>(result)
        PAGE_JSON.decodeFromString<RecipePageContent>(pageJson)
    } catch (_: SerializationException) {
        null
    } catch (_: IllegalArgumentException) {
        null
    }
}

private const val EXTRACTION_SCRIPT = "recipe_page_extractor.js"
private const val PAGE_SETTLE_MILLIS = 1_200L
private const val PAGE_TIMEOUT_MILLIS = 12_000L
private val PAGE_JSON = Json { ignoreUnknownKeys = true }
