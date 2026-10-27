package com.herbrowser.app;

import android.Manifest;
import android.animation.ObjectAnimator;
import android.animation.PropertyValuesHolder;
import android.animation.ValueAnimator;
import android.app.Dialog;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ShortcutInfo;
import android.content.pm.ShortcutManager;
import android.content.res.ColorStateList;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Outline;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.Icon;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.StrictMode;
import android.provider.Settings;
import android.speech.RecognizerIntent;
import android.text.Editable;
import android.text.InputType;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.text.style.ForegroundColorSpan;
import android.util.DisplayMetrics;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewOutlineProvider;
import android.view.Window;
import android.view.WindowManager;
import android.view.animation.AccelerateDecelerateInterpolator;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.OvershootInterpolator;
import android.view.inputmethod.EditorInfo;
import android.webkit.CookieManager;
import android.webkit.GeolocationPermissions;
import android.webkit.JavascriptInterface;
import android.webkit.PermissionRequest;
import android.webkit.RenderProcessGoneDetail;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.List;

public class MainActivity extends Screens {

    static final String MSG_OFFLINE = "Нет подключения к интернету, включите Wi-Fi или мобильный интернет";
    static final String MSG_WHITELIST = "Вы не можете зайти на иностранный сайт или наши во время белого списка, заходите на российские сайты";
    static final String MSG_OTHER = "Не удалось загрузить страницу. Проверьте адрес и попробуйте ещё раз";
    static final String MSG_CHECK = "Проверяем соединение…";

    static final String DESKTOP_UA = "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36";

    /** Подмена Notification API: сайты и мессенджеры отправляют уведомления через браузер. */
    static final String SHIM =
            "(function(){if(window.__her)return;window.__her=1;"
                    + "function N(t,o){try{HerNotify.show(String(t),(o&&o.body)?String(o.body):'');}catch(e){}this.close=function(){};this.onclick=null;}"
                    + "N.permission=HerNotify.state();"
                    + "N.requestPermission=function(cb){return new Promise(function(res){"
                    + "window.__herPerm=function(r){N.permission=r;if(cb)cb(r);res(r);};"
                    + "var s=HerNotify.state();if(s!=='default'){window.__herPerm(s);}else{HerNotify.ask();}});};"
                    + "try{Object.defineProperty(window,'Notification',{value:N,configurable:true,writable:true});}catch(e){window.Notification=N;}"
                    + "})();";

    FrameLayout content;
    ScrollView homeView;
    LinearLayout topBar, shortcutsRow, errorView, findBar;
    EditText urlBar, homeSearch, findInput;
    ProgressBar progress;
    TextView privLabel, errIcon, errMsg, tabsBox, reloadBtn;
    Ico backBtn, fwdBtn;
    ObjectAnimator logoAnim;
    Runnable pendingGrant, pendingDeny, permRefresh;
    long lastNotify;
    LinearLayout searchRow, notifBanner;
    TextView notifText, notifBtn;
    final List<View> fadeViews = new ArrayList<View>();
    boolean searchActive, kbSeen, notifDismissed, notifAsked, notifWasOff;

