from pathlib import Path

p=Path('app/src/main/java/com/wethaq/app/MainActivity.java')
s=p.read_text(encoding='utf-8')
# Remove the obsolete archive entry from the home menu.
s=s.replace('menu("▣  المحفوظات",this::archiveScreen);','')
# Keep login controls visible, enabled and clickable.
s=s.replace('content.addView(c,lp(-1,76,14));content.addView(tv("المؤسس:', 'content.addView(c,lp(-1,76,14));l.bringToFront();c.bringToFront();l.setEnabled(true);c.setEnabled(true);content.addView(tv("المؤسس:')
# Make trimmed authentication inputs final/effectively-final for the executor lambda.
old='private void auth(String n,String y,boolean create){n=n.trim();y=y.trim();if(n.split(" ").length<3||(y.length()!=4)){'
new='private void auth(String n,String y,boolean create){final String fn=n.trim(),fy=y.trim();if(fn.split(" ").length<3||(fy.length()!=4)){'
s=s.replace(old,new,1)
s=s.replace('q.put("name",n);q.put("birthYear",Integer.parseInt(y));','q.put("name",fn);q.put("birthYear",Integer.parseInt(fy));',1)
s=s.replace('u.optString("name",n)).put(YEAR,y)', 'u.optString("name",fn)).put(YEAR,fy)',1)
p.write_text(s,encoding='utf-8')

p=Path('backend/server.js')
s=p.read_text(encoding='utf-8')
anchor="function receiver(to,res){const u=db.prepare('SELECT id,wethaq_id,name FROM users WHERE wethaq_id=?').get(to);if(!u){res.status(404).json({error:'user_not_found'});return null}return u}"
if 'function linkContacts(a,b)' not in s:
    s=s.replace(anchor,anchor+"function linkContacts(a,b){db.prepare('INSERT OR IGNORE INTO contacts(user_id,contact_id) VALUES(?,?)').run(a,b);db.prepare('INSERT OR IGNORE INTO contacts(user_id,contact_id) VALUES(?,?)').run(b,a)}")
s=s.replace(".run(req.user.sub,u.id,body,isOnline(u.id)?'delivered':'sent');res.status(201)",".run(req.user.sub,u.id,body,isOnline(u.id)?'delivered':'sent');linkContacts(Number(req.user.sub),u.id);res.status(201)")
s=s.replace(".run(req.user.sub,u.id,'',mime,audio);res.status(201)",".run(req.user.sub,u.id,'',mime,audio);linkContacts(Number(req.user.sub),u.id);res.status(201)")
s=s.replace(".run(req.user.sub,u.id,'',mime,image);res.status(201)",".run(req.user.sub,u.id,'',mime,image);linkContacts(Number(req.user.sub),u.id);res.status(201)")
p.write_text(s,encoding='utf-8')
print('OK')
