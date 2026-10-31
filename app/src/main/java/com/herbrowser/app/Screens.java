package com.herbrowser.app;

import android.Manifest;
import android.app.DownloadManager;
import android.app.admin.DevicePolicyManager;
import android.content.ComponentName;
import android.app.Dialog;
import android.content.ActivityNotFoundException;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.res.ColorStateList;
import android.database.Cursor;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ComposeShader;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.RadialGradient;
import android.graphics.Shader;
import android.graphics.SweepGradient;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.media.MediaScannerConnection;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.provider.DocumentsContract;
import android.provider.MediaStore;
import android.provider.Settings;
import android.text.Editable;
import android.text.InputType;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.webkit.CookieManager;
import android.webkit.MimeTypeMap;
import android.webkit.URLUtil;
import android.widget.EditText;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.widget.TextView;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.Locale;

abstract class Screens extends Base {

    static final String APK_MIME = "application/vnd.android.package-archive";

    Dialog themeDlg, settingsDlg;

    // ---------------------------------------------------------------- Настройки (на весь экран)

    void showSettings(boolean animate) {
        LinearLayout body = new LinearLayout(this);
        body.setOrientation(LinearLayout.VERTICAL);
        body.setPadding(dp(16), dp(4), dp(16), dp(16));
        String[] g = {"\uD83C\uDFA8", "\uD83D\uDCBE", "\uD83D\uDD14", "\uD83D\uDD0B", "\uD83D\uDD12", "\u2139"};
        String[] n = {"Оформление", "Память", "Уведомления сайтов", "Экономия аккумулятора", "Безопасность", "О приложении"};
        String[] ds = {"Тема и свои цвета", "Кэш и сохранённые данные",
                "Сайты и мессенджеры", "Включать при низком заряде",
                "Родительский контроль, защита, установки", "Версия и сведения"};
        final Runnable[] acts = {() -> showThemeScreen(true), () -> showMemoryScreen(),
                () -> showNotifScreen(), () -> showSaverScreen(), () -> showSecurityScreen(),
                () -> showAboutScreen()};
        for (int i = 0; i < n.length; i++) {
            final int idx = i;
            body.addView(menuRow(g[i], n[i], ds[i], v -> acts[idx].run()),
                    new LinearLayout.LayoutParams(MATCH, WRAP));
        }
        settingsDlg = screen("Настройки", body, animate);
    }

    /** Выбор цвета: обновляется экран «Оформление», всё приложение обновится при выходе. */
    void recolor() {
        applyTheme();
        showThemeScreen(false);
    }

    /** Нажали «назад» в «Оформлении»: обновляем всё приложение сразу. */
    void onThemeClosed() {
        if (isFinishing()) return;
        applyTheme();
        reloadMainUi();
        if (settingsDlg != null && settingsDlg.isShowing()) {
            settingsDlg.dismiss();
            showSettings(false);
        }
    }

    void showThemeScreen(boolean animate) {
        if (themeDlg != null) {
            themeDlg.setOnDismissListener(null);
            themeDlg.dismiss();
        }
        LinearLayout body = new LinearLayout(this);
        body.setOrientation(LinearLayout.VERTICAL);
        body.setPadding(dp(16), dp(4), dp(16), dp(24));

        TextView t0 = tv("Режим", 16, text);
        t0.setTypeface(null, Typeface.BOLD);
        body.addView(t0);
        LinearLayout chips = new LinearLayout(this);
        final String[] vals = {"light", "dark", "system"};
        String[] labs = {"Светлая", "Тёмная", "Как в системе"};
        String mode = store.getStr("theme", "light");
        boolean customBg = customColor("c_bg") != null;
        for (int i = 0; i < 3; i++) {
            final String val = vals[i];
            boolean sel = val.equals(mode) && !customBg;
            TextView c = tv(labs[i], 14, sel ? Color.WHITE : text);
            c.setBackground(box(sel ? brand : card, 20));
            c.setPadding(dp(14), dp(10), dp(14), dp(10));
            rip(c, 20);
            press(c);
            c.setOnClickListener(v -> {
                store.setStr("theme", val);
                store.setStr("c_bg", "");
                recolor();
            });
            LinearLayout.LayoutParams cp = new LinearLayout.LayoutParams(WRAP, WRAP);
            cp.topMargin = dp(10);
            cp.rightMargin = dp(8);
            chips.addView(c, cp);
        }
        body.addView(chips);

        body.addView(colorSection("Основной цвет", "c_accent", ACCENTS));
        body.addView(colorSection("Цвет фона", "c_bg", BGS));
        body.addView(bigButton("Сбросить цвета", () -> {
            store.setStr("c_accent", "");
            store.setStr("c_bg", "");
            store.setStr("theme", "light");
            recolor();
        }));
        themeDlg = screen("Оформление", body, animate);
        themeDlg.setOnDismissListener(dd -> onThemeClosed());
    }

    static final int[] SW = {0xFFE53030, 0xFFFF8F0A, 0xFFFFDC1A, 0xFF2FB344, 0xFF12B5D4, 0xFF1E6FE8,
            0xFF8A2BE2, 0xFFF0508F, 0xFFF783B0, 0xFF7F4A2F, 0xFF9AA0AA, 0xFF0D0D0D,
            0xFFF4F4F6, 0xFFB388F5, 0xFF5252B8, 0xFF74A3F5, 0xFF80D0B0, 0xFFE5CDB8};
    static final String[] SW_NAMES = {"Красный", "Оранжевый", "Жёлтый", "Зелёный", "Бирюзовый", "Синий",
            "Фиолетовый", "Розовый", "Светло-розовый", "Коричневый", "Серый", "Чёрный",
            "Белый", "Сиреневый", "Индиго", "Голубой", "Мятный", "Бежевый"};

    String colorName(int c) {
        int best = 0;
        long bd = Long.MAX_VALUE;
        for (int i = 0; i < SW.length; i++) {
            long dr = Color.red(c) - Color.red(SW[i]);
            long dg = Color.green(c) - Color.green(SW[i]);
            long db = Color.blue(c) - Color.blue(SW[i]);
            long dist = dr * dr * 3 + dg * dg * 4 + db * db * 2;
            if (dist < bd) {
                bd = dist;
                best = i;
            }
        }
        return SW_NAMES[best];
    }

    String hexOf(int c) {
        return String.format("#%06X", c & 0xFFFFFF);
    }

    /** Цветовой круг: оттенок по углу, насыщенность по расстоянию от центра. */
    static class ColorWheel extends View {
        interface Listener {
            void onColor(int color);
        }

        final Paint wp = new Paint(Paint.ANTI_ALIAS_FLAG);
        final Paint dim = new Paint(Paint.ANTI_ALIAS_FLAG);
        final Paint ring = new Paint(Paint.ANTI_ALIAS_FLAG);
        final Paint dot = new Paint(Paint.ANTI_ALIAS_FLAG);
        float hue = 270f, sat = 1f, val = 1f, cx, cy, r;
        Listener listener;

        ColorWheel(Context c) {
            super(c);
            float d = c.getResources().getDisplayMetrics().density;
            ring.setStyle(Paint.Style.STROKE);
            ring.setColor(Color.WHITE);
            ring.setStrokeWidth(3f * d);
            dot.setStyle(Paint.Style.FILL);
        }

        int color() {
            return Color.HSVToColor(new float[]{hue, sat, val});
        }

        void setColor(int c) {
            float[] h = new float[3];
            Color.colorToHSV(c, h);
            hue = h[0];
            sat = h[1];
            val = h[2];
            invalidate();
        }

        void setVal(float v) {
            val = v;
            invalidate();
            if (listener != null) listener.onColor(color());
        }

        @Override
        protected void onSizeChanged(int w, int h, int ow, int oh) {
            cx = w / 2f;
            cy = h / 2f;
            r = Math.min(w, h) / 2f - 4f * getResources().getDisplayMetrics().density;
            int[] cols = {0xFFFF0000, 0xFFFFFF00, 0xFF00FF00, 0xFF00FFFF, 0xFF0000FF, 0xFFFF00FF, 0xFFFF0000};
            Shader sweep = new SweepGradient(cx, cy, cols, null);
            Shader radial = new RadialGradient(cx, cy, r, 0xFFFFFFFF, 0x00FFFFFF, Shader.TileMode.CLAMP);
            wp.setShader(new ComposeShader(sweep, radial, PorterDuff.Mode.SRC_OVER));
        }

