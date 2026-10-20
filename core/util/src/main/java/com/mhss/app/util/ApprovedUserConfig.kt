package com.mhss.app.util

object ApprovedUserConfig {
    private const val ADMIN_EMAIL = "norrmann035@pm.me"

    val approvedEmails: Set<String> = setOf(ADMIN_EMAIL)

    fun isApprovedEmail(email: String): Boolean {
        val normalized = email.trim()
        return normalized.equals(ADMIN_EMAIL, ignoreCase = true)
    }
}
