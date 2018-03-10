package com.example.ipaschenko.carslist.camera

import com.example.ipaschenko.carslist.utils.Cancellable
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

/**
 *
 */
class FrameProcessor(private val cancellable: Cancellable,
                     private val listener: CameraPreviewManager.CameraPreviewManagerEventListener) {

    companion object {
        private val EXECUTOR = Executors.newSingleThreadExecutor()
    }

    private val mBusy = AtomicBoolean(false)

    fun take(): Boolean = mBusy.compareAndSet(false, true)

    fun release() = mBusy.set(false)

    fun process(buffer: ByteArray, width: Int, height: Int, imageFormat: Int) {
        EXECUTOR.execute {
            try {
                listener.onCameraPreviewObtained(buffer, width, height, imageFormat, cancellable)
            } finally {
                release()
            }
        }
    }
}
