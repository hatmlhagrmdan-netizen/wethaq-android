package com.wethaq.app;

import android.Manifest;
import android.app.Activity;
import android.content.Context;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.media.AudioManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;
import org.webrtc.*;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.*;

public final class VideoCallActivity extends Activity {
    private static final String API="https://wethaq-backend-production.up.railway.app";
    private static final int PERM_CALL=500;
    private final Handler handler=new Handler(Looper.getMainLooper());
    private final Set<String> seenSignals=new HashSet<>();
    private final List<IceCandidate> pendingCandidates=new ArrayList<>();
    private final ScheduledExecutorService callIo=Executors.newSingleThreadScheduledExecutor();
    private PeerConnectionFactory factory;
    private PeerConnection peer;
    private EglBase egl;
    private SurfaceViewRenderer localView,remoteView;
    private VideoCapturer capturer;
    private VideoSource videoSource;
    private AudioSource audioSource;
    private AudioTrack localAudioTrack;
    private VideoTrack localVideoTrack;
    private AudioManager audioManager;
    private boolean previousSpeaker;
    private String target,token,myId,incomingOffer;
    private boolean audioOnly,cleaned,offerSent,remoteDescriptionSet,accepted,micMuted=false;
    private TextView status;
    private Button muteButton,speakerButton,endButton;

    @Override public void onCreate(Bundle state){
        super.onCreate(state);
        target=getIntent().getStringExtra("target");
        token=getSharedPreferences("wethaq",MODE_PRIVATE).getString("token","");
        myId=getSharedPreferences("wethaq",MODE_PRIVATE).getString("wethaq_id","");
        audioOnly=getIntent().getBooleanExtra("audioOnly",false);
        incomingOffer=getIntent().getStringExtra("incomingOffer");
        accepted=incomingOffer==null||incomingOffer.trim().isEmpty();
        if(target==null||target.trim().isEmpty()||token.isEmpty()||myId.isEmpty()){setContentView(simpleMessage("تعذر بدء المكالمة",true));return;}
        if(!accepted){setContentView(incomingUi());return;}
        prepareAndStart();
    }

    private int dp(int n){return(int)(n*getResources().getDisplayMetrics().density+.5f);}
    private Button actionButton(String text){Button b=new Button(this);b.setText(text);b.setTextColor(Color.WHITE);b.setTextSize(16);b.setAllCaps(false);b.setMinHeight(dp(60));return b;}
    private TextView simpleMessage(String text,boolean finishable){FrameLayout root=new FrameLayout(this);root.setBackgroundColor(Color.BLACK);TextView t=new TextView(this);t.setText(text);t.setTextColor(Color.WHITE);t.setTextSize(23);t.setGravity(Gravity.CENTER);root.addView(t,new FrameLayout.LayoutParams(-1,-2,Gravity.CENTER));if(finishable){Button b=actionButton("رجوع");FrameLayout.LayoutParams p=new FrameLayout.LayoutParams(-1,dp(65),Gravity.BOTTOM);p.setMargins(dp(20),0,dp(20),dp(22));root.addView(b,p);b.setOnClickListener(v->finish());}return t;}
    private View incomingUi(){LinearLayout root=new LinearLayout(this);root.setOrientation(LinearLayout.VERTICAL);root.setGravity(Gravity.CENTER);root.setPadding(dp(24),dp(24),dp(24),dp(24));root.setBackgroundColor(Color.BLACK);TextView title=new TextView(this);title.setText((audioOnly?"📞 مكالمة صوتية واردة":"📹 مكالمة فيديو واردة"));title.setTextColor(Color.WHITE);title.setTextSize(28);title.setGravity(Gravity.CENTER);TextView name=new TextView(this);name.setText("من: "+String.valueOf(getIntent().getStringExtra("name")));name.setTextColor(Color.WHITE);name.setTextSize(21);name.setGravity(Gravity.CENTER);LinearLayout buttons=new LinearLayout(this);buttons.setOrientation(LinearLayout.HORIZONTAL);buttons.setGravity(Gravity.CENTER);Button reject=actionButton("رفض");Button accept=actionButton("قبول");buttons.addView(reject,new LinearLayout.LayoutParams(0,dp(72),1));buttons.addView(accept,new LinearLayout.LayoutParams(0,dp(72),1));root.addView(title,new LinearLayout.LayoutParams(-1,dp(90)));root.addView(name,new LinearLayout.LayoutParams(-1,dp(80)));root.addView(buttons,new LinearLayout.LayoutParams(-1,dp(86)));reject.setOnClickListener(v->{sendSignal("end","{}");finish();});accept.setOnClickListener(v->{accepted=true;prepareAndStart();});return root;}

