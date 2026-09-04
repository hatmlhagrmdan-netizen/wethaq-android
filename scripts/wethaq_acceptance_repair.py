from pathlib import Path

MAIN=Path('app/src/main/java/com/wethaq/app/MainActivity.java')
PUBLIC=Path('app/src/main/java/com/wethaq/app/PublicAdministrationActivity.java')
SERVER=Path('backend/server.js')
SMOKE=Path('backend/smoke-test.mjs')

m=MAIN.read_text(encoding='utf-8')
if 'الإدارة والمناصب الإدارية' not in m:
    old='Button out=btn("تسجيل الخروج");content.addView(out,lp(-1,76,8));'
    new=('LinearLayout paid=infoCard("💼 الإدارة والمناصب الإدارية");'
         'paid.addView(tv("المناصب الإدارية في وَثاق مدفوعة لمدة سنة واحدة. للحصول على منصب إداري، يرجى التواصل مباشرةً مع المدير والمؤسس.",15,Color.WHITE),lp(-1,-2,0));'
         'content.addView(paid,lp(-1,-2,10));'
         +old)
    if old not in m: raise SystemExit('main insertion point missing')
    m=m.replace(old,new,1)
    anchor='private void menu(String s,Runnable r){'
    helper=('private LinearLayout infoCard(String title){LinearLayout c=new LinearLayout(this);c.setOrientation(LinearLayout.VERTICAL);c.setPadding(dp(14),dp(12),dp(14),dp(12));GradientDrawable d=new GradientDrawable();d.setColor(Color.rgb(20,20,20));d.setCornerRadius(dp(16));d.setStroke(dp(2),gold());c.setBackground(d);TextView h=tv(title,18,gold());h.setGravity(Gravity.RIGHT);h.setTypeface(Typeface.DEFAULT,Typeface.BOLD);c.addView(h,lp(-1,-2,4));return c;}\n ')
    if anchor not in m: raise SystemExit('main helper point missing')
    m=m.replace(anchor,helper+anchor,1)
    MAIN.write_text(m,encoding='utf-8')

p=PUBLIC.read_text(encoding='utf-8')
start=p.index('    private void show(String raw){')
end=p.index('    private String nameOf',start)
show='''    private void show(String raw){try{JSONObject o=new JSONObject(raw);body.removeAllViews();JSONObject f=o.optJSONObject("founder");body.addView(card("👑 المؤسس\\n"+(f==null?"غير محدد":f.optString("name","غير محدد")),"القيادة الكاملة للتطبيق واعتماد القرارات والهيكل الإداري والصلاحيات العليا.","مجاني — منصب المؤسس لا يُباع",true));JSONObject d=o.optJSONObject("deputies");body.addView(card("🥇 النائب الأول\\n"+nameOf(d==null?null:d.optJSONObject("deputy1")),"إشراف إداري موسع، متابعة أعضاء الإدارة، المخالفات والحظر ضمن الصلاحية، التنبيهات والشكاوى ورفع التقارير للمؤسس.","20$ سنويًا",false));body.addView(card("🥈 النائب الثاني\\n"+nameOf(d==null?null:d.optJSONObject("deputy2")),"إشراف متوسط، متابعة المستخدمين وأعضاء الإدارة، التنبيهات والمخالفات ضمن الصلاحية ورفع الحالات الأعلى.","17$ سنويًا",false));body.addView(card("🥉 النائب الثالث\\n"+nameOf(d==null?null:d.optJSONObject("deputy3")),"متابعة المستخدمين، استقبال البلاغات، التنبيهات وإجراءات الإشراف الأساسية، دون تعيين مناصب.","12$ سنويًا",false));body.addView(text("👥 أعضاء الإدارة — 10 خانات",20,Color.rgb(212,175,55)));JSONArray a=o.optJSONArray("members");for(int i=0;i<10;i++){JSONObject x=(a!=null&&i<a.length())?a.optJSONObject(i):null;body.addView(card((i+1)+". "+(x==null?"غير معين":x.optString("name","غير معروف")),"المتابعة الإدارية، الاطلاع على المعلومات الإدارية العامة، استقبال التوجيهات ورفع البلاغات والتنبيهات. لا يملك صلاحية التعيين أو الحظر.","8$ سنويًا",false));}body.addView(card("المستخدمون\\nجميع المستخدمين خارج الهيكل الإداري","استخدام خدمات وَثاق الأساسية دون صلاحيات إدارية.","مجاني",false));}catch(Exception e){status.setText("🔴 حدث خطأ أثناء قراءة البيانات");}}\n'''
p=p[:start]+show+p[end:]
card_start=p.index('    private TextView card(')
card='''    private TextView card(String title,String permissions,String price,boolean founder){TextView t=text(title+"\\n\\nالصلاحيات الممنوحة:\\n"+permissions+"\\n\\nالرسوم: "+price,founder?20:17,Color.WHITE);GradientDrawable d=new GradientDrawable();d.setColor(founder?Color.rgb(45,36,12):Color.rgb(24,24,28));d.setCornerRadius(dp(16));d.setStroke(dp(2),founder?Color.rgb(212,175,55):Color.rgb(70,70,75));t.setBackground(d);t.setPadding(dp(16),dp(14),dp(16),dp(14));LinearLayout.LayoutParams q=new LinearLayout.LayoutParams(-1,-2);q.bottomMargin=dp(10);t.setLayoutParams(q);return t;}\n'''
p=p[:card_start]+card
PUBLIC.write_text(p,encoding='utf-8')

