package com.mhss.app.mybrain.presentation.auth

import com.google.firebase.auth.FirebaseAuthException
import com.mhss.app.mybrain.BuildConfig
import com.mhss.app.util.FileLogger

object AuthDebug {
    const val TAG = "AUTH_DEBUG"

    fun d(msg: String) {
        if (BuildConfig.DEBUG) FileLogger.d(TAG, msg)
    }

    fun w(msg: String) {
        if (BuildConfig.DEBUG) FileLogger.w(TAG, msg)
    }

    fun e(msg: String, t: Throwable? = null) {
        if (BuildConfig.DEBUG) FileLogger.e(TAG, msg, t)
    }

    fun maskEmail(email: String?): String {
        if (email.isNullOrBlank()) return "<blank>"
        val at = email.indexOf('@')
        if (at <= 0) return "***"
        return "${email[0]}***${email.substring(at)}"
    }

    fun describe(t: Throwable): String {
        val code = (t as? FirebaseAuthException)?.errorCode
        return "class=${t.javaClass.name}, message=${t.localizedMessage}, errorCode=${code ?: "n/a"}"
    }
}
