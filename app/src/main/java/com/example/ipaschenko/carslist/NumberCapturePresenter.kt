package com.example.ipaschenko.carslist

import java.lang.ref.WeakReference
import android.content.Context
import android.os.Handler
import android.os.SystemClock
import android.text.TextUtils
import android.util.Size
import com.example.ipaschenko.carslist.camera.CameraPreviewManager
import com.example.ipaschenko.carslist.data.*
import com.example.ipaschenko.carslist.utils.Cancellable
import com.example.ipaschenko.carslist.utils.canContinue
import com.google.android.gms.vision.Detector
import com.google.android.gms.vision.Frame
import com.google.android.gms.vision.text.TextBlock
import com.google.android.gms.vision.text.TextRecognizer
import java.nio.ByteBuffer
import java.util.*

/**
 * Presenter-style camera events listener. Handles camera preview, performs recognition and passes
 * results to the view
 */
class NumberCapturePresenter(context: Context, view: View):
        CameraPreviewManager.CameraPreviewManagerEventListener, Detector.Processor<TextBlock> {

    /** View interface **/
    interface View {
        /** Called when some text is detected to let view indicate it's area**/
        fun displayDetections(detections: Collection<Detection>)

        /** Called when detections should be cleared **/
        fun clearDetections()

        /** Called when car is recognized and view **/
        fun showCarDetails(car: CarDetails)

        /** Called when we likely recognize the car but it isn't present in the database **/
        fun displayNotFoundNumber(number: CarNumber)

        /** Called when the preview error did occur**/
        fun displayPreviewError(error: Throwable)
    }

    private val mViewRef = WeakReference(view)
    private val mRecognizer = TextRecognizer.Builder(context).build()
    private var mStartTime = SystemClock.elapsedRealtime()
    private var mFrameId = 0
    private var mCancellable: Cancellable? = null
    private val mHandler = Handler()
    private var mLastDetectionsCount = 0
    private var mDatabase: CarsDatabase? = null
    private var mIsRecognized = false

    init {
        mRecognizer.setProcessor(this)
    }

    // CameraPreviewManager.CameraPreviewManagerEventListener impl
    override fun onCameraPreviewObtained(imageData: ByteArray, imageSize: Size, imageFormat: Int,
                                         frameRotation: Int, cancellable: Cancellable) {

        mCancellable = cancellable
        if (!mCancellable.canContinue() || mIsRecognized || mViewRef.get() == null) {
            return
        }

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
        postToView { it.clearDetections(); it.displayPreviewError(error) }
    }

    override fun release() {
        if (!mCancellable.canContinue()) {
            return
        }
        mLastDetectionsCount = 0

        postToView { it.clearDetections() }
    }

    override fun receiveDetections(detections: Detector.Detections<TextBlock>?) {

        if (!mCancellable.canContinue() || mIsRecognized) {
            return
        }
        // Test only
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
        postToView {
            it.displayDetections(Collections.unmodifiableCollection(detectionsList))
        }

        for (detection in detectionsList) {
            if (processText(detection.text)) {
                break
            }
        }
    }

    private fun processText(text: String): Boolean {
        val number = CarNumber.fromString(text, false)
        if (number == null || number.isCustom) {
            return false
        }

        if (mDatabase == null) {
            mDatabase = CarsListApplication.application.getCarsListDatabase(mCancellable)
        }

        val dao = mDatabase?.carsDao() ?: return false

        val matches = dao.loadByNumberRoot(number.root)
        val car = getMostProperCar(matches, number)
        if (car != null) {
            mIsRecognized = true
            processCar(car)
            return true
        }

        processNotFoundNumber(number)
        return false
    }

    private fun processCar(car: CarInfo) {
        val carDetails = CarDetails(car)
        postToView{
            it.showCarDetails(carDetails)
        }
    }

    private fun postToView(what: (View) -> Unit) {
        // View should be notified in the main thread
        val cancellable = mCancellable
        mHandler.post {
            if (cancellable.canContinue()) {
                val view = mViewRef.get()
                if (view != null) {
                    what(view)
                }
            }
        }
    }

    private var mNotFoundNumber: CarNumber? = null
    private var mNotFoundNumberRecognitions = 0
    private var mNotfoundNumberStartTime = 0L

    private fun processNotFoundNumber(number: CarNumber) {
        // If we red the number that can't be found in database, report 'unknown number'
        // to the client

        if (mNotFoundNumber == null || mNotFoundNumber!!.root != number.root) {
            // Init new number
            mNotFoundNumber = number
            mNotFoundNumberRecognitions = 1
            mNotfoundNumberStartTime = System.currentTimeMillis()

        } else {
            // process existed one
            mNotFoundNumberRecognitions ++

            if (mNotFoundNumberRecognitions > 7 &&
                    System.currentTimeMillis() - mNotfoundNumberStartTime > 3000) {
                // It looks like user points the camera to this number

                postToView { it.displayNotFoundNumber(number) }
                mNotFoundNumber = null
                mNotFoundNumberRecognitions = 0
                mNotfoundNumberStartTime = 0
            }
        }
    }
}