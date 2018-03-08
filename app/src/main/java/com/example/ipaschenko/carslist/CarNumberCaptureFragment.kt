package com.example.ipaschenko.carslist

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Matrix
import android.graphics.RectF
import android.graphics.SurfaceTexture
import android.os.Bundle
import android.support.v4.app.Fragment
import android.support.v4.content.ContextCompat
import android.view.*
import com.example.ipaschenko.carslist.camera.CameraPreviewManager
import com.example.ipaschenko.carslist.utils.Cancellable
import com.example.ipaschenko.carslist.views.AutoFitTextureView
import com.example.ipaschenko.carslist.views.applyRoundOutline

/**
 *
 */
class CarNumberCaptureFragment: Fragment(), TextureView.SurfaceTextureListener,
        CameraPreviewManager.CameraPreviewManagerEventListener {

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
            startPreview(mTextureView.width, mTextureView.height)
        } else {
            mTextureView.surfaceTextureListener = this
        }
    }

    override fun onPause() {
        super.onPause()
        mPreviewManager?.stop()
        mPreviewManager = null
    }

    private fun startPreview(viewWidth: Int, viewHeight: Int) {
        activity ?: return

        val permission = ContextCompat.checkSelfPermission(activity!!, Manifest.permission.CAMERA)
        if (permission != PackageManager.PERMISSION_GRANTED) {
            requestCameraPermission()
        } else {
            startPreviewManager(viewWidth, viewHeight)
        }
    }

    private fun startPreviewManager(viewWidth: Int, viewHeight: Int) {
        mPreviewManager = CameraPreviewManager.newPreviewManager(activity!!, mTextureView, this)
        transformSurface(viewWidth, viewHeight)

        mToggleFlashButton.visibility =
                if (mPreviewManager!!.flashSupported) View.VISIBLE else View.GONE

        mPreviewManager?.start()
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
                activity?.apply{ startPreviewManager(mTextureView.width, mTextureView.height) }
            }
        } else {
            super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        }
    }

    private fun transformSurface(viewWidth: Int, viewHeight: Int) {
        activity ?: return

        val previewWidth = mPreviewManager?.previewSize?.width ?: viewWidth
        val previewHeight = mPreviewManager?.previewSize?.height ?: viewHeight

//        if (resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE) {
//            mTextureView.setAspectRatio(previewWidth, previewHeight)
//        } else {
//            mTextureView.setAspectRatio(previewHeight, previewWidth)
//        }

        val rotation = activity!!.windowManager.defaultDisplay.rotation
        val matrix = Matrix()
        val viewRect = RectF(0f, 0f, viewWidth.toFloat(), viewHeight.toFloat())
        val bufferRect = RectF(0f, 0f, previewHeight.toFloat(),
                previewWidth.toFloat())

        val centerX = viewRect.centerX()
        val centerY = viewRect.centerY()

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

        mTextureView.setTransform(matrix)
    }

    // ---------------------------------------------------------------------------------------------
    // TextureView.SurfaceTextureListener implementation
    override fun onSurfaceTextureSizeChanged(surface: SurfaceTexture?, width: Int, height: Int) {
        transformSurface(width, height)
    }

    override fun onSurfaceTextureAvailable(surface: SurfaceTexture?, width: Int, height: Int) {
        startPreview(width, height)
    }

    override fun onSurfaceTextureUpdated(surface: SurfaceTexture?) = Unit

    override fun onSurfaceTextureDestroyed(surface: SurfaceTexture?): Boolean = true

    // ---------------------------------------------------------------------------------------------
    // CameraPreviewManager.CameraPreviewManagerEventListener implementation
    override fun onCameraPreviewObtained(imageData: ByteArray, width: Int, height: Int,
            imageFormat: Int, cancellable: Cancellable) {

        val i = 1

    }

    override fun onCameraPreviewError(error: Throwable) {
        // TODO handle error
    }
}
