# SuperJet Staff v2.2 PRO

تطبيق موظفي SuperJet لمتابعة المدفوعات وربط الهاتف بالموظف وقراءة إشعارات التحويل.

## الإصدار
- واجهة حديثة Dark/Navy + Gold.
- تسجيل دخول الموظف عبر /api/mobile/login.
- حفظ Session Token محليًا داخل Android Keystore.
- ربط الجهاز بالموظف عبر Android ID.
- Notification Access.
- VF-Cash parser مع دعم الأرقام العربية/الإنجليزية واستخراج المبلغ والهواتف والمرجع عند توفره.
- مزامنة الإشعار إلى /api/mobile/android-notification باستخدام Bearer Session Token + Device ID.
- سجل محلي للإشعارات مع حالة المزامنة.
- لوحة الموظف مع عمليات الدفع اليومية وحالات الاعتماد والرفض.
- يعمل حتى مع انقطاع مؤقت في الشبكة ثم يعيد المزامنة.

## Server
العنوان الافتراضي المدمج:
https://superjet.tail0f920c.ts.net:8443

لا يحتوي APK على كلمة مرور الموظف أو Token ثابت للدفع أو Telegram Token؛ يتم الحصول على جلسة بعد تسجيل الدخول.
