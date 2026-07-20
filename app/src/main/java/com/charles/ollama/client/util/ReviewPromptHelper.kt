package com.charles.ollama.client.util

import android.app.Activity
import com.charles.ollama.client.data.preferences.ReviewPromptPreferences
import com.google.android.play.core.review.ReviewManagerFactory
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Wraps the Play In-App Review API. The Play Store decides whether the dialog
 * actually shows (it's quota-limited on Google's side), so this only decides
 * *when it's worth asking* — once, after a few successful replies.
 */
@Singleton
class ReviewPromptHelper @Inject constructor(
    private val preferences: ReviewPromptPreferences
) {
    /** Call after a chat reply completes successfully. Returns true the moment the threshold is crossed. */
    fun onSuccessfulReply(): Boolean {
        if (preferences.hasBeenPrompted()) return false
        val count = preferences.incrementSuccessfulReplies()
        return count == ReviewPromptPreferences.SUCCESS_THRESHOLD
    }

    fun requestReview(activity: Activity) {
        preferences.markPrompted()
        val manager = ReviewManagerFactory.create(activity)
        manager.requestReviewFlow().addOnCompleteListener { request ->
            if (request.isSuccessful) {
                manager.launchReviewFlow(activity, request.result)
            }
        }
    }
}
