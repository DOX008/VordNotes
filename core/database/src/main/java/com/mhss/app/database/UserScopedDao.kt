package com.mhss.app.database

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flatMapLatest
import java.lang.reflect.InvocationTargetException
import java.lang.reflect.Proxy

/**
 * Creates a DAO that resolves the real DAO for the *currently signed in* user on every call, so
 * that the DAO (and the repositories holding it) never stays bound to the user that happened to be
 * signed in when the singleton was first created. Methods returning [Flow] re-subscribe to the
 * new user's database whenever [userKey] changes.
 */
@OptIn(ExperimentalCoroutinesApi::class)
internal fun <T : Any> userScopedDao(
    type: Class<T>,
    userKey: StateFlow<String>,
    resolve: () -> T
): T {
    val handler = java.lang.reflect.InvocationHandler { proxy, method, args ->
        when (method.name) {
            "toString" -> if (method.parameterCount == 0) return@InvocationHandler "UserScopedDao(${type.simpleName})"
            "hashCode" -> if (method.parameterCount == 0) return@InvocationHandler System.identityHashCode(proxy)
            "equals" -> if (method.parameterCount == 1) return@InvocationHandler proxy === args?.get(0)
        }
        val callArgs = args ?: emptyArray()
        fun invokeOnCurrent(): Any? = try {
            method.invoke(resolve(), *callArgs)
        } catch (e: InvocationTargetException) {
            throw e.targetException
        }
        if (Flow::class.java.isAssignableFrom(method.returnType)) {
            userKey.flatMapLatest {
                @Suppress("UNCHECKED_CAST")
                invokeOnCurrent() as Flow<Any?>
            }
        } else {
            invokeOnCurrent()
        }
    }
    @Suppress("UNCHECKED_CAST")
    return Proxy.newProxyInstance(type.classLoader, arrayOf(type), handler) as T
}
