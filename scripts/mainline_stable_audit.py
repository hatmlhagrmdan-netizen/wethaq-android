from pathlib import Path
import re
import sys

ROOT = Path(__file__).resolve().parents[1]
errors = []


def read(path: str) -> str:
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

# Android release/CI contract.
for needle, message in [
    ("applicationId 'com.wethaq.app'", "applicationId changed or missing"),
    ("compileSdk 35", "compileSdk 35 is missing"),
    ("targetSdk 35", "targetSdk 35 is missing"),
    ("signingConfig signingConfigs.release", "production release must use the release signing config"),
    ("signingConfig signingConfigs.debug", "CI build must use the debug signing config"),
    ("assembleCi", "dedicated CI build type is missing"),
    ("WETHAQ_KEYSTORE_PATH", "environment-driven production signing path missing"),
    ("WETHAQ_KEYSTORE_PASSWORD", "keystore password must be environment-driven"),
    ("WETHAQ_KEY_ALIAS", "key alias must be environment-driven"),
    ("WETHAQ_KEY_PASSWORD", "key password must be environment-driven"),
]:
    if needle not in build:
        errors.append(message)

if re.search(r"storePassword\s+['\"]|keyPassword\s+['\"]|-----BEGIN (RSA|EC|PRIVATE) KEY-----", build):
    errors.append("hardcoded production signing credential material detected in Gradle configuration")

# Never allow obvious secret/key material in application, backend, or scripts.
source_text = "\n".join([main, server, read("backend/security-smoke-test.mjs")])
if re.search(r"-----BEGIN (RSA|EC|PRIVATE) KEY-----|-----BEGIN OPENSSH PRIVATE KEY-----", source_text):
    errors.append("private-key material detected in source")
if re.search(r"(?:storePassword|keyPassword)\s+['\"][^'\"]+['\"]", source_text):
    errors.append("hardcoded signing password detected in source")

# Android source must use HTTPS for remote endpoints.
if "https://wethaq-backend-production.up.railway.app" not in main:
    errors.append("expected HTTPS Wethaq backend endpoint missing")
if re.search(r"http://(?!127\.0\.0\.1(?::\d+)?(?:[\"/]|$))", main):
    errors.append("non-local cleartext HTTP endpoint detected in Android source")

# Backend security invariants.
for needle, message in [
    ("function auth(", "backend authentication middleware missing"),
    ("jwt.verify(", "JWT verification missing"),
    ("foreign_keys = ON", "SQLite foreign-key enforcement missing"),
    ("journal_mode = WAL", "SQLite WAL mode missing"),
    ("express.json({ limit: '7mb' })", "bounded JSON request size missing"),
    ("function rateLimit(", "rate limiting missing"),
]:
    if needle not in server:
        errors.append(message)

# Stable workflow must be deterministic and must not mutate application source.
for needle, message in [
    ("actions/checkout@v5", "checkout action pin missing"),
    ("actions/setup-java@v5", "Java setup pin missing"),
    ("java-version: '17'", "Java 17 pin missing"),
    ("actions/setup-node@v5", "Node setup pin missing"),
    ("node-version: '22'", "Node 22 pin missing"),
    ("gradle/actions/setup-gradle@v5", "Gradle setup action pin missing"),
    ("gradle-version: '8.9'", "Gradle 8.9 pin missing"),
    ("assembleCi", "CI assemble gate missing"),
    ("apksigner", "APK signature verification gate missing"),
    ("--print-certs", "certificate inspection gate missing"),
    ("actions/upload-artifact@v4", "artifact publication gate missing"),
]:
    if needle not in workflow:
        errors.append(message)

# No production secrets are referenced by the stable workflow.
if re.search(r"secrets\.(WETHAQ_KEYSTORE_B64|WETHAQ_KEYSTORE_PASSWORD|WETHAQ_KEY_ALIAS|WETHAQ_KEY_PASSWORD)", workflow):
    errors.append("production secrets must not be referenced by stable no-secrets CI")

# Obvious committed secret-bearing files are prohibited.
for p in ROOT.rglob("*"):
    if not p.is_file() or ".git" in p.parts:
        continue
    if p.name.lower() in {"secrets.txt", ".env", ".env.production", "wethaq-production.jks", "wethaq-production.keystore"}:
        errors.append(f"secret-bearing file must not be committed: {p.relative_to(ROOT)}")

if errors:
    print("MAINLINE_STABLE_AUDIT_FAIL")
    for error in errors:
        print(f"- {error}")
    sys.exit(1)

print("MAINLINE_STABLE_AUDIT_OK")
