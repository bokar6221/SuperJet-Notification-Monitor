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

## Version 3.7.0 — Manager seat statistics
- Manager-only statistics screen backed by `/api/mobile/manager-statistics`.
- Separates raw website booking requests, employee booking operations, issued-ticket operations, rejected operations, requested seats, and issued seats.
- Shows today, current month, all-time figures, and per-employee totals.
- Seat totals are derived from the unique selected seat identifiers saved for each operation, rather than counting one booking as one seat.
- Requires the matching SuperJet server update from the `feature/mobile-manager-seat-stats` branch to be deployed before this screen can load data.


## Version 3.7.1 — Full consolidated package
- A single downloadable bundle now contains the full Android APK, full Android source archive, the matching SuperJet server source archive, a deployment/install guide, and SHA-256 checksums.
- The build checks backend Python syntax and verifies the Android/backend manager-statistics endpoint contract before building.
- The APK is rebuilt from the entire Android project; it is not a patch APK.
- See `RELEASE_GUIDE_v3.7.1.md` for installation and server-deployment requirements, including the debug-signature upgrade caveat.


## Version 3.8.5 — full integrated mobile build
- Uses the provider-aware payment notification parser from the full SuperJet V37.8.4/V37.8.5 source.
- Hides approved/rejected items from the booking decision queue, refreshes after every decision attempt, and retains final records in Payments.
- Includes manager-only daily/monthly/all-time counts for booking requests, operations, issued ticket records, rejected operations, and actual reserved seats.
- This is an Android source/APK repository only; the full Windows project archive is distributed separately.
