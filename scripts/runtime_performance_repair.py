from pathlib import Path

MAIN = Path('app/src/main/java/com/wethaq/app/MainActivity.java')
VIDEO = Path('app/src/main/java/com/wethaq/app/VideoCallActivity.java')
UI = Path('app/src/main/java/com/wethaq/app/WethaqUi.java')
APP = Path('app/src/main/java/com/wethaq/app/WethaqApp.java')
SERVER = Path('backend/server.js')


def replace_method(src, signature, replacement):
    start = src.find(signature)
    if start < 0:
        raise SystemExit(f'missing method: {signature}')
    brace = src.find('{', start)
    if brace < 0:
        raise SystemExit(f'missing body: {signature}')
    depth = 0
    end = -1
    for i in range(brace, len(src)):
        if src[i] == '{':
            depth += 1
        elif src[i] == '}':
            depth -= 1
            if depth == 0:
                end = i + 1
                break
    if end < 0:
        raise SystemExit(f'unbalanced method: {signature}')
    return src[:start] + replacement + src[end:]

# ---- Authentication / personal-code flow ----
s = MAIN.read_text(encoding='utf-8')
s = s.replace(
    'l.setOnClickListener(v->{click();savePersonalCode(pc.getText().toString());auth(n.getText().toString().trim(),y.getText().toString().trim(),false);});',
    'l.setOnClickListener(v->{click();auth(n.getText().toString().trim(),y.getText().toString().trim(),false,pc.getText().toString().trim());});'
)
s = s.replace(
    'savePersonalCode(code);auth(n.getText().toString().trim(),y.getText().toString().trim(),true);',
    'auth(n.getText().toString().trim(),y.getText().toString().trim(),true,code);'
)
old_auth = 'private void auth(String n,String y,boolean create)'
new_auth = '''private void auth(String n,String y,boolean create,String personalCode){if(n.split("\\\\s+").length<3||!y.matches("\\\\d{4}")){toast("أدخل الاسم الثلاثي وسنة الميلاد");return;}showProgress(create?"جاري إنشاء الهوية…":"جاري تسجيل الدخول…");io.execute(()->{try{JSONObject q=new JSONObject();q.put("name",n);q.put("birthYear",Integer.parseInt(y));q.put("deviceKey",deviceKey());String code=personalCode==null?"":personalCode.trim();if(!code.isEmpty())q.put("personalCodeHash",hashPersonalCode(code));HttpResult r=request("POST",create?"/api/identity":"/api/login",q.toString(),null);if(r.code>=200&&r.code<300){JSONObject o=new JSONObject(r.body),u=o.getJSONObject("user");String role=o.optString("role","");boolean codeConfigured=o.optBoolean("personal_code_configured",false);prefs.edit().putString(T,o.getString("token")).putString(ID,u.optString("wethaq_id")).putString("db_user_id",u.optString("id")).putString(NAME,u.optString("name",n)).putString(YEAR,y).putString("admin_role",role).apply();if(create&& !code.isEmpty())savePersonalCode(code);else if(!create&&isAdminRoleName(role)&&!codeConfigured&&!code.isEmpty())savePersonalCode(code);h.post(()->{dismissProgress();home();});}else{final HttpResult result=r;h.post(()->{dismissProgress();toast(error(result));});}}catch(Exception e){h.post(()->{dismissProgress();toast("تعذر الاتصال بالخادم");});}});}'''
s = replace_method(s, old_auth, new_auth)
MAIN.write_text(s, encoding='utf-8')

# ---- Video signaling: one bounded worker instead of a new thread every 500 ms ----
s = VIDEO.read_text(encoding='utf-8')
s = s.replace('import java.util.*;\n', 'import java.util.*;\nimport java.util.concurrent.*;\n')
s = s.replace('private Runnable poll;\n', 'private Runnable poll;private final ExecutorService callIo=Executors.newSingleThreadExecutor();private volatile boolean pollInFlight;\n')
s = s.replace('poll=new Runnable(){@Override public void run(){pollSignals();if(!cleaned)handler.postDelayed(this,500);}};handler.post(poll);', 'poll=new Runnable(){@Override public void run(){pollSignals();if(!cleaned)handler.postDelayed(this,1000);}};handler.post(poll);')
old_poll = 'private void pollSignals()'
new_poll = '''private void pollSignals(){if(cleaned||pollInFlight)return;pollInFlight=true;callIo.execute(()->{HttpURLConnection c=null;try{c=(HttpURLConnection)new URL(API+"/api/calls/signals/"+URLEncoder.encode(target,"UTF-8")).openConnection();c.setRequestProperty("Authorization","Bearer "+token);c.setConnectTimeout(3500);c.setReadTimeout(5000);if(c.getResponseCode()!=200)return;InputStream in=c.getInputStream();ByteArrayOutputStream out=new ByteArrayOutputStream();byte[] b=new byte[4096];int n;while((n=in.read(b))!=-1)out.write(b,0,n);in.close();JSONArray a=new JSONObject(new String(out.toByteArray(),StandardCharsets.UTF_8)).optJSONArray("signals");if(a==null)return;for(int i=0;i<a.length();i++){JSONObject x=a.optJSONObject(i);if(x==null)continue;String id=x.optString("id","");String key=id.isEmpty()?x.optString("created_at","")+x.optString("type","")+x.optString("payload",""):id;if(!seenSignals.add(key))continue;String sender=x.optString("sender_id","");if(sender.equals(myId)||sender.equals(getSharedPreferences("wethaq",MODE_PRIVATE).getString("db_user_id","")))continue;handle(x.optString("type"),x.optString("payload"));}}catch(Exception ignored){}finally{if(c!=null)c.disconnect();pollInFlight=false;}});}'''
s = replace_method(s, old_poll, new_poll)
s = s.replace('private void sendSignal(String type,String payload){new Thread(()->{', 'private void sendSignal(String type,String payload){if(cleaned&&!("end".equals(type)))return;callIo.execute(()->{')
s = s.replace('}catch(Exception ignored){}});}', '}catch(Exception ignored){}});}')
s = s.replace('capturer.startCapture(640,480,24);', 'capturer.startCapture(640,360,20);')
s = s.replace('if(poll!=null)handler.removeCallbacks(poll);if(target!=null&&!token.isEmpty())sendSignal("end","{}");', 'if(poll!=null)handler.removeCallbacks(poll);if(target!=null&&!token.isEmpty())sendSignal("end","{}");')
s = s.replace('if(egl!=null)egl.release();}catch(Exception ignored){}if(audioManager!=null)', 'if(egl!=null)egl.release();}catch(Exception ignored){}callIo.shutdownNow();if(audioManager!=null)')
VIDEO.write_text(s, encoding='utf-8')

