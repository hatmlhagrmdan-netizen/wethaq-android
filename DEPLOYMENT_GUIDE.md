# دليل النشر والإصدار | Deployment & Release Guide

## 🚀 الإصدار الرسمي المعتمد | Official Production Release

مسار الإصدار الرسمي هو **Wethaq Production Release** في الملف:
`.github/workflows/finalize-v2.yml`

المسار يعمل يدويًا فقط، ويستخدم GitHub Environment باسم `production` حتى لا تدخل أسرار التوقيع في المستودع.

### الخطوة 1: تجهيز Keystore
حوّل ملف الـKeystore الإنتاجي إلى Base64 قبل تخزينه كسر GitHub.

```bash
base64 -w 0 wethaq-production.jks > WETHAQ_PRODUCTION_KEYSTORE_B64.txt
```

### الخطوة 2: إعداد GitHub Environment Secrets
من:
`Repository > Settings > Environments > production > Environment secrets`

يجب أن تكون الأسماء **مطابقة حرفيًا** لمسار الإصدار:

```text
WETHAQ_PRODUCTION_KEYSTORE_B64
WETHAQ_KEYSTORE_PASSWORD
WETHAQ_KEY_ALIAS
WETHAQ_KEY_PASSWORD
WETHAQ_CERT_SHA256
```

لا تضع قيم الأسرار داخل Git أو داخل ملفات المصدر أو Gradle.

`WETHAQ_CERT_SHA256` هو بصمة SHA-256 للشهادة الإنتاجية، ويستخدمها مسار الإصدار كحاجز لمنع توقيع APK بمفتاح مختلف.

### الخطوة 3: تشغيل الإصدار الرسمي
من:
`GitHub > Actions > Wethaq Production Release > Run workflow`

المسار يقوم بالترتيب التالي:

1. فحص أسرار التوقيع.
2. فك الـBase64 إلى Keystore مؤقت داخل Runner.
3. التحقق من الـKeystore والـAlias.
4. فحص المصدر واختبار Backend Smoke.
5. بناء `assembleRelease` بتوقيع الإنتاج.
6. التحقق من APK بواسطة `apksigner`.
7. استخراج بصمة الشهادة ومقارنتها مع `WETHAQ_CERT_SHA256`.
8. إنشاء SHA-256 وملف Provenance.
9. نشر الـAPK كـArtifact.
10. حذف ملفات التوقيع المؤقتة دائمًا.

### الخطوة 4: التحقق من APK الرسمي
بعد نجاح الـWorkflow، استخدم ملف `app-release.apk` من Artifact الرسمي فقط.

```bash
unzip -t app-release.apk
apksigner verify --verbose --print-certs app-release.apk
sha256sum app-release.apk
```

يجب أن يظهر في سجل الإصدار أن بصمة الشهادة تطابق `WETHAQ_CERT_SHA256` وأن النتيجة النهائية هي:

```text
WETHAQ_PRODUCTION_APK_OK
```

### الخطوة 5: نشر الإصدار للمستخدمين
لا تعتبر أي APK صادر من مسارات CI ذات التوقيع المؤقت إصدارًا رسميًا.

مسار `Wethaq Mainline Stable CI` يبني APK للتحقق فقط، مع توقيع CI مخصص للاختبارات، وليس مفتاح الإنتاج. كما أن ملف `wethaq-release.yml` لا يُستخدم كمرجع اعتماد للإصدار الرسمي.

بعد نجاح مسار **Wethaq Production Release**، يمكن نشر APK الناتج في GitHub Releases مع ملف SHA-256 وملف Provenance.

---

## 📋 متطلبات الخادم | Server Requirements

```bash
npm install

export PORT=3100
export WETHAQ_CORE_PORT=39091
export JWT_SECRET="your-long-random-secret-here"
export DB_PATH="./data/wethaq.db"
export OWNER_WETHAQ_ID="<production-owner-wethaq-id>"
export NODE_ENV="production"

node server.js
```

لا تضع الأسرار الفعلية في المستودع أو في ملفات عامة.

---

## 📱 متطلبات المستخدم | User Requirements

### الحد الأدنى
- **الجهاز:** Android 6.0 (API 23)+
- **الذاكرة:** 512 MB RAM
- **التخزين:** 50 MB
- **الاتصال:** WiFi أو 4G

### الموصى به
- **الجهاز:** Android 10+
- **الذاكرة:** 2+ GB RAM
- **التخزين:** 100 MB
- **الاتصال:** WiFi أو 4G LTE

---

## 🔧 استكشاف الأخطاء | Troubleshooting

### المشكلة: فشل التوقيع
تحقق من:
- وجود `WETHAQ_PRODUCTION_KEYSTORE_B64` داخل Environment `production`.
- صحة `WETHAQ_KEYSTORE_PASSWORD`.
- صحة `WETHAQ_KEY_ALIAS`.
- صحة `WETHAQ_KEY_PASSWORD`.
- تطابق `WETHAQ_CERT_SHA256` مع شهادة Keystore الإنتاجية.

### المشكلة: فشل الاتصال بالخادم
تحقق من عنوان Backend الإنتاجي ومن حالة Railway، ثم أعد اختبار Health وSmoke قبل اعتماد APK.

### المشكلة: فشل الاختبارات
```bash
cd backend
npm run test:smoke
node security-smoke-test.mjs
```

---

## ✅ معيار الاعتماد

لا تُسمّى النسخة **رسمية معتمدة** إلا بعد تحقق جميع الشروط التالية:

- نجاح فحص المصدر.
- نجاح Backend Smoke/Security checks.
- نجاح `assembleRelease`.
- نجاح `apksigner verify`.
- وجود توقيع واحد صالح.
- تطابق بصمة الشهادة مع `WETHAQ_CERT_SHA256`.
- إنتاج SHA-256 وProvenance.
- نشر Artifact من مسار `Wethaq Production Release`.

*تمت مواءمة الدليل مع بنية الإصدار الحالية في 2026-09-11.*
