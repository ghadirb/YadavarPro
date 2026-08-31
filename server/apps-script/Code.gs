// Yadavar Pro Apps Script backend — version 7
// After pasting this file you MUST: Deploy → Manage deployments → pencil → New version → Deploy
// A live v7 /exec URL with no ?path= returns {"ok":true,"service":"yadavar-pro","version":7,...}
// not {"error":"unknown_path"}.
//
// Google Apps Script version of the Maliar Pro billing backend - a free, no-hosting-
// needed alternative to the Node.js server in /server. Uses PropertiesService as a tiny
// key-value store (no Google Sheet needed) and UrlFetchApp to call the gateway's API.
//
// Supports three gateways - pick one with PAYMENT_GATEWAY ("zarinpal", "nextpay", or
// "payping"):
//   - Zarinpal needs its own merchant account approved and working.
//   - NextPay needs a "درگاه مستقیم" (direct gateway) API key, NOT the "صفحه پرداخت
//     شخصی" (personal payment page) - that one has no API for an app to call.
//   - PayPing needs an access token (Bearer) from its developer console. No manual
//     "product"/"permalink" setup needed in the PayPing dashboard - this calls /v3/pay
//     directly with the exact plan amount each time, same pattern as the other two.
//
// SETUP:
// 1. Go to https://script.google.com/ -> New project.
// 2. Delete the default code and paste this whole file in.
// 3. In "Project Settings" (gear icon) -> Script Properties, add ONE "Property" +
//    "Value" row per line below (the Property box takes the name on the left,
//    the Value box takes what's on the right):
//      PAYMENT_GATEWAY      = zarinpal   (or nextpay, or payping)
//      -- if using zarinpal --
//      ZARINPAL_MERCHANT_ID = your merchant id
//      ZARINPAL_SANDBOX     = true   (set to false once you've tested)
//      PRICE_MONTHLY_RIAL   = 1990000   (= 199,000 Toman)
//      PRICE_YEARLY_RIAL    = 18900000  (= 1,890,000 Toman)
//      -- if using nextpay --
//      NEXTPAY_API_KEY      = your direct-gateway api_key from NextPay's panel
//      PRICE_MONTHLY_TOMAN  = 199000   (NextPay's amount is in Toman, not Rial)
//      PRICE_YEARLY_TOMAN   = 1890000
//      -- if using payping --
//      PAYPING_TOKEN        = your PayPing access token (Bearer)
//      PRICE_MONTHLY_TOMAN  = 199000   (PayPing's amount is in Toman too)
//      PRICE_YEARLY_TOMAN   = 1890000
//      -- if using Bazaar / Myket in-app products too --
//      APP_PACKAGE_NAME     = com.ghadirb.yadavar
//      BAZAAR_API_TOKEN     = token from Bazaar developer dashboard
//      MYKET_ACCESS_TOKEN   = token from Myket developer dashboard
//      -- optional AI proxy --
//      AI_PROVIDER          = gapgpt (or liara)
//      GAPGPT_API_KEY       = provider key (Script Property only)
//      LIARA_API_KEY        = provider key (Script Property only)
//      AI_MODEL             = gpt-4o-mini
//      AI_TTS_MODEL         = gpt-4o-mini-tts
//      AI_DAILY_LIMIT       = 10
// 4. Deploy -> New deployment -> type: "Web app".
//      Execute as: Me
//      Who has access: Anyone
// 5. Copy the resulting /exec URL - that's what goes into the Android app's
//    SubscriptionManager.kt as STATUS_URL/REQUEST_URL (see bottom of this file for the
//    exact values to use).

function getSetting_(key, fallback) {
  const value = PropertiesService.getScriptProperties().getProperty(key);
  return (value === null || value === undefined || value === '') ? fallback : value;
}

function activeGateway_() {
  const g = getSetting_('PAYMENT_GATEWAY', 'zarinpal');
  if (g === 'nextpay' || g === 'payping') return g;
  return 'zarinpal';
}

function getPlans_() {
  // Monthly: 199,000 Toman. Yearly: 1,890,000 Toman (= 1,990,000 / 18,900,000 Rial).
  if (activeGateway_() === 'nextpay' || activeGateway_() === 'payping') {
    return {
      monthly: { days: 30, amount: parseInt(getSetting_('PRICE_MONTHLY_TOMAN', '199000'), 10) },
      yearly: { days: 365, amount: parseInt(getSetting_('PRICE_YEARLY_TOMAN', '1890000'), 10) }
    };
  }
  return {
    monthly: { days: 30, amount: parseInt(getSetting_('PRICE_MONTHLY_RIAL', '1990000'), 10) },
    yearly: { days: 365, amount: parseInt(getSetting_('PRICE_YEARLY_RIAL', '18900000'), 10) }
  };
}

const PAYPING_BASE = 'https://api.payping.ir/v3';

function isSandbox_() {
  return getSetting_('ZARINPAL_SANDBOX', 'true') === 'true';
}

