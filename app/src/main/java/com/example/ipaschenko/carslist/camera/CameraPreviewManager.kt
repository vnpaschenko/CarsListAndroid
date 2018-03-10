package com.example.ipaschenko.carslist.camera

import android.app.Activity
import android.graphics.ImageFormat
import android.support.annotation.MainThread
import android.support.annotation.WorkerThread
import android.util.Size
import android.view.TextureView
import com.example.ipaschenko.carslist.utils.Cancellable

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
        fun onCameraPreviewObtained(imageData: ByteArray, width: Int, height: Int, imageFormat: Int,
                    cancellable: Cancellable)

        /**
         * Called when the error occurs
         */
        @MainThread
        fun onCameraPreviewError(error: Throwable)
    }

    class CameraNotFoundException: Exception() {}

    companion object {

        /**
         * Preview manager factory method
         */
        fun newPreviewManager(settings: CameraPreviewSettings, activity: Activity,
                textureView: TextureView, listener: CameraPreviewManagerEventListener):
                CameraPreviewManager {

            return CameraV2PreviewManager(settings, activity, textureView, listener)
        }
    }

    /** Start preview **/
    fun start()

    /** Stop preview **/
    fun stop()

    /** Indicates that flash is supported **/
    val flashSupported: Boolean

    /** Turn flash on/off **/
    fun toggleFlash()

    /** Preview size in px **/
    val previewSize: Size

    /** Camera orientation in degrees **/
    val cameraOrientation: Int
}
