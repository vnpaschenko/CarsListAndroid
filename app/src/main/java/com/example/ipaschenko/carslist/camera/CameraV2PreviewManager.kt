package com.example.ipaschenko.carslist.camera

import android.content.Context
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
import java.io.IOException

import java.util.*
import java.util.concurrent.Semaphore
import java.util.concurrent.TimeUnit
import android.graphics.*
import android.graphics.ImageFormat
import android.util.Range
import android.view.View
import android.view.WindowManager
import java.nio.ByteBuffer


/**
 * CameraPreviewManager based on camera v2 API
 */
internal class CameraV2PreviewManager(context: Context, settings: CameraPreviewSettings):
        CameraPreviewManager {

    val hardwareLevel: Int // Camera device HW level

    private val mSettings = settings

    // Camera info
    private val mCameraManager = context.getSystemService(Context.CAMERA_SERVICE) as CameraManager
    private val mCameraId: String

    private val mPreviewSize: Size
    private val mFlashSupported: Boolean
    private val mCameraOrientation: Int
    private val mFpsRange: Range<Int>?

    private var mEventListener: CameraPreviewManager.CameraPreviewManagerEventListener? = null

    // Threading stuff
    private val mCameraLock = Semaphore(1)

    private var mBackgroundThread: HandlerThread? = null
    private var mBackgroundHandler: Handler? = null
    private val mMainHandler = Handler()

    // Session data, accessed from bg thread only
    private var mCameraDevice: CameraDevice? = null
    private var mRequestBuilder: CaptureRequest.Builder? = null
    private var mCaptureSession: CameraCaptureSession? = null
    private var mImageReader: ImageReader? = null

    // Run data
    private var mRunning = false
    private var mCancellationToken: CancellationToken? = null
    private var mFlashIsOn = false

    private var mLayoutCallback: (()->Unit)? = null

    // ---------------------------------------------------------------------------------------------
    // Initialization
    init {
        // Currently we support only ImageFormat.NV21
        if (settings.imageFormat != ImageFormat.NV21) {
            throw CameraPreviewManager.PreviewFormatNotSupportedException()
        }

        var selectedPreviewSize: Size? = null
        var selectedCameraId: String? = null
        var isFlashSupported: Boolean? = null
        var orientation: Int? = null
        var fpsRange: Range<Int>? = null
        var hwLevel = -1

        // Look for proper LENS_FACING_BACK camera
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

            selectedPreviewSize = getBestPreviewSize(map.getOutputSizes(ImageFormat.YUV_420_888)) ?:
                    continue

            // Check if the flash is supported.
            isFlashSupported =
                    characteristics.get(CameraCharacteristics.FLASH_INFO_AVAILABLE)

            orientation = characteristics.get(CameraCharacteristics.SENSOR_ORIENTATION)

            selectedCameraId = cameraId

            hwLevel = characteristics.get(CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL)

            fpsRange = getBestFpsRange(characteristics.get(
                    CameraCharacteristics.CONTROL_AE_AVAILABLE_TARGET_FPS_RANGES))

            // We've found a viable camera and finished setting up member variables,
            // so we don't need to iterate through other available cameras.
            break
        }

        if (selectedCameraId == null) {
            throw CameraPreviewManager.CameraNotFoundException()
        } else {
            mCameraId = selectedCameraId
            mPreviewSize = selectedPreviewSize!!
            mFlashSupported =  isFlashSupported ?: false
            mCameraOrientation = orientation ?: 90 // usually 90
            mFpsRange = fpsRange
            hardwareLevel = hwLevel
        }
    }

    private fun getBestFpsRange(supportedRanges: Array<Range<Int>>?): Range<Int>? {
        if (supportedRanges?.isNotEmpty() != true) {
            return null
        }
        var requestedRange = mSettings.requestedFps

        // Find the closest range to requested one
        var result = supportedRanges.filter { it.contains(requestedRange) }.minBy {
            requestedRange - it.lower + it.upper - requestedRange }

        // For some devices FPS range reported multiplied by 1000
        if (result == null) {
            requestedRange = mSettings.requestedFps * 1000
            result = supportedRanges.filter { it.contains(requestedRange) }.minBy {
                requestedRange - it.lower + it.upper - requestedRange }
        }

        return result
    }

    private fun getBestPreviewSize(supportedSizes: Array<Size>?): Size? {

        return supportedSizes?.asList()?.sortedByDescending { it.width * it.height }?.first {
            it.width * it.height <= mSettings.maxPreviewSize
        }
    }

    @MainThread
    @Throws(CameraPreviewManager.CameraException::class, IOException::class)
    override fun start(context: Context, turnFlash: Boolean, textureView: TextureView,
              listener: CameraPreviewManager.CameraPreviewManagerEventListener,
              parametersSelected:(previewSize: Size, flashStatus: Boolean?) -> Unit) {

        if (mRunning) {
            return
        }

        startBackgroutThread()
        var error: Throwable? = null

        val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager

        try {
            // Wait for camera to open - 2.5 seconds is sufficient
            if (!mCameraLock.tryAcquire(2500, TimeUnit.MILLISECONDS)) {
                throw RuntimeException("Time out waiting to lock camera opening.")
            }
            mEventListener = listener
            mCancellationToken = CancellationToken()

            // Determine the frame rotation, assuming we use only back camera
            var degrees = 0
            val rotation = windowManager.defaultDisplay.rotation
            when (rotation) {
                Surface.ROTATION_0 -> degrees = 0
                Surface.ROTATION_90 -> degrees = 90
                Surface.ROTATION_180 -> degrees = 180
                Surface.ROTATION_270 -> degrees = 270
            }

            degrees = (mCameraOrientation - degrees + 360) % 360

            val stateCallback = CameraStateCallback(textureView.surfaceTexture,
                    degrees / 90,  if (mFlashSupported) turnFlash else null,
                    mBackgroundHandler, listener, mCancellationToken!!)

            mCameraManager.openCamera(mCameraId, stateCallback, mBackgroundHandler)

            mRunning = true

        } catch (se: SecurityException) {
            error = se
        } catch (e: Exception) {
            error = e
        }

        if (error != null) {
            stopBackgroundThread()
            reportError(error, mCancellationToken!!)
        } else {

            // Notify that params are applied
            parametersSelected(mPreviewSize, if (mFlashSupported) turnFlash else null)

            // Schedule surface transformation
            val layoutListener =  object: View.OnLayoutChangeListener {
                override fun onLayoutChange(v: View?, left: Int, top: Int, right: Int,
                            bottom: Int, oldLeft: Int, oldTop: Int, oldRight: Int, oldBottom: Int) {

                    transformSurface(windowManager, textureView)
                    textureView.removeOnLayoutChangeListener(this)
                    mLayoutCallback = null
                }
            }

            textureView.addOnLayoutChangeListener(layoutListener)
            mLayoutCallback = {textureView.removeOnLayoutChangeListener(layoutListener)}
        }
    }

    @MainThread
    override fun stop() {

        if (mRunning) {

            mLayoutCallback?.invoke()
            mLayoutCallback = null

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
        if (mRunning && mFlashSupported) {
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
            mEventListener?.onCameraPreviewError(error)
            cancellationToken.cancel()
        }
    }

    private fun createCameraPreviewSession(camera: CameraDevice, callback: CameraStateCallback) {

        // Init image reader
        val imageReader = ImageReader.newInstance(mPreviewSize.width, mPreviewSize.height,
                ImageFormat.YUV_420_888, 2)

        imageReader.setOnImageAvailableListener(ImageAvailableListener(callback.frameRotation,
                callback.cancellationToken, callback.listener), null)

        mImageReader = imageReader

        mCameraDevice = camera

        // Init capture request

        callback.texture.setDefaultBufferSize(mPreviewSize.width, mPreviewSize.height)

        // This is the output Surface we need to start preview.
        val surface = Surface(callback.texture)

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

                            // Set FPS range if determined
                            if (mFpsRange != null) {
                                builder.set(CaptureRequest.CONTROL_AE_TARGET_FPS_RANGE, mFpsRange)
                            }

                            val request = builder.build()
                            mRequestBuilder = builder
                            mCaptureSession = session

                            if (!callback.cancellationToken.isCancelled) {
                                session.setRepeatingRequest(request, null, null)

                                // Deal with the initial flash request (for some reason setting
                                // FLASH_MODE_TORCH before preview start doesn't work
                                if (callback.flashStatus == true) {
                                    callback.backgroundHandler?.post {
                                        mFlashIsOn = true
                                        mRequestBuilder?.set(CaptureRequest.FLASH_MODE,
                                                CaptureRequest.FLASH_MODE_TORCH)
                                        mCaptureSession?.setRepeatingRequest(
                                                mRequestBuilder?.build(), null, null)
                                    }
                                } else {
                                    mFlashIsOn = false
                                }

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
                            reportError(Exception(), callback.cancellationToken)
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
            mFlashIsOn = false
        } catch (e: InterruptedException) {
            throw RuntimeException("Interrupted while trying to lock camera closing.", e)
        } finally {
            mCameraLock.release()
        }
    }

    private fun transformSurface(windowManager: WindowManager, textureView: TextureView) {

        val viewWidth = textureView.measuredWidth
        val viewHeight = textureView.measuredHeight

        var previewWidth = mPreviewSize.width
        var previewHeight = mPreviewSize.height

        if (viewWidth > viewHeight && previewWidth < previewHeight || viewHeight > viewWidth &&
                previewHeight < previewWidth) {
            previewWidth = mPreviewSize.height
            previewHeight = mPreviewSize.width
        }

        val rotation = windowManager.defaultDisplay.rotation
        val matrix = Matrix()
        val viewRect = RectF(0f, 0f, viewWidth.toFloat(), viewHeight.toFloat())
        val bufferRect = RectF(0f, 0f, previewHeight.toFloat(),
                previewWidth.toFloat())

        val centerX =  (textureView.right - textureView.left) / 2f
        val centerY = (textureView.bottom - textureView.top) / 2f

        if (Surface.ROTATION_90 == rotation || Surface.ROTATION_270 == rotation) {
            bufferRect.offset(centerX - bufferRect.centerX(), centerY - bufferRect.centerY())

            val scale = Math.max(viewHeight.toFloat() / previewHeight,
                    viewWidth.toFloat() / previewWidth)

            with(matrix) {
                setRectToRect(viewRect, bufferRect, Matrix.ScaleToFit.FILL)
                postScale(scale, scale, centerX, centerY)
                postRotate((90 * (rotation - 2)).toFloat(), centerX, centerY)
            }
        } else if (Surface.ROTATION_180 == rotation) {

            matrix.postRotate(180f, centerX, centerY)
        }

        textureView.setTransform(matrix)
    }

    // ---------------------------------------------------------------------------------------------
    // CameraDevice.StateCallback implementation
    private inner class CameraStateCallback(
            val texture: SurfaceTexture,
            val frameRotation: Int,
            val flashStatus: Boolean?,
            val backgroundHandler: Handler?,
            val listener: CameraPreviewManager.CameraPreviewManagerEventListener,
            val cancellationToken: CancellationToken): CameraDevice.StateCallback() {

        override fun onOpened(camera: CameraDevice?) {
            mCameraLock.release()

            if (!cancellationToken.isCancelled) {
                createCameraPreviewSession(camera!!, this)
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
    private class ImageAvailableListener(private val frameRotation: Int,
            private val cancellationToken: CancellationToken,
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

            val yPlane = image.planes[0]
            val vPlane = image.planes[2]

            val yBuffer = yPlane.buffer
            val yLen = yBuffer.remaining()

            val vBuffer = vPlane.buffer
            val vLen = vBuffer.remaining()

            val data: ByteArray
            if (vPlane.pixelStride == 1) {
                // planar image
                val uPlane = image.planes[1]
                val uBuffer = uPlane.buffer
                val uLen = uBuffer.remaining()


                data = getData(yLen + uLen + vLen)
                yBuffer.get(data, 0, yLen)

                var index = yLen
                for (i in 0 until vLen) {
                    data[index++] = vBuffer[i]
                    data[index++] = uBuffer[i]
                }

            } else {
                // Semi-planar image, Use the v plane that corresponds to NV21 format
                data = getData(yLen + vLen)
                yBuffer.get(data, 0, yLen)

                val vPart = ByteBuffer.wrap(data, yLen, vLen)
                vPart.put(vBuffer)

            }

//            val img = YuvImage(data, ImageFormat.NV21, image.width, image.height, null)
//            val out = ByteArrayOutputStream()
//            img.compressToJpeg(Rect(0, 0, image.width, image.height), 100, out)
//            val imageBytes = out.toByteArray()
//            val image_ = BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.size)

            mFrameProcessor.process(data, Size(image.width, image.height), ImageFormat.NV21,
                    frameRotation)

        }

        private fun getData(size: Int): ByteArray {
            if (mBytes == null || mBytes?.size != size) {
                mBytes = ByteArray(size)
            }

            return mBytes!!
        }
    }

}
