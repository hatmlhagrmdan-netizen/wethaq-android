package com.wethaq.app;

import android.content.Context;
import android.app.Activity;
import android.app.Application;
import android.graphics.*;
import android.graphics.drawable.*;
import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.os.Handler;
import android.os.Looper;
import android.view.*;
import android.widget.*;
import org.json.*;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;

public final class WethaqUi{
    private static final int GOLD=Color.rgb(229,193,71),GOLD_SOFT=Color.rgb(246,222,132),DARK=Color.rgb(10,29,43),PRESSED=Color.rgb(18,50,71),MUTED=Color.rgb(165,178,188);
    private static final String API=WethaqConfig.API;
    private WethaqUi(){}

    public static void apply(Application app,Activity a){
        Window w=a.getWindow();
        w.setStatusBarColor(Color.rgb(3,10,16));
        w.setNavigationBarColor(Color.rgb(2,7,12));
        w.getDecorView().setSystemUiVisibility(w.getDecorView().getSystemUiVisibility() & ~View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR & ~View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR);
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
        if("wethaq_button".equals(b.getTag()))return;
        b.setTag("wethaq_button");
        b.setAllCaps(false);
        b.setTypeface(Typeface.DEFAULT,Typeface.BOLD);
        b.setMinHeight(dp(b,68));
        b.setMinimumHeight(dp(b,68));
        b.setMinWidth(0);
        b.setMinimumWidth(0);
        b.setPadding(dp(b,14),0,dp(b,14),0);
        b.setGravity(Gravity.CENTER);
        b.setTextColor(Color.WHITE);
        b.setIncludeFontPadding(false);
        b.setBackgroundResource(R.drawable.bg_wethaq_button);
        b.setElevation(dp(b,6));
        b.setTranslationZ(dp(b,1));
        if(b.getContentDescription()==null||b.getContentDescription().length()==0)b.setContentDescription(b.getText());
        b.setOnTouchListener((v,e)->{
            if(e.getAction()==MotionEvent.ACTION_DOWN)
                v.animate().scaleX(.985f).scaleY(.97f).translationZ(0).setDuration(70).start();
            else if(e.getAction()==MotionEvent.ACTION_UP||e.getAction()==MotionEvent.ACTION_CANCEL)
                v.animate().scaleX(1f).scaleY(1f).translationZ(dp(v,1)).setDuration(110).start();
            return false;
        });
    }

