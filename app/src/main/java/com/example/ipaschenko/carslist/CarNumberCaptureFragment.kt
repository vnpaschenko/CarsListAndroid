package com.example.ipaschenko.carslist

import android.Manifest
import android.arch.lifecycle.Lifecycle
import android.content.Context
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.graphics.ImageFormat
import android.graphics.Point
import android.graphics.Rect
import android.graphics.SurfaceTexture
import android.os.Bundle
import android.os.Handler
import android.os.SystemClock
import android.support.v4.app.Fragment
import android.support.v4.content.ContextCompat
import android.text.TextUtils
import android.util.Size
import android.view.*
import com.example.ipaschenko.carslist.camera.CameraPreviewManager
import com.example.ipaschenko.carslist.camera.CameraPreviewSettings
import com.example.ipaschenko.carslist.utils.Cancellable
import com.example.ipaschenko.carslist.utils.canContinue
import com.example.ipaschenko.carslist.views.AutoFitTextureView
import com.example.ipaschenko.carslist.views.OverlayView
import com.example.ipaschenko.carslist.views.applyRoundOutline
import com.google.android.gms.vision.Detector
import com.google.android.gms.vision.Frame
import com.google.android.gms.vision.text.TextBlock
import com.google.android.gms.vision.text.TextRecognizer
import java.lang.ref.WeakReference
import java.nio.ByteBuffer
import java.util.*
import android.arch.lifecycle.Observer
import android.widget.Toast
import com.example.ipaschenko.carslist.data.*

const val SHARED_PREFS_NAME = "CarsListPrefs"

/**
 * Represents text detection
 */
class Detection(val text: String, val boundingBox: Rect?) {}

/**
 *
 */
class CarNumberCaptureFragment: Fragment(), TextureView.SurfaceTextureListener {

    private lateinit var mTextureView: AutoFitTextureView
    private lateinit var mOverlay: OverlayView

    private lateinit var mToggleFlashButton: View
    private var mPreviewManager: CameraPreviewManager? = null

    private var mHintView: View? = null
    private var mHideHintButton: View? = null

    private val isHintDisplayed: Boolean
        get() = mHideHintButton != null

    companion object {
        private const val CAMERA_PERMISSION_REQUEST = 2018
        private const val SKIP_HINT_KEY = "CarNumberCaptureFragment-SkipHint"

        @JvmStatic
        fun newInstance(): CarNumberCaptureFragment = CarNumberCaptureFragment()
    }

    override fun onAttach(context: Context?) {
        super.onAttach(context)
        CarsListApplication.application.databaseStatus.observe(this,
                Observer { status: DbStatus? ->
                    if (status != null) {
                        if (!status.isDataAvailable) {
                            val message = getString(R.string.unavailable_data_message)
                            val error = formatDbStatusError(context!!, status.errorInfo?.error)
                            Toast.makeText(context, "$message\n$error", Toast.LENGTH_LONG).
                                    show()

                        } else if (status.errorInfo != null && !status.errorInfo.handled) {
                            status.errorInfo.handled = true
                            val message = getString(R.string.update_data_error_message)
                            val error = formatDbStatusError(context!!, status.errorInfo.error)
                            Toast.makeText(context, "$message\n$error", Toast.LENGTH_SHORT).
                                    show()
                        }
                    }
                }
        )
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

        mOverlay = view.findViewById(R.id.overlay)
        mTextureView.addOnLayoutChangeListener { _, left, top, _, _, _, _, _, _ ->
            mOverlay.startPoint = Point(left, top)

        }

        mTextureView.surfaceTextureListener = this

        // Show hint ?
        if (context?.getSharedPreferences(SHARED_PREFS_NAME, Context.MODE_PRIVATE)
                ?.getBoolean(SKIP_HINT_KEY, false) != true) {
            mHintView = view.findViewById(R.id.hint_view)
            mHideHintButton = mHintView?.findViewById(R.id.hint_hide_button)
            mHintView?.visibility = View.VISIBLE
            mHideHintButton?.setOnClickListener {
                hideHintView(null)
            }

            mOverlay.visibility = View.GONE
        }
    }

