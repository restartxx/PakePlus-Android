package com.app.pakeplus

import android.Manifest // ADICIONADO: Import para permissões
import android.annotation.SuppressLint
import android.app.Activity
import android.app.DownloadManager
import android.content.Intent
import android.content.pm.PackageManager // ADICIONADO: Import para checar permissões
import android.graphics.Bitmap
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.view.GestureDetector
import android.view.MotionEvent
import android.webkit.CookieManager
import android.webkit.URLUtil
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat // ADICIONADO: Import para solicitar permissões
import androidx.core.content.ContextCompat // ADICIONADO: Import para checar permissões
import androidx.core.view.GestureDetectorCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import android.view.WindowManager
import android.view.View
import android.graphics.Color

class MainActivity : AppCompatActivity() {

    private lateinit var webView: WebView
    private lateinit var gestureDetector: GestureDetectorCompat

    // Variáveis para o seletor de arquivos
    private var filePathCallback: ValueCallback<Array<Uri>>? = null
    private val fileChooserRequestCode = 101
    
    // ADICIONADO: Variáveis para gerenciar o pedido de permissão de download
    private var pendingDownloadRequest: DownloadRequest? = null
    private val storagePermissionCode = 102
    private data class DownloadRequest(val url: String, val userAgent: String, val contentDisposition: String, val mimetype: String)


    @SuppressLint("SetJavaScriptEnabled", "ClickableViewAccessibility")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.attributes.layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
        window.decorView.systemUiVisibility = (
            View.SYSTEM_UI_FLAG_LAYOUT_STABLE
            or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
            or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
            or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
            or View.SYSTEM_UI_FLAG_FULLSCREEN
            or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
        )
        window.statusBarColor = Color.TRANSPARENT
        window.navigationBarColor = Color.TRANSPARENT
        enableEdgeToEdge()
        setContentView(R.layout.single_main)
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.ConstraintLayout)) { view, insets ->
            val systemBar = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.setPadding(0, systemBar.top, 0, 0)
            insets
        }
        webView = findViewById<WebView>(R.id.webview)
        webView.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            allowFileAccess = true
            setSupportMultipleWindows(true)
        }
        webView.settings.loadWithOverviewMode = true
        webView.settings.setSupportZoom(false)
        webView.clearCache(true)
        webView.webViewClient = MyWebViewClient()
        webView.webChromeClient = MyChromeClient()
        
        // MODIFICADO: Chama a função que verifica a permissão antes de baixar
        webView.setDownloadListener { url, userAgent, contentDisposition, mimetype, _ ->
            checkStoragePermissionAndDownload(url, userAgent, contentDisposition, mimetype)
        }

        gestureDetector =
            GestureDetectorCompat(this, object : GestureDetector.SimpleOnGestureListener() {
                // ... (código do gestureDetector sem alterações)
            })
        webView.setOnTouchListener { _, event ->
            gestureDetector.onTouchEvent(event)
            false
        }
        webView.loadUrl("https://juejin.cn/") // Lembre-se de trocar para a sua URL
    }

    // ADICIONADO: Função que verifica a permissão e inicia o download ou pede permissão
    private fun checkStoragePermissionAndDownload(url: String, userAgent: String, contentDisposition: String, mimetype: String) {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED) {
            startDownload(url, userAgent, contentDisposition, mimetype)
        } else {
            // Salva os detalhes do download para usar depois que a permissão for concedida
            pendingDownloadRequest = DownloadRequest(url, userAgent, contentDisposition, mimetype)
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.WRITE_EXTERNAL_STORAGE), storagePermissionCode)
        }
    }

    // ADICIONADO: Função que efetivamente realiza o download
    private fun startDownload(url: String, userAgent: String, contentDisposition: String, mimetype: String) {
        val request = DownloadManager.Request(Uri.parse(url))
        val cookies = CookieManager.getInstance().getCookie(url)
        
        request.addRequestHeader("Cookie", cookies)
        request.addRequestHeader("User-Agent", userAgent)
        request.setDescription("Downloading file...")
        
        val fileName = URLUtil.guessFileName(url, contentDisposition, mimetype)
        request.setTitle(fileName)
        
        request.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
        request.setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, fileName)
        
        val dManager = getSystemService(DOWNLOAD_SERVICE) as DownloadManager
        dManager.enqueue(request)
        
        Toast.makeText(applicationContext, "Iniciando download de $fileName", Toast.LENGTH_LONG).show()
    }

    // ADICIONADO: Função que trata a resposta do usuário ao pedido de permissão
    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == storagePermissionCode) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                // Permissão concedida, retoma o download pendente
                pendingDownloadRequest?.let {
                    startDownload(it.url, it.userAgent, it.contentDisposition, it.mimetype)
                }
            } else {
                Toast.makeText(this, "Permissão de armazenamento negada. Não é possível baixar.", Toast.LENGTH_LONG).show()
            }
            // Limpa o pedido pendente
            pendingDownloadRequest = null
        }
    }


    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == fileChooserRequestCode && filePathCallback != null) {
            var uris: Array<Uri>? = null
            if (resultCode == Activity.RESULT_OK) {
                if (data?.clipData != null) {
                    val count = data.clipData!!.itemCount
                    uris = Array(count) { i -> data.clipData!!.getItemAt(i).uri }
                } else if (data?.data != null) {
                    uris = arrayOf(data.data!!)
                }
            }
            filePathCallback?.onReceiveValue(uris)
            filePathCallback = null
        }
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        if (webView.canGoBack()) {
            webView.goBack()
        } else {
            super.onBackPressed()
        }
    }

    inner class MyWebViewClient : WebViewClient() {
        // ... (código existente sem alterações)
    }

    inner class MyChromeClient : WebChromeClient() {
        // ... (código existente sem alterações)
    }
}
