package com.example.ipaschenko.carslist.views

import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.util.Size
import android.view.View
import com.example.ipaschenko.carslist.Detection

/**
 * View that displays preview overlay info
 */
class OverlayView @JvmOverloads constructor(
        context: Context,
        attrs: AttributeSet? = null,
        defStyle: Int = 0 ) : View(context, attrs, defStyle)  {

    var previewSize: Size = Size(0, 0)
        set(value) {
            field = value
            if (mDetections?.isNotEmpty() == true) postInvalidate()
        }

    var startPoint: Point = Point(0, 0)
        set(value) {
            field = value
            if (mDetections?.isNotEmpty() == true) postInvalidate()
        }

    private var mDetections: Collection<Detection>? = null
    private val mRectPaint = Paint()

    init {
        mRectPaint.color = Color.WHITE
        mRectPaint.style = Paint.Style.STROKE
        mRectPaint.strokeWidth = 4.0f
    }

    fun drawDetections(detections: Collection<Detection>?) {
        mDetections = detections
        postInvalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (previewSize.width <= 0 || previewSize.height <= 0 ||
                mDetections?.isNotEmpty() != true) {
            return
        }

        val scale = Math.max(canvas.width, canvas.height).toFloat() /
                Math.max(previewSize!!.width, previewSize!!.height).toFloat()

        for(detection in mDetections!!) {
            drawDetection(canvas, detection, scale)
        }
    }

    private fun drawDetection(canvas: Canvas, detection: Detection, scale: Float) {
        detection.boundingBox ?: return

        val rect = RectF(detection.boundingBox)
        // Scale rect and adjust it for start point
        rect.left = rect.left * scale + startPoint.x
        rect.top = rect.top * scale + startPoint.y
        rect.right = rect.right * scale + startPoint.x
        rect.bottom = rect.bottom * scale + startPoint.y

        canvas.drawRect(rect, mRectPaint)
    }

}