package com.wethaq.app;

import android.content.Context;
import android.content.SharedPreferences;
import org.json.JSONArray;
import org.json.JSONObject;

public final class CallHistory {
    public static final String PENDING_OUTGOING="pending_outgoing";
    public static final String PENDING_INCOMING="pending_incoming";
    public static final String ANSWERED="answered";
    public static final String MISSED="missed";
    private static final String PREFS="wethaq";
    private static final String PREFIX="call_history_";
    private static final String MAP_PREFIX="pending_call_";
    private static final int MAX_ITEMS=200;
    private CallHistory(){}
    private static SharedPreferences prefs(Context c){return c.getSharedPreferences(PREFS,Context.MODE_PRIVATE);}
    private static String key(Context c){return PREFIX+prefs(c).getString("wethaq_id","guest");}
    private static JSONArray read(Context c){try{return new JSONArray(prefs(c).getString(key(c),"[]"));}catch(Exception e){return new JSONArray();}}
    private static void write(Context c,JSONArray a){prefs(c).edit().putString(key(c),a.toString()).apply();}
    public static long record(Context c,String target,String name,boolean audioOnly,boolean outgoing,String status){
        long id=System.currentTimeMillis();
        try{
            JSONArray a=read(c);JSONObject o=new JSONObject();
            o.put("id",id).put("target",target==null?"":target).put("name",name==null||name.isEmpty()?"مستخدم":name)
             .put("audioOnly",audioOnly).put("outgoing",outgoing).put("status",status).put("time",id);
            a.put(o);
            while(a.length()>MAX_ITEMS){JSONArray n=new JSONArray();for(int i=1;i<a.length();i++)n.put(a.get(i));a=n;}
            write(c,a);
            if(target!=null&&!target.isEmpty())prefs(c).edit().putLong(MAP_PREFIX+target,id).apply();
        }catch(Exception ignored){}
        return id;
    }
    public static long pendingFor(Context c,String target){if(target==null||target.isEmpty())return 0;return prefs(c).getLong(MAP_PREFIX+target,0);}
    public static void clearPending(Context c,String target){if(target!=null&&!target.isEmpty())prefs(c).edit().remove(MAP_PREFIX+target).apply();}
    public static void setStatus(Context c,long id,String status){
        if(id<=0)return;JSONArray a=read(c);
        try{for(int i=0;i<a.length();i++){JSONObject o=a.optJSONObject(i);if(o!=null&&o.optLong("id",0)==id){o.put("status",status).put("updatedAt",System.currentTimeMillis());a.put(i,o);write(c,a);return;}}}catch(Exception ignored){}
    }
    public static void expirePending(Context c,long maxAgeMs){
        long now=System.currentTimeMillis();JSONArray a=read(c);boolean changed=false;
        try{for(int i=0;i<a.length();i++){JSONObject o=a.optJSONObject(i);if(o==null)continue;String s=o.optString("status","");long t=o.optLong("time",now);
            if((PENDING_INCOMING.equals(s)||PENDING_OUTGOING.equals(s))&&now-t>=maxAgeMs){o.put("status",MISSED).put("updatedAt",now);a.put(i,o);clearPending(c,o.optString("target",""));changed=true;}}
        if(changed)write(c,a);}catch(Exception ignored){}
    }
    public static JSONArray missedFor(Context c,String target){
        expirePending(c,60000);JSONArray out=new JSONArray();JSONArray a=read(c);
        try{for(int i=0;i<a.length();i++){JSONObject o=a.optJSONObject(i);if(o!=null&&target!=null&&target.equals(o.optString("target",""))&&MISSED.equals(o.optString("status","")))out.put(o);}}catch(Exception ignored){}
        return out;
    }
}
