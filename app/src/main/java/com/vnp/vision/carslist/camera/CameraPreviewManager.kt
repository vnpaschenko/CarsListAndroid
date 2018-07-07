package com.vnp.vision.carslist.camera

import android.content.Context
import android.support.annotation.MainThread
import android.support.annotation.WorkerThread
import android.util.Size
import android.view.TextureView
import com.vnp.vision.carslist.utils.Cancellable
import java.io.IOException

/**
 * Captures preview from the camera and notifies event listener
 */

class CameraPreviewSettings(val imageFormat: Int, val maxPreviewSize: Int,
                            val requestedFps: Int = 15) {}

interface CameraPreviewManager {

    interface CameraPreviewManagerEventListener {
        /**
         * Called when preview image is available
         * @param imageData Raw image bytes
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
    class PreviewFormatNotSupportedException(): CameraException("Preview format is not supported"){}

    companion object {

        /**
         * Preview manager factory method
         */
        fun newPreviewManager(context: Context, settings: CameraPreviewSettings): CameraPreviewManager {
            var result: CameraPreviewManager? = null
            try {
                result = CameraV2PreviewManager(context, settings)
            } catch (e: CameraException) {
                // No-op
            }

            if (result == null) {
                result = CameraV1PreviewManager(settings)
            }

            return result
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
