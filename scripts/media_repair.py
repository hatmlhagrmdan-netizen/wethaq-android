from pathlib import Path
import re

p=Path('app/src/main/java/com/wethaq/app/MainActivity.java')
s=p.read_text(encoding='utf-8')

fields='private String pendingImageData,pendingAudioData,pendingMime;private int pendingMediaType;'
s=re.sub(r'(?:private String pendingImageData,pendingAudioData,pendingMime;private int pendingMediaType;)+', fields, s)
if 'pendingImageData,pendingAudioData,pendingMime' not in s:
    s=s.replace('private MediaRecorder recorder;private boolean recording;private long lastInboxId;', 'private MediaRecorder recorder;private boolean recording;private long lastInboxId;'+fields)

s=s.replace('if(r==703)uploadAvatar(b64,"image/jpeg");else sendImage(b64,"image/jpeg");', 'if(r==703)uploadAvatar(b64,"image/jpeg");else{pendingImageData=b64;pendingAudioData=null;pendingMime="image/jpeg";pendingMediaType=1;showImagePreview(b64);}')

preview='private void showImagePreview(String data){try{byte[] z=Base64.getDecoder().decode(data);Bitmap bm=BitmapFactory.decodeByteArray(z,0,z.length);if(input!=null){input.setCompoundDrawablesWithIntrinsicBounds(new BitmapDrawable(getResources(),bm),null,null,null);input.setHint("الصورة جاهزة للإرسال — اضغط إرسال");}}catch(Exception ignored){}}'
while s.count(preview)>1:
    pos=s.find(preview,s.find(preview)+1)
    s=s[:pos]+s[pos+len(preview):]
if 'private void showImagePreview(String data)' not in s:
    s=s.replace('private void sendImage(String data,String mime){if(activeId==null)return;', preview+'\n private void sendImage(String data,String mime){if(activeId==null)return;',1)

s=s.replace('Base64.getDecoder().decode(m.optString("audio_data"))','Base64.getDecoder().decode(m.optString("image_data",m.optString("audio_data")))')

old='if(send&&f.exists()&&activeId!=null)io.execute(()->{try{String data=Base64.getEncoder().encodeToString(readFile(f));JSONObject q=new JSONObject();q.put("to",activeId);q.put("audioBase64",data);q.put("mimeType","audio/mp4");HttpResult r=request("POST","/api/messages/audio",q.toString(),auth());h.post(()->{if(r.code>=200&&r.code<300){f.delete();loadMessages();}else toast("فشل إرسال الصوت");});}catch(Exception e){h.post(()->toast("فشل إرسال الصوت"));}});'
new='if(send&&f.exists()&&activeId!=null)io.execute(()->{try{String data=Base64.getEncoder().encodeToString(readFile(f));h.post(()->{pendingAudioData=data;pendingImageData=null;pendingMime="audio/mp4";pendingMediaType=2;input.setHint("التسجيل جاهز للإرسال — اضغط إرسال");});f.delete();}catch(Exception e){h.post(()->toast("فشل تجهيز التسجيل"));}});'
s=s.replace(old,new)
p.write_text(s,encoding='utf-8')
print('OK')
