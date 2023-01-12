package ru.herobrine1st.matrix.bridge.database

import app.cash.sqldelight.driver.r2dbc.R2dbcDriver
import io.r2dbc.spi.Connection
import kotlinx.coroutines.Job
import kotlinx.coroutines.suspendCancellableCoroutine
import org.reactivestreams.Publisher
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

fun Connection.toDriver() = R2dbcDriver(this) {
    it?.printStackTrace()
}

suspend fun <T> Publisher<T>.awaitFirst(): T = suspendCancellableCoroutine { continuation ->
    val subscription = Mono.from(this)
        .doOnSuccess {
            it?.let { continuation.resume(it) }
                ?: continuation.resumeWithException(NoSuchElementException("Expected at least one item"))
        }
        .doOnError {
            continuation.resumeWithException(it)
        }
        .subscribe()
    continuation.invokeOnCancellation {
        subscription.dispose()
    }
}

suspend fun <T> Publisher<T>.awaitCompletion() = suspendCancellableCoroutine { continuation ->
    val subscription = Flux.from(this)
        .doOnComplete {
            continuation.resume(Unit)
        }
        .doOnError {
            continuation.resumeWithException(it)
        }
        .subscribe()
    continuation.invokeOnCancellation {
        subscription.dispose()
    }
}

/**
 * Implementation of awaitCompletion where only final line is suspending, thus completing Publisher in any circumstances.
 */
suspend fun <T> Publisher<T>.completeAnyway() {
    val job = Job()
    Flux.from(this).doOnComplete { job.complete() }
        .doOnError { job.completeExceptionally(it) }
        .subscribe()
    job.join()
}

fun <T> Publisher<T>.toFlux(): Flux<T> = Flux.from(this)