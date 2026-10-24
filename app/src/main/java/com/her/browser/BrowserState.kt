package com.her.browser

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.app.DownloadManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.net.Uri
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.View
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.GeolocationPermissions
import android.webkit.PermissionRequest
import android.webkit.URLUtil
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.json.JSONArray
import org.json.JSONObject

data class Site(val title: String, val url: String)

object Perms {
    val camera = listOf(Manifest.permission.CAMERA)
    val mic = listOf(Manifest.permission.RECORD_AUDIO)
    val location = listOf(
        Manifest.permission.ACCESS_FINE_LOCATION,
        Manifest.permission.ACCESS_COARSE_LOCATION
    )
    val notifications = listOf(Manifest.permission.POST_NOTIFICATIONS)
    val nearby = listOf(
        Manifest.permission.BLUETOOTH_SCAN,
        Manifest.permission.BLUETOOTH_CONNECT,
        Manifest.permission.NEARBY_WIFI_DEVICES
    )
    val all = camera + mic + location + notifications + nearby
}

interface Host {
    fun ensure(perms: List<String>, done: () -> Unit)
    fun chooseFile(cb: ValueCallback<Array<Uri>>, params: WebChromeClient.FileChooserParams): Boolean
}

class TabState(val id: Int) {
    var title by mutableStateOf("Новая вкладка")
    var url by mutableStateOf("")
    var progress by mutableFloatStateOf(0f)
    var loading by mutableStateOf(false)
    var canGoBack by mutableStateOf(false)
    var canGoForward by mutableStateOf(false)
    var showHome by mutableStateOf(true)
    var webView: WebView? = null
    val hasPage: Boolean get() = webView != null && url.isNotBlank()
}

class BrowserState(private val activity: Activity, private val host: Host) {

    private val prefs = activity.getSharedPreferences("her", Context.MODE_PRIVATE)
    private var nextId = 1

    val tabs = mutableStateListOf(TabState(nextId++))
    var index by mutableIntStateOf(0)
    var showTabs by mutableStateOf(false)
    var showMenu by mutableStateOf(false)
    var showFavs by mutableStateOf(false)
    var showAdd by mutableStateOf(false)
    var showPerms by mutableStateOf(!prefs.getBoolean("perms_asked", false))
    var searchFocus by mutableIntStateOf(0)
    var permTick by mutableIntStateOf(0)
    var customView by mutableStateOf<View?>(null)
    private var customCb: WebChromeClient.CustomViewCallback? = null

    val favorites = mutableStateListOf<Site>().apply { addAll(load("fav")) }
    val links = mutableStateListOf<Site>().apply { addAll(load("links")) }

    val tab: TabState get() = tabs[index.coerceIn(0, tabs.lastIndex)]

    // ---------- хранилище ----------
    private fun load(key: String): List<Site> = try {
        val a = JSONArray(prefs.getString(key, "[]"))
        List(a.length()) {
            val o = a.getJSONObject(it)
            Site(o.getString("t"), o.getString("u"))
        }
    } catch (e: Exception) {
        emptyList()
    }

    private fun save(key: String, list: List<Site>) {
        val a = JSONArray()
        list.forEach { a.put(JSONObject().put("t", it.title).put("u", it.url)) }
        prefs.edit().putString(key, a.toString()).apply()
    }

    // ---------- навигация ----------
    fun normalize(input: String): String {
        val t = input.trim()
        if (t.isEmpty()) return ""
        if (t.startsWith("http://", true)) return "https://" + t.substring(7)
        if (t.startsWith("https://", true)) return t
        val isUrl = !t.contains(' ') && t.contains('.') && !t.endsWith(".")
        return if (isUrl) "https://$t" else "https://www.google.com/search?q=" + Uri.encode(t)
    }

    fun open(input: String) = openIn(tab, input)

    private fun openIn(t: TabState, input: String) {
        val u = normalize(input)
        if (u.isEmpty()) return
        showTabs = false
        val wv = t.webView ?: createWebView(t)
        t.showHome = false
        t.url = u
        wv.loadUrl(u)
    }

