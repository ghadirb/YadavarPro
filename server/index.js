// Minimal Zarinpal payment + subscription-status backend for Yadavar Pro.
// Storage is a single JSON file (db.json) - completely fine for hundreds/low-thousands
// of users; if you outgrow it later, swap the four functions at the top for real
// database calls (Postgres, MongoDB, etc.) without touching the routes below.

const express = require('express');
const fs = require('fs');
const path = require('path');

const app = express();
app.use(express.json());

const DB_PATH = path.join(__dirname, 'db.json');

const MERCHANT_ID = process.env.ZARINPAL_MERCHANT_ID || '';
const SANDBOX = (process.env.ZARINPAL_SANDBOX || 'true') === 'true';
const PUBLIC_BASE_URL = process.env.PUBLIC_BASE_URL || 'http://localhost:3000';
const PORT = process.env.PORT || 3000;

// Prices are in Rial (Zarinpal's unit) - 1 Toman = 10 Rial.
// Monthly: 199,000 Toman = 1,990,000 Rial. Yearly: 1,890,000 Toman = 18,900,000 Rial.
const PLANS = {
  monthly: { days: 30, amountRial: parseInt(process.env.PRICE_MONTHLY_RIAL || '1990000', 10) },
  yearly: { days: 365, amountRial: parseInt(process.env.PRICE_YEARLY_RIAL || '18900000', 10) }
};

// SKU ids as registered in the Bazaar/Myket developer panels - must match
// BazaarBillingHelper.SKU_* / MyketBillingHelper.SKU_* on the Android side.
const PLAN_TO_SKU = { monthly: 'yadavar_pro_monthly', yearly: 'yadavar_pro_yearly' };
const APP_PACKAGE_NAME = process.env.APP_PACKAGE_NAME || 'com.ghadirb.yadavar';

const ZARINPAL_REQUEST_URL = SANDBOX
  ? 'https://sandbox.zarinpal.com/pg/v4/payment/request.json'
  : 'https://api.zarinpal.com/pg/v4/payment/request.json';
const ZARINPAL_VERIFY_URL = SANDBOX
  ? 'https://sandbox.zarinpal.com/pg/v4/payment/verify.json'
  : 'https://api.zarinpal.com/pg/v4/payment/verify.json';
const ZARINPAL_STARTPAY_URL = SANDBOX
  ? 'https://sandbox.zarinpal.com/pg/StartPay/'
  : 'https://www.zarinpal.com/pg/StartPay/';

// --- tiny JSON "database" -----------------------------------------------------------

function loadDb() {
  if (!fs.existsSync(DB_PATH)) {
    return { devices: {}, orders: {}, storePurchases: {} };
  }
  try {
    return JSON.parse(fs.readFileSync(DB_PATH, 'utf8'));
  } catch (e) {
    console.error('db.json is corrupt, starting fresh:', e.message);
    return { devices: {}, orders: {} };
  }
}

function saveDb(db) {
  fs.writeFileSync(DB_PATH, JSON.stringify(db, null, 2));
}

function getPremiumUntil(deviceId) {
  const db = loadDb();
  return (db.devices[deviceId] && db.devices[deviceId].premiumUntil) || 0;
}

/** Extends (never shortens) the device's premium expiry by `days` from whichever is
 *  later: now, or their current expiry - so buying more time while already premium
 *  stacks on top instead of wasting the remaining days. */
function grantPremiumDays(deviceId, days) {
  const db = loadDb();
  const current = (db.devices[deviceId] && db.devices[deviceId].premiumUntil) || 0;
  const base = Math.max(current, Date.now());
  const premiumUntil = base + days * 24 * 60 * 60 * 1000;
  db.devices[deviceId] = { premiumUntil };
  saveDb(db);
  return premiumUntil;
}

// --- Bazaar / Myket in-app purchase verification --------------------------------------
// Confirmed against each store's own official docs (July 2026): BOTH now use a simple
// static per-app token in a request header - no OAuth2, no refresh, no expiry to manage.
// Bazaar: header "CAFEBAZAAR-PISHKHAN-API-SECRET" (from پیشخان بازار -> برنامه شما ->
// API پیشخان بازار -> دریافت توکن جدید). Myket: header "X-Access-Token" (see
// verifyMyketPurchase below). If you haven't set up credentials in .env for a given
// store, its verify function simply returns false - the Zarinpal direct-payment flow
// keeps working regardless.

