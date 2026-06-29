package com.charles.ollama.client.ads

import android.app.Activity
import android.content.Context
import android.util.Log
import com.google.android.ump.ConsentInformation
import com.google.android.ump.ConsentRequestParameters
import com.google.android.ump.UserMessagingPlatform
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Wraps Google's User Messaging Platform (UMP) so the app obtains GDPR consent
 * before serving ads to users in the EEA/UK/Switzerland — a Google Play and
 * AdMob requirement. Ads must not be requested until [canRequestAds] is true.
 *
 * Outside regulated regions UMP reports "not required" and [canRequestAds]
 * becomes true immediately, so this adds no friction for most users.
 */
@Singleton
class AdConsentManager @Inject constructor(@ApplicationContext context: Context) {

    private val consentInformation: ConsentInformation =
        UserMessagingPlatform.getConsentInformation(context)

    /** True once consent is resolved (granted, not required, or already stored). */
    val canRequestAds: Boolean
        get() = consentInformation.canRequestAds()

    /**
     * Refresh consent state and, if a form is required, present it.
     *
     * [onResult] receives `allowed`:
     *  - `true`  — the app may proceed and ads may load (consent granted, not
     *    required for this region, or the consent service is unavailable so we
     *    fail open rather than brick the app).
     *  - `false` — a consent form was shown and the user declined; the caller
     *    should keep the app usable and skip ad requests.
     *
     * UMP persists the user's choice, so a consenting user is only asked once.
     */
    fun ensureConsent(activity: Activity, onResult: (allowed: Boolean) -> Unit) {
        val params = ConsentRequestParameters.Builder().build()
        consentInformation.requestConsentInfoUpdate(
            activity,
            params,
            {
                UserMessagingPlatform.loadAndShowConsentFormIfRequired(activity) { formError ->
                    if (formError != null) {
                        Log.w(TAG, "Consent form error: ${formError.message}")
                    }
                    // canRequestAds() is true when consent was granted OR isn't
                    // required for this user; false means a form was shown and
                    // the user declined, so the app continues without ads.
                    onResult(consentInformation.canRequestAds())
                }
            },
            { requestError ->
                // The update can fail for reasons unrelated to the user's choice —
                // most commonly the publisher hasn't configured a GDPR form in
                // AdMob yet, or a transient network error. There's no form to
                // gate on, so fail OPEN: never brick the app or disable all ads
                // because of a setup/network gap.
                Log.w(TAG, "Consent info update failed; proceeding (fail-open): ${requestError.message}")
                onResult(true)
            }
        )
    }

    /**
     * Re-show the consent UI after a decline (drives the consent wall's retry).
     * Prefers the privacy-options form (lets the user change a prior choice);
     * falls back to a fresh consent request when that isn't available.
     */
    fun rePrompt(activity: Activity, onResult: (allowed: Boolean) -> Unit) {
        if (isPrivacyOptionsRequired) {
            UserMessagingPlatform.showPrivacyOptionsForm(activity) { error ->
                if (error != null) Log.w(TAG, "Privacy options error: ${error.message}")
                onResult(consentInformation.canRequestAds())
            }
        } else {
            ensureConsent(activity, onResult)
        }
    }

    /**
     * Show the privacy options form so users can change their choice later.
     * Required by UMP when a privacy-options entry point is needed.
     */
    fun showPrivacyOptions(activity: Activity, onComplete: (String?) -> Unit) {
        UserMessagingPlatform.showPrivacyOptionsForm(activity) { error ->
            onComplete(error?.message)
        }
    }

    val isPrivacyOptionsRequired: Boolean
        get() = consentInformation.privacyOptionsRequirementStatus ==
            ConsentInformation.PrivacyOptionsRequirementStatus.REQUIRED

    companion object {
        private const val TAG = "AdConsentManager"
    }
}