s=SERVER.read_text(encoding='utf-8')
anchor="db.exec(`CREATE TABLE IF NOT EXISTS admin_roles(id INTEGER PRIMARY KEY AUTOINCREMENT,user_id INTEGER NOT NULL UNIQUE,role TEXT NOT NULL,admin_code_hash TEXT,created_by INTEGER,created_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP,FOREIGN KEY(user_id) REFERENCES users(id) ON DELETE CASCADE);CREATE INDEX IF NOT EXISTS idx_admin_roles_role ON admin_roles(role);`);"
if anchor not in s: raise SystemExit('admin roles schema point missing')
s=s.replace(anchor,anchor+"ensureColumn('admin_roles','expires_at','TEXT');",1)
old_role="function roleOf(userId){if(!userId)return null;const owner=db.prepare('SELECT id FROM users WHERE wethaq_id=?').get(OWNER_WETHAQ_ID);if(owner&&Number(userId)===Number(owner.id))return ROLE_FOUNDER;return db.prepare('SELECT role FROM admin_roles WHERE user_id=?').get(Number(userId))?.role||null}"
new_role="function roleOf(userId){if(!userId)return null;const owner=db.prepare('SELECT id FROM users WHERE wethaq_id=?').get(OWNER_WETHAQ_ID);if(owner&&Number(userId)===Number(owner.id))return ROLE_FOUNDER;const row=db.prepare('SELECT role,expires_at FROM admin_roles WHERE user_id=?').get(Number(userId));if(!row)return null;if(row.expires_at&&new Date(row.expires_at)<=new Date()){db.prepare('DELETE FROM admin_roles WHERE user_id=?').run(Number(userId));return null}return row.role||null}"
if old_role not in s: raise SystemExit('roleOf pattern missing')
s=s.replace(old_role,new_role,1)
old_struct="const rows=db.prepare('SELECT ar.role,u.id,u.wethaq_id,u.name,u.birth_year FROM admin_roles ar JOIN users u ON u.id=ar.user_id ORDER BY ar.id').all();"
new_struct="const rows=db.prepare('SELECT ar.role,ar.expires_at,u.id,u.wethaq_id,u.name,u.birth_year FROM admin_roles ar JOIN users u ON u.id=ar.user_id ORDER BY ar.id').all().filter(x=>!x.expires_at||new Date(x.expires_at)>new Date());"
if old_struct not in s: raise SystemExit('structure query pattern missing')
s=s.replace(old_struct,new_struct,1)
old_assign="const code=newAdminCode();db.prepare('INSERT INTO admin_roles(user_id,role,admin_code_hash,created_by) VALUES(?,?,?,?)').run(target.id,role,hashCode(code),req.user.sub);"
new_assign="const code=newAdminCode(),expiresAt=new Date(Date.now()+365*24*60*60*1000).toISOString();db.prepare('INSERT INTO admin_roles(user_id,role,admin_code_hash,created_by,expires_at) VALUES(?,?,?,?,?)').run(target.id,role,hashCode(code),req.user.sub,expiresAt);"
if old_assign not in s: raise SystemExit('assign insert pattern missing')
s=s.replace(old_assign,new_assign,1)
old_response="res.status(201).json({ok:true,user:target,role,role_label:roleLabel(role),admin_code:code})"
new_response="res.status(201).json({ok:true,user:target,role,role_label:roleLabel(role),admin_code:code,expires_at:expiresAt,period:'سنة واحدة',price_usd:role==='deputy1'?20:role==='deputy2'?17:role==='deputy3'?12:8})"
if old_response not in s: raise SystemExit('assign response pattern missing')
s=s.replace(old_response,new_response,1)
old_verify="const code=String(req.body?.code||'').trim().toUpperCase(),row=db.prepare('SELECT role,admin_code_hash FROM admin_roles WHERE user_id=?').get(req.user.sub);if(!row||!code||hashCode(code)!==row.admin_code_hash)return res.status(403).json({error:'invalid_admin_code'});res.json({ok:true,role:row.role,role_label:roleLabel(row.role)})"
new_verify="const code=String(req.body?.code||'').trim().toUpperCase(),row=db.prepare('SELECT role,admin_code_hash,expires_at FROM admin_roles WHERE user_id=?').get(req.user.sub);if(!row||!roleOf(req.user.sub)||!code||hashCode(code)!==row.admin_code_hash)return res.status(403).json({error:'invalid_admin_code'});res.json({ok:true,role:row.role,role_label:roleLabel(row.role),expires_at:row.expires_at||null})"
if old_verify not in s: raise SystemExit('verify pattern missing')
s=s.replace(old_verify,new_verify,1)
SERVER.write_text(s,encoding='utf-8')