        @Override
        protected void onDraw(Canvas c) {
            c.drawCircle(cx, cy, r, wp);
            dim.setColor(Color.argb((int) ((1f - val) * 255), 0, 0, 0));
            c.drawCircle(cx, cy, r, dim);
            double a = Math.toRadians(hue);
            float x = cx + (float) Math.cos(a) * sat * r;
            float y = cy + (float) Math.sin(a) * sat * r;
            float d = getResources().getDisplayMetrics().density;
            dot.setColor(color());
            c.drawCircle(x, y, 11f * d, dot);
            c.drawCircle(x, y, 11f * d, ring);
        }

        @Override
        public boolean onTouchEvent(MotionEvent e) {
            int a = e.getAction();
            if (a == MotionEvent.ACTION_DOWN || a == MotionEvent.ACTION_MOVE) {
                getParent().requestDisallowInterceptTouchEvent(true);
                float dx = e.getX() - cx;
                float dy = e.getY() - cy;
                sat = Math.min(1f, (float) Math.sqrt(dx * dx + dy * dy) / r);
                hue = (float) ((Math.toDegrees(Math.atan2(dy, dx)) + 360.0) % 360.0);
                invalidate();
                if (listener != null) listener.onColor(color());
            }
            return true;
        }
    }

    /** Блок «Основной цвет» / «Цвет фона»: текущий цвет и кнопка «Выбрать цвет». */
    View colorSection(String title, final String key, int[] presets) {
        LinearLayout sec = new LinearLayout(this);
        sec.setOrientation(LinearLayout.VERTICAL);
        sec.setPadding(0, dp(20), 0, 0);
        TextView t = tv(title, 16, text);
        t.setTypeface(null, Typeface.BOLD);
        sec.addView(t);

        Integer curC = customColor(key);
        final int shown = curC != null ? curC.intValue() : ("c_bg".equals(key) ? bg : accent);
        LinearLayout row = new LinearLayout(this);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(14), dp(12), dp(14), dp(12));
        row.setBackground(box(card, 18));
        View sw = new View(this);
        GradientDrawable g = new GradientDrawable();
        g.setShape(GradientDrawable.OVAL);
        g.setColor(shown);
        g.setStroke(dp(1), sub);
        sw.setBackground(g);
        row.addView(sw, new LinearLayout.LayoutParams(dp(40), dp(40)));
        LinearLayout tx = new LinearLayout(this);
        tx.setOrientation(LinearLayout.VERTICAL);
        tx.addView(tv(colorName(shown), 15, text));
        tx.addView(tv(hexOf(shown) + (curC == null ? " (по умолчанию)" : ""), 12, sub));
        LinearLayout.LayoutParams txp = new LinearLayout.LayoutParams(0, WRAP, 1f);
        txp.leftMargin = dp(12);
        row.addView(tx, txp);
        TextView pick = tv("Выбрать цвет", 14, Color.WHITE);
        pick.setTypeface(null, Typeface.BOLD);
        pick.setBackground(box(brand, 20));
        pick.setPadding(dp(16), dp(10), dp(16), dp(10));
        rip(pick, 20);
        press(pick);
        pick.setOnClickListener(v -> showColorPicker(key, shown));
        row.addView(pick);
        LinearLayout.LayoutParams rp = new LinearLayout.LayoutParams(MATCH, WRAP);
        rp.topMargin = dp(10);
        sec.addView(row, rp);
        return sec;
    }

    /** Экран «Выбор цвета»: круг, готовые цвета, «Сохранить». */
    void showColorPicker(final String key, int initial) {
        final Dialog[] dlg = new Dialog[1];
        final int[] chosen = {initial | 0xFF000000};
        LinearLayout body = new LinearLayout(this);
        body.setOrientation(LinearLayout.VERTICAL);
        body.setPadding(dp(16), dp(8), dp(16), dp(24));

        LinearLayout top = new LinearLayout(this);
        final ColorWheel wheel = new ColorWheel(this);
        top.addView(wheel, new LinearLayout.LayoutParams(dp(190), dp(190)));

        LinearLayout pv = new LinearLayout(this);
        pv.setOrientation(LinearLayout.VERTICAL);
        pv.setGravity(Gravity.CENTER);
        pv.setPadding(dp(10), dp(10), dp(10), dp(10));
        pv.setBackground(box(card, 20));
        final GradientDrawable pg = new GradientDrawable();
        pg.setShape(GradientDrawable.OVAL);
        View big = new View(this);
        big.setBackground(pg);
        pv.addView(big, new LinearLayout.LayoutParams(dp(84), dp(84)));
        final TextView nameTv = tv("", 15, text);
        nameTv.setTypeface(null, Typeface.BOLD);
        nameTv.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams nlp = new LinearLayout.LayoutParams(WRAP, WRAP);
        nlp.topMargin = dp(10);
        pv.addView(nameTv, nlp);
        final TextView hexTv = tv("", 13, sub);
        pv.addView(hexTv);
        LinearLayout.LayoutParams pvp = new LinearLayout.LayoutParams(0, dp(190), 1f);
        pvp.leftMargin = dp(12);
        top.addView(pv, pvp);
        body.addView(top);

        TextView bl = tv("Яркость", 13, sub);
        bl.setPadding(dp(4), dp(12), 0, 0);
        body.addView(bl);
        final SeekBar bright = new SeekBar(this);
        bright.setMax(100);
        bright.setProgressTintList(ColorStateList.valueOf(brand));
        bright.setThumbTintList(ColorStateList.valueOf(brand));
        body.addView(bright, new LinearLayout.LayoutParams(MATCH, WRAP));

        final List<GradientDrawable> ringDraw = new ArrayList<GradientDrawable>();
        final Runnable[] update = new Runnable[1];
        update[0] = () -> {
            pg.setColor(chosen[0]);
            nameTv.setText(colorName(chosen[0]));
            hexTv.setText(hexOf(chosen[0]));
            for (int i = 0; i < SW.length; i++) {
                boolean sel = (SW[i] & 0xFFFFFF) == (chosen[0] & 0xFFFFFF);
                ringDraw.get(i).setStroke(dp(sel ? 3 : 1), sel ? brand : light);
            }
        };

        for (int rowI = 0; rowI < 3; rowI++) {
            LinearLayout gr = new LinearLayout(this);
            gr.setGravity(Gravity.CENTER);
            for (int k = 0; k < 6; k++) {
                final int idx = rowI * 6 + k;
                GradientDrawable g = new GradientDrawable();
                g.setShape(GradientDrawable.OVAL);
                g.setColor(SW[idx]);
                ringDraw.add(g);
                View sw = new View(this);
                sw.setBackground(g);
                press(sw);
                sw.setOnClickListener(v -> {
                    chosen[0] = SW[idx];
                    wheel.setColor(SW[idx]);
                    bright.setProgress(Math.round(wheel.val * 100f));
                    update[0].run();
                });
                LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, dp(46), 1f);
                lp.setMargins(dp(5), dp(6), dp(5), dp(6));
                gr.addView(sw, lp);
            }
            body.addView(gr, new LinearLayout.LayoutParams(MATCH, WRAP));
        }

        wheel.listener = c -> {
            chosen[0] = c;
            update[0].run();
        };
        bright.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar sb, int p, boolean fromUser) {
                if (fromUser) wheel.setVal(p / 100f);
            }

            @Override
            public void onStartTrackingTouch(SeekBar sb) {
            }

            @Override
            public void onStopTrackingTouch(SeekBar sb) {
            }
        });
        wheel.setColor(chosen[0]);
        bright.setProgress(Math.round(wheel.val * 100f));
        update[0].run();

        body.addView(bigButton("\uD83D\uDCBE  Сохранить", () -> {
            store.setStr(key, String.valueOf(chosen[0]));
            dlg[0].dismiss();
            recolor();
        }));
        dlg[0] = screen("Выбор цвета", body, true);
    }

    void showMemoryScreen() {
        LinearLayout body = new LinearLayout(this);
        body.setOrientation(LinearLayout.VERTICAL);
        body.setPadding(dp(16), dp(4), dp(16), dp(16));
        String[][] stats = {
                {"Закладки", String.valueOf(store.list("bookmarks").size())},
                {"История", String.valueOf(store.list("history").size())},
                {"Пароли", String.valueOf(store.list("passwords").size())},
                {"Загрузки", String.valueOf(store.list("downloads").size())},
                {"Кэш", String.format(Locale.getDefault(), "%.1f МБ", size(getCacheDir()) / 1048576.0)}};
        for (String[] s : stats) {
            LinearLayout r = new LinearLayout(this);
            r.setGravity(Gravity.CENTER_VERTICAL);
            r.setPadding(dp(16), dp(14), dp(16), dp(14));
            r.setBackground(box(card, 14));
            r.addView(tv(s[0], 16, text), new LinearLayout.LayoutParams(0, WRAP, 1f));
            TextView v = tv(s[1], 16, accent);
            v.setTypeface(null, Typeface.BOLD);
            r.addView(v);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(MATCH, WRAP);
            lp.bottomMargin = dp(8);
            body.addView(r, lp);
        }
        body.addView(bigButton("Очистить кэш", () -> {
            for (Tab t : tabs) {
                t.web.clearCache(true);
                t.thumb = null;
            }
            wipe(getCacheDir());
            toast("Кэш очищен. При следующем запуске появится приветствие");
        }));
        body.addView(bigButton("Очистить историю", () ->
                confirm("Очистить историю браузера?", () -> {
                    if (pcDeny("hist")) return;
                    store.clear("history");
                    toast("История очищена");
                })));
        body.addView(bigButton("Сбросить запомненные разрешения сайтов", () -> {
            store.clear("sitePerm");
            toast("Сайты снова будут спрашивать разрешения");
        }));
        screen("Память", body, true);
    }

    void showNotifScreen() {
        final LinearLayout body = new LinearLayout(this);
        body.setOrientation(LinearLayout.VERTICAL);
        final Runnable[] render = new Runnable[1];
        render[0] = () -> {
            body.removeAllViews();
            List<String[]> l = store.list("notif");
            if (l.isEmpty()) {
                TextView e = tv("Пока ни один сайт не просил уведомления", 15, sub);
                e.setPadding(dp(24), dp(32), dp(24), dp(24));
                body.addView(e);
            }
            for (int i = 0; i < l.size(); i++) {
                final String[] e = l.get(i);
                final boolean on = "1".equals(e[1]);
                body.addView(listRow(firstLetter(e[0]), avatarColor(e[0]), e[0],
                        on ? "Уведомления включены" : "Уведомления выключены",
                        v -> {
                            setSiteState(e[0], !on);
                            render[0].run();
                        },
                        v -> popup(v, new String[]{on ? "Выключить" : "Включить", "Удалить"}, new Runnable[]{
                                () -> {
                                    setSiteState(e[0], !on);
                                    render[0].run();
                                },
                                () -> {
                                    List<String[]> all = store.list("notif");
                                    for (int k = 0; k < all.size(); k++) {
                                        if (all.get(k)[0].equals(e[0])) {
                                            all.remove(k);
                                            break;
                                        }
                                    }
                                    store.save("notif", all);
                                    render[0].run();
                                }})));
            }
        };
        screen("Уведомления сайтов", body, true);
        render[0].run();
    }

    void showAboutScreen() {
        LinearLayout body = new LinearLayout(this);
        body.setOrientation(LinearLayout.VERTICAL);
        body.setGravity(Gravity.CENTER_HORIZONTAL);
        body.setPadding(dp(24), dp(40), dp(24), dp(24));
        TextView logo = tv("H\u2665", 34, Color.WHITE);
        logo.setTypeface(null, Typeface.BOLD);
        logo.setGravity(Gravity.CENTER);
        GradientDrawable lg = new GradientDrawable(GradientDrawable.Orientation.TL_BR,
                new int[]{0xFFFF5FA8, brand});
        lg.setCornerRadius(dp(22));
        logo.setBackground(lg);
        body.addView(logo, new LinearLayout.LayoutParams(dp(96), dp(96)));
        TextView n = tv("HerBrowser", 26, text);
        n.setTypeface(null, Typeface.BOLD);
        LinearLayout.LayoutParams np = new LinearLayout.LayoutParams(WRAP, WRAP);
        np.topMargin = dp(18);
        body.addView(n, np);
        TextView desc = tv("HerBrowser \u2014 это приложение-браузер, которое вы сможете настроить под себя, "
                + "открывать в нём любые сайты без ограничений, сохранять важное и работать в интернете "
                + "так, как удобно именно вам.", 16, sub);
        desc.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams dsp = new LinearLayout.LayoutParams(WRAP, WRAP);
        dsp.topMargin = dp(16);
        body.addView(desc, dsp);
        LinearLayout vr = new LinearLayout(this);
        vr.setGravity(Gravity.CENTER_VERTICAL);
        vr.setPadding(dp(16), dp(14), dp(16), dp(14));
        vr.setBackground(box(card, 16));
        vr.addView(tv("Версия " + VERSION, 16, text), new LinearLayout.LayoutParams(0, WRAP, 1f));
        TextView cp = tv("Скопировать", 14, accent);
        cp.setTypeface(null, Typeface.BOLD);
        vr.addView(cp);
        rip(vr, 16);
        press(vr);
        vr.setOnClickListener(v -> copy("HerBrowser " + VERSION));
        LinearLayout.LayoutParams vlp = new LinearLayout.LayoutParams(MATCH, WRAP);
        vlp.topMargin = dp(24);
        body.addView(vr, vlp);
        screen("О приложении", body, true);
    }

    // ---------------------------------------------------------------- закладки, история, загрузки, пароли

    void addBookmarkRaw(String title, String url) {
        if (isBookmarked(url)) {
            toast("Уже в закладках");
            return;
        }
        store.add("bookmarks", new String[]{title, url}, 500);
        toast("Добавлено в закладки");
    }

    /** mode: 0 закладки, 1 история, 2 загрузки, 3 пароли */
    void showListScreen(final int mode) {
        final String[] keys = {"bookmarks", "history", "downloads", "passwords"};
        final String[] titles = {"Закладки", "История", "Загрузки", "Пароли"};
        final String key = keys[mode];
        final Dialog[] dlg = new Dialog[1];
        final boolean[] running = {false};

        LinearLayout body = new LinearLayout(this);
        body.setOrientation(LinearLayout.VERTICAL);
        final EditText q = new EditText(this);
        q.setHint("Поиск");
        q.setSingleLine(true);
        q.setTextSize(15);
        q.setTextColor(text);
        q.setHintTextColor(sub);
        q.setBackground(box(card, 24));
        q.setPadding(dp(18), dp(10), dp(18), dp(10));
        LinearLayout.LayoutParams qp = new LinearLayout.LayoutParams(MATCH, WRAP);
        qp.setMargins(dp(16), dp(4), dp(16), dp(8));
        body.addView(q, qp);
        final LinearLayout list = new LinearLayout(this);
        list.setOrientation(LinearLayout.VERTICAL);
        body.addView(list, new LinearLayout.LayoutParams(MATCH, WRAP));

        final Runnable[] render = new Runnable[1];
        render[0] = () -> {
            list.removeAllViews();
            running[0] = false;
            String f = q.getText().toString().trim().toLowerCase(Locale.getDefault());
            List<String[]> items = store.list(key);
            String lastDay = null;
            int shown = 0;
            if (mode == 2) {
                for (final Dl dl : new ArrayList<Dl>(active)) {
                    running[0] = true;
                    shown++;
                    list.addView(listRow(ext(dl.name), avatarColor(ext(dl.name)), dl.name, dl.status,
                            v -> { },
                            v -> popup(v, new String[]{"Отменить"}, new Runnable[]{() -> {
                                dl.cancel = true;
                                render[0].run();
                            }})));
                }
            }
            for (int i = 0; i < items.size(); i++) {
                final int idx = i;
                final String[] it = items.get(i);
                String hay = (it[0] + " " + (mode == 2 ? "" : (it.length > 1 ? it[1] : ""))).toLowerCase(Locale.getDefault());
                if (!f.isEmpty() && !hay.contains(f)) continue;
                shown++;

                if (mode == 0 || mode == 1) {
                    if (mode == 1) {
                        String day = dayLabel(it.length > 2 ? Long.parseLong(it[2]) : 0);
                        if (!day.equals(lastDay)) {
                            lastDay = day;
                            TextView h = tv(day, 14, accent);
                            h.setTypeface(null, Typeface.BOLD);
                            h.setPadding(dp(20), dp(14), dp(20), dp(4));
                            list.addView(h);
                        }
                    }
                    final Runnable openNew = () -> {
                        dlg[0].dismiss();
                        newTab(false);
                        go(it[1]);
                    };
                    final Runnable del = () -> {
                        if (mode == 1 && pcDeny("hist")) return;
                        store.remove(key, idx);
                        render[0].run();
                    };
                    list.addView(listRow(firstLetter(it[0]), avatarColor(it[1]), it[0], it[1],
                            v -> {
                                dlg[0].dismiss();
                                go(it[1]);
                            },
                            v -> popup(v,
                                    mode == 0
                                            ? new String[]{"Открыть в новой вкладке", "Копировать ссылку", "Удалить"}
                                            : new String[]{"Открыть в новой вкладке", "Копировать ссылку", "В закладки", "Удалить"},
                                    mode == 0
                                            ? new Runnable[]{openNew, () -> copy(it[1]), del}
                                            : new Runnable[]{openNew, () -> copy(it[1]),
                                            () -> addBookmarkRaw(it[0], it[1]), del})));
                } else if (mode == 2) {
                    String st = entryStatus(it);
                    if (st.startsWith("Загрузка")) running[0] = true;
                    list.addView(listRow(ext(it[0]), avatarColor(ext(it[0])), it[0], st,
                            v -> openEntry(it),
                            v -> popup(v, new String[]{"Открыть", "Удалить"}, new Runnable[]{
                                    () -> openEntry(it),
                                    () -> {
                                        if (pcDeny("dl")) return;
                                        deleteEntry(it);
                                        store.remove(key, idx);
                                        render[0].run();
                                    }})));
                } else {
                    View row = listRow(firstLetter(it[0]), avatarColor(it[0]), it[0],
                            it[1].isEmpty() ? "Без имени пользователя" : it[1],
                            v -> showPassword(it),
                            v -> popup(v, new String[]{"Копировать пароль", "Копировать имя", "Удалить"}, new Runnable[]{
                                    () -> copy(it[2]),
                                    () -> copy(it[1]),
                                    () -> confirm("Удалить пароль?", () -> {
                                        if (pcDeny("pw")) return;
                                        store.remove(key, idx);
                                        render[0].run();
                                    })}));
                    row.setOnLongClickListener(v -> {
                        confirm("Удалить пароль?", () -> {
                            if (pcDeny("pw")) return;
                            store.remove(key, idx);
                            render[0].run();
                        });
                        return true;
                    });
                    list.addView(row);
                }
            }
            if (shown == 0) {
                TextView e = tv(f.isEmpty() ? "Пока пусто" : "Ничего не найдено", 16, sub);
                e.setGravity(Gravity.CENTER);
                e.setPadding(dp(24), dp(48), dp(24), dp(24));
                list.addView(e, new LinearLayout.LayoutParams(MATCH, WRAP));
            }
            ui.removeCallbacks(render[0]);
            if (mode == 2 && running[0] && dlg[0] != null && dlg[0].isShowing()) {
                ui.postDelayed(render[0], 1000);
            }
        };
        q.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int a, int b, int c) {
            }

            @Override
            public void onTextChanged(CharSequence s, int a, int b, int c) {
            }

            @Override
            public void afterTextChanged(Editable s) {
                render[0].run();
            }
        });

        View[] acts = new View[0];
        if (mode == 1) {
            acts = new View[]{actionBtn("\uD83D\uDDD1", v ->
                    confirm("Очистить историю браузера?", () -> {
                        if (pcDeny("hist")) return;
                        store.clear("history");
                        render[0].run();
                    }))};
        } else if (mode == 2) {
            acts = new View[]{actionBtn("\u2699", v -> showDlSettings())};
        } else if (mode == 3) {
            acts = new View[]{actionBtn("\uFF0B", v -> addPasswordDialog(() -> render[0].run()))};
        }
        dlg[0] = screen(titles[mode], body, true, acts);
        render[0].run();
    }

    EditText dlgField(String hint, int inputType) {
        EditText e = new EditText(this);
        e.setHint(hint);
        e.setSingleLine(true);
        e.setInputType(inputType);
        return e;
    }

    void addPasswordDialog(final Runnable done) {
        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setPadding(dp(20), dp(8), dp(20), 0);
        final EditText site = dlgField("Сайт", InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_URI);
        final EditText user = dlgField("Имя пользователя", InputType.TYPE_CLASS_TEXT);
        final EditText pass = dlgField("Пароль", InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        col.addView(site, new LinearLayout.LayoutParams(MATCH, WRAP));
        col.addView(user, new LinearLayout.LayoutParams(MATCH, WRAP));
        col.addView(pass, new LinearLayout.LayoutParams(MATCH, WRAP));
        builder().setTitle("Новый пароль")
                .setView(col)
                .setPositiveButton("Сохранить", (d, w) -> {
                    String s = site.getText().toString().trim();
                    String p = pass.getText().toString();
                    if (s.isEmpty() || p.isEmpty()) {
                        toast("Введите сайт и пароль");
                    } else {
                        store.add("passwords", new String[]{s, user.getText().toString().trim(), p}, 500);
                        toast("Пароль сохранён");
                    }
                    done.run();
                })
                .setNegativeButton("Отмена", null)
                .show();
    }

    void showPassword(final String[] it) {
        builder().setTitle(it[0])
                .setMessage("Имя пользователя: " + it[1] + "\nПароль: " + it[2])
                .setPositiveButton("Копировать пароль", (d, w) -> copy(it[2]))
                .setNegativeButton("Закрыть", null)
                .show();
    }

    // ---------------------------------------------------------------- куда сохранять загрузки

    String pendingTree = "";
    Runnable treeRefresh;

    String folderLabel(String tree) {
        if (tree == null || tree.isEmpty()) {
            return "Стандартные папки: «Загрузки», «Фото», «Видео», «Музыка» (папка HerBrowser)";
        }
        try {
            String id = Uri.decode(DocumentsContract.getTreeDocumentId(Uri.parse(tree)));
            int i = id.indexOf(':');
            String vol = i > 0 ? id.substring(0, i) : id;
            String path = i >= 0 ? id.substring(i + 1) : "";
            return ("primary".equals(vol) ? "Память телефона" : vol) + (path.isEmpty() ? "" : "/" + path);
        } catch (Exception e) {
            return tree;
        }
    }

    void showDlSettings() {
        pendingTree = store.getStr("dl_tree", "");
        LinearLayout body = new LinearLayout(this);
        body.setOrientation(LinearLayout.VERTICAL);
        body.setPadding(dp(16), dp(8), dp(16), dp(24));
        body.addView(tv("Куда сохранять загрузки", 16, text));
        final TextView label = tv(folderLabel(pendingTree), 15, accent);
        label.setPadding(dp(16), dp(14), dp(16), dp(14));
        label.setBackground(box(card, 16));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(MATCH, WRAP);
        lp.topMargin = dp(10);
        body.addView(label, lp);
        treeRefresh = () -> label.setText(folderLabel(pendingTree));
        body.addView(bigButton("Выбрать папку", () -> pickFolder()));
        body.addView(bigButton("Стандартные папки", () -> {
            pendingTree = "";
            treeRefresh.run();
        }));
        body.addView(bigButton("Сохранить", () -> {
            store.setStr("dl_tree", pendingTree);
            toast(pendingTree.isEmpty() ? "Сохранено: стандартные папки"
                    : "Сохранено: новые загрузки будут появляться в выбранной папке");
        }));
        screen("Настройки загрузок", body, true);
    }

    void pickFolder() {
        Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE);
        i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                | Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
        try {
            startActivityForResult(i, 78);
        } catch (Exception e) {
            toast("Не удалось открыть выбор папки");
        }
    }

    void onTreePicked(Intent data) {
        Uri u = data.getData();
        if (u == null) return;
        try {
            getContentResolver().takePersistableUriPermission(u,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
        } catch (Exception e) {
            toast("Не удалось запомнить папку");
            return;
        }
        pendingTree = u.toString();
        if (treeRefresh != null) treeRefresh.run();
    }

    // ---------------------------------------------------------------- экономия аккумулятора

    void showSaverScreen() {
        final boolean[] on = {"1".equals(store.getStr("saver_on", "0"))};
        final int[] pct = {savePct()};
        LinearLayout body = new LinearLayout(this);
        body.setOrientation(LinearLayout.VERTICAL);
        body.setPadding(dp(16), dp(8), dp(16), dp(24));
        body.addView(tv("Сейчас заряд: " + batteryLevel() + "%" + (saver ? " \u2014 экономия включена" : ""), 15, sub));

        TextView t0 = tv("Экономия аккумулятора", 16, text);
        t0.setTypeface(null, Typeface.BOLD);
        LinearLayout.LayoutParams t0p = new LinearLayout.LayoutParams(WRAP, WRAP);
        t0p.topMargin = dp(18);
        body.addView(t0, t0p);

        LinearLayout chips = new LinearLayout(this);
        final TextView cOn = tv("Включена", 14, text);
        final TextView cOff = tv("Выключена", 14, text);
        final Runnable paint = () -> {
            cOn.setBackground(box(on[0] ? brand : card, 20));
            cOn.setTextColor(on[0] ? Color.WHITE : text);
            cOff.setBackground(box(on[0] ? card : brand, 20));
            cOff.setTextColor(on[0] ? text : Color.WHITE);
        };
        cOn.setPadding(dp(16), dp(10), dp(16), dp(10));
        cOff.setPadding(dp(16), dp(10), dp(16), dp(10));
        press(cOn);
        press(cOff);
        cOn.setOnClickListener(v -> {
            on[0] = true;
            paint.run();
        });
        cOff.setOnClickListener(v -> {
            on[0] = false;
            paint.run();
        });
        LinearLayout.LayoutParams cp = new LinearLayout.LayoutParams(WRAP, WRAP);
        cp.topMargin = dp(10);
        cp.rightMargin = dp(8);
        chips.addView(cOn, cp);
        chips.addView(cOff, cp);
        body.addView(chips);

        final TextView pl = tv("Включать при заряде: " + pct[0] + "%", 16, text);
        LinearLayout.LayoutParams plp = new LinearLayout.LayoutParams(WRAP, WRAP);
        plp.topMargin = dp(22);
        body.addView(pl, plp);
        SeekBar sb = new SeekBar(this);
        sb.setMax(89);
        sb.setProgress(pct[0] - 1);
        sb.setProgressTintList(ColorStateList.valueOf(brand));
        sb.setThumbTintList(ColorStateList.valueOf(brand));
        sb.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar s, int p, boolean user) {
                pct[0] = p + 1;
                pl.setText("Включать при заряде: " + pct[0] + "%");
            }

            @Override
            public void onStartTrackingTouch(SeekBar s) {
            }

            @Override
            public void onStopTrackingTouch(SeekBar s) {
            }
        });
        body.addView(sb, new LinearLayout.LayoutParams(MATCH, WRAP));
        TextView hint = tv("Когда заряд станет равным или ниже выбранного (от 1% до 90%), браузер сам включит экономию: "
                + "уберёт анимации и приостановит фоновые вкладки. Её можно выключить или изменить процент.", 13, sub);
        LinearLayout.LayoutParams hp = new LinearLayout.LayoutParams(WRAP, WRAP);
        hp.topMargin = dp(8);
        body.addView(hint, hp);
        body.addView(bigButton("Сохранить", () -> {
            store.setStr("saver_on", on[0] ? "1" : "0");
            store.setStr("saver_pct", String.valueOf(pct[0]));
            checkSaver(true);
            toast(on[0] ? "Сохранено: экономия включится при заряде " + pct[0] + "% и ниже"
                    : "Экономия аккумулятора выключена");
        }));
        screen("Экономия аккумулятора", body, true);
        paint.run();
    }

    // ---------------------------------------------------------------- Безопасность

    Runnable secRefresh, pcRefresh;

    View secRow(String glyph, String name, String desc, String status, View.OnClickListener l) {
        LinearLayout row = new LinearLayout(this);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(4), dp(10), dp(4), dp(10));
        TextView ic = tv(glyph, 22, accent);
        ic.setGravity(Gravity.CENTER);
        ic.setBackground(box(soft, 16));
        row.addView(ic, new LinearLayout.LayoutParams(dp(52), dp(52)));
        LinearLayout tx = new LinearLayout(this);
        tx.setOrientation(LinearLayout.VERTICAL);
        tx.addView(tv(name, 17, text));
        tx.addView(tv(desc, 13, sub));
        LinearLayout.LayoutParams txp = new LinearLayout.LayoutParams(0, WRAP, 1f);
        txp.leftMargin = dp(16);
        row.addView(tx, txp);
        row.addView(tv(status, 22, sub), new LinearLayout.LayoutParams(WRAP, WRAP));
        rip(row, 16);
        press(row);
        row.setOnClickListener(l);
        return row;
    }

    void showSecurityScreen() {
        final LinearLayout body = new LinearLayout(this);
        body.setOrientation(LinearLayout.VERTICAL);
        body.setPadding(dp(16), dp(4), dp(16), dp(16));
        final Runnable[] render = new Runnable[1];
        render[0] = () -> {
            body.removeAllViews();
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(MATCH, WRAP);
            body.addView(secRow("\uD83D\uDEE1", "Родительский контроль",
                    "PIN-код, ограничения, запрещённые сайты", "\u203A", v -> showParental()), lp);
            boolean lock = "1".equals(store.getStr("app_lock", "0")) && deviceSecure();
            body.addView(secRow("\uD83D\uDD12", "Защита приложений паролем",
                    "Вход по блокировке экрана телефона", lock ? "\u2705" : "\u274C",
                    v -> toggleAppLock(render[0])), lp);
            boolean inst = Build.VERSION.SDK_INT < 26 || getPackageManager().canRequestPackageInstalls();
            body.addView(secRow("\uD83D\uDCE5", "Установка неизвестных приложений",
                    "Разрешение устанавливать из HerBrowser", inst ? "\u2705" : "\u274C",
                    v -> openInstallSettings()), lp);
        };
        Dialog dd = screen("Безопасность", body, true);
        secRefresh = render[0];
        dd.setOnDismissListener(x -> secRefresh = null);
        render[0].run();
    }

    void needScreenLockDialog(String what) {
        builder().setTitle("Нужна блокировка экрана")
                .setMessage("Чтобы " + what + ", включите блокировку экрана (PIN-код, графический ключ или пароль) в настройках телефона.")
                .setPositiveButton("Открыть настройки", (d, w) -> {
                    try {
                        startActivity(new Intent(Settings.ACTION_SECURITY_SETTINGS));
                    } catch (Exception e) {
                        toast("Откройте настройки телефона");
                    }
                })
                .setNegativeButton("Отмена", null)
                .show();
    }

    void toggleAppLock(Runnable after) {
        boolean on = "1".equals(store.getStr("app_lock", "0"));
        if (!on && !deviceSecure()) {
            needScreenLockDialog("защитить приложение паролем");
            return;
        }
        store.setStr("app_lock", on ? "0" : "1");
        toast(on ? "Защита приложения выключена" : "Защита включена: при входе нужна блокировка экрана");
        after.run();
    }

    void openInstallSettings() {
        if (Build.VERSION.SDK_INT < 26) {
            toast("На вашей версии Android это включается в настройках телефона");
            return;
        }
        try {
            startActivity(new Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                    Uri.parse("package:" + getPackageName())));
        } catch (Exception e) {
            toast("Не удалось открыть настройки");
        }
    }

    // ---------------------------------------------------------------- Родительский контроль

    EditText pinField(String hint) {
        EditText e = new EditText(this);
        e.setHint(hint);
        e.setSingleLine(true);
        e.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_VARIATION_PASSWORD);
        return e;
    }

    void showParental() {
        if (!pinSet()) setupPin(() -> showParentalScreen());
        else askPin(() -> showParentalScreen());
    }

    void askPin(final Runnable ok) {
        LinearLayout col = new LinearLayout(this);
        col.setPadding(dp(20), dp(8), dp(20), 0);
        final EditText p = pinField("PIN-код");
        col.addView(p, new LinearLayout.LayoutParams(MATCH, WRAP));
        builder().setTitle("Родительский контроль")
                .setMessage("Введите PIN-код родителя")
                .setView(col)
                .setPositiveButton("Войти", (d, w) -> {
                    if (checkPin(p.getText().toString())) ok.run();
                    else toast("Неверный PIN-код");
                })
                .setNegativeButton("Отмена", null)
                .show();
    }

    void setupPin(final Runnable ok) {
        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setPadding(dp(20), dp(8), dp(20), 0);
        final EditText p1 = pinField("Придумайте PIN-код (4\u20138 цифр)");
        final EditText p2 = pinField("Повторите PIN-код");
        col.addView(p1, new LinearLayout.LayoutParams(MATCH, WRAP));
        col.addView(p2, new LinearLayout.LayoutParams(MATCH, WRAP));
        builder().setTitle("Установите PIN-код родителя")
                .setMessage("PIN-код защищает ограничения: ребёнок не сможет их снять. Введите его два раза.")
                .setView(col)
                .setPositiveButton("Сохранить", (d, w) -> {
                    String a = p1.getText().toString();
                    String b = p2.getText().toString();
                    if (a.length() < 4 || a.length() > 8) {
                        toast("PIN-код должен быть из 4\u20138 цифр");
                        setupPin(ok);
                    } else if (!a.equals(b)) {
                        toast("PIN-коды не совпадают");
                        setupPin(ok);
                    } else {
                        setPin(a);
                        toast("PIN-код установлен");
                        ok.run();
                    }
                })
                .setNegativeButton("Отмена", null)
                .show();
    }

    View checkRow(String label, final boolean[] st) {
        LinearLayout row = new LinearLayout(this);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(16), dp(14), dp(16), dp(14));
        row.setBackground(box(card, 14));
        row.addView(tv(label, 15, text), new LinearLayout.LayoutParams(0, WRAP, 1f));
        final TextView mark = tv(st[0] ? "\u2705" : "\u2B1C", 20, accent);
        row.addView(mark);
        rip(row, 14);
        press(row);
        row.setOnClickListener(v -> {
            st[0] = !st[0];
            mark.setText(st[0] ? "\u2705" : "\u2B1C");
        });
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(MATCH, WRAP);
        lp.topMargin = dp(8);
        row.setLayoutParams(lp);
        return row;
    }

    ComponentName adminCn() {
        return new ComponentName(this, AdminReceiver.class);
    }

    boolean adminOn() {
        DevicePolicyManager dpm = (DevicePolicyManager) getSystemService(Context.DEVICE_POLICY_SERVICE);
        return dpm != null && dpm.isAdminActive(adminCn());
    }

    void toggleAdmin() {
        DevicePolicyManager dpm = (DevicePolicyManager) getSystemService(Context.DEVICE_POLICY_SERVICE);
        if (dpm == null) return;
        if (adminOn()) {
            dpm.removeActiveAdmin(adminCn());
            toast("Защита от удаления выключена");
            if (pcRefresh != null) pcRefresh.run();
            return;
        }
        Intent i = new Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN);
        i.putExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN, adminCn());
        i.putExtra(DevicePolicyManager.EXTRA_ADD_EXPLANATION,
                "Пока включено, приложение HerBrowser нельзя удалить, не отключив эту защиту.");
        try {
            startActivityForResult(i, 81);
        } catch (Exception e) {
            toast("Не удалось включить защиту от удаления");
        }
    }

    void showParentalScreen() {
        final boolean[] on = {"1".equals(store.getStr("pc_on", "0"))};
        final boolean[] bh = {pcBlocks("hist")};
        final boolean[] bd = {pcBlocks("dl")};
        final boolean[] bp = {pcBlocks("pw")};
        final int nowYear = Calendar.getInstance().get(Calendar.YEAR);

        LinearLayout body = new LinearLayout(this);
        body.setOrientation(LinearLayout.VERTICAL);
        body.setPadding(dp(16), dp(4), dp(16), dp(24));

        body.addView(checkRow("Родительский контроль включён", on));

        TextView yl = tv("Год рождения ребёнка (обязательно)", 15, text);
        yl.setTypeface(null, Typeface.BOLD);
        LinearLayout.LayoutParams ylp = new LinearLayout.LayoutParams(WRAP, WRAP);
        ylp.topMargin = dp(18);
        body.addView(yl, ylp);
        final EditText year = new EditText(this);
        year.setHint("Например, " + (nowYear - 10));
        year.setSingleLine(true);
        year.setInputType(InputType.TYPE_CLASS_NUMBER);
        year.setTextColor(text);
        year.setHintTextColor(sub);
        year.setBackground(box(card, 14));
        year.setPadding(dp(16), dp(12), dp(16), dp(12));
        if (pcYear() > 0) year.setText(String.valueOf(pcYear()));
        LinearLayout.LayoutParams yp = new LinearLayout.LayoutParams(MATCH, WRAP);
        yp.topMargin = dp(8);
        body.addView(year, yp);
        body.addView(tv("Ограничения действуют, пока ребёнку не исполнится 18 лет.", 12, sub));

        TextView t1 = tv("Запретить ребёнку", 15, text);
        t1.setTypeface(null, Typeface.BOLD);
        LinearLayout.LayoutParams t1p = new LinearLayout.LayoutParams(WRAP, WRAP);
        t1p.topMargin = dp(18);
        body.addView(t1, t1p);
        body.addView(checkRow("Очищать историю браузера", bh));
        body.addView(checkRow("Удалять установки (загрузки)", bd));
        body.addView(checkRow("Удалять пароли", bp));

        TextView t2 = tv("Запрещённые сайты", 15, text);
        t2.setTypeface(null, Typeface.BOLD);
        LinearLayout.LayoutParams t2p = new LinearLayout.LayoutParams(WRAP, WRAP);
        t2p.topMargin = dp(18);
        body.addView(t2, t2p);
        final LinearLayout sites = new LinearLayout(this);
        sites.setOrientation(LinearLayout.VERTICAL);
        body.addView(sites);
        final EditText addSite = new EditText(this);
        addSite.setHint("Адрес сайта, например example.com");
        addSite.setSingleLine(true);
        addSite.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_URI);
        addSite.setTextColor(text);
        addSite.setHintTextColor(sub);
        addSite.setBackground(box(card, 14));
        addSite.setPadding(dp(16), dp(12), dp(16), dp(12));
        LinearLayout.LayoutParams asp = new LinearLayout.LayoutParams(MATCH, WRAP);
        asp.topMargin = dp(8);
        body.addView(addSite, asp);

        final Runnable[] renderSites = new Runnable[1];
        renderSites[0] = () -> {
            sites.removeAllViews();
            List<String[]> l = store.list("pc_sites");
            if (l.isEmpty()) sites.addView(tv("Список пуст", 13, sub));
            for (int i = 0; i < l.size(); i++) {
                final int idx = i;
                LinearLayout r = new LinearLayout(this);
                r.setGravity(Gravity.CENTER_VERTICAL);
                r.setPadding(dp(14), dp(10), dp(8), dp(10));
                r.setBackground(box(card, 12));
                r.addView(tv("\uD83D\uDEAB  " + l.get(i)[0], 15, text), new LinearLayout.LayoutParams(0, WRAP, 1f));
                TextView x = tv("\u2715", 16, sub);
                x.setPadding(dp(12), dp(6), dp(12), dp(6));
                x.setOnClickListener(v -> {
                    store.remove("pc_sites", idx);
                    renderSites[0].run();
                });
                r.addView(x);
                LinearLayout.LayoutParams rl = new LinearLayout.LayoutParams(MATCH, WRAP);
                rl.topMargin = dp(6);
                sites.addView(r, rl);
            }
        };
        renderSites[0].run();
        body.addView(bigButton("Добавить сайт в запрещённые", () -> {
            String h = normHost(addSite.getText().toString());
            if (h.isEmpty() || !h.contains(".")) {
                toast("Введите адрес сайта, например example.com");
                return;
            }
            for (String[] e : store.list("pc_sites")) {
                if (e[0].equals(h)) {
                    toast("Этот сайт уже в списке");
                    return;
                }
            }
            store.append("pc_sites", new String[]{h});
            addSite.setText("");
            renderSites[0].run();
        }));

        TextView t3 = tv("Защита от удаления приложения", 15, text);
        t3.setTypeface(null, Typeface.BOLD);
        LinearLayout.LayoutParams t3p = new LinearLayout.LayoutParams(WRAP, WRAP);
        t3p.topMargin = dp(18);
        body.addView(t3, t3p);
        final TextView adminState = tv("", 14, sub);
        adminState.setPadding(0, dp(4), 0, 0);
        body.addView(adminState);
        body.addView(tv("Включает системную защиту: приложение нельзя удалить, пока её не отключат. "
                + "Её можно отключить и в настройках телефона, поэтому отключать её лучше только вам.", 12, sub));
        final Runnable paintAdmin = () -> adminState.setText(adminOn()
                ? "\u2705 Защита включена \u2014 нажмите, чтобы выключить"
                : "\u274C Защита выключена \u2014 нажмите, чтобы включить");
        paintAdmin.run();
        adminState.setOnClickListener(v -> toggleAdmin());
        pcRefresh = paintAdmin;

        body.addView(bigButton("Сменить PIN-код", () -> setupPin(() -> { })));
        body.addView(bigButton("Сохранить", () -> {
            int y = 0;
            try {
                y = Integer.parseInt(year.getText().toString().trim());
            } catch (Exception e) {
                y = 0;
            }
            boolean valid = y >= 1900 && y <= nowYear;
            if (on[0] && !valid) {
                toast("Укажите год рождения ребёнка");
                return;
            }
            if (valid) store.setStr("pc_year", String.valueOf(y));
            store.setStr("pc_on", on[0] ? "1" : "0");
            store.setStr("pc_blk_hist", bh[0] ? "1" : "0");
            store.setStr("pc_blk_dl", bd[0] ? "1" : "0");
            store.setStr("pc_blk_pw", bp[0] ? "1" : "0");
            if (on[0] && valid && nowYear - y >= 18) {
                toast("Сохранено. Ребёнку уже 18 лет, ограничения не действуют");
            } else {
                toast(on[0] ? "Родительский контроль включён" : "Родительский контроль выключен");
            }
        }));
        Dialog pd = screen("Родительский контроль", body, true);
        pd.setOnDismissListener(x -> pcRefresh = null);
    }

    // ---------------------------------------------------------------- загрузки файлов

    static final String[] UNKNOWN_EXT = {"bin", "bit", "dat", "tmp", "php", "asp", "aspx", "jsp",
            "cgi", "ashx", "download", "file"};

    String mimeFromName(String name) {
        String n = name.toLowerCase(Locale.ROOT);
        int i = n.lastIndexOf('.');
        String e = i >= 0 ? n.substring(i + 1) : "";
        if (e.equals("apk")) return APK_MIME;
        if (e.equals("ts")) return "video/mp2t";
        String m = MimeTypeMap.getSingleton().getMimeTypeFromExtension(e);
        return m == null ? "application/octet-stream" : m;
    }

    String dirFor(String mime) {
        String m = mime == null ? "" : mime;
        if (m.startsWith("image/")) return Environment.DIRECTORY_PICTURES;
        if (m.startsWith("video/")) return Environment.DIRECTORY_MOVIES;
        if (m.startsWith("audio/")) return Environment.DIRECTORY_MUSIC;
        return Environment.DIRECTORY_DOWNLOADS;
    }

    String extOf(String name) {
        int i = name.lastIndexOf('.');
        return (i >= 0 && i < name.length() - 1) ? name.substring(i + 1).toLowerCase(Locale.ROOT) : "";
    }

    boolean isUnknownExt(String ex) {
        if (ex.isEmpty()) return true;
        for (String u : UNKNOWN_EXT) if (u.equals(ex)) return true;
        return false;
    }

    String family(String mime) {
        if (APK_MIME.equals(mime)) return "apk";
        int i = mime.indexOf('/');
        return i > 0 ? mime.substring(0, i) : mime;
    }

    boolean at(byte[] b, int n, int off, String s) {
        if (n < off + s.length()) return false;
        for (int i = 0; i < s.length(); i++) if (b[off + i] != (byte) s.charAt(i)) return false;
        return true;
    }

    boolean contains(byte[] b, int n, String s) {
        int m = s.length();
        outer:
        for (int i = 0; i + m <= n; i++) {
            for (int j = 0; j < m; j++) if (b[i + j] != (byte) s.charAt(j)) continue outer;
            return true;
        }
        return false;
    }

    /** Определяет тип файла по первым байтам (фото, видео, музыка, приложение и т. д.). */
    String sniffExt(byte[] b, int n) {
        if (n < 4) return null;
        int b0 = b[0] & 0xFF, b1 = b[1] & 0xFF, b2 = b[2] & 0xFF, b3 = b[3] & 0xFF;
        if (b0 == 0x89 && at(b, n, 1, "PNG")) return "png";
        if (b0 == 0xFF && b1 == 0xD8 && b2 == 0xFF) return "jpg";
        if (at(b, n, 0, "GIF8")) return "gif";
        if (at(b, n, 0, "RIFF") && at(b, n, 8, "WEBP")) return "webp";
        if (at(b, n, 0, "RIFF") && at(b, n, 8, "WAVE")) return "wav";
        if (at(b, n, 0, "%PDF")) return "pdf";
        if (at(b, n, 4, "ftyp")) {
            if (at(b, n, 8, "M4A ")) return "m4a";
            if (at(b, n, 8, "qt  ")) return "mov";
            if (at(b, n, 8, "3gp")) return "3gp";
            if (at(b, n, 8, "heic") || at(b, n, 8, "heix") || at(b, n, 8, "mif1")) return "heic";
            return "mp4";
        }
        if (at(b, n, 0, "ID3") || (b0 == 0xFF && (b1 & 0xE0) == 0xE0)) return "mp3";
        if (at(b, n, 0, "OggS")) return "ogg";
        if (at(b, n, 0, "fLaC")) return "flac";
        if (b0 == 0x1A && b1 == 0x45 && b2 == 0xDF && b3 == 0xA3) {
            return contains(b, Math.min(n, 64), "webm") ? "webm" : "mkv";
        }
        if (b0 == 'P' && b1 == 'K' && b2 == 3 && b3 == 4) {
            return contains(b, n, "AndroidManifest.xml") ? "apk" : "zip";
        }
        if (at(b, n, 0, "Rar!")) return "rar";
        if (b0 == 0x1F && b1 == 0x8B) return "gz";
        if (b0 == '7' && b1 == 'z' && b2 == 0xBC && b3 == 0xAF) return "7z";
        if (at(b, n, 0, "<!DO") || at(b, n, 0, "<!do") || at(b, n, 0, "<htm") || at(b, n, 0, "<HTM")) return "html";
        return null;
    }

    /** Имя файла без «.bin»: расширение берётся из заголовков и из самого файла. */
    String finalName(String url, String cd, String mime, byte[] head, int n) {
        String name = URLUtil.guessFileName(url, cd, mime);
        String ex = extOf(name);
        String sn = sniffExt(head, n);
        int dot = name.lastIndexOf('.');
        String base = dot > 0 ? name.substring(0, dot) : name;
        if (sn != null) {
            String curMime = mimeFromName(name);
            boolean unknown = isUnknownExt(ex) || curMime.equals("application/octet-stream");
            boolean differs = !family(curMime).equals(family(mimeFromName("x." + sn)));
            if (unknown || differs) name = base + "." + sn;
        } else if (isUnknownExt(ex) && mime != null) {
            String fm = MimeTypeMap.getSingleton().getExtensionFromMimeType(mime);
            if (fm != null) name = base + "." + fm;
        }
        String e2 = extOf(name);
        if (e2.equals("bin") || e2.equals("bit")) name = name.substring(0, name.lastIndexOf('.'));
        name = safeName(name);
        return name.isEmpty() ? "file" : name;
    }

    void startDownload(final String url, final String ua, final String cd, final String mime) {
        if (!url.startsWith("http")) {
            toast("Этот тип загрузки не поддерживается");
            return;
        }
        final String cookie = CookieManager.getInstance().getCookie(url);
        final Dl dl = new Dl();
        active.add(dl);
        toast("Загрузка началась");
        new Thread(() -> {
            Out o = null;
            boolean ok = false;
            String err = null;
            HttpURLConnection c = null;
            try {
                c = (HttpURLConnection) new URL(url).openConnection();
                c.setConnectTimeout(15000);
                c.setReadTimeout(30000);
                c.setInstanceFollowRedirects(true);
                if (ua != null) c.setRequestProperty("User-Agent", ua);
                if (cookie != null) c.setRequestProperty("Cookie", cookie);
                int code = c.getResponseCode();
                if (code >= 400) throw new IOException("HTTP " + code);
                String rcd = c.getHeaderField("Content-Disposition");
                String rct = c.getContentType();
                if (rct != null && rct.contains(";")) rct = rct.substring(0, rct.indexOf(';')).trim();
                long total = c.getContentLength();
                InputStream in = c.getInputStream();
                byte[] head = new byte[65536];
                int hn = 0;
                int r;
                while (hn < head.length && (r = in.read(head, hn, head.length - hn)) > 0) hn += r;
                String name = finalName(url, rcd != null ? rcd : cd, rct != null ? rct : mime, head, hn);
                dl.name = name;
                String mt = mimeFromName(name);
                if (mt.equals("application/octet-stream") && rct != null) mt = rct;
                o = openOut(name, mt);
                o.os.write(head, 0, hn);
                long done = hn;
                byte[] buf = new byte[64 * 1024];
                while ((r = in.read(buf)) > 0) {
                    if (dl.cancel) throw new IOException("Отменено");
                    o.os.write(buf, 0, r);
                    done += r;
                    dl.status = total > 0 ? "Загрузка\u2026 " + (done * 100 / total) + "%"
                            : "Загрузка\u2026 " + fmtSize(done);
                }
                ok = true;
            } catch (Exception e) {
                err = e.getMessage() == null ? e.toString() : e.getMessage();
            } finally {
                if (c != null) c.disconnect();
            }
            final Out fo = o;
            final boolean fok = ok;
            final String ferr = err;
            if (fo != null) closeOut(fo, fok);
            ui.post(() -> {
                active.remove(dl);
                if (fok && fo != null) {
                    String[] entry = fo.uri != null
                            ? new String[]{dl.name, fo.uri.toString(), "uri"}
                            : new String[]{dl.name, fo.file.getPath(), "path"};
                    store.add("downloads", entry, 200);
                    toast("Готово: " + dl.name);
                } else if (!dl.cancel) {
                    toast("Не удалось скачать: " + ferr);
                }
            });
        }).start();
    }

    // старые записи (скачанные через системный загрузчик)
    String downloadStatus(long id) {
        DownloadManager dm = (DownloadManager) getSystemService(Context.DOWNLOAD_SERVICE);
        Cursor c = null;
        try {
            c = dm.query(new DownloadManager.Query().setFilterById(id));
            if (c != null && c.moveToFirst()) {
                int st = c.getInt(c.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS));
                if (st == DownloadManager.STATUS_SUCCESSFUL) return "Готово \u2014 нажмите, чтобы открыть";
                if (st == DownloadManager.STATUS_FAILED) return "Ошибка загрузки";
                return "Загрузка\u2026";
            }
        } catch (Exception e) {
            // ниже вернём "недоступен"
        } finally {
            if (c != null) c.close();
        }
        return "Файл недоступен";
    }

    String kindOf(String[] it) {
        return it.length > 2 ? it[2] : "dm";
    }

    String entryStatus(String[] it) {
        if ("dm".equals(kindOf(it))) return downloadStatus(Long.parseLong(it[1]));
        return "Готово \u2014 нажмите, чтобы открыть";
    }

    void openEntry(final String[] it) {
        String ex = extOf(it[0]);
        if (ex.equals("html") || ex.equals("htm")) {
            builder().setTitle("\uD83D\uDEAB Файл HTML может быть опасным")
                    .setMessage("Такие файлы могут содержать вредоносные страницы, могут быть злоумышленники.")
                    .setPositiveButton("Не открывать (рекомендую)", null)
                    .setNegativeButton("Рискнуть для проверки (не советую)", (d, w) -> openEntryNow(it))
                    .show();
            return;
        }
        openEntryNow(it);
    }

    void openEntryNow(String[] it) {
        String k = kindOf(it);
        String mime = mimeFromName(it[0]);
        Uri u;
        try {
            if ("dm".equals(k)) {
                DownloadManager dm = (DownloadManager) getSystemService(Context.DOWNLOAD_SERVICE);
                long id = Long.parseLong(it[1]);
                u = dm.getUriForDownloadedFile(id);
                if (mime.equals("application/octet-stream")) {
                    String m = dm.getMimeTypeForDownloadedFile(id);
                    if (m != null) mime = m;
                }
            } else if ("uri".equals(k)) {
                u = Uri.parse(it[1]);
            } else {
                u = Uri.fromFile(new File(it[1]));
            }
        } catch (Exception e) {
            u = null;
        }
        if (u == null) {
            toast("Файл ещё не загружен или удалён");
            return;
        }
        if (APK_MIME.equals(mime) && Build.VERSION.SDK_INT >= 26
                && !getPackageManager().canRequestPackageInstalls()) {
            toast("Разрешите HerБраузеру устанавливать приложения и откройте файл ещё раз");
            startActivity(new Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                    Uri.parse("package:" + getPackageName())));
            return;
        }
        Intent i = new Intent(Intent.ACTION_VIEW);
        i.setDataAndType(u, mime);
        i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_ACTIVITY_NEW_TASK);
        try {
            startActivity(i);
        } catch (ActivityNotFoundException e) {
            toast("Нет приложения для открытия этого файла");
        }
    }

    void deleteEntry(String[] it) {
        String k = kindOf(it);
        try {
            if ("dm".equals(k)) {
                ((DownloadManager) getSystemService(Context.DOWNLOAD_SERVICE)).remove(Long.parseLong(it[1]));
            } else if ("uri".equals(k)) {
                Uri du = Uri.parse(it[1]);
                if (DocumentsContract.isDocumentUri(this, du)) {
                    DocumentsContract.deleteDocument(getContentResolver(), du);
                } else {
                    getContentResolver().delete(du, null, null);
                }
            } else {
                new File(it[1]).delete();
            }
        } catch (Exception e) {
            // запись всё равно уберём
        }
    }

    // ---------------------------------------------------------------- запись файла в «Загрузки», «Фото», «Видео», «Музыку»

    File uniqueFile(File folder, String name) {
        File f = new File(folder, name);
        int n = 1;
        int dot = name.lastIndexOf('.');
        String base = dot > 0 ? name.substring(0, dot) : name;
        String ex = dot > 0 ? name.substring(dot) : "";
        while (f.exists()) f = new File(folder, base + " (" + (n++) + ")" + ex);
        return f;
    }

    Out openOut(String name, String mime) throws Exception {
        Out o = new Out();
        String tree = store.getStr("dl_tree", "");
        if (!tree.isEmpty()) {
            try {
                Uri tu = Uri.parse(tree);
                Uri parent = DocumentsContract.buildDocumentUriUsingTree(tu, DocumentsContract.getTreeDocumentId(tu));
                Uri f = DocumentsContract.createDocument(getContentResolver(), parent, mime, name);
                if (f != null) {
                    o.uri = f;
                    o.doc = true;
                    o.os = getContentResolver().openOutputStream(f);
                    return o;
                }
            } catch (Exception e) {
                // выбранная папка недоступна: сохраняем в стандартную
            }
        }
        String dir = dirFor(mime);
        if (Build.VERSION.SDK_INT >= 29) {
            ContentValues cv = new ContentValues();
            cv.put(MediaStore.MediaColumns.DISPLAY_NAME, name);
            cv.put(MediaStore.MediaColumns.MIME_TYPE, mime);
            cv.put(MediaStore.MediaColumns.RELATIVE_PATH, dir + "/HerBrowser");
            cv.put(MediaStore.MediaColumns.IS_PENDING, 1);
            Uri coll;
            if (Environment.DIRECTORY_MOVIES.equals(dir)) coll = MediaStore.Video.Media.EXTERNAL_CONTENT_URI;
            else if (Environment.DIRECTORY_MUSIC.equals(dir)) coll = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI;
            else if (Environment.DIRECTORY_PICTURES.equals(dir)) coll = MediaStore.Images.Media.EXTERNAL_CONTENT_URI;
            else coll = MediaStore.Downloads.EXTERNAL_CONTENT_URI;
            o.uri = getContentResolver().insert(coll, cv);
            if (o.uri == null) throw new IOException("Не удалось создать файл");
            o.os = getContentResolver().openOutputStream(o.uri);
        } else {
            File base = has(Manifest.permission.WRITE_EXTERNAL_STORAGE)
                    ? Environment.getExternalStoragePublicDirectory(dir)
                    : getExternalFilesDir(dir);
            File folder = new File(base, "HerBrowser");
            folder.mkdirs();
            o.file = uniqueFile(folder, name);
            o.os = new FileOutputStream(o.file);
        }
        return o;
    }

    void closeOut(Out o, boolean ok) {
        try {
            o.os.close();
        } catch (Exception e) {
            // игнорируем
        }
        try {
            if (o.doc) {
                if (!ok) DocumentsContract.deleteDocument(getContentResolver(), o.uri);
            } else if (o.uri != null) {
                if (ok) {
                    ContentValues cv = new ContentValues();
                    cv.put(MediaStore.MediaColumns.IS_PENDING, 0);
                    getContentResolver().update(o.uri, cv, null, null);
                } else {
                    getContentResolver().delete(o.uri, null, null);
                }
            } else if (o.file != null) {
                if (ok) MediaScannerConnection.scanFile(this, new String[]{o.file.getPath()}, null, null);
                else o.file.delete();
            }
        } catch (Exception e) {
            // игнорируем
        }
    }
}
