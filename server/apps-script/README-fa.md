# نسخه‌ی Google Apps Script (کاملاً رایگان، بدون سرور واقعی)

این جایگزینِ رایگانِ پوشه‌ی `/server` است. هم اشتراک (زرین‌پال / نکست‌پی / پی‌پینگ /
بازار / مایکت) و هم پروکسی هوش مصنوعی (چت دستیار + صدای هشدار هوشمند) را روی
زیرساخت گوگل اجرا می‌کند. کلید GapGPT داخل APK نیست؛ فقط در Script Properties می‌ماند.

## اگر `{"error":"unknown_path"}` می‌بینید

این یعنی **نسخهٔ دیپلوی‌شده قدیمی است** (مسیرهای `aiChat` / `aiTts` را ندارد) یا
آدرس را بدون `?path=...` در مرورگر باز کرده‌اید.

1. کل فایل `Code.gs` همین پوشه را کپی کنید و در پروژه Apps Script جایگزین کنید.
2. **حتماً دوباره Deploy کنید** — ذخیره کردن کد کافی نیست:
   Deploy → Manage deployments → مداد (Edit) → Version: **New version** → Deploy
3. در Script Properties این‌ها را اضافه کنید (اگر نیست):

   | Property | Value |
   |---|---|
   | `GAPGPT_API_KEY` | کلید GapGPT شما |
   | `AI_PROVIDER` | `gapgpt` |
   | `AI_MODEL` | `gpt-4o-mini` |
   | `AI_DAILY_LIMIT` | `10` |

4. تست در مرورگر:

   - `.../exec` → باید `{"ok":true,"service":"yadavar-pro",...}` باشد نه `unknown_path`
   - `.../exec?path=status&deviceId=test` → `{"isPremium":false,"premiumUntil":0}`
   - `.../exec?path=aiChat&deviceId=test` → `messages are required` یا جواب مدل؛ **نه** `unknown_path`
     اگر `ai_provider_not_configured` آمد، `GAPGPT_API_KEY` تنظیم نشده.

اپ اندروید از نسخهٔ ۱.۴.۱ به بعد چت و TTS را با GET (`?path=aiChat`) می‌فرستد، چون
POST به Apps Script بدنه JSON را در ریدایرکت گوگل گم می‌کند.

از نسخهٔ ۶ اسکریپت، هشدار هوشمند اول جملهٔ فارسی را با مدل چت می‌سازد، بعد TTS
(`tts-1` با timeout کوتاه، بعد Gemini TTS). اگر TTS ابری نرسد، اپ از گفتار دستگاه
استفاده می‌کند و فقط اگر آن هم نباشد زنگ پیش‌فرض نوتیفیکیشن را می‌زند.
برای هشدار هوشمند گفتاری **حتماً نسخهٔ ۶ را Deploy کنید**:
Deploy → Manage deployments → مداد → Version: New version → Deploy

زمان زنگ یادآوری روی **گوشی** است (AlarmManager)، نه داخل این اسکریپت. عوض کردن
`Code.gs` تأخیر یک‌دقیقه‌ای را درست نمی‌کند.

## درگاه پرداخت

با `PAYMENT_GATEWAY` یکی را انتخاب کنید:
- **زرین‌پال** - نیاز به مرچنت آی‌دی تاییدشده‌ی خودتان دارد.
- **نکست‌پی** - نیاز به یک **«درگاه مستقیم» (وب‌سرویس)** دارد که `api_key` می‌دهد؛
  **«صفحه پرداخت شخصی»** برای این کار مناسب نیست.
- **پی‌پینگ** - توکن Bearer از کنسول توسعه‌دهنده.

## مراحل نصب (اولین بار)
1. به [script.google.com](https://script.google.com) بروید → پروژه‌ی جدید.
2. کد `Code.gs` را کامل کپی و جایگزین کد پیش‌فرض کنید.
3. از آیکون چرخ‌دنده (Project Settings) → پایین صفحه → "Script Properties" → «Add
   script property» را بزنید:

   | Property | Value |
   |---|---|
   | `PAYMENT_GATEWAY` | `zarinpal` یا `nextpay` یا `payping` |
   | `GAPGPT_API_KEY` | کلید GapGPT |
   | `AI_PROVIDER` | `gapgpt` |

   اگر `zarinpal`:

   | Property | Value |
   |---|---|
   | `ZARINPAL_MERCHANT_ID` | مرچنت آی‌دی شما |
   | `ZARINPAL_SANDBOX` | `true` (اول برای تست، بعداً `false`) |
   | `PRICE_MONTHLY_RIAL` | `1990000` |
   | `PRICE_YEARLY_RIAL` | `18900000` |

   اگر `nextpay`:

   | Property | Value |
   |---|---|
   | `NEXTPAY_API_KEY` | `api_key` درگاه مستقیم |
   | `PRICE_MONTHLY_TOMAN` | `199000` |
   | `PRICE_YEARLY_TOMAN` | `1890000` |

4. دکمه‌ی آبی «Deploy» بالا سمت راست → «New deployment» → نوع: «Web app».
   - Execute as: **Me**
   - Who has access: **Anyone**
   - «Deploy» را بزنید و Authorize کنید.
5. آدرس `https://script.google.com/macros/s/AKfycb.../exec` را کپی کنید. همین آدرس
   در اپ داخل `SubscriptionManager` و `AI_BACKEND_URL` تنظیم شده است.

## محدودیت‌ها
- ذخیره‌سازی با `PropertiesService` است؛ برای صدها/چندهزار کاربر کافی است.
- هر بار که کد را ویرایش می‌کنید باید از Manage deployments نسخهٔ جدید Deploy شود.
  ذخیره به‌تنهایی روی آدرس `/exec` اعمال نمی‌شود.
