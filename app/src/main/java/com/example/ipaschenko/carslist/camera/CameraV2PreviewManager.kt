package com.example.ipaschenko.carslist.camera

import android.app.Activity
import android.content.Context
import android.graphics.ImageFormat
import android.hardware.camera2.*
import android.media.Image
import android.media.ImageReader
import android.os.Handler
import android.os.HandlerThread
import android.support.annotation.MainThread
import android.util.Size
import android.view.Surface
import android.view.TextureView
import com.example.ipaschenko.carslist.utils.CancellationToken
import com.example.ipaschenko.carslist.utils.canContinue

import java.util.*
import java.util.concurrent.Semaphore
import java.util.concurrent.TimeUnit

/**
 * CameraPreviewManager based on camera v2 API
 */
internal class CameraV2PreviewManager(settings: CameraPreviewSettings, activity: Activity,
        textureView: TextureView, listener: CameraPreviewManager.CameraPreviewManagerEventListener):
        CameraPreviewManager {

    override val previewSize: Size
    override val flashSupported: Boolean
    override val cameraOrientation: Int

    private val mSettings = settings
    private val mTextureView = textureView
    private val mEventListener = listener

    private val mCameraManager = activity.getSystemService(Context.CAMERA_SERVICE) as CameraManager
    private val mCameraId: String

    private val mCameraLock = Semaphore(1)

    private var mBackgroundThread: HandlerThread? = null
    private var mBackgroundHandler: Handler? = null
    private val mMainHandler = Handler()

    private var mCameraDevice: CameraDevice? = null
    private var mRequestBuilder: CaptureRequest.Builder? = null
    private var mCaptureSession: CameraCaptureSession? = null
    private var mImageReader: ImageReader? = null

    private var mRunning = false
    private var mCancellationToken: CancellationToken? = null
    private var mFlashIsOn = false

    // ---------------------------------------------------------------------------------------------
    // Initialization
    init {

        var selectedPreviewSize: Size? = null
        var selectedCameraId: String? = null
        var isFlashSupported: Boolean? = null
        var orientation: Int? = null

        for (cameraId in mCameraManager.cameraIdList) {
            val characteristics = mCameraManager.getCameraCharacteristics(cameraId)

            // We don't use a front facing camera in this sample.
            val cameraDirection = characteristics.get(CameraCharacteristics.LENS_FACING)
            if (cameraDirection != null &&
                    cameraDirection == CameraCharacteristics.LENS_FACING_FRONT) {
                continue
            }

            val map = characteristics.get(
                    CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP) ?: continue

            selectedPreviewSize = getBestPreviewSize(map.getOutputSizes(mSettings.imageFormat)) ?:
                    continue

            // Check if the flash is supported.
            isFlashSupported =
                    characteristics.get(CameraCharacteristics.FLASH_INFO_AVAILABLE)

            orientation = characteristics.get(CameraCharacteristics.SENSOR_ORIENTATION)

            selectedCameraId = cameraId

            // We've found a viable camera and finished setting up member variables,
            // so we don't need to iterate through other available cameras.
            break
        }

        if (selectedCameraId == null) {
            throw CameraPreviewManager.CameraNotFoundException()
        } else {
            mCameraId = selectedCameraId
            previewSize = selectedPreviewSize!!
            flashSupported =  isFlashSupported ?: false
            cameraOrientation = orientation ?: 90 // usually 90
        }
    }

    private fun getBestPreviewSize(supportedSizes: Array<Size>?): Size? {

        return supportedSizes?.asList()?.sortedByDescending { it.width * it.height }?.first {
            it.width * it.height <= mSettings.maxPreviewSize
        }
    }

    @MainThread
    override fun start() {
        if (mRunning) {
            return
        }

        mCancellationToken = CancellationToken()

        startBackgroutThread()
        var error: Throwable? = null

        try {
            // Wait for camera to open - 2.5 seconds is sufficient
            if (!mCameraLock.tryAcquire(2500, TimeUnit.MILLISECONDS)) {
                throw RuntimeException("Time out waiting to lock camera opening.")
            }
            mCameraManager.openCamera(mCameraId, CameraStateCallback(mCancellationToken!!),
                    mBackgroundHandler)
            mRunning = true

        } catch (se: SecurityException) {
            error = se
        } catch (e: Exception) {
            error = e
        }

        error?.apply {
            stopBackgroundThread()
            reportError(error, mCancellationToken!!)
        }
    }

    @MainThread
    override fun stop() {
        if (mRunning) {
            val handlerThread = mBackgroundThread
            mCancellationToken?.cancel()

            mBackgroundHandler?.post{
                releaseCameraPreviewSession()
                mBackgroundThread?.quitSafely()
                handlerThread?.quitSafely()
            }

            handlerThread?.join()
            mCancellationToken = null
            mBackgroundThread = null
            mBackgroundHandler = null
            mRunning = false
        }
    }

    @MainThread
    override fun toggleFlash() {
        if (mRunning && flashSupported) {
            val cancellationToken = mCancellationToken
            mBackgroundHandler?.post {
                if (mCaptureSession != null && mRequestBuilder != null &&
                        cancellationToken.canContinue()) {
                    if (mFlashIsOn) {
                        mFlashIsOn = false
                        mRequestBuilder?.set(CaptureRequest.FLASH_MODE,
                                CaptureRequest.FLASH_MODE_OFF)

                    } else {
                        mFlashIsOn = true
                        mRequestBuilder?.set(CaptureRequest.FLASH_MODE,
                                CaptureRequest.FLASH_MODE_TORCH)
                    }
                    //mCaptureSession?.stopRepeating()
                    mCaptureSession?.setRepeatingRequest(mRequestBuilder?.build(), null,
                            null)
                    //mCaptureSession?.capture(mRequestBuilder?.build(), null, null);

                }
            }
        }
    }

    private fun startBackgroutThread() {
        if (mBackgroundThread == null) {
            val backgroundThread = HandlerThread("ImageReaderThread")
            backgroundThread.start()
            mBackgroundHandler = Handler(backgroundThread.looper)
            mBackgroundThread = backgroundThread
        }
    }

    private fun stopBackgroundThread() {
        mBackgroundThread?.quitSafely()
        mBackgroundThread = null
        mBackgroundHandler = null
        mRunning = false
    }

    private fun reportError(error: Throwable, cancellationToken: CancellationToken) {
        if (!cancellationToken.isCancelled) {
            mEventListener.onCameraPreviewError(error)
            cancellationToken.cancel()
        }
    }

    private fun createCameraPreviewSession(camera: CameraDevice, cancellationToken: CancellationToken) {

        // Init image reader
        val imageReader = ImageReader.newInstance(previewSize.width, previewSize.height,
                mSettings.imageFormat, 2)

        imageReader.setOnImageAvailableListener(ImageAvailableListener(cancellationToken,
                mEventListener), null)

        mImageReader = imageReader

        mCameraDevice = camera

        // Init capture request
        val texture = mTextureView.surfaceTexture
        texture.setDefaultBufferSize(previewSize.width, previewSize.height)

        // This is the output Surface we need to start preview.
        val surface = Surface(texture)

        // We set up a CaptureRequest.Builder with the output Surface.
        val builder = camera.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW)
        builder.addTarget(imageReader.surface)
        builder.addTarget(surface)

        // Create a CameraCaptureSession for camera preview.
        camera.createCaptureSession(Arrays.asList(surface, imageReader.surface),
                object : CameraCaptureSession.StateCallback() {

                    override fun onConfigured(session: CameraCaptureSession?) {
                        if (session == null) {
                            // Something goes wrong.
                            // TODO: Report error?
                            releaseCameraPreviewSession()
                        } else {
                            builder.set(CaptureRequest.CONTROL_AF_MODE,
                                    CaptureRequest.CONTROL_AF_STATE_ACTIVE_SCAN)

                            val request = builder.build()
                            mRequestBuilder = builder
                            mCaptureSession = session

                            if (!cancellationToken.isCancelled) {
                                session.setRepeatingRequest(request, null, null)
                            } else {
                                releaseCameraPreviewSession()
                                mMainHandler.post {
                                    stopBackgroundThread()
                                }
                            }
                       }
                    }

                    override fun onConfigureFailed(session: CameraCaptureSession?) {
                        releaseCameraPreviewSession()
                        mMainHandler.post {
                            stopBackgroundThread()
                            reportError(Exception(), cancellationToken)
                        }

                    }

                }, null)
    }

    private fun releaseCameraPreviewSession() {
        try {
            mCameraLock.acquire()
            mCaptureSession?.close()
            mCaptureSession = null
            mCameraDevice?.close()
            mCameraDevice = null
            mImageReader?.close()
            mImageReader = null
        } catch (e: InterruptedException) {
            throw RuntimeException("Interrupted while trying to lock camera closing.", e)
        } finally {
            mCameraLock.release()
        }
    }

    // ---------------------------------------------------------------------------------------------
    // CameraDevice.StateCallback implementation
    private inner class CameraStateCallback(private val cancellationToken: CancellationToken):
            CameraDevice.StateCallback() {

        override fun onOpened(camera: CameraDevice?) {
            mCameraLock.release()

            if (!cancellationToken.isCancelled) {
                createCameraPreviewSession(camera!!, cancellationToken)
            } else {
                camera?.close()
                releaseCameraPreviewSession()
                mMainHandler.post {
                    stopBackgroundThread()
                }
            }
        }

        override fun onDisconnected(camera: CameraDevice?) {
            mCameraLock.release()
            camera?.close()
            releaseCameraPreviewSession()
        }

        override fun onError(camera: CameraDevice?, error: Int) {
            onDisconnected(camera)
            mMainHandler.post {
                stopBackgroundThread()
                reportError(Exception(), cancellationToken)
            }
        }
    }

    // ---------------------------------------------------------------------------------------------
    // ImageReader.OnImageAvailableListener implementation
    private class ImageAvailableListener(private val cancellationToken: CancellationToken,
            listener: CameraPreviewManager.CameraPreviewManagerEventListener):
            ImageReader.OnImageAvailableListener {

        private val mFrameProcessor = FrameProcessor(cancellationToken, listener)
        private var mBytes: ByteArray? = null

        override fun onImageAvailable(reader: ImageReader?) {
            val image = reader?.acquireLatestImage()
            if (image != null && !cancellationToken.isCancelled && mFrameProcessor.take()) {
                try {
                    processImage(image)
                } catch (e: Throwable) {
                    mFrameProcessor.release()
                }
            }

            image?.close()
        }

        private fun processImage(image: Image) {
            val width = image.width
            val height = image.height

            val buffer = image.planes[0].buffer
            val size = buffer.remaining()
            if (mBytes == null || mBytes!!.size != size) {
                mBytes = ByteArray(size)
            }

            buffer.get(mBytes!!)
            mFrameProcessor.process(mBytes!!, width, height, image.format)
         }
    }
}