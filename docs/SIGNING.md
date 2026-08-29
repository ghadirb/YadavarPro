# امضا و انتشار یادآور پرو (بازار / مایکت / گیت‌هاب)

این سند همان الگوی [مالیار پرو](https://github.com/ghadirb/Maliar-Pro) است، با **کلید جداگانه**.
کلید مالیار پرو را اینجا استفاده نکن — `applicationId` فرق دارد (`com.ghadirb.yadavar`).

## چرا کلید باید ثابت بماند؟

کافه‌بازار و مایکت هر بسته را با **اثرانگشت گواهی امضا** به همان `applicationId` قفل می‌کنند.
اگر یک بار با کلید A منتشر کنی و بعد با کلید B آپدیت بفرستی، فروشگاه آپدیت را رد می‌کند و کاربران نمی‌توانند به‌روز کنند.

- یک بار کلید بساز
- همان را در گیت‌هاب Secrets و روی سیستم خودت نگه دار
- **هرگز** در ورک‌فلو کلید جدید تولید نکن

## Secretهای گیت‌هاب

مسیر:

`GitHub → YadavarPro → Settings → Secrets and variables → Actions → New repository secret`

| نام Secret | مقدار |
|---|---|
| `RELEASE_KEYSTORE_BASE64` | خروجی `base64 -w 0 yadavar-release.jks` (یک خط) |
| `RELEASE_KEYSTORE_PASSWORD` | رمز keystore |
| `RELEASE_KEY_ALIAS` | `yadavar_pro_key` |
| `RELEASE_KEY_PASSWORD` | رمز کلید (معمولاً همان رمز keystore) |
| `BAZAAR_IAB_PUBLIC_KEY` | اختیاری — کلید RSA عمومی کافه‌بازار (وقتی خرید درون‌برنامه‌ای اضافه شد) |
| `MYKET_IAB_PUBLIC_KEY` | اختیاری — کلید RSA عمومی مایکت |

اگر این Secretها از قبل ست شده‌اند، دوباره نساز. عوض کردنشان یعنی **کلید جدید** و شکست انتشار.

### ساخت مقدار Base64 روی سیستم خودت

```bash
base64 -w 0 yadavar-release.jks | pbcopy   # macOS: pbcopy / Linux: xclip
# یا ذخیره در فایل:
base64 -w 0 yadavar-release.jks > yadavar-release.jks.b64
```

روی ویندوز (PowerShell):

```powershell
[Convert]::ToBase64String([IO.File]::ReadAllBytes("yadavar-release.jks")) | Set-Clipboard
```

## بیلد محلی ریلیز

1. فایل `yadavar-release.jks` را در ریشهٔ پروژه بگذار.
2. `keystore.properties.example` را به `keystore.properties` کپی کن و رمزها را پر کن.
3. بیلد:

```bash
./gradlew :app:assembleBazaarRelease :app:bundleBazaarRelease
./gradlew :app:assembleMyketRelease :app:bundleMyketRelease
./gradlew :app:assembleDirectRelease
```

خروجی:

- APK بازار: `app/build/outputs/apk/bazaar/release/`
- AAB بازار: `app/build/outputs/bundle/bazaarRelease/`
- APK مایکت: `app/build/outputs/apk/myket/release/`
- AAB مایکت: `app/build/outputs/bundle/myketRelease/`

دیباگ برای تست روی گوشی (امضای debug، مناسب فروشگاه نیست):

```bash
./gradlew :app:assembleDirectDebug
```

## ورک‌فلو گیت‌هاب

فایل: `.github/workflows/android-build.yml`

- هر push روی `main` / `master` / `develop`: ریلیز امضاشده (APK+AAB) **و** دیباگ
- Pull Request: فقط دیباگ (بدون نیاز به کلید)
- اجرای دستی: تب **Actions → Android Builds → Run workflow**
  - `build_type`: `debug` یا `release`
  - `store`: `direct` / `bazaar` / `myket` / `all`

آرتیفکت‌ها را از همان run دانلود کن و همان فایل را در پنل بازار/مایکت آپلود کن.

## اثرانگشت گواهی (قابل انتشار)

این مقادیر را می‌توانی در پنل توسعه‌دهندهٔ فروشگاه وارد کنی. رمز نیستند.

```
SHA1:   D8:E0:16:73:18:F8:DA:9F:51:C9:6B:70:C7:C8:5C:0C:E8:DF:05:01
SHA256: 5F:33:FC:7F:08:A1:25:56:DD:20:F8:50:6E:F3:83:5D:9E:10:7A:67:BE:FD:DA:88:32:6D:68:9B:15:6E:97:95
Alias:  yadavar_pro_key
Valid:  2026-08-29 → 2054-01-14
```

بررسی روی سیستم:

```bash
keytool -list -v -keystore yadavar-release.jks -alias yadavar_pro_key
```

## پشتیبان

فایل‌های `yadavar-release.jks` و `keystore.properties` را در جای امن (رمزنگاری‌شده، خارج از گیت) نگه دار.
اگر جفت کلید گم شود، آپدیت روی همان بستهٔ فروشگاه غیرممکن است.

## خرید درون‌برنامه‌ای (بازار / مایکت)

سیستم پرداخت مثل مالیار پرو است. راهنمای کامل پنل فروشگاه و سرور:

[docs/STORE_SETUP_FA.md](STORE_SETUP_FA.md) و [server/README-fa.md](../server/README-fa.md)

کارهایی که **تو** باید در پنل‌ها انجام بدهی (کد آماده است):

1. در پیشخان کافه‌بازار و پنل مایکت دو محصول **مصرف‌شدنی** بساز:
   - `yadavar_pro_monthly`
   - `yadavar_pro_yearly`
2. کلید RSA عمومی هر فروشگاه را در Secretهای `BAZAAR_IAB_PUBLIC_KEY` و `MYKET_IAB_PUBLIC_KEY` بگذار (یا در `keystore.properties` محلی).
3. سرور `server/` را روی لیارا یا Apps Script دیپلوی کن و سه آدرس را در `SubscriptionManager.kt` به‌جای `CHANGE-ME` بگذار.
4. توکن API پیشخان بازار و Access Token مایکت را فقط روی سرور بگذار — هرگز در گیت عمومی.

تا وقتی آدرس سرور `CHANGE-ME` است، خرید موفق فروشگاه به‌صورت محلی پریمیوم می‌دهد (برای تست محصول). قبل از انتشار عمومی حتماً سرور را وصل کن.