    private static void styleField(EditText e){
        if(Boolean.TRUE.equals(e.getTag()))return;
        e.setTag(Boolean.TRUE);
        e.setTextColor(Color.WHITE);
        e.setHintTextColor(MUTED);
        e.setBackgroundResource(R.drawable.bg_wethaq_field);
        e.setPadding(dp(e,14),0,dp(e,14),0);
        e.setGravity(Gravity.RIGHT|Gravity.CENTER_VERTICAL);
        e.setMinHeight(dp(e,60));
        e.setSelectAllOnFocus(false);
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
        if(t.getGravity()==0)t.setGravity(Gravity.CENTER_VERTICAL|Gravity.RIGHT);
        String s=t.getText().toString().trim();
        if(s.isEmpty()||Boolean.TRUE.equals(t.getTag()))return;

        if(s.equals("وَثاق")||s.equals("هوية رقمية آمنة")||t.getTextSize()>=dp(t,22)){
            t.setTypeface(Typeface.DEFAULT,Typeface.BOLD);
            t.setTextColor(s.equals("وَثاق")?GOLD:GOLD_SOFT);
            t.setShadowLayer(dp(t,5),0,dp(t,2),Color.argb(120,0,0,0));
        }

        String[] p=s.split("\n");
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
        d.setColor(Color.rgb(17,46,62));
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

    public static View liveHero(Activity a,String headline,String subtitle){
        FrameLayout shell=new FrameLayout(a);
        shell.setTag("wethaq_live_hero");
        shell.setClipToOutline(true);
        GradientDrawable panel=new GradientDrawable();
        panel.setCornerRadius(dp(shell,24));
        panel.setStroke(dp(shell,1),Color.argb(170,229,193,71));
        panel.setColor(Color.rgb(5,16,25));
        shell.setBackground(panel);

        ImageView image=new ImageView(a);
        image.setTag("wethaq_live_image");
        image.setImageResource(R.drawable.profile_photo);
        image.setScaleType(ImageView.ScaleType.CENTER_CROP);
        image.setAlpha(.98f);
        shell.addView(image,new FrameLayout.LayoutParams(-1,-1));

        ViewShade shade=new ViewShade(a);
        shell.addView(shade,new FrameLayout.LayoutParams(-1,-1));

        ImageView portrait=new ImageView(a);
        portrait.setTag("wethaq_live_portrait");
        portrait.setImageResource(R.drawable.profile_photo);
        portrait.setScaleType(ImageView.ScaleType.CENTER_CROP);
        portrait.setBackground(ovalBorder(a));
        portrait.setClipToOutline(true);
        if(Build.VERSION.SDK_INT>=21)portrait.setOutlineProvider(new ViewOutlineProvider(){
            @Override public void getOutline(View v,Outline o){o.setOval(0,0,v.getWidth(),v.getHeight());}
        });
        FrameLayout.LayoutParams pp=new FrameLayout.LayoutParams(dp(shell,76),dp(shell,76),Gravity.RIGHT|Gravity.TOP);
        pp.setMargins(0,dp(shell,14),dp(shell,14),0);
        shell.addView(portrait,pp);

        LinearLayout text=new LinearLayout(a);
        text.setOrientation(LinearLayout.VERTICAL);
        text.setGravity(Gravity.RIGHT);
        TextView h=new TextView(a);
        h.setText(headline);
        h.setTextColor(GOLD);
        h.setTextSize(27);
        h.setTypeface(Typeface.DEFAULT,Typeface.BOLD);
        h.setShadowLayer(dp(shell,5),0,dp(shell,2),Color.BLACK);
        h.setGravity(Gravity.RIGHT);
        TextView sub=new TextView(a);
        sub.setText(subtitle);
        sub.setTextColor(Color.WHITE);
        sub.setTextSize(14);
        sub.setGravity(Gravity.RIGHT);
        sub.setShadowLayer(dp(shell,4),0,dp(shell,2),Color.BLACK);
        text.addView(h,new LinearLayout.LayoutParams(-1,-2));
        text.addView(sub,new LinearLayout.LayoutParams(-1,-2));
        FrameLayout.LayoutParams tp=new FrameLayout.LayoutParams(-1,-2,Gravity.RIGHT|Gravity.BOTTOM);
        tp.setMargins(dp(shell,16),0,dp(shell,18),dp(shell,16));
        shell.addView(text,tp);

        TextView live=new TextView(a);
        live.setText("● LIVE • هوية وَثاق");
        live.setTextColor(GOLD_SOFT);
        live.setTextSize(11);
        live.setGravity(Gravity.CENTER);
        live.setPadding(dp(shell,12),0,dp(shell,12),0);
        GradientDrawable lb=new GradientDrawable();
        lb.setColor(Color.argb(205,6,25,38));
        lb.setCornerRadius(dp(shell,20));
        lb.setStroke(dp(shell,1),Color.argb(165,229,193,71));
        live.setBackground(lb);
        FrameLayout.LayoutParams lp=new FrameLayout.LayoutParams(-2,dp(shell,30),Gravity.LEFT|Gravity.TOP);
        lp.setMargins(dp(shell,14),dp(shell,14),0,0);
        shell.addView(live,lp);

        AnimatorSet motion=new AnimatorSet();
        ObjectAnimator sx=ObjectAnimator.ofFloat(image,View.SCALE_X,1.0f,1.085f);
        ObjectAnimator sy=ObjectAnimator.ofFloat(image,View.SCALE_Y,1.0f,1.085f);
        ObjectAnimator tx=ObjectAnimator.ofFloat(image,View.TRANSLATION_X,-dp(shell,8),dp(shell,8));
        sx.setDuration(7600);sy.setDuration(7600);tx.setDuration(7600);
        sx.setRepeatMode(ObjectAnimator.REVERSE);sy.setRepeatMode(ObjectAnimator.REVERSE);tx.setRepeatMode(ObjectAnimator.REVERSE);
        sx.setRepeatCount(ObjectAnimator.INFINITE);sy.setRepeatCount(ObjectAnimator.INFINITE);tx.setRepeatCount(ObjectAnimator.INFINITE);
        motion.playTogether(sx,sy,tx);
        motion.start();

        final String[] remote={
            "https://images.unsplash.com/photo-1550751827-4bd374c3f58b?auto=format&fit=crop&w=1200&q=88",
            "https://images.unsplash.com/photo-1451187580459-43490279c0fa?auto=format&fit=crop&w=1200&q=88",
            "https://images.unsplash.com/photo-1518770660439-4636190af475?auto=format&fit=crop&w=1200&q=88"
        };
        Handler handler=new Handler(Looper.getMainLooper());
        final int[] index={-1};
        Runnable[] cycle=new Runnable[1];
        cycle[0]=()->{
            int next=(index[0]+1)%remote.length;
            index[0]=next;
            new Thread(()->{
                Bitmap b=downloadBitmap(remote[next]);
                if(b==null)return;
                handler.post(()->{
                    image.animate().alpha(0f).setDuration(220).withEndAction(()->{
                        image.setImageBitmap(b);
                        image.setAlpha(0f);
                        image.animate().alpha(.98f).setDuration(520).start();
                    }).start();
                });
            },"wethaq-live-image").start();
            handler.postDelayed(cycle[0],7800);
        };
        handler.postDelayed(cycle[0],1200);
        shell.addOnAttachStateChangeListener(new View.OnAttachStateChangeListener(){
            @Override public void onViewAttachedToWindow(View v){}
            @Override public void onViewDetachedFromWindow(View v){
                handler.removeCallbacks(cycle[0]);
                motion.cancel();
            }
        });
        return shell;
    }

    private static GradientDrawable ovalBorder(View v){
        GradientDrawable d=new GradientDrawable();
        d.setShape(GradientDrawable.OVAL);
        d.setColor(Color.argb(40,0,0,0));
        d.setStroke(dp(v,3),GOLD);
        return d;
    }

    private static Bitmap downloadBitmap(String url){
        HttpURLConnection c=null;
        try{
            c=(HttpURLConnection)new URL(url).openConnection();
            c.setConnectTimeout(7000);
            c.setReadTimeout(10000);
            c.setUseCaches(true);
            c.setRequestProperty("Accept","image/avif,image/webp,image/apng,image/*,*/*;q=0.8");
            c.setRequestProperty("User-Agent","Wethaq-Android/2.5");
            if(c.getResponseCode()!=200)return null;
            try(InputStream in=c.getInputStream()){
                return BitmapFactory.decodeStream(in);
            }
        }catch(Exception ignored){
            return null;
        }finally{
            if(c!=null)c.disconnect();
        }
    }

    private static final class ViewShade extends View{
        private final Paint p=new Paint(Paint.ANTI_ALIAS_FLAG);
        ViewShade(Context c){super(c);}
        @Override protected void onDraw(Canvas c){
            super.onDraw(c);
            LinearGradient g=new LinearGradient(0,0,0,getHeight(),
                    new int[]{Color.argb(45,0,0,0),Color.argb(105,0,0,0),Color.argb(220,2,9,15)},
                    new float[]{0f,.48f,1f},Shader.TileMode.CLAMP);
            p.setShader(g);
            c.drawRect(0,0,getWidth(),getHeight(),p);
            p.setShader(null);
            p.setColor(Color.argb(85,229,193,71));
            c.drawCircle(getWidth()*0.16f,getHeight()*0.16f,getWidth()*0.13f,p);
        }
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