    fun newTab(input: String = "") {
        val t = TabState(nextId++)
        tabs.add(t)
        index = tabs.lastIndex
        showTabs = false
        if (input.isNotBlank()) openIn(t, input)
    }

    fun closeTab(i: Int) {
        val t = tabs.getOrNull(i) ?: return
        t.webView?.let { wv ->
            t.webView = null
            Handler(Looper.getMainLooper()).post {
                (wv.parent as? ViewGroup)?.removeView(wv)
                wv.destroy()
            }
        }
        tabs.removeAt(i)
        if (tabs.isEmpty()) {
            tabs.add(TabState(nextId++))
            index = 0
        } else if (i < index || index > tabs.lastIndex) {
            index = (index - 1).coerceAtLeast(0)
        }
    }

    fun back() {
        val t = tab
        when {
            customView != null -> exitFullscreen()
            showTabs -> showTabs = false
            !t.showHome && t.canGoBack -> t.webView?.goBack()
            !t.showHome -> t.showHome = true
            tabs.size > 1 -> closeTab(index)
        }
    }

    fun forward() {
        val t = tab
        if (t.showHome && t.hasPage) t.showHome = false
        else if (t.canGoForward) t.webView?.goForward()
    }

    fun reload() {
        tab.webView?.reload()
    }

    private fun exitFullscreen() {
        customView = null
        customCb?.onCustomViewHidden()
        customCb = null
    }

    // ---------- избранное и ссылки ----------
    fun isFav(url: String) = favorites.any { it.url == url }

    fun toggleFav() {
        val t = tab
        if (t.showHome || t.url.isBlank()) return
        val i = favorites.indexOfFirst { it.url == t.url }
        if (i >= 0) favorites.removeAt(i)
        else favorites.add(Site(t.title.ifBlank { t.url }, t.url))
        save("fav", favorites)
    }

    fun removeFav(s: Site) {
        favorites.remove(s)
        save("fav", favorites)
    }

    fun addLink(title: String, url: String) {
        val u = normalize(url)
        if (u.isEmpty()) return
        links.add(Site(title.ifBlank { Uri.parse(u).host ?: u }, u))
        save("links", links)
    }

    fun removeLink(s: Site) {
        links.remove(s)
        save("links", links)
        toast("Ссылка удалена")
    }

