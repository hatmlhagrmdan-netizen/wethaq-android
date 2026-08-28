from pathlib import Path

VIDEO='app/src/main/java/com/wethaq/app/VideoCallActivity.java'
MAIN='app/src/main/java/com/wethaq/app/MainActivity.java'

video=r'''package com.wethaq.app;

import android.Manifest;
import android.app.Activity;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.os.*;
import android.view.*;
import android.widget.*;
import org.json.*;
import org.webrtc.*;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

public final class VideoCallActivity extends Activity {
    private static final String API="https://wethaq-backend-production.up.railway.app";
    private PeerConnectionFactory factory;
    private PeerConnection peer;
    private EglBase egl;
    private SurfaceViewRenderer localView,remoteView;
    private VideoCapturer capturer;
    private VideoSource videoSource;
    private AudioSource audioSource;
    private String target,token,myId;
    private final Handler h=new Handler(Looper.getMainLooper());
    private Runnable poll;
    private boolean cleaned,offerSent,remoteDescriptionSet;
    private final List<IceCandidate> pendingCandidates=new ArrayList<>();
    private TextView status;

    @Override public void onCreate(Bundle b){
        super.onCreate(b);
        target=getIntent().getStringExtra("target");
        token=getSharedPreferences("wethaq",MODE_PRIVATE).getString("token","");
        myId=getSharedPreferences("wethaq",MODE_PRIVATE).getString("wethaq_id","");
        setContentView(makeUi());
        if(target==null||target.trim().isEmpty()||token.isEmpty()){toast("تعذر بدء المكالمة: هوية الطرف الآخر غير موجودة");finish();return;}
        if(Build.VERSION.SDK_INT>=23 && (checkSelfPermission(Manifest.permission.CAMERA)!=PackageManager.PERMISSION_GRANTED || checkSelfPermission(Manifest.permission.RECORD_AUDIO)!=PackageManager.PERMISSION_GRANTED)){
            requestPermissions(new String[]{Manifest.permission.CAMERA,Manifest.permission.RECORD_AUDIO},500);
        }else startCall();
    }

    private View makeUi(){
        FrameLayout f=new FrameLayout(this);f.setBackgroundColor(Color.BLACK);
        remoteView=new SurfaceViewRenderer(this);localView=new SurfaceViewRenderer(this);
        f.addView(remoteView,new FrameLayout.LayoutParams(-1,-1));
        FrameLayout.LayoutParams lp=new FrameLayout.LayoutParams(300,400,Gravity.RIGHT|Gravity.TOP);lp.setMargins(0,50,20,0);f.addView(localView,lp);
        status=new TextView(this);status.setText("جاري الاتصال…");status.setTextColor(Color.WHITE);status.setTextSize(18);status.setGravity(Gravity.CENTER);status.setBackgroundColor(0x99000000);
        FrameLayout.LayoutParams sp=new FrameLayout.LayoutParams(-1,60,Gravity.TOP);sp.setMargins(20,0,20,0);f.addView(status,sp);
        Button end=new Button(this);end.setText("إنهاء المكالمة");end.setTextSize(20);end.setTextColor(Color.WHITE);end.setBackgroundColor(Color.rgb(150,20,20));
        FrameLayout.LayoutParams ep=new FrameLayout.LayoutParams(-1,76,Gravity.BOTTOM);ep.setMargins(20,0,20,30);f.addView(end,ep);end.setOnClickListener(v->endCall());
        return f;
    }

    private void startCall(){
        try{
            egl=EglBase.create();
            localView.init(egl.getEglBaseContext(),null);remoteView.init(egl.getEglBaseContext(),null);localView.setMirror(true);
            PeerConnectionFactory.initialize(PeerConnectionFactory.InitializationOptions.builder(this).createInitializationOptions());
            factory=PeerConnectionFactory.builder()
                .setVideoEncoderFactory(new DefaultVideoEncoderFactory(egl.getEglBaseContext(),true,true))
                .setVideoDecoderFactory(new DefaultVideoDecoderFactory(egl.getEglBaseContext()))
                .createPeerConnectionFactory();
            createPeer();
            startLocal();
            poll=()->{pollSignals();h.postDelayed(poll,500);};
            h.post(poll);
            if(isInitiator())sendOffer();
            status.setText(isInitiator()?"جاري الاتصال بالطرف الآخر…":"بانتظار اتصال الطرف الآخر…");
        }catch(Exception e){status.setText("تعذر تشغيل الكاميرا والميكروفون");new Handler(Looper.getMainLooper()).postDelayed(this::endCall,1200);}
    }

    private boolean isInitiator(){return myId!=null&&target!=null&&myId.compareTo(target)<0;}

    private void createPeer(){
        List<PeerConnection.IceServer> servers=new ArrayList<>();
        servers.add(PeerConnection.IceServer.builder("stun:stun.l.google.com:19302").createIceServer());
        servers.add(PeerConnection.IceServer.builder("stun:stun1.l.google.com:19302").createIceServer());
        servers.add(PeerConnection.IceServer.builder("turn:openrelay.metered.ca:80").setUsername("openrelayproject").setPassword("openrelayproject").createIceServer());
        servers.add(PeerConnection.IceServer.builder("turn:openrelay.metered.ca:443?transport=tcp").setUsername("openrelayproject").setPassword("openrelayproject").createIceServer());
        peer=factory.createPeerConnection(new PeerConnection.RTCConfiguration(servers),new PeerConnection.Observer(){
            public void onSignalingChange(PeerConnection.SignalingState s){}
            public void onIceConnectionChange(PeerConnection.IceConnectionState s){runOnUiThread(()->{if(s==PeerConnection.IceConnectionState.CONNECTED||s==PeerConnection.IceConnectionState.COMPLETED)status.setText("تم الاتصال ✓");else if(s==PeerConnection.IceConnectionState.FAILED)status.setText("فشل الاتصال بالطرف الآخر");});}
            public void onIceConnectionReceivingChange(boolean b){}
            public void onIceGatheringChange(PeerConnection.IceGatheringState s){}
            public void onIceCandidate(IceCandidate c){sendSignal("ice",candidateJson(c));}
            public void onIceCandidatesRemoved(IceCandidate[] c){}
            public void onAddStream(MediaStream s){if(!s.videoTracks.isEmpty())s.videoTracks.get(0).addSink(remoteView);}
            public void onRemoveStream(MediaStream s){}
            public void onDataChannel(DataChannel d){}
            public void onRenegotiationNeeded(){}
            public void onAddTrack(RtpReceiver r,MediaStream[] s){if(r.track() instanceof VideoTrack)((VideoTrack)r.track()).addSink(remoteView);}
        });
        if(peer==null)throw new IllegalStateException("peer");
    }

    private void startLocal(){
        capturer=createCapturer();
        if(capturer==null)throw new IllegalStateException("camera");
        videoSource=factory.createVideoSource(false);
        capturer.initialize(SurfaceTextureHelper.create("WethaqCapture",egl.getEglBaseContext()),this,new CapturerObserver(){public void onCapturerStarted(boolean b){}public void onCaptureFormatChosen(int w,int h,int f){}public void onFrameCaptured(VideoFrame f){}public void onCapturerStopped(){}});
        capturer.startCapture(640,480,24);
        VideoTrack vt=factory.createVideoTrack("wethaq_video",videoSource);vt.addSink(localView);
        audioSource=factory.createAudioSource(new MediaConstraints());
        AudioTrack at=factory.createAudioTrack("wethaq_audio",audioSource);
        List<String> streamIds=Collections.singletonList("wethaq_stream");
        peer.addTrack(vt,streamIds);peer.addTrack(at,streamIds);
    }

    private VideoCapturer createCapturer(){
        Camera2Enumerator e=new Camera2Enumerator(this);
        for(String n:e.getDeviceNames())if(e.isFrontFacing(n))return e.createCapturer(n,null);
        for(String n:e.getDeviceNames())return e.createCapturer(n,null);
        return null;
    }

    private void sendOffer(){
        if(offerSent||peer==null)return;offerSent=true;
        peer.createOffer(new SdpObserver(){
            public void onCreateSuccess(SessionDescription d){peer.setLocalDescription(new SimpleSdp(){public void onSetSuccess(){sendSignal("offer",sdpJson(d));}},d);}
            public void onSetSuccess(){}public void onCreateFailure(String s){status.setText("فشل إنشاء الاتصال");}public void onSetFailure(String s){}
        },new MediaConstraints());
    }

    private void pollSignals(){new Thread(()->{try{
        HttpURLConnection c=(HttpURLConnection)new URL(API+"/api/calls/signals/"+URLEncoder.encode(target,"UTF-8")).openConnection();
        c.setRequestProperty("Authorization","Bearer "+token);c.setConnectTimeout(8000);c.setReadTimeout(8000);
        int code=c.getResponseCode();if(code!=200){c.disconnect();return;}
        InputStream in=c.getInputStream();ByteArrayOutputStream out=new ByteArrayOutputStream();byte[] b=new byte[4096];int n;while((n=in.read(b))!=-1)out.write(b,0,n);in.close();
        JSONArray a=new JSONObject(new String(out.toByteArray(),StandardCharsets.UTF_8)).optJSONArray("signals");
        if(a!=null)for(int i=0;i<a.length();i++){JSONObject x=a.getJSONObject(i);handle(x.optString("type"),x.optString("payload"));}c.disconnect();
    }catch(Exception ignored){}}).start();}

    private void handle(String type,String payload){runOnUiThread(()->{try{
        JSONObject o=new JSONObject(payload);
        if("offer".equals(type)&&!isInitiator()&&peer!=null&&!remoteDescriptionSet){
            SessionDescription d=new SessionDescription(SessionDescription.Type.OFFER,o.getString("sdp"));
            peer.setRemoteDescription(new SimpleSdp(){public void onSetSuccess(){remoteDescriptionSet=true;flushIce();peer.createAnswer(new SdpObserver(){public void onCreateSuccess(SessionDescription a){peer.setLocalDescription(new SimpleSdp(){public void onSetSuccess(){sendSignal("answer",sdpJson(a));}},a);}public void onSetSuccess(){}public void onCreateFailure(String s){}public void onSetFailure(String s){}},new MediaConstraints());}},d);
        }else if("answer".equals(type)&&isInitiator()&&peer!=null&&!remoteDescriptionSet){
            peer.setRemoteDescription(new SimpleSdp(){public void onSetSuccess(){remoteDescriptionSet=true;flushIce();}},new SessionDescription(SessionDescription.Type.ANSWER,o.getString("sdp")));
        }else if("ice".equals(type)&&peer!=null){
            IceCandidate ic=new IceCandidate(o.getString("sdpMid"),o.getInt("sdpMLineIndex"),o.getString("candidate"));if(remoteDescriptionSet)peer.addIceCandidate(ic);else pendingCandidates.add(ic);
        }else if("end".equals(type))endCall();
    }catch(Exception ignored){}});}

    private void flushIce(){for(IceCandidate c:pendingCandidates)peer.addIceCandidate(c);pendingCandidates.clear();}
    private String sdpJson(SessionDescription d){try{return new JSONObject().put("sdp",d.description).toString();}catch(Exception e){return "{}";}}
    private String candidateJson(IceCandidate c){try{return new JSONObject().put("candidate",c.sdp).put("sdpMid",c.sdpMid).put("sdpMLineIndex",c.sdpMLineIndex).toString();}catch(Exception e){return "{}";}}

    private void sendSignal(String type,String payload){new Thread(()->{try{
        JSONObject q=new JSONObject().put("to",target).put("type",type).put("payload",payload);
        HttpURLConnection c=(HttpURLConnection)new URL(API+"/api/calls/signal").openConnection();c.setRequestMethod("POST");c.setDoOutput(true);c.setConnectTimeout(8000);c.setReadTimeout(8000);c.setRequestProperty("Authorization","Bearer "+token);c.setRequestProperty("Content-Type","application/json");
        byte[] z=q.toString().getBytes(StandardCharsets.UTF_8);try(OutputStream o=c.getOutputStream()){o.write(z);}c.getResponseCode();c.disconnect();
    }catch(Exception ignored){runOnUiThread(()->status.setText("تعذر إرسال إشارة الاتصال"));}}).start();}

    private void endCall(){if(cleaned)return;cleaned=true;if(target!=null&&!token.isEmpty())sendSignal("end","{}");if(poll!=null)h.removeCallbacks(poll);try{if(capturer!=null)capturer.stopCapture();}catch(Exception ignored){}if(peer!=null)peer.close();if(factory!=null)factory.dispose();if(videoSource!=null)videoSource.dispose();if(audioSource!=null)audioSource.dispose();if(localView!=null)localView.release();if(remoteView!=null)remoteView.release();if(egl!=null)egl.release();finish();}
    private void toast(String s){Toast.makeText(this,s,Toast.LENGTH_LONG).show();}
    private static class SimpleSdp implements SdpObserver{public void onCreateSuccess(SessionDescription d){}public void onSetSuccess(){}public void onCreateFailure(String s){}public void onSetFailure(String s){}}
    @Override public void onRequestPermissionsResult(int r,String[] p,int[] g){super.onRequestPermissionsResult(r,p,g);if(r==500&&g.length>=2&&g[0]==PackageManager.PERMISSION_GRANTED&&g[1]==PackageManager.PERMISSION_GRANTED)startCall();else {toast("يجب السماح بالكاميرا والميكروفون للمكالمة");finish();}}
    @Override protected void onDestroy(){if(!cleaned)endCall();super.onDestroy();}
}
'''

Path(VIDEO).write_text(video,encoding='utf-8')

# Guarantee the conversation button passes the actual selected Wethaq ID.
p=Path(MAIN)
s=p.read_text(encoding='utf-8')
marker='private void startVideoCall()'
start=s.find(marker)
if start>=0:
    brace=s.find('{',start);depth=0;end=-1
    for i in range(brace,len(s)):
        if s[i]=='{': depth+=1
        elif s[i]=='}':
            depth-=1
            if depth==0: end=i+1; break
    if end>0:
        method='''private void startVideoCall(){if(activeId==null||activeId.trim().isEmpty()){toast("لا يمكن بدء المكالمة: معرف الطرف الآخر غير موجود");return;}try{Intent i=new Intent(this,VideoCallActivity.class);i.putExtra("target",activeId);startActivity(i);}catch(Exception e){toast("تعذر فتح مكالمة الفيديو");}}'''
        s=s[:start]+method+s[end:]
p.write_text(s,encoding='utf-8')
print('OK')