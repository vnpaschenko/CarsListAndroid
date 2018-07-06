package com.example.ipaschenko.carslist

import android.app.Activity
import android.arch.lifecycle.Observer
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.support.v7.app.AppCompatActivity
import android.view.View
import android.widget.Button
import android.widget.TextView
import com.example.ipaschenko.carslist.data.DbProcessingState
import com.example.ipaschenko.carslist.data.DbStatus

class SettingsActivity : AppCompatActivity() {

    companion object {
        fun newIntent(context: Context): Intent =
                Intent(context, SettingsActivity::class.java)
        private const val SELECT_UPDATE_FILE_CODE = 706
    }

    private lateinit var progressLayout: View
    private lateinit var progressText: TextView
    private lateinit var dbStatusText: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)

        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = getString(R.string.settings_title)

        CarsListApplication.application.databaseStatus.observe(this,
                Observer { status: DbStatus? ->
            if (status != null) {
                processDbStatus(status)
            }
        })

        progressLayout = findViewById(R.id.progress_layout)
        progressText = findViewById(R.id.progress_text)
        dbStatusText = findViewById(R.id.database_status_text)

        val updateButton: Button = findViewById(R.id.update_database_button)
        updateButton.setOnClickListener {
            val intent = Intent()
            intent.type = "text/html"
            intent.action = Intent.ACTION_GET_CONTENT

            startActivityForResult(Intent.createChooser(intent, "Select a File to Open"),
                    SELECT_UPDATE_FILE_CODE)
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)

        if (requestCode == SELECT_UPDATE_FILE_CODE && resultCode == Activity.RESULT_OK) {
            val uri = data?.data
        }
    }

    private fun processDbStatus(status: DbStatus) {
        when (status.processingState) {
            DbProcessingState.INITIALIZING ->
                displayProgress(getString(R.string.initializing_db_message))
            DbProcessingState.UPDATING ->
                displayProgress(getString(R.string.updating_db_message))
            DbProcessingState.INITIALIZED, DbProcessingState.UPDATED -> {
                displayRecordsCount(status.availableRecordsCount)
                if (status.errorInfo != null && !status.errorInfo.handled) {
                    displayError(status.errorInfo.error)
                    status.errorInfo.handled = true;
                }
            }
        }
    }

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }

    private fun displayProgress(message: String) {
        progressText.text = message;
        progressLayout.visibility = View.VISIBLE
    }

    private fun displayRecordsCount(count: Int) {
        progressLayout.visibility = View.INVISIBLE

        dbStatusText.text = String.format(getString(R.string.db_status_message_format), count);
    }

    private fun displayError(error: Throwable) {

    }

}
