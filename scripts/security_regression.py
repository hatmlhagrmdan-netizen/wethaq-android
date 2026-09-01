from pathlib import Path
import re

MAIN = Path('app/src/main/java/com/wethaq/app/MainActivity.java').read_text(encoding='utf-8')
ADMIN = Path('app/src/main/java/com/wethaq/app/AdminActivity.java').read_text(encoding='utf-8')
PUBLIC = Path('app/src/main/java/com/wethaq/app/PublicAdministrationActivity.java').read_text(encoding='utf-8')
MANIFEST = Path('app/src/main/AndroidManifest.xml').read_text(encoding='utf-8')
SERVER = Path('backend/server.js').read_text(encoding='utf-8')

# Founder/admin activity must not be externally exported.
assert 'android:name=".AdminActivity" android:exported="false"' in MANIFEST
assert 'android:name=".PublicAdministrationActivity" android:exported="false"' in MANIFEST

# The public hierarchy must never contain private codes.
assert 'personal_code_hash' not in PUBLIC
assert 'admin_code' not in PUBLIC
assert 'personal_code' not in PUBLIC.lower()

# MainActivity must keep the administrative screen behind a role gate.
assert 'this::adminScreen' in MAIN
assert re.search(r'if\(isOwner\(\)\|\|isAdminRole\(\)\)menu\([^;]*this::adminScreen\)', MAIN)

# Client must not grant an administrative role locally.
assert 'putString("admin_role"' in MAIN
assert 'putString("admin_role","founder")' not in MAIN
assert 'putString("admin_role","deputy1")' not in MAIN
assert 'putString("admin_role","deputy2")' not in MAIN
assert 'putString("admin_role","deputy3")' not in MAIN
assert 'putString("admin_role","admin_member")' not in MAIN

# Backend remains the authority for founder access and RBAC.
assert 'function founderOnly' in SERVER
assert 'function roleCanBan' in SERVER
assert 'function rbac' in SERVER
assert '/api/admin/audit-log' in SERVER
assert '/api/profile/personal-code' in SERVER
assert 'personal_code_hash' in SERVER

# Private personal-code data must not be included by the public user serializer.
public_user_match = re.search(r'function publicUser\(.*?\}', SERVER)
if public_user_match:
    assert 'personal_code_hash' not in public_user_match.group(0)

print('WETHAQ_SECURITY_REGRESSION_OK')
