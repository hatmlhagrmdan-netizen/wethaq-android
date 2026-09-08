from pathlib import Path
import re

BACKEND = Path('backend/server.js')
MAIN = Path('app/src/main/java/com/wethaq/app/MainActivity.java')

s = BACKEND.read_text(encoding='utf-8')

# Persist only a salted scrypt hash of the personal identity code.
if "ensureColumn('users','personal_code_hash','TEXT')" not in s:
    anchor = "ensureColumn('users','avatar_mime','TEXT');"
    if anchor not in s:
        raise SystemExit('BACKEND_SCHEMA_ANCHOR_NOT_FOUND')
    s = s.replace(anchor, anchor + "ensureColumn('users','personal_code_hash','TEXT');", 1)

# Add secure personal-code helpers immediately before identityInput.
marker = 'function identityInput(req,res){'
if 'function hashPersonalCode(' not in s:
    helper = "function validPersonalCode(v){return /^\\d{6,12}$/.test(String(v||'').trim())}function hashPersonalCode(code){const salt=crypto.randomBytes(16),hash=crypto.scryptSync(String(code).trim(),salt,32);return `${salt.toString('hex')}:${hash.toString('hex')}`}function verifyPersonalCode(code,stored){try{const[a,b]=String(stored||'').split(':');if(!a||!b)return false;const salt=Buffer.from(a,'hex'),expected=Buffer.from(b,'hex'),actual=crypto.scryptSync(String(code||'').trim(),salt,32);return expected.length===actual.length&&crypto.timingSafeEqual(expected,actual)}catch{return false}}"
    if marker not in s:
        raise SystemExit('IDENTITY_INPUT_ANCHOR_NOT_FOUND')
    s = s.replace(marker, helper + marker, 1)

# Require personalCode on both creation and login requests.
s = s.replace("const name=safeName(req.body?.name),birthYear=Number(req.body?.birthYear),deviceKey=String(req.body?.deviceKey||'').trim(),currentYear=new Date().getUTCFullYear();", "const name=safeName(req.body?.name),birthYear=Number(req.body?.birthYear),deviceKey=String(req.body?.deviceKey||'').trim(),personalCode=String(req.body?.personalCode||'').trim(),currentYear=new Date().getUTCFullYear();", 1)
s = s.replace("if(!validText(name,MAX_NAME)||name.split(/\\s+/).length<3||!Number.isInteger(birthYear)||birthYear<1900||birthYear>currentYear||deviceKey.length>MAX_DEVICE_KEY){", "if(!validText(name,MAX_NAME)||name.split(/\\s+/).length<3||!Number.isInteger(birthYear)||birthYear<1900||birthYear>currentYear||deviceKey.length>MAX_DEVICE_KEY||!validPersonalCode(personalCode)){", 1)
s = s.replace("return{name,birthYear,deviceKey:deviceKey||crypto.randomBytes(24).toString('hex')}}", "return{name,birthYear,deviceKey:deviceKey||crypto.randomBytes(24).toString('hex'),personalCode}}", 1)

# Replace identity creation route while preserving the unique Wethaq ID invariant.
identity_pat = re.compile(r"app\.post\('/api/identity'.*?\}\);\napp\.post\('/api/login'", re.S)
identity_repl = "app.post('/api/identity',(req,res)=>{const x=identityInput(req,res);if(!x)return;const{name,birthYear,deviceKey,personalCode}=x,base=makeBaseId(name,birthYear);let u=db.prepare('SELECT * FROM users WHERE wethaq_id=?').get(base);if(u){if(u.device_key&&u.device_key!==deviceKey)return res.status(409).json({error:'identity_claimed'});if(u.personal_code_hash&&!verifyPersonalCode(personalCode,u.personal_code_hash))return res.status(401).json({error:'invalid_personal_code'});db.prepare('UPDATE users SET device_key=?,name=?,birth_year=?,personal_code_hash=?,last_seen=? WHERE id=?').run(deviceKey,name,birthYear,hashPersonalCode(personalCode),now(),u.id)}else{const id=makeWethaqId(name,birthYear),z=db.prepare('INSERT INTO users(wethaq_id,name,birth_year,personal_code_hash,device_key,last_seen) VALUES(?,?,?,?,?,?)').run(id,name,birthYear,hashPersonalCode(personalCode),deviceKey,now());u=db.prepare('SELECT * FROM users WHERE id=?').get(z.lastInsertRowid)}u=db.prepare('SELECT * FROM users WHERE id=?').get(u.id);res.status(201).json({user:publicUser(u),token:tokenFor(u)})});\napp.post('/api/login'"
if not identity_pat.search(s):
    raise SystemExit('IDENTITY_ROUTE_NOT_FOUND')
s = identity_pat.sub(identity_repl, s, count=1)

