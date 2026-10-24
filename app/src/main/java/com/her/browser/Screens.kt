package com.her.browser

import android.net.Uri
import android.view.ViewGroup
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import kotlinx.coroutines.delay

// ============================================================ корень

@Composable
fun App(s: BrowserState) {
    val t = s.tab
    Box(Modifier.fillMaxSize().background(C.Bg)) {
        Column(Modifier.fillMaxSize().imePadding()) {
            Box(Modifier.weight(1f).fillMaxWidth()) {
                when {
                    s.showTabs -> TabsScreen(s)
                    t.showHome -> HomeScreen(s)
                    else -> BrowserScreen(s, t)
                }
            }
            BottomBar(s)
        }
        s.customView?.let { v ->
            AndroidView(
                factory = {
                    (v.parent as? ViewGroup)?.removeView(v)
                    v
                },
                modifier = Modifier.fillMaxSize().background(Color.Black)
            )
        }
        if (s.showMenu) MenuSheet(s)
        if (s.showFavs) FavsSheet(s)
        if (s.showAdd) AddLinkDialog(s)
        if (s.showPerms) PermsDialog(s)
    }
    BackHandler(enabled = s.customView != null || s.showTabs || !t.showHome || s.tabs.size > 1) {
        s.back()
    }
}

// ============================================================ главная

private data class Feed(
    val emoji: String, val c1: Color, val c2: Color,
    val title: String, val tag: String, val query: String
)

private val FEED = listOf(
    Feed("☕", Color(0xFFEADFD8), Color(0xFFC8B6AA), "Как найти баланс в жизни: 5 простых привычек", "Статьи", "как найти баланс в жизни 5 привычек"),
    Feed("🗼", Color(0xFFFF9AB8), Color(0xFF7E57C2), "Путешествия, которые вдохновляют", "Путешествия", "путешествия которые вдохновляют"),
    Feed("🧴", Color(0xFFFFCAD4), Color(0xFFF08BA3), "Лучшие уходовые средства для твоей кожи", "Красота", "лучшие уходовые средства для кожи"),
    Feed("🥗", Color(0xFFD8F0D2), Color(0xFF8CCB8A), "Простые рецепты на каждый день", "Еда", "простые рецепты на каждый день"),
)

@Composable
fun HomeScreen(s: BrowserState) {
    LazyColumn(
        Modifier.fillMaxSize().statusBarsPadding(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)
    ) {
        item { Logo() }
        item { SearchPill(s, big = true) }
        item { Shortcuts(s) }
        item {
            Row(
                Modifier.fillMaxWidth().padding(top = 12.dp, bottom = 6.dp, start = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Интересное", color = C.Ink, fontSize = 24.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                IconButton(onClick = { s.open("интересные статьи") }) {
                    Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, "Ещё", tint = C.Violet)
                }
            }
        }
        items(FEED) { FeedCard(s, it) }
        item { Spacer(Modifier.height(16.dp)) }
    }
}

