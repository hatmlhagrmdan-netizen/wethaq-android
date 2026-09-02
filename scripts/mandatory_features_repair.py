from pathlib import Path


def read(path):
    return Path(path).read_text(encoding='utf-8')


def write(path, value):
    Path(path).write_text(value, encoding='utf-8')


def replace_once(s, old, new, label):
    if new in s:
        return s
    if old not in s:
        raise SystemExit(f'PATCH_MISSING:{label}')
    return s.replace(old, new, 1)


def patch_video():
    p='app/src/main/java/com/wethaq/app/VideoCallActivity.java'
    s=read(p)
    s=replace_once(s,
        'private Button speakerButton,muteButton,cameraButton;',
        'private Button speakerButton,muteButton,cameraButton,acceptButton,rejectButton;\n    private Runnable callTimeout;',
        'video incoming controls fields')
    old='Button end=controlButton("☎ إنهاء المكالمة");end.setContentDescription("إنهاء المكالمة");end.setTextSize(18);FrameLayout.LayoutParams ep=new FrameLayout.LayoutParams(-1,dp(64),Gravity.BOTTOM);ep.setMargins(dp(16),0,dp(16),dp(24));root.addView(end,ep);end.setOnClickListener(v->endCall());'
    new='''if(incomingOffer!=null&&!incomingOffer.trim().isEmpty()&&!isInitiator()){\n            LinearLayout incomingControls=new LinearLayout(this);incomingControls.setOrientation(LinearLayout.HORIZONTAL);incomingControls.setGravity(Gravity.CENTER);\n            FrameLayout.LayoutParams ip=new FrameLayout.LayoutParams(-1,dp(72),Gravity.TOP);ip.setMargins(dp(16),dp(84),dp(16),0);root.addView(incomingControls,ip);\n            acceptButton=controlButton("✅ قبول المكالمة");rejectButton=controlButton("❌ رفض المكالمة");\n            acceptButton.setContentDescription("قبول المكالمة الواردة");rejectButton.setContentDescription("رفض المكالمة الواردة");\n            incomingControls.addView(acceptButton,buttonParams());incomingControls.addView(rejectButton,buttonParams());\n            acceptButton.setOnClickListener(v->acceptIncoming());rejectButton.setOnClickListener(v->rejectIncoming());\n        }\n        Button end=controlButton("☎ إنهاء المكالمة");end.setContentDescription("إنهاء المكالمة");end.setTextSize(18);FrameLayout.LayoutParams ep=new FrameLayout.LayoutParams(-1,dp(64),Gravity.BOTTOM);ep.setMargins(dp(16),0,dp(16),dp(24));root.addView(end,ep);end.setOnClickListener(v->endCall());'''
    s=replace_once(s,old,new,'incoming accept/reject UI')
    old='if(incomingOffer!=null&&!incomingOffer.trim().isEmpty()&&!isInitiator())handler.post(()->handle("offer",incomingOffer));'
    new='if(incomingOffer!=null&&!incomingOffer.trim().isEmpty()&&!isInitiator())handler.post(()->status.setText("مكالمة واردة — اختر قبول أو رفض"));'
    s=replace_once(s,old,new,'defer incoming offer until accept')
    s=replace_once(s,
        'if(isInitiator()&&(incomingOffer==null||incomingOffer.trim().isEmpty()))sendOffer();\n            status.setText(isInitiator()?"جاري الاتصال بالطرف الآخر…":"بانتظار اتصال الطرف الآخر…");',
        'if(isInitiator()&&(incomingOffer==null||incomingOffer.trim().isEmpty()))sendOffer();\n            if(!isInitiator()){status.setText("مكالمة واردة — اختر قبول أو رفض");}else{status.setText("جاري الاتصال بالطرف الآخر…");}\n            callTimeout=()->{if(!cleaned){runOnUiThread(()->status.setText("انتهت مهلة الاتصال"));endCall();}};handler.postDelayed(callTimeout,45000);',
        'call timeout and incoming status')
    s=replace_once(s,
        'if(s==PeerConnection.IceConnectionState.CONNECTED||s==PeerConnection.IceConnectionState.COMPLETED)status.setText("تم الاتصال ✓");',
        'if(s==PeerConnection.IceConnectionState.CONNECTED||s==PeerConnection.IceConnectionState.COMPLETED){if(callTimeout!=null)handler.removeCallbacks(callTimeout);status.setText("تم الاتصال ✓");}',
        'cancel call timeout after connection')
    anchor='private void createAnswer(){'
    methods='''private void acceptIncoming(){if(cleaned||incomingOffer==null||incomingOffer.trim().isEmpty()||peer==null)return;if(acceptButton!=null)acceptButton.setVisibility(View.GONE);if(rejectButton!=null)rejectButton.setVisibility(View.GONE);status.setText("جاري قبول المكالمة…");handle("offer",incomingOffer);}\n    private void rejectIncoming(){if(cleaned)return;sendSignal("end","{}");endCall();}\n    \n    '''
    if 'private void acceptIncoming()' not in s:
        if anchor not in s: raise SystemExit('PATCH_MISSING:incoming action methods anchor')
        s=s.replace(anchor,methods+anchor,1)
    write(p,s)


