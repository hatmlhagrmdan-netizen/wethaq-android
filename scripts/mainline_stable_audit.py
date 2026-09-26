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
main = read("app/src/main/java/com/wethaq/app/MainActivity.java")
config = read("app/src/main/java/com/wethaq/app/WethaqConfig.java")
server = read("backend/server.js")
workflow = read(".github/workflows/finalize-v2.yml")
ui = read("app/src/main/java/com/wethaq/app/WethaqUi.java")
app_lifecycle = read("app/src/main/java/com/wethaq/app/WethaqApp.java")
admin = read("app/src/main/java/com/wethaq/app/AdminAccessActivity.java")
public_admin = read("app/src/main/java/com/wethaq/app/PublicAdministrationActivity.java")
manifest = read("app/src/main/AndroidManifest.xml")

for needle, message in [
    ("applicationId 'com.wethaq.app'", "applicationId changed or missing"),
    ("compileSdk 36", "compileSdk 36 is missing"),
    ("targetSdk 36", "targetSdk 36 is missing"),
    ("signingConfig signingConfigs.release", "production release must use the release signing config"),
    ("signingConfig signingConfigs.debug", "CI build must use the debug signing config"),
    ("ci {", "dedicated CI build type is missing"),
    ("WETHAQ_KEYSTORE_PATH", "environment-driven production signing path missing"),
    ("WETHAQ_KEYSTORE_PASSWORD", "keystore password must be environment-driven"),
    ("WETHAQ_KEY_ALIAS", "key alias must be environment-driven"),
    ("WETHAQ_KEY_PASSWORD", "key password must be environment-driven"),
]:
    if needle not in build:
        errors.append(message)

if re.search(r"storePassword\s+['\"]|keyPassword\s+['\"]|-----BEGIN (RSA|EC|PRIVATE) KEY-----", build):
    errors.append("hardcoded production signing credential material detected in Gradle configuration")

source_text = "\n".join([main, server, read("backend/security-smoke-test.mjs")])
if re.search(r"READ_MEDIA_IMAGES", read("app/src/main/AndroidManifest.xml")):
    errors.append("READ_MEDIA_IMAGES must remain removed; use the system photo picker") 
if "ACTION_PICK_IMAGES" not in main:
    errors.append("system photo picker integration missing") 

# Visual identity invariants: these prevent a drift back to parallel UI systems.
for needle, message in [
    ("public static void apply(Activity a)", "WethaqUi global entry point drifted"),
    ("public static View liveHero(Activity a,String headline,String subtitle)", "live visual hero is missing"),
    ('root.setBackgroundResource(R.drawable.bg_wethaq)', "AdminAccess root is outside the shared Wethaq background"),
]:
    haystack = ui + admin
    if needle not in haystack:
        errors.append(message)

for needle, message in [
    ("root.setBackgroundResource(R.drawable.bg_wethaq)", "PublicAdministration root is outside the shared Wethaq background"),
    ('WethaqUi.liveHero(this,"الإدارة العامة في وَثاق"', "PublicAdministration lost the live identity hero"),
]:
    if needle not in public_admin:
        errors.append(message)

if "setBackgroundColor(Color.BLACK)" in admin + public_admin:
    errors.append("hard black activity roots reintroduce a parallel visual system")

if "PremiumActivity" in manifest:
    errors.append("unreferenced PremiumActivity must not remain in the application manifest")

if "root.post(()->WethaqUi.apply(this));" not in main:
    errors.append("MainActivity rebuilt screens must re-apply the shared visual system")

if "WethaqUi.apply(a)" not in app_lifecycle:
    errors.append("Application lifecycle must apply the shared visual system")

if "imageExecutor.shutdownNow()" not in ui or "AtomicBoolean active" not in ui:
    errors.append("live visual image executor must have a bounded lifecycle")

if ui.count("https://images.unsplash.com/") < 3:
    errors.append("live visual gallery must keep at least three image sources")

if re.search(r"-----BEGIN (RSA|EC|OPENSSH|PRIVATE) KEY-----", source_text):
    errors.append("private-key material detected in runtime source")
if re.search(r"(?:storePassword|keyPassword)\s+['\"][^'\"]+['\"]", source_text):
    errors.append("hardcoded signing password detected in runtime source")

if "https://wethaq-backend-production.up.railway.app" not in config:
    errors.append("expected HTTPS Wethaq backend endpoint missing from WethaqConfig")
if "open" + "relayproject" in main:
    errors.append("public demo TURN credentials must not remain in Android source")
if "/api/calls/ice-config" not in server:
    errors.append("server-side ICE configuration endpoint missing")
if re.search(r"http://(?!127\.0\.0\.1(?::\d+)?(?:[\"/]|$))", main):
    errors.append("non-local cleartext HTTP endpoint detected in Android source")

for needle, message in [
    ("function auth(", "backend authentication middleware missing"),
    ("jwt.verify(", "JWT verification missing"),
    ("foreign_keys=ON", "SQLite foreign-key enforcement missing"),
    ("journal_mode=WAL", "SQLite WAL mode missing"),
    ("express.json({limit:'12mb'})", "bounded JSON request size missing"),
    ("function rateLimit(", "rate limiting missing"),
]:
    if needle not in server:
        errors.append(message)

for needle, message in [
    ("actions/checkout@v5", "checkout action pin missing"),
    ("actions/setup-java@v5", "Java setup pin missing"),
    ("java-version: '17'", "Java 17 pin missing"),
    ("actions/setup-node@v5", "Node setup pin missing"),
    ("node-version: '22'", "Node 22 pin missing"),
    ("gradle/actions/setup-gradle@v5", "Gradle setup action pin missing"),
    ("gradle-version: '8.9'", "Gradle 8.9 pin missing"),
    ("assembleCi", "CI assemble gate missing"),
    ("bundleCi", "CI AAB build gate missing"),
    ("bundleRelease", "production AAB build gate missing"),
    ("apksigner", "APK signature verification gate missing"),
    ("--print-certs", "certificate inspection gate missing"),
    ("actions/upload-artifact@v4", "artifact publication gate missing"),
]:
    if needle not in workflow:
        errors.append(message)

if re.search(r"secrets\.WETHAQ_(PRODUCTION_KEYSTORE_B64|KEYSTORE_PASSWORD|KEY_ALIAS|KEY_PASSWORD|CERT_SHA256)", workflow):
    # These secrets belong only to the protected production job. Ensure the
    # non-production validation job remains secret-free by checking its scope.
    validation = workflow.split("  production:", 1)[0]
    if "secrets." in validation:
        errors.append("production secrets leaked into the validation job")

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