@Composable
private fun Logo() {
    Column(
        Modifier.fillMaxWidth().padding(top = 28.dp, bottom = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            Modifier.size(92.dp).clip(RoundedCornerShape(28.dp)).background(C.Brand),
            contentAlignment = Alignment.Center
        ) {
            Text("H", color = Color.White, fontSize = 62.sp, fontWeight = FontWeight.ExtraBold)
            Box(Modifier.size(32.dp).clip(CircleShape).background(Color.White), contentAlignment = Alignment.Center) {
                Icon(Icons.Filled.Favorite, null, tint = C.Pink, modifier = Modifier.size(20.dp))
            }
        }
        Spacer(Modifier.height(14.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Her", style = TextStyle(brush = C.Brand, fontSize = 40.sp, fontWeight = FontWeight.ExtraBold))
            Text("Браузер", color = C.Ink, fontSize = 40.sp, fontWeight = FontWeight.ExtraBold)
        }
        Spacer(Modifier.height(4.dp))
        Text("Быстрый. Удобный. Для тебя.", color = C.Muted, fontSize = 17.sp)
    }
}

@Composable
private fun SearchPill(s: BrowserState, big: Boolean, modifier: Modifier = Modifier) {
    val t = s.tab
    val shown = if (t.showHome) "" else t.url.removePrefix("https://").removeSuffix("/")
    var tf by remember(shown, t.id) { mutableStateOf(TextFieldValue(shown)) }
    val fr = remember { FocusRequester() }
    val fm = LocalFocusManager.current
    var seen by remember { mutableIntStateOf(s.searchFocus) }
    LaunchedEffect(s.searchFocus) {
        if (s.searchFocus != seen) {
            seen = s.searchFocus
            delay(80)
            runCatching { fr.requestFocus() }
        }
    }
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(if (big) 30.dp else 24.dp),
        color = Color.White,
        shadowElevation = if (big) 8.dp else 3.dp
    ) {
        Row(
            Modifier.height(if (big) 58.dp else 46.dp).padding(start = 18.dp, end = if (big) 18.dp else 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Filled.Search, null, tint = C.Violet, modifier = Modifier.size(if (big) 26.dp else 22.dp))
            Spacer(Modifier.width(12.dp))
            BasicTextField(
                value = tf,
                onValueChange = { tf = it },
                modifier = Modifier
                    .weight(1f)
                    .focusRequester(fr)
                    .onFocusChanged {
                        if (it.isFocused) tf = tf.copy(selection = TextRange(0, tf.text.length))
                    },
                singleLine = true,
                textStyle = TextStyle(color = C.Ink, fontSize = if (big) 17.sp else 15.sp),
                cursorBrush = SolidColor(C.Violet),
                keyboardOptions = KeyboardOptions(
                    capitalization = KeyboardCapitalization.None,
                    keyboardType = KeyboardType.Text,
                    imeAction = ImeAction.Go
                ),
                keyboardActions = KeyboardActions(onGo = {
                    fm.clearFocus()
                    s.open(tf.text)
                }),
                decorationBox = { inner ->
                    Box(contentAlignment = Alignment.CenterStart) {
                        if (tf.text.isEmpty()) {
                            Text(
                                "Введите адрес или поисковый запрос",
                                color = C.Muted, fontSize = if (big) 16.sp else 14.sp, maxLines = 1
                            )
                        }
                        inner()
                    }
                }
            )
            if (!big) {
                IconButton(onClick = { s.reload() }, modifier = Modifier.size(34.dp)) {
                    Icon(Icons.Filled.Refresh, "Обновить", tint = C.Muted, modifier = Modifier.size(20.dp))
                }
            }
        }
    }
}

