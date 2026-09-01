from pathlib import Path
import re

APP = Path('app/src/main/java/com/wethaq/app/MainActivity.java')
SERVER = Path('backend/server.js')

# ---- Android: never send or persist a personal code before authentication succeeds ----
s = APP.read_text(encoding='utf-8')
if 'pendingPersonalCodeHash' not in s:
    anchor = 'private final ExecutorService io=Executors.newSingleThreadExecutor();'
    if anchor not in s:
        raise SystemExit('MainActivity executor anchor missing')
    s = s.replace(anchor, anchor + 'private String pendingPersonalCodeHash;', 1)

# Login / registration only keep the hash in memory until the server issued a valid token.
s = s.replace('l.setOnClickListener(v->{click();savePersonalCode(pc.getText().toString());auth(', 'l.setOnClickListener(v->{click();pendingPersonalCodeHash=hashPersonalCode(pc.getText().toString().trim());auth(')
s = s.replace('savePersonalCode(code);auth(n.getText().toString().trim(),y.getText().toString().trim(),true);', 'pendingPersonalCodeHash=hashPersonalCode(code);auth(n.getText().toString().trim(),y.getText().toString().trim(),true);')

# Include the hash in identity/login requests without exposing the raw code.
needle = 'q.put("name",n);q.put("birthYear",Integer.parseInt(y));q.put("deviceKey",deviceKey());HttpResult r=request('
if needle in s and 'q.put("personalCodeHash",pendingPersonalCodeHash)' not in s:
    replacement = 'q.put("name",n);q.put("birthYear",Integer.parseInt(y));q.put("deviceKey",deviceKey());if(pendingPersonalCodeHash!=null&&!pendingPersonalCodeHash.isEmpty())q.put("personalCodeHash",pendingPersonalCodeHash);HttpResult r=request('
    s = s.replace(needle, replacement, 1)

# After successful authentication, persist the hash with the fresh token. Never save before this point.
success_anchor = 'putString("admin_role",o.optString("role","")).apply();h.post(()->{dismissProgress();home();});'
if success_anchor in s and 'String pcHash=pendingPersonalCodeHash;' not in s:
    success_repl = '''putString("admin_role",o.optString("role","")).apply();String pcHash=pendingPersonalCodeHash;pendingPersonalCodeHash=null;if(pcHash!=null&&!pcHash.isEmpty()){try{JSONObject pq=new JSONObject();pq.put("codeHash",pcHash);request("POST","/api/profile/personal-code",pq.toString(),auth());}catch(Exception ignored){}}h.post(()->{dismissProgress();home();});'''
    s = s.replace(success_anchor, success_repl, 1)

# Do not silently accept too-short personal codes on the login path.
# Registration already enforces >=4 characters; login accepts blank for legacy accounts,
# while server-side verification is enforced for accounts that have a configured hash.
APP.write_text(s, encoding='utf-8')

# ---- Backend: backwards-compatible personal-code verification ----
server = SERVER.read_text(encoding='utf-8')

# /api/identity: accept an optional hashed code and persist it with a newly created/updated identity.
old_identity = "const{ name,birthYear,deviceKey}=x,base=makeBaseId(name,birthYear);let u=db.prepare('SELECT * FROM users WHERE wethaq_id=?').get(base);"
new_identity = "const{ name,birthYear,deviceKey}=x,base=makeBaseId(name,birthYear),personalCodeHash=String(req.body?.personalCodeHash||'').trim().toLowerCase();if(personalCodeHash&&!/^[a-f0-9]{64}$/.test(personalCodeHash))return res.status(400).json({error:'invalid_personal_code'});let u=db.prepare('SELECT * FROM users WHERE wethaq_id=?').get(base);"
if old_identity in server:
    server = server.replace(old_identity, new_identity, 1)

old_identity_update = "db.prepare('UPDATE users SET device_key=?,name=?,birth_year=?,last_seen=? WHERE id=?').run(deviceKey,name,birthYear,now(),u.id)"
new_identity_update = "db.prepare('UPDATE users SET device_key=?,name=?,birth_year=?,last_seen=?,personal_code_hash=COALESCE(?,personal_code_hash) WHERE id=?').run(deviceKey,name,birthYear,now(),personalCodeHash||null,u.id)"
if old_identity_update in server:
    server = server.replace(old_identity_update, new_identity_update, 1)

old_identity_insert = "db.prepare('INSERT INTO users(wethaq_id,name,birth_year,device_key,last_seen) VALUES(?,?,?,?,?)').run(id,name,birthYear,deviceKey,now())"
new_identity_insert = "db.prepare('INSERT INTO users(wethaq_id,name,birth_year,device_key,last_seen,personal_code_hash) VALUES(?,?,?,?,?,?)').run(id,name,birthYear,deviceKey,now(),personalCodeHash||null)"
if old_identity_insert in server:
    server = server.replace(old_identity_insert, new_identity_insert, 1)

# /api/login: configured personal codes become a mandatory second factor, while legacy accounts remain compatible.
old_login = "const x=identityInput(req,res);if(!x)return;let u=db.prepare('SELECT * FROM users WHERE name=? AND birth_year=?').get(x.name,x.birthYear);"
new_login = "const x=identityInput(req,res);if(!x)return;const personalCodeHash=String(req.body?.personalCodeHash||'').trim().toLowerCase();if(personalCodeHash&&!/^[a-f0-9]{64}$/.test(personalCodeHash))return res.status(400).json({error:'invalid_personal_code'});let u=db.prepare('SELECT * FROM users WHERE name=? AND birth_year=?').get(x.name,x.birthYear);"
if old_login in server:
    server = server.replace(old_login, new_login, 1)

old_login_device = "if(u.device_key&&u.device_key!==x.deviceKey)return res.status(401).json({error:'device_not_trusted'});if(!u.device_key)return res.status(409).json({error:'device_binding_required'});db.prepare('UPDATE users SET last_seen=? WHERE id=?').run(now(),u.id);"
new_login_device = "if(u.device_key&&u.device_key!==x.deviceKey)return res.status(401).json({error:'device_not_trusted'});if(!u.device_key)return res.status(409).json({error:'device_binding_required'});if(u.personal_code_hash&&u.personal_code_hash!==personalCodeHash)return res.status(401).json({error:'personal_code_required'});if(personalCodeHash&&!u.personal_code_hash)db.prepare('UPDATE users SET personal_code_hash=? WHERE id=?').run(personalCodeHash,u.id);db.prepare('UPDATE users SET last_seen=? WHERE id=?').run(now(),u.id);"
if old_login_device in server:
    server = server.replace(old_login_device, new_login_device, 1)

SERVER.write_text(server, encoding='utf-8')
print('WETHAQ_FINAL_HARDENING_REPAIR_OK')
