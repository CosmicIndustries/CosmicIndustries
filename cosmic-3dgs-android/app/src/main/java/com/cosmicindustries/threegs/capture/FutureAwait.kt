package com.cosmicindustries.threegs.capture

import com.google.common.util.concurrent.ListenableFuture
import kotlinx.coroutines.suspendCancellableCoroutine
import java.util.concurrent.ExecutionException
import java.util.concurrent.Executor
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/** Suspends until a CameraX ListenableFuture finishes. Avoids adding concurrent-futures-ktx. */
internal suspend fun <T> ListenableFuture<T>.awaitResult(executor: Executor): T =
    suspendCancellableCoroutine { cont ->
        addListener({
            try {
                cont.resume(get())
            } catch (e: ExecutionException) {
                cont.resumeWithException(e.cause ?: e)
            } catch (e: Throwable) {
                cont.resumeWithException(e)
            }
        }, executor)
        cont.invokeOnCancellation { cancel(false) }
    }
