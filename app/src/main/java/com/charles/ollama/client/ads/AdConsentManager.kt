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
     * Refresh consent state and, if a form is required, present it. [onResolved]
     * is invoked (on the main thread) once ads may be requested — or immediately
     * if consent is already satisfied. It is only called when [canRequestAds].
     */
    fun ensureConsent(activity: Activity, onResolved: () -> Unit) {
        val params = ConsentRequestParameters.Builder().build()
        consentInformation.requestConsentInfoUpdate(
            activity,
            params,
            {
                UserMessagingPlatform.loadAndShowConsentFormIfRequired(activity) { formError ->
                    if (formError != null) {
                        Log.w(TAG, "Consent form error: ${formError.message}")
                    }
                    if (consentInformation.canRequestAds()) onResolved()
                }
            },
            { requestError ->
                Log.w(TAG, "Consent info update failed: ${requestError.message}")
                // Fail-safe: if consent state can't be fetched, fall back to
                // whatever is already stored (defaults to no ads in EEA).
                if (consentInformation.canRequestAds()) onResolved()
            }
        )
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
