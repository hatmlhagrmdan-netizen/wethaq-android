from pathlib import Path

MAIN=Path('app/src/main/java/com/wethaq/app/MainActivity.java')
VIDEO=Path('app/src/main/java/com/wethaq/app/VideoCallActivity.java')
UI=Path('app/src/main/java/com/wethaq/app/WethaqUi.java')
APP=Path('app/src/main/java/com/wethaq/app/WethaqApp.java')
SERVER=Path('backend/server.js')

a=MAIN.read_text(encoding='utf-8'); v=VIDEO.read_text(encoding='utf-8'); u=UI.read_text(encoding='utf-8'); ap=APP.read_text(encoding='utf-8'); s=SERVER.read_text(encoding='utf-8')

if 'private void auth(String n,String y,boolean create,String personalCode)' not in a and 'private void auth(String n,String y,boolean create)' not in a:
    raise SystemExit('Missing runtime invariant: MainActivity auth method')

checks=[
    (a,'warmBackend','backend warm-up'),
    (v,'pollInFlight','call polling guard'),
    (v,'640,360,20','bounded video capture'),
    (s,'personalCodeHash','server personal-code handling'),
]
if 'callIo' not in v and not ('pollIo' in v and 'signalIo' in v):
    raise SystemExit('Missing runtime invariant: dedicated call executors')
for text,needle,label in checks:
    if needle not in text:
        raise SystemExit(f'Missing runtime invariant: {label}')
print('WETHAQ_RUNTIME_PERFORMANCE_REPAIR_OK')
