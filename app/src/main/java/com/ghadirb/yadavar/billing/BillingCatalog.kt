package com.ghadirb.yadavar.billing

/** SKU ids registered in Cafe Bazaar and Myket developer panels.
 *  Must match server/index.js PLAN_TO_SKU. Products are consumable;
 *  premium days are granted by the billing backend after verification. */
object BillingCatalog {
    const val SKU_MONTHLY = "yadavar_pro_monthly"
    const val SKU_YEARLY = "yadavar_pro_yearly"
    val ALL_SKUS = listOf(SKU_MONTHLY, SKU_YEARLY)
}
