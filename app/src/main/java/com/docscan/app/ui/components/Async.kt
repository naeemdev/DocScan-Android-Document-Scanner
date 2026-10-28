package com.docscan.app.ui.components

import kotlin.coroutines.cancellation.CancellationException

suspend fun <T> attempt(block: suspend () -> T): Result<T> = try {
    Result.success(block())
} catch (e: CancellationException) {
    throw e
} catch (e: Exception) {
    Result.failure(e)
}