/** Confirmed against Bazaar's official "راه اندازی API (روش جدید)" و "ارسال درخواست
 *  به API بازار (روش جدید)" docs:
 *  GET https://pardakht.cafebazaar.ir/devapi/v2/api/validate/{PACKAGE_NAME}/inapp/{SKU}/purchases/{PURCHASE_TOKEN}
 *  Header: CAFEBAZAAR-PISHKHAN-API-SECRET: {TOKEN}
 *  Response.purchaseState: 0 = purchased normally, 1 = refunded. */
async function verifyBazaarPurchase(sku, purchaseToken) {
  const apiSecret = process.env.BAZAAR_API_TOKEN || '';
  if (!apiSecret) return false;
  const url = `https://pardakht.cafebazaar.ir/devapi/v2/api/validate/${APP_PACKAGE_NAME}/inapp/${sku}/purchases/${purchaseToken}`;
  const resp = await fetch(url, {
    headers: { 'CAFEBAZAAR-PISHKHAN-API-SECRET': apiSecret }
  });
  if (!resp.ok) return false;
  const data = await resp.json();
  return data.purchaseState === 0 || data.purchaseState === '0';
}

async function getMyketAccessToken() {
  // Confirmed against Myket's official "استفاده از API صحت سنجی خرید" docs: Myket's
  // purchase-verification API also just uses a single static "X-Access-Token" you
  // generate once per app in the developer panel and store directly as
  // MYKET_ACCESS_TOKEN in .env - no token exchange call needed.
  return process.env.MYKET_ACCESS_TOKEN || '';
}

/** Confirmed against Myket's official "استفاده از API صحت سنجی خرید" docs:
 *  POST https://developer.myket.ir/api/partners/applications/{PACKAGE_NAME}/purchases/products/{SKU_ID}/verify
 *  Header: X-Access-Token: {ACCESS_TOKEN}   Body: { "tokenId": "{TOKEN_ID}" }
 *  Response.purchaseState: 0 = successful purchase, 1 = failed. */
async function verifyMyketPurchase(sku, purchaseToken) {
  const accessToken = await getMyketAccessToken();
  if (!accessToken) return false;
  const url = `https://developer.myket.ir/api/partners/applications/${APP_PACKAGE_NAME}/purchases/products/${sku}/verify`;
  const resp = await fetch(url, {
    method: 'POST',
    headers: {
      'X-Access-Token': accessToken,
      'Content-Type': 'application/json'
    },
    body: JSON.stringify({ tokenId: purchaseToken })
  });
  if (!resp.ok) return false;
  const data = await resp.json();
  return data.purchaseState === 0 || data.purchaseState === '0';
}

// --- routes --------------------------------------------------------------------------

app.get('/', (req, res) => res.send('Yadavar Pro billing backend is running.'));

app.get('/subscription/status', (req, res) => {
  const deviceId = String(req.query.deviceId || '');
  if (!deviceId) return res.status(400).json({ error: 'deviceId is required' });
  const premiumUntil = getPremiumUntil(deviceId);
  res.json({ isPremium: premiumUntil > Date.now(), premiumUntil });
});

app.get('/payment/request', async (req, res) => {
  try {
    const { deviceId, plan } = req.query || {};
    const planConfig = PLANS[plan];
    if (!deviceId || !planConfig) {
      return res.status(400).json({ error: 'deviceId and a valid plan are required' });
    }
    if (!MERCHANT_ID) {
      return res.status(500).json({ error: 'ZARINPAL_MERCHANT_ID is not configured on the server' });
    }

    const callbackUrl = `${PUBLIC_BASE_URL}/payment/callback?deviceId=${encodeURIComponent(deviceId)}&plan=${plan}`;

    const zpResponse = await fetch(ZARINPAL_REQUEST_URL, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({
        merchant_id: MERCHANT_ID,
        amount: planConfig.amountRial,
        callback_url: callbackUrl,
        description: `یادآور پرو - ${plan === 'monthly' ? 'اشتراک ماهانه' : 'اشتراک سالانه'}`
      })
    });
    const zpData = await zpResponse.json();

    if (zpData && zpData.data && zpData.data.code === 100) {
      const authority = zpData.data.authority;
      const db = loadDb();
      db.orders[authority] = { deviceId, plan, amountRial: planConfig.amountRial, verified: false };
      saveDb(db);
      return res.json({ paymentUrl: `${ZARINPAL_STARTPAY_URL}${authority}` });
    }

    console.error('Zarinpal request.json error:', JSON.stringify(zpData));
    res.status(502).json({ error: 'zarinpal_request_failed', details: zpData });
  } catch (e) {
    console.error('POST /payment/request failed:', e);
    res.status(500).json({ error: 'internal_error' });
  }
});

