package com.herbrowser.app;

import android.media.MediaCodec;
import android.media.MediaExtractor;
import android.media.MediaFormat;
import android.media.MediaMuxer;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Поиск ссылок на видео и их загрузка. Без интерфейса. */
class VideoDl {
    static final String UA_DESKTOP = "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36";
    static final String UA_MOBILE = "Mozilla/5.0 (Linux; Android 13; Pixel 7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Mobile Safari/537.36";

    interface Progress {
        void update(long done, long total);

        boolean cancelled();
    }

    static class Variant {
        String label = "Видео";
        String url;
        String ext = "mp4";
        String mime = "video/mp4";
        boolean hls;
        boolean audio;
        long size;
        int height;
        int bitrate;
        int segments;
        String ua = UA_DESKTOP;
        boolean chunked;
        String audioUrl;   // если задано: видео и звук отдельно, склеим на телефоне
        long audioSize;
    }

    static class Info {
        String site = "";
        String title = "Видео";
        List<Variant> variants = new ArrayList<Variant>();
    }

    // ------------------------------------------------------------ сеть

    static HttpURLConnection open(String url, String method, String ua, Map<String, String> headers) throws IOException {
        HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
        c.setConnectTimeout(15000);
        c.setReadTimeout(20000);
        c.setInstanceFollowRedirects(true);
        c.setRequestMethod(method);
        c.setRequestProperty("User-Agent", ua);
        c.setRequestProperty("Accept-Language", "ru,en;q=0.8");
        if (headers != null) {
            for (Map.Entry<String, String> e : headers.entrySet()) c.setRequestProperty(e.getKey(), e.getValue());
        }
        return c;
    }

    static String readAll(InputStream in) throws IOException {
        ByteArrayOutputStream b = new ByteArrayOutputStream();
        byte[] buf = new byte[8192];
        int n;
        while ((n = in.read(buf)) > 0) b.write(buf, 0, n);
        return b.toString("UTF-8");
    }

    static String get(String url, String ua, Map<String, String> h) throws IOException {
        HttpURLConnection c = open(url, "GET", ua, h);
        try {
            int code = c.getResponseCode();
            if (code >= 400) throw new IOException("HTTP " + code);
            return readAll(c.getInputStream());
        } finally {
            c.disconnect();
        }
    }

    static String post(String url, String body, String ua, Map<String, String> h) throws IOException {
        HttpURLConnection c = open(url, "POST", ua, h);
        c.setDoOutput(true);
        c.setRequestProperty("Content-Type", "application/json");
        try {
            OutputStream o = c.getOutputStream();
            o.write(body.getBytes("UTF-8"));
            o.close();
            int code = c.getResponseCode();
            if (code >= 400) throw new IOException("HTTP " + code);
            return readAll(c.getInputStream());
        } finally {
            c.disconnect();
        }
    }

    static String resolve(String base, String rel) throws IOException {
        return new URL(new URL(base), rel).toString();
    }

    static String unescape(String s) {
        return s.replace("\\/", "/").replace("&amp;", "&").replace("\\u0026", "&");
    }

    static String pageTitle(String html, String def) {
        Matcher m = Pattern.compile("<title[^>]*>(.*?)</title>", Pattern.DOTALL | Pattern.CASE_INSENSITIVE).matcher(html);
        if (m.find()) {
            String t = m.group(1).replace("&amp;", "&").replace("&quot;", "\"").replace("&#39;", "'").trim();
            if (!t.isEmpty()) return t;
        }
        return def;
    }

    static long parseLong(String s) {
        try {
            return Long.parseLong(s);
        } catch (Exception e) {
            return 0;
        }
    }

    // ------------------------------------------------------------ поиск видео

    static Info extract(String raw) throws Exception {
        String url = raw.trim();
        if (!url.startsWith("http")) url = "https://" + url;
        String host = new URL(url).getHost().toLowerCase(Locale.ROOT);
        if (host.endsWith("rutube.ru")) return rutube(url);
        if (host.contains("youtube.com") || host.equals("youtu.be") || host.endsWith("youtube-nocookie.com")) {
            return youtube(url);
        }
        if (host.endsWith("vk.com") || host.endsWith("vk.ru") || host.endsWith("vkvideo.ru")) return vk(url);
        return generic(url);
    }

