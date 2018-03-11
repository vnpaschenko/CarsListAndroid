package com.example.ipaschenko.carslist.camera

import android.util.Size
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
    private var mBuffer: ByteArray? = null

    /** Occur ownership on busy flag **/
    fun take(): Boolean = mBusy.compareAndSet(false, true)

    /** Release ownership on busy flag **/
    fun release() = mBusy.set(false)

    /** Process data  **/
    fun process(buffer: ByteArray, imageSize: Size, imageFormat: Int, frameRotation: Int) {

        EXECUTOR.execute {
            try {
                if (!cancellable.isCancelled) {
                    listener.onCameraPreviewObtained(buffer, imageSize, imageFormat, frameRotation,
                            cancellable)
                }
            } finally {
                release()
            }
        }
    }

    /** Copy the data and process it. dataConsumed will be called when it is copied and can be
     * re-used **/
    fun processCopy(buffer: ByteArray, imageSize: Size, imageFormat: Int, frameRotation: Int,
                dataConsumed:(buffer: ByteArray) -> Unit) {

        EXECUTOR.execute {
            try {
                if (!cancellable.isCancelled) {

                    // Copy the data and notify it is consumed
                    val size = buffer.size
                    if (mBuffer?.size != size) {
                        mBuffer = ByteArray(size)
                    }
                    System.arraycopy(buffer, 0, mBuffer, 0, size)
                    dataConsumed(buffer)

                    // Perform processing
                    listener.onCameraPreviewObtained(mBuffer!!, imageSize, imageFormat,
                            frameRotation, cancellable)
                } else {
                    dataConsumed(buffer)
                }
            } finally {
                release()
            }
        }
    }
}
