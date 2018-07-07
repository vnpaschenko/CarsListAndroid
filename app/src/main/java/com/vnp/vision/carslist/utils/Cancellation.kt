package com.vnp.vision.carslist.utils

import java.util.concurrent.atomic.AtomicBoolean

/**
 *
 */
interface Cancellable {
    val isCancelled: Boolean
}

class CancellationToken: Cancellable {
    private val mCancelled = AtomicBoolean(false)
    override val isCancelled: Boolean
        get() = mCancelled.get()

    fun cancel() = mCancelled.set(true)
}

fun Cancellable?.canContinue(): Boolean = this == null || !this.isCancelled
