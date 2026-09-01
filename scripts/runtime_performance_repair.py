from pathlib import Path

MAIN=Path('app/src/main/java/com/wethaq/app/MainActivity.java')
VIDEO=Path('app/src/main/java/com/wethaq/app/VideoCallActivity.java')
UI=Path('app/src/main/java/com/wethaq/app/WethaqUi.java')
APP=Path('app/src/main/java/com/wethaq/app/WethaqApp.java')
SERVER=Path('backend/server.js')

# CI must validate the already-reviewed runtime implementation, not regenerate it.
a=MAIN.read_text(encoding='utf-8'); v=VIDEO.read_text(encoding='utf-8'); u=UI.read_text(encoding='utf-8'); ap=APP.read_text(encoding='utf-8'); s=SERVER.read_text(encoding='utf-8')
checks=[(a,'private void auth(String n,String y,boolean create,String personalCode)','MainActivity auth signature'),(a,'warmBackend','backend warm-up'),(v,'callIo','dedicated call executor'),(v,'pollInFlight','call polling guard'),(v,'640,360,20','bounded video capture'),(s,'personalCodeHash','server personal-code handling')]
for text,needle,label in checks:
    if needle not in text: raise SystemExit(f'Missing runtime invariant: {label}')
print('WETHAQ_RUNTIME_PERFORMANCE_REPAIR_OK')