from pathlib import Path

p = Path('app/src/main/java/com/wethaq/app/MainActivity.java')
s = p.read_text(encoding='utf-8')

# Keep the existing application flow intact, but warm the public backend health
# endpoint asynchronously before the first authentication request. This avoids
# making the login UI pay the Railway cold-start latency while preserving the
# existing authentication and API implementation.
if 'private void warmBackend()' not in s:
    old = '@Override public void onCreate(Bundle b){super.onCreate(b);prefs=getSharedPreferences(P,MODE_PRIVATE);createChannel();if(hasToken())home();else login();}'
    new = '@Override public void onCreate(Bundle b){super.onCreate(b);prefs=getSharedPreferences(P,MODE_PRIVATE);createChannel();warmBackend();if(hasToken())home();else login();}'
    if old not in s:
        raise SystemExit('MainActivity onCreate anchor not found')
    s = s.replace(old, new, 1)
    anchor = '@Override protected void onDestroy(){stopPolling();stopRecording(false);dismissProgress();io.shutdownNow();super.onDestroy();}'
    method = '''private void warmBackend(){io.execute(()->{HttpURLConnection c=null;try{c=(HttpURLConnection)new URL(API+"/health").openConnection();c.setRequestMethod("GET");c.setConnectTimeout(4000);c.setReadTimeout(6000);c.setUseCaches(false);c.getResponseCode();}catch(Exception ignored){}finally{if(c!=null)c.disconnect();}});}\n '''
    if anchor not in s:
        raise SystemExit('MainActivity destroy anchor not found')
    s = s.replace(anchor, method + anchor, 1)

p.write_text(s, encoding='utf-8')
print('WETHAQ_LOADING_REPAIR_OK')
