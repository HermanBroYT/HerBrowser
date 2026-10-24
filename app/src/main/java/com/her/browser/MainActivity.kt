package com.her.browser

import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts

class MainActivity : ComponentActivity(), Host {

    private lateinit var state: BrowserState
    private var pending: (() -> Unit)? = null
    private var fileCb: ValueCallback<Array<Uri>>? = null

    private val permLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
            val cb = pending
            pending = null
            cb?.invoke()
        }

    private val fileLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { r ->
            fileCb?.onReceiveValue(
                WebChromeClient.FileChooserParams.parseResult(r.resultCode, r.data)
            )
            fileCb = null
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val bar = SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT)
        enableEdgeToEdge(statusBarStyle = bar, navigationBarStyle = bar)
        state = BrowserState(this, this)
        if (intent?.action == Intent.ACTION_VIEW) {
            intent.data?.let { state.open(it.toString()) }
        }
        setContent { HerTheme { App(state) } }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        if (intent.action == Intent.ACTION_VIEW) {
            intent.data?.let { state.newTab(it.toString()) }
        }
    }

    override fun ensure(perms: List<String>, done: () -> Unit) {
        val missing = perms.filter {
            checkSelfPermission(it) != PackageManager.PERMISSION_GRANTED
        }
        if (missing.isEmpty()) {
            done()
            return
        }
        pending?.invoke()
        pending = done
        permLauncher.launch(missing.toTypedArray())
    }

    override fun chooseFile(
        cb: ValueCallback<Array<Uri>>,
        params: WebChromeClient.FileChooserParams
    ): Boolean {
        fileCb?.onReceiveValue(null)
        fileCb = cb
        return try {
            fileLauncher.launch(params.createIntent())
            true
        } catch (e: Exception) {
            fileCb = null
            false
        }
    }
}
