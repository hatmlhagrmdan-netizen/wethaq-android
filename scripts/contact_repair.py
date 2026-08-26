from pathlib import Path
p=Path('app/src/main/java/com/wethaq/app/MainActivity.java')
s=p.read_text(encoding='utf-8')
old='if(a!=null&&a.length()>0){JSONObject m=a.optJSONObject(a.length()-1);'
new='if(a!=null&&a.length()>0){for(int k=0;k<a.length();k++){JSONObject z=a.optJSONObject(k);if(z!=null)addLocalContact(z.optString("sender_wethaq_id"),z.optString("sender_name","مستخدم"));}JSONObject m=a.optJSONObject(a.length()-1);'
s=s.replace(old,new)
anchor='private void send(){'
method='private void addLocalContact(String id,String name){try{if(id==null||id.isEmpty())return;JSONArray a=contacts();for(int i=0;i<a.length();i++)if(id.equals(a.optJSONObject(i).optString("wethaq_id")))return;JSONObject o=new JSONObject();o.put("wethaq_id",id);o.put("name",name==null?"مستخدم":name);a.put(o);prefs.edit().putString(CONTACTS,a.toString()).apply();}catch(Exception ignored){}}\n '
if 'private void addLocalContact(' not in s:s=s.replace(anchor,method+anchor,1)
p.write_text(s,encoding='utf-8')
print('OK')
