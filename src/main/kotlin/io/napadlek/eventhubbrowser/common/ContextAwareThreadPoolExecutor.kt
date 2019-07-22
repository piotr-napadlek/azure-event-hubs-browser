package io.napadlek.eventhubbrowser.common

import org.springframework.web.context.request.RequestAttributes
import org.springframework.web.context.request.RequestContextHolder
import java.util.concurrent.*


internal class ContextAwareThreadPoolExecutor(coreSize: Int) : ThreadPoolExecutor(coreSize, coreSize, 0L, TimeUnit.MILLISECONDS, LinkedBlockingQueue()) {
    override fun <T> submit(task: Callable<T>): Future<T> {
        return super.submit<T>(ContextAwareCallable(task, RequestContextHolder.currentRequestAttributes()))
    }
}

private class ContextAwareCallable<T>(private val task: Callable<T>, private val context: RequestAttributes) : Callable<T> {

    @Throws(Exception::class)
    override fun call(): T {
        RequestContextHolder.setRequestAttributes(context)

        try {
            return task.call()
        } finally {
            RequestContextHolder.resetRequestAttributes()
        }
    }
}