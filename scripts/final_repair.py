from pathlib import Path
import re

p = Path('app/src/main/java/com/wethaq/app/MainActivity.java')
s = p.read_text(encoding='utf-8')
s = s.replace('menu("▣  المحفوظات",this::archiveScreen);', '')
s = s.replace('content.addView(c,lp(-1,76,14));content.addView(tv("المؤسس:', 'content.addView(c,lp(-1,76,14));l.bringToFront();c.bringToFront();l.setEnabled(true);c.setEnabled(true);content.addView(tv("المؤسس:', 1)
s = s.replace('n.split("\\s+")', 'n.split("\\\\s+")')
s = s.replace('!y.matches("\\d{4}")', '!y.matches("\\\\d{4}")')
s = s.replace('!code.matches("\\d{6,12}")', '!code.matches("\\\\d{6,12}")')
s = s.replace('final String fn=n.trim(),fy=y.trim();', 'final String name=n.trim(),year=y.trim();')
s = s.replace('q.put("name",fn);', 'q.put("name",name);')
s = s.replace('q.put("birthYear",Integer.parseInt(fy));', 'q.put("birthYear",Integer.parseInt(year));')
s = s.replace('u.optString("name",fn)).put(YEAR,fy)', 'u.optString("name",name)).put(YEAR,year)')
if 'q.put("personalCode",code)' not in s or 'private void auth(String n,String y,String code,boolean create)' not in s:
    raise SystemExit('PERSONAL_CODE_AUTH_CONTRACT_MISSING')
p.write_text(s, encoding='utf-8')

p = Path('backend/server.js')
s = p.read_text(encoding='utf-8')
anchor = "function receiver(to,res){const u=db.prepare('SELECT id,wethaq_id,name FROM users WHERE wethaq_id=?').get(to);if(!u){res.status(404).json({error:'user_not_found'});return null}return u}"
if 'function linkContacts(a,b)' not in s:
    s = s.replace(anchor, anchor + "function linkContacts(a,b){if(!a||!b||Number(a)===Number(b))return;db.prepare('INSERT OR IGNORE INTO contacts(user_id,contact_id) VALUES(?,?)').run(Number(a),Number(b));db.prepare('INSERT OR IGNORE INTO contacts(user_id,contact_id) VALUES(?,?)').run(Number(b),Number(a))}")
contact_route = "app.post('/api/contacts',auth,(req,res)=>{const wethaqId=String(req.body?.wethaqId||'').trim();if(!wethaqId)return res.status(400).json({error:'invalid_contact'});const u=db.prepare('SELECT * FROM users WHERE wethaq_id=?').get(wethaqId);if(!u)return res.status(404).json({error:'user_not_found'});if(Number(u.id)===Number(req.user.sub))return res.status(400).json({error:'self_contact'});linkContacts(Number(req.user.sub),u.id);res.status(201).json({contact:publicUser(u,true)})});"
if "app.post('/api/contacts'" not in s:
    insert_at = s.find("app.post('/api/messages'")
    if insert_at >= 0: s = s[:insert_at] + contact_route + s[insert_at:]

login = re.compile(r"app\.post\('/api/login'.*?\}\);app\.get\('/api/me'", re.S)
login_route = "app.post('/api/login',(req,res)=>{const x=identityInput(req,res);if(!x)return;let u=db.prepare('SELECT * FROM users WHERE name=? AND birth_year=?').get(x.name,x.birthYear);if(!u)return res.status(404).json({error:'user_not_found'});if(!u.personal_code_hash)return res.status(409).json({error:'personal_code_not_set'});if(!verifyPersonalCode(x.personalCode,u.personal_code_hash))return res.status(401).json({error:'invalid_personal_code'});const ban=activeBan(u.id);if(ban)return res.status(403).json({error:'user_banned',ban_type:ban.ban_type,expires_at:ban.expires_at||null,reason:ban.reason||''});db.prepare('UPDATE users SET device_key=?,last_seen=? WHERE id=?').run(x.deviceKey,now(),u.id);u=db.prepare('SELECT * FROM users WHERE id=?').get(u.id);res.json({user:publicUser(u),token:tokenFor(u)});});app.get('/api/me'"
if not login.search(s): raise SystemExit('LOGIN_ROUTE_NOT_FOUND')
s = login.sub(login_route, s, count=1)
p.write_text(s, encoding='utf-8')
print('FINAL_REPAIR_IDEMPOTENT')
