package com.example.ipaschenko.carslist

import android.Manifest
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.graphics.ImageFormat
import android.graphics.SurfaceTexture
import android.os.Bundle
import android.os.SystemClock
import android.support.v4.app.Fragment
import android.support.v4.content.ContextCompat
import android.util.Size
import android.view.*
import com.example.ipaschenko.carslist.camera.CameraPreviewManager
import com.example.ipaschenko.carslist.camera.CameraPreviewSettings
import com.example.ipaschenko.carslist.utils.Cancellable
import com.example.ipaschenko.carslist.views.AutoFitTextureView
import com.example.ipaschenko.carslist.views.applyRoundOutline
import com.google.android.gms.vision.Detector
import com.google.android.gms.vision.Frame
import com.google.android.gms.vision.text.TextBlock
import com.google.android.gms.vision.text.TextRecognizer
import java.lang.ref.WeakReference
import java.nio.ByteBuffer
import java.util.concurrent.atomic.AtomicInteger

/**
 *
 */
class CarNumberCaptureFragment: Fragment(), TextureView.SurfaceTextureListener {

    private lateinit var mTextureView: AutoFitTextureView
    private lateinit var mToggleFlashButton: View
    private var mPreviewManager: CameraPreviewManager? = null

    companion object {
        private const val CAMERA_PERMISSION_REQUEST = 2018

        @JvmStatic
        fun newInstance(): CarNumberCaptureFragment = CarNumberCaptureFragment()
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?,
            savedInstanceState: Bundle?): View? =
            inflater.inflate(R.layout.fragment_car_number_capture, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        mTextureView = view.findViewById(R.id.texture)
        mToggleFlashButton = view.findViewById(R.id.toggle_flash)
        mToggleFlashButton.applyRoundOutline()

        mToggleFlashButton.setOnClickListener {
            mPreviewManager?.apply {
                mPreviewManager!!.toggleFlash()
                mToggleFlashButton.isSelected = !mToggleFlashButton.isSelected
            }
        }
        mToggleFlashButton.visibility = View.GONE
    }

    override fun onResume() {
        super.onResume()
        if (mTextureView.isAvailable) {
            startPreview()
        } else {
            mTextureView.surfaceTextureListener = this
        }
    }

    override fun onPause() {
        super.onPause()
        mPreviewManager?.stop()
        mPreviewManager = null
    }

    private fun startPreview() {
        activity ?: return

        val permission = ContextCompat.checkSelfPermission(activity!!, Manifest.permission.CAMERA)
        if (permission != PackageManager.PERMISSION_GRANTED) {
            requestCameraPermission()
        } else {
            startPreviewManager()
        }
    }

    private fun startPreviewManager() {
        val settings = CameraPreviewSettings(ImageFormat.NV21, 1024 * 768) // 1280x720?

        mToggleFlashButton.visibility = View.GONE

        mPreviewManager = CameraPreviewManager.newPreviewManager(settings)
        val listener = CameraEventListener(this)

        try {

            mPreviewManager!!.start(context!!, false, mTextureView, listener,
            { previewSize, flashStatus ->

                if (resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE) {
                    mTextureView.setAspectRatio(previewSize.width, previewSize.height)
                } else {
                    mTextureView.setAspectRatio(previewSize.height, previewSize.width)
                }

                if (flashStatus != null) {
                    mToggleFlashButton.visibility = View.VISIBLE
                    mToggleFlashButton.isSelected = flashStatus
                }

            })
        } catch (e: Throwable) {
            mPreviewManager = null
            listener.onCameraPreviewError(e)
        }
    }

    private fun requestCameraPermission() {
       requestPermissions(arrayOf(Manifest.permission.CAMERA), CAMERA_PERMISSION_REQUEST)
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<String>,
            grantResults: IntArray) {

        if (requestCode == CAMERA_PERMISSION_REQUEST) {
            if (grantResults.size != 1 || grantResults[0] != PackageManager.PERMISSION_GRANTED) {
                // Todo: Show some error dialog
                activity?.finish()
            } else {
                activity?.apply{ startPreviewManager() }
            }
        } else {
            super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        }
    }

    // ---------------------------------------------------------------------------------------------
    // TextureView.SurfaceTextureListener implementation
    override fun onSurfaceTextureSizeChanged(surface: SurfaceTexture?, width: Int, height: Int) {
        //transformSurface(width, height)
    }

    override fun onSurfaceTextureAvailable(surface: SurfaceTexture?, width: Int, height: Int) {
        startPreview()
    }

    override fun onSurfaceTextureUpdated(surface: SurfaceTexture?) = Unit

    override fun onSurfaceTextureDestroyed(surface: SurfaceTexture?): Boolean = true


    // ---------------------------------------------------------------------------------------------
    // CameraPreviewManager.CameraPreviewManagerEventListener
    private class CameraEventListener(parent: CarNumberCaptureFragment):
            CameraPreviewManager.CameraPreviewManagerEventListener,
            Detector.Processor<TextBlock> {

        private val mParentRef = WeakReference<CarNumberCaptureFragment>(parent)
        private val mRecognizer = TextRecognizer.Builder(parent.context).build()
        private var mStartTime = SystemClock.elapsedRealtime()
        private var mFrameId = 0

        init {
            mRecognizer.setProcessor(this)
        }

        override fun onCameraPreviewObtained(imageData: ByteArray, imageSize: Size, imageFormat: Int,
                frameRotation: Int, cancellable: Cancellable) {

            val parent = mParentRef.get()
            parent ?: return

            val buffer = ByteBuffer.wrap(imageData, 0, imageData.size)

            val frame = Frame.Builder()
                    .setImageData(buffer, imageSize.width, imageSize.height, imageFormat)
                    .setId(mFrameId++)
                    .setTimestampMillis(SystemClock.elapsedRealtime() - mStartTime)
                    .setRotation(frameRotation)
                    .build()
            mRecognizer.receiveFrame(frame)
        }

        override fun onCameraPreviewError(error: Throwable) {

        }

        override fun release() {

        }

        override fun receiveDetections(detections: Detector.Detections<TextBlock>?) {
            val items = detections?.detectedItems

        }
    }

}
