package com.vnp.vision.carslist

import android.support.v7.app.AppCompatActivity
import android.os.Bundle

class CarNumberCaptureActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_car_number_capture)

        supportFragmentManager.findFragmentById(R.id.capture_fragment_container) ?:
            supportFragmentManager.beginTransaction().replace(R.id.capture_fragment_container,
                    CarNumberCaptureFragment.newInstance()).commit()
    }
}
