from pathlib import Path
MAIN=Path('app/src/main/java/com/wethaq/app/MainActivity.java')
VIDEO=Path('app/src/main/java/com/wethaq/app/VideoCallActivity.java')

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
# video_repair owns startVideoCall; replace that method by a shared audio/video launcher.
if 'private void startAudioCall()' not in s:
    s=replace_method(s,'private void startVideoCall()', '''private void startVideoCall(){startCall(false);} private void startAudioCall(){startCall(true);} private void startCall(boolean audioOnly){if(activeId==null||activeId.trim().isEmpty()){toast("معرف الطرف الآخر غير موجود");return;}try{Intent i=new Intent(this,VideoCallActivity.class);i.putExtra("target",activeId);i.putExtra("name",activeName);i.putExtra("audioOnly",audioOnly);startActivity(i);}catch(Exception e){toast("تعذر فتح المكالمة");}}''')
# Ask Android 13+ for notifications after authenticated entry.
s=s.replace('startPolling();startInboxDelivery();','startPolling();startInboxDelivery();requestNotifications();')
MAIN.write_text(s,encoding='utf-8')

v=VIDEO.read_text(encoding='utf-8')
# video_repair uses this exact state declaration.
if 'private boolean audioOnly;' not in v:
    v=v.replace('private boolean cleaned,offerSent,remoteDescriptionSet;', 'private boolean cleaned,offerSent,remoteDescriptionSet;private boolean audioOnly;')
v=v.replace('myId=getSharedPreferences("wethaq",MODE_PRIVATE).getString("wethaq_id","");', 'myId=getSharedPreferences("wethaq",MODE_PRIVATE).getString("wethaq_id","");audioOnly=getIntent().getBooleanExtra("audioOnly",false);')
v=v.replace('if(Build.VERSION.SDK_INT>=23 && (checkSelfPermission(Manifest.permission.CAMERA)!=PackageManager.PERMISSION_GRANTED || checkSelfPermission(Manifest.permission.RECORD_AUDIO)!=PackageManager.PERMISSION_GRANTED)){requestPermissions(new String[]{Manifest.permission.CAMERA,Manifest.permission.RECORD_AUDIO},500);}else startCall();', 'if(Build.VERSION.SDK_INT>=23){boolean mic=checkSelfPermission(Manifest.permission.RECORD_AUDIO)==PackageManager.PERMISSION_GRANTED;boolean cam=checkSelfPermission(Manifest.permission.CAMERA)==PackageManager.PERMISSION_GRANTED;if((audioOnly&&!mic)||(!audioOnly&&(!mic||!cam))){requestPermissions(audioOnly?new String[]{Manifest.permission.RECORD_AUDIO}:new String[]{Manifest.permission.CAMERA,Manifest.permission.RECORD_AUDIO},500);}else startCall();}else startCall();')
v=v.replace('f.addView(remoteView,new FrameLayout.LayoutParams(-1,-1));\n        FrameLayout.LayoutParams lp=new FrameLayout.LayoutParams(300,400,Gravity.RIGHT|Gravity.TOP);lp.setMargins(0,50,20,0);f.addView(localView,lp);', 'if(!audioOnly){f.addView(remoteView,new FrameLayout.LayoutParams(-1,-1));FrameLayout.LayoutParams lp=new FrameLayout.LayoutParams(300,400,Gravity.RIGHT|Gravity.TOP);lp.setMargins(0,50,20,0);f.addView(localView,lp);}else{TextView t=new TextView(this);t.setText("📞 مكالمة صوتية\\n"+getIntent().getStringExtra("name"));t.setTextColor(Color.WHITE);t.setTextSize(26);t.setGravity(Gravity.CENTER);f.addView(t,new FrameLayout.LayoutParams(-1,-1));}')
v=replace_method(v,'private void startLocal()', '''private void startLocal(){audioSource=factory.createAudioSource(new MediaConstraints());AudioTrack at=factory.createAudioTrack("wethaq_audio",audioSource);List<String> ids=Collections.singletonList("wethaq_stream");peer.addTrack(at,ids);if(!audioOnly){capturer=createCapturer();if(capturer==null)throw new IllegalStateException("camera");videoSource=factory.createVideoSource(false);capturer.initialize(SurfaceTextureHelper.create("WethaqCapture",egl.getEglBaseContext()),this,new CapturerObserver(){public void onCapturerStarted(boolean b){}public void onCaptureFormatChosen(int w,int h,int f){}public void onFrameCaptured(VideoFrame f){}public void onCapturerStopped(){}});capturer.startCapture(640,480,24);VideoTrack vt=factory.createVideoTrack("wethaq_video",videoSource);vt.addSink(localView);peer.addTrack(vt,ids);}}''')
v=v.replace('status.setText("تعذر تشغيل الكاميرا والميكروفون")','status.setText(audioOnly?"تعذر تشغيل الميكروفون":"تعذر تشغيل الكاميرا والميكروفون")')
v=v.replace('if(r==500&&g.length>=2&&g[0]==PackageManager.PERMISSION_GRANTED&&g[1]==PackageManager.PERMISSION_GRANTED)startCall();else {toast("يجب السماح بالكاميرا والميكروفون للمكالمة");finish();}', 'if(r==500){if(audioOnly&&g.length>=1&&g[0]==PackageManager.PERMISSION_GRANTED)startCall();else if(!audioOnly&&g.length>=2&&g[0]==PackageManager.PERMISSION_GRANTED&&g[1]==PackageManager.PERMISSION_GRANTED)startCall();else {toast(audioOnly?"يجب السماح بالميكروفون للمكالمة":"يجب السماح بالكاميرا والميكروفون للمكالمة");finish();}}')
VIDEO.write_text(v,encoding='utf-8')
print('OK')