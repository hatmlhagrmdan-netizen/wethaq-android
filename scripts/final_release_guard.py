from pathlib import Path
import re

MAIN = Path('app/src/main/java/com/wethaq/app/MainActivity.java').read_text(encoding='utf-8')
ADMIN = Path('app/src/main/java/com/wethaq/app/AdminActivity.java').read_text(encoding='utf-8')
PUBLIC = Path('app/src/main/java/com/wethaq/app/PublicAdministrationActivity.java').read_text(encoding='utf-8')
VIDEO = Path('app/src/main/java/com/wethaq/app/VideoCallActivity.java').read_text(encoding='utf-8')
BUILD = Path('app/build.gradle').read_text(encoding='utf-8')
BUILD_INFO = Path('app/src/main/java/com/wethaq/app/BuildInfo.java').read_text(encoding='utf-8')
MANIFEST = Path('app/src/main/AndroidManifest.xml').read_text(encoding='utf-8')
SERVER = Path('backend/server.js').read_text(encoding='utf-8')

# Release identity must be single-source-consistent across Gradle and runtime metadata.
version_name = re.search(r"versionName\s+['\"]([^'\"]+)['\"]", BUILD)
version_marker = re.search(r"VERSION\s*=\s*['\"]([^'\"]+)['\"]", BUILD_INFO)
assert version_name and version_marker, 'missing release version identity'
assert version_name.group(1) == version_marker.group(1), (
    f"release version mismatch: Gradle={version_name.group(1)} BuildInfo={version_marker.group(1)}"
)

# Founder panel isolation.
assert 'android:name=".AdminActivity" android:exported="false"' in MANIFEST
assert 'android:name=".PublicAdministrationActivity" android:exported="false"' in MANIFEST
assert 'if(isOwner()||isAdminRole())menu("🛡  لوحة التحكم الإدارية",this::adminScreen);' in MAIN
assert 'this::publicAdministrationScreen' in MAIN
for forbidden in ('putString("admin_role","founder")','putString("admin_role","deputy1")','putString("admin_role","deputy2")','putString("admin_role","deputy3")','putString("admin_role","admin_member")'):
    assert forbidden not in MAIN
assert 'function founderOnly' in SERVER
assert 'function rbac' in SERVER
assert 'function roleCanBan' in SERVER

# Private code must never enter public UI / public serializer.
assert 'personal_code_hash' not in PUBLIC
assert 'admin_code' not in PUBLIC
assert 'personal_code' not in PUBLIC.lower()
assert 'personal_code_hash' in SERVER
assert '/api/profile/personal-code' in SERVER
assert 'personal_code_configured' in SERVER
assert 'personalCodeHash' in SERVER

# Required administration capabilities.
assert '/api/public/administration' in SERVER
assert '/api/admin/audit-log' in SERVER
assert 'app.get(\'/api/admin/audit-log\',auth,founderOnly' in SERVER
assert '10' in PUBLIC
assert 'members' in PUBLIC
assert 'setBusy(boolean busy)' in ADMIN
assert 'جاري الاتصال بالخادم' in ADMIN
assert 'تم تأكيد العملية من الخادم' in ADMIN
assert 'تعذر الاتصال بالخادم' in ADMIN
assert 'هل أنت متأكد' in ADMIN
assert 'حظر نهائي' in ADMIN
assert 'تواصل مع المدير العام للحصول على رمز الدخول الإداري الخاص بك.' in SERVER

# Call controls / media wiring preserved.
assert 'startAudioCall' in MAIN
assert 'startVideoCall' in MAIN
assert 'pendingAudioData' in MAIN
assert 'pendingImageData' in MAIN
assert 'pollInFlight' in VIDEO
assert ('callIo' in VIDEO or ('pollIo' in VIDEO and 'signalIo' in VIDEO))
assert 'sendEndSignal' in VIDEO

# Startup loading repair is preserved.
assert 'warmBackend' in MAIN
assert 'setConnectTimeout(4000)' in MAIN
assert 'setReadTimeout(6000)' in MAIN

# Authentication/personal-code flow must submit the code hash during login,
# not attempt an authenticated profile write before a JWT exists.
assert 'String personalCode' in MAIN
assert 'hashPersonalCode(code)' in MAIN
assert 'personalCodeHash' in MAIN
assert 'savePersonalCode(pc.getText().toString())' not in MAIN
assert 'savePersonalCode(code);auth(' not in MAIN

print('WETHAQ_FINAL_RELEASE_GUARD_OK')