function zarinpalUrls_() {
  const sandbox = isSandbox_();
  return {
    request: sandbox
      ? 'https://sandbox.zarinpal.com/pg/v4/payment/request.json'
      : 'https://api.zarinpal.com/pg/v4/payment/request.json',
    verify: sandbox
      ? 'https://sandbox.zarinpal.com/pg/v4/payment/verify.json'
      : 'https://api.zarinpal.com/pg/v4/payment/verify.json',
    startPay: sandbox
      ? 'https://sandbox.zarinpal.com/pg/StartPay/'
      : 'https://www.zarinpal.com/pg/StartPay/'
  };
}

// NextPay's plain-HTTP (non-SOAP) endpoints - ".http" instead of ".wsdl". Its success
// codes are its own quirky convention, not the usual "0 = ok" - a *token* request
// succeeds when code === -1, while a *verify* request succeeds when code === 0.
const NEXTPAY_TOKEN_URL = 'https://api.nextpay.org/gateway/token.http';
const NEXTPAY_VERIFY_URL = 'https://api.nextpay.org/gateway/verify.http';
const NEXTPAY_PAYMENT_BASE = 'https://api.nextpay.org/gateway/payment/';

// --- tiny device/order storage using PropertiesService ------------------------------
// Well within Apps Script's free quota (500KB total, ~9KB per value) for many thousands
// of small device/order records - fine for an app at this scale.

function getDeviceRecord_(deviceId) {
  const raw = PropertiesService.getScriptProperties().getProperty('device_' + deviceId);
  return raw ? JSON.parse(raw) : { premiumUntil: 0 };
}

function grantPremiumDays_(deviceId, days) {
  const current = getDeviceRecord_(deviceId).premiumUntil || 0;
  const base = Math.max(current, Date.now());
  const premiumUntil = base + days * 24 * 60 * 60 * 1000;
  PropertiesService.getScriptProperties().setProperty('device_' + deviceId, JSON.stringify({ premiumUntil: premiumUntil }));
  return premiumUntil;
}

function getOrder_(key) {
  const raw = PropertiesService.getScriptProperties().getProperty('order_' + key);
  return raw ? JSON.parse(raw) : null;
}

function saveOrder_(key, order) {
  PropertiesService.getScriptProperties().setProperty('order_' + key, JSON.stringify(order));
}

function getStorePurchase_(key) {
  const raw = PropertiesService.getScriptProperties().getProperty('store_purchase_' + key);
  return raw ? JSON.parse(raw) : null;
}

function saveStorePurchase_(key, purchase) {
  PropertiesService.getScriptProperties().setProperty('store_purchase_' + key, JSON.stringify(purchase));
}

// --- HTTP response helpers -----------------------------------------------------------

function jsonOutput_(obj) {
  return ContentService.createTextOutput(JSON.stringify(obj))
    .setMimeType(ContentService.MimeType.JSON);
}

function htmlOutput_(message) {
  return HtmlService.createHtmlOutput(
    '<html dir="rtl" lang="fa"><body style="font-family:tahoma;text-align:center;padding:40px">' +
    message +
    '<p>می‌توانید این صفحه را ببندید و به اپ یادآور پرو برگردید.</p></body></html>'
  );
}

// --- main entry point ------------------------------------------------------------------
// Everything is a GET (Apps Script Web Apps handle POST redirects unreliably, so the
// Android client sends deviceId/plan as query params instead - see SubscriptionManager.kt).

function doGet(e) {
  return routeRequest_(e);
}

// PayPing's payment-result callback is a real HTTP POST (application/x-www-form-urlencoded),
// unlike Zarinpal/NextPay which redirect back with a plain GET - Apps Script needs a
// separate doPost entry point to receive it. e.parameter merges the URL's own query
// params (path/deviceId/plan, which we put in the returnUrl ourselves) together with the
// POSTed form fields (paymentCode, paymentRefId, amount, etc.), so the same routing works.
function doPost(e) {
  return routeRequest_(e);
}

function routeRequest_(e) {
  const params = Object.assign({}, (e && e.parameter) || {}, parseJsonBody_(e));
  const path = String(params.path || params.action || '').trim();

  // Visiting /exec in a browser has no ?path= — that used to look like a broken
  // deploy ("unknown_path"). Return a health payload instead so the owner can
  // tell a live script from a missing one.
  if (!path) {
    return jsonOutput_({
      ok: true,
      service: 'yadavar-pro',
      version: 7,
      routes: ['status', 'request', 'callback', 'paypingCallback', 'verifyStore', 'aiChat', 'aiStt', 'aiTts', 'aiSmartAlert'],
      hint: 'این آدرس سالم است. اپ با ?path=status و ?path=aiChat صدا می‌زند.'
    });
  }

  if (path === 'status') return handleStatus_(params);
  if (path === 'request') return handleRequest_(params);
  if (path === 'callback') return handleCallback_(params);
  if (path === 'paypingCallback') return handleCallbackPayping_(params);
  if (path === 'verifyStore') return handleVerifyStore_(params);
  if (path === 'aiChat' || path === 'chat' || path === 'ai_chat') return handleAiChat_(params);
  if (path === 'aiStt' || path === 'stt' || path === 'ai_stt') return handleAiStt_(params);
  if (path === 'aiTts' || path === 'tts' || path === 'ai_tts') return handleAiTts_(params);
  if (path === 'aiSmartAlert' || path === 'smartAlert' || path === 'ai_smart_alert') return handleAiSmartAlert_(params);

  return jsonOutput_({ error: 'unknown_path', path: path });
}

