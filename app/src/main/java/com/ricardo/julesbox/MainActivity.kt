package com.ricardo.julesbox

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Color
import android.net.ConnectivityManager
import android.net.NetworkInfo
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.os.Handler
import android.provider.MediaStore
import android.util.Log
import android.view.*
import android.webkit.*
import android.widget.Button
import android.widget.ProgressBar
import android.widget.RelativeLayout
import android.widget.Toast
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.view.WindowCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import java.io.File
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.*
import javax.crypto.*
import javax.crypto.spec.SecretKeySpec
import android.graphics.Outline

class MainActivity : Activity() {
    private lateinit var mContext: Context
    internal var mLoaded = false
    internal var URL = "https://jules.google.com/u/0/session"

    private var mCameraPhotoPath: String? = null
    private var mFilePathCallback: ValueCallback<Array<Uri>>? = null
    internal var doubleBackToExitPressedOnce = false

    private lateinit var btnTryAgain: Button
    private lateinit var mWebView: WebView
    private lateinit var prgs: ProgressBar
    private lateinit var layoutSplash: RelativeLayout
    private lateinit var layoutNoInternet: RelativeLayout

    private lateinit var gestureDetector: GestureDetector

    @SuppressLint("SetJavaScriptEnabled", "ClickableViewAccessibility")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        WindowCompat.setDecorFitsSystemWindows(window, false)

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                window.addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS)
                window.statusBarColor = Color.TRANSPARENT
                window.navigationBarColor = Color.TRANSPARENT
            }
            
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                window.isNavigationBarContrastEnforced = false
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                window.attributes.layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
            }
        } catch (e: Exception) {
            Log.e(TAG, "Edge-to-edge failed", e)
        }

        setContentView(R.layout.activity_main)
        
        val rootView = findViewById<View>(R.id.swipe_container)
        ViewCompat.setOnApplyWindowInsetsListener(rootView) { view, windowInsets ->
            val insets = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.setPadding(insets.left, insets.top, insets.right, 0)
            windowInsets
        }

        mContext = this
        mWebView = findViewById(R.id.webview)
        prgs = findViewById(R.id.progressBar)
        btnTryAgain = findViewById(R.id.btn_try_again)
        layoutNoInternet = findViewById(R.id.layout_no_internet)
        layoutSplash = findViewById(R.id.layout_splash)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            val radius = 64f 
            mWebView.outlineProvider = object : ViewOutlineProvider() {
                override fun getOutline(view: View, outline: Outline) {
                    if (view.width > 0 && view.height > 0) {
                        outline.setRoundRect(0, 0, view.width, view.height, radius)
                    }
                }
            }
            mWebView.clipToOutline = true
        }

        setupSwipes()
        requestForWebview()

        btnTryAgain.setOnClickListener {
            mWebView.visibility = View.GONE
            prgs.visibility = View.VISIBLE
            layoutSplash.visibility = View.VISIBLE
            layoutNoInternet.visibility = View.GONE
            requestForWebview()
        }
    }

    private fun setupSwipes() {
        gestureDetector = GestureDetector(this, object : GestureDetector.SimpleOnGestureListener() {
            private val SWIPE_THRESHOLD = 150
            private val SWIPE_VELOCITY_THRESHOLD = 150

            override fun onFling(e1: MotionEvent?, e2: MotionEvent, vx: Float, vy: Float): Boolean {
                if (e1 == null) return false
                val diffX = e2.x - e1.x
                val diffY = e2.y - e1.y
                
                if (Math.abs(diffX) > Math.abs(diffY)) {
                    if (Math.abs(diffX) > SWIPE_THRESHOLD && Math.abs(vx) > SWIPE_VELOCITY_THRESHOLD) {
                        if (diffX > 0) onSwipeRight() else onSwipeLeft()
                        return true
                    }
                }
                return false
            }
        })

        mWebView.setOnTouchListener { _, event ->
            gestureDetector.onTouchEvent(event)
            false 
        }
    }

    private fun onSwipeRight() {
        val js = "(function() { const selectors = ['[aria-label*=\"Main menu\"]', '[aria-label*=\"History\"]', '[aria-label*=\"Navigation\"]', 'button svg path[d*=\"M3 18h18v-2H3v2zm0-5h18v-2H3v2zm0-7v2h18V6H3z\"]']; for (let s of selectors) { let btn = document.querySelector(s); if (btn) { btn.click(); return; } } })();"
        mWebView.evaluateJavascript(js, null)
    }

    private fun onSwipeLeft() {
        val js = "(function() { const selectors = ['[aria-label*=\"Close\"]', '[aria-label*=\"Dismiss\"]', '[aria-label*=\"back\"]', 'button svg path[d*=\"M19 6.41L17.59 5 12 10.59 6.41 5 5 6.41 10.59 12 5 17.59 6.41 19 12 13.41 17.59 19 19 17.59 13.41 12z\"]']; for (let s of selectors) { let btn = document.querySelector(s); if (btn) { btn.click(); return; } } })();"
        mWebView.evaluateJavascript(js, null)
    }

    private fun requestForWebview() {
        if (!mLoaded) {
            requestWebView()
        } else {
            mWebView.visibility = View.VISIBLE
            prgs.visibility = View.GONE
            layoutSplash.visibility = View.GONE
            layoutNoInternet.visibility = View.GONE
        }
    }

    @SuppressLint("SetJavaScriptEnabled")
    @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
    private fun requestWebView() {
        if (internetCheck(mContext)) {
            mWebView.visibility = View.VISIBLE
            layoutNoInternet.visibility = View.GONE
            mWebView.loadUrl(URL)
        } else {
            prgs.visibility = View.GONE
            mWebView.visibility = View.GONE
            layoutSplash.visibility = View.GONE
            layoutNoInternet.visibility = View.VISIBLE
            return
        }
        
        mWebView.settings.javaScriptEnabled = true
        mWebView.settings.domStorageEnabled = true
        mWebView.settings.databaseEnabled = true
        mWebView.settings.userAgentString = "Mozilla/5.0 (Linux; Android 13) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/116.0.0.0 Mobile Safari/537.36"
        mWebView.settings.javaScriptCanOpenWindowsAutomatically = true
        mWebView.settings.allowFileAccess = true
        mWebView.settings.allowContentAccess = true
        mWebView.settings.mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW

        CookieManager.getInstance().setAcceptCookie(true)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            CookieManager.getInstance().setAcceptThirdPartyCookies(mWebView, true)
        }

        mWebView.webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView, url: String) {
                super.onPageFinished(view, url)
                mLoaded = true
                prgs.visibility = View.GONE
                layoutSplash.visibility = View.GONE
                mWebView.visibility = View.VISIBLE
            }
        }
    }

    public override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        if (requestCode != INPUT_FILE_REQUEST_CODE || mFilePathCallback == null) {
            super.onActivityResult(requestCode, resultCode, data)
            return
        }
        var results: Array<Uri>? = null
        if (resultCode == Activity.RESULT_OK) {
            if (data == null) {
                if (mCameraPhotoPath != null) results = arrayOf(Uri.parse(mCameraPhotoPath))
            } else {
                val dataString = data.dataString
                if (dataString != null) results = arrayOf(Uri.parse(dataString))
            }
        }
        mFilePathCallback!!.onReceiveValue(results)
        mFilePathCallback = null
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        if (keyCode == KeyEvent.KEYCODE_BACK && mWebView.canGoBack()) {
            mWebView.goBack()
            return true
        }
        if (doubleBackToExitPressedOnce) return super.onKeyDown(keyCode, event)
        this.doubleBackToExitPressedOnce = true
        Toast.makeText(this, "Please click BACK again to exit", Toast.LENGTH_SHORT).show()
        Handler().postDelayed({ doubleBackToExitPressedOnce = false }, 2000)
        return true
    }

    companion object {
        internal var TAG = "---MainActivity"
        val INPUT_FILE_REQUEST_CODE = 1
        
        fun internetCheck(context: Context): Boolean {
            val connectivity = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
            val networkInfo = connectivity.allNetworkInfo
            return networkInfo?.any { it.state == NetworkInfo.State.CONNECTED } ?: false
        }
    }
}
