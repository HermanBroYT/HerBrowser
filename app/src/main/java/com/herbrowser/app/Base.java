package com.herbrowser.app;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.pm.PackageManager;
import android.content.res.ColorStateList;
import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.HapticFeedbackConstants;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.OvershootInterpolator;
import android.view.inputmethod.InputMethodManager;
import android.webkit.WebView;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.PopupMenu;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;
import java.net.URLEncoder;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Locale;

abstract class Base extends Activity {

    static final String VERSION = "1.3";
    static final int PINK = 0xFFE0409A;
    static final int VIOLET = 0xFF8B3FE8;
    static final int MATCH = ViewGroup.LayoutParams.MATCH_PARENT;
    static final int WRAP = ViewGroup.LayoutParams.WRAP_CONTENT;

    static final int[] PAL = {0xFF8B3FE8, 0xFFE0409A, 0xFF2787F5, 0xFF1FA67A,
            0xFFF29D38, 0xFFE5484D, 0xFF6E56CF, 0xFF0EA5B7};
    static final int[] ACCENTS = {0xFF8B3FE8, 0xFFE0409A, 0xFF2787F5, 0xFF1FA67A,
            0xFFF29D38, 0xFFE5484D, 0xFF0EA5B7, 0xFF3D3D3D};
    static final int[] BGS = {0xFFF3F0FF, 0xFFFFFFFF, 0xFFFFF1F5, 0xFFEAF6FF,
            0xFFEFFAF2, 0xFF15131F, 0xFF000000, 0xFF1B2433};

    /** Значки, нарисованные кодом: одинаковый размер и ровный центр. */
    static class Ico extends View {
        static final int LEFT = 0, RIGHT = 1, DOTS = 2;
        final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        final int type;

        Ico(Context c, int type, int color) {
            super(c);
            this.type = type;
            p.setColor(color);
            p.setStyle(Paint.Style.STROKE);
            p.setStrokeCap(Paint.Cap.ROUND);
            p.setStrokeJoin(Paint.Join.ROUND);
            p.setStrokeWidth(c.getResources().getDisplayMetrics().density * 2.4f);
        }

        @Override
        protected void onDraw(Canvas c) {
            float cx = getWidth() / 2f, cy = getHeight() / 2f;
            float s = Math.min(getWidth(), getHeight()) * 0.2f;
            if (type == DOTS) {
                p.setStyle(Paint.Style.FILL);
                float r = p.getStrokeWidth() * 0.85f;
                c.drawCircle(cx, cy - s, r, p);
                c.drawCircle(cx, cy, r, p);
                c.drawCircle(cx, cy + s, r, p);
                p.setStyle(Paint.Style.STROKE);
            } else {
                float d = type == RIGHT ? 1f : -1f;
                float tip = cx + d * s;
                float tail = cx - d * s;
                c.drawLine(tail, cy, tip, cy, p);
                c.drawLine(tip, cy, tip - d * s * 0.9f, cy - s * 0.9f, p);
                c.drawLine(tip, cy, tip - d * s * 0.9f, cy + s * 0.9f, p);
            }
        }
    }

    static class Tab {
        WebView web;
        boolean priv;
        boolean home = true;
        boolean desktop;
        String error;
        String pendingUrl;
        volatile String lastUrl;
        Bitmap thumb, favicon;
    }

    // цвета темы
    boolean dark;
    int bg, bar, card, text, sub, accent, brand, light, soft;

    Store store;
    final Handler ui = new Handler(Looper.getMainLooper());
    final List<Tab> tabs = new ArrayList<Tab>();
    Tab cur;

    // то, что реализует MainActivity
    abstract void go(String input);

    abstract void newTab(boolean priv);

    abstract void reloadMainUi();

    // ---------------------------------------------------------------- помощники

    int dp(int v) {
        return (int) (v * getResources().getDisplayMetrics().density + 0.5f);
    }

