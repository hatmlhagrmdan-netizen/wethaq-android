from pathlib import Path
import re


def text(path): return Path(path).read_text(encoding='utf-8')
def write(path,s): Path(path).write_text(s,encoding='utf-8')

def method_span(s, sig):
    start=s.find(sig)
    if start<0:return None
    brace=s.find('{',start); depth=0
    for i in range(brace,len(s)):
        if s[i]=='{':depth+=1
        elif s[i]=='}':
            depth-=1
            if depth==0:return start,i+1
    raise RuntimeError('unclosed '+sig)

def replace_method(path,sig,new):
    s=text(path); span=method_span(s,sig)
    if not span:return False
    write(path,s[:span[0]]+new+s[span[1]:]);return True

def replace_first(path,old,new):
    s=text(path)
    if old not in s:return False
    write(path,s.replace(old,new,1));return True

main='app/src/main/java/com/wethaq/app/MainActivity.java'
s=text(main)
# Remove archived menu permanently from the home screen.
s=s.replace('menu("▣  المحفوظات",this::archiveScreen);','')
# Complaint opens a real form.
s=s.replace('menu("⚠  الشكاوى والتواصل مع المؤسس",this::sendComplaint);','menu("⚠  الشكاوى والتواصل مع المؤسس",this::complaintScreen);')
# Incoming messages are automatically saved locally.
needle='String type=m.optString("message_type","text");boolean mine=meW.equals(senderW)||meDb.equals(s);'
if needle in s:
    s=s.replace(needle,'String type=m.optString("message_type","text");boolean mine=meW.equals(senderW)||meDb.equals(s);if(!mine&&!senderW.isEmpty())saveContact(senderW,senderName);',1)
# Outgoing messages automatically save the recipient.
s=s.replace('if(r.code>=200&&r.code<300){input.setText("");loadMessages();}', 'if(r.code>=200&&r.code<300){saveContact(activeId,activeName);input.setText("");loadMessages();}',1)
write(main,s)

# Replace the old automatic complaint action with a form.
replace_method(main,'private void sendComplaint()', '''private void complaintScreen(){base("الشكاوى والتواصل مع المؤسس");TextView info=tv("اكتب شكواك أو رسالتك ثم اضغط إرسال، وستصل مباشرة إلى المؤسس.",17,Color.WHITE);content.addView(info,lp(-1,-2,12));EditText box=field("اكتب الشكوى أو الرسالة هنا…");box.setSingleLine(false);box.setGravity(Gravity.RIGHT|Gravity.TOP);box.setMinHeight(dp(180));content.addView(box,lp(-1,180,12));Button send=btn("إرسال الشكوى"),back=btn("رجوع");content.addView(send,lp(-1,76,8));content.addView(back,lp(-1,76,8));send.setOnClickListener(v->{click();String body=box.getText().toString().trim();if(body.length()<2){toast("اكتب الشكوى أولاً");return;}io.execute(()->{try{JSONObject q=new JSONObject();q.put("message",body);HttpResult r=request("POST","/api/complaints",q.toString(),auth());h.post(()->toast(r.code>=200&&r.code<300?"تم إرسال الشكوى إلى المؤسس ✓":error(r)));}catch(Exception e){h.post(()->toast("تعذر إرسال الشكوى"));}});});back.setOnClickListener(v->{click();home();});}''')

# Draft image/audio in the composer. They are sent only when the Send button is pressed.
s=text(main)
if 'private String pendingImageBase64;' not in s:
    s=s.replace('private MediaRecorder recorder;private boolean recording;', 'private MediaRecorder recorder;private boolean recording;private String pendingImageBase64,pendingImageType,pendingAudioBase64;')
# Make gallery select a draft instead of sending immediately.
if 'private void pickImage(){' in s:
    s=re.sub(r'private void pickImage\(\)\{.*?\}', 'private void pickImage(){startActivityForResult(new Intent(Intent.ACTION_PICK,MediaStore.Images.Media.EXTERNAL_CONTENT_URI),702);}', s, count=1, flags=re.S)
