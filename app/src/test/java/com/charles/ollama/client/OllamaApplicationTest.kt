package com.charles.ollama.client

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Regression coverage for the Crashlytics issue where the framework throws
 * `IllegalArgumentException("parameter must be a descendant of this view")`
 * from `ViewGroup.offsetRectBetweenParentAndChild` during
 * `ViewRootImpl.scrollToRectOrFocus`. The wrapped main looper in
 * [OllamaApplication] swallows that one specific stack and reports it as a
 * non-fatal — anything else must keep crashing.
 */
class OllamaApplicationTest {

    @Test
    fun `matches the descendant rect framework race`() {
        val ex = IllegalArgumentException("parameter must be a descendant of this view").apply {
            stackTrace = arrayOf(
                StackTraceElement("android.view.ViewGroup", "offsetRectBetweenParentAndChild", "ViewGroup.java", 6524),
                StackTraceElement("android.view.ViewGroup", "offsetDescendantRectToMyCoords", "ViewGroup.java", 6453),
                StackTraceElement("android.view.ViewRootImpl", "scrollToRectOrFocus", "ViewRootImpl.java", 5618),
            )
        }
        assertTrue(isSuppressibleFrameworkBug(ex))
    }

    @Test
    fun `does not match a different IllegalArgumentException`() {
        val ex = IllegalArgumentException("something else entirely")
        assertFalse(isSuppressibleFrameworkBug(ex))
    }

    @Test
    fun `does not match the right message but wrong stack`() {
        val ex = IllegalArgumentException("parameter must be a descendant of this view").apply {
            stackTrace = arrayOf(
                StackTraceElement("com.example.Foo", "bar", "Foo.kt", 1),
            )
        }
        assertFalse(isSuppressibleFrameworkBug(ex))
    }

    @Test
    fun `does not match other exception types`() {
        val ex = NullPointerException("parameter must be a descendant of this view")
        assertFalse(isSuppressibleFrameworkBug(ex))
    }

    @Test
    fun `does not match a null message`() {
        val ex = IllegalArgumentException()
        assertFalse(isSuppressibleFrameworkBug(ex))
    }
}
