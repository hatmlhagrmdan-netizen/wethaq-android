from pathlib import Path
import re
import sys
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parents[1]
JAVA = ROOT / 'app/src/main/java/com/wethaq/app'
MANIFEST = ROOT / 'app/src/main/AndroidManifest.xml'
BUILD = ROOT / 'app/build.gradle'
BACKEND = ROOT / 'backend'

errors = []

def need(path: Path):
    if not path.is_file():
        errors.append(f'MISSING_FILE:{path.relative_to(ROOT)}')

for rel in [
    'settings.gradle', 'build.gradle', 'app/build.gradle',
    'app/src/main/AndroidManifest.xml',
    'app/src/main/java/com/wethaq/app/MainActivity.java',
    'app/src/main/java/com/wethaq/app/VideoCallActivity.java',
    'app/src/main/java/com/wethaq/app/WethaqMessageService.java',
    'app/src/main/java/com/wethaq/app/WethaqBootReceiver.java',
    'backend/package.json', 'backend/server.js', 'backend/smoke-test.mjs',
]:
    need(ROOT / rel)

if BUILD.is_file():
    build = BUILD.read_text(encoding='utf-8')
    release_block = build.split('buildTypes', 1)[1] if 'buildTypes' in build else ''
    if 'release {' not in release_block:
        errors.append('RELEASE_BUILD_TYPE_MISSING')
    if 'signingConfig signingConfigs.release' not in build:
        errors.append('RELEASE_SIGNING_CONFIG_MISSING')
    if 'release {' in release_block:
        release_section = release_block.split('ci {', 1)[0]
        if 'signingConfig signingConfigs.debug' in release_section:
            errors.append('RELEASE_USES_DEBUG_SIGNING')
    if 'buildTypes' in build and 'ci {' not in build:
        errors.append('CI_BUILD_TYPE_MISSING')
    if not re.search(r'versionName\s+[\'\"]([^\'\"]+)[\'\"]', build):
        errors.append('VERSION_NAME_MISSING')

if MANIFEST.is_file():
    ns = {'a': 'http://schemas.android.com/apk/res/android'}
    try:
        root = ET.parse(MANIFEST).getroot()
        app = root.find('a:application', ns)
        if app is None:
            errors.append('APPLICATION_MISSING')
        else:
            for tag in ('activity', 'service', 'receiver', 'provider'):
                for node in app.findall(f'a:{tag}', ns):
                    name = node.get('{http://schemas.android.com/apk/res/android}name')
                    if not name:
                        continue
                    if name.startswith('.'):
                        path = JAVA / (name[1:] + '.java')
                    elif name.startswith('com.wethaq.app.'):
                        path = JAVA / (name.removeprefix('com.wethaq.app.') + '.java')
                    else:
                        path = None
                    if path is not None and not path.is_file():
                        errors.append(f'MANIFEST_COMPONENT_MISSING:{tag}:{name}')
    except Exception as exc:
        errors.append(f'MANIFEST_PARSE_ERROR:{exc}')

main = JAVA / 'MainActivity.java'
if main.is_file():
    text = main.read_text(encoding='utf-8')
    stale = ['q.put("name",fn)', 'Integer.parseInt(fy)', 'optString("name",fn)', '.put(YEAR,fy)']
    for token in stale:
        if token in text:
            errors.append(f'STALE_AUTH_IDENTIFIER:{token}')

for secret_path in [BACKEND / '.env', ROOT / 'local.properties']:
    if secret_path.exists():
        errors.append(f'LOCAL_SECRET_FILE_TRACKED_OR_PRESENT:{secret_path.relative_to(ROOT)}')

# Flag likely literal secrets in application/backend source while allowing env reads.
source_files = list((ROOT / 'app').rglob('*.java')) + list((ROOT / 'backend').glob('*.js')) + list((ROOT / 'backend').glob('*.mjs'))
secret_re = re.compile(r'(?i)\b(password|secret|api[_-]?key|private[_-]?key)\b\s*[:=]\s*[\'\"][^\'\"]{16,}[\'\"]')
for path in source_files:
    text = path.read_text(encoding='utf-8', errors='ignore')
    for line_no, line in enumerate(text.splitlines(), 1):
        if secret_re.search(line) and 'process.env.' not in line and 'System.getenv(' not in line:
            errors.append(f'POSSIBLE_HARDCODED_SECRET:{path.relative_to(ROOT)}:{line_no}')

if errors:
    print('BASELINE_AUDIT_FAIL')
    for e in sorted(set(errors)):
        print(e)
    sys.exit(1)

print('BASELINE_AUDIT_OK')
