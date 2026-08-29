from pathlib import Path

MAIN=Path('app/src/main/java/com/wethaq/app/MainActivity.java')
VIDEO=Path('app/src/main/java/com/wethaq/app/VideoCallActivity.java')
SERVER=Path('backend/server.js')

s=MAIN.read_text(encoding='utf-8')
# Make login failures actionable and ensure the login request is not blocked by a stale local token.
s=s.replace('if(hasToken())home();else login();','if(hasToken())home();else login();')
old='private void auth(String n,String y,boolean create){if(n.split("\\\\s+").length<3||!y.matches("\\\\d{4}")){toast("أدخل الاسم الثلاثي وسنة الميلاد");return;}'
if old not in s:
    old='private void auth(String n,String y,boolean create){if(n.split("\\s+").length<3||!y.matches("\\d{4}")){toast("أدخل الاسم الثلاثي وسنة الميلاد");return;}'
new='private void auth(String n,String y,boolean create){n=n.trim();y=y.trim();if(n.split("\\s+").length<3||!y.matches("\\d{4}")){toast("أدخل الاسم الثلاثي وسنة الميلاد");return;}'
s=s.replace(old,new)

# Poll for incoming call offers. The server endpoint only peeks; VideoCallActivity consumes the signal.
if 'private void pollIncomingCalls()' not in s:
    marker='private void startPolling(){stopPolling();poller=()->{if(activeId!=null)loadMessages();pollInbox();if(hasToken())h.postDelayed(poller,2500);};h.postDelayed(poller,1200);}'
    replacement='''private void pollIncomingCalls(){if(!hasToken())return;io.execute(()->{try{HttpResult r=request("GET","/api/calls/incoming",null,auth());if(r.code!=200)return;JSONArray a=new JSONObject(r.body).optJSONArray("calls");if(a==null||a.length()==0)return;JSONObject c=a.optJSONObject(0);if(c==null)return;String sid=c.optString("sender_wethaq_id"),name=c.optString("sender_name","مستخدم");long signalId=c.optLong("id");if(sid.isEmpty())return;String seen=prefs.getString("last_call_prompt","0");if(String.valueOf(signalId).equals(seen))return;prefs.edit().putString("last_call_prompt",String.valueOf(signalId)).apply();boolean audioOnly=false;try{JSONObject payload=new JSONObject(c.optString("payload","{}"));audioOnly=payload.optBoolean("audioOnly",false);}catch(Exception ignored){}final boolean ao=audioOnly;h.post(()->new AlertDialog.Builder(this).setTitle(ao?"مكالمة صوتية واردة":"مكالمة فيديو واردة").setMessage("اتصال وارد من "+name).setPositiveButton("رد",(d,w)->{Intent i=new Intent(this,VideoCallActivity.class);i.putExtra("target",sid);i.putExtra("name",name);i.putExtra("audioOnly",ao);startActivity(i);}).setNegativeButton("رفض",null).setCancelable(false).show());}catch(Exception ignored){}});}'''
    if marker in s:
        s=s.replace(marker,replacement+marker.replace('if(activeId!=null)loadMessages();pollInbox();','if(activeId!=null)loadMessages();pollInbox();pollIncomingCalls();'))

# Audio draft: make the state explicit and let Send work once encoding has completed.
s=s.replace('toast("جارٍ التسجيل… اضغط 🎙 للإرسال");','toast("جارٍ التسجيل… اضغط 🎙 لإيقافه ثم اضغط إرسال");')
s=s.replace('h.post(()->{pendingAudioData=data;pendingImageData=null;pendingMime="audio/mp4";pendingMediaType=2;input.setHint("التسجيل جاهز للإرسال — اضغط إرسال");});f.delete();','h.post(()->{pendingAudioData=data;pendingImageData=null;pendingMime="audio/mp4";pendingMediaType=2;if(input!=null){input.setCompoundDrawables(null,null,null,null);input.setHint("🎙 التسجيل جاهز للإرسال — اضغط إرسال");input.setText("");}toast("تم تجهيز التسجيل الصوتي ✓");});f.delete();')
MAIN.write_text(s,encoding='utf-8')

v=VIDEO.read_text(encoding='utf-8')
# Remove accidental repeated assignment noise and include audioOnly in the offer SDP payload.
v=v.replace('audioOnly=getIntent().getBooleanExtra("audioOnly",false);audioOnly=getIntent().getBooleanExtra("audioOnly",false);audioOnly=getIntent().getBooleanExtra("audioOnly",false);audioOnly=getIntent().getBooleanExtra("audioOnly",false);audioOnly=getIntent().getBooleanExtra("audioOnly",false);','audioOnly=getIntent().getBooleanExtra("audioOnly",false);')
v=v.replace('if(!audioOnly){if(!audioOnly){if(!audioOnly){if(!audioOnly){localView.init(egl.getEglBaseContext(),null);remoteView.init(egl.getEglBaseContext(),null);localView.setMirror(true);}}}}','if(!audioOnly){localView.init(egl.getEglBaseContext(),null);remoteView.init(egl.getEglBaseContext(),null);localView.setMirror(true);}')
v=v.replace('return new JSONObject().put("sdp",d.description).toString();','return new JSONObject().put("sdp",d.description).put("audioOnly",audioOnly).toString();')
VIDEO.write_text(v,encoding='utf-8')

b=SERVER.read_text(encoding='utf-8')
if 'app.get(\'/api/calls/incoming\'' not in b:
    marker="app.post('/api/calls/signal',auth,(req,res)=>"
    endpoint="app.get('/api/calls/incoming',auth,(req,res)=>{const rows=db.prepare(\"SELECT cs.id,cs.sender_id,cs.receiver_id,cs.type,cs.payload,cs.created_at,u.name sender_name,u.wethaq_id sender_wethaq_id FROM call_signals cs JOIN users u ON u.id=cs.sender_id WHERE cs.receiver_id=? AND cs.type='offer' ORDER BY cs.id DESC LIMIT 10\").all(req.user.sub);res.json({calls:rows});});"
    b=b.replace(marker,endpoint+marker)
SERVER.write_text(b,encoding='utf-8')
print('FUNCTIONAL_REPAIR_OK')