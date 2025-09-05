package com.app.pakeplus

import android.annotation.SuppressLint
import android.app.Activity
import android.app.DownloadManager
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.util.Base64
import android.view.GestureDetector
import android.view.MotionEvent
import android.webkit.JavascriptInterface
import android.webkit.URLUtil
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.GestureDetectorCompat
import java.io.File
import java.io.FileOutputStream

class MainActivity : AppCompatActivity() {

    private lateinit var webView: WebView
    private lateinit var gestureDetector: GestureDetectorCompat
    private var filePathCallback: ValueCallback<Array<Uri>>? = null
    private val fileChooserRequestCode = 101

    // A interface para comunicação JS -> Kotlin
    private inner class JsBridge {
        @JavascriptInterface
        fun processBlob(base64Data: String, fileName: String) {
            runOnUiThread {
                saveBase64ToFile(base64Data, fileName)
            }
        }
    }

    @SuppressLint("SetJavaScriptEnabled", "ClickableViewAccessibility")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // ... (Seu código de UI e flags do window permanecem os mesmos)
        setContentView(R.layout.single_main)

        webView = findViewById(R.id.webview)
        setupWebView()

        gestureDetector = GestureDetectorCompat(this, object : GestureDetector.SimpleOnGestureListener() {
            // ... (Seu código de gestos permanece o mesmo)
        })

        webView.setOnTouchListener { _, event ->
            gestureDetector.onTouchEvent(event)
            false
        }

        webView.loadUrl("https://juejin.cn/") // Lembre-se de trocar para a sua URL
    }

    private fun setupWebView() {
        webView.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            allowFileAccess = true
            allowContentAccess = true
            setSupportMultipleWindows(true)
        }
        webView.addJavascriptInterface(JsBridge(), "AndroidBridge")
        webView.webViewClient = MyWebViewClient()
        webView.webChromeClient = MyChromeClient()
    }

    private fun saveBase64ToFile(base64Data: String, fileName: String) {
        try {
            val data = base64Data.substringAfter("base64,")
            val decodedBytes = Base64.decode(data, Base64.DEFAULT)
            val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
            val file = File(downloadsDir, fileName)

            FileOutputStream(file).use { os ->
                os.write(decodedBytes)
            }

            // Notifica o sistema para que o arquivo apareça
            val mediaScanIntent = Intent(Intent.ACTION_MEDIA_SCANNER_SCAN_FILE)
            mediaScanIntent.data = Uri.fromFile(file)
            sendBroadcast(mediaScanIntent)

            Toast.makeText(this, "$fileName salvo na pasta Downloads", Toast.LENGTH_LONG).show()
        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(this, "Falha ao salvar o arquivo: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }
    
    // ... (onActivityResult e onBackPressed permanecem os mesmos)

    private inner class MyWebViewClient : WebViewClient() {
        override fun onPageFinished(view: WebView?, url: String?) {
            super.onPageFinished(view, url)
            // Script para interceptar cliques em links de blob
            val js = """
                document.addEventListener('click', function(e) {
                    let target = e.target.closest('a');
                    if (target && target.href.startsWith('blob:')) {
                        e.preventDefault();
                        fetch(target.href)
                            .then(res => res.blob())
                            .then(blob => {
                                const reader = new FileReader();
                                reader.onloadend = function() {
                                    const fileName = target.download || 'downloaded_file';
                                    AndroidBridge.processBlob(reader.result, fileName);
                                };
                                reader.readAsDataURL(blob);
                            });
                    }
                });
            """
            view?.evaluateJavascript(js, null)
        }
    }

    private inner class MyChromeClient : WebChromeClient() {
        // ... (onShowFileChooser permanece o mesmo)
    }
}
