package com.wethaq.app;

import android.app.Activity;
import android.app.Application;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.os.Build;
import android.os.Bundle;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;

public final class WethaqApp extends Application implements Application.ActivityLifecycleCallbacks {
    private static final String PREFS="wethaq";
    private static final String CONTACTS="saved_contacts";
    private static final String BACKUP="wethaq_contacts_backup";
    private static final String API="https://wethaq-backend-production.up.railway.app";
    private SharedPreferences prefs;
    private SharedPreferences backup;
    private boolean restoring;
    private long lastSync;
    private final SharedPreferences.OnSharedPreferenceChangeListener prefListener=(sp,key)->{
        if(restoring||!CONTACTS.equals(key))return;
        preserveAndRestoreContacts();
    };
    private final BroadcastReceiver messageReceiver=new BroadcastReceiver(){
        @Override public void onReceive(Context context,Intent intent){
            if(!"com.wethaq.MESSAGE_RECEIVED".equals(intent.getAction()))return;
            String id=intent.getStringExtra("sender_wethaq_id");
            String name=intent.getStringExtra("sender_name");
            if(id==null||id.trim().isEmpty())return;
            addContact(id,name==null||name.trim().isEmpty()?"مستخدم":name.trim());
        }
    };

    @Override public void onCreate(){
        super.onCreate();
        prefs=getSharedPreferences(PREFS,MODE_PRIVATE);
        backup=getSharedPreferences(BACKUP,MODE_PRIVATE);
        preserveAndRestoreContacts();
        prefs.registerOnSharedPreferenceChangeListener(prefListener);
        IntentFilter f=new IntentFilter("com.wethaq.MESSAGE_RECEIVED");
        if(Build.VERSION.SDK_INT>=33)registerReceiver(messageReceiver,f,Context.RECEIVER_NOT_EXPORTED);else registerReceiver(messageReceiver,f);
        registerActivityLifecycleCallbacks(this);
        syncContactsIfNeeded();
    }

    private synchronized void preserveAndRestoreContacts(){
        try{
            String current=prefs.getString(CONTACTS,"");
            String saved=backup.getString(CONTACTS,"[]");
            if(current!=null&&!current.isEmpty()&&!"[]".equals(current))backup.edit().putString(CONTACTS,current).apply();
            else if(saved!=null&&!saved.isEmpty()&&!"[]".equals(saved)){
                restoring=true;
                prefs.edit().putString(CONTACTS,saved).apply();
                restoring=false;
            }
        }catch(Exception ignored){restoring=false;}
    }

    private synchronized void addContact(String id,String name){
        try{
            JSONArray a=new JSONArray(prefs.getString(CONTACTS,backup.getString(CONTACTS,"[]")));
            boolean found=false;
            for(int i=0;i<a.length();i++){
                JSONObject o=a.optJSONObject(i);
                if(o!=null&&id.equals(o.optString("wethaq_id"))){
                    if(name!=null&&!name.isEmpty())o.put("name",name);
                    found=true;break;
                }
            }
            if(!found){JSONObject o=new JSONObject();o.put("wethaq_id",id);o.put("name",name);a.put(o);}
            String value=a.toString();
            prefs.edit().putString(CONTACTS,value).apply();
            backup.edit().putString(CONTACTS,value).apply();
        }catch(Exception ignored){}
    }

    private void syncContactsIfNeeded(){
        if(prefs.getString("token","").length()<10)return;
        long now=System.currentTimeMillis();
        if(now-lastSync<15000)return;
        lastSync=now;
        new Thread(()->{
            try{
                String token=prefs.getString("token","");
                HttpURLConnection c=(HttpURLConnection)new URL(API+"/api/contacts").openConnection();
                c.setRequestMethod("GET");c.setConnectTimeout(8000);c.setReadTimeout(10000);
                c.setRequestProperty("Accept","application/json");c.setRequestProperty("Authorization","Bearer "+token);
                int code=c.getResponseCode();
                InputStream in=code>=200&&code<300?c.getInputStream():c.getErrorStream();
                ByteArrayOutputStream out=new ByteArrayOutputStream();
                if(in!=null){byte[] b=new byte[4096];int n;while((n=in.read(b))!=-1)out.write(b,0,n);in.close();}
                c.disconnect();
                if(code<200||code>=300)return;
                JSONObject root=new JSONObject(new String(out.toByteArray(),StandardCharsets.UTF_8));
                JSONArray contacts=root.optJSONArray("contacts");
                if(contacts==null)return;
                for(int i=0;i<contacts.length();i++){
                    JSONObject x=contacts.optJSONObject(i);if(x==null)continue;
                    String id=x.optString("wethaq_id").trim(),name=x.optString("name").trim();
                    if(!id.isEmpty())addContact(id,name.isEmpty()?"مستخدم":name);
                }
            }catch(Exception ignored){}
        }).start();
    }

    private void refresh(Activity a){
        syncContactsIfNeeded();
        a.getWindow().setStatusBarColor(android.graphics.Color.rgb(8,8,10));
        a.getWindow().setNavigationBarColor(android.graphics.Color.rgb(8,8,10));
        a.getWindow().getDecorView().postDelayed(()->WethaqUi.apply(this,a),120);
        a.getWindow().getDecorView().postDelayed(()->WethaqUi.apply(this,a),500);
    }
    @Override public void onActivityCreated(Activity a,Bundle b){refresh(a);}
    @Override public void onActivityResumed(Activity a){refresh(a);}
    @Override public void onActivityStarted(Activity a){}
    @Override public void onActivityPaused(Activity a){}
    @Override public void onActivityStopped(Activity a){}
    @Override public void onActivitySaveInstanceState(Activity a,Bundle b){}
    @Override public void onActivityDestroyed(Activity a){}
}