function parseJsonBody_(e) {
  try {
    const raw = e && e.postData && e.postData.contents;
    if (!raw) return {};
    const parsed = JSON.parse(raw);
    return parsed && typeof parsed === 'object' && !Array.isArray(parsed) ? parsed : {};
  } catch (err) {
    return {};
  }
}

function coerceMessages_(raw) {
  if (Array.isArray(raw)) return raw;
  if (typeof raw === 'string' && raw.trim()) {
    try {
      const parsed = JSON.parse(raw);
      if (Array.isArray(parsed)) return parsed;
    } catch (err) {}
  }
  return null;
}

function aiConfig_() {
  const provider = getSetting_('AI_PROVIDER', 'gapgpt').toLowerCase();
  const isLiara = provider === 'liara';
  return {
    provider: provider,
    key: getSetting_(isLiara ? 'LIARA_API_KEY' : 'GAPGPT_API_KEY', ''),
    baseUrl: getSetting_(
      isLiara ? 'LIARA_BASE_URL' : 'GAPGPT_BASE_URL',
      isLiara ? 'https://ai.liara.ir/api/69467b6ba99a2016cac892e1/v1' : 'https://api.gapgpt.app/v1'
    ),
    model: getSetting_('AI_MODEL', isLiara ? 'openai/gpt-4o-mini' : 'gpt-4o-mini')
  };
}

function aiLimit_() {
  return Math.max(1, parseInt(getSetting_('AI_DAILY_LIMIT', '10'), 10));
}

function aiUsageKey_(deviceId) {
  const day = Utilities.formatDate(new Date(), Session.getScriptTimeZone() || 'UTC', 'yyyyMMdd');
  const digest = Utilities.computeDigest(Utilities.DigestAlgorithm.SHA_256, String(deviceId));
  const safe = Utilities.base64EncodeWebSafe(digest).replace(/=+$/, '');
  return 'ai_usage_' + day + '_' + safe;
}

function consumeAiQuota_(deviceId) {
  if (!deviceId || String(deviceId).length > 128) return false;
  if ((getDeviceRecord_(deviceId).premiumUntil || 0) > Date.now()) return true;
  const lock = LockService.getScriptLock();
  lock.waitLock(10000);
  try {
    const props = PropertiesService.getScriptProperties();
    const key = aiUsageKey_(deviceId);
    const used = parseInt(props.getProperty(key) || '0', 10);
    if (used >= aiLimit_()) return false;
    props.setProperty(key, String(used + 1));
    return true;
  } finally {
    lock.releaseLock();
  }
}

function requireAiFields_(params) {
  const deviceId = String(params.deviceId || '').trim();
  if (!deviceId || deviceId.length > 128) return jsonOutput_({ error: 'deviceId is required' });
  const cfg = aiConfig_();
  if (!cfg.key) return jsonOutput_({ error: 'ai_provider_not_configured' });
  if (!consumeAiQuota_(deviceId)) return jsonOutput_({ error: 'ai_daily_limit_reached', limit: aiLimit_() });
  return null;
}

function aiFetch_(url, payload, timeoutSec) {
  const response = UrlFetchApp.fetch(url, {
    method: 'post',
    contentType: 'application/json',
    muteHttpExceptions: true,
    followRedirects: true,
    timeout: timeoutSec || 20,
    headers: { Authorization: 'Bearer ' + payload.apiKey },
    payload: JSON.stringify(payload.body)
  });
  return response;
}

function handleAiChat_(params) {
  const denied = requireAiFields_(params);
  if (denied) return denied;
  const messages = coerceMessages_(params.messages);
  if (!messages || messages.length === 0 || messages.length > 40) {
    return jsonOutput_({ error: 'messages are required' });
  }
  const cfg = aiConfig_();
  try {
    const response = aiFetch_(cfg.baseUrl + '/chat/completions', {
      apiKey: cfg.key,
      body: {
        model: cfg.model,
        messages: messages,
        max_tokens: Number(params.maxTokens || 500),
        temperature: Number(params.temperature || 0.7)
      }
    });
    if (response.getResponseCode() < 200 || response.getResponseCode() >= 300) {
      return jsonOutput_({ error: 'ai_unavailable', status: response.getResponseCode() });
    }
    const data = JSON.parse(response.getContentText());
    return jsonOutput_({ text: data.choices && data.choices[0] && data.choices[0].message
      ? String(data.choices[0].message.content || '').trim() : '' });
  } catch (err) {
    return jsonOutput_({ error: 'ai_unavailable' });
  }
}

