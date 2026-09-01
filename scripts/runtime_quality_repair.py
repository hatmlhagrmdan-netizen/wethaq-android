from pathlib import Path

VIDEO = Path('app/src/main/java/com/wethaq/app/VideoCallActivity.java')

s = VIDEO.read_text(encoding='utf-8')

# Make call termination reliable: do not queue the final signal on an executor
# that is immediately shut down. Keep the existing signaling endpoint/system.
if 'private void sendEndSignal()' not in s:
    anchor = '    private void fail(String text){'
    method = '''    private void sendEndSignal(){
        if(target==null||token.isEmpty())return;
        HttpURLConnection c=null;
        try{
            JSONObject q=new JSONObject().put("to",target).put("type","end").put("payload","{}");
            c=(HttpURLConnection)new URL(API+"/api/calls/signal").openConnection();
            c.setRequestMethod("POST");c.setDoOutput(true);c.setConnectTimeout(2500);c.setReadTimeout(2500);
            c.setRequestProperty("Authorization","Bearer "+token);c.setRequestProperty("Content-Type","application/json");
            try(OutputStream o=c.getOutputStream()){o.write(q.toString().getBytes(StandardCharsets.UTF_8));}
            c.getResponseCode();
        }catch(Exception ignored){}
        finally{if(c!=null)c.disconnect();}
    }
'''
    if anchor not in s: raise SystemExit('missing fail anchor')
    s = s.replace(anchor, method + anchor, 1)

old = 'if(poll!=null)handler.removeCallbacks(poll);if(target!=null&&!token.isEmpty())sendSignal("end","{}");'
new = 'if(poll!=null)handler.removeCallbacks(poll);sendEndSignal();'
if old in s:
    s = s.replace(old, new, 1)

old_destroy='@Override protected void onDestroy(){if(!cleaned&&poll!=null)handler.removeCallbacks(poll);super.onDestroy();}'
new_destroy='@Override protected void onDestroy(){if(!cleaned)endCall();else if(poll!=null)handler.removeCallbacks(poll);super.onDestroy();}'
if old_destroy in s:
    s = s.replace(old_destroy, new_destroy, 1)

# The production source currently uses separate read/write executors (pollIo/signalIo).
# Accept that design, while keeping backward compatibility with the earlier callIo name.
VIDEO.write_text(s, encoding='utf-8')
check = VIDEO.read_text(encoding='utf-8')
if not ('callIo' in check or ('pollIo' in check and 'signalIo' in check)):
    raise SystemExit('missing runtime quality invariant: call executors')
for needle in ('pollInFlight','sendEndSignal','@Override protected void onDestroy(){if(!cleaned)endCall()'):
    if needle not in check:
        raise SystemExit(f'missing runtime quality invariant: {needle}')
print('WETHAQ_RUNTIME_QUALITY_REPAIR_OK')
