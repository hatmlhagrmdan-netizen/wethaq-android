from pathlib import Path
import re

p=Path('app/src/main/java/com/wethaq/app/MainActivity.java')
s=p.read_text(encoding='utf-8')
s=s.replace('menu("▣  المحفوظات",this::archiveScreen);','')
s=s.replace('content.addView(c,lp(-1,76,14));content.addView(tv("المؤسس:', 'content.addView(c,lp(-1,76,14));l.bringToFront();c.bringToFront();l.setEnabled(true);c.setEnabled(true);content.addView(tv("المؤسس:')
# Always make the executor-captured authentication values final.
s=s.replace('n=n.trim();y=y.trim();','final String fn=n.trim(),fy=y.trim();',1)
s=s.replace('q.put("name",n);q.put("birthYear",Integer.parseInt(y));','q.put("name",fn);q.put("birthYear",Integer.parseInt(fy));',1)
s=s.replace('u.optString("name",n)).put(YEAR,y)', 'u.optString("name",fn)).put(YEAR,fy)',1)
p.write_text(s,encoding='utf-8')

p=Path('backend/server.js')
s=p.read_text(encoding='utf-8')
anchor="function receiver(to,res){const u=db.prepare('SELECT id,wethaq_id,name FROM users WHERE wethaq_id=?').get(to);if(!u){res.status(404).json({error:'user_not_found'});return null}return u}"
if 'function linkContacts(a,b)' not in s:
    s=s.replace(anchor,anchor+"function linkContacts(a,b){if(!a||!b||Number(a)===Number(b))return;db.prepare('INSERT OR IGNORE INTO contacts(user_id,contact_id) VALUES(?,?)').run(Number(a),Number(b));db.prepare('INSERT OR IGNORE INTO contacts(user_id,contact_id) VALUES(?,?)').run(Number(b),Number(a))}")
# Restore the authenticated contact creation endpoint used by the application and smoke test.
if "app.post('/api/contacts'" not in s:
    contact_route="app.post('/api/contacts',auth,(req,res)=>{const wethaqId=String(req.body?.wethaqId||'').trim();if(!wethaqId)return res.status(400).json({error:'invalid_contact'});const u=db.prepare('SELECT * FROM users WHERE wethaq_id=?').get(wethaqId);if(!u)return res.status(404).json({error:'user_not_found'});if(Number(u.id)===Number(req.user.sub))return res.status(400).json({error:'self_contact'});linkContacts(Number(req.user.sub),u.id);res.status(201).json({contact:publicUser(u,true)})});"
    insert_at=s.find("app.post('/api/messages'")
    if insert_at>=0:s=s[:insert_at]+contact_route+s[insert_at:]
# Keep message/media contact linking enabled without duplicating existing calls.
if "app.post('/api/messages',auth" in s and "linkContacts(req.user.sub,u.id);const z=db.prepare(\"INSERT INTO messages" not in s:
    s=s.replace("const sender=db.prepare('SELECT id,wethaq_id,name FROM users WHERE id=?').get(req.user.sub);", "const sender=db.prepare('SELECT id,wethaq_id,name FROM users WHERE id=?').get(req.user.sub);linkContacts(req.user.sub,u.id);",1)
# Enforce device binding during name+birthYear login. Replace the whole login route by boundaries,
# rather than relying on one exact minified source string; this makes the repair idempotent.
login=re.compile(r"app\.post\('/api/login'.*?\}\);app\.get\('/api/me'", re.S)
login_route="app.post('/api/login',(req,res)=>{const x=identityInput(req,res);if(!x)return;let u=db.prepare('SELECT * FROM users WHERE name=? AND birth_year=?').get(x.name,x.birthYear);if(!u)return res.status(404).json({error:'user_not_found'});if(u.device_key&&u.device_key!==x.deviceKey)return res.status(401).json({error:'untrusted_device'});const ban=activeBan(u.id);if(ban)return res.status(403).json({error:'user_banned',ban_type:ban.ban_type,expires_at:ban.expires_at||null,reason:ban.reason||''});db.prepare('UPDATE users SET last_seen=? WHERE id=?').run(now(),u.id);u=db.prepare('SELECT * FROM users WHERE id=?').get(u.id);res.json({user:publicUser(u),token:tokenFor(u)})});app.get('/api/me'"
if login.search(s):
    s=login.sub(login_route,s,count=1)
else:
    raise SystemExit('LOGIN_ROUTE_NOT_FOUND')
p.write_text(s,encoding='utf-8')
print('OK')
