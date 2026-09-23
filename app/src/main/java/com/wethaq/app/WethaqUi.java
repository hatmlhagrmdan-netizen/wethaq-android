package com.wethaq.app;

import android.app.Activity;
import android.app.Application;
import android.graphics.*;
import android.graphics.drawable.*;
import android.view.*;
import android.widget.*;
import org.json.*;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;

public final class WethaqUi{
    private static final int GOLD=Color.rgb(229,193,71),DARK=Color.rgb(10,29,43),PRESSED=Color.rgb(18,50,71);
    private static final String API=WethaqConfig.API;
    private WethaqUi(){}

    public static void apply(Application app,Activity a){
        Window w=a.getWindow();
        w.setStatusBarColor(Color.rgb(5,15,23));
        w.setNavigationBarColor(Color.rgb(2,7,12));
        View r=a.findViewById(android.R.id.content);
        if(r instanceof ViewGroup){
            r.setBackgroundResource(R.drawable.bg_wethaq);
            styleTree(a,(ViewGroup)r);
        }
    }

    private static void styleTree(Activity a,ViewGroup g){
        for(int i=0;i<g.getChildCount();i++){
            View v=g.getChildAt(i);
            if(v instanceof Button)styleButton((Button)v);
            else if(v instanceof EditText)styleField((EditText)v);
            else if(v instanceof TextView)styleText(a,(TextView)v);
            if(v instanceof ImageView)styleImage((ImageView)v);
            if(v instanceof ViewGroup)styleTree(a,(ViewGroup)v);
        }
    }

    private static void styleButton(Button b){
        if(Boolean.TRUE.equals(b.getTag()))return;
        b.setTag(Boolean.TRUE);
        b.setAllCaps(false);
        b.setTypeface(Typeface.DEFAULT,Typeface.BOLD);
        b.setMinHeight(dp(b,68));
        b.setMinimumHeight(dp(b,68));
        b.setMinWidth(0);
        b.setMinimumWidth(0);
        b.setPadding(dp(b,14),0,dp(b,14),0);
        b.setGravity(Gravity.CENTER);
        b.setTextColor(Color.WHITE);
        StateListDrawable s=new StateListDrawable();
        s.addState(new int[]{android.R.attr.state_pressed},face(b,PRESSED,2));
        s.addState(new int[]{-android.R.attr.state_enabled},face(b,Color.rgb(38,48,56),1));
        s.addState(new int[]{},face(b,DARK,1));
        b.setBackground(s);
        b.setElevation(dp(b,5));
        b.setTranslationZ(dp(b,1));
        b.setOnTouchListener((v,e)->{
            if(e.getAction()==MotionEvent.ACTION_DOWN)
                v.animate().scaleX(.98f).scaleY(.96f).translationZ(0).setDuration(70).start();
            else if(e.getAction()==MotionEvent.ACTION_UP||e.getAction()==MotionEvent.ACTION_CANCEL)
                v.animate().scaleX(1f).scaleY(1f).translationZ(dp(v,1)).setDuration(100).start();
            return false;
        });
    }

    private static void styleField(EditText e){
        if(Boolean.TRUE.equals(e.getTag()))return;
        e.setTag(Boolean.TRUE);
        e.setTextColor(Color.WHITE);
        e.setHintTextColor(Color.rgb(165,178,188));
        e.setBackgroundResource(R.drawable.bg_wethaq_field);
        e.setPadding(dp(e,14),0,dp(e,14),0);
        e.setGravity(Gravity.RIGHT|Gravity.CENTER_VERTICAL);
        e.setMinHeight(dp(e,60));
    }

    private static GradientDrawable face(View v,int c,int st){
        GradientDrawable d=new GradientDrawable();
        d.setColor(c);
        d.setCornerRadius(dp(v,16));
        d.setStroke(dp(v,st),GOLD);
        return d;
    }

    private static void styleText(Activity a,TextView t){
        if(t.getText()==null)return;
        t.setIncludeFontPadding(true);
        if(t.getTextSize()<dp(t,16))t.setTextSize(16);
        if(t.getGravity()==0)t.setGravity(Gravity.CENTER_VERTICAL|Gravity.RIGHT);
        String s=t.getText().toString().trim();
        if(s.isEmpty()||Boolean.TRUE.equals(t.getTag()))return;
        String[] p=s.split("\\n");
        String id="";
        if(p.length>=2&&p[1].trim().length()>=2)id=p[1].trim();
        else id=contactIdForName(a,s);
        if(id.isEmpty())return;
        t.setTag(Boolean.TRUE);
        setDefaultAvatar(t);
        String token=a.getSharedPreferences("wethaq",0).getString("token","");
        loadAvatarDrawable(id,token,d->t.post(()->{
            t.setCompoundDrawablesWithIntrinsicBounds(null,null,d,null);
            t.setCompoundDrawablePadding(dp(t,10));
        }));
    }

