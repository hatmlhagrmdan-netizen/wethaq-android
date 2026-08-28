from pathlib import Path
import re

MAIN=Path('app/src/main/java/com/wethaq/app/MainActivity.java')
VIDEO=Path('app/src/main/java/com/wethaq/app/VideoCallActivity.java')


def replace_method(src, signature, body):
    start=src.find(signature)
    if start < 0:
        raise RuntimeError('missing '+signature)
    brace=src.find('{',start)
    if brace < 0: raise RuntimeError('missing brace '+signature)
    depth=0
    for i in range(brace,len(src)):
        if src[i]=='{': depth+=1
        elif src[i]=='}':
            depth-=1
            if depth==0:
                return src[:start]+body+src[i+1:]
    raise RuntimeError('unterminated '+signature)

s=MAIN.read_text(encoding='utf-8')

# Pending media is state in the composer, not an immediate network send.
if 'pendingImageData' not in s:
    s=s.replace('private MediaRecorder recorder;private boolean recording;private long lastInboxId;', 'private MediaRecorder recorder;private boolean recording;private long lastInboxId;private String pendingImageData,pendingAudioData,pendingMime;private int pendingMediaType;')

# Replace the send method so the single Send button commits either a text,
# image draft or audio draft. This fixes the old repair which prepared media
# but left send() text-only.
s=replace_method(s,'private void send()', '''private void send(){
 final String target=activeId;
 if(target==null||target.trim().isEmpty())return;
 if(pendingAudioData!=null&&!pendingAudioData.isEmpty()){final String data=pendingAudioData;final String mime=pendingMime==null?"audio/mp4":pendingMime;io.execute(()->{try{JSONObject q=new JSONObject();q.put("to",target);q.put("audioBase64",data);q.put("mimeType",mime);HttpResult r=request("POST","/api/messages/audio",q.toString(),auth());h.post(()->{if(r.code>=200&&r.code<300){pendingAudioData=null;pendingMime=null;pendingMediaType=0;clearComposerMedia();loadMessages();toast("تم إرسال التسجيل الصوتي ✓");}else toast(error(r));});}catch(Exception e){h.post(()->toast("فشل إرسال التسجيل الصوتي"));}});return;}
 if(pendingImageData!=null&&!pendingImageData.isEmpty()){final String data=pendingImageData;final String mime=pendingMime==null?"image/jpeg":pendingMime;io.execute(()->{try{JSONObject q=new JSONObject();q.put("to",target);q.put("imageBase64",data);q.put("mimeType",mime);HttpResult r=request("POST","/api/messages/image",q.toString(),auth());h.post(()->{if(r.code>=200&&r.code<300){pendingImageData=null;pendingMime=null;pendingMediaType=0;clearComposerMedia();loadMessages();toast("تم إرسال الصورة ✓");}else toast(error(r));});}catch(Exception e){h.post(()->toast("فشل إرسال الصورة"));}});return;}
 String text=input==null?"":input.getText().toString().trim();if(text.isEmpty())return;io.execute(()->{try{JSONObject q=new JSONObject();q.put("to",target);q.put("body",text);HttpResult r=request("POST","/api/messages",q.toString(),auth());h.post(()->{if(r.code>=200&&r.code<300){input.setText("");loadMessages();}else toast(error(r));});}catch(Exception e){h.post(()->toast("فشل الإرسال"));}});
}
 private void clearComposerMedia(){if(input!=null){input.setCompoundDrawables(null,null,null,null);input.setHint("اكتب رسالة…");input.setText("");}}
''')

# Make image picker create a visible draft reliably.
s=s.replace('if(r==703)uploadAvatar(b64,"image/jpeg");else{pendingImageData=b64;pendingAudioData=null;pendingMime="image/jpeg";pendingMediaType=1;showImagePreview(b64);}', 'if(r==703)uploadAvatar(b64,"image/jpeg");else{pendingImageData=b64;pendingAudioData=null;pendingMime="image/jpeg";pendingMediaType=1;showImagePreview(b64);toast("الصورة جاهزة للإرسال — اضغط إرسال");}')

# If the old picker branch is present but the preview helper is missing, install it.
if 'private void showImagePreview(' not in s:
    marker='private void sendImage('
    idx=s.find(marker)
    if idx>=0:
        s=s[:idx]+'''private void showImagePreview(String data){try{byte[] z=Base64.getDecoder().decode(data);Bitmap bm=BitmapFactory.decodeByteArray(z,0,z.length);if(input!=null){BitmapDrawable bd=new BitmapDrawable(getResources(),bm);bd.setBounds(0,0,dp(64),dp(64));input.setCompoundDrawables(null,null,bd,null);input.setHint("الصورة جاهزة للإرسال — اضغط إرسال");}}catch(Exception ignored){}}\n '''+s[idx:]

# Add an explicit audio-call entry beside video call when the conversation opens.
needle='Button call=btn("📹 مكالمة فيديو");content.addView(call,lp(-1,76,6));'
if needle in s and 'Button audioCall=btn("📞 مكالمة صوتية")' not in s:
    s=s.replace(needle, needle+'Button audioCall=btn("📞 مكالمة صوتية");content.addView(audioCall,lp(-1,76,6));audioCall.setOnClickListener(v->{click();startAudioCall();});')

MAIN.write_text(s,encoding='utf-8')

# Harden the WebRTC activity for an explicit caller/callee session and avoid
# the old endCall recursion from onDestroy.
v=VIDEO.read_text(encoding='utf-8')
if 'private boolean audioOnly;' not in v:
    v=v.replace('private boolean cleaned,offerSent,remoteDescriptionSet;', 'private boolean cleaned,offerSent,remoteDescriptionSet;private boolean audioOnly;')
v=v.replace('if(!audioOnly){f.addView(remoteView,new FrameLayout.LayoutParams(-1,-1));', 'if(!audioOnly){f.addView(remoteView,new FrameLayout.LayoutParams(-1,-1));')
v=v.replace('private void endCall(){if(cleaned)return;cleaned=true;', 'private void endCall(){if(cleaned)return;cleaned=true;')
v=v.replace('@Override protected void onDestroy(){if(!cleaned)endCall();super.onDestroy();}', '@Override protected void onDestroy(){if(!cleaned){try{if(poll!=null)h.removeCallbacks(poll);}catch(Exception ignored){}}super.onDestroy();}')
VIDEO.write_text(v,encoding='utf-8')
print('production feature repair applied')