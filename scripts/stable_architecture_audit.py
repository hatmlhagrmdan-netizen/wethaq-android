from pathlib import Path
import re
import sys

ROOT = Path(__file__).resolve().parents[1]
errors = []

def read(path):
    p = ROOT / path
    if not p.is_file():
        errors.append(f"missing required file: {path}")
        return ""
    return p.read_text(encoding="utf-8")

build = read("app/build.gradle")
manifest = read("app/src/main/AndroidManifest.xml")
main = read("app/src/main/java/com/wethaq/app/MainActivity.java")
server = read("backend/server.js")
workflow = read(".github/workflows/wethaq-stable-ci.yml")

if "applicationId 'com.wethaq.app'" not in build:
    errors.append("applicationId changed or missing")
if "signingConfig signingConfigs.debug" not in build:
    errors.append("stable candidate must use isolated/debug signing, not production secrets")
if re.search(r"signingConfigs\\.release|WETHAQ_KEYSTORE|WETHAQ_KEY_PASSWORD|WETHAQ_KEYSTORE_PASSWORD", build + workflow + main + server):
    errors.append("production signing material/configuration leaked into stable source or CI")
if "https://" not in main or "http://" in re.sub(r"http://127\\.0\\.0\\.1", "", main):
    errors.append("non-local cleartext HTTP endpoint detected in Android source")
if "Bearer "+"" not in main or "private String auth()" not in main:
    errors.append("authenticated API helper missing")
if "function auth(req,res,next)" not in server:
    errors.append("backend authentication middleware missing")
if "jwt.verify(token,JWT_SECRET)" not in server:
    errors.append("JWT verification missing")
if "foreign_keys = ON" not in server:
    errors.append("SQLite foreign-key enforcement missing")
if "journal_mode = WAL" not in server:
    errors.append("SQLite WAL mode missing")
if "express.json({ limit: '7mb' })" not in server:
    errors.append("bounded JSON request size missing")
if "rateLimit(" not in server:
    errors.append("rate limiting missing")
if "actions/setup-java@v5" not in workflow or "java-version: '17'" not in workflow:
    errors.append("Java 17 CI pin missing")
if "node-version: '22'" not in workflow:
    errors.append("Node 22 CI pin missing")
if "gradle-version: '8.9'" not in workflow:
    errors.append("Gradle 8.9 CI pin missing")
if "clean test assembleRelease" not in workflow:
    errors.append("release build gate missing")
if "apksigner" not in workflow or "--print-certs" not in workflow:
    errors.append("APK signature verification gate missing")
if "actions/upload-artifact@v4" not in workflow:
    errors.append("verified artifact publication gate missing")

# Detect obvious committed secret files. This intentionally ignores .git internals.
for p in ROOT.rglob("*"):
    if not p.is_file() or ".git" in p.parts:
        continue
    if p.name.lower() in {"secrets.txt", ".env", ".env.production"}:
        errors.append(f"secret-bearing file must not be committed: {p.relative_to(ROOT)}")

if errors:
    print("STABLE_ARCHITECTURE_AUDIT_FAIL")
    for e in errors:
        print(f"- {e}")
    sys.exit(1)

print("STABLE_ARCHITECTURE_AUDIT_OK")
