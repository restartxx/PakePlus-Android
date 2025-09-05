package com.app.pakeplus

import android.annotation.SuppressLint
import android.app.Activity
import android.app.DownloadManager
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.util.Base64 // ADICIONADO: Para decodificar blobs
import android.view.GestureDetector
import android.view.MotionEvent
import android.webkit.JavascriptInterface // ADICIONADO: Para comunicação JS -> Kotlin
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
import androidx.core.view.GestureDetectorCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import android.view.WindowManager
import android.view.View
import android.graphics.Color
import java.io.File // ADICIONADO: Para manipulação de arquivos
import java.io.FileOutputStream // ADICIONADO: Para salvar arquivos

class MainActivity : AppCompatActivity() {

    private lateinit var webView: WebView
    private lateinit var gestureDetector: GestureDetectorCompat

    private var filePathCallback: ValueCallback<Array<Uri>>? = null
    private val fileChooserRequestCode = 101

    // ADICIONADO: Classe para a interface JavaScript
    inner class JsInterface {
        @JavascriptInterface
        fun getBase64FromBlobData(base64Data: String, mimetype: String, fileName: String) {
            runOnUiThread {
                saveBlobToFile(base64Data, mimetype, fileName)
            }
        }
    }
    
    @SuppressLint("SetJavaScriptEnabled", "ClickableViewAccessibility")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // ... (código do onCreate inicial sem alterações)

        webView = findViewById<WebView>(R.id.webview)
        // ... (configurações do webView)
        
        // ADICIONADO: Registra a interface JavaScript no WebView
        webView.addJavascriptInterface(JsInterface(), "Android")

        // MODIFICADO: Lógica de download que agora trata blobs e URLs HTTP/HTTPS
        webView.setDownloadListener { url, userAgent, contentDisposition, mimetype, _ ->
            if (url.startsWith("blob:")) {
                // É um blob, injeta JavaScript para converter e enviar para Kotlin
                val js = """
                    var xhr = new XMLHttpRequest();
                    xhr.open('GET', '$url', true);
                    xhr.responseType = 'blob';
                    xhr.onload = function(e) {
                        if (this.status == 200) {
                            var blob = this.response;
                            var reader = new FileReader();
                            reader.readAsDataURL(blob);
                            reader.onloadend = function() {
                                base64data = reader.result;
                                var fileName = '$contentDisposition'.match(/filename="?([^"]+)"?/);
                                var finalFileName = fileName ? fileName[1] : 'downloaded_file';
                                Android.getBase64FromBlobData(base64data, '$mimetype', finalFileName);
                            }
                        }
                    };
                    xhr.send();
                """
                webView.evaluateJavascript(js, null)
            } else {
                // É uma URL HTTP/HTTPS, usa o DownloadManager como antes
                try {
                    val request = DownloadManager.Request(Uri.parse(url))
                    val cookies = CookieManager.getInstance().getCookie(url) ?: ""

                    if (cookies.isNotEmpty()) {
                        request.addRequestHeader("Cookie", cookies)
                    }
                    request.addRequestHeader("User-Agent", userAgent)
                    request.setDescription("Baixando arquivo...")

                    val fileName = URLUtil.guessFileName(url, contentDisposition, mimetype)
                    request.setTitle(fileName)
                    
                    request.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                    request.setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, fileName)
                    
                    val dManager = getSystemService(DOWNLOAD_SERVICE) as DownloadManager
                    dManager.enqueue(request)
                    
                    Toast.makeText(applicationContext, "Iniciando download de $fileName", Toast.LENGTH_LONG).show()
                } catch (e: Exception) {
                    e.printStackTrace()
                    Toast.makeText(applicationContext, "Erro ao iniciar download: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
        }
        
        // ... (resto do código do onCreate sem alterações)
        webView.loadUrl("https://juejin.cn/") // Lembre-se de trocar para a sua URL
    }

    // ADICIONADO: Função para salvar os dados do blob em um arquivo
    private fun saveBlobToFile(base64Data: String, mimetype: String, fileName: String) {
        try {
            val file = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), fileName)
            val dataString = base64Data.substringAfter("base64,")
            val decodedBytes = Base64.decode(dataString, Base64.DEFAULT)
            
            val os = FileOutputStream(file, false)
            os.write(decodedBytes)
            os.flush()
            os.close()
            
            // Notifica o sistema sobre o novo arquivo para que ele apareça na galeria/downloads
            val mediaScanIntent = Intent(Intent.ACTION_MEDIA_SCANNER_SCAN_FILE)
            mediaScanIntent.data = Uri.fromFile(file)
            sendBroadcast(mediaScanIntent)
            
            Toast.makeText(this, "$fileName salvo na pasta Downloads", Toast.LENGTH_LONG).show()
        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(this, "Falha ao salvar o arquivo: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }
    
    // ... (resto do MainActivity.kt sem alterações)
}