    static Info rutube(String url) throws Exception {
        Matcher m = Pattern.compile("([0-9a-f]{32})").matcher(url);
        if (!m.find()) throw new IOException("Не удалось найти ID видео RuTube в ссылке");
        String api = "https://rutube.ru/api/play/options/" + m.group(1)
                + "/?no_404=true&referer=https%3A%2F%2Frutube.ru&pver=v2";
        Map<String, String> h = new HashMap<String, String>();
        h.put("Referer", "https://rutube.ru/");
        JSONObject j = new JSONObject(get(api, UA_DESKTOP, h));
        Info info = new Info();
        info.site = "RuTube";
        info.title = j.optString("title", "Видео RuTube");
        JSONObject vb = j.optJSONObject("video_balancer");
        String master = vb != null ? vb.optString("m3u8", vb.optString("default", "")) : "";
        if (master.isEmpty()) throw new IOException("RuTube не отдал ссылку на видео (возможно, оно закрыто)");
        addHls(info, master, UA_DESKTOP);
        return info;
    }

    static Info youtube(String url) throws Exception {
        Matcher m = Pattern.compile("(?:v=|youtu\\.be/|shorts/|embed/|live/)([A-Za-z0-9_-]{11})").matcher(url);
        if (!m.find()) throw new IOException("Не удалось найти ID видео YouTube в ссылке");
        String id = m.group(1);
        String ver = "1.60.19";
        String body = "{\"context\":{\"client\":{\"clientName\":\"ANDROID_VR\",\"clientVersion\":\"" + ver
                + "\",\"deviceMake\":\"Oculus\",\"deviceModel\":\"Quest 3\",\"osName\":\"Android\","
                + "\"osVersion\":\"12L\",\"androidSdkVersion\":32,\"hl\":\"ru\",\"gl\":\"RU\"}},"
                + "\"videoId\":\"" + id + "\",\"contentCheckOk\":true,\"racyCheckOk\":true}";
        String ua = "com.google.android.apps.youtube.vr.oculus/" + ver
                + " (Linux; U; Android 12L; eureka-user Build/SQ3A.220605.009.A1) gzip";
        Map<String, String> h = new HashMap<String, String>();
        h.put("X-YouTube-Client-Name", "28");
        h.put("X-YouTube-Client-Version", ver);
        h.put("Origin", "https://www.youtube.com");
        JSONObject j = new JSONObject(post("https://www.youtube.com/youtubei/v1/player?prettyPrint=false", body, ua, h));
        JSONObject ps = j.optJSONObject("playabilityStatus");
        if (ps != null && !"OK".equals(ps.optString("status"))) {
            throw new IOException("YouTube: " + ps.optString("reason", ps.optString("status")));
        }
        Info info = new Info();
        info.site = "YouTube";
        JSONObject vd = j.optJSONObject("videoDetails");
        if (vd != null) info.title = vd.optString("title", "Видео YouTube");
        JSONObject sd = j.optJSONObject("streamingData");
        if (sd == null) throw new IOException("YouTube не отдал ссылки на видео");
        JSONArray fm = sd.optJSONArray("formats");
        JSONArray ad = sd.optJSONArray("adaptiveFormats");

        List<Variant> audios = new ArrayList<Variant>();
        if (ad != null) {
            for (int i = 0; i < ad.length(); i++) {
                JSONObject f = ad.optJSONObject(i);
                if (f == null) continue;
                String u = f.optString("url", "");
                if (u.isEmpty() || !f.optString("mimeType", "").startsWith("audio/mp4")) continue;
                Variant a = new Variant();
                a.url = u;
                a.audio = true;
                a.ext = "m4a";
                a.mime = "audio/mp4";
                a.size = parseLong(f.optString("contentLength", "0"));
                a.bitrate = f.optInt("bitrate");
                a.ua = ua;
                a.chunked = true;
                a.label = "Звук · " + (a.bitrate / 1000) + " кбит/с";
                audios.add(a);
            }
        }
        Collections.sort(audios, new Comparator<Variant>() {
            @Override
            public int compare(Variant a, Variant b) {
                return b.bitrate - a.bitrate;
            }
        });

        List<Variant> videos = new ArrayList<Variant>();
        Set<Integer> have = new HashSet<Integer>();
        if (fm != null) {
            for (int i = 0; i < fm.length(); i++) {
                JSONObject f = fm.optJSONObject(i);
                if (f == null) continue;
                String u = f.optString("url", "");
                if (u.isEmpty() || !f.optString("mimeType", "").startsWith("video/mp4")) continue;
                Variant v = new Variant();
                v.url = u;
                v.height = f.optInt("height");
                v.label = "Видео " + f.optString("qualityLabel", v.height + "p") + " · со звуком";
                v.size = parseLong(f.optString("contentLength", "0"));
                v.ua = ua;
                v.chunked = true;
                videos.add(v);
                have.add(v.height);
            }
        }
        if (ad != null && !audios.isEmpty()) {
            Variant best = audios.get(0);
            for (int i = 0; i < ad.length(); i++) {
                JSONObject f = ad.optJSONObject(i);
                if (f == null) continue;
                String u = f.optString("url", "");
                String mt = f.optString("mimeType", "");
                if (u.isEmpty() || !mt.startsWith("video/mp4") || !mt.contains("avc1")) continue;
                int hgt = f.optInt("height");
                if (have.contains(hgt)) continue;
                Variant v = new Variant();
                v.url = u;
                v.height = hgt;
                v.label = "Видео " + f.optString("qualityLabel", hgt + "p") + " · со звуком";
                v.size = parseLong(f.optString("contentLength", "0"));
                v.ua = ua;
                v.chunked = true;
                v.audioUrl = best.url;
                v.audioSize = best.size;
                videos.add(v);
                have.add(hgt);
            }
        }
        Collections.sort(videos, new Comparator<Variant>() {
            @Override
            public int compare(Variant a, Variant b) {
                return b.height - a.height;
            }
        });
        info.variants.addAll(videos);
        for (int i = 0; i < audios.size() && i < 2; i++) info.variants.add(audios.get(i));
        return info;
    }

