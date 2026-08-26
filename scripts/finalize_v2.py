from pathlib import Path

def R(p): return Path(p).read_text(encoding='utf-8')
def W(p,s): Path(p).write_text(s,encoding='utf-8')
def span(s,sig):
    a=s.find(sig)
    if a<0:return None
    b=s.find('{',a);d=0
    for i in range(b,len(s)):
        if s[i]=='{':d+=1
        elif s[i]=='}':
            d-=1
            if d==0:return a,i+1
    raise RuntimeError('unclosed '+sig)
def method(p,sig,new):
    s=R(p);z=span(s,sig)
    if z: W(p,s[:z[0]]+new+s[z[1]:]);return True
    return False

def add_before_class_end(p,code):
    s=R(p)
    if code.strip() in s:return
    i=s.rfind('}')
    W(p,s[:i]+code+'\n'+s[i:])

main='app/src/main/java/com/wethaq/app/MainActivity.java'
s=R(main)
s=s.replace('menu("▣  المحفوظات",this::archiveScreen);','')
s=s.replace('menu("⚠  الشكاوى والتواصل مع المؤسس",this::sendComplaint);','menu("⚠  الشكاوى والتواصل مع المؤسس",this::complaintScreen);')
needle='String type=m.optString("message_type","text");boolean mine=meW.equals(senderW)||meDb.equals(s);'
s=s.replace(needle,'String type=m.optString("message_type","text");boolean mine=meW.equals(senderW)||meDb.equals(s);if(!mine&&!senderW.isEmpty())saveContact(senderW,senderName);',1)
s=s.replace('if(r.code>=200&&r.code<300){input.setText("");loadMessages();}','if(r.code>=200&&r.code<300){saveContact(activeId,activeName);input.setText("");loadMessages();}',1)
if 'private String pendingImageBase64' not in s:s=s.replace('private MediaRecorder recorder;private boolean recording;','private MediaRecorder recorder;private boolean recording;private String recordingPath,pendingImageBase64,pendingAudioBase64;')
W(main,s)

method(main,'private void sendComplaint()', '''private void complaintScreen(){base("الشكاوى والتواصل مع المؤسس");TextView info=tv("اكتب شكواك أو رسالتك ثم اضغط إرسال، وستصل مباشرة إلى المؤسس.",17,Color.WHITE);content.addView(info,lp(-1,-2,12));EditText box=field("اكتب الشكوى أو الرسالة هنا…");box.setSingleLine(false);box.setGravity(Gravity.RIGHT|Gravity.TOP);box.setMinHeight(dp(180));content.addView(box,lp(-1,180,12));Button send=btn("إرسال الشكوى"),back=btn("رجوع");content.addView(send,lp(-1,76,8));content.addView(back,lp(-1,76,8));send.setOnClickListener(v->{click();String body=box.getText().toString().trim();if(body.length()<2){toast("اكتب الشكوى أولاً");return;}io.execute(()->{try{JSONObject q=new JSONObject();q.put("message",body);HttpResult r=request("POST","/api/complaints",q.toString(),auth());h.post(()->toast(r.code>=200&&r.code<300?"تم إرسال الشكوى إلى المؤسس ✓":error(r)));}catch(Exception e){h.post(()->toast("تعذر إرسال الشكوى"));}});});back.setOnClickListener(v->{click();home();});}''')

# Recording becomes a draft. Send button performs the actual upload.
method(main,'private void startRecording()', '''private void startRecording(){try{if(Build.VERSION.SDK_INT>=23&&checkSelfPermission(Manifest.permission.RECORD_AUDIO)!=PackageManager.PERMISSION_GRANTED){requestPermissions(new String[]{Manifest.permission.RECORD_AUDIO},901);toast("اسمح للميكروفون أولاً");return;}recordingPath=new File(getCacheDir(),"wethaq_voice_"+System.currentTimeMillis()+".m4a").getAbsolutePath();recorder=new MediaRecorder();recorder.setAudioSource(MediaRecorder.AudioSource.MIC);recorder.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4);recorder.setAudioEncoder(MediaRecorder.AudioEncoder.AAC);recorder.setOutputFile(recordingPath);recorder.prepare();recorder.start();recording=true;toast("جاري التسجيل… اضغط الميكروفون لإيقافه");}catch(Exception e){recording=false;toast("تعذر تشغيل التسجيل الصوتي");}}''')
method(main,'private void stopRecording(boolean send)', '''private void stopRecording(boolean send){try{if(recorder!=null){if(recording){try{recorder.stop();}catch(Exception ignored){}}recorder.release();recorder=null;}recording=false;if(recordingPath!=null){File f=new File(recordingPath);if(f.exists()&&f.length()>0){pendingAudioBase64=Base64.getEncoder().encodeToString(java.nio.file.Files.readAllBytes(f.toPath()));if(input!=null)input.setText("🎙 رسالة صوتية جاهزة للإرسال");}f.delete();}}catch(Exception e){toast("فشل حفظ التسجيل");}recordingPath=null;}''')

