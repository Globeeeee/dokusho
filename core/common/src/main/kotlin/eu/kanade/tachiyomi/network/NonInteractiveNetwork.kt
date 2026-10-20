package eu.kanade.tachiyomi.network

import kotlinx.coroutines.ThreadContextElement
import okhttp3.Call
import java.util.Collections
import java.util.WeakHashMap
import kotlin.coroutines.CoroutineContext

/**
 * Coroutine context for work no one is watching, like downloads. Calls made under it never open a Cloudflare check
 * for the user to solve and wait on it: they fail with the Cloudflare message, so the work can be retried later.
 *
 * Calls run on the caller's thread with [Call.execute] and are registered by [Call.await] when enqueued, since
 * interceptors of an enqueued call run on OkHttp's threads.
 */
object NonInteractiveNetwork : ThreadContextElement<Boolean>, CoroutineContext.Key<NonInteractiveNetwork> {

    override val key: CoroutineContext.Key<*> get() = this

    private val activeOnThread = ThreadLocal<Boolean>()

    private val calls: MutableSet<Call> = Collections.synchronizedSet(Collections.newSetFromMap(WeakHashMap()))

    override fun updateThreadContext(context: CoroutineContext): Boolean {
        val previous = activeOnThread.get() == true
        activeOnThread.set(true)
        return previous
    }

    override fun restoreThreadContext(context: CoroutineContext, oldState: Boolean) {
        activeOnThread.set(oldState)
    }

    /** Remembers [call] when it's made under this context, for interceptors that run on another thread. */
    internal fun register(call: Call) {
        if (activeOnThread.get() == true) calls.add(call)
    }

    /** True when [call] was made under this context, so no one can solve a check for it. */
    fun isNonInteractive(call: Call): Boolean = activeOnThread.get() == true || call in calls
}
