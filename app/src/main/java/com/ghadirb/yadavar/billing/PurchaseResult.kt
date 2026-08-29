package com.ghadirb.yadavar.billing

/** Unified outcome of a purchase, whatever channel handled it.
 *  [Success.purchaseToken] must be sent to the backend so it can verify with
 *  Bazaar/Myket server-to-server before granting premium. */
sealed class PurchaseResult {
    data class Success(
        val sku: String,
        val purchaseToken: String,
        val orderId: String? = null
    ) : PurchaseResult()

    data class Failed(val message: String) : PurchaseResult()
    object Cancelled : PurchaseResult()
}
