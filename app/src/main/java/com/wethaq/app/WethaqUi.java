package com.wethaq.app;

import android.app.Activity;
import android.app.Application;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.StateListDrawable;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

public final class WethaqUi {
    private static final int GOLD=Color.rgb(212,175,55), DARK=Color.rgb(28,28,32), PRESSED=Color.rgb(55,45,24);
    private static final String API="https://wethaq-backend-production.up.railway.app";
    private WethaqUi(){}

    public static void apply(Application app, Activity activity){
        View root=activity.findViewById(android.R.id.content);
        if(root instanceof ViewGroup) styleTree(activity,(ViewGroup)root);
    }

    private static void styleTree(Activity a, ViewGroup g){
        for(int i=0;i<g.getChildCount();i++){
            View v=g.getChildAt(i);
            if(v instanceof Button) styleButton((Button)v);
            if(v instanceof TextView) styleText((TextView)v);
            if(v instanceof ImageView) styleImage((ImageView)v);
            if(v instanceof ViewGroup) styleTree(a,(ViewGroup)v);
        }
    }

    private static void styleButton(Button b){
        if(Boolean.TRUE.equals(b.getTag())) return;
        b.setTag(Boolean.TRUE);
        b.setAllCaps(false); b.setEnabled(true); b.setClickable(true); b.setFocusable(true);
        b.setTextSize(Math.max(18,b.getTextSize()/b.getResources().getDisplayMetrics().scaledDensity));
        b.setTypeface(android.graphics.Typeface.DEFAULT,android.graphics.Typeface.BOLD);
        b.setMinHeight(dp(b,68)); b.setMinimumHeight(dp(b,68)); b.setMinWidth(0); b.setMinimumWidth(0);
        b.setPadding(dp(b,14),0,dp(b,14),0); b.setGravity(android.view.Gravity.CENTER);
        StateListDrawable states=new StateListDrawable();
        states.addState(new int[]{android.R.attr.state_pressed},face(b,PRESSED,2));
        states.addState(new int[]{-android.R.attr.state_enabled},face(b,Color.rgb(45,45,45),1));
        states.addState(new int[]{},face(b,DARK,3));
        b.setBackground(states); b.setElevation(dp(b,7)); b.setTranslationZ(dp(b,1));
        b.setOnTouchListener((v,e)->{
            if(e.getAction()==MotionEvent.ACTION_DOWN){v.animate().scaleX(.97f).scaleY(.94f).translationZ(0).setDuration(80).start();}
            else if(e.getAction()==MotionEvent.ACTION_UP||e.getAction()==MotionEvent.ACTION_CANCEL){v.animate().scaleX(1f).scaleY(1f).translationZ(dp(v,1)).setDuration(110).start();}
            return false;
        });
    }

    private static GradientDrawable face(View v,int color,int stroke){GradientDrawable d=new GradientDrawable();d.setColor(color);d.setCornerRadius(dp(v,16));d.setStroke(dp(v,stroke),GOLD);return d;}
    private static void styleText(TextView t){
        if(t.getText()==null)return;
        t.setIncludeFontPadding(true);
        if(t.getTextSize()<dp(t,16)) t.setTextSize(16);
        if(t.getGravity()==0)t.setGravity(android.view.Gravity.CENTER_VERTICAL|android.view.Gravity.RIGHT);
    }
    private static void styleImage(ImageView v){
        if(v.getTag()!=null)return;
        v.setTag("wethaq_avatar_style");
        v.setScaleType(ImageView.ScaleType.CENTER_CROP);
    }
    private static int dp(View v,int n){return (int)(n*v.getResources().getDisplayMetrics().density+.5f);}

    public static void loadAvatar(String id,String token,ImageView view){
        if(id==null||id.trim().isEmpty()||token==null||token.isEmpty())return;
        new Thread(()->{try{
            HttpURLConnection c=(HttpURLConnection)new URL(API+"/api/users/"+URLEncoder.encode(id,"UTF-8")+"/avatar").openConnection();
            c.setRequestProperty("Authorization","Bearer "+token);c.setConnectTimeout(5000);c.setReadTimeout(7000);
            if(c.getResponseCode()!=200){c.disconnect();return;}
            InputStream in=c.getInputStream();ByteArrayOutputStream out=new ByteArrayOutputStream();byte[] buf=new byte[4096];int n;while((n=in.read(buf))!=-1)out.write(buf,0,n);in.close();c.disconnect();
            String data=new JSONObject(new String(out.toByteArray(),StandardCharsets.UTF_8)).optString("imageBase64","");
            if(data.isEmpty())return;byte[] raw=android.util.Base64.decode(data,android.util.Base64.DEFAULT);Bitmap bm=BitmapFactory.decodeByteArray(raw,0,raw.length);if(bm!=null)view.post(()->view.setImageBitmap(bm));
        }catch(Exception ignored){}}
        ).start();
    }

    public static String contactIdForName(android.content.Context c,String name){
        try{String raw=c.getSharedPreferences("wethaq",0).getString("saved_contacts","[]");JSONArray a=new JSONArray(raw);for(int i=0;i<a.length();i++){JSONObject o=a.optJSONObject(i);if(o!=null&&name.equals(o.optString("name")))return o.optString("wethaq_id");}}
        catch(Exception ignored){}return "";
    }
}
