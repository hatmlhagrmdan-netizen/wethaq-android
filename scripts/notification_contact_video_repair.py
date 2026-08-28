from pathlib import Path
import re

# Patch Android client without replacing the existing messaging/media implementation.
p=Path('app/src/main/java/com/wethaq/app/MainActivity.java')
s=p.read_text(encoding='utf-8')

# Directly open a conversation from search; saving is no longer a prerequisite.
needle='Button save=btn("حفظ جهة الاتصال");'
if needle in s and 'Button chat=btn("مراسلة");' not in s:
    s=s.replace(needle, needle+'Button chat=btn("مراسلة");list.addView(chat,lp(-1,76,8));chat.setOnClickListener(v->{click();saveContact(id,n);conversation(id,n);});',1)

# Add the recipient locally as soon as the first message is successfully sent.
s=s.replace('if(r.code>=200&&r.code<300){input.setText("");loadMessages();}', 'if(r.code>=200&&r.code<300){addLocalContact(activeId,activeName);input.setText("");loadMessages();}', 1)

# Add a background inbox poll independent of the contacts screen. This makes incoming
# messages/notifications work even when the sender was never saved as a contact.
if 'private void startInboxDelivery()' not in s:
    marker=' @Override public void onCreate(Bundle b)'
    method=''' private long lastDeliveredInboxId=0;\n private void startInboxDelivery(){if(!hasToken())return;Runnable r=new Runnable(){public void run(){if(!hasToken())return;io.execute(()->{try{HttpResult x=request("GET","/api/messages/inbox",null,auth());if(x.code==200){JSONArray a=new JSONObject(x.body).optJSONArray("messages");if(a!=null){for(int i=0;i<a.length();i++){JSONObject m=a.optJSONObject(i);if(m==null)continue;long id=m.optLong("id");if(id<=lastDeliveredInboxId)continue;String sender=m.optString("sender_name","مستخدم");String body=m.optString("body","");addLocalContact(m.optString("sender_wethaq_id"),sender);if(lastDeliveredInboxId>0)h.post(()->notifyIncoming(sender+"\\n"+(body.isEmpty()?"رسالة جديدة":body)));lastDeliveredInboxId=Math.max(lastDeliveredInboxId,id);}}}}catch(Exception ignored){} });h.postDelayed(this,2000);}};h.post(r);}\n'''
    s=s.replace(marker,method+marker,1)
# Start independent delivery after the authenticated home screen is built.
s=s.replace('startPolling();\n }', 'startPolling();startInboxDelivery();\n }',1)
# Ensure the notification channel exists and notifications are requested when entering home.
p.write_text(s,encoding='utf-8')

# Stabilize WebRTC: ignore repeated persisted signaling rows, and make ICE handling robust.
p=Path('app/src/main/java/com/wethaq/app/VideoCallActivity.java')
s=p.read_text(encoding='utf-8')
if 'private final java.util.HashSet<Long> seenSignals' not in s:
    s=s.replace('private final List<IceCandidate> pendingCandidates=new ArrayList<>();', 'private final List<IceCandidate> pendingCandidates=new ArrayList<>();\n    private final java.util.HashSet<Long> seenSignals=new java.util.HashSet<>();')
s=s.replace('JSONObject x=a.getJSONObject(i);handle(x.optString("type"),x.optString("payload"));', 'JSONObject x=a.getJSONObject(i);long sid=x.optLong("id",0);if(sid!=0&&!seenSignals.add(sid))continue;handle(x.optString("type"),x.optString("payload"));')
# Do not send end signals repeatedly during cleanup.
s=s.replace('if(target!=null)sendSignal("end","{}");', 'if(target!=null&&!cleaned)sendSignal("end","{}");')
p.write_text(s,encoding='utf-8')
print('OK')
