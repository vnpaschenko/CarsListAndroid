package com.example.ipaschenko.carslist.camera

import android.app.Activity
import android.content.Context
import android.graphics.ImageFormat
import android.support.annotation.MainThread
import android.support.annotation.WorkerThread
import android.util.Size
import android.view.TextureView
import com.example.ipaschenko.carslist.utils.Cancellable
import java.io.IOException

/**
 * Captures preview from the camera and notifies event listener
 */

class CameraPreviewSettings(val imageFormat: Int, val maxPreviewSize: Int) {}

interface CameraPreviewManager {

    interface CameraPreviewManagerEventListener {
        /**
         * Called when preview image is available
         * @param imageData Raw image bytes
         * @param width image width
         * @param height image height
         * @param imageFormat Image format, see {@link android.graphics.ImageFormat}
         * @param cancellable Can be used to determine that the preview is cancelled
         */
        @WorkerThread
        fun onCameraPreviewObtained(imageData: ByteArray, imageSize: Size, imageFormat: Int,
                    frameRotation: Int, cancellable: Cancellable)

        /**
         * Called when the error occurs
         */
        @MainThread
        fun onCameraPreviewError(error: Throwable)
    }

    /** Exceptions **/
    open class CameraException(message: String?): Exception(message) {}
    class CameraNotFoundException(): CameraException("Back camera is not found") {}
    class CameraSettingsException(message: String?): CameraException(message) {}

    companion object {

        /**
         * Preview manager factory method
         */
        fun newPreviewManager(settings: CameraPreviewSettings): CameraPreviewManager {

            return CameraV1PreviewManager(settings)
        }
    }


    /** Start preview **/
    @Throws(CameraException::class, IOException::class)
    fun start(context: Context, turnFlash: Boolean, textureView: TextureView,
              listener: CameraPreviewManager.CameraPreviewManagerEventListener,
              parametersSelected:(previewSize: Size, flashStatus: Boolean?) -> Unit)

    /** Stop preview **/
    fun stop()

    /** Turn flash on/off **/
    fun toggleFlash()
}