    override fun onResume() {
        super.onResume()
        if (mTextureView.isAvailable) {
            startPreview()
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

                if (mHideHintButton != null) {
                    mHideHintButton!!.setOnClickListener {
                        hideHintView(flashStatus)
                    }
                } else if (flashStatus != null) {
                    mToggleFlashButton.visibility = View.VISIBLE
                    mToggleFlashButton.isSelected = flashStatus
                }

                mOverlay.previewSize = previewSize

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

    private fun displayDetections(detections: Collection<Detection>) {
        mOverlay.drawDetections(detections)
    }

    private fun clearDetections() {
        mOverlay.drawDetections(null)
    }

    private fun hideHintView(flashStatus: Boolean?) {

        mHideHintButton = mHintView?.findViewById(R.id.hint_hide_button)
        mHintView?.visibility = View.GONE
        mHideHintButton?.setOnClickListener(null)
        mHintView = null
        mHideHintButton = null

        mOverlay.visibility = View.VISIBLE

        if (flashStatus != null) {
            mToggleFlashButton.visibility = View.VISIBLE
            mToggleFlashButton.isSelected = flashStatus
        }

        context?.getSharedPreferences(SHARED_PREFS_NAME, Context.MODE_PRIVATE)?.edit()?.
                putBoolean(SKIP_HINT_KEY, true)?.apply()
    }

    // ---------------------------------------------------------------------------------------------
    // TextureView.SurfaceTextureListener implementation
    override fun onSurfaceTextureSizeChanged(surface: SurfaceTexture?, width: Int, height: Int) {
        //transformSurface(width, height)
    }

    override fun onSurfaceTextureAvailable(surface: SurfaceTexture?, width: Int, height: Int) {
        if (lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) {
            startPreview()
        }
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
        private var mCancellable: Cancellable? = null
        private val mHandler = Handler()
        private var mLastDetectionsCount = 0
        private var mDatabase: CarsDatabase? = null

        init {
            mRecognizer.setProcessor(this)
        }

        override fun onCameraPreviewObtained(imageData: ByteArray, imageSize: Size, imageFormat: Int,
                frameRotation: Int, cancellable: Cancellable) {

            mCancellable = cancellable

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
            if (!mCancellable.canContinue()) {
                return
            }
            mLastDetectionsCount = 0

            val cancellable = mCancellable
            mHandler.post {
                if (cancellable.canContinue()) {
                    val parent = mParentRef.get()
                    if (parent != null) {
                        parent.clearDetections()
                    }
                }
            }
        }

        override fun receiveDetections(detections: Detector.Detections<TextBlock>?) {

            if (!mCancellable.canContinue()) {
                return
            }

            //processText("AE 5432")

            val items = detections?.detectedItems
            val count = items?.size() ?: 0

            // Don't sequentaly report 0
            if (count == 0 && mLastDetectionsCount == 0) {
                return
            }
            mLastDetectionsCount = count

            val detectionsList = ArrayList<Detection>(count)
            for (i in 0 until count) {
                val block = items!![i]
                val text = block?.value

                if (!TextUtils.isEmpty(text)) {
                    detectionsList.add(Detection(text!!, block.boundingBox))
                }
            }


            // Post display detections
            val cancellable = mCancellable
            mHandler.post {
                if (cancellable.canContinue()) {
                    val parent = mParentRef.get()
                    if (parent != null) {
                        parent.displayDetections(Collections.unmodifiableCollection(detectionsList))
                    }
                }
            }

            for (detection in detectionsList) {
                processText(detection.text)
            }
        }

        private fun processText(text: String) {
            val number = CarNumber.fromString(text, false)
            if (number == null || number.isCustom) {
                return
            }

            if (mDatabase == null) {
                mDatabase = CarsListApplication.application.getCarsListDatabase(mCancellable)
            }

            val dao = mDatabase?.carsDao() ?: return

            val matches = dao.loadByNumberRoot(number.root)
            val car = getMostProperCar(matches, number)
            if (car != null) {
                processCar(car)
            }
        }

        private fun processCar(car: CarInfo) {

        }

    }

}
