from pathlib import Path
import re

MAIN = Path('app/src/main/java/com/wethaq/app/MainActivity.java').read_text(encoding='utf-8')
ADMIN = Path('app/src/main/java/com/wethaq/app/AdminActivity.java').read_text(encoding='utf-8')
PUBLIC = Path('app/src/main/java/com/wethaq/app/PublicAdministrationActivity.java').read_text(encoding='utf-8')
VIDEO = Path('app/src/main/java/com/wethaq/app/VideoCallActivity.java').read_text(encoding='utf-8')
SERVICE = Path('app/src/main/java/com/wethaq/app/WethaqMessageService.java').read_text(encoding='utf-8')
BUILD = Path('app/build.gradle').read_text(encoding='utf-8')
BUILD_INFO = Path('app/src/main/java/com/wethaq/app/BuildInfo.java').read_text(encoding='utf-8')
MANIFEST = Path('app/src/main/AndroidManifest.xml').read_text(encoding='utf-8')
SERVER = Path('backend/server.js').read_text(encoding='utf-8')

version_name = re.search(r"versionName\s+['\"]([^'\"]+)['\"]", BUILD)
version_marker = re.search(r"VERSION\s*=\s*['\"]([^'\"]+)['\"]", BUILD_INFO)
assert version_name and version_marker, 'missing release version identity'
assert version_name.group(1) == version_marker.group(1), (
    f"release version mismatch: Gradle={version_name.group(1)} BuildInfo={version_marker.group(1)}"
)

# Founder panel isolation and backend authorization.
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

# Administration capabilities and real UI workflow contracts.
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
assert 'addAdminSearchControls()' in ADMIN
assert 'الاسم الكامل أو معرف وَثاق' in ADMIN
assert 'إرسال تنبيه' in ADMIN
assert 'حظر حسب الصلاحية' in ADMIN
assert 'تواصل مع المدير العام للحصول على رمز الدخول الإداري الخاص بك.' in SERVER

# Conversation/media path is wired end-to-end at the client contract level.
assert 'searchScreen' in MAIN
assert 'conversation(id,n)' in MAIN
assert '/api/messages' in MAIN
assert '/api/messages/audio' in MAIN
assert '/api/messages/image' in MAIN
assert 'pendingAudioData' in MAIN
assert 'pendingImageData' in MAIN
assert 'startAudioCall' in MAIN
assert 'startVideoCall' in MAIN

# Real incoming/outgoing call controls and WebRTC state machine.
assert 'acceptIncoming()' in VIDEO
assert 'rejectIncoming()' in VIDEO
assert 'مكالمة واردة — اختر قبول أو رفض' in VIDEO
assert 'toggleMute' in VIDEO
assert 'toggleSpeaker' in VIDEO
assert 'toggleCamera' in VIDEO
assert 'PeerConnectionFactory' in VIDEO
assert 'createOffer' in VIDEO
assert 'createAnswer' in VIDEO
assert 'setRemoteDescription' in VIDEO
assert 'onIceCandidate' in VIDEO
assert 'addIceCandidate' in VIDEO
assert 'sendSignal("end"' in VIDEO
assert 'pollInFlight' in VIDEO
assert 'signalIo' in VIDEO
assert 'pollIo' in VIDEO

# Background/closed-app notification and call notification contracts.
assert 'START_STICKY' in SERVICE
assert 'showMessage' in SERVICE
assert 'showIncomingCall' in SERVICE
assert 'setFullScreenIntent' in SERVICE
assert 'MESSAGE_CHANNEL' in SERVICE
assert 'CALL_CHANNEL' in SERVICE
assert 'scheduleReconnect' in SERVICE

# Startup loading safety.
assert 'warmBackend' in MAIN
assert 'setConnectTimeout(4000)' in MAIN
assert 'setReadTimeout(6000)' in MAIN

# Authentication/personal-code flow submits the hash during identity/login.
assert 'String personalCode' in MAIN
assert 'hashPersonalCode(code)' in MAIN
assert 'personalCodeHash' in MAIN
assert 'savePersonalCode(pc.getText().toString())' not in MAIN
assert 'savePersonalCode(code);auth(' not in MAIN

print('WETHAQ_FINAL_RELEASE_GUARD_OK')