    private static void setDefaultAvatar(TextView t){
        t.setCompoundDrawablesWithIntrinsicBounds(null,null,placeholder(t),null);
    }

    private static Drawable placeholder(View v){
        GradientDrawable d=new GradientDrawable();
        d.setShape(GradientDrawable.OVAL);
        d.setColor(Color.rgb(48,48,54));
        d.setStroke(dp(v,2),GOLD);
        d.setSize(dp(v,42),dp(v,42));
        return d;
    }

    private static void styleImage(ImageView v){
        if(v.getTag()!=null)return;
        v.setTag("wethaq_avatar_style");
        v.setScaleType(ImageView.ScaleType.CENTER_CROP);
    }

    private static int dp(View v,int n){
        return(int)(n*v.getResources().getDisplayMetrics().density+.5f);
    }

    public static void loadAvatar(String id,String token,ImageView view){
        loadAvatarDrawable(id,token,d->view.post(()->view.setImageDrawable(d)));
    }

    private interface DrawableConsumer{void accept(Drawable d);}

    private static void loadAvatarDrawable(String id,String token,DrawableConsumer consumer){
        if(id==null||id.trim().isEmpty()||token==null||token.isEmpty())return;
        new Thread(()->{
            try{
                HttpURLConnection c=(HttpURLConnection)new URL(API+"/api/users/"+URLEncoder.encode(id,"UTF-8")+"/avatar").openConnection();
                c.setRequestProperty("Authorization","Bearer "+token);
                c.setConnectTimeout(5000);
                c.setReadTimeout(7000);
                if(c.getResponseCode()!=200){c.disconnect();return;}
                InputStream in=c.getInputStream();
                ByteArrayOutputStream out=new ByteArrayOutputStream();
                byte[] z=new byte[4096];int n;
                while((n=in.read(z))!=-1)out.write(z,0,n);
                in.close();c.disconnect();
                String data=new JSONObject(new String(out.toByteArray(),StandardCharsets.UTF_8)).optString("imageBase64","");
                if(data.isEmpty())return;
                byte[] raw=android.util.Base64.decode(data,android.util.Base64.DEFAULT);
                Bitmap bm=BitmapFactory.decodeByteArray(raw,0,raw.length);
                if(bm!=null)consumer.accept(new CircularBitmapDrawable(bm));
            }catch(Exception ignored){}
        }).start();
    }

    public static String contactIdForName(android.content.Context c,String name){
        try{
            JSONArray a=new JSONArray(c.getSharedPreferences("wethaq",0).getString("saved_contacts","[]"));
            for(int i=0;i<a.length();i++){
                JSONObject o=a.optJSONObject(i);
                if(o!=null&&name.equals(o.optString("name")))return o.optString("wethaq_id");
            }
        }catch(Exception ignored){}
        return "";
    }

    private static final class CircularBitmapDrawable extends Drawable{
        final Paint p=new Paint(Paint.ANTI_ALIAS_FLAG);
        final BitmapShader sh;
        final Bitmap bm;
        CircularBitmapDrawable(Bitmap b){
            bm=b;
            sh=new BitmapShader(b,Shader.TileMode.CLAMP,Shader.TileMode.CLAMP);
            p.setShader(sh);
        }
        protected void onBoundsChange(Rect r){
            float sc=Math.max(r.width()/(float)bm.getWidth(),r.height()/(float)bm.getHeight());
            Matrix m=new Matrix();
            m.setScale(sc,sc);
            m.postTranslate((r.width()-bm.getWidth()*sc)/2f,(r.height()-bm.getHeight()*sc)/2f);
            sh.setLocalMatrix(m);
        }
        public void draw(Canvas c){
            Rect r=getBounds();
            c.drawCircle(r.centerX(),r.centerY(),Math.min(r.width(),r.height())/2f-1,p);
        }
        public void setAlpha(int a){p.setAlpha(a);}
        public void setColorFilter(ColorFilter f){p.setColorFilter(f);}
        public int getOpacity(){return PixelFormat.TRANSLUCENT;}
        public int getIntrinsicWidth(){return 42;}
        public int getIntrinsicHeight(){return 42;}
    }
}