def patch_admin():
    p='app/src/main/java/com/wethaq/app/AdminActivity.java'
    s=read(p)
    old='setContentView(root);loadStructure();target=f("معرف وَثاق للمستخدم");minutes=f("مدة الحظر بالدقائق — 0 = نهائي");reason=f("السبب");'
    new='setContentView(root);target=f("معرف وَثاق للمستخدم");minutes=f("مدة الحظر بالدقائق — 0 = نهائي");reason=f("السبب");addAdminSearchControls();loadStructure();'
    s=replace_once(s,old,new,'admin search insertion')
    if 'private void addAdminSearchControls()' not in s:
        anchor='private void loadStructure(){'
        method='''private void addAdminSearchControls(){LinearLayout box=card("🔎 البحث الإداري");EditText q=f("الاسم الكامل أو معرف وَثاق");Button go=b("بحث");LinearLayout results=new LinearLayout(this);results.setOrientation(LinearLayout.VERTICAL);box.addView(q,lp(-1,60,6));box.addView(go,lp(-1,68,6));box.addView(results,lp(-1,-2,4));go.setOnClickListener(v->{String query=q.getText().toString().trim();if(query.length()<2){toast("أدخل الاسم أو معرف وَثاق");return;}setBusy(true);req("GET","/api/search?q="+URLEncoder.encode(query,"UTF-8"),null,r->{try{results.removeAllViews();JSONArray a=new JSONObject(r).optJSONArray("users");if(a==null||a.length()==0){results.addView(heading("لا توجد نتائج"));return;}for(int i=0;i<a.length();i++){JSONObject u=a.optJSONObject(i);if(u==null)continue;String id=u.optString("wethaq_id"),name=u.optString("name");LinearLayout row=new LinearLayout(this);row.setGravity(Gravity.CENTER_VERTICAL|Gravity.RIGHT);TextView info=heading(name+"\\n"+id);row.addView(info,new LinearLayout.LayoutParams(0,-2,1));Button use=b("استخدام");row.addView(use,lp(dp(110),64,4));use.setOnClickListener(x->{target.setText(id);toast("تم اختيار المستخدم: "+id);});results.addView(row,lp(-1,-2,4));}}catch(Exception e){toast("فشل البحث الإداري");}finally{setBusy(false);}});});}\n    '''
        if anchor not in s: raise SystemExit('PATCH_MISSING:admin search anchor')
        s=s.replace(anchor,method+anchor,1)
    write(p,s)


if __name__=='__main__':
    patch_video()
    patch_admin()
    print('WETHAQ_MANDATORY_FEATURE_REPAIR_OK')
