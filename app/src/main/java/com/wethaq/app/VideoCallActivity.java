package com.wethaq.app;

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
    private String target,token,myId;
    private Handler h=new Handler(Looper.getMainLooper());
    private Runnable poll;
    private boolean started,cleaned,offerSent;

    @Override public void onCreate(Bundle b){
        super.onCreate(b);
        target=getIntent().getStringExtra("target");
        token=getSharedPreferences("wethaq",MODE_PRIVATE).getString("token","");
        myId=getSharedPreferences("wethaq",MODE_PRIVATE).getString("wethaq_id","");
        setContentView(makeUi());
        if(target==null||target.trim().isEmpty()||token.isEmpty()){finish();return;}
        if(Build.VERSION.SDK_INT>=23 && (checkSelfPermission(Manifest.permission.CAMERA)!=PackageManager.PERMISSION_GRANTED || checkSelfPermission(Manifest.permission.RECORD_AUDIO)!=PackageManager.PERMISSION_GRANTED)){
            requestPermissions(new String[]{Manifest.permission.CAMERA,Manifest.permission.RECORD_AUDIO},500);
        }else startCall();
    }

    private View makeUi(){
        FrameLayout f=new FrameLayout(this);f.setBackgroundColor(Color.BLACK);
        remoteView=new SurfaceViewRenderer(this);localView=new SurfaceViewRenderer(this);
        f.addView(remoteView,new FrameLayout.LayoutParams(-1,-1));
        FrameLayout.LayoutParams lp=new FrameLayout.LayoutParams(300,400,Gravity.RIGHT|Gravity.TOP);lp.setMargins(0,30,20,0);f.addView(localView,lp);
        Button end=new Button(this);end.setText("إنهاء المكالمة");end.setTextSize(20);end.setTextColor(Color.WHITE);end.setBackgroundColor(Color.rgb(150,20,20));
        FrameLayout.LayoutParams ep=new FrameLayout.LayoutParams(-1,76,Gravity.BOTTOM);ep.setMargins(20,0,20,30);f.addView(end,ep);end.setOnClickListener(v->endCall());
        return f;
    }

    private void startCall(){
        if(started)return;started=true;
        try{
            egl=EglBase.create();
            localView.init(egl.getEglBaseContext(),null);remoteView.init(egl.getEglBaseContext(),null);localView.setMirror(true);
            PeerConnectionFactory.initialize(PeerConnectionFactory.InitializationOptions.builder(this).createInitializationOptions());
            factory=PeerConnectionFactory.builder().setVideoEncoderFactory(new DefaultVideoEncoderFactory(egl.getEglBaseContext(),true,true)).setVideoDecoderFactory(new DefaultVideoDecoderFactory(egl.getEglBaseContext())).createPeerConnectionFactory();
            createPeer();startLocal();
            poll=()->{pollSignals();h.postDelayed(poll,700);};h.post(poll);
            if(isInitiator())sendOffer();
        }catch(Exception e){endCall();}
    }

    private boolean isInitiator(){return myId!=null&&target!=null&&myId.compareTo(target)<0;}

    private void createPeer(){
        PeerConnection.RTCConfiguration cfg=new PeerConnection.RTCConfiguration(Collections.singletonList(PeerConnection.IceServer.builder("stun:stun.l.google.com:19302").createIceServer()));
        peer=factory.createPeerConnection(cfg,new PeerConnection.Observer(){
            public void onSignalingChange(PeerConnection.SignalingState s){}
            public void onIceConnectionChange(PeerConnection.IceConnectionState s){}
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
    }

    private void startLocal(){
        VideoCapturer cap=createCapturer();
        if(cap==null)throw new IllegalStateException("camera");
        VideoSource vs=factory.createVideoSource(cap.isScreencast());
        cap.initialize(SurfaceTextureHelper.create("WethaqCapture",egl.getEglBaseContext()),this,new CapturerObserver(){public void onCapturerStarted(boolean b){}public void onCaptureFormatChosen(int w,int h,int f){}public void onFrameCaptured(VideoFrame f){}public void onCapturerStopped(){}});
        try{cap.startCapture(640,480,24);}catch(Exception ignored){}
        VideoTrack vt=factory.createVideoTrack("camera",vs);vt.addSink(localView);
        AudioSource as=factory.createAudioSource(new MediaConstraints());AudioTrack at=factory.createAudioTrack("audio",as);
        MediaStream ms=factory.createLocalMediaStream("wethaq_stream");ms.addTrack(vt);ms.addTrack(at);peer.addStream(ms);
    }

    private VideoCapturer createCapturer(){
        Camera2Enumerator e=new Camera2Enumerator(this);
        for(String n:e.getDeviceNames())if(e.isFrontFacing(n))return e.createCapturer(n,null);
        String[] names=e.getDeviceNames();return names.length>0?e.createCapturer(names[0],null):null;
    }

    private void sendOffer(){
        if(offerSent||peer==null)return;offerSent=true;
        peer.createOffer(new SdpObserver(){
            public void onCreateSuccess(SessionDescription d){peer.setLocalDescription(this,d);sendSignal("offer",sdpJson(d));}
            public void onSetSuccess(){}public void onCreateFailure(String s){}public void onSetFailure(String s){}
        },new MediaConstraints());
    }

    private void pollSignals(){new Thread(()->{try{
        HttpURLConnection c=(HttpURLConnection)new URL(API+"/api/calls/signals/"+URLEncoder.encode(target,"UTF-8")).openConnection();
        c.setRequestProperty("Authorization","Bearer "+token);c.setConnectTimeout(6000);c.setReadTimeout(6000);
        InputStream in=c.getInputStream();ByteArrayOutputStream out=new ByteArrayOutputStream();byte[] b=new byte[4096];int n;while((n=in.read(b))!=-1)out.write(b,0,n);in.close();
        JSONArray a=new JSONObject(new String(out.toByteArray(),StandardCharsets.UTF_8)).optJSONArray("signals");
        if(a!=null)for(int i=0;i<a.length();i++){JSONObject x=a.getJSONObject(i);handle(x.optString("type"),x.optString("payload"));}c.disconnect();
    }catch(Exception ignored){}}).start();}

    private void handle(String type,String payload){runOnUiThread(()->{try{
        JSONObject o=new JSONObject(payload);
        if("offer".equals(type)&&!isInitiator()){
            SessionDescription d=new SessionDescription(SessionDescription.Type.OFFER,o.getString("sdp"));
            peer.setRemoteDescription(new SimpleSdp(){public void onSetSuccess(){peer.createAnswer(new SdpObserver(){public void onCreateSuccess(SessionDescription a){peer.setLocalDescription(this,a);sendSignal("answer",sdpJson(a));}public void onSetSuccess(){}public void onCreateFailure(String s){}public void onSetFailure(String s){}},new MediaConstraints());}},d);
        }else if("answer".equals(type)&&isInitiator()){
            peer.setRemoteDescription(new SimpleSdp(),new SessionDescription(SessionDescription.Type.ANSWER,o.getString("sdp")));
        }else if("ice".equals(type)){
            peer.addIceCandidate(new IceCandidate(o.getString("sdpMid"),o.getInt("sdpMLineIndex"),o.getString("candidate")));
        }else if("end".equals(type))endCall();
    }catch(Exception ignored){}});}

    private String sdpJson(SessionDescription d){try{return new JSONObject().put("sdp",d.description).toString();}catch(Exception e){return "{}";}}
    private String candidateJson(IceCandidate c){try{return new JSONObject().put("candidate",c.sdp).put("sdpMid",c.sdpMid).put("sdpMLineIndex",c.sdpMLineIndex).toString();}catch(Exception e){return "{}";}}

    private void sendSignal(String type,String payload){new Thread(()->{try{
        JSONObject q=new JSONObject().put("to",target).put("type",type).put("payload",payload);
        HttpURLConnection c=(HttpURLConnection)new URL(API+"/api/calls/signal").openConnection();c.setRequestMethod("POST");c.setDoOutput(true);c.setRequestProperty("Authorization","Bearer "+token);c.setRequestProperty("Content-Type","application/json");
        byte[] z=q.toString().getBytes(StandardCharsets.UTF_8);try(OutputStream o=c.getOutputStream()){o.write(z);}c.getResponseCode();c.disconnect();
    }catch(Exception ignored){}}).start();}

    private void endCall(){if(cleaned)return;cleaned=true;if(target!=null)sendSignal("end","{}");if(poll!=null)h.removeCallbacks(poll);if(peer!=null)peer.close();if(factory!=null)factory.dispose();if(localView!=null)localView.release();if(remoteView!=null)remoteView.release();if(egl!=null)egl.release();finish();}
    private static class SimpleSdp implements SdpObserver{public void onCreateSuccess(SessionDescription d){}public void onSetSuccess(){}public void onCreateFailure(String s){}public void onSetFailure(String s){}}
    @Override public void onRequestPermissionsResult(int r,String[] p,int[] g){super.onRequestPermissionsResult(r,p,g);if(r==500&&g.length>=2&&g[0]==PackageManager.PERMISSION_GRANTED&&g[1]==PackageManager.PERMISSION_GRANTED)startCall();else finish();}
    @Override protected void onDestroy(){if(!cleaned)endCall();super.onDestroy();}
}