    private void prepareAndStart(){
        if(Build.VERSION.SDK_INT>=23){boolean mic=checkSelfPermission(Manifest.permission.RECORD_AUDIO)==PackageManager.PERMISSION_GRANTED;boolean cam=audioOnly||checkSelfPermission(Manifest.permission.CAMERA)==PackageManager.PERMISSION_GRANTED;if(!mic||!cam){requestPermissions(audioOnly?new String[]{Manifest.permission.RECORD_AUDIO}:new String[]{Manifest.permission.CAMERA,Manifest.permission.RECORD_AUDIO},PERM_CALL);return;}}
        setContentView(makeUi());startCall();
    }

    private View makeUi(){
        FrameLayout root=new FrameLayout(this);root.setBackgroundColor(Color.BLACK);
        if(!audioOnly){remoteView=new SurfaceViewRenderer(this);localView=new SurfaceViewRenderer(this);root.addView(remoteView,new FrameLayout.LayoutParams(-1,-1));FrameLayout.LayoutParams lp=new FrameLayout.LayoutParams(dp(120),dp(180),Gravity.RIGHT|Gravity.TOP);lp.setMargins(0,dp(24),dp(16),0);root.addView(localView,lp);}else{TextView call=new TextView(this);call.setText("📞\nمكالمة صوتية\n"+String.valueOf(getIntent().getStringExtra("name")));call.setTextColor(Color.WHITE);call.setTextSize(25);call.setGravity(Gravity.CENTER);root.addView(call,new FrameLayout.LayoutParams(-1,-1));}
        status=new TextView(this);status.setText("جاري الاتصال…");status.setTextColor(Color.WHITE);status.setTextSize(18);status.setGravity(Gravity.CENTER);root.addView(status,new FrameLayout.LayoutParams(-1,dp(64),Gravity.TOP));
        LinearLayout controls=new LinearLayout(this);controls.setOrientation(LinearLayout.HORIZONTAL);controls.setGravity(Gravity.CENTER);muteButton=actionButton("🎙 كتم");speakerButton=actionButton("🔊 سماعة");endButton=actionButton("⛔ إنهاء");controls.addView(muteButton,new LinearLayout.LayoutParams(0,dp(72),1));controls.addView(speakerButton,new LinearLayout.LayoutParams(0,dp(72),1));controls.addView(endButton,new LinearLayout.LayoutParams(0,dp(72),1));FrameLayout.LayoutParams cp=new FrameLayout.LayoutParams(-1,dp(82),Gravity.BOTTOM);cp.setMargins(dp(10),0,dp(10),dp(18));root.addView(controls,cp);
        muteButton.setOnClickListener(v->{micMuted=!micMuted;if(localAudioTrack!=null)localAudioTrack.setEnabled(!micMuted);muteButton.setText(micMuted?"🔇 تشغيل الميكروفون":"🎙 كتم");});
        speakerButton.setOnClickListener(v->toggleSpeaker());endButton.setOnClickListener(v->endCall());
        return root;
    }

