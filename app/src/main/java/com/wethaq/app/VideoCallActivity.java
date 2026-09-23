package com.wethaq.app;

import android.Manifest;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.BroadcastReceiver;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.media.AudioManager;
import android.media.ToneGenerator;
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
import org.json.JSONArray;
import org.json.JSONObject;
import org.webrtc.*;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import okhttp3.MediaType;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public final class VideoCallActivity extends Activity {
    private static final String API=WethaqConfig.API;
    private static final int PERM_CALL=500;
    private final Handler handler=new Handler(Looper.getMainLooper());
    private final Set<String> seenSignals=new HashSet<>();
    private final List<IceCandidate> pendingCandidates=new ArrayList<>();
    private final List<PeerConnection.IceServer> configuredIceServers=new ArrayList<>();
    private final ScheduledExecutorService callIo=Executors.newSingleThreadScheduledExecutor();
    private final OkHttpClient callClient=new OkHttpClient.Builder().connectTimeout(5,TimeUnit.SECONDS).readTimeout(5,TimeUnit.SECONDS).writeTimeout(5,TimeUnit.SECONDS).retryOnConnectionFailure(true).build();
    private static final MediaType JSON=MediaType.parse("application/json; charset=utf-8");
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
    private boolean audioOnly,cleaned,offerSent,remoteDescriptionSet,micMuted,callEstablished;private Runnable callTimeout;private ToneGenerator ringTone;private Runnable ringLoop;private long callRecordId;private String serverCallId;
    private final BroadcastReceiver callSignalReceiver=new BroadcastReceiver(){@Override public void onReceive(Context context,Intent intent){if(!"com.wethaq.CALL_SIGNAL".equals(intent.getAction())||cleaned)return;String sender=intent.getStringExtra("sender_wethaq_id");if(sender==null||!sender.equals(target))return;String type=intent.getStringExtra("type");String payload=intent.getStringExtra("payload");long signalId=intent.getLongExtra("signal_id",0);if(signalId>0&&!seenSignals.add(String.valueOf(signalId)))return;handler.post(()->handle(type==null?"":type,payload==null?"":payload));}};
    private TextView status;
    private LinearLayout controls;

    @Override public void onBackPressed(){if(!cleaned)endCall();else super.onBackPressed();}

    @Override public void onDestroy(){cancelCallTimeout();stopOutgoingRinging();unregisterCallSignalReceiver();callIo.shutdownNow();callClient.dispatcher().cancelAll();callClient.connectionPool().evictAll();super.onDestroy();}

    @Override public void onCreate(Bundle state){
        super.onCreate(state);
        cancelCallNotification();
        target=getIntent().getStringExtra("target");
        token=getSharedPreferences("wethaq",MODE_PRIVATE).getString("token","");
        myId=getSharedPreferences("wethaq",MODE_PRIVATE).getString("wethaq_id","");
        audioOnly=getIntent().getBooleanExtra("audioOnly",false);
        incomingOffer=getIntent().getStringExtra("incomingOffer");
        if(isIncoming()){callRecordId=CallHistory.pendingFor(this,target);if(callRecordId==0)callRecordId=CallHistory.record(this,target,getIntent().getStringExtra("name"),audioOnly,false,CallHistory.PENDING_INCOMING);}
        else{callRecordId=CallHistory.record(this,target,getIntent().getStringExtra("name"),audioOnly,true,CallHistory.PENDING_OUTGOING);serverCallId=UUID.randomUUID().toString();syncCallHistoryStart();}
        setContentView(makeUi());
        registerCallSignalReceiver();
        if(target==null||target.trim().isEmpty()||token.isEmpty()||myId.isEmpty()){fail("تعذر بدء المكالمة");return;}
        if(Build.VERSION.SDK_INT>=23){
            boolean mic=checkSelfPermission(Manifest.permission.RECORD_AUDIO)==PackageManager.PERMISSION_GRANTED;
            boolean cam=audioOnly||checkSelfPermission(Manifest.permission.CAMERA)==PackageManager.PERMISSION_GRANTED;
            if(!mic||!cam){
                requestPermissions(audioOnly?new String[]{Manifest.permission.RECORD_AUDIO}:new String[]{Manifest.permission.CAMERA,Manifest.permission.RECORD_AUDIO},PERM_CALL);
                return;
            }
        }
        if(isIncoming())showIncomingControls();else startCall();
    }

    private boolean isIncoming(){return incomingOffer!=null&&!incomingOffer.trim().isEmpty();}
    private void registerCallSignalReceiver(){IntentFilter f=new IntentFilter("com.wethaq.CALL_SIGNAL");if(Build.VERSION.SDK_INT>=33)registerReceiver(callSignalReceiver,f,Context.RECEIVER_NOT_EXPORTED);else registerReceiver(callSignalReceiver,f);}
    private void unregisterCallSignalReceiver(){try{unregisterReceiver(callSignalReceiver);}catch(Exception ignored){}}


    private View makeUi(){
        FrameLayout root=new FrameLayout(this);root.setBackgroundColor(Color.BLACK);
        if(!audioOnly){
            remoteView=new SurfaceViewRenderer(this);localView=new SurfaceViewRenderer(this);
            root.addView(remoteView,new FrameLayout.LayoutParams(-1,-1));
            FrameLayout.LayoutParams lp=new FrameLayout.LayoutParams(dp(120),dp(180),Gravity.RIGHT|Gravity.TOP);lp.setMargins(0,dp(24),dp(16),0);root.addView(localView,lp);
        }else{
            TextView call=new TextView(this);call.setText("📞\nمكالمة صوتية\n"+String.valueOf(getIntent().getStringExtra("name")));call.setTextColor(Color.WHITE);call.setTextSize(25);call.setGravity(Gravity.CENTER);root.addView(call,new FrameLayout.LayoutParams(-1,-1));
        }
        status=new TextView(this);status.setText(isIncoming()?"مكالمة واردة":"جاري الاتصال…");status.setTextColor(Color.WHITE);status.setTextSize(18);status.setGravity(Gravity.CENTER);root.addView(status,new FrameLayout.LayoutParams(-1,dp(72),Gravity.TOP));
        controls=new LinearLayout(this);controls.setOrientation(LinearLayout.HORIZONTAL);controls.setGravity(Gravity.CENTER);controls.setPadding(dp(10),dp(8),dp(10),dp(16));FrameLayout.LayoutParams cp=new FrameLayout.LayoutParams(-1,dp(92),Gravity.BOTTOM);cp.setMargins(dp(8),0,dp(8),dp(8));root.addView(controls,cp);
        return root;
    }

    private Button actionButton(String text){
        Button b=new Button(this);b.setText(text);b.setTextSize(16);b.setAllCaps(false);b.setTextColor(Color.WHITE);b.setMinHeight(dp(68));b.setMinimumWidth(0);b.setPadding(dp(10),0,dp(10),0);return b;
    }

    private void showIncomingControls(){
        controls.removeAllViews();
        TextView title=new TextView(this);title.setText("📞 مكالمة واردة من "+String.valueOf(getIntent().getStringExtra("name")));title.setTextColor(Color.WHITE);title.setTextSize(18);title.setGravity(Gravity.CENTER);FrameLayout.LayoutParams tp=new FrameLayout.LayoutParams(-1,dp(70),Gravity.CENTER_HORIZONTAL|Gravity.CENTER_VERTICAL);tp.setMargins(dp(16),dp(48),dp(16),dp(108));addContentView(title,tp);
        Button accept=actionButton("✅ قبول المكالمة"),reject=actionButton("❌ رفض");accept.setOnClickListener(v->{accept.setEnabled(false);reject.setEnabled(false);startCall();});reject.setOnClickListener(v->rejectCall());controls.addView(accept,new LinearLayout.LayoutParams(0,dp(72),1));controls.addView(reject,new LinearLayout.LayoutParams(0,dp(72),1));
    }

    private void showInCallControls(){
        controls.removeAllViews();
        Button mic=actionButton("🎙 كتم الميكروفون"),speaker=actionButton("🔊 مكبر الصوت"),end=actionButton("⛔ إنهاء");
        mic.setOnClickListener(v->{if(localAudioTrack==null)return;micMuted=!micMuted;localAudioTrack.setEnabled(!micMuted);mic.setText(micMuted?"🎙 تشغيل الميكروفون":"🔇 كتم الميكروفون");status.setText(micMuted?"الميكروفون مكتوم":"تم الاتصال ✓");});
        speaker.setOnClickListener(v->{if(audioManager==null)return;boolean on=!audioManager.isSpeakerphoneOn();audioManager.setSpeakerphoneOn(on);speaker.setText(on?"🔊 إيقاف مكبر الصوت":"📱 سماعة الهاتف");});
        end.setOnClickListener(v->endCall());
        controls.addView(mic,new LinearLayout.LayoutParams(0,dp(72),1));controls.addView(speaker,new LinearLayout.LayoutParams(0,dp(72),1));controls.addView(end,new LinearLayout.LayoutParams(0,dp(72),1));
    }

    private int dp(int n){return (int)(n*getResources().getDisplayMetrics().density+.5f);}

    private void startCall(){
        if(!isIncoming())startOutgoingRinging();
        status.setText(isIncoming()?"جاري قبول المكالمة…":"جاري تهيئة المكالمة…");
        loadIceConfigAndStart();
    }

    private void loadIceConfigAndStart(){
        Request req=new Request.Builder().url(API+"/api/calls/ice-config").header("Authorization","Bearer "+token).get().build();
        callClient.newCall(req).enqueue(new Callback(){
            public void onFailure(Call call,java.io.IOException e){runOnUiThread(()->fail("تعذر تحميل إعدادات اتصال المكالمة"));}
            public void onResponse(Call call,Response response){
                try(Response r=response){
                    if(!r.isSuccessful()||r.body()==null){runOnUiThread(()->fail("إعدادات اتصال المكالمة غير متاحة"));return;}
                    JSONObject root=new JSONObject(r.body().string());
                    JSONArray list=root.optJSONArray("iceServers");
                    configuredIceServers.clear();
                    if(list!=null)for(int i=0;i<list.length();i++){
                        JSONObject item=list.optJSONObject(i);if(item==null)continue;
                        Object urls=item.opt("urls");ArrayList<String> urlList=new ArrayList<>();
                        if(urls instanceof JSONArray){JSONArray arr=(JSONArray)urls;for(int j=0;j<arr.length();j++)if(arr.optString(j,"").trim().length()>0)urlList.add(arr.optString(j).trim());}
                        else if(urls!=null&&String.valueOf(urls).trim().length()>0)urlList.add(String.valueOf(urls).trim());
                        if(urlList.isEmpty())continue;
                        String username=item.optString("username",""),credential=item.optString("credential","");
                        for(String u:urlList){
                            if(username.isEmpty()||credential.isEmpty())configuredIceServers.add(PeerConnection.IceServer.builder(u).createIceServer());
                            else configuredIceServers.add(PeerConnection.IceServer.builder(u).setUsername(username).setPassword(credential).createIceServer());
                        }
                    }
                    if(configuredIceServers.isEmpty())throw new IllegalStateException("ICE servers missing");
                    boolean turnConfigured=root.optBoolean("turnConfigured",false);
                    handler.post(()->{status.setText(turnConfigured?"تم تحميل TURN الإنتاجي — بدء المكالمة":"تم تحميل ICE — بدء المكالمة المباشرة");startCallEngine();});
                }catch(Exception e){runOnUiThread(()->fail("إعدادات ICE غير صالحة"));}
            }
        });
    }

    private void startCallEngine(){
        try{
            PeerConnectionFactory.initialize(PeerConnectionFactory.InitializationOptions.builder(this).createInitializationOptions());
            if(!audioOnly){egl=EglBase.create();localView.init(egl.getEglBaseContext(),null);remoteView.init(egl.getEglBaseContext(),null);localView.setMirror(true);}
            audioManager=(AudioManager)getSystemService(Context.AUDIO_SERVICE);
            if(audioManager!=null){previousSpeaker=audioManager.isSpeakerphoneOn();audioManager.setMode(AudioManager.MODE_IN_COMMUNICATION);audioManager.setSpeakerphoneOn(true);}
            PeerConnectionFactory.Builder builder=PeerConnectionFactory.builder();
            if(!audioOnly)builder.setVideoEncoderFactory(new DefaultVideoEncoderFactory(egl.getEglBaseContext(),true,true)).setVideoDecoderFactory(new DefaultVideoDecoderFactory(egl.getEglBaseContext()));
            factory=builder.createPeerConnectionFactory();createPeer();startLocal();showInCallControls();
            if(incomingOffer!=null&&!incomingOffer.trim().isEmpty()&&!isInitiator())handler.post(()->handle("offer",incomingOffer));
            callIo.scheduleWithFixedDelay(this::pollSignals,0,1500,TimeUnit.MILLISECONDS);callTimeout=()->{if(!cleaned&&!remoteDescriptionSet)fail("تعذر تثبيت اتصال المكالمة خلال المهلة");};handler.postDelayed(callTimeout,25000);
            if(isInitiator()&&(incomingOffer==null||incomingOffer.trim().isEmpty()))sendOffer();
            status.setText(isIncoming()?"جاري توصيل المكالمة…":(isInitiator()?"جاري الاتصال بالطرف الآخر…":"بانتظار اتصال الطرف الآخر…"));
        }catch(Throwable e){fail("تعذر بدء المكالمة: "+(e.getMessage()==null?"خطأ WebRTC":e.getMessage()));}
    }

    // The device that explicitly starts the call is always the offerer.
    // Do not derive caller/callee from Wethaq IDs: either user must be able to call the other.
    private boolean isInitiator(){return incomingOffer==null||incomingOffer.trim().isEmpty();}
    private void createPeer(){
        List<PeerConnection.IceServer> servers=new ArrayList<>(configuredIceServers);
        if(servers.isEmpty())throw new IllegalStateException("ICE configuration is empty");
        PeerConnection.RTCConfiguration cfg=new PeerConnection.RTCConfiguration(servers);cfg.sdpSemantics=PeerConnection.SdpSemantics.UNIFIED_PLAN;cfg.continualGatheringPolicy=PeerConnection.ContinualGatheringPolicy.GATHER_CONTINUALLY;cfg.iceCandidatePoolSize=8;
        peer=factory.createPeerConnection(cfg,new PeerConnection.Observer(){
            public void onSignalingChange(PeerConnection.SignalingState s){}
            public void onIceConnectionChange(PeerConnection.IceConnectionState s){android.util.Log.i("WETHAQ_CALL","ICE state="+s+" target="+target);runOnUiThread(()->{if(s==PeerConnection.IceConnectionState.CONNECTED||s==PeerConnection.IceConnectionState.COMPLETED){cancelCallTimeout();callEstablished=true;stopOutgoingRinging();CallHistory.setStatus(VideoCallActivity.this,callRecordId,CallHistory.ANSWERED);syncCallHistoryStatus("answered");CallHistory.clearPending(VideoCallActivity.this,target);status.setText("تم الاتصال ✓");}else if(s==PeerConnection.IceConnectionState.CHECKING)status.setText("جاري تثبيت الاتصال…");else if(s==PeerConnection.IceConnectionState.DISCONNECTED)status.setText("إعادة الاتصال…");else if(s==PeerConnection.IceConnectionState.FAILED)status.setText("تعذر الاتصال بالطرف الآخر");});}
            public void onIceConnectionReceivingChange(boolean b){}
            public void onIceGatheringChange(PeerConnection.IceGatheringState s){android.util.Log.i("WETHAQ_CALL","ICE gathering="+s+" target="+target);}
            public void onIceCandidate(IceCandidate c){android.util.Log.d("WETHAQ_CALL","ICE candidate generated");sendSignal("ice",candidateJson(c));}
            public void onIceCandidatesRemoved(IceCandidate[] c){}
            public void onAddStream(MediaStream s){if(!audioOnly&&s.videoTracks!=null&&!s.videoTracks.isEmpty())s.videoTracks.get(0).addSink(remoteView);}
            public void onRemoveStream(MediaStream s){}
            public void onDataChannel(DataChannel d){}
            public void onRenegotiationNeeded(){}
            public void onAddTrack(RtpReceiver r,MediaStream[] s){MediaStreamTrack t=r.track();if(t instanceof AudioTrack)((AudioTrack)t).setEnabled(true);if(!audioOnly&&t instanceof VideoTrack)((VideoTrack)t).addSink(remoteView);}
        });
        if(peer==null)throw new IllegalStateException("peer connection unavailable");
    }

    private void startLocal(){
        List<String> ids=Collections.singletonList("wethaq_stream");
        audioSource=factory.createAudioSource(new MediaConstraints());
        localAudioTrack=factory.createAudioTrack("wethaq_audio",audioSource);localAudioTrack.setEnabled(true);peer.addTrack(localAudioTrack,ids);
        if(audioOnly)return;
        capturer=createCapturer();if(capturer==null)throw new IllegalStateException("camera unavailable");
        videoSource=factory.createVideoSource(false);
        SurfaceTextureHelper helper=SurfaceTextureHelper.create("WethaqCapture",egl.getEglBaseContext());
        capturer.initialize(helper,this,videoSource.getCapturerObserver());
        localVideoTrack=factory.createVideoTrack("wethaq_video",videoSource);localVideoTrack.setEnabled(true);localVideoTrack.addSink(localView);peer.addTrack(localVideoTrack,ids);
        capturer.startCapture(640,480,24);
    }
    private CameraVideoCapturer createCapturer(){Camera2Enumerator e=new Camera2Enumerator(this);for(String n:e.getDeviceNames())if(e.isFrontFacing(n))return e.createCapturer(n,null);for(String n:e.getDeviceNames())return e.createCapturer(n,null);return null;}
    private void sendOffer(){if(offerSent||peer==null)return;offerSent=true;peer.createOffer(new SdpObserver(){public void onCreateSuccess(SessionDescription d){peer.setLocalDescription(new SimpleSdp(){public void onSetSuccess(){sendSignal("offer",sdpJson(d));}public void onSetFailure(String s){offerSent=false;}},d);}public void onSetSuccess(){}public void onCreateFailure(String s){offerSent=false;}public void onSetFailure(String s){}},new MediaConstraints());}
    private void pollSignals(){if(cleaned)return;Request req=new Request.Builder().url(API+"/api/calls/signals/"+urlEncode(target)).header("Authorization","Bearer "+token).build();callClient.newCall(req).enqueue(new Callback(){public void onFailure(Call call,java.io.IOException e){}public void onResponse(Call call,Response response){try(Response r=response){if(!r.isSuccessful()||r.body()==null)return;JSONArray a=new JSONObject(r.body().string()).optJSONArray("signals");if(a==null)return;for(int i=0;i<a.length();i++){JSONObject x=a.optJSONObject(i);if(x==null)continue;String id=x.optString("id","");String key=id.isEmpty()?x.optString("created_at","")+x.optString("type","")+x.optString("payload",""):id;if(!seenSignals.add(key))continue;String sender=x.optString("sender_id","");if(sender.equals(myId)||sender.equals(getSharedPreferences("wethaq",MODE_PRIVATE).getString("db_user_id","")))continue;String type=x.optString("type","");String payload=x.optString("payload","");handler.post(()->handle(type,payload));}}catch(Exception ignored){}}});}
    private String urlEncode(String s){try{return URLEncoder.encode(s,"UTF-8");}catch(Exception e){return "";}}
    private void handle(String type,String payload){try{JSONObject o=new JSONObject(payload);if("offer".equals(type){String incomingCallId=o.optString("callId","");if(!incomingCallId.isEmpty()){serverCallId=incomingCallId;syncCallHistoryStart();}}if("offer".equals(type)&&!isInitiator()&&peer!=null&&!remoteDescriptionSet){SessionDescription d=new SessionDescription(SessionDescription.Type.OFFER,o.getString("sdp"));peer.setRemoteDescription(new SimpleSdp(){public void onSetSuccess(){remoteDescriptionSet=true;flushIce();createAnswer();status.setText("تم استلام المكالمة…");}},d);}else if("answer".equals(type)&&isInitiator()&&peer!=null&&!remoteDescriptionSet){SessionDescription d=new SessionDescription(SessionDescription.Type.ANSWER,o.getString("sdp"));peer.setRemoteDescription(new SimpleSdp(){public void onSetSuccess(){remoteDescriptionSet=true;flushIce();}},d);}else if("ice".equals(type)&&peer!=null){IceCandidate c=new IceCandidate(o.getString("sdpMid"),o.getInt("sdpMLineIndex"),o.getString("candidate"));if(remoteDescriptionSet)peer.addIceCandidate(c);else pendingCandidates.add(c);}else if("end".equals(type))runOnUiThread(this::endCall);}catch(Exception ignored){}}
    private void createAnswer(){if(peer==null)return;peer.createAnswer(new SdpObserver(){public void onCreateSuccess(SessionDescription a){peer.setLocalDescription(new SimpleSdp(){public void onSetSuccess(){sendSignal("answer",sdpJson(a));}},a);}public void onSetSuccess(){}public void onCreateFailure(String s){}public void onSetFailure(String s){}},new MediaConstraints());}
    private void flushIce(){if(peer==null)return;for(IceCandidate c:pendingCandidates)peer.addIceCandidate(c);pendingCandidates.clear();}
    private String sdpJson(SessionDescription d){try{JSONObject o=new JSONObject().put("sdp",d.description);if(serverCallId!=null&&!serverCallId.isEmpty())o.put("callId",serverCallId);return o.toString();}catch(Exception e){return "{}";}}
    private String candidateJson(IceCandidate c){try{return new JSONObject().put("candidate",c.sdp).put("sdpMid",c.sdpMid).put("sdpMLineIndex",c.sdpMLineIndex).toString();}catch(Exception e){return "{}";}}
    private void sendSignal(String type,String payload){try{JSONObject q=new JSONObject().put("to",target).put("type",type).put("payload",payload);Request req=new Request.Builder().url(API+"/api/calls/signal").header("Authorization","Bearer "+token).post(RequestBody.create(q.toString(),JSON)).build();callClient.newCall(req).enqueue(new Callback(){public void onFailure(Call call,java.io.IOException e){android.util.Log.w("WETHAQ_CALL","signal send failed type="+type,e);}public void onResponse(Call call,Response response){try(Response r=response){if(!r.isSuccessful())android.util.Log.w("WETHAQ_CALL","signal send HTTP "+r.code()+" type="+type);}}});}catch(Exception e){android.util.Log.w("WETHAQ_CALL","signal build failed type="+type,e);}}
    private void startOutgoingRinging(){stopOutgoingRinging();try{ringTone=new ToneGenerator(AudioManager.STREAM_VOICE_CALL,85);}catch(Exception ignored){ringTone=null;}ringLoop=()->{if(cleaned||callEstablished){stopOutgoingRinging();return;}try{if(ringTone!=null)ringTone.startTone(ToneGenerator.TONE_SUP_RINGTONE,900);}catch(Exception ignored){}handler.postDelayed(ringLoop,1800);};handler.post(ringLoop);}
    private void stopOutgoingRinging(){if(ringLoop!=null){handler.removeCallbacks(ringLoop);ringLoop=null;}if(ringTone!=null){try{ringTone.stopTone();ringTone.release();}catch(Exception ignored){}ringTone=null;}}
    private void syncCallHistoryStart(){if(serverCallId==null||serverCallId.isEmpty()||token==null||token.isEmpty()||target==null)return;try{JSONObject q=new JSONObject().put("callId",serverCallId).put("to",target).put("audioOnly",audioOnly);Request req=new Request.Builder().url(API+"/api/calls/history/start").header("Authorization","Bearer "+token).post(RequestBody.create(q.toString(),JSON)).build();callClient.newCall(req).enqueue(new Callback(){public void onFailure(Call call,java.io.IOException e){}public void onResponse(Call call,Response response){response.close();}});}catch(Exception ignored){}} private void syncCallHistoryStatus(String status){if(serverCallId==null||serverCallId.isEmpty()||token==null||token.isEmpty())return;try{JSONObject q=new JSONObject().put("callId",serverCallId).put("status",status);Request req=new Request.Builder().url(API+"/api/calls/history/status").header("Authorization","Bearer "+token).post(RequestBody.create(q.toString(),JSON)).build();callClient.newCall(req).enqueue(new Callback(){public void onFailure(Call call,java.io.IOException e){}public void onResponse(Call call,Response response){response.close();}});}catch(Exception ignored){}} private void cancelCallTimeout(){if(callTimeout!=null){handler.removeCallbacks(callTimeout);callTimeout=null;}} private void cancelCallNotification(){try{android.app.NotificationManager nm=getSystemService(android.app.NotificationManager.class);if(nm!=null)nm.cancel(4102);}catch(Exception ignored){}} private void rejectCall(){cancelCallNotification();cancelCallTimeout();stopOutgoingRinging();if(!callEstablished){CallHistory.setStatus(this,callRecordId,CallHistory.MISSED);syncCallHistoryStatus("missed");}else syncCallHistoryStatus("ended");CallHistory.clearPending(this,target);sendSignal("end","{}");cleaned=true;callIo.shutdownNow();finish();}
    private void fail(String text){if(status!=null)status.setText(text);else toast(text);if(!callEstablished){CallHistory.setStatus(this,callRecordId,CallHistory.MISSED);syncCallHistoryStatus("missed");}stopOutgoingRinging();handler.postDelayed(this::endCall,1800);}
    private void endCall(){if(cleaned)return;cancelCallNotification();cancelCallTimeout();stopOutgoingRinging();if(!callEstablished)CallHistory.setStatus(this,callRecordId,CallHistory.MISSED);else CallHistory.setStatus(this,callRecordId,CallHistory.ANSWERED);CallHistory.clearPending(this,target);sendSignal("end","{}");cleaned=true;callIo.shutdownNow();try{if(capturer!=null)capturer.stopCapture();}catch(Exception ignored){}try{if(peer!=null)peer.close();}catch(Exception ignored){}try{if(factory!=null)factory.dispose();}catch(Exception ignored){}try{if(videoSource!=null)videoSource.dispose();}catch(Exception ignored){}try{if(audioSource!=null)audioSource.dispose();}catch(Exception ignored){}try{if(localView!=null)localView.release();if(remoteView!=null)remoteView.release();if(egl!=null)egl.release();}catch(Exception ignored){}if(audioManager!=null){audioManager.setSpeakerphoneOn(previousSpeaker);audioManager.setMode(AudioManager.MODE_NORMAL);}finish();}
    private void toast(String s){android.widget.Toast.makeText(this,s,android.widget.Toast.LENGTH_LONG).show();}
    @Override public void onRequestPermissionsResult(int r,String[] p,int[] g){super.onRequestPermissionsResult(r,p,g);if(r==PERM_CALL){boolean ok=audioOnly?(g.length>0&&g[0]==PackageManager.PERMISSION_GRANTED):(g.length>=2&&g[0]==PackageManager.PERMISSION_GRANTED&&g[1]==PackageManager.PERMISSION_GRANTED);if(ok){if(isIncoming())showIncomingControls();else startCall();}else{toast(audioOnly?"يجب السماح بالميكروفون للمكالمة":"يجب السماح بالميكروفون والكاميرا للمكالمة");finish();}}}
    private abstract static class SimpleSdp implements SdpObserver{public void onCreateSuccess(SessionDescription d){}public void onSetSuccess(){}public void onCreateFailure(String s){}public void onSetFailure(String s){}}
}