# Name + birth year + personal code is the durable login factor; a successful code login migrates the device binding.
login_pat = re.compile(r"app\.post\('/api/login'.*?\}\);app\.get\('/api/me'", re.S)
login_repl = "app.post('/api/login',(req,res)=>{const x=identityInput(req,res);if(!x)return;let u=db.prepare('SELECT * FROM users WHERE name=? AND birth_year=?').get(x.name,x.birthYear);if(!u)return res.status(404).json({error:'user_not_found'});if(!u.personal_code_hash){db.prepare('UPDATE users SET personal_code_hash=? WHERE id=?').run(hashPersonalCode(x.personalCode),u.id)}else if(!verifyPersonalCode(x.personalCode,u.personal_code_hash))return res.status(401).json({error:'invalid_personal_code'});const ban=activeBan(u.id);if(ban)return res.status(403).json({error:'user_banned',ban_type:ban.ban_type,expires_at:ban.expires_at||null,reason:ban.reason||''});db.prepare('UPDATE users SET device_key=?,last_seen=? WHERE id=?').run(x.deviceKey,now(),u.id);u=db.prepare('SELECT * FROM users WHERE id=?').get(u.id);res.json({user:publicUser(u),token:tokenFor(u)})});app.get('/api/me'"
if not login_pat.search(s):
    raise SystemExit('LOGIN_ROUTE_NOT_FOUND')
s = login_pat.sub(login_repl, s, count=1)

# Health endpoint documents the actual authentication model.
s = s.replace("auth:'name_birth_year'", "auth:'name_birth_year_personal_code'", 1)

# Public structures must not expose private birth years.
s = s.replace("birth_year:u.birth_year||0,created_at:u.created_at", "created_at:u.created_at", 1)

BACKEND.write_text(s, encoding='utf-8')

m = MAIN.read_text(encoding='utf-8')

old_login = 'EditText n=field("الاسم الثلاثي"),y=field("سنة الميلاد");y.setInputType(InputType.TYPE_CLASS_NUMBER);content.addView(n,lp(-1,64,10));content.addView(y,lp(-1,64,14));Button l=btn("تسجيل الدخول"),c=btn("إنشاء هوية جديدة");'
new_login = 'EditText n=field("الاسم الثلاثي"),y=field("سنة الميلاد"),code=field("الرمز الشخصي (6-12 أرقام)");y.setInputType(InputType.TYPE_CLASS_NUMBER);code.setInputType(InputType.TYPE_CLASS_NUMBER|InputType.TYPE_NUMBER_VARIATION_PASSWORD);content.addView(n,lp(-1,64,10));content.addView(y,lp(-1,64,10));content.addView(code,lp(-1,64,14));Button l=btn("تسجيل الدخول"),c=btn("إنشاء هوية جديدة");'
if old_login not in m:
    raise SystemExit('MAIN_LOGIN_UI_ANCHOR_NOT_FOUND')
m = m.replace(old_login,new_login,1)
m = m.replace('auth(n.getText().toString().trim(),y.getText().toString().trim(),false);','auth(n.getText().toString().trim(),y.getText().toString().trim(),code.getText().toString().trim(),false);',1)
m = m.replace('auth(n.getText().toString().trim(),y.getText().toString().trim(),true);','auth(n.getText().toString().trim(),y.getText().toString().trim(),code.getText().toString().trim(),true);',1)

old_auth = 'private void auth(String n,String y,boolean create){if(n.split("\\\\s+").length<3||!y.matches("\\\\d{4}")){toast("أدخل الاسم الثلاثي وسنة الميلاد");return;}io.execute(()->{try{JSONObject q=new JSONObject();q.put("name",n);q.put("birthYear",Integer.parseInt(y));q.put("deviceKey",deviceKey());'
if old_auth not in m:
    old_auth = 'private void auth(String n,String y,boolean create){if(n.split("\\s+").length<3||!y.matches("\\d{4}")){toast("أدخل الاسم الثلاثي وسنة الميلاد");return;}io.execute(()->{try{JSONObject q=new JSONObject();q.put("name",n);q.put("birthYear",Integer.parseInt(y));q.put("deviceKey",deviceKey());'
new_auth = 'private void auth(String n,String y,String code,boolean create){if(n.split("\\s+").length<3||!y.matches("\\d{4}")||!code.matches("\\d{6,12}")){toast("أدخل الاسم الثلاثي وسنة الميلاد والرمز الشخصي (6-12 أرقام)");return;}io.execute(()->{try{JSONObject q=new JSONObject();q.put("name",n);q.put("birthYear",Integer.parseInt(y));q.put("personalCode",code);q.put("deviceKey",deviceKey());'
if old_auth not in m:
    raise SystemExit('MAIN_AUTH_METHOD_ANCHOR_NOT_FOUND')
m = m.replace(old_auth,new_auth,1)

m = m.replace('Menu("⚠', 'Menu("⚠') if False else m
MAIN.write_text(m, encoding='utf-8')
print('OK')
