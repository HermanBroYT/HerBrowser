package com.herbrowser.app;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONException;

import java.util.ArrayList;
import java.util.List;

class Store {
    private final SharedPreferences sp;

    Store(Context c) {
        sp = c.getSharedPreferences("herbrowser", Context.MODE_PRIVATE);
    }

    String getStr(String key, String def) {
        return sp.getString(key, def);
    }

    void setStr(String key, String value) {
        sp.edit().putString(key, value).apply();
    }

    List<String[]> list(String key) {
        List<String[]> r = new ArrayList<String[]>();
        try {
            JSONArray a = new JSONArray(sp.getString(key, "[]"));
            for (int i = 0; i < a.length(); i++) {
                JSONArray o = a.getJSONArray(i);
                String[] s = new String[o.length()];
                for (int j = 0; j < s.length; j++) s[j] = o.getString(j);
                r.add(s);
            }
        } catch (JSONException e) {
            // пустой список
        }
        return r;
    }

    void save(String key, List<String[]> items) {
        JSONArray a = new JSONArray();
        for (String[] s : items) {
            JSONArray o = new JSONArray();
            for (String x : s) o.put(x);
            a.put(o);
        }
        sp.edit().putString(key, a.toString()).apply();
    }

    void add(String key, String[] item, int max) {
        List<String[]> l = list(key);
        l.add(0, item);
        while (l.size() > max) l.remove(l.size() - 1);
        save(key, l);
    }

    void append(String key, String[] item) {
        List<String[]> l = list(key);
        l.add(item);
        save(key, l);
    }

    void remove(String key, int index) {
        List<String[]> l = list(key);
        if (index >= 0 && index < l.size()) {
            l.remove(index);
            save(key, l);
        }
    }

    void clear(String key) {
        sp.edit().remove(key).apply();
    }
}
