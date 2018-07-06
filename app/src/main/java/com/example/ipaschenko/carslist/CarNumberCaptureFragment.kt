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
import android.support.v4.app.Fragment
import android.support.v4.content.ContextCompat
import android.view.*
import com.example.ipaschenko.carslist.camera.CameraPreviewManager
import com.example.ipaschenko.carslist.camera.CameraPreviewSettings
import com.example.ipaschenko.carslist.views.AutoFitTextureView
import com.example.ipaschenko.carslist.views.OverlayView
import com.example.ipaschenko.carslist.views.applyRoundOutline
import android.arch.lifecycle.Observer
import android.widget.Toast
import com.example.ipaschenko.carslist.data.*

const val SHARED_PREFS_NAME = "CarsListPrefs"

/**
 * Represents text detection
 */
class Detection(val text: String, val boundingBox: Rect?) {}

/**
 * Fragment that shows preview and deals with camera manager
 */
class CarNumberCaptureFragment: Fragment(), TextureView.SurfaceTextureListener,
        NumberCapturePresenter.View {

    private lateinit var mTextureView: AutoFitTextureView
    private lateinit var mOverlay: OverlayView

    private lateinit var mToggleFlashButton: View
    private lateinit var mShowSettingsButton: View

    private var mPreviewManager: CameraPreviewManager? = null

    private var mHintView: View? = null
    private var mHideHintButton: View? = null

    private var mFlashIsOn: Boolean = false
    private var mFlashOnTime = 0L

    private val isHintDisplayed: Boolean
        get() = mHideHintButton != null

    private var mPendingCarDetails: CarDetails? = null

    companion object {
        private const val CAMERA_PERMISSION_REQUEST = 2018
        private const val SKIP_HINT_KEY = "CarNumberCaptureFragment-SkipHint"
        private const val PENDING_DETAILS_KEY = "CarNumberCaptureFragment-PendingDetails"
        private const val FLASH_ON_KEY = "CarNumberCaptureFragment-FlashOn"
        private const val FLASH_ON_TIME_KEY = "CarNumberCaptureFragment-FlashOnTime"
        private const val FLASH_DATE_TOLERANCE = 5000L // 5 sec

        @JvmStatic
        fun newInstance(): CarNumberCaptureFragment = CarNumberCaptureFragment()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (savedInstanceState != null) {
            mPendingCarDetails = savedInstanceState.getParcelable(PENDING_DETAILS_KEY)
            mFlashOnTime = savedInstanceState.getLong(FLASH_ON_TIME_KEY)
            mFlashIsOn = savedInstanceState.getBoolean(FLASH_ON_KEY)
        }

        // Setup observer for DB status
        CarsListApplication.application.databaseStatus.observe(this,
                Observer { status: DbStatus? ->
                    if (status != null && status.processingState in
                            listOf(DbProcessingState.INITIALIZED, DbProcessingState.UPDATED)) {
                        if (!status.isDataAvailable) {
                            // Data is unavailable, we can't recognize anything
                            val message = getString(R.string.unavailable_data_message)
                            val error = formatDbStatusError(context!!, status.errorInfo?.error)
                            showToast("$message\n$error", false)
                            status.errorInfo?.handled = true

                        } else if (status.errorInfo != null && !status.errorInfo.handled) {
                            // Data is available but was not successfully updated
                            status.errorInfo.handled = true
                            val message = getString(R.string.update_data_error_message)
                            val error = formatDbStatusError(context!!, status.errorInfo.error)
                            showToast("$message\n$error")

                        }
                    }
                }
        )
    }

    override fun onAttach(context: Context?) {
        super.onAttach(context)

        val settings = CameraPreviewSettings(ImageFormat.NV21, 1024 * 768) // 1280x720?
        // Initialize the preview manager
        if (mPreviewManager == null) {
            mPreviewManager = CameraPreviewManager.newPreviewManager(context!!.applicationContext,
                    settings)
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?,
                              savedInstanceState: Bundle?): View? =
            inflater.inflate(R.layout.fragment_car_number_capture, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Setup controls here
        mTextureView = view.findViewById(R.id.texture)
        mToggleFlashButton = view.findViewById(R.id.toggle_flash)
        mToggleFlashButton.applyRoundOutline()

        mShowSettingsButton = view.findViewById(R.id.show_settings)
        mShowSettingsButton.applyRoundOutline()

        mToggleFlashButton.setOnClickListener {
            mPreviewManager?.apply {
                mPreviewManager!!.toggleFlash()
                mFlashIsOn = !mFlashIsOn
                mToggleFlashButton.isSelected = mFlashIsOn
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
            mShowSettingsButton.visibility = View.GONE
        }

        mShowSettingsButton.setOnClickListener {

            context?.apply {
                this.startActivity(SettingsActivity.newIntent(this))
            }
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)

        // If we have obtained car details that can't be presented due to inproper lifecycle
        // state, save it for further processing
        if (mPendingCarDetails != null) {
            outState.putParcelable(PENDING_DETAILS_KEY, mPendingCarDetails)
        }
        outState.putBoolean(FLASH_ON_KEY, mFlashIsOn)
        outState.putLong(FLASH_ON_TIME_KEY, mFlashOnTime)
    }

    override fun onResume() {
        super.onResume()
        if (mPendingCarDetails != null) {

            // We have details to show, start CarDetailsActivity
            val intent = CarDetailsActivity.newIntent(context!!, mPendingCarDetails!!)
            mPendingCarDetails = null
            startActivity(intent)

            // Reset flash status
            mFlashIsOn = false

        } else if (mTextureView.isAvailable) {


            // Our texture is available, start preview
            startPreview()
        }
    }

    override fun onPause() {
        super.onPause()
        mPreviewManager?.stop()
        clearDetections()
        mFlashOnTime = System.currentTimeMillis()
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

        // If we was paused too long, reset flash
        if (mFlashIsOn && System.currentTimeMillis() - mFlashOnTime > FLASH_DATE_TOLERANCE) {
            mFlashIsOn = false
        }

        mToggleFlashButton.visibility = View.GONE

        val listener = NumberCapturePresenter(context!!, this)

        try {
            mPreviewManager?.start(context!!, mFlashIsOn, mTextureView, listener,
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
                    mFlashIsOn = flashStatus
                }

                mOverlay.previewSize = previewSize

            })
        } catch (e: Throwable) {

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

        mShowSettingsButton.visibility = View.VISIBLE

        context?.getSharedPreferences(SHARED_PREFS_NAME, Context.MODE_PRIVATE)?.edit()?.
                putBoolean(SKIP_HINT_KEY, true)?.apply()
    }

    private fun showToast(message: CharSequence, shortDuration: Boolean = true) {
        if (context != null) {
            val toast = Toast.makeText(context, message,
                    if (shortDuration) Toast.LENGTH_SHORT else Toast.LENGTH_LONG)
            toast.setGravity(Gravity.CENTER_VERTICAL, 0, 0)
            toast.show()
        }
    }

    //  NumberCapturePresenter.View implementation
    override fun displayDetections(detections: Collection<Detection>) {
        if (!isHintDisplayed) {
            mOverlay.drawDetections(detections)
        }
    }

    override fun clearDetections() {
        mOverlay.drawDetections(null)
    }

    override fun displayNotFoundNumber(number: CarNumber) {
        if (!isHintDisplayed) {
            val message = String.format(getString(R.string.unknown_car_message_format), number.toString())
            showToast(message)
        }
    }

    override fun showCarDetails(car: CarDetails) {
        if (isHintDisplayed) {
            return
        }

        if (lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) {
            // We can start the activity
            val intent = CarDetailsActivity.newIntent(context!!, car)
            mPendingCarDetails = null
            startActivity(intent)
            mFlashIsOn = false

        } else {
            // Remember the details to show when we will be resumed
            mPendingCarDetails = car
        }
    }

    override fun displayPreviewError(error: Throwable) {
        showToast(getString(R.string.preview_error_message))
    }

    // ---------------------------------------------------------------------------------------------
    // TextureView.SurfaceTextureListener implementation
    override fun onSurfaceTextureSizeChanged(surface: SurfaceTexture?, width: Int, height: Int) {
        //transformSurface(width, height)
    }

    override fun onSurfaceTextureAvailable(surface: SurfaceTexture?, width: Int, height: Int) {
        // Don't start preview if we have pending details to show
        if (lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED) &&
                mPendingCarDetails == null) {
            startPreview()
        }
    }

    override fun onSurfaceTextureUpdated(surface: SurfaceTexture?) = Unit

    override fun onSurfaceTextureDestroyed(surface: SurfaceTexture?): Boolean = true

}