    private void toggleSpeaker(){if(audioManager==null)return;boolean on=!audioManager.isSpeakerphoneOn();audioManager.setSpeakerphoneOn(on);speakerButton.setText(on?"🔊 سماعة":"📱 سماعة الهاتف");}
    private void startCall(){
        try{PeerConnectionFactory.initialize(PeerConnectionFactory.InitializationOptions.builder(this).createInitializationOptions());if(!audioOnly){egl=EglBase.create();localView.init(egl.getEglBaseContext(),null);remoteView.init(egl.getEglBaseContext(),null);localView.setMirror(true);}audioManager=(AudioManager)getSystemService(Context.AUDIO_SERVICE);if(audioManager!=null){previousSpeaker=audioManager.isSpeakerphoneOn();audioManager.setMode(AudioManager.MODE_IN_COMMUNICATION);audioManager.setSpeakerphoneOn(true);}PeerConnectionFactory.Builder builder=PeerConnectionFactory.builder();if(!audioOnly)builder.setVideoEncoderFactory(new DefaultVideoEncoderFactory(egl.getEglBaseContext(),true,true)).setVideoDecoderFactory(new DefaultVideoDecoderFactory(egl.getEglBaseContext()));factory=builder.createPeerConnectionFactory();createPeer();startLocal();if(incomingOffer!=null&&!incomingOffer.trim().isEmpty()&&!isInitiator())handler.post(()->handle("offer",incomingOffer));callIo.scheduleWithFixedDelay(this::pollSignals,0,1500,TimeUnit.MILLISECONDS);if(isInitiator()&&(incomingOffer==null||incomingOffer.trim().isEmpty()))sendOffer();status.setText(isInitiator()?"جاري الاتصال بالطرف الآخر…":"تم قبول المكالمة — بانتظار تثبيت الاتصال…");}catch(Throwable e){fail("تعذر بدء المكالمة: "+(e.getMessage()==null?"خطأ WebRTC":e.getMessage()));}}
    private boolean isInitiator(){return myId.compareTo(target)<0;}
    private void createPeer(){List<PeerConnection.IceServer> servers=new ArrayList<>();servers.add(PeerConnection.IceServer.builder("stun:stun.l.google.com:19302").createIceServer());servers.add(PeerConnection.IceServer.builder("stun:stun1.l.google.com:19302").createIceServer());String u="openrelayproject",p="openrelayproject";for(String uri:Arrays.asList("turn:openrelay.metered.ca:3478?transport=udp","turn:openrelay.metered.ca:3478?transport=tcp","turn:openrelay.metered.ca:80?transport=tcp","turn:openrelay.metered.ca:443?transport=tcp"))servers.add(PeerConnection.IceServer.builder(uri).setUsername(u).setPassword(p).createIceServer());PeerConnection.RTCConfiguration cfg=new PeerConnection.RTCConfiguration(servers);cfg.sdpSemantics=PeerConnection.SdpSemantics.UNIFIED_PLAN;cfg.continualGatheringPolicy=PeerConnection.ContinualGatheringPolicy.GATHER_CONTINUALLY;peer=factory.createPeerConnection(cfg,new PeerConnection.Observer(){public void onSignalingChange(PeerConnection.SignalingState s){}public void onIceConnectionChange(PeerConnection.IceConnectionState s){runOnUiThread(()->{if(status==null)return;if(s==PeerConnection.IceConnectionState.CONNECTED||s==PeerConnection.IceConnectionState.COMPLETED)status.setText("تم الاتصال ✓");else if(s==PeerConnection.IceConnectionState.CHECKING)status.setText("جاري تثبيت الاتصال…");else if(s==PeerConnection.IceConnectionState.DISCONNECTED)status.setText("إعادة الاتصال…");else if(s==PeerConnection.IceConnectionState.FAILED)status.setText("تعذر الاتصال بالطرف الآخر");});}public void onIceConnectionReceivingChange(boolean b){}public void onIceGatheringChange(PeerConnection.IceGatheringState s){}public void onIceCandidate(IceCandidate c){sendSignal("ice",candidateJson(c));}public void onIceCandidatesRemoved(IceCandidate[] c){}public void onAddStream(MediaStream s){if(!audioOnly&&s.videoTracks!=null&&!s.videoTracks.isEmpty())s.videoTracks.get(0).addSink(remoteView);}public void onRemoveStream(MediaStream s){}public void onDataChannel(DataChannel d){}public void onRenegotiationNeeded(){}public void onAddTrack(RtpReceiver r,MediaStream[] s){MediaStreamTrack t=r.track();if(t instanceof AudioTrack)((AudioTrack)t).setEnabled(true);if(!audioOnly&&t instanceof VideoTrack)((VideoTrack)t).addSink(remoteView);}});if(peer==null)throw new IllegalStateException("peer connection unavailable");}
    private void startLocal(){List<String> ids=Collections.singletonList("wethaq_stream");audioSource=factory.createAudioSource(new MediaConstraints());localAudioTrack=factory.createAudioTrack("wethaq_audio",audioSource);localAudioTrack.setEnabled(true);peer.addTrack(localAudioTrack,ids);if(audioOnly)return;capturer=createCapturer();if(capturer==null)throw new IllegalStateException("camera unavailable");videoSource=factory.createVideoSource(false);SurfaceTextureHelper helper=SurfaceTextureHelper.create("WethaqCapture",egl.getEglBaseContext());capturer.initialize(helper,this,videoSource.getCapturerObserver());localVideoTrack=factory.createVideoTrack("wethaq_video",videoSource);localVideoTrack.setEnabled(true);localVideoTrack.addSink(localView);peer.addTrack(localVideoTrack,ids);capturer.startCapture(640,480,24);}
    private CameraVideoCapturer createCapturer(){Camera2Enumerator e=new Camera2Enumerator(this);for(String n:e.getDeviceNames())if(e.isFrontFacing(n))return e.createCapturer(n,null);for(String n:e.getDeviceNames())return e.createCapturer(n,null);return null;}
    private void sendOffer(){if(offerSent||peer==null)return;offerSent=true;peer.createOffer(new SdpObserver(){public void onCreateSuccess(SessionDescription d){peer.setLocalDescription(new SimpleSdp(){public void onSetSuccess(){sendSignal("offer",sdpJson(d));}public void onSetFailure(String s){offerSent=false;}},d);}public void onSetSuccess(){}public void onCreateFailure(String s){offerSent=false;}public void onSetFailure(String s){}},new MediaConstraints());}
    private void pollSignals(){if(cleaned)return;try{HttpURLConnection c=(HttpURLConnection)new URL(API+"/api/calls/signals/"+URLEncoder.encode(target,"UTF-8")).openConnection();c.setRequestProperty("Authorization","Bearer "+token);c.setConnectTimeout(4000);c.setReadTimeout(4000);if(c.getResponseCode()!=200){c.disconnect();return;}InputStream in=c.getInputStream();ByteArrayOutputStream out=new ByteArrayOutputStream();byte[] b=new byte[4096];int n;while((n=in.read(b))!=-1)out.write(b,0,n);in.close();c.disconnect();JSONArray a=new JSONObject(new String(out.toByteArray(),StandardCharsets.UTF_8)).optJSONArray("signals");if(a==null)return;for(int i=0;i<a.length();i++){JSONObject x=a.optJSONObject(i);if(x==null)continue;String id=x.optString("id","");String key=id.isEmpty()?x.optString("created_at","")+x.optString("type","")+x.optString("payload",""):id;if(!seenSignals.add(key))continue;String sender=x.optString("sender_id","");if(sender.equals(myId))continue;handler.post(()->handle(x.optString("type",""),x.optString("payload","")));}}catch(Exception ignored){}}
    private void handle(String type,String payload){try{JSONObject o=new JSONObject(payload);if("offer".equals(type)&&!isInitiator()&&peer!=null&&!remoteDescriptionSet){SessionDescription d=new SessionDescription(SessionDescription.Type.OFFER,o.getString("sdp"));peer.setRemoteDescription(new SimpleSdp(){public void onSetSuccess(){remoteDescriptionSet=true;flushIce();createAnswer();if(status!=null)status.setText("تم استلام المكالمة…");}},d);}else if("answer".equals(type)&&isInitiator()&&peer!=null&&!remoteDescriptionSet){SessionDescription d=new SessionDescription(SessionDescription.Type.ANSWER,o.getString("sdp"));peer.setRemoteDescription(new SimpleSdp(){public void onSetSuccess(){remoteDescriptionSet=true;flushIce();}},d);}else if("ice".equals(type)&&peer!=null){IceCandidate c=new IceCandidate(o.getString("sdpMid"),o.getInt("sdpMLineIndex"),o.getString("candidate"));if(remoteDescriptionSet)peer.addIceCandidate(c);else pendingCandidates.add(c);}else if("end".equals(type))runOnUiThread(this::endCall);}catch(Exception ignored){}}
    private void createAnswer(){if(peer==null)return;peer.createAnswer(new SdpObserver(){public void onCreateSuccess(SessionDescription a){peer.setLocalDescription(new SimpleSdp(){public void onSetSuccess(){sendSignal("answer",sdpJson(a));}},a);}public void onSetSuccess(){}public void onCreateFailure(String s){}public void onSetFailure(String s){}},new MediaConstraints());}
    private void flushIce(){if(peer==null)return;for(IceCandidate c:pendingCandidates)peer.addIceCandidate(c);pendingCandidates.clear();}
    private String sdpJson(SessionDescription d){try{return new JSONObject().put("sdp",d.description).toString();}catch(Exception e){return "{}";}}
    private String candidateJson(IceCandidate c){try{return new JSONObject().put("candidate",c.sdp).put("sdpMid",c.sdpMid).put("sdpMLineIndex",c.sdpMLineIndex).toString();}catch(Exception e){return "{}";}}
    private void sendSignal(String type,String payload){new Thread(()->{try{JSONObject q=new JSONObject().put("to",target).put("type",type).put("payload",payload);HttpURLConnection c=(HttpURLConnection)new URL(API+"/api/calls/signal").openConnection();c.setRequestMethod("POST");c.setDoOutput(true);c.setConnectTimeout(4000);c.setReadTimeout(4000);c.setRequestProperty("Authorization","Bearer "+token);c.setRequestProperty("Content-Type","application/json");try(OutputStream o=c.getOutputStream()){o.write(q.toString().getBytes(StandardCharsets.UTF_8));}c.getResponseCode();c.disconnect();}catch(Exception ignored){}}).start();}
    private void fail(String text){runOnUiThread(()->{if(status!=null)status.setText(text);});handler.postDelayed(this::endCall,2200);}
    private void endCall(){if(cleaned)return;sendSignal("end","{}");cleaned=true;callIo.shutdownNow();try{if(capturer!=null)capturer.stopCapture();}catch(Exception ignored){}try{if(peer!=null)peer.close();}catch(Exception ignored){}try{if(factory!=null)factory.dispose();}catch(Exception ignored){}try{if(videoSource!=null)videoSource.dispose();}catch(Exception ignored){}try{if(audioSource!=null)audioSource.dispose();}catch(Exception ignored){}try{if(localView!=null)localView.release();if(remoteView!=null)remoteView.release();if(egl!=null)egl.release();}catch(Exception ignored){}if(audioManager!=null){audioManager.setSpeakerphoneOn(previousSpeaker);audioManager.setMode(AudioManager.MODE_NORMAL);}finish();}
    @Override public void onRequestPermissionsResult(int r,String[] p,int[] g){super.onRequestPermissionsResult(r,p,g);if(r==PERM_CALL){boolean ok=audioOnly?(g.length>0&&g[0]==PackageManager.PERMISSION_GRANTED):(g.length>=2&&g[0]==PackageManager.PERMISSION_GRANTED&&g[1]==PackageManager.PERMISSION_GRANTED);if(ok)prepareAndStart();else{Toast.makeText(this,audioOnly?"يجب السماح بالميكروفون للمكالمة":"يجب السماح بالميكروفون والكاميرا للمكالمة",Toast.LENGTH_LONG).show();finish();}}}
    @Override protected void onDestroy(){if(!cleaned)callIo.shutdownNow();super.onDestroy();}
    private abstract static class SimpleSdp implements SdpObserver{public void onCreateSuccess(SessionDescription d){}public void onSetSuccess(){}public void onCreateFailure(String s){}public void onSetFailure(String s){}}
}
