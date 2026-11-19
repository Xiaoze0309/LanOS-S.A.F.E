package com.lanos.safe.data

import kotlinx.coroutines.CancellationException

/**
 * Compose 的 LaunchedEffect 在离开组合时会取消协程，此时网络调用抛 CancellationException。
 * 如果被 runCatching 当成普通错误展示，就会出现 “The coroutine scope left the composition” 这种假错误。
 * 所有 onFailure 里先调它，把取消原样抛出去。
 */
fun Throwable.rethrowIfCancelled() {
    if (this is CancellationException) throw this
}
