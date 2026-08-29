# راه‌اندازی پرداخت یادآور پرو

این پروژه سه خروجی مستقل می‌سازد. هر خروجی فقط مسیر پرداخت خودش را دارد؛ این کار برای
بازار و مایکت ضروری است، چون سرویس پرداخت و کلید عمومی آن‌ها متفاوت است.

| خروجی | روش پرداخت | دستور ساخت |
| --- | --- | --- |
| direct | زرین‌پال، پی‌پینگ یا نکست‌پی در مرورگر | `assembleDirectRelease` یا `bundleDirectRelease` |
| bazaar | خرید درون‌برنامه‌ای کافه‌بازار | `assembleBazaarRelease` یا `bundleBazaarRelease` |
| myket | خرید درون‌برنامه‌ای مایکت | `assembleMyketRelease` یا `bundleMyketRelease` |

## پاسخ کوتاه درباره سرور

برای زرین‌پال/پی‌پینگ/نکست‌پی یک سرور لازم است تا پس از بازگشت کاربر، پرداخت را با API
درگاه تایید و اشتراک را فعال کند. سرور آماده در پوشه `server` برای Node.js است و فایل
`apps-script/Code.gs` گزینه رایگان Google Apps Script را هم دارد.

برای خود صفحه پرداخت بازار و مایکت، Node.js یا Apps Script لازم نیست؛ کتابخانه پرداخت
داخل APK مستقیماً پنجره فروشگاه را باز می‌کند. اما برای استفاده واقعی و امن، تایید سروری
رسید لازم است. همان سرور Node.js یا Apps Script موجود این کار را انجام می‌دهد؛ سرویس
جداگانه‌ای نیاز نیست.

## مرحله 1: امضای یکسان

هر سه خروجی باید با یک keystore امضا شوند. همان keystore را در تمام انتشارها حفظ کنید.
اگر نسخه بازار یا مایکت با امضای دیگری بارگذاری شود، فروشگاه آن را به‌روزرسانی برنامه
قبلی نمی‌داند.

## مرحله 2: ساخت محصول در فروشگاه‌ها

در پنل هر فروشگاه، دو محصول **مصرف‌شدنی** بسازید:

| شناسه محصول | کاربرد |
| --- | --- |
| `yadavar_pro_monthly` | 30 روز پریمیوم |
| `yadavar_pro_yearly` | 365 روز پریمیوم |

قیمت هر محصول را در پنل همان فروشگاه تعیین کنید. مایکت برای این سناریو محصول مصرف‌شدنی
می‌خواهد؛ پس اشتراک زمان‌دار توسط سرور مالیار اعمال و بعد محصول مصرف می‌شود.

## مرحله 3: کلیدهای عمومی APK

از پنل بازار و پنل مایکت، کلید عمومی RSA برنامه را بگیرید. این کلیدها محرمانه نیستند، ولی
در مخزن عمومی هم قرار ندهید تا تغییر آن‌ها کنترل‌شده بماند.

برای ساخت محلی، این دو خط را به `keystore.properties` (که نباید commit شود) اضافه کنید:

```properties
BAZAAR_IAB_PUBLIC_KEY=کلید عمومی بازار
MYKET_IAB_PUBLIC_KEY=کلید عمومی مایکت
```

برای GitHub Actions همان نام‌ها را در Settings > Secrets and variables > Actions > Secrets
به‌عنوان Secret وارد کنید. workflow باید آن‌ها را به environment ساخت بدهد.

همچنین برای امضای Release این چهار Secret را اضافه کنید. مقادیر آمادهٔ آن‌ها در فایل محلی
`release/github-release-secrets.txt` قرار دارد و این فایل نباید وارد Git شود:

```text
RELEASE_KEYSTORE_BASE64
RELEASE_KEYSTORE_PASSWORD
RELEASE_KEY_ALIAS
RELEASE_KEY_PASSWORD
```

## مرحله 4: تنظیم سرور اعتبارسنجی

در محیط سرور، این موارد را تنظیم کنید:

```text
APP_PACKAGE_NAME=com.ghadirb.yadavar
BAZAAR_API_TOKEN=توکن API پیشخان بازار
MYKET_ACCESS_TOKEN=توکن API مایکت
```

برای پرداخت مستقیم، فقط یکی از درگاه‌های موجود در `server` یا `server/apps-script/Code.gs`
را فعال کنید و متغیرهای همان درگاه را وارد کنید. لازم نیست لینک محصول جداگانه در زرین‌پال
بسازید؛ سرور برای پلن ماهانه یا سالانه درخواست پرداخت ایجاد می‌کند.

## مرحله 5: آدرس سرور در اپ

در `app/src/main/java/com/maliar/pro/utils/SubscriptionManager.kt` مقدارهای `CHANGE-ME`
را با آدرس سرور خود جایگزین کنید:

```kotlin
const val STATUS_URL = "https://your-host/subscription/status"
const val REQUEST_URL = "https://your-host/payment/request"
const val VERIFY_STORE_URL = "https://your-host/payment/verify-store"
```

برای Apps Script هر سه آدرس یک Web App هستند و فقط `path` فرق دارد، مانند
`...?path=status`، `...?path=request` و `...?path=verifyStore`.

## مرحله 6: تست قبل از انتشار

1. ابتدا محصول آزمایشی ارزان در پنل فروشگاه بسازید.
2. خروجی مخصوص همان فروشگاه را فقط از همان فروشگاه نصب کنید.
3. یک خرید انجام دهید؛ پس از تایید سرور، وضعیت پریمیوم باید تغییر کند.
4. برنامه را در میانه خرید ببندید و دوباره باز کنید؛ خرید تاییدشده باید بازیابی و مصرف شود.
5. در پایان، نسخه `bundle...Release` را برای انتشار به‌صورت AAB بارگذاری کنید.

هرگز `BAZAAR_API_TOKEN`، `MYKET_ACCESS_TOKEN`، Merchant ID درگاه یا فایل keystore را در
GitHub عمومی commit نکنید.