# Add a visible draft marker before send.
if 'private void showMediaDraft' not in s:
    marker='private void pickImage(){startActivityForResult(new Intent(Intent.ACTION_PICK,MediaStore.Images.Media.EXTERNAL_CONTENT_URI),702);}'
    helper='''\n private void showMediaDraft(String label){if(input!=null)input.setText(label);}\n private String compressImageToBase64(Uri u)throws Exception{Bitmap src=BitmapFactory.decodeStream(getContentResolver().openInputStream(u));if(src==null)return null;int max=1280;float sc=Math.min(1f,max/(float)Math.max(src.getWidth(),src.getHeight()));Bitmap b=Bitmap.createScaledBitmap(src,Math.max(1,(int)(src.getWidth()*sc)),Math.max(1,(int)(src.getHeight()*sc)),true);ByteArrayOutputStream o=new ByteArrayOutputStream();b.compress(Bitmap.CompressFormat.JPEG,78,o);if(b!=src)b.recycle();if(src!=b)src.recycle();return Base64.getEncoder().encodeToString(o.toByteArray());}\n'''
    s=s.replace(marker,marker+helper,1)
# Replace send so it handles text/image/audio drafts in one place.
new_send='''private void send(){if(activeId==null)return;if(pendingImageBase64!=null){final String data=pendingImageBase64;pendingImageBase64=null;pendingImageType=null;input.setText("");io.execute(()->{try{sendImage(data,"image/jpeg");h.post(()->{saveContact(activeId,activeName);loadMessages();});}catch(Exception e){h.post(()->toast("فشل إرسال الصورة"));}});return;}if(pendingAudioBase64!=null){final String data=pendingAudioBase64;pendingAudioBase64=null;input.setText("");io.execute(()->{try{sendAudio(data);h.post(()->{saveContact(activeId,activeName);loadMessages();});}catch(Exception e){h.post(()->toast("فشل إرسال الصوت"));}});return;}String text=input==null?"":input.getText().toString().trim();if(text.isEmpty())return;io.execute(()->{try{JSONObject q=new JSONObject();q.put("to",activeId);q.put("body",text);HttpResult r=request("POST","/api/messages",q.toString(),auth());h.post(()->{if(r.code>=200&&r.code<300){saveContact(activeId,activeName);input.setText("");loadMessages();}else toast(error(r));});}catch(Exception e){h.post(()->toast("فشل الإرسال"));}});}'''
replace_method(main,'private void send()',new_send)
# Make recording stop create a draft instead of immediately sending where possible.
s=text(main)
s=s.replace('if(recording)stopRecording(true);else startRecording();','if(recording)stopRecording(false);else startRecording();',1)
# Login is already wired; add explicit enabled/clickable properties defensively.
s=s.replace('Button l=btn("تسجيل الدخول"),c=btn("إنشاء هوية جديدة");','Button l=btn("تسجيل الدخول"),c=btn("إنشاء هوية جديدة");l.setEnabled(true);l.setClickable(true);c.setEnabled(true);c.setClickable(true);',1)
write(main,s)

# Ensure backend login returns a clear Arabic ban message when applicable, without duplicating the check.
server='backend/server.js'; ss=text(server)
if 'تم حظرك من الإدارة' not in ss:
    ss=ss.replace("return res.status(403).json({error:'user_banned',ban_type:ban.ban_type,expires_at:ban.expires_at||null,reason:ban.reason||''})", "return res.status(403).json({error:ban.ban_type==='permanent'?'تم طردك من التطبيق نهائيًا من الإدارة: '+(ban.reason||'بدون سبب'):'تم حظرك من الإدارة مؤقتًا: '+(ban.reason||'بدون سبب'),ban_type:ban.ban_type,expires_at:ban.expires_at||null,reason:ban.reason||''})",1)
    write(server,ss)
print('FINALIZE_PATCH_OK')