# Add a media endpoint client helper and replace send() with a single deterministic dispatcher.
method(main,'private void send()', '''private void send(){if(activeId==null)return;if(pendingImageBase64!=null){final String data=pendingImageBase64;pendingImageBase64=null;input.setText("");io.execute(()->sendMediaDraft("image",data));return;}if(pendingAudioBase64!=null){final String data=pendingAudioBase64;pendingAudioBase64=null;input.setText("");io.execute(()->sendMediaDraft("audio",data));return;}String text=input==null?"":input.getText().toString().trim();if(text.isEmpty())return;io.execute(()->{try{JSONObject q=new JSONObject();q.put("to",activeId);q.put("body",text);HttpResult r=request("POST","/api/messages",q.toString(),auth());h.post(()->{if(r.code>=200&&r.code<300){saveContact(activeId,activeName);input.setText("");loadMessages();}else toast(error(r));});}catch(Exception e){h.post(()->toast("فشل الإرسال"));}});}''')
add_before_class_end(main, '''private void sendMediaDraft(String type,String data){try{JSONObject q=new JSONObject();q.put("to",activeId);q.put("type",type);q.put("data",data);HttpResult r=request("POST","/api/messages/media",q.toString(),auth());h.post(()->{if(r.code>=200&&r.code<300){saveContact(activeId,activeName);loadMessages();}else toast(error(r));});}catch(Exception e){h.post(()->toast("فشل إرسال الوسائط"));}}''')

# Gallery result becomes a composer draft. 703 is reserved for profile selection.
add_before_class_end(main, '''@Override protected void onActivityResult(int requestCode,int resultCode,Intent data){super.onActivityResult(requestCode,resultCode,data);if(resultCode!=RESULT_OK||data==null||data.getData()==null)return;try{if(requestCode==702){pendingImageBase64=compressImageToBase64(data.getData());pendingAudioBase64=null;if(input!=null)input.setText("🖼 صورة جاهزة للإرسال");}else if(requestCode==703){uploadAvatar(compressImageToBase64(data.getData()),"image/jpeg");}}catch(Exception e){toast("تعذر تجهيز الملف");}}''')
add_before_class_end(main, '''private String compressImageToBase64(Uri u)throws Exception{Bitmap src=BitmapFactory.decodeStream(getContentResolver().openInputStream(u));if(src==null)return null;int max=1280;float sc=Math.min(1f,max/(float)Math.max(src.getWidth(),src.getHeight()));Bitmap b=Bitmap.createScaledBitmap(src,Math.max(1,(int)(src.getWidth()*sc)),Math.max(1,(int)(src.getHeight()*sc)),true);ByteArrayOutputStream o=new ByteArrayOutputStream();b.compress(Bitmap.CompressFormat.JPEG,78,o);if(b!=src)b.recycle();if(src!=b)src.recycle();return Base64.getEncoder().encodeToString(o.toByteArray());}''')

server='backend/server.js'; ss=R(server)
if "app.post('/api/messages/media'" not in ss:
    marker="server.listen(PORT,'0.0.0.0',()=>console.log(`Wethaq backend listening on ${PORT}`));"
    endpoint='''app.post('/api/messages/media',auth,(req,res)=>{const to=String(req.body?.to||'').trim(),type=String(req.body?.type||'').trim(),data=String(req.body?.data||'');if(!to||!['image','audio'].includes(type)||!data)return res.status(400).json({error:'invalid_media'});const u=db.prepare('SELECT id FROM users WHERE wethaq_id=?').get(to);if(!u)return res.status(404).json({error:'user_not_found'});const max=type==='image'?6*1024*1024:5*1024*1024;if(data.length>max)return res.status(413).json({error:'media_too_large'});const z=db.prepare("INSERT INTO messages(sender_id,receiver_id,body,message_type,audio_data,status) VALUES(?,?,?,? ,? ,?)").run(req.user.sub,u.id,'',type,data,isOnline(u.id)?'delivered':'sent');res.status(201).json({ok:true,message_id:z.lastInsertRowid})});
'''
    ss=ss.replace(marker,endpoint+marker)
W(server,ss)
print('FINALIZE_V2_OK')
