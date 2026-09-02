from pathlib import Path
import re

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

# Normalize common one-line lifecycle variants emitted by earlier repair scripts.
# The semantic invariant is: onDestroy must route through endCall before super.
variants = [
    '@Override protected void onDestroy(){if(!cleaned)endCall();else if(poll!=null)handler.removeCallbacks(poll);super.onDestroy();}',
    '@Override protected void onDestroy(){if(!cleaned)endCall();super.onDestroy();}',
    '@Override protected void onDestroy(){if(!cleaned) endCall();super.onDestroy();}',
]
canonical = '@Override protected void onDestroy(){if(!cleaned)endCall();else if(poll!=null)handler.removeCallbacks(poll);super.onDestroy();}'
for variant in variants:
    if variant in s:
        s = s.replace(variant, canonical, 1)
        break

VIDEO.write_text(s, encoding='utf-8')
check = VIDEO.read_text(encoding='utf-8')
if not ('callIo' in check or ('pollIo' in check and 'signalIo' in check)):
    raise SystemExit('missing runtime quality invariant: call executors')
if 'pollInFlight' not in check:
    raise SystemExit('missing runtime quality invariant: pollInFlight')
if 'sendEndSignal' not in check:
    raise SystemExit('missing runtime quality invariant: sendEndSignal')
on_destroy = re.search(r'@Override\s+protected\s+void\s+onDestroy\s*\(\)\s*\{[^}]*endCall\s*\(\)', check, re.S)
if not on_destroy:
    raise SystemExit('missing runtime quality invariant: onDestroy must call endCall')
print('WETHAQ_RUNTIME_QUALITY_REPAIR_OK')
