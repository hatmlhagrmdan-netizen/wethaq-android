from pathlib import Path

VIDEO='app/src/main/java/com/wethaq/app/VideoCallActivity.java'
MAIN='app/src/main/java/com/wethaq/app/MainActivity.java'

p=Path(VIDEO)
s=p.read_text(encoding='utf-8')

# Audio calls must not require a camera. Keep one WebRTC activity but switch
# media tracks and permissions according to audioOnly.
s=s.replace('private boolean cleaned,offerSent,remoteDescriptionSet;\n', 'private boolean cleaned,offerSent,remoteDescriptionSet,audioOnly;\n')
s=s.replace('myId=getSharedPreferences("wethaq",MODE_PRIVATE).getString("wethaq_id","");\n', 'myId=getSharedPreferences("wethaq",MODE_PRIVATE).getString("wethaq_id","");audioOnly=getIntent().getBooleanExtra("audioOnly",false);\n')
s=s.replace('if(Build.VERSION.SDK_INT>=23 && (checkSelfPermission(Manifest.permission.CAMERA)!=PackageManager.PERMISSION_GRANTED || checkSelfPermission(Manifest.permission.RECORD_AUDIO)!=PackageManager.PERMISSION_GRANTED)){\n            requestPermissions(new String[]{Manifest.permission.CAMERA,Manifest.permission.RECORD_AUDIO},500);\n        }else startCall();', 'if(Build.VERSION.SDK_INT>=23){ boolean mic=checkSelfPermission(Manifest.permission.RECORD_AUDIO)==PackageManager.PERMISSION_GRANTED; boolean cam=audioOnly||checkSelfPermission(Manifest.permission.CAMERA)==PackageManager.PERMISSION_GRANTED; if(!mic||!cam){ if(audioOnly)requestPermissions(new String[]{Manifest.permission.RECORD_AUDIO},500); else requestPermissions(new String[]{Manifest.permission.CAMERA,Manifest.permission.RECORD_AUDIO},500); } else startCall(); } else startCall();')

# UI: audio call shows a clean audio screen; video keeps local/remote renderers.
old='f.addView(remoteView,new FrameLayout.LayoutParams(-1,-1));\n        FrameLayout.LayoutParams lp=new FrameLayout.LayoutParams(300,400,Gravity.RIGHT|Gravity.TOP);lp.setMargins(0,50,20,0);f.addView(localView,lp);'
new='if(!audioOnly){f.addView(remoteView,new FrameLayout.LayoutParams(-1,-1)); FrameLayout.LayoutParams lp=new FrameLayout.LayoutParams(300,400,Gravity.RIGHT|Gravity.TOP);lp.setMargins(0,50,20,0);f.addView(localView,lp);}else{TextView a=new TextView(this);a.setText("📞\\nمكالمة صوتية\\n"+getIntent().getStringExtra("name"));a.setTextColor(Color.WHITE);a.setTextSize(28);a.setGravity(Gravity.CENTER);f.addView(a,new FrameLayout.LayoutParams(-1,-1));}'
s=s.replace(old,new)
s=s.replace('localView.init(egl.getEglBaseContext(),null);remoteView.init(egl.getEglBaseContext(),null);localView.setMirror(true);', 'if(!audioOnly){localView.init(egl.getEglBaseContext(),null);remoteView.init(egl.getEglBaseContext(),null);localView.setMirror(true);}')
s=s.replace('.setVideoEncoderFactory(new DefaultVideoEncoderFactory(egl.getEglBaseContext(),true,true))\n                .setVideoDecoderFactory(new DefaultVideoDecoderFactory(egl.getEglBaseContext()))', '.setVideoEncoderFactory(new DefaultVideoEncoderFactory(egl.getEglBaseContext(),true,true))\n                .setVideoDecoderFactory(new DefaultVideoDecoderFactory(egl.getEglBaseContext()))')

