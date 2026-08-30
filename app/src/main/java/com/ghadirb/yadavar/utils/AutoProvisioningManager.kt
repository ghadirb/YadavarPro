package com.ghadirb.yadavar.utils

import android.content.Context
import android.util.Log
import com.ghadirb.yadavar.models.APIKey

/**
 * [autoProvision] used to download an "encrypted" file of shared API keys from a public
 * GitHub Gist, decrypt it with a password hardcoded in the app ("12345"), and store the
 * plaintext provider key (GapGPT) directly on the device. That pattern - fetch a payload
 * from an untrusted host, decrypt with a secret baked into the APK, use the result to
 * drive the app's behavior - is exactly the behavioral signature malware/PHA scanners
 * (including Google Play Protect) are built to catch, regardless of what the payload
 * actually contains. It also meant anyone who decompiled the APK could recover the shared
 * GapGPT key and use it as their own, unmetered.
 *
 * Shared/free-tier AI access no longer needs any locally-stored key at all: requests for
 * users without their own personal key go straight to [AIBackendClient], which calls a
 * Google Apps Script Web App that holds the real provider key server-side
 * (PropertiesService "Script Properties", never shipped in the APK) - see
 * server/apps-script/Code.gs. This function is kept only so existing call sites don't need
 * to change; it now does nothing and always reports "no local keys to provision", which is
 * correct.
 */
object AutoProvisioningManager {

    private const val TAG = "AutoProvisioning"

    suspend fun autoProvision(context: Context): Result<List<APIKey>> {
        Log.d(TAG, "Local key auto-provisioning is disabled; shared AI access goes through AIBackendClient instead.")
        return Result.success(emptyList())
    }
}