@Composable
private fun Shortcuts(s: BrowserState) {
    val defaults = listOf(
        Site("Google", "https://www.google.com"),
        Site("YouTube", "https://m.youtube.com"),
        Site("ВКонтакте", "https://m.vk.com"),
        Site("Instagram", "https://www.instagram.com"),
    )
    LazyRow(
        horizontalArrangement = Arrangement.spacedBy(2.dp),
        contentPadding = PaddingValues(vertical = 16.dp)
    ) {
        items(defaults) { d -> Tile(d.title, { s.open(d.url) }) { BrandIcon(d.title) } }
        items(s.links) { l ->
            Tile(l.title, onClick = { s.open(l.url) }, onLong = { s.removeLink(l) }) {
                Text(l.title.take(1).uppercase(), color = C.Violet, fontSize = 22.sp, fontWeight = FontWeight.Bold)
            }
        }
        item {
            Tile("Избранное", { s.showFavs = true }) {
                Icon(Icons.Filled.Favorite, null, tint = Color(0xFFB04CE0), modifier = Modifier.size(30.dp))
            }
        }
        item {
            Tile("Добавить ссылку", { s.showAdd = true }) {
                Icon(Icons.Filled.Add, null, tint = C.Violet, modifier = Modifier.size(30.dp))
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun Tile(
    label: String,
    onClick: () -> Unit,
    onLong: (() -> Unit)? = null,
    icon: @Composable BoxScope.() -> Unit
) {
    Column(
        Modifier.width(78.dp).clip(RoundedCornerShape(18.dp))
            .combinedClickable(onClick = onClick, onLongClick = onLong)
            .padding(vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Surface(
            shape = RoundedCornerShape(18.dp), color = Color.White,
            shadowElevation = 3.dp, modifier = Modifier.size(60.dp)
        ) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center, content = icon)
        }
        Spacer(Modifier.height(6.dp))
        Text(label, color = C.Ink, fontSize = 12.sp, textAlign = TextAlign.Center, maxLines = 2, lineHeight = 15.sp)
    }
}

@Composable
private fun BrandIcon(name: String) {
    when (name) {
        "Google" -> Text(
            "G",
            style = TextStyle(
                brush = Brush.linearGradient(
                    listOf(Color(0xFF4285F4), Color(0xFFEA4335), Color(0xFFFBBC05), Color(0xFF34A853))
                ),
                fontSize = 34.sp, fontWeight = FontWeight.ExtraBold
            )
        )
        "YouTube" -> Box(
            Modifier.size(width = 38.dp, height = 27.dp).clip(RoundedCornerShape(8.dp)).background(Color(0xFFFF0000)),
            contentAlignment = Alignment.Center
        ) { Icon(Icons.Filled.PlayArrow, null, tint = Color.White, modifier = Modifier.size(20.dp)) }
        "ВКонтакте" -> Box(
            Modifier.size(40.dp).clip(RoundedCornerShape(12.dp)).background(Color(0xFF0077FF)),
            contentAlignment = Alignment.Center
        ) { Text("VK", color = Color.White, fontWeight = FontWeight.ExtraBold, fontSize = 16.sp) }
        else -> Box(
            Modifier.size(40.dp).clip(RoundedCornerShape(12.dp)).background(
                Brush.linearGradient(
                    listOf(Color(0xFFFEDA75), Color(0xFFFA7E1E), Color(0xFFD62976), Color(0xFF962FBF), Color(0xFF4F5BD5))
                )
            ),
            contentAlignment = Alignment.Center
        ) {
            Box(Modifier.size(22.dp).border(2.dp, Color.White, RoundedCornerShape(7.dp)), contentAlignment = Alignment.Center) {
                Box(Modifier.size(9.dp).border(2.dp, Color.White, CircleShape))
            }
        }
    }
}

@Composable
private fun FeedCard(s: BrowserState, f: Feed) {
    Surface(
        onClick = { s.open(f.query) },
        shape = RoundedCornerShape(24.dp),
        color = Color.White.copy(alpha = 0.9f),
        shadowElevation = 2.dp,
        modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp)
    ) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(width = 104.dp, height = 84.dp).clip(RoundedCornerShape(18.dp))
                    .background(Brush.linearGradient(listOf(f.c1, f.c2))),
                contentAlignment = Alignment.Center
            ) { Text(f.emoji, fontSize = 38.sp) }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    f.title, color = C.Ink, fontSize = 16.sp, fontWeight = FontWeight.Medium,
                    maxLines = 3, overflow = TextOverflow.Ellipsis, lineHeight = 21.sp
                )
                Spacer(Modifier.height(6.dp))
                Text(f.tag, color = C.Muted, fontSize = 13.sp)
            }
            IconButton(onClick = { s.newTab(f.query) }) {
                Icon(Icons.Filled.MoreVert, "Открыть в новой вкладке", tint = C.Muted)
            }
        }
    }
}

// ============================================================ страница

@Composable
fun BrowserScreen(s: BrowserState, t: TabState) {
    Column(Modifier.fillMaxSize().statusBarsPadding()) {
        SearchPill(s, big = false, modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp))
        Box(Modifier.fillMaxWidth().height(3.dp)) {
            if (t.loading) {
                LinearProgressIndicator(
                    progress = { t.progress },
                    modifier = Modifier.fillMaxSize(),
                    color = C.Violet,
                    trackColor = Color.Transparent
                )
            }
        }
        key(t.id) {
            AndroidView(
                factory = {
                    val wv = t.webView!!
                    (wv.parent as? ViewGroup)?.removeView(wv)
                    wv
                },
                modifier = Modifier.weight(1f).fillMaxWidth()
            )
        }
    }
}

// ============================================================ вкладки

