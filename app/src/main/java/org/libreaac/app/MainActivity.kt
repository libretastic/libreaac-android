package org.libreaac.app

import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.util.Base64
import android.webkit.JavascriptInterface
import android.webkit.MimeTypeMap
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.webkit.WebViewAssetLoader
import java.util.Locale

class MainActivity : Activity(), TextToSpeech.OnInitListener {
    private lateinit var webView: WebView
    private var textToSpeech: TextToSpeech? = null
    private var speechReady = false
    private var pendingSpeech: SpeechRequest? = null
    private var pendingSave: PendingSave? = null
    private var fileSelection: ValueCallback<Array<Uri>>? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        textToSpeech = TextToSpeech(this, this)
        webView = WebView(this)
        setContentView(webView)
        configureWebView()

        if (savedInstanceState == null) {
            webView.loadUrl(APP_URL)
        } else {
            webView.restoreState(savedInstanceState)
        }
    }

    @Suppress("SetJavaScriptEnabled")
    private fun configureWebView() {
        val assetLoader = WebViewAssetLoader.Builder()
            .addPathHandler("/assets/", WebViewAssetLoader.AssetsPathHandler(this))
            .build()

        webView.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            allowFileAccess = false
            allowContentAccess = false
            mediaPlaybackRequiresUserGesture = true
            setSupportMultipleWindows(false)
        }

        webView.addJavascriptInterface(LibreAACBridge(), BRIDGE_NAME)
        webView.webViewClient = object : WebViewClient() {
            override fun shouldInterceptRequest(
                view: WebView,
                request: WebResourceRequest
            ): WebResourceResponse? = assetLoader.shouldInterceptRequest(request.url)

            override fun shouldOverrideUrlLoading(
                view: WebView,
                request: WebResourceRequest
            ): Boolean {
                if (request.url.host == APP_HOST) return false
                openExternal(request.url)
                return true
            }
        }
        webView.webChromeClient = object : WebChromeClient() {
            override fun onShowFileChooser(
                webView: WebView,
                filePathCallback: ValueCallback<Array<Uri>>,
                fileChooserParams: FileChooserParams
            ): Boolean {
                fileSelection?.onReceiveValue(null)
                fileSelection = filePathCallback
                val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                    addCategory(Intent.CATEGORY_OPENABLE)
                    type = "*/*"
                    putExtra(
                        Intent.EXTRA_MIME_TYPES,
                        arrayOf("application/json", "application/zip", "application/octet-stream")
                    )
                }
                startActivityForResult(intent, REQUEST_OPEN)
                return true
            }
        }
    }

    private fun openExternal(uri: Uri) {
        val scheme = uri.scheme?.lowercase(Locale.ROOT)
        if (scheme != "http" && scheme != "https") return
        runCatching { startActivity(Intent(Intent.ACTION_VIEW, uri)) }
    }

    @Deprecated("Used for WebView file selection and the Storage Access Framework")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        when (requestCode) {
            REQUEST_OPEN -> {
                val result = if (resultCode == RESULT_OK) {
                    WebChromeClient.FileChooserParams.parseResult(resultCode, data)
                } else {
                    null
                }
                fileSelection?.onReceiveValue(result)
                fileSelection = null
            }

            REQUEST_SAVE -> {
                val save = pendingSave
                pendingSave = null
                if (resultCode == RESULT_OK && save != null && data?.data != null) {
                    val succeeded = runCatching {
                        contentResolver.openOutputStream(data.data!!, "w").use { stream ->
                            requireNotNull(stream) { "No writable stream" }
                            stream.write(save.bytes)
                        }
                    }.isSuccess
                    Toast.makeText(
                        this,
                        if (succeeded) R.string.file_saved else R.string.file_save_failed,
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
        }
    }

    override fun onInit(status: Int) {
        speechReady = status == TextToSpeech.SUCCESS
        if (speechReady) {
            textToSpeech?.language = Locale.getDefault()
            pendingSpeech?.let(::performSpeech)
            pendingSpeech = null
        }
    }

    private fun performSpeech(request: SpeechRequest) {
        val engine = textToSpeech ?: return
        engine.setSpeechRate(request.rate.coerceIn(0.1f, 4f))
        engine.setPitch(request.pitch.coerceIn(0.1f, 2f))
        val parameters = Bundle().apply {
            putFloat(TextToSpeech.Engine.KEY_PARAM_VOLUME, request.volume.coerceIn(0f, 1f))
        }
        engine.speak(request.text, TextToSpeech.QUEUE_FLUSH, parameters, "libreaac-utterance")
    }

    override fun onSaveInstanceState(outState: Bundle) {
        webView.saveState(outState)
        super.onSaveInstanceState(outState)
    }

    override fun onDestroy() {
        webView.removeJavascriptInterface(BRIDGE_NAME)
        webView.destroy()
        textToSpeech?.stop()
        textToSpeech?.shutdown()
        textToSpeech = null
        super.onDestroy()
    }

    inner class LibreAACBridge {
        @JavascriptInterface
        fun speak(text: String, rate: Double, pitch: Double, volume: Double) {
            val request = SpeechRequest(
                text.take(MAX_SPEECH_LENGTH),
                rate.toFloat(),
                pitch.toFloat(),
                volume.toFloat()
            )
            runOnUiThread {
                if (speechReady) performSpeech(request) else pendingSpeech = request
            }
        }

        @JavascriptInterface
        fun stopSpeaking() {
            runOnUiThread {
                pendingSpeech = null
                textToSpeech?.stop()
            }
        }

        @JavascriptInterface
        fun saveFile(base64: String, filename: String, mimeType: String) {
            val bytes = runCatching { Base64.decode(base64, Base64.DEFAULT) }.getOrNull()
                ?: return
            if (bytes.size > MAX_SAVE_BYTES) return
            runOnUiThread {
                pendingSave = PendingSave(bytes)
                val intent = Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
                    addCategory(Intent.CATEGORY_OPENABLE)
                    type = mimeType.ifBlank {
                        MimeTypeMap.getSingleton()
                            .getMimeTypeFromExtension(filename.substringAfterLast('.', ""))
                            ?: "application/octet-stream"
                    }
                    putExtra(Intent.EXTRA_TITLE, FileNames.safe(filename))
                }
                startActivityForResult(intent, REQUEST_SAVE)
            }
        }

        @JavascriptInterface
        fun copyText(text: String) {
            runOnUiThread {
                val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                clipboard.setPrimaryClip(ClipData.newPlainText("LibreAAC phrase", text))
            }
        }

        @JavascriptInterface
        fun shareText(text: String): Boolean {
            runOnUiThread {
                val intent = Intent.createChooser(
                    Intent(Intent.ACTION_SEND).apply {
                        type = "text/plain"
                        putExtra(Intent.EXTRA_TEXT, text)
                    },
                    null
                )
                startActivity(intent)
            }
            return true
        }
    }

    private data class SpeechRequest(
        val text: String,
        val rate: Float,
        val pitch: Float,
        val volume: Float
    )

    private data class PendingSave(val bytes: ByteArray)

    companion object {
        private const val APP_HOST = "appassets.androidplatform.net"
        private const val APP_URL = "https://$APP_HOST/assets/index.html"
        private const val BRIDGE_NAME = "LibreAACAndroid"
        private const val REQUEST_OPEN = 10
        private const val REQUEST_SAVE = 11
        private const val MAX_SPEECH_LENGTH = 20_000
        private const val MAX_SAVE_BYTES = 250 * 1024 * 1024
    }
}
