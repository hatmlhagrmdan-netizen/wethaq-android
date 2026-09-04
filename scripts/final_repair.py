from pathlib import Path

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
    s=s.replace(anchor,anchor+"function linkContacts(a,b){db.prepare('INSERT OR IGNORE INTO contacts(user_id,contact_id) VALUES(?,?)').run(a,b);db.prepare('INSERT OR IGNORE INTO contacts(user_id,contact_id) VALUES(?,?)').run(b,a)}")
# Restore the authenticated contact creation endpoint used by the application and smoke test.
if "app.post('/api/contacts'" not in s:
    contact_route="app.post('/api/contacts',auth,(req,res)=>{const wethaqId=String(req.body?.wethaqId||'').trim();if(!wethaqId)return res.status(400).json({error:'invalid_contact'});const u=db.prepare('SELECT * FROM users WHERE wethaq_id=?').get(wethaqId);if(!u)return res.status(404).json({error:'user_not_found'});if(Number(u.id)===Number(req.user.sub))return res.status(400).json({error:'self_contact'});linkContacts(Number(req.user.sub),u.id);res.status(201).json({contact:publicUser(u,true)})});"
    s=s.replace(anchor,contact_route+anchor,1)
s=s.replace(".run(req.user.sub,u.id,body,isOnline(u.id)?'delivered':'sent');res.status(201)",".run(req.user.sub,u.id,body,isOnline(u.id)?'delivered':'sent');linkContacts(Number(req.user.sub),u.id);res.status(201)")
s=s.replace(".run(req.user.sub,u.id,'',mime,audio);res.status(201)",".run(req.user.sub,u.id,'',mime,audio);linkContacts(Number(req.user.sub),u.id);res.status(201)")
s=s.replace(".run(req.user.sub,u.id,'',mime,image);res.status(201)",".run(req.user.sub,u.id,'',mime,image);linkContacts(Number(req.user.sub),u.id);res.status(201)")
# Enforce device binding during name+birthYear login. Identity creation already binds the first device;
# login must never mint a token for a different device.
old="app.post('/api/login',(req,res)=>{const x=identityInput(req,res);if(!x)return;let u=db.prepare('SELECT * FROM users WHERE name=? AND birth_year=?').get(x.name,x.birthYear);if(!u)return res.status(404).json({error:'user_not_found'});const ban=activeBan(u.id);if(ban)return res.status(403).json({error:'user_banned',ban_type:ban.ban_type,expires_at:ban.expires_at||null,reason:ban.reason||''});db.prepare('UPDATE users SET last_seen=? WHERE id=?').run(now(),u.id);u=db.prepare('SELECT * FROM users WHERE id=?').get(u.id);res.json({user:publicUser(u),token:tokenFor(u)})});"
new="app.post('/api/login',(req,res)=>{const x=identityInput(req,res);if(!x)return;let u=db.prepare('SELECT * FROM users WHERE name=? AND birth_year=?').get(x.name,x.birthYear);if(!u)return res.status(404).json({error:'user_not_found'});if(u.device_key&&u.device_key!==x.deviceKey)return res.status(401).json({error:'untrusted_device'});const ban=activeBan(u.id);if(ban)return res.status(403).json({error:'user_banned',ban_type:ban.ban_type,expires_at:ban.expires_at||null,reason:ban.reason||''});db.prepare('UPDATE users SET last_seen=? WHERE id=?').run(now(),u.id);u=db.prepare('SELECT * FROM users WHERE id=?').get(u.id);res.json({user:publicUser(u),token:tokenFor(u)})});"
if old not in s:
    raise SystemExit('LOGIN_BLOCK_NOT_FOUND')
s=s.replace(old,new,1)
p.write_text(s,encoding='utf-8')
print('OK')