@Composable
fun TabsScreen(s: BrowserState) {
    Column(Modifier.fillMaxSize().statusBarsPadding().padding(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Вкладки", color = C.Ink, fontSize = 26.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
            Surface(
                onClick = { s.newTab() },
                shape = RoundedCornerShape(20.dp),
                color = Color.Transparent,
                modifier = Modifier.background(C.Brand, RoundedCornerShape(20.dp))
            ) {
                Row(Modifier.padding(horizontal = 16.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.Add, null, tint = Color.White, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Новая", color = Color.White, fontWeight = FontWeight.SemiBold)
                }
            }
        }
        Spacer(Modifier.height(14.dp))
        LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            itemsIndexed(s.tabs, key = { _, t -> t.id }) { i, t ->
                Surface(
                    onClick = { s.index = i; s.showTabs = false },
                    shape = RoundedCornerShape(20.dp),
                    color = Color.White,
                    border = if (i == s.index) BorderStroke(2.dp, C.Violet) else null,
                    shadowElevation = 2.dp
                ) {
                    Row(Modifier.padding(start = 18.dp, top = 8.dp, bottom = 8.dp, end = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(
                                if (t.url.isBlank()) "Новая вкладка" else t.title,
                                color = C.Ink, fontSize = 16.sp, fontWeight = FontWeight.SemiBold,
                                maxLines = 1, overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                if (t.url.isBlank()) "HerБраузер" else (Uri.parse(t.url).host ?: t.url),
                                color = C.Muted, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis
                            )
                        }
                        IconButton(onClick = { s.closeTab(i) }) {
                            Icon(Icons.Filled.Close, "Закрыть вкладку", tint = C.Muted)
                        }
                    }
                }
            }
        }
    }
}

// ============================================================ нижняя панель