SMOKE.write_text(r'''const base = `http://127.0.0.1:${process.env.PORT || 3100}`;
const assert=(x,m)=>{if(!x)throw new Error(m)};
const request=async(path,opt={})=>{const r=await fetch(base+path,{...opt,headers:{'content-type':'application/json',...(opt.headers||{})}});const text=await r.text();let body={};try{body=JSON.parse(text)}catch{}return{r,body,text}};
const h=await request('/health');assert(h.r.ok&&h.body.ok,'health');
const suffix=Date.now();const ownerName=`حاتم حسين الحاج رمضان`,ownerDevice=`owner-${suffix}`;
const owner=await request('/api/identity',{method:'POST',body:JSON.stringify({name:ownerName,birthYear:1995,deviceKey:ownerDevice})});assert(owner.r.status===201,'owner create');
const aName=`اختبار وثاق ${suffix}`,aCode=`code-${suffix}`;const a=await request('/api/identity',{method:'POST',body:JSON.stringify({name:aName,birthYear:1995,deviceKey:`a-${suffix}`,personalCodeHash:''})});assert(a.r.status===201,'a create');
const aLogin=await request('/api/login',{method:'POST',body:JSON.stringify({name:aName,birthYear:1995,deviceKey:`a-${suffix}`})});assert(aLogin.r.status===401||aLogin.r.status===409,'missing code protected');
const bName=`مستخدم وثاق ${suffix}`;const b=await request('/api/identity',{method:'POST',body:JSON.stringify({name:bName,birthYear:1996,deviceKey:`b-${suffix}`})});assert(b.r.status===201,'b create');
const send=await request('/api/messages',{method:'POST',headers:{authorization:`Bearer ${a.body.token}`},body:JSON.stringify({to:b.body.user.wethaq_id,body:'اختبار رسالة كامل'})});assert(send.r.status===201,'message send');
const hist=await request(`/api/messages/${encodeURIComponent(b.body.user.wethaq_id)}`,{headers:{authorization:`Bearer ${a.body.token}`}});assert(hist.r.ok&&hist.body.messages.some(x=>x.id===send.body.message.id),'message history');
const struct0=await request('/api/public/administration');assert(struct0.r.ok,'public administration');
const assigned=await request('/api/admin/assign',{method:'POST',headers:{authorization:`Bearer ${owner.body.token}`},body:JSON.stringify({wethaqId:b.body.user.wethaq_id,role:'deputy1'})});assert(assigned.r.status===201&&assigned.body.admin_code&&assigned.body.expires_at,'admin assign');
const secondDevice=await request('/api/login',{method:'POST',body:JSON.stringify({name:bName,birthYear:1996,deviceKey:`second-${suffix}`,personalCodeHash:assigned.body.admin_code})});assert(secondDevice.r.ok&&secondDevice.body.role==='deputy1','admin login other device');
const role=await request('/api/admin/role',{headers:{authorization:`Bearer ${secondDevice.body.token}`}});assert(role.r.ok&&role.body.role==='deputy1','admin role');
const verify=await request('/api/admin/verify-code',{method:'POST',headers:{authorization:`Bearer ${secondDevice.body.token}`},body:JSON.stringify({code:assigned.body.admin_code})});assert(verify.r.ok,'admin code verify');
const db=(await import('better-sqlite3')).default;const sqlite=new db(process.env.DB_PATH);sqlite.prepare('UPDATE admin_roles SET expires_at=? WHERE user_id=?').run(new Date(Date.now()-1000).toISOString(),b.body.user.id);sqlite.close();
const expired=await request('/api/admin/role',{headers:{authorization:`Bearer ${secondDevice.body.token}`}});assert(expired.r.status===403,'expired admin role must be rejected');
const wrong=await request('/api/login',{method:'POST',body:JSON.stringify({name:bName,birthYear:1996,deviceKey:`third-${suffix}`,personalCodeHash:'0'.repeat(64)})});assert(wrong.r.status===401,'wrong admin code accepted');
console.log('WETHAQ_FULL_ACCEPTANCE_OK');
''','utf-8')
print('WETHAQ_ACCEPTANCE_REPAIR_OK')
