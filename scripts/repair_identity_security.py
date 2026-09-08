from pathlib import Path
import re

p = Path('backend/server.js')
s = p.read_text(encoding='utf-8')

# A missing stored personal-code hash must never be treated as an opportunity to
# initialize authentication from an arbitrary code supplied by a login attempt.
old = re.compile(r"app\.post\('/api/login',\(req,res\)=>\{const x=identityInput\(req,res\);if\(!x\)return;let u=db\.prepare\('SELECT \* FROM users WHERE name=\? AND birth_year=\?'\)\.get\(x\.name,x\.birthYear\);if\(!u\)return res\.status\(404\)\.json\(\{error:'user_not_found'\}\);if\(!u\.personal_code_hash\)\{db\.prepare\('UPDATE users SET personal_code_hash=\? WHERE id=\?'\)\.run\(hashPersonalCode\(x\.personalCode\),u\.id\)\}else if\(!verifyPersonalCode\(x\.personalCode,u\.personal_code_hash\)\)return res\.status\(401\)\.json\(\{error:'invalid_personal_code'\}\);const ban=activeBan\(u\.id\);if\(ban\)return res\.status\(403\)\.json\(\{error:'user_banned',ban_type:ban\.ban_type,expires_at:ban\.expires_at\|\|null,reason:ban\.reason\|\|''\}\);db\.prepare\('UPDATE users SET device_key=\?,last_seen=\? WHERE id=\?'\)\.run\(x\.deviceKey,now\(\),u\.id\);u=db\.prepare\('SELECT \* FROM users WHERE id=\?'\)\.get\(u\.id\);res\.json\(\{user:publicUser\(u\),token:tokenFor\(u\)\}\)\}\);app\.get\('/api/me'", re.S)
new = "app.post('/api/login',(req,res)=>{const x=identityInput(req,res);if(!x)return;let u=db.prepare('SELECT * FROM users WHERE name=? AND birth_year=?').get(x.name,x.birthYear);if(!u)return res.status(404).json({error:'user_not_found'});if(!u.personal_code_hash)return res.status(409).json({error:'personal_code_not_set'});if(!verifyPersonalCode(x.personalCode,u.personal_code_hash))return res.status(401).json({error:'invalid_personal_code'});const ban=activeBan(u.id);if(ban)return res.status(403).json({error:'user_banned',ban_type:ban.ban_type,expires_at:ban.expires_at||null,reason:ban.reason||''});db.prepare('UPDATE users SET device_key=?,last_seen=? WHERE id=?').run(x.deviceKey,now(),u.id);u=db.prepare('SELECT * FROM users WHERE id=?').get(u.id);res.json({user:publicUser(u),token:tokenFor(u)});});app.get('/api/me'"
if not old.search(s):
    if "if(!u.personal_code_hash)return res.status(409).json({error:'personal_code_not_set'});if(!verifyPersonalCode(x.personalCode,u.personal_code_hash))" in s:
        print('ALREADY_HARDENED')
    else:
        raise SystemExit('LOGIN_BLOCK_NOT_FOUND')
else:
    s = old.sub(new, s, count=1)
p.write_text(s, encoding='utf-8')
print('IDENTITY_SECURITY_HARDENED')