    static Info vk(String url) throws Exception {
        Matcher m = Pattern.compile("(-?\\d+)_(\\d+)").matcher(url);
        if (!m.find()) throw new IOException("Не удалось найти ID видео VK в ссылке");
        String id = m.group(1) + "_" + m.group(2);
        Map<String, String> h = new HashMap<String, String>();
        h.put("Cookie", "remixmdevice=360/800/2/!!-!!!!!!!!/1");
        String page = get("https://m.vk.com/video" + id, UA_MOBILE, h);
        String t = unescape(page);
        Info info = new Info();
        info.site = "VK Видео";
        info.title = pageTitle(page, "Видео VK");
        Set<String> seen = new HashSet<String>();
        Matcher q = Pattern.compile("\"url(\\d{3,4})\"\\s*:\\s*\"(https?://[^\"]+)\"").matcher(t);
        while (q.find()) {
            if (!seen.add(q.group(2))) continue;
            Variant v = new Variant();
            v.url = q.group(2);
            v.height = Integer.parseInt(q.group(1));
            v.label = "Видео " + q.group(1) + "p";
            v.ua = UA_MOBILE;
            info.variants.add(v);
        }
        if (info.variants.isEmpty()) {
            Matcher s = Pattern.compile("<source[^>]+src=\"(https?://[^\"]+)\"").matcher(t);
            int n = 0;
            while (s.find()) {
                String u = s.group(1);
                if (!seen.add(u)) continue;
                if (u.contains(".m3u8")) {
                    try {
                        addHls(info, u, UA_MOBILE);
                    } catch (Exception e) {
                        // пропускаем
                    }
                } else {
                    Variant v = new Variant();
                    v.url = u;
                    v.label = "Видео (вариант " + (++n) + ")";
                    v.ua = UA_MOBILE;
                    info.variants.add(v);
                }
            }
        }
        Collections.sort(info.variants, new Comparator<Variant>() {
            @Override
            public int compare(Variant a, Variant b) {
                return b.height - a.height;
            }
        });
        if (info.variants.isEmpty()) {
            throw new IOException("VK не отдал прямую ссылку: видео закрытое или требует входа в аккаунт");
        }
        return info;
    }