@Composable
fun BottomBar(s: BrowserState) {
    val t = s.tab
    val canBack = s.showTabs || !t.showHome || s.tabs.size > 1
    val canFwd = !s.showTabs && (if (t.showHome) t.hasPage else t.canGoForward)
    Surface(
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        color = Color.White.copy(alpha = 0.95f),
        shadowElevation = 12.dp
    ) {
        Row(
            Modifier.fillMaxWidth().navigationBarsPadding().padding(top = 8.dp, bottom = 6.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.Top
        ) {
            NavItem("Назад", canBack, { s.back() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, null, tint = it) }
            NavItem("Вперёд", canFwd, { s.forward() }) { Icon(Icons.AutoMirrored.Filled.ArrowForward, null, tint = it) }
            Column(
                Modifier.width(68.dp).clip(RoundedCornerShape(16.dp)).clickable {
                    s.showTabs = false
                    s.showMenu = false
                    s.searchFocus++
                },
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Box(Modifier.size(56.dp).clip(CircleShape).background(C.Tint), contentAlignment = Alignment.Center) {
                    Icon(Icons.Filled.Search, null, tint = C.Violet, modifier = Modifier.size(28.dp))
                }
                Text("Поиск", color = C.Muted, fontSize = 12.sp)
            }
            NavItem("Вкладки", true, { s.showTabs = !s.showTabs }) {
                Box(Modifier.size(24.dp).border(2.dp, it, RoundedCornerShape(7.dp)), contentAlignment = Alignment.Center) {
                    Text("${s.tabs.size}", color = it, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
            }
            NavItem("Меню", true, { s.showMenu = true }) { Icon(Icons.Filled.Menu, null, tint = it) }
        }
    }
}

@Composable
private fun NavItem(label: String, enabled: Boolean, onClick: () -> Unit, icon: @Composable (Color) -> Unit) {
    val tint = if (enabled) C.Violet else C.Muted.copy(alpha = 0.45f)
    Column(
        Modifier.width(68.dp).clip(RoundedCornerShape(16.dp)).clickable(enabled = enabled, onClick = onClick).padding(vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(Modifier.size(40.dp), contentAlignment = Alignment.Center) { icon(tint) }
        Text(label, color = if (enabled) C.Muted else tint, fontSize = 12.sp)
    }
}

// ============================================================ меню, избранное, диалоги

@Composable
private fun MenuRow(icon: androidx.compose.ui.graphics.vector.ImageVector, text: String, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 24.dp, vertical = 15.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, null, tint = C.Violet)
        Spacer(Modifier.width(18.dp))
        Text(text, color = C.Ink, fontSize = 17.sp)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MenuSheet(s: BrowserState) {
    val t = s.tab
    val onPage = !t.showHome && t.url.isNotBlank()
    ModalBottomSheet(
        onDismissRequest = { s.showMenu = false },
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = Color.White
    ) {
        Column(Modifier.padding(bottom = 24.dp)) {
            MenuRow(Icons.Filled.Add, "Новая вкладка") { s.showMenu = false; s.newTab() }
            if (onPage) {
                MenuRow(Icons.Filled.Refresh, "Обновить") { s.showMenu = false; s.reload() }
                val fav = s.isFav(t.url)
                MenuRow(
                    if (fav) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                    if (fav) "Убрать из избранного" else "Добавить в избранное"
                ) { s.showMenu = false; s.toggleFav() }
                MenuRow(Icons.Filled.Share, "Поделиться") { s.showMenu = false; s.share() }
            }
            MenuRow(Icons.Filled.Favorite, "Избранное") { s.showMenu = false; s.showFavs = true }
            MenuRow(Icons.Filled.Lock, "Разрешения") { s.showMenu = false; s.showPerms = true }
            MenuRow(Icons.Filled.Info, "О приложении: HerБраузер 1.0") { s.showMenu = false; s.toast("HerБраузер 1.0 — быстрый, удобный, для тебя") }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FavsSheet(s: BrowserState) {
    ModalBottomSheet(
        onDismissRequest = { s.showFavs = false },
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = Color.White
    ) {
        Text("Избранное", color = C.Ink, fontSize = 22.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp))
        if (s.favorites.isEmpty()) {
            Text(
                "Здесь пока пусто. Откройте сайт и выберите «Меню → Добавить в избранное».",
                color = C.Muted, modifier = Modifier.padding(start = 24.dp, end = 24.dp, top = 8.dp, bottom = 40.dp)
            )
        } else {
            LazyColumn(Modifier.heightIn(max = 420.dp).padding(bottom = 24.dp)) {
                items(s.favorites) { f ->
                    Row(
                        Modifier.fillMaxWidth().clickable { s.showFavs = false; s.open(f.url) }.padding(start = 24.dp, end = 8.dp, top = 6.dp, bottom = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Filled.Favorite, null, tint = Color(0xFFB04CE0))
                        Spacer(Modifier.width(16.dp))
                        Column(Modifier.weight(1f)) {
                            Text(f.title, color = C.Ink, fontSize = 16.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(Uri.parse(f.url).host ?: f.url, color = C.Muted, fontSize = 13.sp, maxLines = 1)
                        }
                        IconButton(onClick = { s.removeFav(f) }) { Icon(Icons.Filled.Close, "Удалить", tint = C.Muted) }
                    }
                }
            }
        }
    }
}

@Composable
private fun AddLinkDialog(s: BrowserState) {
    var name by remember { mutableStateOf("") }
    var url by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = { s.showAdd = false },
        containerColor = Color.White,
        title = { Text("Добавить ссылку") },
        text = {
            Column {
                OutlinedTextField(name, { name = it }, label = { Text("Название") }, singleLine = true)
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(url, { url = it }, label = { Text("Адрес сайта") }, singleLine = true)
            }
        },
        confirmButton = { TextButton(onClick = { s.addLink(name, url); s.showAdd = false }) { Text("Добавить") } },
        dismissButton = { TextButton(onClick = { s.showAdd = false }) { Text("Отмена") } }
    )
}

@Composable
private fun PermsDialog(s: BrowserState) {
    key(s.permTick) {
        val rows = listOf(
            Triple("📷  Камера", Perms.camera, false),
            Triple("🎙  Микрофон", Perms.mic, false),
            Triple("📍  Местоположение", Perms.location, true),
            Triple("🔔  Уведомления", Perms.notifications, false),
            Triple("📡  Устройства рядом", Perms.nearby, false),
        )
        AlertDialog(
            onDismissRequest = { s.dismissPerms() },
            containerColor = Color.White,
            title = { Text("Разрешения") },
            text = {
                Column {
                    Text(
                        "Нужны сайтам для видеозвонков, голосового ввода, карт и уведомлений.",
                        color = C.Muted, fontSize = 14.sp
                    )
                    Spacer(Modifier.height(12.dp))
                    rows.forEach { (name, perms, any) ->
                        val on = if (any) s.hasAny(perms) else s.has(perms)
                        Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text(name, color = C.Ink, modifier = Modifier.weight(1f))
                            Text(
                                if (on) "Включено" else "Выключено",
                                color = if (on) Color(0xFF2E9E6A) else C.Muted,
                                fontSize = 13.sp, fontWeight = FontWeight.Medium
                            )
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { s.requestAll() }) { Text("Разрешить всё") } },
            dismissButton = {
                Row {
                    TextButton(onClick = { s.openAppSettings() }) { Text("Настройки") }
                    TextButton(onClick = { s.dismissPerms() }) { Text("Закрыть") }
                }
            }
        )
    }
}
