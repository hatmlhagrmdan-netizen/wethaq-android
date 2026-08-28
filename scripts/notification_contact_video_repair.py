from pathlib import Path
# This repair layer is intentionally small and is applied after all other repair scripts.
# It guarantees that the production source has the final notification/contact/call behavior.
p=Path('app/src/main/java/com/wethaq/app/MainActivity.java')
s=p.read_text(encoding='utf-8')
# First-run notification permission: Android 13+ otherwise silently suppresses alerts.
s=s.replace('startPolling();startInboxDelivery();','startPolling();startInboxDelivery();requestNotifications();')
# Preserve direct-chat behavior if an older repair script ran before this one.
if 'Button chat=btn("💬 مراسلة مباشرة")' not in s:
    s=s.replace('Button save=btn("حفظ جهة الاتصال");', 'Button chat=btn("💬 مراسلة مباشرة");list.addView(chat,lp(-1,76,8));chat.setOnClickListener(v->{click();addLocalContact(id,n);conversation(id,n);});Button save=btn("حفظ جهة الاتصال");', 1)
# Ensure the contact helper exists.
if 'private void addLocalContact(String id,String n)' not in s:
    marker='private void saveContact(String id,String n)'
    helper='private void addLocalContact(String id,String n){if(id==null||id.trim().isEmpty())return;try{JSONArray a=contacts();for(int i=0;i<a.length();i++){JSONObject o=a.optJSONObject(i);if(o!=null&&id.equals(o.optString("wethaq_id")))return;}JSONObject o=new JSONObject();o.put("wethaq_id",id);o.put("name",n==null?"مستخدم":n);a.put(o);prefs.edit().putString(CONTACTS,a.toString()).apply();}catch(Exception ignored){}}\n '
    s=s.replace(marker,helper+marker,1)
p.write_text(s,encoding='utf-8')
print('OK')