function handleAiStt_(params) {
  const denied = requireAiFields_(params);
  if (denied) return denied;
  const encoded = String(params.audioBase64 || '');
  if (!encoded || encoded.length > 10000000) return jsonOutput_({ error: 'audioBase64 is required' });
  const cfg = aiConfig_();
  try {
    const response = UrlFetchApp.fetch(cfg.baseUrl + '/audio/transcriptions', {
      method: 'post',
      muteHttpExceptions: true,
      headers: { Authorization: 'Bearer ' + cfg.key },
      payload: {
        model: 'whisper-1',
        file: Utilities.newBlob(Utilities.base64Decode(encoded), 'audio/mp4', 'audio.m4a')
      }
    });
    if (response.getResponseCode() < 200 || response.getResponseCode() >= 300) {
      return jsonOutput_({ error: 'stt_unavailable' });
    }
    return jsonOutput_({ text: String(JSON.parse(response.getContentText()).text || '') });
  } catch (err) {
    return jsonOutput_({ error: 'stt_unavailable' });
  }
}

function handleAiTts_(params) {
  const denied = requireAiFields_(params);
  if (denied) return denied;
  const text = String(params.text || '').trim();
  if (!text || text.length > 4000) return jsonOutput_({ error: 'text is required' });
  const result = synthesizeSpeech_(text);
  if (result.error) return jsonOutput_({ error: result.error, status: result.status || 0 });
  return jsonOutput_({ audioBase64: result.audioBase64, mimeType: result.mimeType || 'audio/mpeg' });
}

function spokenReminderFallback_(title, description) {
  const t = String(title || '').trim() || 'یک کار';
  const d = String(description || '').trim();
  return d
    ? ('سلام، الان وقتشه که ' + t + '. ' + d)
    : ('سلام، الان وقتشه که ' + t + '. لطفاً انجامش بده.');
}

