package com.mhss.app.util

import android.util.Patterns

object RegistrationValidator {
    const val MIN_PASSWORD_LENGTH = 6

    fun isValidEmail(email: String): Boolean {
        val trimmed = email.trim()
        return trimmed.isNotEmpty() && Patterns.EMAIL_ADDRESS.matcher(trimmed).matches()
    }

    fun isValidPassword(password: String): Boolean = password.length >= MIN_PASSWORD_LENGTH

    fun passwordsMatch(password: String, confirmation: String): Boolean = password == confirmation

    fun maskEmail(email: String): String {
        val trimmed = email.trim()
        val at = trimmed.indexOf('@')
        if (at <= 0) return "***"
        return trimmed.first() + "***" + trimmed.substring(at)
    }
}
