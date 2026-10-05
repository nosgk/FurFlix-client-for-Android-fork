package com.furflix.app.translate

import com.furflix.app.translate.data.TranslationErrors
import com.furflix.app.translate.data.TranslationException
import com.furflix.app.translate.data.isRetryable
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RetryPolicyTest {

    @Test
    fun `config errors are not retryable`() {
        assertFalse(TranslationException(TranslationErrors.NO_KEY).isRetryable)
        assertFalse(TranslationException(TranslationErrors.INVALID_KEY).isRetryable)
        assertFalse(TranslationException(TranslationErrors.CANCELLED).isRetryable)
        assertFalse(TranslationException(TranslationErrors.NOTHING).isRetryable)
    }

    @Test
    fun `4xx is deterministic and not retried except 408 and 429`() {
        assertFalse(TranslationException(TranslationErrors.serverError(400), code = 400).isRetryable)
        assertFalse(TranslationException(TranslationErrors.serverError(404), code = 404).isRetryable)
        assertTrue(TranslationException(TranslationErrors.serverError(408), code = 408).isRetryable)
        assertTrue(TranslationException(TranslationErrors.RATE_LIMITED, code = 429).isRetryable)
    }

    @Test
    fun `5xx timeouts and network errors are retryable`() {
        assertTrue(TranslationException(TranslationErrors.serverError(500), code = 500).isRetryable)
        assertTrue(TranslationException(TranslationErrors.serverError(503), code = 503).isRetryable)
        assertTrue(TranslationException(TranslationErrors.TIMEOUT).isRetryable)
        assertTrue(TranslationException(TranslationErrors.CONNECTION_FAILED).isRetryable)
        assertTrue(TranslationException(TranslationErrors.NETWORK).isRetryable)
        assertTrue(TranslationException(TranslationErrors.BAD_FORMAT).isRetryable)
        assertTrue(TranslationException(TranslationErrors.EMPTY).isRetryable)
    }
}