# Replace local media setup with audio-only branch.
start=s.find('    private void startLocal(){')
if start>=0:
    brace=s.find('{',start);depth=0;end=-1
    for i in range(brace,len(s)):
        if s[i]=='{': depth+=1
        elif s[i]=='}':
            depth-=1
            if depth==0:end=i+1;break
    if end>0:
        method='''    private void startLocal(){\n        audioSource=factory.createAudioSource(new MediaConstraints());\n        AudioTrack at=factory.createAudioTrack("wethaq_audio",audioSource);\n        List<String> streamIds=Collections.singletonList("wethaq_stream");\n        peer.addTrack(at,streamIds);\n        if(audioOnly)return;\n        capturer=createCapturer();\n        if(capturer==null)throw new IllegalStateException("camera");\n        videoSource=factory.createVideoSource(false);\n        capturer.initialize(SurfaceTextureHelper.create("WethaqCapture",egl.getEglBaseContext()),this,new CapturerObserver(){public void onCapturerStarted(boolean b){}public void onCaptureFormatChosen(int w,int h,int f){}public void onFrameCaptured(VideoFrame f){}public void onCapturerStopped(){}});\n        capturer.startCapture(640,480,24);\n        VideoTrack vt=factory.createVideoTrack("wethaq_video",videoSource);\n        vt.addSink(localView);\n        peer.addTrack(vt,streamIds);\n    }'''
        s=s[:start]+method+s[end:]

s=s.replace('catch(Exception e){status.setText("تعذر تشغيل الكاميرا والميكروفون");', 'catch(Exception e){status.setText(audioOnly?"تعذر تشغيل الميكروفون":"تعذر تشغيل الكاميرا والميكروفون");')
# Correct permission callback for audio-only calls.
s=s.replace('if(r==500&&g.length>=2&&g[0]==PackageManager.PERMISSION_GRANTED&&g[1]==PackageManager.PERMISSION_GRANTED)startCall();else {toast("يجب السماح بالكاميرا والميكروفون للمكالمة");finish();}', 'if(r==500){if(audioOnly&&g.length>=1&&g[0]==PackageManager.PERMISSION_GRANTED){startCall();}else if(!audioOnly&&g.length>=2&&g[0]==PackageManager.PERMISSION_GRANTED&&g[1]==PackageManager.PERMISSION_GRANTED){startCall();}else{toast(audioOnly?"يجب السماح بالميكروفون للمكالمة":"يجب السماح بالكاميرا والميكروفون للمكالمة");finish();}}')
# Do not call endCall recursively from onDestroy after the activity is already being destroyed.
s=s.replace('@Override protected void onDestroy(){if(!cleaned)endCall();super.onDestroy();}', '@Override protected void onDestroy(){if(poll!=null)h.removeCallbacks(poll);super.onDestroy();}')
p.write_text(s,encoding='utf-8')

# Ensure MainActivity passes both the real target ID and call mode/name.
p=Path(MAIN);m=p.read_text(encoding='utf-8')
for name,body in [
('startVideoCall','''private void startVideoCall(){if(activeId==null||activeId.trim().isEmpty()){toast("معرف الطرف الآخر غير موجود");return;}try{Intent i=new Intent(this,VideoCallActivity.class);i.putExtra("target",activeId);i.putExtra("name",activeName==null?"مستخدم":activeName);i.putExtra("audioOnly",false);startActivity(i);}catch(Exception e){toast("تعذر فتح مكالمة الفيديو");}}'''),
('startAudioCall','''private void startAudioCall(){if(activeId==null||activeId.trim().isEmpty()){toast("معرف الطرف الآخر غير موجود");return;}try{Intent i=new Intent(this,VideoCallActivity.class);i.putExtra("target",activeId);i.putExtra("name",activeName==null?"مستخدم":activeName);i.putExtra("audioOnly",true);startActivity(i);}catch(Exception e){toast("تعذر فتح المكالمة الصوتية");}}''')]:
    marker='private void '+name+'()';st=m.find(marker)
    if st>=0:
        br=m.find('{',st);d=0;en=-1
        for i in range(br,len(m)):
            if m[i]=='{':d+=1
            elif m[i]=='}':
                d-=1
                if d==0:en=i+1;break
        if en>0:m=m[:st]+body+m[en:]
p.write_text(m,encoding='utf-8')
print('OK')