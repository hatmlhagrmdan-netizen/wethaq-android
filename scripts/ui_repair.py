from pathlib import Path
p=Path('app/src/main/java/com/wethaq/app/MainActivity.java')
s=p.read_text(encoding='utf-8')

# Ensure the image preview dependency is imported in the generated Android source.
if 'import android.graphics.drawable.BitmapDrawable;' not in s:
    s=s.replace('import android.graphics.*;', 'import android.graphics.*;import android.graphics.drawable.BitmapDrawable;', 1)

def replace_method(src, name, replacement):
    marker='private void '+name+'()'
    start=src.find(marker)
    if start<0:return src
    brace=src.find('{',start)
    if brace<0:return src
    depth=0; end=-1
    for i in range(brace,len(src)):
        if src[i]=='{': depth+=1
        elif src[i]=='}':
            depth-=1
            if depth==0: end=i+1; break
    if end<0:return src
    return src[:start]+replacement+src[end:]

send_method='''private void send(){
if(activeId==null)return;
if(pendingImageData!=null||pendingAudioData!=null){final String image=pendingImageData,audio=pendingAudioData,mime=pendingMime;final int type=pendingMediaType;io.execute(()->{try{JSONObject q=new JSONObject();q.put("to",activeId);String path;if(type==1){path="/api/messages/image";q.put("imageBase64",image);q.put("mimeType",mime);}else{path="/api/messages/audio";q.put("audioBase64",audio);q.put("mimeType",mime);}HttpResult r=request("POST",path,q.toString(),auth());h.post(()->{if(r.code>=200&&r.code<300){pendingImageData=null;pendingAudioData=null;pendingMime=null;pendingMediaType=0;input.setText("");input.setHint("اكتب رسالة…");input.setCompoundDrawables(null,null,null,null);loadMessages();}else toast(error(r));});}catch(Exception e){h.post(()->toast("فشل إرسال الوسائط"));}});return;}
String text=input==null?"":input.getText().toString().trim();if(text.isEmpty())return;io.execute(()->{try{JSONObject q=new JSONObject();q.put("to",activeId);q.put("body",text);HttpResult r=request("POST","/api/messages",q.toString(),auth());h.post(()->{if(r.code>=200&&r.code<300){input.setText("");loadMessages();}else toast(error(r));});}catch(Exception e){h.post(()->toast("فشل الإرسال"));}});}'''
s=replace_method(s,'send',send_method)

start=s.find('private void sendComplaint()')
if start>=0:
    brace=s.find('{',start);depth=0;end=-1
    for i in range(brace,len(s)):
        if s[i]=='{':depth+=1
        elif s[i]=='}':
            depth-=1
            if depth==0:end=i+1;break
    if end>0:
        method='''private void sendComplaint(){base("الشكاوى والتواصل مع المؤسس");TextView info=tv("اكتب شكواك أو رسالتك للمؤسس ثم اضغط إرسال.",18,Color.WHITE);info.setGravity(Gravity.CENTER);EditText box=field("اكتب الشكوى هنا…");box.setSingleLine(false);box.setMinHeight(dp(180));box.setGravity(Gravity.TOP|Gravity.RIGHT);Button send=btn("إرسال الشكوى"),back=btn("رجوع");content.addView(info,lp(-1,-2,12));content.addView(box,lp(-1,190,12));content.addView(send,lp(-1,76,8));content.addView(back,lp(-1,76,8));send.setOnClickListener(v->{click();String msg=box.getText().toString().trim();if(msg.length()<2){toast("اكتب الشكوى أولاً");return;}io.execute(()->{try{JSONObject q=new JSONObject();q.put("message",msg);HttpResult r=request("POST","/api/complaints",q.toString(),auth());h.post(()->toast(r.code>=200&&r.code<300?"تم إرسال الشكوى إلى المؤسس ✓":error(r)));}catch(Exception e){h.post(()->toast("تعذر إرسال الشكوى"));}});});back.setOnClickListener(v->{click();home();});}'''
        s=s[:start]+method+s[end:]

old='private String error(HttpResult r){try{JSONObject o=new JSONObject(r.body);String x=o.optString("error");if(!x.isEmpty())return x;}catch(Exception ignored){}return "HTTP "+r.code;}'
new='''private String error(HttpResult r){try{JSONObject o=new JSONObject(r.body);String x=o.optString("error");if("user_banned".equals(x)){String reason=o.optString("reason");String type=o.optString("ban_type");if("permanent".equals(type))return "تم طردك نهائيًا من تطبيق وَثاق من الإدارة. السبب: "+(reason.isEmpty()?"مخالفة شروط الاستخدام":reason);return "تم حظرك من الإدارة مؤقتًا. السبب: "+(reason.isEmpty()?"مخالفة شروط الاستخدام":reason);}if(!x.isEmpty())return x;}catch(Exception ignored){}return "HTTP "+r.code;}'''
s=s.replace(old,new)
old='if(a!=null&&a.length()>0){JSONObject m=a.optJSONObject(a.length()-1);long id=m.optLong("id");if(lastInboxId>0&&id>lastInboxId){String body=m.optString("body");h.post(()->{toneIncoming();notifyIncoming(body.isEmpty()?"وسائط جديدة":body);});}lastInboxId=Math.max(lastInboxId,id);}'
new='if(a!=null&&a.length()>0){JSONObject m=a.optJSONObject(a.length()-1);long id=m.optLong("id");if(lastInboxId>0&&id>lastInboxId){String body=m.optString("body"),sender=m.optString("sender_name","مستخدم");h.post(()->{toneIncoming();notifyIncoming(sender+"\\n"+(body.isEmpty()?"وسائط جديدة":body));});}lastInboxId=Math.max(lastInboxId,id);}'
s=s.replace(old,new)
p.write_text(s,encoding='utf-8')
print('OK')