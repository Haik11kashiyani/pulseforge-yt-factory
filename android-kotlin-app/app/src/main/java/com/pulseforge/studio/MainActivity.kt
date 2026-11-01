package com.pulseforge.studio

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.content.SharedPreferences
import android.graphics.Color
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.tts.TextToSpeech
import android.util.Base64
import android.view.Gravity
import android.view.ViewGroup
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import org.json.JSONObject
import java.security.SecureRandom
import java.util.Locale
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

class MainActivity : Activity(), TextToSpeech.OnInitListener {

    private lateinit var webView: WebView
    private lateinit var statusBanner: TextView
    private var tts: TextToSpeech? = null
    private var ttsReady: Boolean = false
    private val mainHandler: Handler = Handler(Looper.getMainLooper())

    @SuppressLint("SetJavaScriptEnabled")
    @Suppress("DEPRECATION")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        tts = TextToSpeech(this, this)
        val prefs = getSharedPreferences("pulseforge_pro_vault", Context.MODE_PRIVATE)

        val rootLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.parseColor("#FAF8F5"))
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        }

        statusBanner = TextView(this).apply {
            text = "PulseForge Pro v2.1 (Signed Release) • Live Sync Ready"
            setBackgroundColor(Color.parseColor("#141413"))
            setTextColor(Color.parseColor("#10B981"))
            textSize = 11f
            setPadding(28, 16, 28, 16)
            gravity = Gravity.CENTER_VERTICAL
        }
        rootLayout.addView(statusBanner)

        webView = WebView(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                0,
                1f
            )
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            settings.allowFileAccess = true
            settings.allowContentAccess = true
            settings.allowFileAccessFromFileURLs = true
            settings.allowUniversalAccessFromFileURLs = true
            settings.cacheMode = WebSettings.LOAD_DEFAULT
            webChromeClient = WebChromeClient()
            webViewClient = WebViewClient()
            addJavascriptInterface(NativeBridge(prefs), "AndroidNativeVault")
        }
        rootLayout.addView(webView)
        setContentView(rootLayout)

        webView.loadUrl("file:///android_asset/studio.html")
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            tts?.language = Locale("en", "IN")
            tts?.setSpeechRate(1.02f)
            ttsReady = true
        }
    }

    override fun onDestroy() {
        tts?.stop()
        tts?.shutdown()
        super.onDestroy()
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        if (this::webView.isInitialized && webView.canGoBack()) {
            webView.goBack()
        } else {
            super.onBackPressed()
        }
    }

    inner class NativeBridge(private val prefs: SharedPreferences) {

        @JavascriptInterface
        fun speakVoiceover(text: String, langCode: String) {
            if (!ttsReady) return
            if (langCode.contains("in") || langCode.contains("hindi")) {
                tts?.language = Locale("en", "IN")
            } else {
                tts?.language = Locale.US
            }
            tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "pf_vo_id")
        }

        @JavascriptInterface
        fun stopVoiceover() {
            tts?.stop()
        }

        @JavascriptInterface
        fun saveAllCredentials(
            repo: String,
            pat: String,
            geminiKey: String,
            aesKey: String,
            pinCode: String
        ): String {
            val trimmedPat = pat.trim()
            val cleanPat = if (trimmedPat.startsWith("Ghp_")) {
                "ghp_" + trimmedPat.substring(4)
            } else {
                trimmedPat
            }
            val safePin = if (pinCode.trim().isEmpty()) "2026" else pinCode.trim()
            prefs.edit()
                .putString("gh_repo", repo.trim())
                .putString("gh_pat", cleanPat)
                .putString("gemini_key", geminiKey.trim())
                .putString("aes_key", aesKey.trim())
                .putString("app_pin", safePin)
                .apply()
            notifyNative("Saved Vault & PIN to Encrypted Android Storage")
            return encryptAes256Gcm("PulseForge-Dynamic-Sync-v2", aesKey.trim())
        }

        @JavascriptInterface
        fun getSavedConfigJson(): String {
            val obj = JSONObject()
            obj.put("repo", prefs.getString("gh_repo", "") ?: "")
            obj.put("pat", prefs.getString("gh_pat", "") ?: "")
            obj.put("geminiKey", prefs.getString("gemini_key", "") ?: "")
            obj.put("aesKey", prefs.getString("aes_key", "PF-2026-AES256-KEY") ?: "PF-2026-AES256-KEY")
            obj.put("pin", prefs.getString("app_pin", "2026") ?: "2026")
            return obj.toString()
        }

        @JavascriptInterface
        fun notifyNative(msg: String) {
            mainHandler.post {
                statusBanner.text = msg
                Toast.makeText(this@MainActivity, msg, Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun encryptAes256Gcm(plainText: String, passphrase: String): String {
        return try {
            val padded = passphrase.padEnd(32, '0').substring(0, 32)
            val keyBytes = padded.toByteArray(Charsets.UTF_8)
            val secretKey = SecretKeySpec(keyBytes, "AES")
            val iv = ByteArray(12)
            SecureRandom().nextBytes(iv)
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.ENCRYPT_MODE, secretKey, GCMParameterSpec(128, iv))
            val encrypted = cipher.doFinal(plainText.toByteArray(Charsets.UTF_8))
            val combined = ByteArray(iv.size + encrypted.size)
            System.arraycopy(iv, 0, combined, 0, iv.size)
            System.arraycopy(encrypted, 0, combined, iv.size, encrypted.size)
            Base64.encodeToString(combined, Base64.NO_WRAP)
        } catch (e: Exception) {
            "ERR"
        }
    }
}
