from pathlib import Path

APP=Path('app/src/main/java/com/wethaq/app/MainActivity.java')
ADMIN=Path('app/src/main/java/com/wethaq/app/AdminActivity.java')
MANIFEST=Path('app/src/main/AndroidManifest.xml')
SERVER=Path('backend/server.js')

# Stable source validator. Do not rewrite working Java or backend code in CI.
a=APP.read_text(encoding='utf-8'); ad=ADMIN.read_text(encoding='utf-8'); m=MANIFEST.read_text(encoding='utf-8'); s=SERVER.read_text(encoding='utf-8')
required_app=['PERSONAL_CODE=','hashPersonalCode','savePersonalCode','publicAdministrationScreen','isAdminRoleName','private void auth(String n,String y,boolean create,String personalCode)']
for x in required_app:
    if x not in a: raise SystemExit(f'Missing MainActivity invariant: {x}')
required_admin=['connectionStatus','auditLog','setBusy','/api/admin/structure']
for x in required_admin:
    if x not in ad: raise SystemExit(f'Missing AdminActivity invariant: {x}')
required_server=['personal_code_hash','/api/public/administration','/api/admin/audit-log','/api/profile/personal-code/verify','personalCodeHash','founderOnly','roleCanBan']
for x in required_server:
    if x not in s: raise SystemExit(f'Missing backend invariant: {x}')
if '.PublicAdministrationActivity' not in m: raise SystemExit('PublicAdministrationActivity missing from Manifest')
if 'android:name=".AdminActivity" android:exported="false"' not in m: raise SystemExit('AdminActivity must remain non-exported')
print('WETHAQ_SPEC_COMPLIANCE_REPAIR_OK')