    static String mimeFor(String ext) {
        switch (ext) {
            case "webm": return "video/webm";
            case "mkv": return "video/x-matroska";
            case "mov": return "video/quicktime";
            case "m4v": return "video/x-m4v";
            case "mp3": return "audio/mpeg";
            case "m4a": return "audio/mp4";
            case "ogg": case "opus": return "audio/ogg";
            case "wav": return "audio/wav";
            case "flac": return "audio/flac";
            case "aac": return "audio/aac";
            default: return "video/mp4";
        }
    }

    static void addDirect(Info info, String url, String ext) {
        Variant v = new Variant();
        v.url = url;
        v.ext = ext;
        v.mime = mimeFor(ext);
        v.audio = v.mime.startsWith("audio/");
        v.label = (v.audio ? "Звук · " : "Видео · ") + ext;
        info.variants.add(v);
    }

    static Info generic(String url) throws Exception {
        Info info = new Info();
        info.site = new URL(url).getHost();
        String clean = url.split("[?#]")[0].toLowerCase(Locale.ROOT);
        Matcher dm = Pattern.compile("\\.(mp4|webm|mkv|mov|m4v|mp3|m4a|ogg|opus|wav|flac|aac)$").matcher(clean);
        if (dm.find()) {
            info.title = clean.substring(clean.lastIndexOf('/') + 1);
            addDirect(info, url, dm.group(1));
            return info;
        }
        if (clean.endsWith(".m3u8")) {
            addHls(info, url, UA_DESKTOP);
            return info;
        }
        String page = get(url, UA_DESKTOP, null);
        info.title = pageTitle(page, info.site);
        String t = unescape(page);
        Matcher m = Pattern.compile("https?://[^\\s\"'<>\\\\]+?\\.(mp4|webm|m3u8|mp3|m4a)(?:\\?[^\\s\"'<>\\\\]*)?").matcher(t);
        Set<String> seen = new LinkedHashSet<String>();
        while (m.find() && seen.size() < 12) seen.add(m.group());
        for (String s : seen) {
            if (s.contains(".m3u8")) {
                try {
                    addHls(info, s, UA_DESKTOP);
                } catch (Exception e) {
                    // пропускаем
                }
            } else {
                Matcher em = Pattern.compile("\\.(mp4|webm|mp3|m4a)").matcher(s);
                addDirect(info, s, em.find() ? em.group(1) : "mp4");
            }
        }
        if (info.variants.isEmpty()) throw new IOException("На странице не найдено видео для скачивания");
        return info;
    }

    static void addHls(Info info, String masterUrl, String ua) throws IOException {
        String text = get(masterUrl, ua, null);
        String[] lines = text.split("\r?\n");
        boolean any = false;
        for (int i = 0; i < lines.length; i++) {
            String l = lines[i].trim();
            if (!l.startsWith("#EXT-X-STREAM-INF")) continue;
            int hgt = 0;
            Matcher m = Pattern.compile("RESOLUTION=\\d+x(\\d+)").matcher(l);
            if (m.find()) hgt = Integer.parseInt(m.group(1));
            int j = i + 1;
            while (j < lines.length && (lines[j].trim().isEmpty() || lines[j].startsWith("#"))) j++;
            if (j >= lines.length) break;
            Variant v = new Variant();
            v.url = resolve(masterUrl, lines[j].trim());
            v.hls = true;
            v.ua = ua;
            v.height = hgt;
            v.label = hgt > 0 ? "Видео " + hgt + "p" : "Видео";
            v.ext = "ts";
            v.mime = "video/mp2t";
            info.variants.add(v);
            any = true;
            i = j;
        }
        if (!any) {
            Variant v = new Variant();
            v.url = masterUrl;
            v.hls = true;
            v.ua = ua;
            v.ext = "ts";
            v.mime = "video/mp2t";
            info.variants.add(v);
        }
        Collections.sort(info.variants, new Comparator<Variant>() {
            @Override
            public int compare(Variant a, Variant b) {
                return b.height - a.height;
            }
        });
    }

