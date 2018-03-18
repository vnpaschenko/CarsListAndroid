package com.example.ipaschenko.carslist

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.support.v7.app.AppCompatActivity
import android.os.Bundle
import android.support.v4.content.ContextCompat
import android.view.View
import android.widget.TextView
import android.widget.Toast
import com.example.ipaschenko.carslist.data.CarDetails
import com.example.ipaschenko.carslist.views.applyRoundOutline

class CarDetailsActivity : AppCompatActivity() {

    companion object {
        fun newIntent(context: Context, carDetails: CarDetails): Intent =
            Intent(context, CarDetailsActivity::class.java).apply {
                putExtra(CAR_DETAILS_KEY, carDetails)
            }

        private const val CAR_DETAILS_KEY = "CarDetails"
        private const val PHONE_PERMISSION_REQUEST = 2019
    }

    lateinit var mCarDetails: CarDetails

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_car_details)

        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = getString(R.string.found_car_title)

        val details: CarDetails = intent.getParcelableExtra(CAR_DETAILS_KEY)
        mCarDetails = details

        var model = details.modelName
        val color = details.color
        if (!color.isBlank()) {
            model = "${Character.toUpperCase(color[0])}${color.substring(1)} $model"

        }

        findViewById<TextView>(R.id.owner_name)!!.text = details.owner
        findViewById<TextView>(R.id.car_model)!!.text = model
        findViewById<TextView>(R.id.car_number)!!.text = details.number

        val callButton = findViewById<View>(R.id.call_owner)!!
        callButton.applyRoundOutline()

        callButton.setOnClickListener {
            callOwner()
        }
    }

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>,
            grantResults: IntArray) {

        if (requestCode == PHONE_PERMISSION_REQUEST) {
            if (grantResults.size != 1 || grantResults[0] != PackageManager.PERMISSION_GRANTED) {
                Toast.makeText(this, R.string.phone_no_permission, Toast.LENGTH_LONG).show()
            } else {
                performOwnerCall()
            }
        } else {
            super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        }
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
    }

    private fun callOwner() {

        var hasPermission = true
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val permission = ContextCompat.checkSelfPermission(this,
                    Manifest.permission.CALL_PHONE)
            if (permission != PackageManager.PERMISSION_GRANTED) {
                requestPermissions(arrayOf(Manifest.permission.CALL_PHONE), PHONE_PERMISSION_REQUEST)
                hasPermission = false
            }
        }

        if (hasPermission) {
            performOwnerCall()
        }

    }

    private fun performOwnerCall() {
        val phoneUri = phoneUri()
        if (phoneUri == null) {
            val message = String.format(getString(R.string.phone_incorrect_format), mCarDetails.owner)
            Toast.makeText(this, message, Toast.LENGTH_LONG).show()
            return
        }

        val callIntent = Intent(Intent.ACTION_CALL)
        callIntent.data = phoneUri
        try {
            startActivity(callIntent)
        } catch (se: SecurityException) {
            Toast.makeText(this, R.string.phone_no_permission, Toast.LENGTH_SHORT).show()
        } catch (e: Throwable) {
            Toast.makeText(this, R.string.phone_call_failed, Toast.LENGTH_LONG).show()
        }
    }

    private fun phoneUri(): Uri? {
        if (mCarDetails.phone.isBlank()) {
            return null
        }
        // TODO: Should we normailze phone?

        return Uri.parse("tel:${mCarDetails.phone}")
    }
}
