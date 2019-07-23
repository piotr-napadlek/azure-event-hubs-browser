package io.napadlek.eventhubbrowser

import java.util.concurrent.Callable
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit


fun <T, R> Collection<T>.pmap(
        numThreads: Int = Runtime.getRuntime().availableProcessors() - 2,
        exec: ExecutorService = Executors.newFixedThreadPool(numThreads),
        transform: (T) -> R): List<R> {

    val futures = this.map { exec.submit(Callable { transform(it) }) }
    exec.shutdown()
    exec.awaitTermination(30, TimeUnit.SECONDS)
    return futures.map { it.get() }
}

fun hubId(namespace: String, name: String) = "$namespace/$name"