    /** Подготовка перед скачиванием: размер файла или число частей. */
    static void prepare(Variant v) {
        try {
            if (v.hls) {
                String t = get(v.url, v.ua, null);
                int n = 0;
                for (String l : t.split("\r?\n")) {
                    l = l.trim();
                    if (!l.isEmpty() && !l.startsWith("#")) n++;
                }
                v.segments = n;
                if (t.contains("#EXT-X-MAP")) {
                    v.ext = "mp4";
                    v.mime = "video/mp4";
                }
            } else if (v.size <= 0) {
                HttpURLConnection c = open(v.url, "HEAD", v.ua, null);
                try {
                    int len = c.getContentLength();
                    if (len > 0) v.size = len;
                } finally {
                    c.disconnect();
                }
            }
        } catch (Exception e) {
            // не критично
        }
    }

    // ------------------------------------------------------------ загрузка

    static class Sub implements Progress {
        final Progress p;
        final float from, to;

        Sub(Progress p, float from, float to) {
            this.p = p;
            this.from = from;
            this.to = to;
        }

        @Override
        public void update(long d, long t) {
            if (t > 0) p.update((long) ((from + (to - from) * d / t) * 1000), 1000);
        }

        @Override
        public boolean cancelled() {
            return p.cancelled();
        }
    }

    static void download(Variant v, OutputStream out, Progress p, File cache) throws Exception {
        if (v.audioUrl != null) {
            File vf = File.createTempFile("vid_", ".mp4", cache);
            File af = File.createTempFile("aud_", ".m4a", cache);
            File mf = File.createTempFile("mux_", ".mp4", cache);
            try {
                FileOutputStream fo = new FileOutputStream(vf);
                try {
                    direct(v.url, v.ua, fo, new Sub(p, 0f, 0.6f), v.size, v.chunked);
                } finally {
                    fo.close();
                }
                fo = new FileOutputStream(af);
                try {
                    direct(v.audioUrl, v.ua, fo, new Sub(p, 0.6f, 0.9f), v.audioSize, true);
                } finally {
                    fo.close();
                }
                p.update(900, 1000);
                mux(vf, af, mf);
                FileInputStream fi = new FileInputStream(mf);
                try {
                    byte[] b = new byte[64 * 1024];
                    int n;
                    while ((n = fi.read(b)) > 0) out.write(b, 0, n);
                } finally {
                    fi.close();
                }
                p.update(1000, 1000);
            } finally {
                vf.delete();
                af.delete();
                mf.delete();
            }
        } else if (v.hls) {
            hls(v, out, p);
        } else {
            direct(v.url, v.ua, out, p, v.size, v.chunked);
        }
    }

    static void direct(String url, String ua, OutputStream out, Progress p, long knownSize, boolean chunked) throws IOException {
        byte[] buf = new byte[64 * 1024];
        long done = 0;
        long total = knownSize;
        if (!chunked) {
            HttpURLConnection c = open(url, "GET", ua, null);
            try {
                int code = c.getResponseCode();
                if (code >= 400) throw new IOException("HTTP " + code);
                if (total <= 0) total = c.getContentLength();
                InputStream in = c.getInputStream();
                int n;
                while ((n = in.read(buf)) > 0) {
                    if (p.cancelled()) throw new IOException("Отменено");
                    out.write(buf, 0, n);
                    done += n;
                    p.update(done, total);
                }
            } finally {
                c.disconnect();
            }
            return;
        }
        long chunk = 9L * 1024 * 1024;
        while (total <= 0 || done < total) {
            long end = done + chunk - 1;
            if (total > 0 && end >= total) end = total - 1;
            Map<String, String> h = new HashMap<String, String>();
            h.put("Range", "bytes=" + done + "-" + end);
            HttpURLConnection c = open(url, "GET", ua, h);
            try {
                int code = c.getResponseCode();
                if (code == 416) break;
                if (code != 200 && code != 206) throw new IOException("HTTP " + code);
                if (total <= 0) {
                    String cr = c.getHeaderField("Content-Range");
                    if (cr != null && cr.contains("/")) total = parseLong(cr.substring(cr.indexOf('/') + 1).trim());
                    if (total <= 0 && code == 200) total = c.getContentLength();
                }
                InputStream in = c.getInputStream();
                int n;
                long got = 0;
                while ((n = in.read(buf)) > 0) {
                    if (p.cancelled()) throw new IOException("Отменено");
                    out.write(buf, 0, n);
                    done += n;
                    got += n;
                    p.update(done, total);
                }
                if (got == 0 || code == 200) break;
            } finally {
                c.disconnect();
            }
        }
    }