    fun share() {
        val t = tab
        if (t.url.isBlank()) return
        val i = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, t.url)
        }
        activity.startActivity(Intent.createChooser(i, null))
    }

    fun toast(msg: String) = Toast.makeText(activity, msg, Toast.LENGTH_SHORT).show()

    // ---------- разрешения ----------
    fun has(p: List<String>) =
        p.all { activity.checkSelfPermission(it) == PackageManager.PERMISSION_GRANTED }

    fun hasAny(p: List<String>) =
        p.any { activity.checkSelfPermission(it) == PackageManager.PERMISSION_GRANTED }

    fun requestAll() {
        prefs.edit().putBoolean("perms_asked", true).apply()
        host.ensure(Perms.all) { permTick++ }
    }

    fun dismissPerms() {
        showPerms = false
        prefs.edit().putBoolean("perms_asked", true).apply()
    }

    fun openAppSettings() {
        activity.startActivity(
            Intent(
                Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                Uri.fromParts("package", activity.packageName, null)
            )
        )
    }

    // ---------- WebView ----------
    private fun sync(t: TabState, v: WebView?) {
        if (v == null) return
        t.canGoBack = v.canGoBack()
        t.canGoForward = v.canGoForward()
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun createWebView(t: TabState): WebView {
        val wv = WebView(activity)
        wv.layoutParams = ViewGroup.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT
        )
        wv.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            mediaPlaybackRequiresUserGesture = false
            setGeolocationEnabled(true)
            useWideViewPort = true
            loadWithOverviewMode = true
            setSupportZoom(true)
            builtInZoomControls = true
            displayZoomControls = false
            javaScriptCanOpenWindowsAutomatically = true
            allowFileAccess = false
        }
        CookieManager.getInstance().setAcceptThirdPartyCookies(wv, true)

        wv.webViewClient = object : WebViewClient() {
            override fun onPageStarted(v: WebView?, url: String?, favicon: Bitmap?) {
                t.loading = true
                url?.let { t.url = it }
                sync(t, v)
            }

            override fun onPageFinished(v: WebView?, url: String?) {
                t.loading = false
                t.progress = 0f
                url?.let { t.url = it }
                v?.title?.takeIf { it.isNotBlank() }?.let { t.title = it }
                sync(t, v)
            }

            override fun doUpdateVisitedHistory(v: WebView?, url: String?, isReload: Boolean) {
                url?.let { t.url = it }
                sync(t, v)
            }

            override fun shouldOverrideUrlLoading(v: WebView?, r: WebResourceRequest?): Boolean {
                val u = r?.url ?: return false
                return when (u.scheme) {
                    "http", "https", "about", "data", "blob", "javascript" -> false
                    else -> {
                        try {
                            activity.startActivity(
                                Intent(Intent.ACTION_VIEW, u).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            )
                        } catch (e: Exception) {
                            toast("Нет приложения для этой ссылки")
                        }
                        true
                    }
                }
            }
        }

        wv.webChromeClient = object : WebChromeClient() {
            override fun onProgressChanged(v: WebView?, p: Int) {
                t.progress = p / 100f
            }

            override fun onReceivedTitle(v: WebView?, title: String?) {
                if (!title.isNullOrBlank()) t.title = title
            }

            override fun onPermissionRequest(request: PermissionRequest?) {
                val req = request ?: return
                val need = mutableListOf<String>()
                req.resources.forEach {
                    if (it == PermissionRequest.RESOURCE_VIDEO_CAPTURE) need += Manifest.permission.CAMERA
                    if (it == PermissionRequest.RESOURCE_AUDIO_CAPTURE) need += Manifest.permission.RECORD_AUDIO
                }
                host.ensure(need) {
                    val ok = req.resources.filter { r ->
                        when (r) {
                            PermissionRequest.RESOURCE_VIDEO_CAPTURE -> has(Perms.camera)
                            PermissionRequest.RESOURCE_AUDIO_CAPTURE -> has(Perms.mic)
                            else -> false
                        }
                    }
                    if (ok.isEmpty()) req.deny() else req.grant(ok.toTypedArray())
                }
            }

            override fun onGeolocationPermissionsShowPrompt(
                origin: String?, callback: GeolocationPermissions.Callback?
            ) {
                host.ensure(Perms.location) {
                    callback?.invoke(origin, hasAny(Perms.location), false)
                }
            }

            override fun onShowFileChooser(
                w: WebView?,
                cb: ValueCallback<Array<Uri>>?,
                p: WebChromeClient.FileChooserParams?
            ): Boolean {
                if (cb == null || p == null) return false
                return host.chooseFile(cb, p)
            }

            override fun onShowCustomView(view: View?, cb: WebChromeClient.CustomViewCallback?) {
                customView = view
                customCb = cb
            }

            override fun onHideCustomView() {
                customView = null
                customCb = null
            }
        }

        wv.setDownloadListener { url, ua, cd, mime, _ ->
            try {
                val name = URLUtil.guessFileName(url, cd, mime)
                val req = DownloadManager.Request(Uri.parse(url)).apply {
                    setMimeType(mime)
                    addRequestHeader("User-Agent", ua)
                    CookieManager.getInstance().getCookie(url)?.let { addRequestHeader("Cookie", it) }
                    setTitle(name)
                    setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                    setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, name)
                }
                (activity.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager).enqueue(req)
                toast("Загрузка началась")
            } catch (e: Exception) {
                toast("Не удалось начать загрузку")
            }
        }

        t.webView = wv
        return wv
    }
}
