from pathlib import Path
import re

p=Path('app/src/main/java/com/wethaq/app/MainActivity.java')
s=p.read_text(encoding='utf-8')
# Remove the obsolete archive entry from the home menu.
s=s.replace('menu("▣  المحفوظات",this::archiveScreen);','')
# Make the login controls explicitly interactive and front-most.
s=s.replace('content.addView(c,lp(-1,76,14));content.addView(tv("المؤسس:', 'content.addView(c,lp(-1,76,14));l.bringToFront();c.bringToFront();l.setEnabled(true);c.setEnabled(true);content.addView(tv("المؤسس:')
# Repair the malformed send-method injection before javac sees the file.
# The previous repair pass could append a second send body and close the class early.
clean_send='''private void send(){
 String text=input==null?"":input.getText().toString().trim();
 if(text.isEmpty()||activeId==null)return;
 io.execute(()->{try{
  JSONObject q=new JSONObject();q.put("to",activeId);q.put("body",text);
  HttpResult r=request("POST","/api/messages",q.toString(),auth());
  h.post(()->{if(r.code>=200&&r.code<300){input.setText("");loadMessages();}else toast(error(r));});
 }catch(Exception e){h.post(()->toast("فشل الإرسال"));}});
}
'''
s=re.sub(r'private void send\(\)\{.*?(?=\s*private void settingsScreen\(\))', clean_send, s, count=1, flags=re.S)
p.write_text(s,encoding='utf-8')

p=Path('backend/server.js')
s=p.read_text(encoding='utf-8')
anchor="function receiver(to,res){const u=db.prepare('SELECT id,wethaq_id,name FROM users WHERE wethaq_id=?').get(to);if(!u){res.status(404).json({error:'user_not_found'});return null}return u}"
helper=anchor+"function linkContacts(a,b){db.prepare('INSERT OR IGNORE INTO contacts(user_id,contact_id) VALUES(?,?)').run(a,b);db.prepare('INSERT OR IGNORE INTO contacts(user_id,contact_id) VALUES(?,?)').run(b,a)}"
s=s.replace(anchor,helper)
s=s.replace(".run(req.user.sub,u.id,body,isOnline(u.id)?'delivered':'sent');res.status(201)",".run(req.user.sub,u.id,body,isOnline(u.id)?'delivered':'sent');linkContacts(Number(req.user.sub),u.id);res.status(201)")
s=s.replace(".run(req.user.sub,u.id,'',mime,audio);res.status(201)",".run(req.user.sub,u.id,'',mime,audio);linkContacts(Number(req.user.sub),u.id);res.status(201)")
s=s.replace(".run(req.user.sub,u.id,'',mime,image);res.status(201)",".run(req.user.sub,u.id,'',mime,image);linkContacts(Number(req.user.sub),u.id);res.status(201)")
p.write_text(s,encoding='utf-8')
print('OK')
