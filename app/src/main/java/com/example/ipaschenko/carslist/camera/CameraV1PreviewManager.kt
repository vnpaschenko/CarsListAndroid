package com.example.ipaschenko.carslist.camera

import android.content.Context
import android.graphics.ImageFormat
import android.hardware.Camera
import android.util.Size
import android.view.Surface
import android.view.TextureView
import android.view.WindowManager
import com.example.ipaschenko.carslist.utils.Cancellable
import com.example.ipaschenko.carslist.utils.CancellationToken
import java.io.IOException

/**
 * CameraPreviewManager implementation based on camera v1 api
 * In spite Camera v1 is deprecated, it can use ImageFormat.NV21 required for frames processing
 * while Camera v2 fails with it
 */
@Suppress("DEPRECATION")
class CameraV1PreviewManager(settings: CameraPreviewSettings): CameraPreviewManager {

    private val mSettings = settings
    private var mCamera: Camera? = null
    private val mCameraId: Int
    private var mCancellationToken: CancellationToken? = null

    init {
        mCameraId = getIdForRequestedCamera(Camera.CameraInfo.CAMERA_FACING_BACK)
        if (mCameraId == -1) {
            throw CameraPreviewManager.CameraNotFoundException()
        }
    }

    @Throws(CameraPreviewManager.CameraException::class, IOException::class)
    override fun start(context: Context, turnFlash: Boolean, textureView: TextureView,
            listener: CameraPreviewManager.CameraPreviewManagerEventListener,
            parametersSelected:(previewSize: Size, flashStatus: Boolean?) -> Unit) {

        if (mCamera != null) {
            // We already started
            return
        }

        try {
            val camera = Camera.open(mCameraId)
            mCamera = camera

            val parameters = camera.parameters

            // Deal with preview format
            if (!parameters.supportedPreviewFormats.contains(mSettings.imageFormat)) {
                throw CameraPreviewManager.CameraSettingsException("Unsupported preview format")
            }
            parameters.previewFormat = mSettings.imageFormat

            // Deal with preview size
            val selectedPreviewSize = selectPreviewSize(parameters)
                    ?: throw CameraPreviewManager.CameraSettingsException("Can't select preview size")
            parameters.setPreviewSize(selectedPreviewSize.width, selectedPreviewSize.height)

            // Deal with fps range
            val fpsRange = selectPreviewFpsRange(parameters, mSettings.requestedFps)
                    ?: throw CameraPreviewManager.CameraSettingsException("Can't select fps range")

            parameters.setPreviewFpsRange(fpsRange[Camera.Parameters.PREVIEW_FPS_MIN_INDEX],
                    fpsRange[Camera.Parameters.PREVIEW_FPS_MAX_INDEX])

            // Deal with focus
            if (parameters.supportedFocusModes?.contains(
                    Camera.Parameters.FOCUS_MODE_CONTINUOUS_PICTURE) == true) {
                parameters.focusMode = Camera.Parameters.FOCUS_MODE_CONTINUOUS_PICTURE
            }

            // Deal with flash
            val flashSupported = parameters.supportedFlashModes?.contains(
                    Camera.Parameters.FLASH_MODE_TORCH) == true

            if (turnFlash && flashSupported) {
                parameters.flashMode = Camera.Parameters.FLASH_MODE_TORCH
            }

            // Deal with rotation
            val frameRotation = applyRotation(context, camera, parameters)

            // Apply parameters and notify client
            camera.parameters = parameters

            // Notify
            parametersSelected(selectedPreviewSize, if (flashSupported) turnFlash else null)

            mCancellationToken = CancellationToken()

            // Setup preview callback and buffers
            val callback = CameraPreviewCallback(listener, selectedPreviewSize, mSettings.imageFormat,
                    frameRotation, mCancellationToken!!)
            camera.setPreviewCallbackWithBuffer(callback)

            // The only thing we do in background before consumption is copying data, so two buffers
            // should be enough
            camera.addCallbackBuffer(createPreviewBuffer(selectedPreviewSize))
            camera.addCallbackBuffer(createPreviewBuffer(selectedPreviewSize))

            // Start preview
            camera.setPreviewTexture(textureView.surfaceTexture)

            camera.startPreview()

        } catch (e: Throwable) {
            stop()
            throw e
        }
    }

    override fun stop() {
        mCancellationToken?.cancel()
        if (mCamera != null) {
            mCamera!!.stopPreview()
            mCamera!!.setPreviewCallbackWithBuffer(null)
            mCamera!!.setPreviewTexture(null)
            mCamera!!.release()
            mCamera = null
        }
    }