function rewriteSpokenReminder_(title, description) {
  const fallback = spokenReminderFallback_(title, description);
  const cfg = aiConfig_();
  try {
    const response = aiFetch_(cfg.baseUrl + '/chat/completions', {
      apiKey: cfg.key,
      body: {
        model: cfg.model,
        messages: [
          {
            role: 'system',
            content: 'فقط فارسی محاوره‌ای. یک جمله کوتاه برای خواندن با صدای بلند برگردان. بدون انگلیسی، بدون اموجی، بدون نقل‌قول، بدون توضیح. مثال: سلام، الان وقتشه که قرص فشارتو بخوری.'
          },
          {
            role: 'user',
            content: 'عنوان یادآوری: ' + title + (description ? ('\nتوضیح: ' + description) : '')
          }
        ],
        max_tokens: 80,
        temperature: 0.6
      }
    });
    if (response.getResponseCode() < 200 || response.getResponseCode() >= 300) return fallback;
    const data = JSON.parse(response.getContentText());
    const text = data.choices && data.choices[0] && data.choices[0].message
      ? String(data.choices[0].message.content || '').trim() : '';
    if (!text) return fallback;
    const cleaned = text.replace(/^["'«»]+|["'«»]+$/g, '').slice(0, 220);
    return /[\u0600-\u06FF]/.test(cleaned) ? cleaned : fallback;
  } catch (err) {
    return fallback;
  }
}

function headerValue_(headers, name) {
  if (!headers) return '';
  const lower = String(name).toLowerCase();
  const keys = Object.keys(headers);
  for (let i = 0; i < keys.length; i++) {
    if (String(keys[i]).toLowerCase() === lower) return String(headers[keys[i]] || '');
  }
  return '';
}

function looksLikeAudio_(bytes) {
  if (!bytes || bytes.length < 64) return false;
  const b0 = bytes[0] < 0 ? bytes[0] + 256 : bytes[0];
  const b1 = bytes[1] < 0 ? bytes[1] + 256 : bytes[1];
  const b2 = bytes[2] < 0 ? bytes[2] + 256 : bytes[2];
  const b3 = bytes[3] < 0 ? bytes[3] + 256 : bytes[3];
  if (b0 === 0x49 && b1 === 0x44 && b2 === 0x33) return true; // ID3
  if (b0 === 0xFF && (b1 & 0xE0) === 0xE0) return true; // MPEG frame
  if (b0 === 0x52 && b1 === 0x49 && b2 === 0x46 && b3 === 0x46) return true; // RIFF
  if (b0 === 0x4F && b1 === 0x67 && b2 === 0x67 && b3 === 0x53) return true; // Ogg
  return bytes.length > 800;
}

function synthesizeOpenAiSpeech_(text, model, timeoutSec, withInstructions) {
  const cfg = aiConfig_();
  const body = { model: model, voice: 'alloy', input: text, response_format: 'mp3' };
  if (withInstructions) {
    body.instructions = 'Speak only in clear natural Persian (Farsi). Do not speak English.';
  }
  const response = aiFetch_(cfg.baseUrl + '/audio/speech', {
    apiKey: cfg.key,
    body: body
  }, timeoutSec || 25);
  const status = response.getResponseCode();
  if (status < 200 || status >= 300) return { error: 'tts_unavailable', status: status };
  const contentType = headerValue_(response.getHeaders(), 'Content-Type').toLowerCase();
  if (contentType.indexOf('json') !== -1) {
    try {
      const data = JSON.parse(response.getContentText());
      const err = data && (data.error && data.error.message ? data.error.message : data.error);
      return { error: String(err || 'tts_unavailable'), status: status };
    } catch (err) {
      return { error: 'tts_unavailable', status: status };
    }
  }
  const bytes = response.getBlob().getBytes();
  if (!looksLikeAudio_(bytes)) return { error: 'tts_unavailable', status: status };
  return { audioBase64: Utilities.base64Encode(bytes), mimeType: 'audio/mpeg' };
}

function synthesizeSpeech_(text) {
  const preferred = getSetting_('AI_TTS_MODEL', 'gpt-4o-mini-tts');
  try {
    const first = synthesizeOpenAiSpeech_(text, preferred, 25, preferred === 'gpt-4o-mini-tts');
    if (first && first.audioBase64) return first;
    if (preferred === 'gpt-4o-mini-tts' && first && first.status) {
      const retry = synthesizeOpenAiSpeech_(text, preferred, 25, false);
      if (retry && retry.audioBase64) return retry;
    }
  } catch (err) {}
  if (preferred !== 'tts-1') {
    try {
      const second = synthesizeOpenAiSpeech_(text, 'tts-1', 20, false);
      if (second && second.audioBase64) return second;
    } catch (err) {}
  }
  return { error: 'tts_unavailable' };
}

function handleAiSmartAlert_(params) {
  const denied = requireAiFields_(params);
  if (denied) return denied;
  const title = String(params.title || '').trim();
  const description = String(params.description || '').trim();
  if (!title) return jsonOutput_({ error: 'title is required' });
  const spoken = rewriteSpokenReminder_(title, description);
  const result = synthesizeSpeech_(spoken);
  if (result.error) return jsonOutput_({ error: result.error, status: result.status || 0, text: spoken });
  return jsonOutput_({
    text: spoken,
    audioBase64: result.audioBase64,
    mimeType: result.mimeType || 'audio/mpeg'
  });
}

// --- Bazaar / Myket in-app purchase verification (optional) -------------------------
// Only needed if you want people who installed from Bazaar/Myket to pay through the
// store's own in-app purchase sheet instead of a browser. Add these Script Properties:
//   APP_PACKAGE_NAME, BAZAAR_API_TOKEN, MYKET_ACCESS_TOKEN
// Confirmed against each store's own docs (July 2026): BOTH now use a single static
// per-app token in a request header - no OAuth2, no refresh, nothing to renew. SKUs must
// match BazaarBillingHelper.SKU_*/MyketBillingHelper.SKU_* on the Android side, and must
// be created as CONSUMABLE in-app products (Myket has no real subscription product type).

const PLAN_TO_SKU_ = { monthly: 'yadavar_pro_monthly', yearly: 'yadavar_pro_yearly' };

/** Confirmed against Bazaar's official "راه اندازی API (روش جدید)" و "ارسال درخواست
 *  به API بازار (روش جدید)" docs:
 *  GET https://pardakht.cafebazaar.ir/devapi/v2/api/validate/{PACKAGE_NAME}/inapp/{SKU}/purchases/{PURCHASE_TOKEN}
 *  Header: CAFEBAZAAR-PISHKHAN-API-SECRET: {TOKEN}
 *  Response.purchaseState: 0 = purchased normally, 1 = refunded. */
function verifyBazaarPurchase_(sku, purchaseToken) {
  const apiSecret = getSetting_('BAZAAR_API_TOKEN', '');
  if (!apiSecret) return false;
  const packageName = getSetting_('APP_PACKAGE_NAME', 'com.ghadirb.yadavar');
  const url = 'https://pardakht.cafebazaar.ir/devapi/v2/api/validate/' + packageName +
    '/inapp/' + sku + '/purchases/' + purchaseToken;
  const response = UrlFetchApp.fetch(url, {
    method: 'get',
    muteHttpExceptions: true,
    headers: { 'CAFEBAZAAR-PISHKHAN-API-SECRET': apiSecret }
  });
  if (response.getResponseCode() !== 200) return false;
  const data = JSON.parse(response.getContentText());
  return data.purchaseState === 0 || data.purchaseState === '0';
}

/** Confirmed against Myket's official "استفاده از API صحت سنجی خرید" docs:
 *  POST https://developer.myket.ir/api/partners/applications/{PACKAGE_NAME}/purchases/products/{SKU_ID}/verify
 *  Header: X-Access-Token: {ACCESS_TOKEN}   Body: { "tokenId": "{TOKEN_ID}" }
 *  Response.purchaseState: 0 = successful purchase, 1 = failed. */
function verifyMyketPurchase_(sku, purchaseToken) {
  const accessToken = getSetting_('MYKET_ACCESS_TOKEN', '');
  if (!accessToken) return false;
  const packageName = getSetting_('APP_PACKAGE_NAME', 'com.ghadirb.yadavar');
  const url = 'https://developer.myket.ir/api/partners/applications/' + packageName +
    '/purchases/products/' + sku + '/verify';
  const response = UrlFetchApp.fetch(url, {
    method: 'post',
    contentType: 'application/json',
    muteHttpExceptions: true,
    headers: { 'X-Access-Token': accessToken },
    payload: JSON.stringify({ tokenId: purchaseToken })
  });
  if (response.getResponseCode() !== 200) return false;
  const data = JSON.parse(response.getContentText());
  return data.purchaseState === 0 || data.purchaseState === '0';
}

function handleVerifyStore_(params) {
  const deviceId = params.deviceId;
  const plan = params.plan;
  const channel = params.channel;
  const purchaseToken = params.purchaseToken;
  const planConfig = getPlans_()[plan];
  const sku = PLAN_TO_SKU_[plan];

  if (!deviceId || !planConfig || !sku || !purchaseToken || (channel !== 'bazaar' && channel !== 'myket')) {
    return jsonOutput_({ verified: false, error: 'invalid_request' });
  }

  try {
    const purchaseKey = channel + ':' + purchaseToken;
    const existing = getStorePurchase_(purchaseKey);
    if (existing) {
      if (existing.deviceId !== deviceId || existing.plan !== plan) {
        return jsonOutput_({ verified: false, error: 'purchase_already_linked' });
      }
      return jsonOutput_({ verified: true, premiumUntil: getDeviceRecord_(deviceId).premiumUntil || 0, alreadyProcessed: true });
    }
    const verified = (channel === 'bazaar')
      ? verifyBazaarPurchase_(sku, purchaseToken)
      : verifyMyketPurchase_(sku, purchaseToken);

    if (!verified) {
      return jsonOutput_({ verified: false, premiumUntil: getDeviceRecord_(deviceId).premiumUntil || 0 });
    }

    // Serialize the check-and-grant step so two simultaneous callbacks cannot add the
    // same receipt twice.
    const lock = LockService.getScriptLock();
    if (!lock.tryLock(10000)) return jsonOutput_({ verified: false, error: 'verification_busy' });
    try {
      const completed = getStorePurchase_(purchaseKey);
      if (completed) {
        return jsonOutput_({ verified: true, premiumUntil: getDeviceRecord_(deviceId).premiumUntil || 0, alreadyProcessed: true });
      }
      saveStorePurchase_(purchaseKey, { deviceId: deviceId, plan: plan, channel: channel, createdAt: Date.now() });
      const premiumUntil = grantPremiumDays_(deviceId, planConfig.days);
      return jsonOutput_({ verified: true, premiumUntil: premiumUntil });
    } finally {
      lock.releaseLock();
    }
  } catch (err) {
    return jsonOutput_({ verified: false, error: 'verification_failed' });
  }
}

function handleStatus_(params) {
  const deviceId = params.deviceId;
  if (!deviceId) return jsonOutput_({ error: 'deviceId is required' });
  const record = getDeviceRecord_(deviceId);
  return jsonOutput_({ isPremium: record.premiumUntil > Date.now(), premiumUntil: record.premiumUntil });
}

function handleRequest_(params) {
  const deviceId = params.deviceId;
  const plan = params.plan;
  const planConfig = getPlans_()[plan];
  if (!deviceId || !planConfig) return jsonOutput_({ error: 'deviceId and a valid plan are required' });

  const gateway = activeGateway_();
  if (gateway === 'nextpay') return handleRequestNextpay_(deviceId, plan, planConfig);
  if (gateway === 'payping') return handleRequestPayping_(deviceId, plan, planConfig);
  return handleRequestZarinpal_(deviceId, plan, planConfig);
}

function handleRequestPayping_(deviceId, plan, planConfig) {
  const token = getSetting_('PAYPING_TOKEN', '');
  if (!token) return jsonOutput_({ error: 'PAYPING_TOKEN is not configured' });

  const selfUrl = ScriptApp.getService().getUrl();
  const clientRefId = deviceId + '_' + plan + '_' + Date.now();
  const returnUrl = selfUrl + '?path=paypingCallback&deviceId=' + encodeURIComponent(deviceId) + '&plan=' + plan;

  const response = UrlFetchApp.fetch(PAYPING_BASE + '/pay', {
    method: 'post',
    contentType: 'application/json',
    muteHttpExceptions: true,
    headers: { Authorization: 'Bearer ' + token },
    payload: JSON.stringify({
      amount: planConfig.amount,
      returnUrl: returnUrl,
      description: 'یادآور پرو - ' + (plan === 'monthly' ? 'اشتراک ماهانه' : 'اشتراک سالانه'),
      clientRefId: clientRefId
    })
  });

  const data = JSON.parse(response.getContentText());
  if (data && data.paymentCode) {
    const order = { deviceId: deviceId, plan: plan, amount: planConfig.amount, paymentCode: data.paymentCode, clientRefId: clientRefId, verified: false };
    saveOrder_(data.paymentCode, order);
    saveOrder_(clientRefId, order);
    return jsonOutput_({ paymentUrl: data.url || (PAYPING_BASE + '/pay/start/' + data.paymentCode) });
  }

  return jsonOutput_({ error: 'payping_request_failed', details: data });
}

function handleRequestZarinpal_(deviceId, plan, planConfig) {
  const merchantId = getSetting_('ZARINPAL_MERCHANT_ID', '');
  if (!merchantId) return jsonOutput_({ error: 'ZARINPAL_MERCHANT_ID is not configured' });

  // The web app's own /exec URL, so Zarinpal can redirect back into this same script.
  const selfUrl = ScriptApp.getService().getUrl();
  const callbackUrl = selfUrl + '?path=callback&deviceId=' + encodeURIComponent(deviceId) + '&plan=' + plan;

  const urls = zarinpalUrls_();
  const response = UrlFetchApp.fetch(urls.request, {
    method: 'post',
    contentType: 'application/json',
    muteHttpExceptions: true,
    payload: JSON.stringify({
      merchant_id: merchantId,
      amount: planConfig.amount,
      callback_url: callbackUrl,
      description: 'یادآور پرو - ' + (plan === 'monthly' ? 'اشتراک ماهانه' : 'اشتراک سالانه')
    })
  });

  const data = JSON.parse(response.getContentText());
  if (data && data.data && data.data.code === 100) {
    const authority = data.data.authority;
    saveOrder_(authority, { deviceId: deviceId, plan: plan, amount: planConfig.amount, verified: false });
    return jsonOutput_({ paymentUrl: urls.startPay + authority });
  }

  return jsonOutput_({ error: 'zarinpal_request_failed', details: data });
}

function handleRequestNextpay_(deviceId, plan, planConfig) {
  const apiKey = getSetting_('NEXTPAY_API_KEY', '');
  if (!apiKey) return jsonOutput_({ error: 'NEXTPAY_API_KEY is not configured' });

  const selfUrl = ScriptApp.getService().getUrl();
  // order_id just needs to be unique per attempt - NextPay hands it back on callback,
  // it's not looked up here (the trans_id it also hands back is the real order key).
  const orderId = deviceId + '_' + plan + '_' + Date.now();
  const callbackUrl = selfUrl + '?path=callback&deviceId=' + encodeURIComponent(deviceId) + '&plan=' + plan;

  const response = UrlFetchApp.fetch(NEXTPAY_TOKEN_URL, {
    method: 'post',
    contentType: 'application/x-www-form-urlencoded',
    muteHttpExceptions: true,
    payload: {
      api_key: apiKey,
      order_id: orderId,
      amount: String(planConfig.amount),
      callback_uri: callbackUrl
    }
  });

  const data = JSON.parse(response.getContentText());
  // NextPay's own convention: -1 means the token was created successfully here (0 is
  // reserved for a *verified payment*, not this step - easy to trip over).
  if (data && Number(data.code) === -1 && data.trans_id) {
    saveOrder_(data.trans_id, { deviceId: deviceId, plan: plan, amount: planConfig.amount, orderId: orderId, verified: false });
    return jsonOutput_({ paymentUrl: NEXTPAY_PAYMENT_BASE + data.trans_id });
  }

  return jsonOutput_({ error: 'nextpay_request_failed', details: data });
}

function handleCallback_(params) {
  return (activeGateway_() === 'nextpay')
    ? handleCallbackNextpay_(params)
    : handleCallbackZarinpal_(params);
}

function handleCallbackPayping_(params) {
  // PayPing posts the result back to returnUrl. Field names have changed between
  // documentation versions, so accept the common spellings and verify against the
  // original order saved under paymentCode before granting premium access.
  const paymentCode = params.paymentCode || params.PaymentCode || params.code;
  const clientRefId = params.clientRefId || params.ClientRefId || params.client_ref_id;
  const refId = params.paymentRefId || params.refId || params.RefId || params.refid || '';

  if (!paymentCode && !clientRefId) {
    return htmlOutput_('<h2>❌ پرداخت ناموفق</h2><p>پرداخت لغو شد یا اطلاعات برگشتی ناقص بود.</p>');
  }

  const order = getOrder_(paymentCode) || getOrder_(clientRefId);
  if (!order) {
    return htmlOutput_('<h2>❌ پرداخت ناموفق</h2><p>این تراکنش شناخته نشده است.</p>');
  }
  if (order.verified) {
    return htmlOutput_('<h2>✅ پرداخت با موفقیت انجام شد</h2><p>اشتراک پریمیوم شما فعال است.</p>');
  }
  if (params.amount && Number(params.amount) !== Number(order.amount)) {
    return htmlOutput_('<h2>❌ پرداخت ناموفق</h2><p>مبلغ برگشتی با سفارش ثبت‌شده همخوانی ندارد.</p>');
  }

  const token = getSetting_('PAYPING_TOKEN', '');
  if (!token) {
    return htmlOutput_('<h2>❌ پرداخت ناموفق</h2><p>توکن پی‌پینگ روی سرور تنظیم نشده است.</p>');
  }

  const response = UrlFetchApp.fetch(PAYPING_BASE + '/pay/verify', {
    method: 'post',
    contentType: 'application/json',
    muteHttpExceptions: true,
    headers: { Authorization: 'Bearer ' + token },
    payload: JSON.stringify({
      paymentCode: paymentCode || order.paymentCode,
      clientRefId: clientRefId || order.clientRefId,
      paymentRefId: refId,
      amount: order.amount
    })
  });

  const status = response.getResponseCode();
  const text = response.getContentText();
  const data = text ? JSON.parse(text) : {};

  if (status >= 200 && status < 300) {
    order.verified = true;
    order.refId = refId;
    order.verifyResponse = data;
    saveOrder_(paymentCode || order.paymentCode, order);
    saveOrder_(clientRefId || order.clientRefId, order);
    grantPremiumDays_(order.deviceId, getPlans_()[order.plan].days);
    return htmlOutput_('<h2>✅ پرداخت با موفقیت انجام شد</h2><p>اشتراک پریمیوم شما فعال شد.</p>');
  }

  return htmlOutput_('<h2>❌ پرداخت ناموفق</h2><p>تایید پرداخت توسط پی‌پینگ ناموفق بود.</p>');
}

function handleCallbackZarinpal_(params) {
  const authority = params.Authority;
  const status = params.Status;
  const deviceId = params.deviceId;
  const plan = params.plan;
  const planConfig = getPlans_()[plan];

  if (status !== 'OK' || !authority || !deviceId || !planConfig) {
    return htmlOutput_('<h2>❌ پرداخت ناموفق</h2><p>پرداخت لغو شد یا اطلاعات ناقص بود.</p>');
  }

  const order = getOrder_(authority);
  if (!order) {
    return htmlOutput_('<h2>❌ پرداخت ناموفق</h2><p>این تراکنش شناخته نشده است.</p>');
  }
  if (order.verified) {
    return htmlOutput_('<h2>✅ پرداخت با موفقیت انجام شد</h2><p>اشتراک پریمیوم شما فعال است.</p>');
  }

  const merchantId = getSetting_('ZARINPAL_MERCHANT_ID', '');
  const urls = zarinpalUrls_();
  const response = UrlFetchApp.fetch(urls.verify, {
    method: 'post',
    contentType: 'application/json',
    muteHttpExceptions: true,
    payload: JSON.stringify({ merchant_id: merchantId, amount: order.amount, authority: authority })
  });
  const data = JSON.parse(response.getContentText());

  // 100 = verified now, 101 = already verified before - both count as success.
  if (data && data.data && (data.data.code === 100 || data.data.code === 101)) {
    order.verified = true;
    order.refId = data.data.ref_id;
    saveOrder_(authority, order);
    grantPremiumDays_(deviceId, planConfig.days);
    return htmlOutput_('<h2>✅ پرداخت با موفقیت انجام شد</h2><p>اشتراک پریمیوم شما فعال شد.</p>');
  }

  return htmlOutput_('<h2>❌ پرداخت ناموفق</h2><p>تایید پرداخت توسط زرین‌پال ناموفق بود.</p>');
}

function handleCallbackNextpay_(params) {
  // NextPay redirects back with these after the payer finishes (or cancels) on its page.
  const transId = params.trans_id;
  const deviceId = params.deviceId;
  const plan = params.plan;
  const planConfig = getPlans_()[plan];

  if (!transId || !deviceId || !planConfig) {
    return htmlOutput_('<h2>❌ پرداخت ناموفق</h2><p>پرداخت لغو شد یا اطلاعات ناقص بود.</p>');
  }

  const order = getOrder_(transId);
  if (!order) {
    return htmlOutput_('<h2>❌ پرداخت ناموفق</h2><p>این تراکنش شناخته نشده است.</p>');
  }
  if (order.verified) {
    return htmlOutput_('<h2>✅ پرداخت با موفقیت انجام شد</h2><p>اشتراک پریمیوم شما فعال است.</p>');
  }

  const apiKey = getSetting_('NEXTPAY_API_KEY', '');
  const response = UrlFetchApp.fetch(NEXTPAY_VERIFY_URL, {
    method: 'post',
    contentType: 'application/x-www-form-urlencoded',
    muteHttpExceptions: true,
    payload: {
      api_key: apiKey,
      order_id: order.orderId,
      amount: String(order.amount),
      trans_id: transId
    }
  });
  const data = JSON.parse(response.getContentText());

  // Unlike the token step above, a *verify* success is code === 0 here - this is
  // NextPay's own convention, not a typo copied from the token step.
  if (data && Number(data.code) === 0) {
    order.verified = true;
    saveOrder_(transId, order);
    grantPremiumDays_(deviceId, planConfig.days);
    return htmlOutput_('<h2>✅ پرداخت با موفقیت انجام شد</h2><p>اشتراک پریمیوم شما فعال شد.</p>');
  }

  return htmlOutput_('<h2>❌ پرداخت ناموفق</h2><p>تایید پرداخت توسط نکست‌پی ناموفق بود.</p>');
}