    // ---------------------------------------------------------------- жизненный цикл

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        store = new Store(this);
        applyTheme();
        makeChannel();
        if (Build.VERSION.SDK_INT >= 24 && Build.VERSION.SDK_INT < 29) {
            StrictMode.setVmPolicy(new StrictMode.VmPolicy.Builder().build());
        }
        buildUi();
        restoreTabs();
        handleIntent(getIntent());
        if (!welcomeFile().exists()) showWelcome();
    }

    @Override
    protected void onNewIntent(Intent i) {
        super.onNewIntent(i);
        handleIntent(i);
    }

    @Override
    protected void onPause() {
        saveTabs();
        super.onPause();
    }

    @Override
    protected void onResume() {
        super.onResume();
        updateNotifBanner();
    }

    @Override
    protected void onActivityResult(int req, int res, Intent data) {
        super.onActivityResult(req, res, data);
        if (req == 77 && res == RESULT_OK && data != null) {
            ArrayList<String> r = data.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS);
            if (r != null && !r.isEmpty()) yandex(r.get(0));
        }
    }

    @Override
    protected void onDestroy() {
        if (logoAnim != null) logoAnim.cancel();
        super.onDestroy();
    }

    @Override
    public void onTrimMemory(int level) {
        super.onTrimMemory(level);
        if (level >= TRIM_MEMORY_RUNNING_LOW) {
            for (Tab t : tabs) if (t != cur) t.thumb = null;
        }
    }

    void handleIntent(Intent i) {
        Uri d = i.getData();
        if (d == null || cur == null) return;
        boolean blank = cur.home && cur.web.getUrl() == null && cur.pendingUrl == null;
        if (i.getBooleanExtra("newtab", false) && !blank) newTab(false);
        go(d.toString());
    }

    @Override
    public void onBackPressed() {
        if (searchActive) {
            closeSearch();
            return;
        }
        if (findBar.getVisibility() == View.VISIBLE) {
            closeFind();
            return;
        }
        if (doBack()) return;
        if (tabs.size() > 1) {
            closeTab(cur);
            return;
        }
        finish();
    }

    @Override
    public void onRequestPermissionsResult(int code, String[] perms, int[] res) {
        super.onRequestPermissionsResult(code, perms, res);
        if (code >= 10 && code < 20 && permRefresh != null) permRefresh.run();
        if (code == 3) updateNotifBanner();
        if (code != 2) return;
        boolean ok = res.length > 0;
        for (int r : res) if (r != PackageManager.PERMISSION_GRANTED) ok = false;
        Runnable g = pendingGrant;
        Runnable dn = pendingDeny;
        pendingGrant = null;
        pendingDeny = null;
        if (ok && g != null) g.run();
        else if (dn != null) dn.run();
    }

    @Override
    void reloadMainUi() {
        content.removeAllViews();
        buildUi();
        select(cur);
    }

    // ---------------------------------------------------------------- приветствие и разрешения

    File welcomeFile() {
        return new File(getCacheDir(), "welcome_done");
    }

    void finishWelcome() {
        try {
            welcomeFile().createNewFile();
        } catch (Exception e) {
            // не страшно
        }
    }

    /** Аватарка появляется, поднимается, затем текст, затем кнопка «Продолжить». */
    void showWelcome() {
        final Dialog dlg = new Dialog(this);
        dlg.requestWindowFeature(Window.FEATURE_NO_TITLE);

        LinearLayout c = new LinearLayout(this);
        c.setOrientation(LinearLayout.VERTICAL);
        c.setGravity(Gravity.CENTER_HORIZONTAL);
        c.setPadding(dp(24), dp(28), dp(24), dp(24));
        c.setBackground(box(dark ? 0xFF211E33 : 0xFFF5EEFF, 28));

        final TextView ic = tv("\u2661", 44, brand);
        ic.setGravity(Gravity.CENTER);
        ic.setBackground(box(dark ? 0xFF3A3560 : 0xFFE6D6FF, 28));
        c.addView(ic, new LinearLayout.LayoutParams(dp(104), dp(104)));

        TextView t1 = tv("Добро пожаловать!", 24, text);
        t1.setTypeface(null, Typeface.BOLD);
        t1.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams p1 = new LinearLayout.LayoutParams(WRAP, WRAP);
        p1.topMargin = dp(20);
        c.addView(t1, p1);

        TextView t2 = tv("HerБраузер", 20, brand);
        t2.setTypeface(null, Typeface.BOLD);
        LinearLayout.LayoutParams p2 = new LinearLayout.LayoutParams(WRAP, WRAP);
        p2.topMargin = dp(10);
        c.addView(t2, p2);

        TextView t3 = tv("Добро пожаловать в наше приложение HerБраузер! Это браузер, созданный для твоего удобства. Желаем приятного и комфортного пользования!", 16, sub);
        t3.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams p3 = new LinearLayout.LayoutParams(WRAP, WRAP);
        p3.topMargin = dp(14);
        c.addView(t3, p3);

        TextView t4 = tv("Приятного просмотра! \uD83D\uDC9C", 16, brand);
        LinearLayout.LayoutParams p4 = new LinearLayout.LayoutParams(WRAP, WRAP);
        p4.topMargin = dp(14);
        c.addView(t4, p4);

        final TextView btn = tv("Продолжить", 17, Color.WHITE);
        btn.setTypeface(null, Typeface.BOLD);
        btn.setGravity(Gravity.CENTER);
        GradientDrawable bgBtn = new GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT,
                new int[]{0xFFFF5FA8, brand});
        bgBtn.setCornerRadius(dp(26));
        btn.setBackground(bgBtn);
        rip(btn, 26);
        press(btn);
        btn.setOnClickListener(v -> {
            dlg.dismiss();
            showPermScreen();
        });
        LinearLayout.LayoutParams pb = new LinearLayout.LayoutParams(MATCH, dp(52));
        pb.topMargin = dp(22);
        c.addView(btn, pb);

        dlg.setContentView(c);
        dlg.setCancelable(false);
        Window w = dlg.getWindow();
        if (w != null) {
            w.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            DisplayMetrics dm = getResources().getDisplayMetrics();
            w.setLayout((int) (dm.widthPixels * 0.9f), WRAP);
        }

        final TextView[] items = {t1, t2, t3, t4};
        ic.setAlpha(0f);
        ic.setScaleX(0.5f);
        ic.setScaleY(0.5f);
        ic.setTranslationY(dp(120));
        for (TextView t : items) {
            t.setAlpha(0f);
            t.setTranslationY(dp(18));
        }
        btn.setAlpha(0f);
        btn.setEnabled(false);
        dlg.show();
        ic.animate().alpha(1f).scaleX(1f).scaleY(1f).setDuration(500)
                .setInterpolator(new OvershootInterpolator(1.6f))
                .withEndAction(() -> ic.animate().translationY(0f).setStartDelay(300).setDuration(550)
                        .setInterpolator(new DecelerateInterpolator())
                        .withEndAction(() -> {
                            for (int i = 0; i < items.length; i++) {
                                items[i].animate().alpha(1f).translationY(0f)
                                        .setStartDelay(i * 130L).setDuration(420).start();
                            }
                            btn.animate().alpha(1f).setStartDelay(700).setDuration(400)
                                    .withEndAction(() -> btn.setEnabled(true)).start();
                        }).start())
                .start();
    }

    String[] permList(int i) {
        switch (i) {
            case 0: return new String[]{Manifest.permission.CAMERA};
            case 1: return new String[]{"android.permission.POST_NOTIFICATIONS"};
            case 2: return new String[]{Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION};
            default: return new String[]{Manifest.permission.RECORD_AUDIO};
        }
    }

    boolean permGranted(int i) {
        if (i == 1 && Build.VERSION.SDK_INT < 33) return true;
        for (String p : permList(i)) if (!has(p)) return false;
        return true;
    }

    void showPermScreen() {
        final Dialog d = fullScreen();
        d.setCancelable(false);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(bg);
        root.setFitsSystemWindows(true);

        LinearLayout tb = new LinearLayout(this);
        tb.setGravity(Gravity.CENTER_VERTICAL);
        tb.setPadding(dp(20), dp(10), dp(12), dp(6));
        TextView title = tv("Разрешения", 22, text);
        title.setTypeface(null, Typeface.BOLD);
        tb.addView(title, new LinearLayout.LayoutParams(0, WRAP, 1f));
        TextView skip = tv("Пропустить", 15, accent);
        skip.setPadding(dp(14), dp(10), dp(14), dp(10));
        press(skip);
        skip.setOnClickListener(v -> {
            finishWelcome();
            permRefresh = null;
            d.dismiss();
        });
        tb.addView(skip);
        root.addView(tb, new LinearLayout.LayoutParams(MATCH, WRAP));

        TextView intro = tv("Чтобы сайты и мессенджеры работали полностью, разрешите доступ. Это можно сделать позже.", 14, sub);
        intro.setPadding(dp(20), 0, dp(20), dp(10));
        root.addView(intro);

        final LinearLayout list = new LinearLayout(this);
        list.setOrientation(LinearLayout.VERTICAL);
        list.setPadding(dp(16), 0, dp(16), 0);
        ScrollView sv = new ScrollView(this);
        sv.addView(list, new ViewGroup.LayoutParams(MATCH, WRAP));
        root.addView(sv, new LinearLayout.LayoutParams(MATCH, 0, 1f));

        final String[] g = {"\uD83D\uDCF7", "\uD83D\uDD14", "\uD83D\uDCCD", "\uD83C\uDFA4"};
        final String[] n = {"Камера", "Уведомления", "Местоположение", "Микрофон"};
        final String[] ds = {"Фото и видеозвонки на сайтах", "Сообщения и загрузки",
                "Карты и погода рядом", "Голосовые сообщения и звонки"};
        final Runnable refresh = () -> {
            list.removeAllViews();
            for (int i = 0; i < 4; i++) {
                final int idx = i;
                LinearLayout row = new LinearLayout(this);
                row.setGravity(Gravity.CENTER_VERTICAL);
                row.setPadding(dp(12), dp(12), dp(12), dp(12));
                row.setBackground(box(card, 18));
                TextView ic = tv(g[i], 22, accent);
                ic.setGravity(Gravity.CENTER);
                ic.setBackground(box(soft, 14));
                row.addView(ic, new LinearLayout.LayoutParams(dp(48), dp(48)));
                LinearLayout tx = new LinearLayout(this);
                tx.setOrientation(LinearLayout.VERTICAL);
                tx.addView(tv(n[i], 17, text));
                tx.addView(tv(ds[i], 13, sub));
                LinearLayout.LayoutParams txp = new LinearLayout.LayoutParams(0, WRAP, 1f);
                txp.leftMargin = dp(14);
                row.addView(tx, txp);
                if (permGranted(i)) {
                    row.addView(tv("\u2713", 22, accent));
                } else {
                    TextView btn = tv("Разрешить", 13, Color.WHITE);
                    btn.setTypeface(null, Typeface.BOLD);
                    btn.setBackground(box(brand, 16));
                    btn.setPadding(dp(14), dp(8), dp(14), dp(8));
                    rip(btn, 16);
                    press(btn);
                    btn.setOnClickListener(v -> ask(permList(idx), 10 + idx));
                    row.addView(btn);
                }
                LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(MATCH, WRAP);
                lp.bottomMargin = dp(10);
                list.addView(row, lp);
                row.setAlpha(0f);
                row.setTranslationY(dp(16));
                row.animate().alpha(1f).translationY(0f).setStartDelay(i * 70L).setDuration(300).start();
            }
        };
        permRefresh = refresh;
        refresh.run();

        TextView next = tv("Продолжить", 17, Color.WHITE);
        next.setTypeface(null, Typeface.BOLD);
        next.setGravity(Gravity.CENTER);
        next.setBackground(box(brand, 26));
        rip(next, 26);
        press(next);
        next.setOnClickListener(v -> {
            finishWelcome();
            permRefresh = null;
            d.dismiss();
        });
        LinearLayout.LayoutParams gp = new LinearLayout.LayoutParams(MATCH, dp(54));
        gp.setMargins(dp(20), dp(8), dp(20), dp(20));
        root.addView(next, gp);

        d.setContentView(root);
        d.show();
        root.setAlpha(0f);
        root.setTranslationX(dp(40));
        root.animate().alpha(1f).translationX(0f).setDuration(300)
                .setInterpolator(new DecelerateInterpolator()).start();
    }

    /** Не спрашиваем, если разрешение уже включено. Если выключено: спрашиваем, можно отметить «Больше не спрашивать». */
    void askSite(final String host, final String kind, String what, final String[] perms,
                 final Runnable grant, final Runnable deny) {
        boolean all = true;
        for (String p : perms) if (!has(p)) all = false;
        if (all) {
            grant.run();
            return;
        }
        String saved = sitePermState(host, kind);
        if ("0".equals(saved)) {
            deny.run();
            return;
        }
        if ("1".equals(saved)) {
            pendingGrant = grant;
            pendingDeny = deny;
            ask(perms, 2);
            return;
        }
        final boolean[] never = {false};
        builder().setTitle("Сайт " + host + " запрашивает " + what)
                .setMultiChoiceItems(new String[]{"Больше не спрашивать"}, never, (d, i, c) -> never[0] = c)
                .setPositiveButton("Разрешить", (d, w) -> {
                    if (never[0]) saveSitePerm(host, kind, true);
                    pendingGrant = grant;
                    pendingDeny = deny;
                    ask(perms, 2);
                })
                .setNegativeButton("Запретить", (d, w) -> {
                    if (never[0]) saveSitePerm(host, kind, false);
                    deny.run();
                })
                .setOnCancelListener(d -> deny.run())
                .show();
    }

    // ---------------------------------------------------------------- уведомления сайтов и мессенджеров

    void makeChannel() {
        if (Build.VERSION.SDK_INT >= 26) {
            NotificationChannel ch = new NotificationChannel("sites", "Уведомления сайтов",
                    NotificationManager.IMPORTANCE_DEFAULT);
            NotificationManager nm = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
            if (nm != null) nm.createNotificationChannel(ch);
        }
    }

    void toggleSiteNotif(String host) {
        boolean on = "granted".equals(siteState(host));
        setSiteState(host, !on);
        if (!on && Build.VERSION.SDK_INT >= 33 && !has("android.permission.POST_NOTIFICATIONS")) {
            ask(new String[]{"android.permission.POST_NOTIFICATIONS"}, 3);
        }
        toast(on ? "Уведомления сайта выключены" : "Уведомления сайта включены");
    }

    void askNotify(final String host, final Tab t) {
        if (isFinishing()) return;
        builder().setTitle("Уведомления")
                .setMessage("Сайт " + host + " хочет показывать уведомления. Разрешить?")
                .setPositiveButton("Разрешить", (d, w) -> {
                    setSiteState(host, true);
                    if (Build.VERSION.SDK_INT >= 33 && !has("android.permission.POST_NOTIFICATIONS")) {
                        ask(new String[]{"android.permission.POST_NOTIFICATIONS"}, 3);
                    }
                    resolvePerm(t, "granted");
                })
                .setNegativeButton("Запретить", (d, w) -> {
                    setSiteState(host, false);
                    resolvePerm(t, "denied");
                })
                .setOnCancelListener(d -> resolvePerm(t, "default"))
                .show();
    }

    void resolvePerm(Tab t, String r) {
        if (tabs.contains(t)) {
            t.web.evaluateJavascript("window.__herPerm&&window.__herPerm('" + r + "')", null);
        }
    }

    void postSiteNotification(String host, String title, String body, String url) {
        if (Build.VERSION.SDK_INT >= 33 && !has("android.permission.POST_NOTIFICATIONS")) return;
        Intent i = new Intent(this, MainActivity.class);
        i.setAction(Intent.ACTION_VIEW);
        i.setData(Uri.parse(url));
        i.putExtra("newtab", true);
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        PendingIntent pi = PendingIntent.getActivity(this, host.hashCode(), i,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        Notification.Builder nb = Build.VERSION.SDK_INT >= 26
                ? new Notification.Builder(this, "sites")
                : new Notification.Builder(this);
        nb.setSmallIcon(R.drawable.ic_launcher)
                .setContentTitle(title.isEmpty() ? host : title)
                .setContentText(body.isEmpty() ? host : body)
                .setSubText(host)
                .setContentIntent(pi)
                .setAutoCancel(true);
        NotificationManager nm = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        if (nm != null) nm.notify((int) (System.currentTimeMillis() & 0x7fffffff), nb.build());
    }

    class NotifBridge {
        final Tab t;

        NotifBridge(Tab t) {
            this.t = t;
        }

        String realHost() {
            return hostOf(t.lastUrl);
        }

        @JavascriptInterface
        public String state() {
            return siteState(realHost());
        }

        @JavascriptInterface
        public void ask() {
            final String h = realHost();
            if (h.isEmpty()) return;
            runOnUiThread(() -> askNotify(h, t));
        }

        @JavascriptInterface
        public void show(String title, String body) {
            final String h = realHost();
            if (h.isEmpty() || !"granted".equals(siteState(h))) return;
            long now = System.currentTimeMillis();
            if (now - lastNotify < 1500) return;
            lastNotify = now;
            final String tt = clip(title, 100);
            final String bb = clip(body, 200);
            final String url = t.lastUrl;
            runOnUiThread(() -> postSiteNotification(h, tt, bb, url));
        }
    }

    // ---------------------------------------------------------------- «Главный экран (Домой)»

    Bitmap makeIcon(String label, Bitmap fav) {
        int s = 216;
        Bitmap b = Bitmap.createBitmap(s, s, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(b);
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
        if (fav != null) {
            p.setColor(Color.WHITE);
            c.drawRect(0, 0, s, s, p);
            int f = (int) (s * 0.5f);
            Bitmap sc = Bitmap.createScaledBitmap(fav, f, f, true);
            c.drawBitmap(sc, (s - f) / 2f, (s - f) / 2f, p);
        } else {
            p.setColor(avatarColor(label));
            c.drawRect(0, 0, s, s, p);
            p.setColor(Color.WHITE);
            p.setTextSize(s * 0.4f);
            p.setTextAlign(Paint.Align.CENTER);
            p.setTypeface(Typeface.DEFAULT_BOLD);
            c.drawText(firstLetter(label), s / 2f, s / 2f - (p.descent() + p.ascent()) / 2f, p);
        }
        return b;
    }

    void pinShortcut(String url, String label, Bitmap fav) {
        Intent si = new Intent(this, MainActivity.class);
        si.setAction(Intent.ACTION_VIEW);
        si.setData(Uri.parse(url));
        si.putExtra("newtab", true);
        if (Build.VERSION.SDK_INT < 26) {
            Intent add = new Intent("com.android.launcher.action.INSTALL_SHORTCUT");
            add.putExtra(Intent.EXTRA_SHORTCUT_INTENT, si);
            add.putExtra(Intent.EXTRA_SHORTCUT_NAME, label);
            add.putExtra(Intent.EXTRA_SHORTCUT_ICON, makeIcon(label, fav));
            sendBroadcast(add);
            toast("Ярлык добавлен на рабочий стол");
            return;
        }
        ShortcutManager sm = (ShortcutManager) getSystemService(Context.SHORTCUT_SERVICE);
        if (sm == null) {
            toast("Не удалось создать ярлык");
            return;
        }
        ShortcutInfo info = new ShortcutInfo.Builder(this, "site_" + Integer.toHexString(url.hashCode()))
                .setShortLabel(label)
                .setLongLabel(label)
                .setIcon(Icon.createWithAdaptiveBitmap(makeIcon(label, fav)))
                .setIntent(si)
                .build();
        if (sm.isRequestPinShortcutSupported()) {
            sm.requestPinShortcut(info, null);
            Toast.makeText(this, "Подтвердите добавление ярлыка на рабочий стол. Если он не появился, разрешите «Ярлыки на рабочем столе» в настройках приложения.", Toast.LENGTH_LONG).show();
        } else {
            sm.addDynamicShortcuts(java.util.Collections.singletonList(info));
            Toast.makeText(this, "Рабочий стол не принимает ярлыки напрямую: ярлык появится при долгом нажатии на значок HerБраузера", Toast.LENGTH_LONG).show();
        }
    }

    void addToHome() {
        final String url = cur.web.getUrl();
        if (cur.home || url == null) {
            toast("Сначала откройте сайт или мессенджер");
            return;
        }
        final String host = hostOf(url);
        String title = cur.web.getTitle();
        final String label = (title != null && !title.isEmpty() && title.length() <= 24)
                ? title : host.replace("www.", "");
        final Bitmap fav = cur.favicon;
        final boolean[] notif = {!"denied".equals(siteState(host))};
        builder().setTitle("Главный экран (Домой)")
                .setMultiChoiceItems(new String[]{"Уведомления от этого сайта"}, notif,
                        (d, i, checked) -> notif[0] = checked)
                .setPositiveButton("Добавить", (d, w) -> {
                    setSiteState(host, notif[0]);
                    if (notif[0] && Build.VERSION.SDK_INT >= 33 && !has("android.permission.POST_NOTIFICATIONS")) {
                        ask(new String[]{"android.permission.POST_NOTIFICATIONS"}, 3);
                    }
                    pinShortcut(url, label, fav);
                })
                .setNegativeButton("Отмена", null)
                .show();
    }

    // ---------------------------------------------------------------- построение интерфейса

    void buildUi() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(bg);
        root.setFitsSystemWindows(true);

        topBar = new LinearLayout(this);
        topBar.setOrientation(LinearLayout.VERTICAL);
        topBar.setVisibility(View.GONE);

        LinearLayout row = new LinearLayout(this);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(12), dp(8), dp(4), dp(6));
        urlBar = field("Введите адрес или поисковый запрос");
        row.addView(urlBar, new LinearLayout.LayoutParams(0, WRAP, 1f));

        reloadBtn = tv("\u21BB", 24, accent);
        reloadBtn.setGravity(Gravity.CENTER);
        reloadBtn.setBackground(circle(light));
        rip(reloadBtn, 60);
        press(reloadBtn);
        reloadBtn.setOnClickListener(v -> reload());
        LinearLayout.LayoutParams rlp = new LinearLayout.LayoutParams(dp(44), dp(44));
        rlp.leftMargin = dp(8);
        row.addView(reloadBtn, rlp);

        Ico dots = new Ico(this, Ico.DOTS, accent);
        press(dots);
        dots.setOnClickListener(v -> showPageMenu());
        row.addView(dots, new LinearLayout.LayoutParams(dp(44), dp(44)));
        topBar.addView(row, new LinearLayout.LayoutParams(MATCH, WRAP));

        progress = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
        progress.setMax(100);
        progress.setProgressTintList(ColorStateList.valueOf(accent));
        progress.setVisibility(View.GONE);
        topBar.addView(progress, new LinearLayout.LayoutParams(MATCH, dp(3)));
        root.addView(topBar, new LinearLayout.LayoutParams(MATCH, WRAP));

        findBar = new LinearLayout(this);
        findBar.setGravity(Gravity.CENTER_VERTICAL);
        findBar.setBackgroundColor(bar);
        findBar.setPadding(dp(14), dp(4), dp(6), dp(4));
        findBar.setVisibility(View.GONE);
        findInput = new EditText(this);
        findInput.setHint("Найти на странице");
        findInput.setSingleLine(true);
        findInput.setTextSize(15);
        findInput.setTextColor(text);
        findInput.setHintTextColor(sub);
        findInput.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int a, int b, int c) {
            }

            @Override
            public void onTextChanged(CharSequence s, int a, int b, int c) {
            }

            @Override
            public void afterTextChanged(Editable s) {
                if (cur != null) cur.web.findAllAsync(s.toString());
            }
        });
        findBar.addView(findInput, new LinearLayout.LayoutParams(0, WRAP, 1f));
        findBar.addView(actionBtn("\u25B2", v -> {
            if (cur != null) cur.web.findNext(false);
        }), new LinearLayout.LayoutParams(dp(44), dp(44)));
        findBar.addView(actionBtn("\u25BC", v -> {
            if (cur != null) cur.web.findNext(true);
        }), new LinearLayout.LayoutParams(dp(44), dp(44)));
        findBar.addView(actionBtn("\u2715", v -> closeFind()), new LinearLayout.LayoutParams(dp(44), dp(44)));
        root.addView(findBar, new LinearLayout.LayoutParams(MATCH, WRAP));

        notifBanner = new LinearLayout(this);
        notifBanner.setGravity(Gravity.CENTER_VERTICAL);
        notifBanner.setBackground(box(soft, 14));
        notifBanner.setPadding(dp(12), dp(10), dp(6), dp(10));
        notifBanner.setVisibility(View.GONE);
        notifText = tv("", 13, text);
        notifBanner.addView(notifText, new LinearLayout.LayoutParams(0, WRAP, 1f));
        notifBtn = tv("Разрешить", 13, Color.WHITE);
        notifBtn.setTypeface(null, Typeface.BOLD);
        notifBtn.setBackground(box(brand, 16));
        notifBtn.setPadding(dp(14), dp(8), dp(14), dp(8));
        rip(notifBtn, 16);
        press(notifBtn);
        notifBtn.setOnClickListener(v -> askNotifPermission());
        LinearLayout.LayoutParams nbp = new LinearLayout.LayoutParams(WRAP, WRAP);
        nbp.leftMargin = dp(8);
        notifBanner.addView(notifBtn, nbp);
        TextView nx = tv("\u2715", 16, sub);
        nx.setPadding(dp(10), dp(6), dp(8), dp(6));
        nx.setOnClickListener(v -> {
            notifDismissed = true;
            notifBanner.setVisibility(View.GONE);
        });
        notifBanner.addView(nx);
        LinearLayout.LayoutParams nlp = new LinearLayout.LayoutParams(MATCH, WRAP);
        nlp.setMargins(dp(10), dp(6), dp(10), dp(2));
        root.addView(notifBanner, nlp);

        content = new FrameLayout(this);
        root.addView(content, new LinearLayout.LayoutParams(MATCH, 0, 1f));
        root.addView(buildBottomBar(), new LinearLayout.LayoutParams(MATCH, WRAP));

        buildHome();
        buildError();
        setContentView(root);

        final View rootRef = root;
        root.getViewTreeObserver().addOnGlobalLayoutListener(() -> {
            Rect r = new Rect();
            rootRef.getWindowVisibleDisplayFrame(r);
            boolean kb = rootRef.getRootView().getHeight() - r.height() > dp(160);
            if (kb) {
                kbSeen = true;
            } else if (kbSeen && searchActive) {
                kbSeen = false;
                closeSearch();
            }
        });
    }

    EditText field(String hint) {
        final EditText e = new EditText(this);
        e.setHint(hint);
        e.setSingleLine(true);
        e.setTextSize(15);
        e.setTextColor(text);
        e.setHintTextColor(sub);
        e.setBackground(box(card, 24));
        e.setPadding(dp(18), dp(12), dp(18), dp(12));
        e.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_URI);
        e.setImeOptions(EditorInfo.IME_ACTION_GO);
        e.setOnEditorActionListener((v, actionId, ev) -> {
            go(e.getText().toString());
            return true;
        });
        return e;
    }

    void buildHome() {
        homeView = new ScrollView(this);
        homeView.setBackgroundColor(bg);
        homeView.setFillViewport(true);

        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setGravity(Gravity.CENTER_HORIZONTAL);
        col.setPadding(dp(20), dp(56), dp(20), dp(20));
        fadeViews.clear();

        TextView logo = new TextView(this);
        logo.setText("H\u2665");
        logo.setTextSize(34);
        logo.setTypeface(null, Typeface.BOLD);
        logo.setTextColor(Color.WHITE);
        logo.setGravity(Gravity.CENTER);
        GradientDrawable lg = new GradientDrawable(GradientDrawable.Orientation.TL_BR,
                new int[]{0xFFFF5FA8, brand});
        lg.setCornerRadius(dp(20));
        logo.setBackground(lg);
        col.addView(logo, new LinearLayout.LayoutParams(dp(84), dp(84)));
        fadeViews.add(logo);

        if (logoAnim != null) logoAnim.cancel();
        logoAnim = ObjectAnimator.ofPropertyValuesHolder(logo,
                PropertyValuesHolder.ofFloat("scaleX", 1f, 1.07f),
                PropertyValuesHolder.ofFloat("scaleY", 1f, 1.07f));
        logoAnim.setDuration(1500);
        logoAnim.setRepeatCount(ValueAnimator.INFINITE);
        logoAnim.setRepeatMode(ValueAnimator.REVERSE);
        logoAnim.setInterpolator(new AccelerateDecelerateInterpolator());
        logoAnim.start();

        SpannableStringBuilder sb = new SpannableStringBuilder("HerБраузер");
        sb.setSpan(new ForegroundColorSpan(PINK), 0, 3, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        sb.setSpan(new ForegroundColorSpan(text), 3, sb.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        TextView title = new TextView(this);
        title.setText(sb);
        title.setTextSize(34);
        title.setTypeface(null, Typeface.BOLD);
        LinearLayout.LayoutParams tlp = new LinearLayout.LayoutParams(WRAP, WRAP);
        tlp.topMargin = dp(14);
        col.addView(title, tlp);
        fadeViews.add(title);

        TextView tag = tv("Быстрый. Удобный. Для тебя.", 15, sub);
        tag.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams glp = new LinearLayout.LayoutParams(WRAP, WRAP);
        glp.topMargin = dp(6);
        col.addView(tag, glp);
        fadeViews.add(tag);

        privLabel = tv("Приватная вкладка: история не сохраняется", 13, PINK);
        privLabel.setVisibility(View.GONE);
        LinearLayout.LayoutParams plp = new LinearLayout.LayoutParams(WRAP, WRAP);
        plp.topMargin = dp(10);
        col.addView(privLabel, plp);
        fadeViews.add(privLabel);

        homeSearch = field("\uD83D\uDD0D  Введите адрес или поисковый запрос");
        homeSearch.setOnFocusChangeListener((v, focus) -> setSearchMode(focus));
        searchRow = new LinearLayout(this);
        searchRow.setGravity(Gravity.CENTER_VERTICAL);
        searchRow.addView(homeSearch, new LinearLayout.LayoutParams(0, WRAP, 1f));
        TextView mic = tv("\uD83C\uDFA4", 20, accent);
        mic.setGravity(Gravity.CENTER);
        mic.setBackground(circle(light));
        rip(mic, 60);
        press(mic);
        mic.setOnClickListener(v -> startVoice());
        LinearLayout.LayoutParams mlp = new LinearLayout.LayoutParams(dp(48), dp(48));
        mlp.leftMargin = dp(8);
        searchRow.addView(mic, mlp);
        LinearLayout.LayoutParams slp = new LinearLayout.LayoutParams(MATCH, WRAP);
        slp.topMargin = dp(28);
        col.addView(searchRow, slp);

        HorizontalScrollView hs = new HorizontalScrollView(this);
        hs.setHorizontalScrollBarEnabled(false);
        shortcutsRow = new LinearLayout(this);
        hs.addView(shortcutsRow, new ViewGroup.LayoutParams(WRAP, WRAP));
        LinearLayout.LayoutParams hlp = new LinearLayout.LayoutParams(MATCH, WRAP);
        hlp.topMargin = dp(24);
        col.addView(hs, hlp);
        fadeViews.add(hs);
        refreshShortcuts();

        homeView.addView(col, new ViewGroup.LayoutParams(MATCH, WRAP));
    }

    void buildError() {
        errorView = new LinearLayout(this);
        errorView.setOrientation(LinearLayout.VERTICAL);
        errorView.setGravity(Gravity.CENTER);
        errorView.setBackgroundColor(bg);
        errorView.setPadding(dp(32), dp(32), dp(32), dp(32));
        errorView.setClickable(true);

        errIcon = tv("\uD83D\uDCE1", 56, accent);
        errIcon.setGravity(Gravity.CENTER);
        errorView.addView(errIcon, new LinearLayout.LayoutParams(WRAP, WRAP));

        errMsg = tv("", 18, text);
        errMsg.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams mp = new LinearLayout.LayoutParams(WRAP, WRAP);
        mp.topMargin = dp(20);
        errorView.addView(errMsg, mp);

        TextView retry = tv("Повторить", 16, Color.WHITE);
        retry.setTypeface(null, Typeface.BOLD);
        retry.setGravity(Gravity.CENTER);
        retry.setBackground(box(brand, 24));
        retry.setPadding(dp(32), dp(12), dp(32), dp(12));
        rip(retry, 24);
        press(retry);
        retry.setOnClickListener(v -> reload());
        LinearLayout.LayoutParams rp = new LinearLayout.LayoutParams(WRAP, WRAP);
        rp.topMargin = dp(24);
        errorView.addView(retry, rp);
    }

    void addShortcut(String sym, String label, int symColor, GradientDrawable bgIcon,
                     final Runnable click, final Runnable longClick) {
        LinearLayout it = new LinearLayout(this);
        it.setOrientation(LinearLayout.VERTICAL);
        it.setGravity(Gravity.CENTER_HORIZONTAL);
        it.setPadding(dp(4), 0, dp(4), 0);

        TextView ic = new TextView(this);
        ic.setText(sym);
        ic.setGravity(Gravity.CENTER);
        ic.setTextSize(20);
        ic.setTypeface(null, Typeface.BOLD);
        ic.setTextColor(symColor);
        ic.setBackground(bgIcon);
        it.addView(ic, new LinearLayout.LayoutParams(dp(56), dp(56)));

        TextView lb = tv(label, 11, text);
        lb.setGravity(Gravity.CENTER);
        lb.setMaxLines(2);
        lb.setPadding(0, dp(6), 0, 0);
        it.addView(lb, new LinearLayout.LayoutParams(dp(68), WRAP));

        press(it);
        it.setOnClickListener(v -> click.run());
        if (longClick != null) {
            it.setOnLongClickListener(v -> {
                longClick.run();
                return true;
            });
        }
        int index = shortcutsRow.getChildCount();
        shortcutsRow.addView(it);
        it.setAlpha(0f);
        it.setTranslationY(dp(14));
        it.animate().alpha(1f).translationY(0f).setStartDelay(50L * index).setDuration(280).start();
    }

    void refreshShortcuts() {
        shortcutsRow.removeAllViews();
        addShortcut("G", "Google", 0xFF4285F4, box(card, 16),
                () -> go("https://www.google.com"), null);
        addShortcut("\u25B6", "YouTube", 0xFFE53935, box(card, 16),
                () -> go("https://www.youtube.com"), null);
        addShortcut("VK", "ВКонтакте", Color.WHITE, box(0xFF2787F5, 16),
                () -> go("https://vk.com"), null);
        GradientDrawable ig = new GradientDrawable(GradientDrawable.Orientation.BL_TR,
                new int[]{0xFFFEDA75, 0xFFFA7E1E, 0xFFD62976, 0xFF4F5BD5});
        ig.setCornerRadius(dp(16));
        addShortcut("IG", "Instagram", Color.WHITE, ig,
                () -> go("https://www.instagram.com"), null);
        addShortcut("\u2665", "Избранное", PINK, box(soft, 16),
                () -> showListScreen(0), null);

        final List<String[]> custom = store.list("shortcuts");
        for (int i = 0; i < custom.size(); i++) {
            final int idx = i;
            final String[] c = custom.get(i);
            addShortcut(firstLetter(c[0]), c[0], accent, box(card, 16),
                    () -> go(c[1]),
                    () -> confirm("Удалить ярлык «" + c[0] + "»?", () -> {
                        store.remove("shortcuts", idx);
                        refreshShortcuts();
                    }));
        }
        addShortcut("+", "Добавить ссылку", accent, box(soft, 16),
                () -> addShortcutDialog(), null);
    }

    void addShortcutDialog() {
        final EditText e = dlgField("Адрес, например example.com",
                InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_URI);
        LinearLayout col = new LinearLayout(this);
        col.setPadding(dp(20), dp(8), dp(20), 0);
        col.addView(e, new LinearLayout.LayoutParams(MATCH, WRAP));
        builder().setTitle("Добавить ссылку")
                .setView(col)
                .setPositiveButton("Сохранить", (d, w) -> {
                    String u = toUrl(e.getText().toString());
                    if (u == null) return;
                    String host = hostOf(u);
                    store.append("shortcuts", new String[]{host.isEmpty() ? u : host.replace("www.", ""), u});
                    refreshShortcuts();
                })
                .setNegativeButton("Отмена", null)
                .show();
    }

    View buildBottomBar() {
        LinearLayout b = new LinearLayout(this);
        b.setBackgroundColor(bar);
        b.setPadding(dp(4), dp(6), dp(4), dp(6));

        backBtn = new Ico(this, Ico.LEFT, accent);
        backBtn.setBackground(circle(light));
        fwdBtn = new Ico(this, Ico.RIGHT, accent);
        fwdBtn.setBackground(circle(light));

        TextView search = tv("\uD83D\uDD0D", 24, accent);
        search.setGravity(Gravity.CENTER);
        search.setBackground(circle(light));

        tabsBox = tv("1", 14, accent);
        tabsBox.setTypeface(null, Typeface.BOLD);
        tabsBox.setGravity(Gravity.CENTER);
        GradientDrawable tb = box(Color.TRANSPARENT, 8);
        tb.setStroke(dp(2), accent);
        tabsBox.setBackground(tb);

        TextView menu = tv("\u2630", 26, accent);
        menu.setGravity(Gravity.CENTER);

        b.addView(navItem(backBtn, 56, "\u25C4 Назад", v -> doBack()), new LinearLayout.LayoutParams(0, WRAP, 1f));
        b.addView(navItem(fwdBtn, 56, "Вперёд \u25BA", v -> doForward()), new LinearLayout.LayoutParams(0, WRAP, 1f));
        b.addView(navItem(search, 64, "Поиск", v -> focusSearch()), new LinearLayout.LayoutParams(0, WRAP, 1f));
        View tabsItem = navItem(tabsBox, 30, "Вкладки", v -> showTabs());
        tabsItem.setOnLongClickListener(v -> {
            newTab(false);
            return true;
        });
        b.addView(tabsItem, new LinearLayout.LayoutParams(0, WRAP, 1f));
        b.addView(navItem(menu, 40, "Меню", v -> showMenu()), new LinearLayout.LayoutParams(0, WRAP, 1f));
        return b;
    }

    View navItem(View icon, int sizeDp, String label, View.OnClickListener l) {
        LinearLayout it = new LinearLayout(this);
        it.setOrientation(LinearLayout.VERTICAL);
        it.setGravity(Gravity.CENTER_HORIZONTAL);
        it.setOnClickListener(l);
        rip(it, 16);
        press(it);

        FrameLayout holder = new FrameLayout(this);
        holder.addView(icon, new FrameLayout.LayoutParams(dp(sizeDp), dp(sizeDp), Gravity.CENTER));
        it.addView(holder, new LinearLayout.LayoutParams(MATCH, dp(64)));

        TextView lb = tv(label, 12, accent);
        lb.setGravity(Gravity.CENTER);
        it.addView(lb, new LinearLayout.LayoutParams(WRAP, WRAP));
        return it;
    }

    void updateNav() {
        if (cur == null || backBtn == null) return;
        boolean canBack = !cur.home;
        boolean canFwd = cur.home ? cur.web.getUrl() != null : cur.web.canGoForward();
        backBtn.animate().alpha(canBack ? 1f : 0.35f).setDuration(160).start();
        fwdBtn.animate().alpha(canFwd ? 1f : 0.35f).setDuration(160).start();
        tabsBox.setText(String.valueOf(tabs.size()));
    }

    // ---------------------------------------------------------------- WebView

    WebView createWebView(final Tab t) {
        final WebView w = new WebView(this);
        WebSettings s = w.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setBuiltInZoomControls(true);
        s.setDisplayZoomControls(false);
        s.setLoadWithOverviewMode(true);
        s.setUseWideViewPort(true);
        s.setGeolocationEnabled(true);
        s.setMediaPlaybackRequiresUserGesture(false);
        if (t.priv) s.setCacheMode(WebSettings.LOAD_NO_CACHE);
        CookieManager.getInstance().setAcceptCookie(true);
        CookieManager.getInstance().setAcceptThirdPartyCookies(w, true);
        if (!t.priv) w.addJavascriptInterface(new NotifBridge(t), "HerNotify");

        w.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView v, WebResourceRequest r) {
                String u = r.getUrl().toString();
                if (u.startsWith("http://") || u.startsWith("https://")) return false;
                try {
                    startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(u)));
                } catch (Exception e) {
                    toast("Не удалось открыть ссылку");
                }
                return true;
            }

            @Override
            public void onPageStarted(WebView v, String url, Bitmap icon) {
                t.lastUrl = url;
                t.favicon = null;
                if (!t.priv) v.evaluateJavascript(SHIM, null);
                if (t == cur && !urlBar.hasFocus()) urlBar.setText(url);
            }

            @Override
            public void onPageFinished(WebView v, String url) {
                if (!t.priv) v.evaluateJavascript(SHIM, null);
                if (!t.priv && t.error == null && url != null && url.startsWith("http")) {
                    String title = v.getTitle();
                    if (title == null || title.isEmpty()) title = url;
                    List<String[]> h = store.list("history");
                    if (h.isEmpty() || !h.get(0)[1].equals(url)) {
                        store.add("history", new String[]{title, url,
                                String.valueOf(System.currentTimeMillis())}, 500);
                    }
                }
                if (t == cur) updateNav();
            }

            @SuppressWarnings("deprecation")
            @Override
            public void onReceivedError(WebView v, int code, String desc, String failingUrl) {
                if (code == ERROR_HOST_LOOKUP || code == ERROR_CONNECT || code == ERROR_TIMEOUT
                        || code == ERROR_IO || code == ERROR_PROXY_AUTHENTICATION) {
                    showError(t, failingUrl);
                }
            }

            @Override
            public boolean onRenderProcessGone(WebView v, RenderProcessGoneDetail detail) {
                runOnUiThread(() -> recreateTabWeb(t));
                return true;
            }
        });

        w.setWebChromeClient(new WebChromeClient() {
            @Override
            public void onProgressChanged(WebView v, int p) {
                if (t == cur) {
                    progress.setProgress(p);
                    progress.setVisibility(p >= 100 ? View.GONE : View.VISIBLE);
                }
            }

            @Override
            public void onReceivedIcon(WebView v, Bitmap icon) {
                t.favicon = icon;
            }

            @Override
            public void onPermissionRequest(final PermissionRequest req) {
                List<String> need = new ArrayList<String>();
                List<String> res = new ArrayList<String>();
                List<String> names = new ArrayList<String>();
                List<String> kinds = new ArrayList<String>();
                for (String r : req.getResources()) {
                    if (PermissionRequest.RESOURCE_VIDEO_CAPTURE.equals(r)) {
                        need.add(Manifest.permission.CAMERA);
                        res.add(r);
                        names.add("камере");
                        kinds.add("cam");
                    } else if (PermissionRequest.RESOURCE_AUDIO_CAPTURE.equals(r)) {
                        need.add(Manifest.permission.RECORD_AUDIO);
                        res.add(r);
                        names.add("микрофону");
                        kinds.add("mic");
                    }
                }
                if (need.isEmpty()) {
                    req.deny();
                    return;
                }
                final String[] granted = res.toArray(new String[0]);
                askSite(req.getOrigin().getHost(), TextUtils.join("+", kinds), "доступ к " + TextUtils.join(" и ", names),
                        need.toArray(new String[0]),
                        () -> req.grant(granted),
                        () -> req.deny());
            }

            @Override
            public void onGeolocationPermissionsShowPrompt(final String origin,
                                                           final GeolocationPermissions.Callback cb) {
                askSite(Uri.parse(origin).getHost(), "geo", "доступ к местоположению",
                        new String[]{Manifest.permission.ACCESS_FINE_LOCATION},
                        () -> cb.invoke(origin, true, false),
                        () -> cb.invoke(origin, false, false));
            }
        });

        w.setDownloadListener((url, ua, cd, mime, len) -> startDownload(url, ua, cd, mime));
        return w;
    }

    void recreateTabWeb(Tab t) {
        boolean wasCur = t == cur;
        String u = t.lastUrl;
        if (wasCur) content.removeAllViews();
        try {
            t.web.destroy();
        } catch (Exception e) {
            // уже уничтожен
        }
        t.web = createWebView(t);
        if (u != null && !t.home) t.pendingUrl = u;
        if (wasCur) {
            cur = null;
            select(t);
        }
    }

    // ---- экран ошибки: нет интернета / белые списки

    boolean hasNetwork() {
        try {
            ConnectivityManager cm = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
            NetworkInfo ni = cm.getActiveNetworkInfo();
            return ni != null && ni.isConnected();
        } catch (Exception e) {
            return false;
        }
    }

    boolean probe(String u) {
        HttpURLConnection c = null;
        try {
            c = (HttpURLConnection) new URL(u).openConnection();
            c.setConnectTimeout(3000);
            c.setReadTimeout(3000);
            c.getResponseCode();
            return true;
        } catch (Exception e) {
            return false;
        } finally {
            if (c != null) c.disconnect();
        }
    }

    void showError(final Tab t, String url) {
        if (url != null) t.lastUrl = url;
        if (!hasNetwork()) {
            setError(t, "offline");
            return;
        }
        setError(t, "checking");
        new Thread(() -> {
            final boolean ru = probe("https://ya.ru");
            final boolean world = probe("https://www.gstatic.com/generate_204");
            runOnUiThread(() -> {
                if (tabs.contains(t) && "checking".equals(t.error)) {
                    if (!ru && !world) setError(t, "offline");
                    else if (ru && !world) setError(t, "whitelist");
                    else setError(t, "other");
                }
            });
        }).start();
    }

    void setError(Tab t, String e) {
        t.error = e;
        if (t == cur) refreshOverlay();
    }

    String errText(String e) {
        if ("offline".equals(e)) return MSG_OFFLINE;
        if ("whitelist".equals(e)) return MSG_WHITELIST;
        if ("checking".equals(e)) return MSG_CHECK;
        return MSG_OTHER;
    }

    String errIconOf(String e) {
        if ("offline".equals(e)) return "\uD83D\uDCE1";
        if ("whitelist".equals(e)) return "\uD83D\uDEAB";
        if ("checking".equals(e)) return "\u23F3";
        return "\u26A0";
    }

    // ---------------------------------------------------------------- вкладки и навигация

    Tab makeTab(boolean priv) {
        Tab t = new Tab();
        t.priv = priv;
        t.web = createWebView(t);
        return t;
    }

    @Override
    void newTab(boolean priv) {
        Tab t = makeTab(priv);
        tabs.add(t);
        select(t);
    }

    void saveTabs() {
        if (store == null || cur == null) return;
        List<String[]> l = new ArrayList<String[]>();
        int ci = 0, k = 0;
        for (Tab t : tabs) {
            if (t.priv) continue;
            String u = "";
            if (!t.home) {
                String w = t.web.getUrl();
                u = w != null ? w : (t.pendingUrl != null ? t.pendingUrl : (t.lastUrl == null ? "" : t.lastUrl));
            }
            if (t == cur) ci = k;
            l.add(new String[]{u});
            k++;
        }
        store.save("tabs", l);
        store.setStr("tab_cur", String.valueOf(ci));
    }

    void restoreTabs() {
        List<String[]> l = store.list("tabs");
        if (l.isEmpty()) {
            newTab(false);
            return;
        }
        for (String[] e : l) {
            if (tabs.size() >= 12) break;
            Tab t = makeTab(false);
            if (!e[0].isEmpty()) {
                t.home = false;
                t.lastUrl = e[0];
                t.pendingUrl = e[0];
            }
            tabs.add(t);
        }
        int ci;
        try {
            ci = Integer.parseInt(store.getStr("tab_cur", "0"));
        } catch (Exception ex) {
            ci = 0;
        }
        select(tabs.get(Math.max(0, Math.min(ci, tabs.size() - 1))));
    }

    void captureThumb(Tab t) {
        try {
            if (t == null || t.home || t.priv || t.web.getWidth() <= 0 || t.web.getHeight() <= 0) return;
            int w = t.web.getWidth();
            int h = t.web.getHeight();
            float sc = 360f / w;
            Bitmap b = Bitmap.createBitmap(360, Math.max(1, (int) (h * sc)), Bitmap.Config.ARGB_8888);
            Canvas c = new Canvas(b);
            c.scale(sc, sc);
            t.web.draw(c);
            t.thumb = b;
        } catch (Throwable e) {
            t.thumb = null;
        }
    }

    void select(Tab t) {
        if (cur != null && cur != t) captureThumb(cur);
        cur = t;
        content.removeAllViews();
        content.addView(t.web, new FrameLayout.LayoutParams(MATCH, MATCH));
        if (t.pendingUrl != null && !t.home) {
            String pu = t.pendingUrl;
            t.pendingUrl = null;
            t.web.loadUrl(pu);
        }
        content.setAlpha(0.3f);
        content.animate().alpha(1f).setDuration(240).start();
        homeSearch.setText("");
        findBar.setVisibility(View.GONE);
        String u = t.web.getUrl();
        if (u == null) u = t.lastUrl;
        urlBar.setText(u == null ? "" : u);
        progress.setVisibility(View.GONE);
        refreshOverlay();
    }

    void refreshOverlay() {
        content.removeView(homeView);
        content.removeView(errorView);
        if (cur.home) {
            resetSearchMode();
            privLabel.setVisibility(cur.priv ? View.VISIBLE : View.GONE);
            content.addView(homeView, new FrameLayout.LayoutParams(MATCH, MATCH));
            homeView.setAlpha(0f);
            homeView.animate().alpha(1f).setDuration(260).start();
        } else if (cur.error != null) {
            errIcon.setText(errIconOf(cur.error));
            errMsg.setText(errText(cur.error));
            content.addView(errorView, new FrameLayout.LayoutParams(MATCH, MATCH));
        }
        topBar.setVisibility(cur.home ? View.GONE : View.VISIBLE);
        topBar.setBackgroundColor(cur.priv ? (dark ? 0xFF3A3560 : 0xFFCFC6E8) : bg);
        updateNav();
        updateNotifBanner();
    }

    void closeTab(Tab t) {
        int i = tabs.indexOf(t);
        boolean wasCur = t == cur;
        tabs.remove(t);
        if (wasCur) {
            content.removeAllViews();
            cur = null;
        }
        if (t.priv) {
            t.web.clearCache(true);
            t.web.clearHistory();
        }
        t.web.destroy();
        if (tabs.isEmpty()) {
            newTab(false);
            return;
        }
        if (wasCur) select(tabs.get(Math.min(i, tabs.size() - 1)));
        else updateNav();
    }

    @Override
    void go(String input) {
        String u = toUrl(input);
        if (u == null) return;
        hideKeyboard();
        resetSearchMode();
        cur.home = false;
        cur.error = null;
        cur.pendingUrl = null;
        cur.lastUrl = u;
        cur.web.loadUrl(u);
        select(cur);
        urlBar.setText(u);
    }

    void reload() {
        if (cur.home) return;
        reloadBtn.animate().rotationBy(360f).setDuration(500)
                .setInterpolator(new DecelerateInterpolator()).start();
        if (cur.error != null) {
            String u = cur.lastUrl != null ? cur.lastUrl : cur.web.getUrl();
            cur.error = null;
            refreshOverlay();
            if (u != null) cur.web.loadUrl(u);
        } else {
            cur.web.reload();
        }
    }

    boolean doBack() {
        if (cur.home) return false;
        if (cur.error != null) {
            cur.error = null;
            if (cur.web.canGoBack()) cur.web.goBack();
            else cur.home = true;
            select(cur);
            return true;
        }
        if (cur.web.canGoBack()) {
            cur.web.goBack();
        } else {
            cur.home = true;
            select(cur);
        }
        return true;
    }

    void doForward() {
        if (cur.error != null) {
            cur.error = null;
            refreshOverlay();
        }
        if (cur.home) {
            if (cur.web.getUrl() != null) {
                cur.home = false;
                select(cur);
            }
        } else if (cur.web.canGoForward()) {
            cur.web.goForward();
        }
    }

    void focusSearch() {
        EditText e = cur.home ? homeSearch : urlBar;
        e.requestFocus();
        if (!cur.home) e.selectAll();
        showKb(e);
    }

    void showFind() {
        if (cur.home) return;
        findBar.setVisibility(View.VISIBLE);
        findInput.setText("");
        findInput.requestFocus();
        showKb(findInput);
    }

    void closeFind() {
        findBar.setVisibility(View.GONE);
        if (cur != null) cur.web.clearMatches();
        hideKeyboard();
    }

    void toggleDesktop() {
        cur.desktop = !cur.desktop;
        WebSettings s = cur.web.getSettings();
        s.setUserAgentString(cur.desktop ? DESKTOP_UA : null);
        s.setUseWideViewPort(true);
        s.setLoadWithOverviewMode(true);
        cur.web.reload();
        toast(cur.desktop ? "Версия для ПК включена" : "Мобильная версия включена");
    }

    void share() {
        String u = cur.web.getUrl();
        if (u == null) return;
        Intent i = new Intent(Intent.ACTION_SEND);
        i.setType("text/plain");
        i.putExtra(Intent.EXTRA_TEXT, u);
        startActivity(Intent.createChooser(i, "Поделиться"));
    }

    void toggleBookmark() {
        String url = cur.web.getUrl();
        if (cur.home || url == null) {
            toast("Сначала откройте страницу");
            return;
        }
        List<String[]> l = store.list("bookmarks");
        for (int i = 0; i < l.size(); i++) {
            if (l.get(i)[1].equals(url)) {
                store.remove("bookmarks", i);
                toast("Удалено из закладок");
                return;
            }
        }
        String title = cur.web.getTitle();
        if (title == null || title.isEmpty()) title = url;
        store.add("bookmarks", new String[]{title, url}, 500);
        toast("Добавлено в закладки");
    }

    // ---------------------------------------------------------------- поиск: наверх с анимацией, голос

    void setSearchMode(boolean on) {
        if (on == searchActive || searchRow == null) return;
        searchActive = on;
        if (on) kbSeen = false;
        float dy = on ? -(searchRow.getTop() - dp(12)) : 0f;
        searchRow.animate().translationY(dy).setDuration(320)
                .setInterpolator(new DecelerateInterpolator()).start();
        for (final View v : fadeViews) {
            if (on) {
                v.animate().alpha(0f).setDuration(240).withEndAction(() -> {
                    if (searchActive) v.setVisibility(View.INVISIBLE);
                }).start();
            } else {
                v.setVisibility(View.VISIBLE);
                v.animate().alpha(1f).setDuration(320).start();
            }
        }
    }

    void resetSearchMode() {
        searchActive = false;
        kbSeen = false;
        if (searchRow != null) {
            searchRow.animate().cancel();
            searchRow.setTranslationY(0f);
        }
        for (View v : fadeViews) {
            v.animate().cancel();
            v.setVisibility(View.VISIBLE);
            v.setAlpha(1f);
        }
    }

    void closeSearch() {
        if (!searchActive) return;
        homeSearch.clearFocus();
        hideKeyboard();
        setSearchMode(false);
    }

    void startVoice() {
        Intent i = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
        i.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
        i.putExtra(RecognizerIntent.EXTRA_LANGUAGE, "ru-RU");
        i.putExtra(RecognizerIntent.EXTRA_PROMPT, "Скажите, что искать в Яндексе");
        try {
            startActivityForResult(i, 77);
        } catch (Exception e) {
            toast("Голосовой ввод недоступен на этом телефоне");
        }
    }

    void yandex(String q) {
        try {
            go("https://yandex.ru/search/?text=" + URLEncoder.encode(q, "UTF-8"));
        } catch (Exception e) {
            go(q);
        }
    }

    // ---------------------------------------------------------------- баннер «разрешите уведомления»

    boolean notifEnabled() {
        if (Build.VERSION.SDK_INT >= 33 && !has("android.permission.POST_NOTIFICATIONS")) return false;
        if (Build.VERSION.SDK_INT >= 24) {
            NotificationManager nm = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
            if (nm != null && !nm.areNotificationsEnabled()) return false;
        }
        return true;
    }

    void showBanner() {
        if (notifBanner.getVisibility() == View.VISIBLE) return;
        notifBanner.setVisibility(View.VISIBLE);
        notifBanner.setAlpha(0f);
        notifBanner.setTranslationY(-dp(12));
        notifBanner.animate().alpha(1f).translationY(0f).setDuration(260)
                .setInterpolator(new DecelerateInterpolator()).start();
    }

    void updateNotifBanner() {
        if (notifBanner == null || cur == null) return;
        if (cur.home || notifDismissed) {
            notifBanner.setVisibility(View.GONE);
            return;
        }
        if (!notifEnabled()) {
            notifWasOff = true;
            notifText.setText("У вас не будет уведомлений, если вы не включите. Чтобы включить, разрешите уведомления.");
            notifBtn.setVisibility(View.VISIBLE);
            showBanner();
        } else if (notifWasOff) {
            notifWasOff = false;
            notifText.setText("Теперь вы разрешили уведомления, теперь вам будут приходить уведомления в приложении.");
            notifBtn.setVisibility(View.GONE);
            showBanner();
            ui.postDelayed(() -> {
                if (notifEnabled() && notifBanner != null) notifBanner.setVisibility(View.GONE);
            }, 5000);
        } else {
            notifBanner.setVisibility(View.GONE);
        }
    }

    void askNotifPermission() {
        if (Build.VERSION.SDK_INT >= 33 && !has("android.permission.POST_NOTIFICATIONS") && !notifAsked) {
            notifAsked = true;
            ask(new String[]{"android.permission.POST_NOTIFICATIONS"}, 3);
        } else {
            openNotifSettings();
        }
    }

    void openNotifSettings() {
        try {
            Intent i;
            if (Build.VERSION.SDK_INT >= 26) {
                i = new Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS);
                i.putExtra(Settings.EXTRA_APP_PACKAGE, getPackageName());
            } else {
                i = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                        Uri.parse("package:" + getPackageName()));
            }
            startActivity(i);
        } catch (Exception e) {
            toast("Откройте настройки приложения и включите уведомления");
        }
    }

    // ---------------------------------------------------------------- вкладки (как в Opera)

    View tabCard(final Tab t, final Dialog d, final Runnable re) {
        final LinearLayout c = new LinearLayout(this);
        c.setOrientation(LinearLayout.VERTICAL);
        GradientDrawable g = box(card, 18);
        if (t == cur) g.setStroke(dp(2), brand);
        c.setBackground(g);
        c.setPadding(dp(8), dp(8), dp(8), dp(8));

        String title;
        if (t.home) {
            title = "Новая вкладка";
        } else {
            title = t.web.getTitle();
            if (title == null || title.isEmpty()) title = hostOf(t.lastUrl);
            if (title.isEmpty()) title = "Вкладка";
        }
        LinearLayout hd = new LinearLayout(this);
        hd.setGravity(Gravity.CENTER_VERTICAL);
        TextView av = tv(firstLetter(title), 11, Color.WHITE);
        av.setGravity(Gravity.CENTER);
        av.setTypeface(null, Typeface.BOLD);
        av.setBackground(circle(avatarColor(title)));
        hd.addView(av, new LinearLayout.LayoutParams(dp(22), dp(22)));
        TextView tt = tv(title, 13, text);
        tt.setSingleLine(true);
        tt.setEllipsize(TextUtils.TruncateAt.END);
        LinearLayout.LayoutParams tp = new LinearLayout.LayoutParams(0, WRAP, 1f);
        tp.leftMargin = dp(8);
        hd.addView(tt, tp);
        TextView x = tv("\u2715", 15, sub);
        x.setPadding(dp(10), dp(6), dp(6), dp(6));
        hd.addView(x);
        c.addView(hd, new LinearLayout.LayoutParams(MATCH, WRAP));

        View body;
        if (t.thumb != null && !t.home) {
            ImageView iv = new ImageView(this);
            iv.setImageBitmap(t.thumb);
            iv.setScaleType(ImageView.ScaleType.CENTER_CROP);
            body = iv;
        } else {
            TextView ph = tv(t.home ? "\u2661" : firstLetter(title), t.home ? 34 : 40, brand);
            ph.setGravity(Gravity.CENTER);
            ph.setBackgroundColor(soft);
            body = ph;
        }
        body.setClipToOutline(true);
        body.setOutlineProvider(new ViewOutlineProvider() {
            @Override
            public void getOutline(View v, Outline o) {
                o.setRoundRect(0, 0, v.getWidth(), v.getHeight(), dp(12));
            }
        });
        LinearLayout.LayoutParams bp = new LinearLayout.LayoutParams(MATCH, dp(150));
        bp.topMargin = dp(8);
        c.addView(body, bp);

        press(c);
        c.setOnClickListener(v -> {
            d.dismiss();
            select(t);
        });
        x.setOnClickListener(v -> c.animate().alpha(0f).scaleX(0.8f).scaleY(0.8f).setDuration(180)
                .withEndAction(() -> {
                    closeTab(t);
                    re.run();
                }).start());
        return c;
    }

    void showTabs() {
        captureThumb(cur);
        final Dialog d = fullScreen();
        final boolean[] pm = {cur.priv};
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(bg);
        root.setFitsSystemWindows(true);

        final TextView sTabs = tv("Вкладки", 15, text);
        final TextView sPriv = tv("Приватность", 15, text);
        sTabs.setGravity(Gravity.CENTER);
        sPriv.setGravity(Gravity.CENTER);
        sTabs.setTypeface(null, Typeface.BOLD);
        sPriv.setTypeface(null, Typeface.BOLD);
        LinearLayout seg = new LinearLayout(this);
        seg.setBackground(box(card, 24));
        seg.setPadding(dp(4), dp(4), dp(4), dp(4));
        seg.addView(sTabs, new LinearLayout.LayoutParams(0, dp(40), 1f));
        seg.addView(sPriv, new LinearLayout.LayoutParams(0, dp(40), 1f));
        LinearLayout.LayoutParams sgp = new LinearLayout.LayoutParams(MATCH, WRAP);
        sgp.setMargins(dp(24), dp(12), dp(24), dp(8));
        root.addView(seg, sgp);
        press(sTabs);
        press(sPriv);

        final LinearLayout grid = new LinearLayout(this);
        grid.setOrientation(LinearLayout.VERTICAL);
        grid.setPadding(dp(8), dp(4), dp(8), dp(8));
        ScrollView sv = new ScrollView(this);
        sv.addView(grid, new ViewGroup.LayoutParams(MATCH, WRAP));
        root.addView(sv, new LinearLayout.LayoutParams(MATCH, 0, 1f));

        final Runnable[] render = new Runnable[1];
        render[0] = () -> {
            sTabs.setBackground(pm[0] ? null : box(brand, 20));
            sTabs.setTextColor(pm[0] ? text : Color.WHITE);
            sPriv.setBackground(pm[0] ? box(brand, 20) : null);
            sPriv.setTextColor(pm[0] ? Color.WHITE : text);
            grid.removeAllViews();
            List<Tab> list = new ArrayList<Tab>();
            for (Tab t : tabs) if (t.priv == pm[0]) list.add(t);
            if (list.isEmpty()) {
                TextView e = tv(pm[0] ? "Приватных вкладок нет" : "Нет открытых вкладок", 16, sub);
                e.setGravity(Gravity.CENTER);
                e.setPadding(dp(24), dp(56), dp(24), dp(24));
                grid.addView(e, new LinearLayout.LayoutParams(MATCH, WRAP));
            }
            for (int i = 0; i < list.size(); i += 2) {
                LinearLayout row = new LinearLayout(this);
                for (int k = 0; k < 2; k++) {
                    LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, WRAP, 1f);
                    lp.setMargins(dp(6), dp(6), dp(6), dp(6));
                    if (i + k < list.size()) {
                        View cv = tabCard(list.get(i + k), d, render[0]);
                        row.addView(cv, lp);
                        cv.setAlpha(0f);
                        cv.setScaleX(0.9f);
                        cv.setScaleY(0.9f);
                        cv.animate().alpha(1f).scaleX(1f).scaleY(1f)
                                .setStartDelay((i + k) * 50L).setDuration(260).start();
                    } else {
                        row.addView(new View(this), lp);
                    }
                }
                grid.addView(row, new LinearLayout.LayoutParams(MATCH, WRAP));
            }
        };
        sTabs.setOnClickListener(v -> {
            pm[0] = false;
            render[0].run();
        });
        sPriv.setOnClickListener(v -> {
            pm[0] = true;
            render[0].run();
        });

        LinearLayout bb = new LinearLayout(this);
        bb.setGravity(Gravity.CENTER_VERTICAL);
        bb.setBackgroundColor(bar);
        bb.setPadding(dp(16), dp(10), dp(16), dp(10));
        TextView closeAll = tv("Закрыть все", 14, accent);
        closeAll.setPadding(dp(8), dp(12), dp(8), dp(12));
        press(closeAll);
        closeAll.setOnClickListener(v -> confirm(
                "Закрыть все " + (pm[0] ? "приватные " : "") + "вкладки?", () -> {
                    for (Tab t : new ArrayList<Tab>(tabs)) if (t.priv == pm[0]) closeTab(t);
                    render[0].run();
                }));
        bb.addView(closeAll, new LinearLayout.LayoutParams(0, WRAP, 1f));
        TextView add = tv("\uFF0B", 28, Color.WHITE);
        add.setGravity(Gravity.CENTER);
        add.setBackground(circle(brand));
        rip(add, 60);
        press(add);
        add.setOnClickListener(v -> {
            d.dismiss();
            newTab(pm[0]);
        });
        bb.addView(add, new LinearLayout.LayoutParams(dp(56), dp(56)));
        TextView done = tv("Готово", 15, accent);
        done.setGravity(Gravity.END);
        done.setPadding(dp(8), dp(12), dp(8), dp(12));
        press(done);
        done.setOnClickListener(v -> d.dismiss());
        bb.addView(done, new LinearLayout.LayoutParams(0, WRAP, 1f));
        root.addView(bb, new LinearLayout.LayoutParams(MATCH, WRAP));

        d.setContentView(root);
        d.show();
        render[0].run();
        root.setAlpha(0f);
        root.animate().alpha(1f).setDuration(200).start();
    }

    // ---------------------------------------------------------------- три точки (как в Opera)

    View pageRow(String glyph, String label, boolean checked, final Runnable r, final Dialog dlg) {
        LinearLayout row = new LinearLayout(this);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(8), dp(12), dp(8), dp(12));
        TextView g = tv(glyph, 20, accent);
        g.setGravity(Gravity.CENTER);
        row.addView(g, new LinearLayout.LayoutParams(dp(36), WRAP));
        TextView t = tv(label, 16, text);
        LinearLayout.LayoutParams tp = new LinearLayout.LayoutParams(0, WRAP, 1f);
        tp.leftMargin = dp(10);
        row.addView(t, tp);
        if (checked) row.addView(tv("\u2713", 18, accent), new LinearLayout.LayoutParams(WRAP, WRAP));
        rip(row, 12);
        press(row);
        row.setOnClickListener(v -> {
            dlg.dismiss();
            r.run();
        });
        return row;
    }

    void showPageMenu() {
        if (cur.home) {
            showMenu();
            return;
        }
        final Dialog dlg = new Dialog(this);
        dlg.requestWindowFeature(Window.FEATURE_NO_TITLE);

        LinearLayout c = new LinearLayout(this);
        c.setOrientation(LinearLayout.VERTICAL);
        c.setBackground(box(card, 20));
        c.setPadding(dp(10), dp(14), dp(10), dp(8));

        final String url = cur.web.getUrl();
        final String host = hostOf(url);
        String[] g = {"\u2192", "\u21BB", isBookmarked(url) ? "\u2605" : "\u2606", "\u2197"};
        String[] l = {"Вперёд", "Обновить", "Закладка", "Поделиться"};
        final Runnable[] a = {() -> doForward(), () -> reload(), () -> toggleBookmark(), () -> share()};

        LinearLayout irow = new LinearLayout(this);
        for (int i = 0; i < 4; i++) {
            final int idx = i;
            LinearLayout cell = new LinearLayout(this);
            cell.setOrientation(LinearLayout.VERTICAL);
            cell.setGravity(Gravity.CENTER_HORIZONTAL);
            TextView ic = tv(g[i], 22, accent);
            ic.setGravity(Gravity.CENTER);
            ic.setBackground(circle(soft));
            rip(ic, 60);
            cell.addView(ic, new LinearLayout.LayoutParams(dp(50), dp(50)));
            TextView lb = tv(l[i], 11, sub);
            lb.setGravity(Gravity.CENTER);
            lb.setPadding(0, dp(4), 0, 0);
            cell.addView(lb, new LinearLayout.LayoutParams(WRAP, WRAP));
            if (i == 0 && !cur.web.canGoForward()) cell.setAlpha(0.35f);
            press(cell);
            cell.setOnClickListener(v -> {
                dlg.dismiss();
                a[idx].run();
            });
            irow.addView(cell, new LinearLayout.LayoutParams(0, WRAP, 1f));
        }
        c.addView(irow, new LinearLayout.LayoutParams(MATCH, WRAP));

        View line = new View(this);
        line.setBackgroundColor(light);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(MATCH, dp(1));
        lp.topMargin = dp(12);
        lp.bottomMargin = dp(4);
        c.addView(line, lp);

        c.addView(pageRow("\uD83D\uDD0E", "Найти на странице", false, () -> showFind(), dlg));
        c.addView(pageRow("\uD83D\uDDA5", "Версия для ПК", cur.desktop, () -> toggleDesktop(), dlg));
        c.addView(pageRow("\uD83C\uDFE0", "Главный экран (Домой)", false, () -> addToHome(), dlg));
        c.addView(pageRow("\uD83D\uDD14", "Уведомления сайта", "granted".equals(siteState(host)),
                () -> toggleSiteNotif(host), dlg));
        c.addView(pageRow("\uD83D\uDD17", "Копировать ссылку", false, () -> copy(url), dlg));

        dlg.setContentView(c);
        Window w = dlg.getWindow();
        if (w != null) {
            w.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            WindowManager.LayoutParams wp = w.getAttributes();
            wp.gravity = Gravity.TOP | Gravity.END;
            wp.x = dp(8);
            wp.y = dp(60);
            wp.width = dp(300);
            wp.height = WRAP;
            wp.dimAmount = 0.25f;
            w.setAttributes(wp);
        }
        c.setPivotX(dp(300));
        c.setPivotY(0f);
        c.setScaleX(0.85f);
        c.setScaleY(0.85f);
        c.setAlpha(0f);
        dlg.show();
        c.animate().scaleX(1f).scaleY(1f).alpha(1f).setDuration(180)
                .setInterpolator(new DecelerateInterpolator()).start();
    }

    // ---------------------------------------------------------------- главное меню

    void showMenu() {
        final Dialog dlg = new Dialog(this);
        dlg.requestWindowFeature(Window.FEATURE_NO_TITLE);

        LinearLayout c = new LinearLayout(this);
        c.setOrientation(LinearLayout.VERTICAL);
        c.setBackground(box(bg, 28));
        c.setPadding(dp(16), dp(16), dp(16), dp(8));

        LinearLayout head = new LinearLayout(this);
        head.setGravity(Gravity.CENTER_VERTICAL);
        TextView hi = tv("\u2661", 28, brand);
        hi.setGravity(Gravity.CENTER);
        hi.setBackground(box(soft, 18));
        head.addView(hi, new LinearLayout.LayoutParams(dp(60), dp(60)));
        LinearLayout ht = new LinearLayout(this);
        ht.setOrientation(LinearLayout.VERTICAL);
        TextView h1 = tv("HerБраузер", 22, text);
        h1.setTypeface(null, Typeface.BOLD);
        ht.addView(h1);
        ht.addView(tv("Всё нужное под рукой", 14, sub));
        LinearLayout.LayoutParams htp = new LinearLayout.LayoutParams(0, WRAP, 1f);
        htp.leftMargin = dp(14);
        head.addView(ht, htp);
        TextView close = tv("\u2715", 22, accent);
        close.setPadding(dp(12), dp(8), dp(8), dp(8));
        close.setOnClickListener(v -> dlg.dismiss());
        head.addView(close, new LinearLayout.LayoutParams(WRAP, WRAP));
        c.addView(head, new LinearLayout.LayoutParams(MATCH, WRAP));

        View line = new View(this);
        line.setBackgroundColor(light);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(MATCH, dp(1));
        lp.topMargin = dp(14);
        lp.bottomMargin = dp(6);
        c.addView(line, lp);

        String[] glyphs = {"\uD83D\uDD16", "\u2B07", "\uD83D\uDD52", "\uD83D\uDD11", "\uFF0B", "\u2699"};
        String[] names = {"Закладки", "Загрузки", "История", "Пароли",
                "Добавить страницу в закладки", "Настройки"};
        String[] descs = {"Сохранённые страницы", "Файлы и изображения",
                "Посещённые страницы", "Сохранённые пароли", "Сохранить текущую страницу",
                "Оформление, память, о приложении"};
        final Runnable[] acts = {
                () -> showListScreen(0), () -> showListScreen(2),
                () -> showListScreen(1), () -> showListScreen(3), () -> toggleBookmark(),
                () -> showSettings(true)};
        ScrollView sv = new ScrollView(this);
        LinearLayout list = new LinearLayout(this);
        list.setOrientation(LinearLayout.VERTICAL);
        for (int i = 0; i < names.length; i++) {
            final int idx = i;
            list.addView(menuRow(glyphs[i], names[i], descs[i], v -> {
                dlg.dismiss();
                acts[idx].run();
            }), new LinearLayout.LayoutParams(MATCH, WRAP));
        }
        sv.addView(list, new ViewGroup.LayoutParams(MATCH, WRAP));
        c.addView(sv, new LinearLayout.LayoutParams(MATCH, WRAP));

        FrameLayout frame = new FrameLayout(this);
        frame.setPadding(dp(10), dp(10), dp(10), dp(10));
        frame.addView(c, new FrameLayout.LayoutParams(MATCH, WRAP));
        dlg.setContentView(frame);

        Window w = dlg.getWindow();
        if (w != null) {
            w.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            w.setGravity(Gravity.BOTTOM);
            w.setLayout(MATCH, WRAP);
        }
        frame.setTranslationY(dp(320));
        dlg.show();
        frame.animate().translationY(0f).setDuration(280)
                .setInterpolator(new DecelerateInterpolator()).start();
    }
}
