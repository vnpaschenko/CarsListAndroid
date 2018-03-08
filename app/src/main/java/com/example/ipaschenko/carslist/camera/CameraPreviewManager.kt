package com.example.ipaschenko.carslist.camera

import android.app.Activity
import android.support.annotation.MainThread
import android.support.annotation.WorkerThread
import android.util.Size
import android.view.TextureView
import com.example.ipaschenko.carslist.utils.Cancellable


/**
 *
 */
interface CameraPreviewManager {
    interface CameraPreviewManagerEventListener {

        @WorkerThread
        fun onCameraPreviewObtained(imageData: ByteArray, width: Int, height: Int, imageFormat: Int,
                    cancellable: Cancellable)

        @MainThread
        fun onCameraPreviewError(error: Throwable)
    }

    class CameraNotFoundException: Exception() {}

    companion object {

        fun newPreviewManager(activity: Activity, textureView: TextureView,
                listener: CameraPreviewManagerEventListener): CameraPreviewManager {
            return CameraV2PreviewManager(activity, textureView, listener)
        }
    }

    fun start()
    fun stop()

    val flashSupported: Boolean
    fun toggleFlash()

    val previewSize: Size

}
