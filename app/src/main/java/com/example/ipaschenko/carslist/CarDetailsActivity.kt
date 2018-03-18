package com.example.ipaschenko.carslist

import android.content.Context
import android.content.Intent
import android.support.v7.app.AppCompatActivity
import android.os.Bundle
import com.example.ipaschenko.carslist.data.CarDetails

class CarDetailsActivity : AppCompatActivity() {

    companion object {
        fun newIntent(context: Context, carDetails: CarDetails): Intent =
            Intent(context, CarDetailsActivity::class.java).apply {
                putExtra(CAR_DETAILS_KEY, carDetails)
            }

        private const val CAR_DETAILS_KEY = "CarDetails"
    }

    lateinit var mCarDetails: CarDetails

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_car_details)

        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        mCarDetails = intent.getParcelableExtra(CAR_DETAILS_KEY)
    }

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }
}
