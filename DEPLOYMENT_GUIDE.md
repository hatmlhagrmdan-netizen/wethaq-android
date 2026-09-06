# دليل النشر والإصدار | Deployment & Release Guide

## 🚀 خطوات الإصدار النهائي | Final Release Steps

### الخطوة 1: تحضير المفاتيح والأسرار
```bash
# تحويل Keystore إلى Base64
base64 -i wethaq-production.jks -o WETHAQ_KEYSTORE_BASE64.txt
```

### الخطوة 2: إعداد GitHub Secrets
```
REPOSITORY SETTINGS > SECRETS AND VARIABLES > ACTIONS

✓ WETHAQ_KEYSTORE_BASE64
✓ WETHAQ_KEYSTORE_PASSWORD
✓ WETHAQ_KEY_ALIAS
✓ WETHAQ_KEY_PASSWORD
```

### الخطوة 3: تشغيل مسار الإصدار الموقع
```
GitHub Actions > wethaq-release-verified > Run workflow
```

### الخطوة 4: التحقق من النتائج
```bash
# التحقق من صحة APK
unzip -t Wethaq-v1.0.0.apk

# التحقق من التوقيع
apksigner verify --verbose --print-certs Wethaq-v1.0.0.apk

# التحقق من البصمة
sha256sum Wethaq-v1.0.0.apk
```

### الخطوة 5: نشر الإصدار
```bash
# إنشاء وسم الإصدار
git tag -a v1.0.0 -m "Wethaq v1.0.0 - First Production Release"
git push origin v1.0.0

# نشر على GitHub Releases
# Upload: Wethaq-v1.0.0.apk
# Upload: wethaq-v1.0.0-sha256.txt
```

---

## 📋 متطلبات الخادم | Server Requirements

```bash
# التثبيت
npm install

# متغيرات البيئة
export PORT=3100
export WETHAQ_CORE_PORT=39091
export JWT_SECRET="your-long-random-secret-here"
export DB_PATH="./data/wethaq.db"
export OWNER_WETHAQ_ID="Hatem_Hussin_Al_Haj_Ramadan1995"
export NODE_ENV="production"

# التشغيل
node server-v7.js
```

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
- **الاتصال:** WiFi 5G أو 4G LTE

---

## 🔧 استكشاف الأخطاء | Troubleshooting

### المشكلة: لا يتصل بالخادم
**الحل:**
- تحقق من عنوان IP الخادم
- تأكد من تشغيل server-v7.js
- تحقق من الاتصال بالإنترنت

### المشكلة: فشل التوقيع
**الحل:**
- تحقق من Keystore
- تحقق من كلمات المرور
- تأكد من Java 17+

### المشكلة: فشل الاختبار
**الحل:**
```bash
cd backend
npm run test:smoke
node final-acceptance.mjs
```

---

## 📞 الدعم | Support

**الأخطاء التقنية:** اتصل بفريق التطوير  
**الأسئلة العامة:** اتصل عبر تطبيق وثاق  
**الإبلاغ عن الأمان:** تواصل سري مع المؤسس  

---

*تم الإصدار: 2026-09-06*