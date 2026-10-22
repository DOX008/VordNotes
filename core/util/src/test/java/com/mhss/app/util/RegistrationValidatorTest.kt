package com.mhss.app.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RegistrationValidatorTest {
    @Test
    fun passwordNeedsAtLeastSixCharacters() {
        assertFalse(RegistrationValidator.isValidPassword("12345"))
        assertTrue(RegistrationValidator.isValidPassword("123456"))
    }

    @Test
    fun passwordsMustMatch() {
        assertTrue(RegistrationValidator.passwordsMatch("abcdef", "abcdef"))
        assertFalse(RegistrationValidator.passwordsMatch("abcdef", "abcdeg"))
    }

    @Test
    fun blankEmailIsInvalid() {
        assertFalse(RegistrationValidator.isValidEmail("   "))
    }

    @Test
    fun emailIsMasked() {
        assertEquals("a***@example.com", RegistrationValidator.maskEmail(" alice@example.com "))
        assertEquals("***", RegistrationValidator.maskEmail("nope"))
    }
}
