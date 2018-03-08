package com.example.ipaschenko.carslist.camera

import android.os.AsyncTask
import com.example.ipaschenko.carslist.utils.Cancellable
import java.util.concurrent.atomic.AtomicBoolean

/**
 *
 */
class FrameProcessor(private val cancellable: Cancellable,
                     private val listener: CameraPreviewManager.CameraPreviewManagerEventListener) {

    private val mBusy = AtomicBoolean(false)
    private val mExecutor = AsyncTask.THREAD_POOL_EXECUTOR

    fun take(): Boolean = mBusy.compareAndSet(false, true)

    fun release() = mBusy.set(false)

    fun process(buffer: ByteArray, width: Int, height: Int, imageFormat: Int) {
        mExecutor.execute {
            try {
                listener.onCameraPreviewObtained(buffer, width, height, imageFormat, cancellable)
            } finally {
                release()
            }
        }
    }
}
