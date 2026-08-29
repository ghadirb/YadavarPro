package com.ghadirb.yadavar.ui.subscription

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.ghadirb.yadavar.R
import com.ghadirb.yadavar.billing.BazaarBillingHelper
import com.ghadirb.yadavar.billing.BillingCatalog
import com.ghadirb.yadavar.billing.MyketBillingHelper
import com.ghadirb.yadavar.billing.PurchaseResult
import com.ghadirb.yadavar.billing.StoreChannel
import com.ghadirb.yadavar.utils.PreferencesManager
import com.ghadirb.yadavar.utils.SubscriptionManager
import kotlinx.coroutines.launch

class SubscriptionActivity : AppCompatActivity() {
    private var storeChannel = StoreChannel.DIRECT
    private var storeReady = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_subscription)

        storeChannel = SubscriptionManager.detectStoreChannel(this)
        connectToStoreIfNeeded()
        findViewById<android.view.View>(R.id.monthlyPlanButton).setOnClickListener {
            startCheckout(SubscriptionManager.Plan.MONTHLY)
        }
        findViewById<android.view.View>(R.id.yearlyPlanButton).setOnClickListener {
            startCheckout(SubscriptionManager.Plan.YEARLY)
        }
        findViewById<android.view.View>(R.id.refreshStatusButton).setOnClickListener {
            refreshStatus(showToast = true)
        }
        findViewById<com.google.android.material.appbar.MaterialToolbar>(R.id.toolbar).setNavigationOnClickListener { finish() }
        refreshStatus(showToast = false)
    }

    private fun connectToStoreIfNeeded() {
        when (storeChannel) {
            StoreChannel.BAZAAR -> BazaarBillingHelper.connect(this, { storeReady = it }, ::verifyAndGrantStorePurchase)
            StoreChannel.MYKET -> MyketBillingHelper.connect(this, { storeReady = it }, ::verifyAndGrantStorePurchase)
            StoreChannel.DIRECT -> Unit
        }
    }

    override fun onDestroy() {
        when (storeChannel) {
            StoreChannel.BAZAAR -> BazaarBillingHelper.disconnect()
            StoreChannel.MYKET -> MyketBillingHelper.disconnect()
            StoreChannel.DIRECT -> Unit
        }
        super.onDestroy()
    }

    override fun onResume() {
        super.onResume()
        refreshStatus(showToast = false)
    }

    private fun refreshStatus(showToast: Boolean) {
        val statusView = findViewById<TextView>(R.id.statusText)
        lifecycleScope.launch {
            SubscriptionManager.refreshFromServer(this@SubscriptionActivity)
            renderStatus(statusView)
            if (showToast) Toast.makeText(this@SubscriptionActivity, getString(R.string.subscription_refreshed), Toast.LENGTH_SHORT).show()
        }
        renderStatus(statusView)
    }

    private fun renderStatus(statusView: TextView) {
        val channelLabel = when (storeChannel) {
            StoreChannel.BAZAAR -> getString(R.string.store_bazaar)
            StoreChannel.MYKET -> getString(R.string.store_myket)
            StoreChannel.DIRECT -> getString(R.string.store_direct)
        }
        val status = SubscriptionManager.premiumExpiryLabel(this)
            ?: if (SubscriptionManager.hasPersonalKey(this)) {
                getString(R.string.subscription_personal_key)
            } else {
                getString(
                    R.string.subscription_free_left,
                    SubscriptionManager.remainingFreeLifetime(this),
                    SubscriptionManager.FREE_AI_LIFETIME_LIMIT
                )
            }
        statusView.text = getString(R.string.subscription_channel_line, channelLabel, status)
    }

    private fun startCheckout(plan: SubscriptionManager.Plan) {
        when (storeChannel) {
            StoreChannel.DIRECT -> startDirectCheckout(plan)
            StoreChannel.BAZAAR, StoreChannel.MYKET -> startStoreCheckout(plan)
        }
    }

    private fun startStoreCheckout(plan: SubscriptionManager.Plan) {
        if (!storeReady) {
            Toast.makeText(this, R.string.subscription_store_not_ready, Toast.LENGTH_LONG).show()
            return
        }
        val deviceId = PreferencesManager(this).getOrCreateDeviceId()
        val sku = when (plan) {
            SubscriptionManager.Plan.MONTHLY -> BillingCatalog.SKU_MONTHLY
            SubscriptionManager.Plan.YEARLY -> BillingCatalog.SKU_YEARLY
        }
        val onResult: (PurchaseResult) -> Unit = { result ->
            when (result) {
                is PurchaseResult.Success -> verifyAndGrantStorePurchase(result)
                is PurchaseResult.Cancelled -> Unit
                is PurchaseResult.Failed -> Toast.makeText(this, getString(R.string.subscription_failed, result.message), Toast.LENGTH_LONG).show()
            }
        }
        when (storeChannel) {
            StoreChannel.BAZAAR -> BazaarBillingHelper.purchase(this, sku, deviceId, onResult)
            StoreChannel.MYKET -> MyketBillingHelper.purchase(this, sku, deviceId, onResult)
            StoreChannel.DIRECT -> Unit
        }
    }

    private fun verifyAndGrantStorePurchase(result: PurchaseResult.Success) {
        val plan = when (result.sku) {
            BillingCatalog.SKU_MONTHLY -> SubscriptionManager.Plan.MONTHLY
            BillingCatalog.SKU_YEARLY -> SubscriptionManager.Plan.YEARLY
            else -> return
        }
        lifecycleScope.launch {
            val verified = SubscriptionManager.verifyStorePurchase(
                this@SubscriptionActivity, storeChannel, plan, result.purchaseToken
            )
            if (verified) {
                when (storeChannel) {
                    StoreChannel.BAZAAR -> BazaarBillingHelper.consume(result.purchaseToken) { }
                    StoreChannel.MYKET -> MyketBillingHelper.consume(result.purchaseToken) { }
                    StoreChannel.DIRECT -> Unit
                }
            }
            renderStatus(findViewById(R.id.statusText))
            Toast.makeText(
                this@SubscriptionActivity,
                if (verified) R.string.subscription_verified else R.string.subscription_held,
                Toast.LENGTH_LONG
            ).show()
        }
    }

    private fun startDirectCheckout(plan: SubscriptionManager.Plan) {
        lifecycleScope.launch {
            val paymentUrl = SubscriptionManager.requestPayment(this@SubscriptionActivity, plan)
            if (paymentUrl != null) {
                startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(paymentUrl)))
            } else {
                Toast.makeText(this@SubscriptionActivity, R.string.subscription_direct_unavailable, Toast.LENGTH_LONG).show()
            }
        }
    }
}
