package com.herbrowser.app;

import android.Manifest;
import android.app.DownloadManager;
import android.app.Dialog;
import android.content.ActivityNotFoundException;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.res.ColorStateList;
import android.database.Cursor;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.media.MediaScannerConnection;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.provider.MediaStore;
import android.provider.Settings;
import android.text.Editable;
import android.text.InputType;
import android.text.TextWatcher;
import android.view.Gravity;
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
        String[] g = {"\uD83C\uDFA8", "\uD83D\uDCBE", "\uD83D\uDD14", "\u2139"};
        String[] n = {"Оформление", "Память", "Уведомления сайтов", "О приложении"};
        String[] ds = {"Тема и свои цвета", "Кэш и сохранённые данные",
                "Сайты и мессенджеры", "Версия и сведения"};
        final Runnable[] acts = {() -> showThemeScreen(true), () -> showMemoryScreen(),
                () -> showNotifScreen(), () -> showAboutScreen()};
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

    View colorSection(String title, final String key, int[] presets) {
        LinearLayout sec = new LinearLayout(this);
        sec.setOrientation(LinearLayout.VERTICAL);
        sec.setPadding(0, dp(20), 0, 0);
        TextView t = tv(title, 16, text);
        t.setTypeface(null, Typeface.BOLD);
        sec.addView(t);

        Integer curC = customColor(key);
        HorizontalScrollView hs = new HorizontalScrollView(this);
        hs.setHorizontalScrollBarEnabled(false);
        LinearLayout row = new LinearLayout(this);
        hs.addView(row);
        for (final int c : presets) {
            GradientDrawable g = new GradientDrawable();
            g.setShape(GradientDrawable.OVAL);
            g.setColor(c);
            boolean sel = curC != null && curC.intValue() == c;
            g.setStroke(dp(sel ? 3 : 1), sel ? text : sub);
            View sw = new View(this);
            sw.setBackground(g);
            press(sw);
            sw.setOnClickListener(v -> {
                store.setStr(key, String.valueOf(c));
                recolor();
            });
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(dp(44), dp(44));
            lp.topMargin = dp(10);
            lp.rightMargin = dp(10);
            row.addView(sw, lp);
        }
        sec.addView(hs);

        int base = curC != null ? curC.intValue() : ("c_bg".equals(key) ? bg : accent);
        final int[] rgb = {Color.red(base), Color.green(base), Color.blue(base)};
        final GradientDrawable pg = box(base, 12);
        final TextView hex = tv(String.format("#%06X", base & 0xFFFFFF), 14, sub);
        LinearLayout pr = new LinearLayout(this);
        pr.setGravity(Gravity.CENTER_VERTICAL);
        pr.setPadding(0, dp(14), 0, 0);
        View preview = new View(this);
        preview.setBackground(pg);
        pr.addView(preview, new LinearLayout.LayoutParams(dp(64), dp(36)));
        LinearLayout.LayoutParams hp = new LinearLayout.LayoutParams(WRAP, WRAP);
        hp.leftMargin = dp(12);
        pr.addView(tv("Свой цвет", 14, text), hp);
        pr.addView(hex, hp);
        sec.addView(pr);

        int[] tint = {0xFFE5484D, 0xFF1FA67A, 0xFF2787F5};
        for (int k = 0; k < 3; k++) {
            final int ki = k;
            SeekBar sb = new SeekBar(this);
            sb.setMax(255);
            sb.setProgress(rgb[k]);
            sb.setProgressTintList(ColorStateList.valueOf(tint[k]));
            sb.setThumbTintList(ColorStateList.valueOf(tint[k]));
            sb.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
                @Override
                public void onProgressChanged(SeekBar s, int p, boolean user) {
                    rgb[ki] = p;
                    int col = Color.rgb(rgb[0], rgb[1], rgb[2]);
                    pg.setColor(col);
                    hex.setText(String.format("#%06X", col & 0xFFFFFF));
                }

                @Override
                public void onStartTrackingTouch(SeekBar s) {
                }

                @Override
                public void onStopTrackingTouch(SeekBar s) {
                }
            });
            sec.addView(sb, new LinearLayout.LayoutParams(MATCH, WRAP));
        }
        sec.addView(bigButton("Применить свой цвет", () -> {
            store.setStr(key, String.valueOf(Color.rgb(rgb[0], rgb[1], rgb[2])));
            recolor();
        }));
        return sec;
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
                                        store.remove(key, idx);
                                        render[0].run();
                                    })}));
                    row.setOnLongClickListener(v -> {
                        confirm("Удалить пароль?", () -> {
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
                        store.clear("history");
                        render[0].run();
                    }))};
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

    void openEntry(String[] it) {
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
                getContentResolver().delete(Uri.parse(it[1]), null, null);
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
            if (o.uri != null) {
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
