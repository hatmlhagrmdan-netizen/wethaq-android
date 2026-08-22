package com.wethaq.app;

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.InputType;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URLEncoder;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class MainActivity extends Activity {
    private static final String API = "https://wethaq-backend-production.up.railway.app";
    private static final String PREFS = "wethaq";
    private static final String TOKEN = "token";
    private static final String USER_ID = "wethaq_id";
    private static final String NAME = "name";
    private static final String CONTACTS = "saved_contacts";
    private static final String FOUNDER = "المؤسس: حاتم حسين الحاج رمضان";

    private final ExecutorService io = Executors.newSingleThreadExecutor();
    private final Handler main = new Handler(Looper.getMainLooper());
    private SharedPreferences prefs;
    private LinearLayout root, content, messages;
    private EditText input;
    private String activeId, activeName;
    private Runnable poller;

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        prefs = getSharedPreferences(PREFS, MODE_PRIVATE);
        if (hasToken()) home(); else login();
    }

    @Override protected void onResume() {
        super.onResume();
        if (hasToken()) startPolling();
    }

    @Override protected void onPause() {
        stopPolling();
        super.onPause();
    }

    @Override protected void onDestroy() {
        stopPolling();
        io.shutdownNow();
        super.onDestroy();
    }

    private boolean hasToken() { return prefs.getString(TOKEN, "").length() > 10; }

    private void base(String title) {
        root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(16, 16, 16, 16);
        root.setBackgroundColor(Color.rgb(7, 15, 27));

        TextView h = tv(title, 22, Color.WHITE);
        h.setGravity(17);
        root.addView(h, lp(-1, -2, 0, 12));

        ScrollView scroll = new ScrollView(this);
        content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        scroll.addView(content);
        root.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));
        setContentView(root);
    }

    private TextView tv(String text, float size, int color) {
        TextView t = new TextView(this);
        t.setText(text);
        t.setTextSize(size);
        t.setTextColor(color);
        t.setPadding(10, 10, 10, 10);
        return t;
    }

    private EditText field(String hint) {
        EditText e = new EditText(this);
        e.setHint(hint);
        e.setTextColor(Color.WHITE);
        e.setHintTextColor(Color.LTGRAY);
        e.setPadding(14, 10, 14, 10);
        return e;
    }

    private Button btn(String text) {
        Button b = new Button(this);
        b.setText(text);
        return b;
    }

    private LinearLayout.LayoutParams lp(int width, int height, int left, int bottom) {
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(width, height);
        p.leftMargin = left;
        p.bottomMargin = bottom;
        return p;
    }

    private void login() {
        base("وَثاق");
        TextView title = tv("هويتك الرقمية الآمنة", 24, Color.WHITE);
        title.setGravity(17);
        content.addView(title);

        EditText name = field("الاسم الثلاثي");
        EditText year = field("سنة الميلاد");
        year.setInputType(InputType.TYPE_CLASS_NUMBER);
        content.addView(name, lp(-1, -2, 0, 8));
        content.addView(year, lp(-1, -2, 0, 8));

        Button login = btn("دخول");
        Button create = btn("إنشاء هوية جديدة");
        content.addView(login);
        content.addView(create);

        TextView founder = tv(FOUNDER, 14, Color.LTGRAY);
        founder.setGravity(17);
        content.addView(founder, lp(-1, -2, 0, 12));

        login.setOnClickListener(v -> authenticate(name.getText().toString().trim(), year.getText().toString().trim(), false));
        create.setOnClickListener(v -> authenticate(name.getText().toString().trim(), year.getText().toString().trim(), true));
    }

    private boolean validIdentity(String name, String year) {
        return name.split("\\s+").length >= 3 && year.matches("\\d{4}");
    }

    private void authenticate(String name, String year, boolean create) {
        if (!validIdentity(name, year)) {
            toast("أدخل الاسم الثلاثي وسنة الميلاد");
            return;
        }
        io.execute(() -> {
            try {
                JSONObject body = new JSONObject();
                body.put("name", name);
                body.put("birthYear", Integer.parseInt(year));
                body.put("deviceKey", deviceKey());

                HttpResult r = request("POST", create ? "/api/identity" : "/api/login", body.toString(), null);
                if (!create && r.code == 404) {
                    r = request("POST", "/api/identity", body.toString(), null);
                }

                if (r.code >= 200 && r.code < 300) {
                    JSONObject response = new JSONObject(r.body);
                    JSONObject user = response.getJSONObject("user");
                    prefs.edit()
                            .putString(TOKEN, response.getString("token"))
                            .putString(USER_ID, user.getString("wethaq_id"))
                            .putString(NAME, user.getString("name"))
                            .putString("numeric_id", String.valueOf(user.optInt("id", -1)))
                            .apply();
                    main.post(this::home);
                } else {
                    String message = error(r);
                    main.post(() -> toast(message));
                }
            } catch (Exception e) {
                main.post(() -> toast("تعذر الاتصال بالخادم"));
            }
        });
    }

    private void home() {
        stopPolling();
        activeId = null;
        base(FOUNDER);

        TextView name = tv(prefs.getString(NAME, "مستخدم وَثاق"), 20, Color.WHITE);
        TextView id = tv(prefs.getString(USER_ID, ""), 14, Color.LTGRAY);
        name.setGravity(17);
        id.setGravity(17);
        content.addView(name);
        content.addView(id);

        Button search = btn("البحث عن مستخدم");
        Button contacts = btn("جهات الاتصال");
        Button logout = btn("تسجيل الخروج");
        content.addView(search);
        content.addView(contacts);
        content.addView(logout);

        search.setOnClickListener(v -> searchScreen());
        contacts.setOnClickListener(v -> contactsScreen());
        logout.setOnClickListener(v -> {
            prefs.edit().clear().apply();
            stopPolling();
            login();
        });
        startPolling();
    }

    private void searchScreen() {
        base("البحث");
        Button back = btn("العودة");
        EditText query = field("الاسم أو معرف وَثاق");
        Button go = btn("بحث");
        LinearLayout list = new LinearLayout(this);
        list.setOrientation(LinearLayout.VERTICAL);
        content.addView(back);
        content.addView(query);
        content.addView(go);
        content.addView(list);
        back.setOnClickListener(v -> home());
        go.setOnClickListener(v -> search(query.getText().toString().trim(), list));
    }

    private void search(String query, LinearLayout list) {
        if (query.length() < 2) {
            toast("أدخل حرفين على الأقل");
            return;
        }
        io.execute(() -> {
            try {
                HttpResult r = request("GET", "/api/search?q=" + URLEncoder.encode(query, "UTF-8"), null, null);
                if (r.code == 200) {
                    JSONArray users = new JSONObject(r.body).optJSONArray("users");
                    main.post(() -> {
                        list.removeAllViews();
                        if (users == null || users.length() == 0) {
                            list.addView(tv("لا توجد نتائج", 16, Color.LTGRAY));
                            return;
                        }
                        for (int i = 0; i < users.length(); i++) {
                            JSONObject user = users.optJSONObject(i);
                            if (user != null) addResult(list, user);
                        }
                    });
                } else {
                    String message = error(r);
                    main.post(() -> toast(message));
                }
            } catch (Exception e) {
                main.post(() -> toast("فشل البحث"));
            }
        });
    }

    private void addResult(LinearLayout list, JSONObject user) {
        String id = user.optString("wethaq_id");
        String name = user.optString("name");
        list.addView(tv(name + "\n" + id, 18, Color.WHITE));
        Button save = btn("حفظ في جهات الاتصال");
        list.addView(save);
        save.setOnClickListener(v -> saveContact(id, name));
    }

    private void saveContact(String id, String name) {
        if (id == null || id.trim().isEmpty()) return;
        io.execute(() -> {
            try {
                JSONObject body = new JSONObject();
                body.put("wethaqId", id);
                body.put("receiver_id", id);
                HttpResult r = request("POST", "/api/contacts", body.toString(), auth());
                if (r.code >= 200 && r.code < 300) {
                    saveLocal(id, name);
                    main.post(() -> toast("تم الحفظ ✓"));
                } else {
                    String message = error(r);
                    main.post(() -> toast(message));
                }
            } catch (Exception e) {
                main.post(() -> toast("تعذر حفظ جهة الاتصال"));
            }
        });
    }

    private void contactsScreen() {
        base("جهات الاتصال");
        Button back = btn("العودة");
        content.addView(back);
        back.setOnClickListener(v -> home());

        JSONArray contacts = localContacts();
        if (contacts.length() == 0) {
            content.addView(tv("لا توجد جهات اتصال محفوظة", 16, Color.LTGRAY));
            return;
        }
        for (int i = 0; i < contacts.length(); i++) {
            JSONObject user = contacts.optJSONObject(i);
            if (user == null) continue;
            String id = user.optString("wethaq_id");
            String name = user.optString("name");
            Button contact = btn(name + "\n" + id);
            content.addView(contact);
            contact.setOnClickListener(v -> conversation(id, name));
        }
    }

    private void conversation(String id, String name) {
        stopPolling();
        activeId = id;
        activeName = name;
        base("محادثة مع " + name);

        Button back = btn("جهات الاتصال");
        content.addView(back);

        messages = new LinearLayout(this);
        messages.setOrientation(LinearLayout.VERTICAL);
        ScrollView scroll = new ScrollView(this);
        scroll.addView(messages);
        content.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));

        LinearLayout bar = new LinearLayout(this);
        input = field("اكتب رسالة…");
        Button send = btn("إرسال");
        bar.addView(input, new LinearLayout.LayoutParams(0, -2, 1));
        bar.addView(send, new LinearLayout.LayoutParams(-2, -2));
        root.addView(bar, new LinearLayout.LayoutParams(-1, -2));

        back.setOnClickListener(v -> contactsScreen());
        send.setOnClickListener(v -> sendMessage());
        loadMessages();
        startPolling();
    }

    private void loadMessages() {
        if (activeId == null || activeId.isEmpty()) return;
        final String target = activeId;
        io.execute(() -> {
            try {
                HttpResult r = request("GET", "/api/messages/" + URLEncoder.encode(target, "UTF-8"), null, auth());
                if (r.code == 200) {
                    JSONArray data = new JSONObject(r.body).optJSONArray("messages");
                    main.post(() -> {
                        if (target.equals(activeId)) renderMessages(data);
                    });
                }
            } catch (Exception ignored) { }
        });
    }

    private void renderMessages(JSONArray data) {
        if (messages == null) return;
        messages.removeAllViews();
        if (data == null || data.length() == 0) {
            messages.addView(tv("ابدأ المحادثة برسالة جديدة.", 16, Color.LTGRAY));
            return;
        }
        for (int i = 0; i < data.length(); i++) {
            JSONObject message = data.optJSONObject(i);
            if (message != null) {
                messages.addView(tv(message.optString("body"), 16, Color.WHITE));
            }
        }
    }

    private void sendMessage() {
        if (input == null) return;
        final String text = input.getText().toString().trim();
        final String target = activeId;
        if (text.isEmpty() || target == null || target.isEmpty()) return;
        if (target.equals(prefs.getString(USER_ID, ""))) {
            toast("لا يمكن الإرسال إلى نفسك");
            return;
        }

        input.setEnabled(false);
        io.execute(() -> {
            try {
                JSONObject body = new JSONObject();
                body.put("receiver_id", target);
                body.put("to", target);
                body.put("body", text);

                HttpResult r = request("POST", "/api/messages", body.toString(), auth());
                main.post(() -> {
                    input.setEnabled(true);
                    if (r.code >= 200 && r.code < 300) {
                        input.setText("");
                        loadMessages();
                        toast("تم إرسال الرسالة ✓");
                    } else {
                        toast(error(r));
                    }
                });
            } catch (Exception e) {
                main.post(() -> {
                    input.setEnabled(true);
                    toast("فشل الإرسال");
                });
            }
        });
    }

    private void startPolling() {
        stopPolling();
        poller = () -> {
            if (activeId != null) loadMessages();
            pollInbox();
            if (hasToken()) main.postDelayed(poller, 2500);
        };
        main.postDelayed(poller, 500);
    }

    private void stopPolling() {
        if (poller != null) main.removeCallbacks(poller);
        poller = null;
    }

    private void pollInbox() {
        if (!hasToken()) return;
        io.execute(() -> {
            try { request("GET", "/api/messages/inbox", null, auth()); }
            catch (Exception ignored) { }
        });
    }

    private JSONArray localContacts() {
        try { return new JSONArray(prefs.getString(CONTACTS, "[]")); }
        catch (Exception e) { return new JSONArray(); }
    }

    private void saveLocal(String id, String name) {
        try {
            JSONArray contacts = localContacts();
            for (int i = 0; i < contacts.length(); i++) {
                JSONObject old = contacts.optJSONObject(i);
                if (old != null && id.equals(old.optString("wethaq_id"))) return;
            }
            JSONObject contact = new JSONObject();
            contact.put("wethaq_id", id);
            contact.put("name", name);
            contacts.put(contact);
            prefs.edit().putString(CONTACTS, contacts.toString()).apply();
        } catch (Exception ignored) { }
    }

    private String deviceKey() {
        String key = prefs.getString("device_key", "");
        if (key.length() > 20) return key;
        key = "wethaq-" + UUID.randomUUID();
        prefs.edit().putString("device_key", key).apply();
        return key;
    }

    private String auth() { return "Bearer " + prefs.getString(TOKEN, ""); }

    private void toast(String text) { Toast.makeText(this, text, Toast.LENGTH_SHORT).show(); }

    private String error(HttpResult r) {
        try {
            JSONObject object = new JSONObject(r.body);
            String value = object.optString("error");
            if (!value.isEmpty()) return value;
            value = object.optString("message");
            if (!value.isEmpty()) return value;
        } catch (Exception ignored) { }
        return "HTTP " + r.code;
    }

    private HttpResult request(String method, String path, String body, String authorization) throws Exception {
        HttpURLConnection connection = (HttpURLConnection) new URL(API + path).openConnection();
        connection.setRequestMethod(method);
        connection.setConnectTimeout(12000);
        connection.setReadTimeout(15000);
        connection.setRequestProperty("Accept", "application/json");
        if (authorization != null) connection.setRequestProperty("Authorization", authorization);
        if (body != null) {
            connection.setDoOutput(true);
            connection.setRequestProperty("Content-Type", "application/json; charset=UTF-8");
            byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
            connection.setFixedLengthStreamingMode(bytes.length);
            try (OutputStream output = connection.getOutputStream()) { output.write(bytes); }
        }
        int code = connection.getResponseCode();
        InputStream inputStream = code >= 200 && code < 400 ? connection.getInputStream() : connection.getErrorStream();
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        if (inputStream != null) {
            byte[] buffer = new byte[4096];
            int count;
            while ((count = inputStream.read(buffer)) != -1) output.write(buffer, 0, count);
            inputStream.close();
        }
        connection.disconnect();
        return new HttpResult(code, new String(output.toByteArray(), StandardCharsets.UTF_8));
    }

    private static final class HttpResult {
        final int code;
        final String body;
        HttpResult(int code, String body) { this.code = code; this.body = body; }
    }
}
