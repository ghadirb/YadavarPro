package com.ghadirb.yadavar.billing

import com.ghadirb.yadavar.BuildConfig

/**
 * Which channel this APK was built for. Bazaar and Myket require different
 * billing-service bindings and public keys, so official builds use separate
 * Gradle flavors rather than guessing the installer at runtime.
 */
enum class StoreChannel(val apiValue: String) {
    BAZAAR("bazaar"),
    MYKET("myket"),
    DIRECT("direct");

    companion object {
        fun current(): StoreChannel = when (BuildConfig.STORE_CHANNEL) {
            BAZAAR.apiValue -> BAZAAR
            MYKET.apiValue -> MYKET
            else -> DIRECT
        }
    }
}
