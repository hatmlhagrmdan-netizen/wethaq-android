from pathlib import Path

MAIN=Path('app/src/main/java/com/wethaq/app/MainActivity.java')
VIDEO=Path('app/src/main/java/com/wethaq/app/VideoCallActivity.java')
SERVICE=Path('app/src/main/java/com/wethaq/app/WethaqMessageService.java')

def replace_method(src, signature, body):
    start=src.find(signature)
    if start<0: raise SystemExit('missing '+signature)
    brace=src.find('{',start);depth=0;end=-1
    for i in range(brace,len(src)):
        if src[i]=='{':depth+=1
        elif src[i]=='}':
            depth-=1
            if depth==0:end=i+1;break
    if end<0:raise SystemExit('unterminated '+signature)
    return src[:start]+body+src[end:]

s=MAIN.read_text(encoding='utf-8')
if 'private void startAudioCall()' not in s:
    s=replace_method(s,'private void startVideoCall()', '''private void startVideoCall(){startCall(false);} private void startAudioCall(){startCall(true);} private void startCall(boolean audioOnly){if(activeId==null||activeId.trim().isEmpty()){toast("معرف الطرف الآخر غير موجود");return;}try{Intent i=new Intent(this,VideoCallActivity.class);i.putExtra("target",activeId);i.putExtra("name",activeName);i.putExtra("audioOnly",audioOnly);startActivity(i);}catch(Exception e){toast("تعذر فتح المكالمة");}}''')
s=s.replace('startPolling();startInboxDelivery();','startPolling();startInboxDelivery();requestNotifications();')
MAIN.write_text(s,encoding='utf-8')

# Pass the actual offer payload through the incoming-call notification. This prevents
# the callee from missing an offer created just before the full-screen Activity starts.
sv=SERVICE.read_text(encoding='utf-8')
sv=sv.replace('showIncomingCall(id,name,audioOnly);','showIncomingCall(id,name,audioOnly,payload);')
sv=sv.replace('private void showIncomingCall(String target,String name,boolean audioOnly){','private void showIncomingCall(String target,String name,boolean audioOnly,String offerPayload){')
sv=sv.replace('i.putExtra("audioOnly",audioOnly);','i.putExtra("audioOnly",audioOnly);i.putExtra("incomingOffer",offerPayload);')
SERVICE.write_text(sv,encoding='utf-8')

v=VIDEO.read_text(encoding='utf-8')
if 'private String incomingOffer;' not in v:
    v=v.replace('private String target,token,myId,signalSince;', 'private String target,token,myId,signalSince,incomingOffer;')
v=v.replace('audioOnly=getIntent().getBooleanExtra("audioOnly",false);signalSince=', 'audioOnly=getIntent().getBooleanExtra("audioOnly",false);incomingOffer=getIntent().getStringExtra("incomingOffer");signalSince=')
# The offer is delivered with the notification. Process it after the peer and local tracks exist.
needle='createPeer();startLocal();poll=new Runnable()'
if needle in v and 'handle("offer",incomingOffer)' not in v:
    v=v.replace(needle,'createPeer();startLocal();if(incomingOffer!=null&&!incomingOffer.trim().isEmpty()&&!isInitiator())handler.post(() -> handle("offer",incomingOffer));poll=new Runnable()')
VIDEO.write_text(v,encoding='utf-8')
print('incoming call offer handoff repaired')
