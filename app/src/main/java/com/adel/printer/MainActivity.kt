package com.adel.printer

import android.annotation.SuppressLint
import android.os.Bundle
import android.view.Gravity
import android.view.ViewGroup
import android.webkit.WebChromeClient
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Button
import android.widget.FrameLayout
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity

/**
 * WebView plein écran qui charge le site Adel EN LIGNE (Netlify).
 * Aucune copie locale du site : toute mise à jour Netlify est visible
 * immédiatement. Le pont d'impression USB est injecté sous
 * window.AndroidPrinter et est appelable depuis les boutons du site.
 *
 * Un petit bouton flottant "Test" ouvre une page de diagnostic embarquée
 * (hors-ligne) pour détecter le protocole de l'imprimante sans dépendre
 * du site — utile au premier test, puis on l'ignore.
 */
class MainActivity : AppCompatActivity() {

    private lateinit var webView: WebView

    // >>> SITE ADEL (chargé par défaut) <<<
    private val adelUrl = "https://lovely-begonia-d851a0.netlify.app/"
    // Page de diagnostic imprimante embarquée dans l'APK (offline).
    private val testUrl = "file:///android_asset/adel_test.html"

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val root = FrameLayout(this)
        webView = WebView(this)
        root.addView(
            webView,
            FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        )

        webView.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true                 // localStorage utilisé par Adel
            databaseEnabled = true
            cacheMode = WebSettings.LOAD_DEFAULT
            useWideViewPort = true
            loadWithOverviewMode = true
            mediaPlaybackRequiresUserGesture = false
            allowFileAccess = true                   // nécessaire pour la page de test locale
        }
        webView.webViewClient = WebViewClient()      // navigation reste dans la WebView
        webView.webChromeClient = WebChromeClient()  // console.log, alert()

        val bridge = UsbPrinterBridge(applicationContext)
        webView.addJavascriptInterface(WebAppInterface(webView, bridge), "AndroidPrinter")

        // Bouton flottant "Test imprimante" (petit, coin bas-droit).
        val testBtn = Button(this).apply {
            text = "🖨️ Test"
            alpha = 0.85f
            setOnClickListener {
                val onSite = webView.url?.startsWith("http") == true
                webView.loadUrl(if (onSite) testUrl else adelUrl)
                text = if (onSite) "↩︎ Adel" else "🖨️ Test"
            }
        }
        val lp = FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT,
            ViewGroup.LayoutParams.WRAP_CONTENT,
            Gravity.BOTTOM or Gravity.END
        ).apply { setMargins(0, 0, 24, 24) }
        root.addView(testBtn, lp)

        setContentView(root)
        webView.loadUrl(adelUrl)

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (webView.canGoBack()) webView.goBack() else finish()
            }
        })
    }

    override fun onDestroy() {
        webView.destroy()
        super.onDestroy()
    }
}
