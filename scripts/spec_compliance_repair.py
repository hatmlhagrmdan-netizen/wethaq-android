from pathlib import Path
import re

APP = Path('app/src/main/java/com/wethaq/app/MainActivity.java')
ADMIN = Path('app/src/main/java/com/wethaq/app/AdminActivity.java')
MANIFEST = Path('app/src/main/AndroidManifest.xml')
SERVER = Path('backend/server.js')

# ---------- MainActivity: personal code + public administration entry ----------
s = APP.read_text(encoding='utf-8')
if 'PERSONAL_CODE=' not in s:
    s = s.replace('API="https://wethaq-backend-production.up.railway.app",P="wethaq",T="token",ID="wethaq_id",NAME="name",YEAR="birth_year",CONTACTS="saved_contacts";',
                  'API="https://wethaq-backend-production.up.railway.app",P="wethaq",T="token",ID="wethaq_id",NAME="name",YEAR="birth_year",CONTACTS="saved_contacts",PERSONAL_CODE="personal_code_hash";')

if 'private String hashPersonalCode' not in s:
    anchor='private void startMessageService()'
    method='''private String hashPersonalCode(String code){try{java.security.MessageDigest md=java.security.MessageDigest.getInstance("SHA-256");byte[] z=md.digest(code.getBytes(StandardCharsets.UTF_8));StringBuilder x=new StringBuilder();for(byte q:z)x.append(String.format("%02x",q));return x.toString();}catch(Exception e){return "";}}\n private void savePersonalCode(String code){String c=code==null?"":code.trim();if(c.length()<4||c.length()>64)return;String h=hashPersonalCode(c);if(h.isEmpty())return;prefs.edit().putString(PERSONAL_CODE,h).apply();io.execute(()->{try{JSONObject q=new JSONObject();q.put("codeHash",h);request("POST","/api/profile/personal-code",q.toString(),auth());}catch(Exception ignored){}});}\n private void publicAdministrationScreen(){startActivity(new Intent(this,PublicAdministrationActivity.class));}\n '''
    if anchor not in s: raise SystemExit('MainActivity anchor missing')
    s=s.replace(anchor,method+anchor,1)

# Replace login with a compatible version that keeps current auth unchanged.
pat=re.compile(r'private void login\(\)\{.*?\n private void auth\(',re.S)
m=pat.search(s)
if not m: raise SystemExit('MainActivity login block missing')
login='''private void login(){base("وَثاق");TextView x=tv("مدير ومؤسس وثاق\\nحاتم حسين الحاج رمضان",25,Color.WHITE);x.setGravity(Gravity.CENTER);x.setTypeface(Typeface.DEFAULT,Typeface.BOLD);content.addView(x,lp(-1,-2,18));EditText n=field("الاسم الثلاثي"),y=field("سنة الميلاد"),pc=field("🔐 الرمز الشخصي");y.setInputType(InputType.TYPE_CLASS_NUMBER);pc.setInputType(InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_VARIATION_PASSWORD);content.addView(n,lp(-1,64,10));content.addView(y,lp(-1,64,10));content.addView(pc,lp(-1,64,14));Button l=btn("تسجيل الدخول"),c=btn("إنشاء هوية جديدة");content.addView(l,lp(-1,76,10));content.addView(c,lp(-1,76,14));content.addView(tv("الرمز الشخصي سري ولا يظهر للآخرين.",14,gold()));l.setOnClickListener(v->{click();savePersonalCode(pc.getText().toString());auth(n.getText().toString().trim(),y.getText().toString().trim(),false);});c.setOnClickListener(v->{click();String code=pc.getText().toString().trim();if(code.length()<4){toast("أدخل رمزاً شخصياً لا يقل عن 4 محارف");return;}savePersonalCode(code);auth(n.getText().toString().trim(),y.getText().toString().trim(),true);});}\n private void auth('''
s=s[:m.start()]+login+s[m.end():]

old='if(isOwner()||isAdminRole())menu("👥  الإدارة",this::adminScreen);'
new='menu("👥  الإدارة",this::publicAdministrationScreen);if(isOwner()||isAdminRole())menu("🛡  لوحة التحكم الإدارية",this::adminScreen);'
if old in s:s=s.replace(old,new,1)
else: print('WARNING: home admin anchor not found')

if 'الرمز الشخصي محفوظ' not in s:
    s=s.replace('content.addView(tv("المعرف الشخصي تلقائيًا",14,gold()));', 'content.addView(tv("الرمز الشخصي: "+(prefs.getString(PERSONAL_CODE,"").isEmpty()?"غير مضبوط":"سري ومحمي"),14,gold()));content.addView(tv("المعرف الشخصي تلقائيًا",14,gold()));',1)

APP.write_text(s,encoding='utf-8')

# ---------- Manifest ----------
m=MANIFEST.read_text(encoding='utf-8')
if '.PublicAdministrationActivity' not in m:
    m=m.replace('<activity android:name=".AdminActivity" android:exported="false" android:screenOrientation="portrait" />', '<activity android:name=".AdminActivity" android:exported="false" android:screenOrientation="portrait" />\n        <activity android:name=".PublicAdministrationActivity" android:exported="false" android:screenOrientation="portrait" />',1)
