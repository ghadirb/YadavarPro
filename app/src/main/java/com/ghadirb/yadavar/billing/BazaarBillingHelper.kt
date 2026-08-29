package com.ghadirb.yadavar.billing

import android.app.Activity
import com.ghadirb.yadavar.BuildConfig

/** Billing adapter for the `bazaar` product flavor. */
object BazaarBillingHelper {
    const val SKU_MONTHLY = BillingCatalog.SKU_MONTHLY
    const val SKU_YEARLY = BillingCatalog.SKU_YEARLY

    private val delegate = StoreBillingHelper { BuildConfig.IAB_PUBLIC_KEY }

    fun connect(
        activity: Activity,
        onReady: (Boolean) -> Unit,
        onPendingPurchase: (PurchaseResult.Success) -> Unit
    ) = delegate.connect(activity, onReady, onPendingPurchase)

    fun disconnect() = delegate.disconnect()

    fun purchase(activity: Activity, sku: String, payload: String, onResult: (PurchaseResult) -> Unit) =
        delegate.purchase(activity, sku, payload, onResult)

    fun consume(purchaseToken: String, onResult: (Boolean) -> Unit) =
        delegate.consume(purchaseToken, onResult)
}