app.post('/payment/verify-store', async (req, res) => {
  try {
    const input = { ...(req.query || {}), ...(req.body || {}) };
    const { deviceId, plan, channel, purchaseToken } = input;
    const planConfig = PLANS[plan];
    const sku = PLAN_TO_SKU[plan];
    if (!deviceId || !planConfig || !sku || !purchaseToken || !['bazaar', 'myket'].includes(channel)) {
      return res.status(400).json({ error: 'deviceId, a valid plan, channel (bazaar/myket) and purchaseToken are required' });
    }

    const purchaseKey = `${channel}:${purchaseToken}`;
    const db = loadDb();
    const existing = db.storePurchases && db.storePurchases[purchaseKey];
    if (existing) {
      if (existing.deviceId !== deviceId || existing.plan !== plan) {
        return res.status(409).json({ verified: false, error: 'purchase_already_linked' });
      }
      return res.json({ verified: true, premiumUntil: getPremiumUntil(deviceId), alreadyProcessed: true });
    }

    const verified = channel === 'bazaar'
      ? await verifyBazaarPurchase(sku, purchaseToken)
      : await verifyMyketPurchase(sku, purchaseToken);

    if (!verified) {
      return res.json({ verified: false, premiumUntil: getPremiumUntil(deviceId) });
    }

    // Re-read after the network verification too: two callbacks can be in flight at
    // once, and only the first one is allowed to grant this token's entitlement.
    const latestDb = loadDb();
    if (latestDb.storePurchases && latestDb.storePurchases[purchaseKey]) {
      return res.json({ verified: true, premiumUntil: getPremiumUntil(deviceId), alreadyProcessed: true });
    }
    // Persist the token before granting access. A repeated callback then returns the
    // original entitlement instead of extending premium a second time.
    latestDb.storePurchases = latestDb.storePurchases || {};
    latestDb.storePurchases[purchaseKey] = { deviceId, plan, channel, createdAt: Date.now() };
    saveDb(latestDb);
    const premiumUntil = grantPremiumDays(deviceId, planConfig.days);
    res.json({ verified: true, premiumUntil });
  } catch (e) {
    console.error('POST /payment/verify-store failed:', e);
    // Fails closed (verified:false) rather than 500-ing into a false grant - a
    // misconfigured/missing Bazaar or Myket credential should never accidentally look
    // like a successful purchase to the app.
    res.json({ verified: false, error: 'verification_failed' });
  }
});

app.get('/payment/callback', async (req, res) => {
  const { Authority, Status, deviceId, plan } = req.query;
  const planConfig = PLANS[plan];

  const fail = (message) => res.status(200).send(`
    <html dir="rtl" lang="fa"><body style="font-family:tahoma;text-align:center;padding:40px">
    <h2>❌ پرداخت ناموفق</h2><p>${message}</p>
    <p>می‌توانید این صفحه را ببندید و به اپ یادآور پرو برگردید.</p>
    </body></html>`);

  if (Status !== 'OK' || !Authority || !deviceId || !planConfig) {
    return fail('پرداخت توسط شما لغو شد یا اطلاعات ناقص بود.');
  }

  try {
    const db = loadDb();
    const order = db.orders[Authority];
    if (!order) return fail('این تراکنش شناخته نشده است.');
    if (order.verified) {
      return res.send(successHtml());
    }

    const zpResponse = await fetch(ZARINPAL_VERIFY_URL, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({
        merchant_id: MERCHANT_ID,
        amount: order.amountRial,
        authority: Authority
      })
    });
    const zpData = await zpResponse.json();

    // 100 = verified now, 101 = already verified before - both count as success.
    if (zpData && zpData.data && (zpData.data.code === 100 || zpData.data.code === 101)) {
      order.verified = true;
      order.refId = zpData.data.ref_id;
      db.orders[Authority] = order;
      saveDb(db);
      grantPremiumDays(deviceId, planConfig.days);
      return res.send(successHtml());
    }

    console.error('Zarinpal verify.json error:', JSON.stringify(zpData));
    return fail('تایید پرداخت توسط زرین‌پال ناموفق بود.');
  } catch (e) {
    console.error('GET /payment/callback failed:', e);
    return fail('خطای داخلی سرور در تایید پرداخت.');
  }
});

function successHtml() {
  return `
    <html dir="rtl" lang="fa"><body style="font-family:tahoma;text-align:center;padding:40px">
    <h2>✅ پرداخت با موفقیت انجام شد</h2>
    <p>اشتراک پریمیوم شما فعال شد. این صفحه را ببندید و به اپ یادآور پرو برگردید.</p>
    </body></html>`;
}

app.listen(PORT, () => {
  console.log(`Yadavar Pro billing backend listening on port ${PORT} (sandbox=${SANDBOX})`);
});
