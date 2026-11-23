package com.ay.briefyou.brief.app

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException

/**
 * What the last sync did, for the hero card on Home: how far a running sync is (feeds done of
 * feeds to fetch; only some accounts know this) and why the last one failed. Kept in memory, so
 * it starts clean with the app.
 */
object SyncStatus {
    enum class Problem { None, Failed, SignIn }

    data class State(val problem: Problem = Problem.None, val done: Int = 0, val total: Int = 0)

    private val mutable = MutableStateFlow(State())
    val state: StateFlow<State> = mutable.asStateFlow()

    @Volatile private var signInError = false

    fun started() {
        signInError = false
        mutable.value = State()
    }

    fun total(count: Int) {
        mutable.value = mutable.value.copy(done = 0, total = count)
    }

    fun step() {
        val now = mutable.value
        mutable.value = now.copy(done = (now.done + 1).coerceAtMost(now.total))
    }

    /** An error the sync logged: a server answering 401/403 means the sign-in no longer works. */
    fun noteError(error: Throwable) {
        var e: Throwable? = error
        while (e != null) {
            val text = e.message.orEmpty()
            if (e.javaClass.simpleName.contains("HttpException") && (text.contains("401") || text.contains("403")) ||
                text.contains("401 ") || text.contains("403 ") || text.contains("Unauthorized", ignoreCase = true)
            ) {
                signInError = true
            }
            e = e.cause?.takeIf { it !== e }
        }
    }

    fun finished(ok: Boolean) {
        mutable.value = State(problem = if (ok) Problem.None else if (signInError) Problem.SignIn else Problem.Failed)
    }
}
