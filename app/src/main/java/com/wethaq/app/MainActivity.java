package com.wethaq.app;

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.Typeface;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class MainActivity extends Activity {
    private static final String API = "https://wethaq-backend-production.up.railway.app";
    private static final String PREFS = "wethaq";
    private static final String TOKEN = "token";
    private static final String USER_ID = "wethaq_id";
    private static final String NAME = "name";
    private static final String BIRTH_YEAR = "birth_year";

    private final ExecutorService io = Executors.newSingleThreadExecutor();
    private final Handler main = new Handler(Looper.getMainLooper());
    private SharedPreferences prefs;
    private LinearLayout root;
    private TextView title;
    private TextView status;
    private LinearLayout content;
    private String activeContactId;
    private String activeContactName;
    private EditText messageInput;
    private LinearLayout messageList;
    private Runnable poller;

    private final int bg = Color.rgb(9, 17, 29);
    private final int panel = Color.rgb(18, 31, 48);
    private final int panel2 = Color.rgb(25, 42, 63);
    private final int text = Color.WHITE;
    private final int muted = Color.rgb(180, 194, 208);
    private final int accent = Color.rgb(70, 145, 170);

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        prefs = getSharedPreferences(PREFS, MODE_PRIVATE);
        if (hasToken()) showHome(); else showLogin();
    }

    @Override
    protected void onResume() {
        super.onResume();
        flushOutbox();
        if (activeContactId != null) startPolling();
    }

    @Override
    protected void onPause() {
        super.onPause();
        stopPolling();
    }

    @Override
    protected void onDestroy() {
        stopPolling();
        io.shutdownNow();
        super.onDestroy();
    }

    private boolean hasToken() {
        return prefs.getString(TOKEN, "").length() > 10;
    }

    private void base(String heading) {
        root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(bg);
        root.setPadding(28, 28, 28, 28);

        LinearLayout header = new LinearLayout(this);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.setPadding(18, 14, 18, 14);
        header.setBackgroundColor(panel);

        title = tv(heading, 26, text, Typeface.BOLD);
        header.addView(title, new LinearLayout.LayoutParams(0, -2, 1));
        root.addView(header, new LinearLayout.LayoutParams(-1, -2));

        status = tv("", 13, muted, Typeface.NORMAL);
        status.setGravity(Gravity.CENTER);
        status.setPadding(8, 10, 8, 8);
        root.addView(status, new LinearLayout.LayoutParams(-1, -2));

        content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.addView(content, new ScrollView.LayoutParams(-1, -1));
        root.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));
        setContentView(root);
    }

    private void showLogin() {
        stopPolling();
        activeContactId = null;
        base("وَثاق");
        TextView welcome = tv("هويتك الرقمية الآمنة", 22, text, Typeface.BOLD);
        welcome.setGravity(Gravity.CENTER);
        content.addView(welcome, lp(18, 18, 18, 18));

        TextView hint = tv("أدخل الاسم الثلاثي وسنة الميلاد لإنشاء أو استعادة هويتك.", 15, muted, Typeface.NORMAL);
        hint.setGravity(Gravity.CENTER);
        content.addView(hint, lp(8, 0, 8, 22));

        EditText name = field("الاسم الثلاثي");
        EditText year = field("سنة الميلاد");
        year.setInputType(2);
        content.addView(name, lp(0, 8, 0, 8));
        content.addView(year, lp(0, 8, 0, 8));

        Button login = button("دخول / إنشاء الهوية");
        content.addView(login, lp(0, 18, 0, 8));

        TextView note = tv("يتم حفظ الجلسة على الجهاز، ولا يحتاج تسجيل الدخول إلى رقم هاتف.", 13, muted, Typeface.NORMAL);
        note.setGravity(Gravity.CENTER);
        content.addView(note, lp(10, 12, 10, 10));

        login.setOnClickListener(v -> {
            String n = name.getText().toString().trim();
            String y = year.getText().toString().trim();
            if (n.split("\\s+").length < 3 || y.length() != 4) {
                setStatus("أدخل اسمًا من ثلاثة مقاطع وسنة صحيحة.", true);
                return;
            }
            login(n, y);
        });
        setStatus(isOnline() ? "متصل بالخادم" : "غير متصل — يلزم الإنترنت لأول تسجيل دخول", false);
    }

    private void login(String name, String year) {
        setBusy(true);
        setStatus("جاري الاتصال بالخادم…", false);
        io.execute(() -> {
            try {
                String deviceKey = getDeviceKey();
                JSONObject body = new JSONObject();
                body.put("name", name);
                body.put("birthYear", Integer.parseInt(year));
                body.put("deviceKey", deviceKey);
                HttpResult r = request("POST", "/api/identity", body.toString(), null);
                if (r.code >= 200 && r.code < 300) {
                    JSONObject data = new JSONObject(r.body);
                    JSONObject u = data.getJSONObject("user");
                    saveSession(data.getString("token"), u.getString("wethaq_id"), u.getString("name"), u.optInt("birth_year"));
                    main.post(() -> { setBusy(false); showHome(); });
                } else {
                    main.post(() -> { setBusy(false); setStatus(errorText(r), true); });
                }
            } catch (Exception e) {
                main.post(() -> { setBusy(false); setStatus("تعذر الاتصال بالخادم. تحقق من الإنترنت.", true); });
            }
        });
    }

    private void showHome() {
        stopPolling();
        activeContactId = null;
        base("وَثاق");

        LinearLayout identity = card();
        TextView name = tv(prefs.getString(NAME, "مستخدم وَثاق"), 20, text, Typeface.BOLD);
        TextView id = tv("المعرف: " + prefs.getString(USER_ID, ""), 14, muted, Typeface.NORMAL);
        identity.addView(name);
        identity.addView(id, lp(0, 8, 0, 0));
        content.addView(identity, lp(0, 4, 0, 12));

        Button search = button("البحث عن مستخدم");
        Button contacts = button("جهات الاتصال");
        Button logout = button("تسجيل الخروج");
        content.addView(search, lp(0, 6, 0, 6));
        content.addView(contacts, lp(0, 6, 0, 6));
        content.addView(logout, lp(0, 6, 0, 6));

        search.setOnClickListener(v -> showSearch());
        contacts.setOnClickListener(v -> showContacts());
        logout.setOnClickListener(v -> {
            prefs.edit().remove(TOKEN).apply();
            showLogin();
        });
        setStatus(isOnline() ? "متصل" : "وضع عدم الاتصال — الهوية المحلية متاحة", false);
        if (isOnline()) refreshMe();
    }

    private void showSearch() {
        stopPolling();
        base("البحث");
        Button back = button("← العودة");
        content.addView(back, lp(0, 0, 0, 12));
        EditText q = field("الاسم أو معرف وَثاق");
        Button go = button("بحث");
        content.addView(q, lp(0, 4, 0, 8));
        content.addView(go, lp(0, 4, 0, 12));
        LinearLayout results = new LinearLayout(this);
        results.setOrientation(LinearLayout.VERTICAL);
        content.addView(results, lp(0, 4, 0, 0));
        back.setOnClickListener(v -> showHome());
        go.setOnClickListener(v -> search(q.getText().toString().trim(), results));
        setStatus("اكتب حرفين على الأقل للبحث.", false);
    }

    private void search(String q, LinearLayout results) {
        if (q.length() < 2) { setStatus("أدخل حرفين على الأقل.", true); return; }
        setStatus("جاري البحث…", false);
        io.execute(() -> {
            try {
                HttpResult r = request("GET", "/api/search?q=" + URLEncoder.encode(q, "UTF-8"), null, null);
                if (r.code == 200) {
                    JSONArray users = new JSONObject(r.body).optJSONArray("users");
                    main.post(() -> {
                        results.removeAllViews();
                        if (users == null || users.length() == 0) {
                            results.addView(tv("لا توجد نتائج.", 16, muted, Typeface.NORMAL), lp(8, 16, 8, 8));
                        } else {
                            for (int i = 0; i < users.length(); i++) {
                                JSONObject u = users.optJSONObject(i);
                                if (u != null) addUserResult(results, u);
                            }
                        }
                        setStatus("تم البحث.", false);
                    });
                } else main.post(() -> setStatus(errorText(r), true));
            } catch (Exception e) { main.post(() -> setStatus("فشل البحث. تحقق من الاتصال.", true)); }
        });
    }

    private void addUserResult(LinearLayout results, JSONObject u) {
        LinearLayout row = card();
        String wid = u.optString("wethaq_id");
        TextView n = tv(u.optString("name"), 18, text, Typeface.BOLD);
        TextView i = tv(wid + (u.optBoolean("online") ? "  • متصل" : ""), 13, muted, Typeface.NORMAL);
        Button add = button("إضافة");
        row.addView(n);
        row.addView(i, lp(0, 5, 0, 8));
        row.addView(add);
        results.addView(row, lp(0, 5, 0, 8));
        add.setOnClickListener(v -> addContact(wid, add));
    }

    private void addContact(String id, Button add) {
        io.execute(() -> {
            try {
                JSONObject b = new JSONObject().put("wethaqId", id);
                HttpResult r = request("POST", "/api/contacts", b.toString(), auth());
                main.post(() -> {
                    if (r.code >= 200 && r.code < 300) { add.setText("تمت الإضافة ✓"); add.setEnabled(false); }
                    else setStatus(errorText(r), true);
                });
            } catch (Exception e) { main.post(() -> setStatus("تعذر إضافة المستخدم.", true)); }
        });
    }

    private void showContacts() {
        stopPolling();
        base("جهات الاتصال");
        Button back = button("← العودة");
        content.addView(back, lp(0, 0, 0, 12));
        LinearLayout list = new LinearLayout(this);
        list.setOrientation(LinearLayout.VERTICAL);
        content.addView(list, lp(0, 0, 0, 0));
        back.setOnClickListener(v -> showHome());
        if (!isOnline()) { setStatus("لا يوجد اتصال. البيانات المحلية ستظهر إن كانت محفوظة.", false); return; }
        setStatus("جاري تحميل جهات الاتصال…", false);
        io.execute(() -> {
            try {
                HttpResult r = request("GET", "/api/contacts", null, auth());
                if (r.code == 200) {
                    JSONArray contacts = new JSONObject(r.body).optJSONArray("contacts");
                    main.post(() -> {
                        list.removeAllViews();
                        if (contacts == null || contacts.length() == 0) list.addView(tv("لا توجد جهات اتصال بعد.", 16, muted, Typeface.NORMAL), lp(8, 18, 8, 8));
                        else for (int i = 0; i < contacts.length(); i++) addContactRow(list, contacts.optJSONObject(i));
                        setStatus("تم التحميل.", false);
                    });
                } else main.post(() -> setStatus(errorText(r), true));
            } catch (Exception e) { main.post(() -> setStatus("تعذر تحميل جهات الاتصال.", true)); }
        });
    }

    private void addContactRow(LinearLayout list, JSONObject u) {
        String id = u.optString("wethaq_id");
        Button row = button(u.optString("name") + "\n" + id);
        row.setGravity(Gravity.CENTER_VERTICAL | Gravity.RIGHT);
        list.addView(row, lp(0, 5, 0, 5));
        row.setOnClickListener(v -> showConversation(id, u.optString("name")));
    }

    private void showConversation(String contactId, String contactName) {
        stopPolling();
        activeContactId = contactId;
        activeContactName = contactName;
        base(contactName);

        Button back = button("← جهات الاتصال");
        content.addView(back, lp(0, 0, 0, 8));
        messageList = new LinearLayout(this);
        messageList.setOrientation(LinearLayout.VERTICAL);
        ScrollView messagesScroll = new ScrollView(this);
        messagesScroll.setFillViewport(true);
        messagesScroll.addView(messageList, new ScrollView.LayoutParams(-1, -2));
        content.addView(messagesScroll, new LinearLayout.LayoutParams(-1, 0, 1));

        LinearLayout composer = new LinearLayout(this);
        composer.setGravity(Gravity.CENTER_VERTICAL);
        messageInput = field("اكتب رسالتك…");
        Button send = button("إرسال");
        composer.addView(messageInput, new LinearLayout.LayoutParams(0, -2, 1));
        composer.addView(send, new LinearLayout.LayoutParams(-2, -2));
        root.addView(composer, new LinearLayout.LayoutParams(-1, -2));

        back.setOnClickListener(v -> showContacts());
        send.setOnClickListener(v -> sendMessage(messageInput.getText().toString().trim()));
        loadMessages();
        startPolling();
    }

    private void loadMessages() {
        if (!isOnline()) { setStatus("غير متصل — سيتم عرض الرسائل عند توفر الاتصال.", false); return; }
        io.execute(() -> {
            try {
                HttpResult r = request("GET", "/api/messages/" + URLEncoder.encode(activeContactId, "UTF-8"), null, auth());
                if (r.code == 200) {
                    JSONArray arr = new JSONObject(r.body).optJSONArray("messages");
                    main.post(() -> renderMessages(arr));
                } else main.post(() -> setStatus(errorText(r), true));
            } catch (Exception e) { main.post(() -> setStatus("تعذر تحميل المحادثة.", true)); }
        });
    }

    private void renderMessages(JSONArray arr) {
        if (messageList == null) return;
        messageList.removeAllViews();
        if (arr == null || arr.length() == 0) {
            messageList.addView(tv("لا توجد رسائل. ابدأ المحادثة.", 15, muted, Typeface.NORMAL), lp(8, 20, 8, 8));
            return;
        }
        String me = prefs.getString(USER_ID, "");
        // The API returns numeric sender IDs, so display direction by cached current user's numeric id when available.
        for (int i = 0; i < arr.length(); i++) {
            JSONObject m = arr.optJSONObject(i);
            if (m == null) continue;
            boolean mine = String.valueOf(m.optInt("sender_id", -1)).equals(prefs.getString("numeric_id", "-2"));
            LinearLayout bubble = card();
            bubble.setBackgroundColor(mine ? panel2 : panel);
            TextView body = tv(m.optString("body"), 16, text, Typeface.NORMAL);
            TextView meta = tv(m.optString("status") + "  •  " + m.optString("created_at"), 11, muted, Typeface.NORMAL);
            bubble.addView(body);
            bubble.addView(meta, lp(0, 5, 0, 0));
            messageList.addView(bubble, lp(mine ? 60 : 0, 5, mine ? 0 : 60, 5));
        }
    }

    private void sendMessage(String body) {
        if (body.length() == 0) return;
        if (body.length() > 4000) { setStatus("الرسالة طويلة جدًا.", true); return; }
        messageInput.setText("");
        if (!isOnline()) {
            queueMessage(activeContactId, body);
            appendPending(body);
            setStatus("تم حفظ الرسالة محليًا وستُرسل عند عودة الاتصال.", false);
            return;
        }
        io.execute(() -> {
            try {
                JSONObject b = new JSONObject().put("to", activeContactId).put("body", body);
                HttpResult r = request("POST", "/api/messages", b.toString(), auth());
                if (r.code >= 200 && r.code < 300) main.post(this::loadMessages);
                else main.post(() -> { queueMessage(activeContactId, body); appendPending(body); setStatus(errorText(r), true); });
            } catch (Exception e) {
                queueMessage(activeContactId, body);
                main.post(() -> { appendPending(body); setStatus("انقطع الاتصال؛ حُفظت الرسالة لإعادة الإرسال.", false); });
            }
        });
    }

    private void appendPending(String body) {
        if (messageList == null) return;
        TextView t = tv("معلّقة: " + body, 15, muted, Typeface.ITALIC);
        messageList.addView(t, lp(60, 5, 0, 5));
    }

    private void startPolling() {
        stopPolling();
        poller = () -> {
            if (activeContactId != null && isOnline()) loadMessages();
            if (activeContactId != null) main.postDelayed(poller, 5000);
        };
        main.postDelayed(poller, 5000);
    }

    private void stopPolling() {
        if (poller != null) main.removeCallbacks(poller);
        poller = null;
    }

    private void flushOutbox() {
        if (!isOnline()) return;
        String raw = prefs.getString("outbox", "[]");
        io.execute(() -> {
            try {
                JSONArray q = new JSONArray(raw);
                JSONArray left = new JSONArray();
                for (int i = 0; i < q.length(); i++) {
                    JSONObject item = q.optJSONObject(i);
                    if (item == null) continue;
                    try {
                        JSONObject b = new JSONObject().put("to", item.getString("to")).put("body", item.getString("body"));
                        HttpResult r = request("POST", "/api/messages", b.toString(), auth());
                        if (r.code < 200 || r.code >= 300) left.put(item);
                    } catch (Exception e) { left.put(item); }
                }
                prefs.edit().putString("outbox", left.toString()).apply();
                if (left.length() == 0) main.post(() -> setStatus("تمت مزامنة الرسائل المعلقة.", false));
            } catch (Exception ignored) { }
        });
    }

    private void queueMessage(String to, String body) {
        try {
            JSONArray q = new JSONArray(prefs.getString("outbox", "[]"));
            q.put(new JSONObject().put("to", to).put("body", body));
            prefs.edit().putString("outbox", q.toString()).apply();
        } catch (Exception ignored) { }
    }

    private void refreshMe() {
        io.execute(() -> {
            try {
                HttpResult r = request("GET", "/api/me", null, auth());
                if (r.code == 200) {
                    JSONObject u = new JSONObject(r.body).getJSONObject("user");
                    prefs.edit().putString(USER_ID, u.getString("wethaq_id")).putString(NAME, u.getString("name")).putString("numeric_id", String.valueOf(u.getInt("id"))).apply();
                }
            } catch (Exception ignored) { }
        });
    }

    private String getDeviceKey() {
        String k = prefs.getString("device_key", "");
        if (k.length() >= 24) return k;
        k = "wethaq-" + java.util.UUID.randomUUID() + "-" + java.util.UUID.randomUUID();
        prefs.edit().putString("device_key", k).apply();
        return k;
    }

    private void saveSession(String token, String id, String name, int birthYear) {
        prefs.edit().putString(TOKEN, token).putString(USER_ID, id).putString(NAME, name).putInt(BIRTH_YEAR, birthYear).apply();
    }

    private String auth() { return "Bearer " + prefs.getString(TOKEN, ""); }

    private HttpResult request(String method, String path, String body, String authorization) throws Exception {
        HttpURLConnection c = (HttpURLConnection) new URL(API + path).openConnection();
        c.setRequestMethod(method);
        c.setConnectTimeout(12000);
        c.setReadTimeout(15000);
        c.setRequestProperty("Accept", "application/json");
        if (authorization != null) c.setRequestProperty("Authorization", authorization);
        if (body != null) {
            c.setDoOutput(true);
            c.setRequestProperty("Content-Type", "application/json; charset=UTF-8");
            byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
            c.setFixedLengthStreamingMode(bytes.length);
            try (OutputStream out = c.getOutputStream()) { out.write(bytes); }
        }
        int code = c.getResponseCode();
        InputStream stream = code >= 400 ? c.getErrorStream() : c.getInputStream();
        String response = read(stream);
        c.disconnect();
        return new HttpResult(code, response);
    }

    private String read(InputStream in) throws Exception {
        if (in == null) return "";
        StringBuilder b = new StringBuilder();
        try (BufferedReader r = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
            String line;
            while ((line = r.readLine()) != null) b.append(line);
        }
        return b.toString();
    }

    private String errorText(HttpResult r) {
        try {
            String e = new JSONObject(r.body).optString("error", "");
            if ("identity_claimed".equals(e)) return "هذه الهوية مرتبطة بجهاز آخر.";
            if ("user_not_found".equals(e)) return "المستخدم غير موجود.";
            if ("invalid_identity".equals(e)) return "بيانات الهوية غير صحيحة.";
            if ("unauthorized".equals(e) || "invalid_token".equals(e)) return "انتهت الجلسة. سجّل الدخول من جديد.";
            if ("rate_limited".equals(e)) return "طلبات كثيرة. حاول لاحقًا.";
            return e.length() > 0 ? e : "حدث خطأ في الخادم (" + r.code + ").";
        } catch (Exception e) { return "حدث خطأ في الخادم (" + r.code + ")."; }
    }

    private boolean isOnline() {
        try {
            ConnectivityManager cm = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
            NetworkInfo n = cm == null ? null : cm.getActiveNetworkInfo();
            return n != null && n.isConnected();
        } catch (Exception e) { return false; }
    }

    private TextView tv(String s, float size, int color, int style) {
        TextView t = new TextView(this);
        t.setText(s);
        t.setTextSize(size);
        t.setTextColor(color);
        t.setTypeface(Typeface.DEFAULT, style);
        t.setTextDirection(View.TEXT_DIRECTION_ANY_RTL);
        return t;
    }

    private EditText field(String hint) {
        EditText e = new EditText(this);
        e.setHint(hint);
        e.setHintTextColor(Color.rgb(130, 145, 160));
        e.setTextColor(text);
        e.setTextSize(16);
        e.setSingleLine(false);
        e.setPadding(18, 14, 18, 14);
        e.setBackgroundColor(panel);
        e.setTextDirection(View.TEXT_DIRECTION_ANY_RTL);
        return e;
    }

    private Button button(String label) {
        Button b = new Button(this);
        b.setText(label);
        b.setTextColor(text);
        b.setTextSize(15);
        b.setAllCaps(false);
        b.setPadding(18, 12, 18, 12);
        b.setBackgroundColor(accent);
        return b;
    }

    private LinearLayout card() {
        LinearLayout c = new LinearLayout(this);
        c.setOrientation(LinearLayout.VERTICAL);
        c.setPadding(18, 16, 18, 16);
        c.setBackgroundColor(panel);
        return c;
    }

    private LinearLayout.LayoutParams lp(int l, int t, int r, int b) {
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(-1, -2);
        p.setMargins(l, t, r, b);
        return p;
    }

    private void setBusy(boolean busy) {
        // UI remains responsive because all network work runs on the IO executor.
    }

    private void setStatus(String s, boolean error) {
        if (status != null) {
            status.setText(s);
            status.setTextColor(error ? Color.rgb(255, 125, 125) : muted);
        }
    }

    private static final class HttpResult {
        final int code;
        final String body;
        HttpResult(int code, String body) { this.code = code; this.body = body; }
    }
}