    override fun toggleFlash() {
        if (mCamera != null) {
            val parameters = mCamera!!.parameters
            if (parameters.supportedFlashModes?.contains(Camera.Parameters.FLASH_MODE_TORCH) == true) {

                if (Camera.Parameters.FLASH_MODE_TORCH.equals(parameters.flashMode)) {
                    parameters.flashMode = Camera.Parameters.FLASH_MODE_OFF
                } else {
                    parameters.flashMode = Camera.Parameters.FLASH_MODE_TORCH
                }

                mCamera!!.parameters = parameters
            }
        }
    }

    private fun getIdForRequestedCamera(facing: Int): Int {
        val cameraInfo = Camera.CameraInfo()
        for (i in 0 until Camera.getNumberOfCameras()) {
            Camera.getCameraInfo(i, cameraInfo)
            if (cameraInfo.facing == facing) {
                return i
            }
        }
        return -1
    }

    private fun selectPreviewSize(parameters: Camera.Parameters): Size? {
        val supportedPreviewSizes = parameters.supportedPreviewSizes
        supportedPreviewSizes ?: return null

        val cameraSize = supportedPreviewSizes.sortedByDescending { it.width * it.height }.first {
            it.width * it.height <= mSettings.maxPreviewSize }

        return if (cameraSize != null) Size(cameraSize.width, cameraSize.height) else null

    }

    private fun selectPreviewFpsRange(parameters: Camera.Parameters, desiredPreviewFps: Int): IntArray? {
        // The camera API uses integers scaled by a factor of 1000 instead of floating-point frame
        // rates.
        val desiredPreviewFpsScaled = desiredPreviewFps * 1000

        // The method for selecting the best range is to minimize the sum of the differences between
        // the desired value and the upper and lower bounds of the range.  This may select a range
        // that the desired value is outside of, but this is often preferred.  For example, if the
        // desired frame rate is 29.97, the range (30, 30) is probably more desirable than the
        // range (15, 30).
        var selectedFpsRange: IntArray? = null
        var minDiff = Integer.MAX_VALUE

        val previewFpsRangeList = parameters.supportedPreviewFpsRange
        for (range in previewFpsRangeList) {
            val deltaMin = desiredPreviewFpsScaled - range[Camera.Parameters.PREVIEW_FPS_MIN_INDEX]
            val deltaMax = desiredPreviewFpsScaled - range[Camera.Parameters.PREVIEW_FPS_MAX_INDEX]
            val diff = Math.abs(deltaMin) + Math.abs(deltaMax)
            if (diff < minDiff) {
                selectedFpsRange = range
                minDiff = diff
            }
        }

        return selectedFpsRange
    }

    private fun applyRotation(context: Context, camera: Camera, parameters: Camera.Parameters): Int {
        // Rotate preview depend on device orientation

        val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
        var degrees = 0
        val rotation = windowManager.defaultDisplay.rotation
        when (rotation) {
            Surface.ROTATION_0 -> degrees = 0
            Surface.ROTATION_90 -> degrees = 90
            Surface.ROTATION_180 -> degrees = 180
            Surface.ROTATION_270 -> degrees = 270
        }

        val cameraInfo = Camera.CameraInfo()
        Camera.getCameraInfo(mCameraId, cameraInfo)

        val angle: Int
        val displayAngle: Int
        if (cameraInfo.facing == Camera.CameraInfo.CAMERA_FACING_FRONT) {
            angle = (cameraInfo.orientation + degrees) % 360
            displayAngle = 360 - angle // compensate for it being mirrored
        } else {  // back-facing
            angle = (cameraInfo.orientation - degrees + 360) % 360
            displayAngle = angle
        }

        camera.setDisplayOrientation(displayAngle)
        parameters.setRotation(angle)

        // This corresponds to the rotation constants in {@link Frame}.
        return angle / 90
    }

    private fun createPreviewBuffer(previewSize: Size): ByteArray {
        val bitsPerPixel = ImageFormat.getBitsPerPixel(mSettings.imageFormat)

        val sizeInBits = (previewSize.height * previewSize.width * bitsPerPixel).toLong()
        val bufferSize = Math.ceil(sizeInBits / 8.0).toInt() + 1

        return ByteArray(bufferSize)
    }

    // Camera.PreviewCallback implementation
    private class CameraPreviewCallback(
            listener: CameraPreviewManager.CameraPreviewManagerEventListener,
            private val previewSize: Size, private val imageFormat: Int,
            private val frameRotation: Int, private val cancellable: Cancellable):
            Camera.PreviewCallback {

        private val mFrameProcessor = FrameProcessor(cancellable, listener)

        override fun onPreviewFrame(data: ByteArray, camera: Camera) {

            if (!cancellable.isCancelled && mFrameProcessor.take()) {
                // We are called from main thread, so let's copy buffer in the frame processor's thread
                mFrameProcessor.processCopy(data, previewSize, imageFormat, frameRotation) {
                    camera.addCallbackBuffer(it)
                }
            } else {
                // Consume data immediately
                camera.addCallbackBuffer(data)
            }
        }
    }

}