MANIFEST.write_text(m,encoding='utf-8')

# ---------- AdminActivity: exact 10 slots, audit log, connection state, role-aware UI ----------
a=ADMIN.read_text(encoding='utf-8')
# Add status field.
a=a.replace('private LinearLayout root,body;private EditText target,minutes,reason;private String token,role="";private boolean verified;', 'private LinearLayout root,body;private EditText target,minutes,reason;private String token,role="";private boolean verified;private TextView connectionStatus;',1)
# Add status to render shell after title.
a=a.replace('root.addView(title,new LinearLayout.LayoutParams(-1,dp(70)));ScrollView scroll=', 'root.addView(title,new LinearLayout.LayoutParams(-1,dp(70)));connectionStatus=heading("🟢 جاهز");connectionStatus.setGravity(Gravity.CENTER);root.addView(connectionStatus,new LinearLayout.LayoutParams(-1,dp(44)));ScrollView scroll=',1)
# Founder audit button.
a=a.replace('Button assignBtn=b("تعيين وإصدار رمز إداري"),remove=b("إزالة التعيين");', 'Button assignBtn=b("تعيين وإصدار رمز إداري"),remove=b("إزالة التعيين"),auditBtn=b("📋 سجل الإدارة");',1)
a=a.replace('body.addView(remove,lp(-1,68,10));assignBtn.setOnClickListener', 'body.addView(remove,lp(-1,68,10));body.addView(auditBtn,lp(-1,68,8));auditBtn.setOnClickListener(v->auditLog());assignBtn.setOnClickListener',1)
# Replace loadStructure method.
pat=re.compile(r'private void loadStructure\(\)\{.*?\n private String nameOf',re.S)
match=pat.search(a)
if not match: raise SystemExit('AdminActivity loadStructure block missing')
load='''private void loadStructure(){req("GET","/api/admin/structure",null,r->{try{JSONObject o=new JSONObject(r);JSONObject f=o.optJSONObject("founder");StringBuilder s=new StringBuilder();s.append("👑 ").append(f==null?"غير محدد":f.optString("name","غير محدد")).append(" — مؤسس وقائد العمل\\n\\n");JSONObject d=o.optJSONObject("deputies");s.append("🥇 النائب الأول: ").append(nameOf(d==null?null:d.optJSONObject("deputy1"))).append("\\n");s.append("🥈 النائب الثاني: ").append(nameOf(d==null?null:d.optJSONObject("deputy2"))).append("\\n");s.append("🥉 النائب الثالث: ").append(nameOf(d==null?null:d.optJSONObject("deputy3"))).append("\\n\\n");s.append("👥 أعضاء الإدارة — 10 خانات\\n");JSONArray a=o.optJSONArray("members");for(int i=0;i<10;i++){JSONObject x=(a!=null&&i<a.length())?a.optJSONObject(i):null;s.append("\\n").append(i+1).append(". ").append(x==null?"غير معين":x.optString("name","غير معروف")).append(" | الميلاد: ").append(x==null?"—":x.optString("birth_year","—")).append(" | الرمز الشخصي: سري");}TextView view=heading(s.toString());body.addView(view,1,lp(-1,-2,12));}catch(Exception e){toast("تعذر قراءة الهيكل الإداري");}});}\n private String nameOf'''
a=a[:match.start()]+load+a[match.end():]
# Add auditLog method before complaints.
if 'private void auditLog()' not in a:
    a=a.replace('private void complaints(){', '''private void auditLog(){req("GET","/api/admin/audit-log",null,r->{try{JSONArray rows=new JSONObject(r).optJSONArray("logs");StringBuilder s=new StringBuilder();if(rows!=null)for(int i=0;i<rows.length();i++){JSONObject x=rows.optJSONObject(i);s.append(x.optString("created_at")).append("\\n").append(x.optString("actor_name","غير معروف")).append(" → ").append(x.optString("action")).append(" → ").append(x.optString("target_name","غير معروف")).append("\\n").append(x.optString("metadata","")).append("\\n\\n");}new AlertDialog.Builder(this).setTitle("📋 سجل الإدارة").setMessage(s.length()==0?"لا توجد عمليات مسجلة":s.toString()).setPositiveButton("إغلاق",null).show();}catch(Exception e){toast("فشل قراءة سجل الإدارة");}});}\n private void complaints(){''',1)
