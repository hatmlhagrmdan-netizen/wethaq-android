package com.wethaq.app;

import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.animation.ValueAnimator;
import android.app.Activity;
import android.app.Application;
import android.graphics.*;
import android.graphics.drawable.*;
import android.view.*;
import android.view.animation.AccelerateDecelerateInterpolator;
import android.widget.*;
import org.json.*;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;

/** Shared Wethaq visual layer: premium RTL styling, visual hierarchy, identity cards, avatars and founder-brand motion. */
public final class WethaqUi{
 private static final int GOLD=Color.rgb(212,175,55),BRIGHT_GOLD=Color.rgb(255,220,110),DARK=Color.rgb(28,28,32),PRESSED=Color.rgb(55,45,24),FIELD=Color.rgb(18,19,23),FIELD_STROKE=Color.rgb(78,80,88),CARD=Color.rgb(19,19,23),CARD_STROKE=Color.rgb(64,61,49);
 private static final String API="https://wethaq-backend-production.up.railway.app";
 private static final String FOUNDER_NAME="حاتم حسين الحاج رمضان";
 private WethaqUi(){}
 public static void apply(Application app,Activity a){
  View r=a.findViewById(android.R.id.content);
  if(!(r instanceof ViewGroup))return;
  ViewGroup root=(ViewGroup)r;
  styleRoot(root);
  styleTree(a,root);
  if(!Boolean.TRUE.equals(root.getTag(R.id.wethaq_ui_root_marker))){
   root.setTag(R.id.wethaq_ui_root_marker,Boolean.TRUE);
   root.getViewTreeObserver().addOnGlobalLayoutListener(()->styleTree(a,root));
  }
 }
 private static void styleRoot(ViewGroup root){
  GradientDrawable bg=new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,new int[]{Color.rgb(9,9,12),Color.rgb(18,17,14),Color.rgb(7,7,9)});
  root.setBackground(bg);
 }
 private static void styleTree(Activity a,ViewGroup g){for(int i=0;i<g.getChildCount();i++){View v=g.getChildAt(i);if(v instanceof Button)styleButton((Button)v);else if(v instanceof EditText)styleField((EditText)v);else if(v instanceof TextView)styleText(a,(TextView)v);if(v instanceof ImageView)styleImage((ImageView)v);if(v instanceof ViewGroup)styleTree(a,(ViewGroup)v);}}
 private static void styleButton(Button b){if(Boolean.TRUE.equals(b.getTag()))return;b.setTag(Boolean.TRUE);b.setAllCaps(false);b.setEnabled(true);b.setClickable(true);b.setFocusable(true);b.setTextSize(Math.max(18,b.getTextSize()/b.getResources().getDisplayMetrics().scaledDensity));b.setTypeface(Typeface.DEFAULT,Typeface.BOLD);b.setMinHeight(dp(b,68));b.setMinimumHeight(dp(b,68));b.setMinWidth(0);b.setMinimumWidth(0);b.setPadding(dp(b,14),0,dp(b,14),0);b.setGravity(Gravity.CENTER);StateListDrawable s=new StateListDrawable();s.addState(new int[]{android.R.attr.state_pressed},face(b,PRESSED,2));s.addState(new int[]{-android.R.attr.state_enabled},face(b,Color.rgb(45,45,45),1));s.addState(new int[]{},face(b,DARK,3));b.setBackground(s);b.setElevation(dp(b,7));b.setTranslationZ(dp(b,1));b.setOnTouchListener((v,e)->{if(e.getAction()==MotionEvent.ACTION_DOWN)v.animate().scaleX(.97f).scaleY(.94f).translationZ(0).setDuration(80).start();else if(e.getAction()==MotionEvent.ACTION_UP||e.getAction()==MotionEvent.ACTION_CANCEL)v.animate().scaleX(1f).scaleY(1f).translationZ(dp(v,1)).setDuration(110).start();return false;});}
 private static void styleField(EditText e){if(Boolean.TRUE.equals(e.getTag()))return;e.setTag(Boolean.TRUE);e.setTextColor(Color.WHITE);e.setHintTextColor(Color.rgb(175,176,184));e.setTextSize(Math.max(17,e.getTextSize()/e.getResources().getDisplayMetrics().scaledDensity));e.setGravity(Gravity.RIGHT|Gravity.CENTER_VERTICAL);e.setPadding(dp(e,16),0,dp(e,16),0);e.setMinHeight(dp(e,64));e.setBackground(fieldFace(e,FIELD,FIELD_STROKE,2));e.setOnFocusChangeListener((v,focused)->v.setBackground(fieldFace(v,FIELD,focused?GOLD:FIELD_STROKE,focused?3:2)));}
 private static GradientDrawable fieldFace(View v,int c,int stroke,int width){GradientDrawable d=new GradientDrawable();d.setColor(c);d.setCornerRadius(dp(v,16));d.setStroke(dp(v,width),stroke);return d;}
 private static GradientDrawable face(View v,int c,int st){GradientDrawable d=new GradientDrawable();d.setColor(c);d.setCornerRadius(dp(v,16));d.setStroke(dp(v,st),GOLD);return d;}
 private static GradientDrawable cardFace(View v,boolean goldEdge){GradientDrawable d=new GradientDrawable();d.setColor(CARD);d.setCornerRadius(dp(v,18));d.setStroke(dp(v,goldEdge?2:1),goldEdge?GOLD:CARD_STROKE);return d;}
 private static void styleText(Activity a,TextView t){if(t.getText()==null)return;t.setIncludeFontPadding(true);if(t.getTextSize()<dp(t,16))t.setTextSize(16);if(t.getGravity()==0)t.setGravity(Gravity.CENTER_VERTICAL|Gravity.RIGHT);String s=t.getText().toString().trim();if(s.isEmpty()||Boolean.TRUE.equals(t.getTag()))return;if(s.contains(FOUNDER_NAME)||s.contains("مدير ومؤسس وثاق")){animateFounderBrand(t);t.setTag(Boolean.TRUE);return;}if(s.contains("العضوية الإدارية في وَثاق")){styleInfoCard(t,true);t.setTag(Boolean.TRUE);return;}if(s.contains("\n")&&s.length()>18&&s.length()<180){styleIdentityCard(t);t.setTag(Boolean.TRUE);return;}t.setTag(Boolean.TRUE);}
 private static void styleIdentityCard(TextView t){t.setTextSize(18);t.setTypeface(Typeface.DEFAULT,Typeface.BOLD);t.setTextColor(Color.WHITE);t.setGravity(Gravity.CENTER);t.setPadding(dp(t,18),dp(t,16),dp(t,18),dp(t,16));t.setBackground(cardFace(t,true));t.setElevation(dp(t,3));}
 private static void styleInfoCard(TextView t,boolean premium){t.setTextSize(15);t.setTypeface(Typeface.DEFAULT,Typeface.NORMAL);t.setTextColor(GOLD);t.setGravity(Gravity.CENTER);t.setPadding(dp(t,18),dp(t,18),dp(t,18),dp(t,18));t.setBackground(cardFace(t,premium));t.setElevation(dp(t,2));}
 /** Premium restrained motion: the founder identity gently breathes and catches a gold highlight, pausing when detached. */
 private static void animateFounderBrand(TextView t){t.setTypeface(Typeface.DEFAULT,Typeface.BOLD);t.setTextColor(BRIGHT_GOLD);t.setLetterSpacing(.018f);t.setShadowLayer(dp(t,10),0,dp(t,2),Color.argb(150,212,175,55));AnimatorSet set=new AnimatorSet();ObjectAnimator sx=ObjectAnimator.ofFloat(t,View.SCALE_X,1f,1.018f,1f);ObjectAnimator sy=ObjectAnimator.ofFloat(t,View.SCALE_Y,1f,1.018f,1f);ObjectAnimator alpha=ObjectAnimator.ofFloat(t,View.ALPHA,1f,.92f,1f);sx.setDuration(2400);sy.setDuration(2400);alpha.setDuration(2400);sx.setRepeatCount(ValueAnimator.INFINITE);sy.setRepeatCount(ValueAnimator.INFINITE);alpha.setRepeatCount(ValueAnimator.INFINITE);set.playTogether(sx,sy,alpha);set.setInterpolator(new AccelerateDecelerateInterpolator());set.setStartDelay(250);t.addOnAttachStateChangeListener(new View.OnAttachStateChangeListener(){@Override public void onViewAttachedToWindow(View v){if(!set.isStarted())set.start();}@Override public void onViewDetachedFromWindow(View v){if(set.isRunning())set.cancel();}});if(t.getWindowToken()!=null)set.start();}
 private static void styleImage(ImageView v){if(v.getTag()!=null)return;v.setTag("wethaq_avatar_style");v.setScaleType(ImageView.ScaleType.CENTER_CROP);}
 public static void loadAvatar(String id,String token,ImageView view){loadAvatarDrawable(id,token,d->view.post(()->view.setImageDrawable(d)));}
 private interface DrawableConsumer{void accept(Drawable d);}
 private static void loadAvatarDrawable(String id,String token,DrawableConsumer consumer){if(id==null||id.trim().isEmpty()||token==null||token.isEmpty())return;new Thread(()->{try{HttpURLConnection c=(HttpURLConnection)new URL(API+"/api/users/"+URLEncoder.encode(id,"UTF-8")+"/avatar").openConnection();c.setRequestProperty("Authorization","Bearer "+token);c.setConnectTimeout(5000);c.setReadTimeout(7000);if(c.getResponseCode()!=200){c.disconnect();return;}InputStream in=c.getInputStream();ByteArrayOutputStream out=new ByteArrayOutputStream();byte[] z=new byte[4096];int n;while((n=in.read(z))!=-1)out.write(z,0,n);in.close();c.disconnect();String data=new JSONObject(new String(out.toByteArray(),StandardCharsets.UTF_8)).optString("imageBase64","");if(data.isEmpty())return;byte[] raw=android.util.Base64.decode(data,android.util.Base64.DEFAULT);Bitmap bm=BitmapFactory.decodeByteArray(raw,0,raw.length);if(bm!=null)consumer.accept(new CircularBitmapDrawable(bm));}catch(Exception ignored){}}).start();}
 public static String contactIdForName(android.content.Context c,String name){try{JSONArray a=new JSONArray(c.getSharedPreferences("wethaq",0).getString("saved_contacts","[]"));for(int i=0;i<a.length();i++){JSONObject o=a.optJSONObject(i);if(o!=null&&name.equals(o.optString("name")))return o.optString("wethaq_id");}}catch(Exception ignored){}return "";}
 private static int dp(View v,int n){return(int)(n*v.getResources().getDisplayMetrics().density+.5f);}
 private static final class CircularBitmapDrawable extends Drawable{final Paint p=new Paint(Paint.ANTI_ALIAS_FLAG);final BitmapShader sh;final Bitmap bm;CircularBitmapDrawable(Bitmap b){bm=b;sh=new BitmapShader(b,Shader.TileMode.CLAMP,Shader.TileMode.CLAMP);p.setShader(sh);}protected void onBoundsChange(Rect r){float sc=Math.max(r.width()/(float)bm.getWidth(),r.height()/(float)bm.getHeight());Matrix m=new Matrix();m.setScale(sc,sc);m.postTranslate((r.width()-bm.getWidth()*sc)/2f,(r.height()-bm.getHeight()*sc)/2f);sh.setLocalMatrix(m);}public void draw(Canvas c){Rect r=getBounds();c.drawCircle(r.centerX(),r.centerY(),Math.min(r.width(),r.height())/2f-1,p);}public void setAlpha(int a){p.setAlpha(a);}public void setColorFilter(ColorFilter f){p.setColorFilter(f);}public int getOpacity(){return PixelFormat.TRANSLUCENT;}public int getIntrinsicWidth(){return 42;}public int getIntrinsicHeight(){return 42;}}
}