# ---- Shared UI layer: never spawn avatar HTTP requests for every TextView/Button ----
s = UI.read_text(encoding='utf-8')
s = s.replace('String[] p=String.valueOf(b.getText()).split("\\\\n");if(p.length>=2&&p[1].trim().length()>=2){String id=p[1].trim();String token=b.getContext().getSharedPreferences("wethaq",0).getString("token","");setDefaultAvatar(b);loadAvatarDrawable(id,token,d->{b.setCompoundDrawablesWithIntrinsicBounds(null,null,d,null);b.setCompoundDrawablePadding(dp(b,10));});}', '')
s = s.replace('String[] p=s.split("\\\\n");String id="";if(p.length>=2&&p[1].trim().length()>=2)id=p[1].trim();else id=contactIdForName(a,s);if(id.isEmpty())return;t.setTag(Boolean.TRUE);setDefaultAvatar(t);String token=a.getSharedPreferences("wethaq",0).getString("token","");loadAvatarDrawable(id,token,d->{t.setCompoundDrawablesWithIntrinsicBounds(null,null,d,null);t.setCompoundDrawablePadding(dp(t,10));});', 't.setTag(Boolean.TRUE);')
UI.write_text(s, encoding='utf-8')

# ---- Global UI layer: apply once when an Activity is created, not twice on every resume ----
s = APP.read_text(encoding='utf-8')
s = s.replace('@Override public void onActivityResumed(Activity a){refresh(a);}', '@Override public void onActivityResumed(Activity a){}')
s = s.replace('a.getWindow().getDecorView().postDelayed(()->WethaqUi.apply(this,a),120);\n        a.getWindow().getDecorView().postDelayed(()->WethaqUi.apply(this,a),500);', 'a.getWindow().getDecorView().post(()->WethaqUi.apply(this,a));')
APP.write_text(s, encoding='utf-8')

# ---- Backend: return role and enforce personal code only for assigned admins ----
s = SERVER.read_text(encoding='utf-8')
old = 'db.prepare(\'UPDATE users SET last_seen=? WHERE id=?\').run(now(),u.id);u=db.prepare(\'SELECT * FROM users WHERE id=?\').get(u.id);audit(u.id,\'login\',u.id);res.json({user:publicUser(u),token:tokenFor(u)})'
new = 'db.prepare(\'UPDATE users SET last_seen=? WHERE id=?\').run(now(),u.id);u=db.prepare(\'SELECT * FROM users WHERE id=?\').get(u.id);const loginRole=roleOf(u.id);const submittedCodeHash=String(req.body?.personalCodeHash||\'\').trim().toLowerCase();if(loginRole&&u.personal_code_hash){if(!/^[a-f0-9]{64}$/.test(submittedCodeHash)||submittedCodeHash!==u.personal_code_hash)return res.status(403).json({error:\'invalid_personal_code\'});}audit(u.id,\'login\',u.id);res.json({user:publicUser(u),token:tokenFor(u),role:loginRole,personal_code_configured:!!u.personal_code_hash})'
if old not in s:
    raise SystemExit('login response anchor not found')
s = s.replace(old,new,1)
# The role helper is intentionally server-side; add a safe client-independent verifier for diagnostics/explicit flows.
anchor = "app.get('/api/public/administration',(_req,res)=>res.json(adminStructure()));"
insert = "app.post('/api/profile/personal-code/verify',auth,(req,res)=>{const h=String(req.body?.codeHash||'').trim().toLowerCase();if(!/^[a-f0-9]{64}$/.test(h))return res.status(400).json({error:'invalid_personal_code'});const u=db.prepare('SELECT personal_code_hash FROM users WHERE id=?').get(req.user.sub);if(!u?.personal_code_hash||u.personal_code_hash!==h)return res.status(403).json({error:'invalid_personal_code'});res.json({ok:true,role:roleOf(req.user.sub)});});"
if anchor not in s:
    raise SystemExit('public administration anchor not found')
s = s.replace(anchor, insert+anchor, 1)
SERVER.write_text(s, encoding='utf-8')

# ---- small helper used by generated auth method ----
helper = 'private boolean isAdminRoleName(String r){return "founder".equals(r)||"deputy1".equals(r)||"deputy2".equals(r)||"deputy3".equals(r)||"admin_member".equals(r);}\n'
if 'private boolean isAdminRoleName(String r)' not in MAIN.read_text(encoding='utf-8'):
    s = MAIN.read_text(encoding='utf-8')
    pos = s.find('private boolean isAdminRole()')
    if pos < 0: raise SystemExit('admin role helper anchor not found')
    s = s[:pos] + helper + s[pos:]
    MAIN.write_text(s, encoding='utf-8')

print('Runtime performance, calls and admin login repair: OK')