    static byte[] fetch(String url, String ua) throws IOException {
        IOException last = null;
        for (int attempt = 0; attempt < 3; attempt++) {
            HttpURLConnection c = open(url, "GET", ua, null);
            try {
                int code = c.getResponseCode();
                if (code >= 400) throw new IOException("HTTP " + code);
                InputStream in = c.getInputStream();
                ByteArrayOutputStream b = new ByteArrayOutputStream();
                byte[] buf = new byte[64 * 1024];
                int n;
                while ((n = in.read(buf)) > 0) b.write(buf, 0, n);
                return b.toByteArray();
            } catch (IOException e) {
                last = e;
            } finally {
                c.disconnect();
            }
        }
        throw last != null ? last : new IOException("Не удалось скачать часть видео");
    }

    static void hls(Variant v, OutputStream out, Progress p) throws IOException {
        String text = get(v.url, v.ua, null);
        if (text.contains("#EXT-X-KEY") && text.contains("METHOD=AES")) {
            throw new IOException("Видео зашифровано, такое скачать нельзя");
        }
        List<String> segs = new ArrayList<String>();
        String init = null;
        for (String l : text.split("\r?\n")) {
            l = l.trim();
            if (l.startsWith("#EXT-X-MAP")) {
                Matcher m = Pattern.compile("URI=\"([^\"]+)\"").matcher(l);
                if (m.find()) init = resolve(v.url, m.group(1));
            } else if (!l.isEmpty() && !l.startsWith("#")) {
                segs.add(resolve(v.url, l));
            }
        }
        if (segs.isEmpty()) throw new IOException("Пустой плейлист");
        if (init != null) out.write(fetch(init, v.ua));
        int i = 0;
        for (String s : segs) {
            if (p.cancelled()) throw new IOException("Отменено");
            out.write(fetch(s, v.ua));
            i++;
            p.update(i, segs.size());
        }
    }

    // ------------------------------------------------------------ склейка видео и звука

    static int pickTrack(MediaExtractor ex, String prefix) {
        for (int i = 0; i < ex.getTrackCount(); i++) {
            String m = ex.getTrackFormat(i).getString(MediaFormat.KEY_MIME);
            if (m != null && m.startsWith(prefix)) return i;
        }
        return -1;
    }

    static void copyTrack(MediaExtractor ex, MediaMuxer mm, int track) {
        ByteBuffer buf = ByteBuffer.allocate(1 << 20);
        MediaCodec.BufferInfo bi = new MediaCodec.BufferInfo();
        while (true) {
            int n = ex.readSampleData(buf, 0);
            if (n < 0) break;
            bi.offset = 0;
            bi.size = n;
            bi.presentationTimeUs = ex.getSampleTime();
            bi.flags = (ex.getSampleFlags() & MediaExtractor.SAMPLE_FLAG_SYNC) != 0
                    ? MediaCodec.BUFFER_FLAG_KEY_FRAME : 0;
            mm.writeSampleData(track, buf, bi);
            ex.advance();
        }
    }

    static void mux(File video, File audio, File out) throws IOException {
        MediaExtractor ve = new MediaExtractor();
        MediaExtractor ae = new MediaExtractor();
        MediaMuxer mm = null;
        try {
            ve.setDataSource(video.getPath());
            ae.setDataSource(audio.getPath());
            int vi = pickTrack(ve, "video/");
            int ai = pickTrack(ae, "audio/");
            if (vi < 0 || ai < 0) throw new IOException("Не удалось объединить видео и звук");
            ve.selectTrack(vi);
            ae.selectTrack(ai);
            mm = new MediaMuxer(out.getPath(), MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4);
            int vt = mm.addTrack(ve.getTrackFormat(vi));
            int at = mm.addTrack(ae.getTrackFormat(ai));
            mm.start();
            copyTrack(ve, mm, vt);
            copyTrack(ae, mm, at);
            mm.stop();
        } finally {
            try {
                if (mm != null) mm.release();
            } catch (Exception e) {
                // игнорируем
            }
            ve.release();
            ae.release();
        }
    }
}