    GradientDrawable box(int color, int radiusDp) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(color);
        g.setCornerRadius(dp(radiusDp));
        return g;
    }

    GradientDrawable circle(int color) {
        GradientDrawable g = new GradientDrawable();
        g.setShape(GradientDrawable.OVAL);
        g.setColor(color);
        return g;
    }

    TextView tv(String s, int sp, int color) {
        TextView t = new TextView(this);
        t.setText(s);
        t.setTextSize(sp);
        t.setTextColor(color);
        return t;
    }

    void toast(String s) {
        Toast.makeText(this, s, Toast.LENGTH_SHORT).show();
    }

    void copy(String s) {
        ClipboardManager cm = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
        cm.setPrimaryClip(ClipData.newPlainText("text", s));
        toast("Скопировано");
    }

    AlertDialog.Builder builder() {
        return new AlertDialog.Builder(this, dark
                ? android.R.style.Theme_Material_Dialog_Alert
                : android.R.style.Theme_Material_Light_Dialog_Alert);
    }

    void confirm(String msg, final Runnable yes) {
        builder().setMessage(msg)
                .setPositiveButton("Да", (d, w) -> yes.run())
                .setNegativeButton("Нет", null)
                .show();
    }

    /** Волна при нажатии поверх текущего фона. */
    void rip(View v, int radiusDp) {
        v.setBackground(new RippleDrawable(
                ColorStateList.valueOf(0x33000000 | (accent & 0xFFFFFF)),
                v.getBackground(), box(Color.WHITE, radiusDp)));
    }

    /** Плавное нажатие: вибро-отклик, сжатие и пружинистый возврат. */
    void press(View v) {
        v.setOnTouchListener((x, e) -> {
            int a = e.getAction();
            if (a == MotionEvent.ACTION_DOWN) {
                x.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
                x.animate().scaleX(0.9f).scaleY(0.9f).setDuration(80)
                        .setInterpolator(new DecelerateInterpolator()).start();
            } else if (a == MotionEvent.ACTION_UP || a == MotionEvent.ACTION_CANCEL) {
                x.animate().scaleX(1f).scaleY(1f).setDuration(220)
                        .setInterpolator(new OvershootInterpolator(3f)).start();
            }
            return false;
        });
    }

    void hideKeyboard() {
        View f = getCurrentFocus();
        if (f != null) {
            InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
            imm.hideSoftInputFromWindow(f.getWindowToken(), 0);
            f.clearFocus();
        }
    }

    void showKb(final EditText e) {
        e.post(() -> {
            InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
            imm.showSoftInput(e, InputMethodManager.SHOW_IMPLICIT);
        });
    }

    String toUrl(String s) {
        s = s.trim();
        if (s.isEmpty()) return null;
        boolean looksUrl = !s.contains(" ")
                && (s.startsWith("http://") || s.startsWith("https://")
                || s.startsWith("localhost") || s.contains("."));
        if (!looksUrl) {
            try {
                return "https://yandex.ru/search/?text=" + URLEncoder.encode(s, "UTF-8");
            } catch (Exception e) {
                return null;
            }
        }
        if (!s.startsWith("http://") && !s.startsWith("https://")) s = "https://" + s;
        return s;
    }

    String hostOf(String url) {
        if (url == null) return "";
        String h = Uri.parse(url).getHost();
        return h == null ? "" : h;
    }

    boolean has(String p) {
        return Build.VERSION.SDK_INT < 23 || checkSelfPermission(p) == PackageManager.PERMISSION_GRANTED;
    }

    void ask(String[] perms, int code) {
        if (Build.VERSION.SDK_INT >= 23) requestPermissions(perms, code);
    }

    int avatarColor(String s) {
        return PAL[(s.hashCode() & 0x7fffffff) % PAL.length];
    }

    String firstLetter(String s) {
        s = s.trim();
        return s.isEmpty() ? "?" : s.substring(0, 1).toUpperCase();
    }

    String ext(String name) {
        int i = name.lastIndexOf('.');
        String e = (i >= 0 && i < name.length() - 1) ? name.substring(i + 1) : "FILE";
        if (e.length() > 4) e = e.substring(0, 4);
        return e.toUpperCase();
    }

    String clip(String s, int n) {
        return s.length() > n ? s.substring(0, n) : s;
    }

    String fmtSize(long b) {
        if (b <= 0) return "";
        if (b < 1024 * 1024) return String.format(Locale.getDefault(), "%.0f КБ", b / 1024.0);
        return String.format(Locale.getDefault(), "%.1f МБ", b / 1048576.0);
    }

    String safeName(String s) {
        s = s.replaceAll("[\\\\/:*?\"'<>|\\r\\n]", "_").trim();
        if (s.length() > 80) s = s.substring(0, 80);
        return s.isEmpty() ? "video" : s;
    }

    String dayLabel(long ts) {
        if (ts <= 0) return "Ранее";
        Calendar c = Calendar.getInstance();
        c.set(Calendar.HOUR_OF_DAY, 0);
        c.set(Calendar.MINUTE, 0);
        c.set(Calendar.SECOND, 0);
        c.set(Calendar.MILLISECOND, 0);
        long t0 = c.getTimeInMillis();
        if (ts >= t0) return "Сегодня";
        if (ts >= t0 - 86400000L) return "Вчера";
        return new SimpleDateFormat("d MMMM yyyy", new Locale("ru")).format(new Date(ts));
    }

    long size(File f) {
        if (f == null || !f.exists()) return 0;
        if (f.isFile()) return f.length();
        long s = 0;
        File[] c = f.listFiles();
        if (c != null) for (File x : c) s += size(x);
        return s;
    }

    void wipe(File dir) {
        File[] c = dir.listFiles();
        if (c == null) return;
        for (File x : c) {
            if (x.isDirectory()) wipe(x);
            x.delete();
        }
    }

    // ---------------------------------------------------------------- данные

    boolean isBookmarked(String url) {
        if (url == null) return false;
        for (String[] b : store.list("bookmarks")) if (b[1].equals(url)) return true;
        return false;
    }

    String siteState(String host) {
        for (String[] e : store.list("notif")) {
            if (e[0].equals(host)) return "1".equals(e[1]) ? "granted" : "denied";
        }
        return "default";
    }

    void setSiteState(String host, boolean allow) {
        List<String[]> l = store.list("notif");
        for (int i = 0; i < l.size(); i++) {
            if (l.get(i)[0].equals(host)) {
                l.remove(i);
                break;
            }
        }
        l.add(0, new String[]{host, allow ? "1" : "0"});
        store.save("notif", l);
    }

    // ---------------------------------------------------------------- тема

    int mix(int a, int b, float t) {
        return Color.rgb(
                (int) (Color.red(a) + (Color.red(b) - Color.red(a)) * t),
                (int) (Color.green(a) + (Color.green(b) - Color.green(a)) * t),
                (int) (Color.blue(a) + (Color.blue(b) - Color.blue(a)) * t));
    }

    Integer customColor(String key) {
        String s = store.getStr(key, "");
        if (s.isEmpty()) return null;
        try {
            return Integer.parseInt(s) | 0xFF000000;
        } catch (Exception e) {
            return null;
        }
    }

    void applyTheme() {
        String mode = store.getStr("theme", "light");
        boolean sysDark = (getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK)
                == Configuration.UI_MODE_NIGHT_YES;
        dark = "dark".equals(mode) || ("system".equals(mode) && sysDark);
        Integer cb = customColor("c_bg");
        Integer ca = customColor("c_accent");
        if (cb != null) {
            bg = cb;
            dark = Color.red(bg) * 0.299 + Color.green(bg) * 0.587 + Color.blue(bg) * 0.114 < 128;
            card = mix(bg, 0xFFFFFFFF, dark ? 0.10f : 0.65f);
            bar = mix(bg, 0xFFFFFFFF, dark ? 0.05f : 0.40f);
        } else {
            bg = dark ? 0xFF15131F : 0xFFF3F0FF;
            card = dark ? 0xFF2A2740 : 0xFFFFFFFF;
            bar = dark ? 0xFF1C1A29 : 0xFFF8F6FF;
        }
        text = dark ? 0xFFEDEAFF : 0xFF1B1B4B;
        sub = dark ? 0xFFA7A1CF : 0xFF7A74A8;
        accent = ca != null ? ca : (dark ? 0xFFB9A8FF : 0xFF5B4FA0);
        brand = ca != null ? ca : VIOLET;
        light = mix(bg, accent, dark ? 0.35f : 0.22f);
        soft = mix(bg, accent, dark ? 0.25f : 0.13f);

        getWindow().setStatusBarColor(bg);
        getWindow().setNavigationBarColor(bar);
        if (Build.VERSION.SDK_INT >= 23) {
            View dv = getWindow().getDecorView();
            int f = dv.getSystemUiVisibility();
            if (dark) f &= ~View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR;
            else f |= View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR;
            dv.setSystemUiVisibility(f);
        }
    }

    // ---------------------------------------------------------------- полноэкранные страницы и строки

    Dialog fullScreen() {
        Dialog d = new Dialog(this, android.R.style.Theme_Material_NoActionBar);
        Window w = d.getWindow();
        if (w != null) {
            w.setBackgroundDrawable(new ColorDrawable(bg));
            w.setLayout(MATCH, MATCH);
            w.setStatusBarColor(bg);
            w.setNavigationBarColor(bar);
            if (Build.VERSION.SDK_INT >= 23) {
                View dv = w.getDecorView();
                int f = dv.getSystemUiVisibility();
                dv.setSystemUiVisibility(dark
                        ? (f & ~View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR)
                        : (f | View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR));
            }
        }
        return d;
    }

    /** Полноэкранная страница: стрелка назад, заголовок, кнопки справа и прокручиваемое содержимое. */
    Dialog screen(String title, View body, boolean animate, View... actions) {
        final Dialog d = fullScreen();
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(bg);
        root.setFitsSystemWindows(true);

        LinearLayout tb = new LinearLayout(this);
        tb.setGravity(Gravity.CENTER_VERTICAL);
        tb.setPadding(dp(8), dp(6), dp(8), dp(6));
        Ico back = new Ico(this, Ico.LEFT, accent);
        press(back);
        back.setOnClickListener(v -> d.dismiss());
        tb.addView(back, new LinearLayout.LayoutParams(dp(48), dp(48)));
        TextView t = tv(title, 21, text);
        t.setTypeface(null, Typeface.BOLD);
        LinearLayout.LayoutParams tp = new LinearLayout.LayoutParams(0, WRAP, 1f);
        tp.leftMargin = dp(8);
        tb.addView(t, tp);
        for (View a : actions) tb.addView(a, new LinearLayout.LayoutParams(dp(48), dp(48)));
        root.addView(tb, new LinearLayout.LayoutParams(MATCH, WRAP));

        ScrollView sv = new ScrollView(this);
        sv.addView(body, new ViewGroup.LayoutParams(MATCH, WRAP));
        root.addView(sv, new LinearLayout.LayoutParams(MATCH, 0, 1f));

        d.setContentView(root);
        d.show();
        if (animate) {
            root.setAlpha(0f);
            root.setTranslationX(dp(48));
            root.animate().alpha(1f).translationX(0f).setDuration(260)
                    .setInterpolator(new DecelerateInterpolator()).start();
        }
        return d;
    }

    TextView bigButton(String label, final Runnable r) {
        TextView b = tv(label, 16, Color.WHITE);
        b.setTypeface(null, Typeface.BOLD);
        b.setGravity(Gravity.CENTER);
        b.setBackground(box(brand, 24));
        b.setPadding(dp(20), dp(14), dp(20), dp(14));
        rip(b, 24);
        press(b);
        b.setOnClickListener(v -> r.run());
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(MATCH, WRAP);
        lp.topMargin = dp(12);
        b.setLayoutParams(lp);
        return b;
    }

    TextView actionBtn(String glyph, View.OnClickListener l) {
        TextView t = tv(glyph, 18, accent);
        t.setGravity(Gravity.CENTER);
        rip(t, 22);
        press(t);
        t.setOnClickListener(l);
        return t;
    }

    View menuRow(String glyph, String name, String desc, View.OnClickListener l) {
        LinearLayout row = new LinearLayout(this);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(4), dp(10), dp(4), dp(10));
        TextView ic = tv(glyph, 22, accent);
        ic.setGravity(Gravity.CENTER);
        ic.setBackground(box(soft, 16));
        row.addView(ic, new LinearLayout.LayoutParams(dp(52), dp(52)));
        LinearLayout tx = new LinearLayout(this);
        tx.setOrientation(LinearLayout.VERTICAL);
        tx.addView(tv(name, 18, text));
        tx.addView(tv(desc, 14, sub));
        LinearLayout.LayoutParams txp = new LinearLayout.LayoutParams(0, WRAP, 1f);
        txp.leftMargin = dp(16);
        row.addView(tx, txp);
        row.addView(tv("\u203A", 30, sub), new LinearLayout.LayoutParams(WRAP, WRAP));
        rip(row, 16);
        press(row);
        row.setOnClickListener(l);
        return row;
    }

    View listRow(String avatar, int color, String title, String subtitle,
                 View.OnClickListener click, View.OnClickListener more) {
        LinearLayout row = new LinearLayout(this);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(16), dp(10), dp(4), dp(10));

        TextView av = tv(avatar, avatar.length() > 2 ? 11 : 17, Color.WHITE);
        av.setTypeface(null, Typeface.BOLD);
        av.setGravity(Gravity.CENTER);
        av.setBackground(circle(color));
        row.addView(av, new LinearLayout.LayoutParams(dp(44), dp(44)));

        LinearLayout tx = new LinearLayout(this);
        tx.setOrientation(LinearLayout.VERTICAL);
        TextView t1 = tv(title, 16, text);
        t1.setSingleLine(true);
        t1.setEllipsize(TextUtils.TruncateAt.END);
        TextView t2 = tv(subtitle, 13, sub);
        t2.setSingleLine(true);
        t2.setEllipsize(TextUtils.TruncateAt.END);
        tx.addView(t1);
        tx.addView(t2);
        LinearLayout.LayoutParams txp = new LinearLayout.LayoutParams(0, WRAP, 1f);
        txp.leftMargin = dp(14);
        row.addView(tx, txp);

        if (more != null) {
            Ico dots = new Ico(this, Ico.DOTS, sub);
            dots.setOnClickListener(more);
            row.addView(dots, new LinearLayout.LayoutParams(dp(44), dp(44)));
        }
        rip(row, 0);
        row.setOnClickListener(click);
        return row;
    }

    void popup(View anchor, String[] labels, final Runnable[] acts) {
        PopupMenu pm = new PopupMenu(this, anchor);
        for (int i = 0; i < labels.length; i++) pm.getMenu().add(0, i, i, labels[i]);
        pm.setOnMenuItemClickListener(item -> {
            acts[item.getItemId()].run();
            return true;
        });
        pm.show();
    }
}