# Replace req method to include connection state; preserve busy behavior.
pat=re.compile(r'private interface CB\{void ok\(String s\);\}private void req\(.*?\n private String error',re.S)
match=pat.search(a)
if not match: raise SystemExit('AdminActivity req block missing')
req='''private interface CB{void ok(String s);}private void req(String m,String p,String body,CB cb){runOnUiThread(()->{setBusy(true);if(connectionStatus!=null)connectionStatus.setText("⏳ جاري الاتصال بالخادم…");});new Thread(()->{try{HttpURLConnection c=(HttpURLConnection)new URL(API+p).openConnection();c.setRequestMethod(m);c.setConnectTimeout(10000);c.setReadTimeout(15000);c.setRequestProperty("Authorization","Bearer "+token);c.setRequestProperty("Accept","application/json");if(body!=null){c.setDoOutput(true);c.setRequestProperty("Content-Type","application/json");try(OutputStream o=c.getOutputStream()){o.write(body.getBytes(StandardCharsets.UTF_8));}}int code=c.getResponseCode();InputStream in=code<400?c.getInputStream():c.getErrorStream();ByteArrayOutputStream o=new ByteArrayOutputStream();if(in!=null){byte[] z=new byte[4096];int n;while((n=in.read(z))!=-1)o.write(z,0,n);in.close();}String s=new String(o.toByteArray(),StandardCharsets.UTF_8);runOnUiThread(()->{setBusy(false);if(code>=200&&code<300){if(connectionStatus!=null)connectionStatus.setText("🟢 تم تأكيد العملية من الخادم");cb.ok(s);}else{if(connectionStatus!=null)connectionStatus.setText("🔴 رفض الخادم العملية");toast(error(code,s));}});}catch(Exception e){runOnUiThread(()->{setBusy(false);if(connectionStatus!=null)connectionStatus.setText("🔴 تعذر الاتصال بالخادم — يمكنك إعادة المحاولة");toast("تعذر الاتصال بالخادم");});}}).start();}\n private String error'''
a=a[:match.start()]+req+a[match.end():]
ADMIN.write_text(a,encoding='utf-8')

# ---------- Backend hardening + public hierarchy + private personal-code hash + uniqueness limits ----------
server=SERVER.read_text(encoding='utf-8')
if "'personal_code_hash'" not in server:
    server=server.replace("ensureColumn('users','avatar_mime','TEXT');", "ensureColumn('users','avatar_mime','TEXT');ensureColumn('users','personal_code_hash','TEXT');",1)
marker='// WETHAQ_SPEC_COMPLIANCE_V2'
if marker not in server:
    extra='''\n// WETHAQ_SPEC_COMPLIANCE_V2\napp.get('/api/public/administration',(_req,res)=>res.json(adminStructure()));\napp.get('/api/admin/audit-log',founderOnly,(req,res)=>{const rows=db.prepare(`SELECT a.id,a.action,a.metadata,a.created_at,u.name AS actor_name,t.name AS target_name FROM audit_logs a LEFT JOIN users u ON u.id=a.actor_user_id LEFT JOIN users t ON t.id=a.target_user_id ORDER BY a.id DESC LIMIT 200`).all();res.json({logs:rows});});\napp.post('/api/profile/personal-code',auth,(req,res)=>{const h=String(req.body?.codeHash||'').trim().toLowerCase();if(!/^[a-f0-9]{64}$/.test(h))return res.status(400).json({error:'invalid_personal_code'});db.prepare('UPDATE users SET personal_code_hash=? WHERE id=?').run(h,req.user.sub);audit(req.user.sub,'personal_code_updated',req.user.sub);res.json({ok:true});});\napp.get('/api/profile/personal-code/status',auth,(req,res)=>{const u=db.prepare('SELECT personal_code_hash FROM users WHERE id=?').get(req.user.sub);res.json({configured:!!u?.personal_code_hash});});\ntry{db.exec(`CREATE UNIQUE INDEX IF NOT EXISTS idx_admin_one_deputy1 ON admin_roles(role) WHERE role='deputy1';CREATE UNIQUE INDEX IF NOT EXISTS idx_admin_one_deputy2 ON admin_roles(role) WHERE role='deputy2';CREATE UNIQUE INDEX IF NOT EXISTS idx_admin_one_deputy3 ON admin_roles(role) WHERE role='deputy3';CREATE TRIGGER IF NOT EXISTS trg_admin_member_limit_insert BEFORE INSERT ON admin_roles WHEN NEW.role='admin_member' AND (SELECT COUNT(*) FROM admin_roles WHERE role='admin_member')>=10 BEGIN SELECT RAISE(ABORT,'admin_member_limit'); END;CREATE TRIGGER IF NOT EXISTS trg_admin_member_limit_update BEFORE UPDATE OF role ON admin_roles WHEN NEW.role='admin_member' AND OLD.role<>'admin_member' AND (SELECT COUNT(*) FROM admin_roles WHERE role='admin_member')>=10 BEGIN SELECT RAISE(ABORT,'admin_member_limit'); END;`);}catch{}\n'''
    # Insert immediately before server.listen, which is the stable end-of-file anchor.
    idx=server.rfind('server.listen(')
    if idx<0: raise SystemExit('server.listen anchor missing')
    server=server[:idx]+extra+server[idx:]
SERVER.write_text(server,encoding='utf-8')

print('WETHAQ_SPEC_COMPLIANCE_REPAIR_OK')
