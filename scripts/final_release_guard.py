from pathlib import Path
import re

MAIN = Path('app/src/main/java/com/wethaq/app/MainActivity.java').read_text(encoding='utf-8')
ADMIN = Path('app/src/main/java/com/wethaq/app/AdminActivity.java').read_text(encoding='utf-8')
PUBLIC = Path('app/src/main/java/com/wethaq/app/PublicAdministrationActivity.java').read_text(encoding='utf-8')
MANIFEST = Path('app/src/main/AndroidManifest.xml').read_text(encoding='utf-8')
SERVER = Path('backend/server.js').read_text(encoding='utf-8')

# Founder panel isolation.
assert 'android:name=".AdminActivity" android:exported="false"' in MANIFEST
assert 'android:name=".PublicAdministrationActivity" android:exported="false"' in MANIFEST
assert 'if(isOwner()||isAdminRole())menu("🛡  لوحة التحكم الإدارية",this::adminScreen);' in MAIN
assert 'this::publicAdministrationScreen' in MAIN
assert 'putString("admin_role","founder")' not in MAIN
assert 'putString("admin_role","deputy1")' not in MAIN
assert 'putString("admin_role","deputy2")' not in MAIN
assert 'putString("admin_role","deputy3")' not in MAIN
assert 'putString("admin_role","admin_member")' not in MAIN
assert 'function founderOnly' in SERVER
assert 'function rbac' in SERVER
assert 'function roleCanBan' in SERVER

# Private code must never enter public UI / public serializer.
assert 'personal_code_hash' not in PUBLIC
assert 'admin_code' not in PUBLIC
assert 'personal_code' not in PUBLIC.lower()
public_user_match = re.search(r'function publicUser\(.*?\}', SERVER)
assert public_user_match and 'personal_code_hash' not in public_user_match.group(0)
assert 'personal_code_hash' in SERVER
assert '/api/profile/personal-code' in SERVER
assert 'personalCodeHash' in SERVER

# Required administration capabilities.
assert '/api/public/administration' in SERVER
assert '/api/admin/audit-log' in SERVER
assert '10' in PUBLIC
assert 'members' in PUBLIC
assert 'setBusy(boolean busy)' in ADMIN
assert 'جاري الاتصال بالخادم' in ADMIN
assert 'تم تأكيد العملية من الخادم' in ADMIN
assert 'تعذر الاتصال بالخادم' in ADMIN
assert 'هل أنت متأكد' in ADMIN
assert 'حظر نهائي' in ADMIN

# Call controls / media wiring preserved.
assert 'startAudioCall' in MAIN
assert 'startVideoCall' in MAIN
assert 'pendingAudioData' in MAIN
assert 'pendingImageData' in MAIN

# Startup loading repair is preserved.
assert 'warmBackend' in MAIN
assert 'setConnectTimeout(4000)' in MAIN
assert 'setReadTimeout(6000)' in MAIN

# Prevent known broken pattern from returning.
assert 'savePersonalCode(pc.getText().toString())' not in MAIN
assert 'savePersonalCode(code);auth(' not in MAIN
assert 'q.put("personalCodeHash",pendingPersonalCodeHash)' in MAIN
assert 'String pcHash=pendingPersonalCodeHash;' in MAIN

print('WETHAQ_FINAL_RELEASE_GUARD_OK')
