package com.example.chatgptapplication.Speech

import android.annotation.SuppressLint
import android.os.Bundle
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.appcompat.app.AppCompatActivity

class SpeechRecognizer : AppCompatActivity() {
    private lateinit var webView: WebView
    private lateinit var speechService: SpeechService

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        webView = findViewById(R.id.webView)
        setupWebView()

        speechService = SpeechService(this, WebSocketManager("ws://your-server-ip:8000/ws"))
    }

    private fun setupWebView() {
        webView.settings.javaScriptEnabled = true
        webView.settings.domStorageEnabled = true
        webView.webChromeClient = WebChromeClient()
        webView.webViewClient = WebViewClient()
        webView.addJavascriptInterface(AndroidWebViewInterface(speechService), "AndroidApp")
        webView.loadUrl("https://your-web-page-url")
    }
}
