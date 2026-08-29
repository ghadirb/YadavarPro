package com.ghadirb.yadavar.billing

import android.app.Activity
import ir.myket.billingclient.IabHelper
import ir.myket.billingclient.util.IabResult
import ir.myket.billingclient.util.Inventory
import ir.myket.billingclient.util.Purchase

/**
 * Shared implementation used by the Bazaar and Myket flavors. Products remain owned
 * until the backend verifies their token; only then are they consumed. This prevents a
 * paid but interrupted purchase from being lost before premium access is granted.
 */
internal class StoreBillingHelper(private val publicKey: () -> String) {
    private var helper: IabHelper? = null
    private val purchasesByToken = mutableMapOf<String, Purchase>()

    fun connect(
        activity: Activity,
        onReady: (Boolean) -> Unit,
        onPendingPurchase: (PurchaseResult.Success) -> Unit
    ) {
        val key = publicKey()
        if (key.isBlank()) {
            onReady(false)
            return
        }
        val instance = IabHelper(activity, key)
        helper = instance
        instance.startSetup { result: IabResult ->
            if (!result.isSuccess) {
                onReady(false)
                return@startSetup
            }
            onReady(true)
            recoverPendingPurchases(onPendingPurchase)
        }
    }

    fun disconnect() {
        helper?.dispose()
        helper = null
        purchasesByToken.clear()
    }

    fun purchase(activity: Activity, sku: String, payload: String, onResult: (PurchaseResult) -> Unit) {
        val current = helper
        if (current == null) {
            onResult(PurchaseResult.Failed("اتصال به فروشگاه برقرار نشده است"))
            return
        }
        current.launchPurchaseFlow(activity, sku, { result: IabResult, purchase: Purchase? ->
            when {
                result.isFailure -> onResult(PurchaseResult.Failed(result.message ?: "خرید ناموفق بود"))
                purchase == null -> onResult(PurchaseResult.Failed("پاسخ نامعتبر از فروشگاه"))
                else -> {
                    purchasesByToken[purchase.token] = purchase
                    onResult(purchase.toResult())
                }
            }
        }, payload)
    }

    fun consume(purchaseToken: String, onResult: (Boolean) -> Unit) {
        val current = helper ?: run {
            onResult(false)
            return
        }
        val cached = purchasesByToken[purchaseToken]
        if (cached != null) {
            consumePurchase(current, cached, onResult)
            return
        }
        current.queryInventoryAsync(true, BillingCatalog.ALL_SKUS) { result: IabResult, inventory: Inventory? ->
            val purchase = if (result.isSuccess && inventory != null) {
                BillingCatalog.ALL_SKUS
                    .mapNotNull { inventory.getPurchase(it) }
                    .firstOrNull { it.token == purchaseToken }
            } else null
            if (purchase == null) onResult(false) else consumePurchase(current, purchase, onResult)
        }
    }

    private fun recoverPendingPurchases(onPendingPurchase: (PurchaseResult.Success) -> Unit) {
        val current = helper ?: return
        current.queryInventoryAsync(true, BillingCatalog.ALL_SKUS) { result: IabResult, inventory: Inventory? ->
            if (result.isFailure || inventory == null) return@queryInventoryAsync
            BillingCatalog.ALL_SKUS.forEach { sku ->
                inventory.getPurchase(sku)?.let { purchase ->
                    purchasesByToken[purchase.token] = purchase
                    onPendingPurchase(purchase.toResult())
                }
            }
        }
    }

    private fun consumePurchase(current: IabHelper, purchase: Purchase, onResult: (Boolean) -> Unit) {
        current.consumeAsync(purchase) { _, result ->
            if (result.isSuccess) purchasesByToken.remove(purchase.token)
            onResult(result.isSuccess)
        }
    }

    private fun Purchase.toResult() = PurchaseResult.Success(sku = sku, purchaseToken = token, orderId = orderId)
}
