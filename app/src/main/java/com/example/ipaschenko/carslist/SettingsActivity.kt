package com.example.ipaschenko.carslist

import android.app.Activity
import android.app.AlertDialog
import android.app.Dialog
import android.app.DialogFragment
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
import com.example.ipaschenko.carslist.data.formatDbStatusError

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

        val versionText: TextView = findViewById(R.id.app_version_text)
        versionText.text = "${getString(R.string.app_name)} v${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})"
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)

        if (requestCode == SELECT_UPDATE_FILE_CODE && resultCode == Activity.RESULT_OK) {
            val uri = data?.data
            if (uri != null) {
                CarsListApplication.application.updateDatabase(uri)
            }
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
                    status.errorInfo.handled = true
                }
            }
        }
    }

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }

    private fun displayProgress(message: String) {
        progressText.text = message
        progressLayout.visibility = View.VISIBLE
    }

    private fun displayRecordsCount(count: Int) {
        progressLayout.visibility = View.INVISIBLE

        dbStatusText.text = String.format(getString(R.string.db_status_message_format), count)
    }

    private fun displayError(error: Throwable) {
        progressLayout.visibility = View.INVISIBLE

        if (fragmentManager.findFragmentByTag(ErrorDialogFragment.TAG) == null) {
            ErrorDialogFragment.newInstance(error).show(fragmentManager, ErrorDialogFragment.TAG)
        }
    }

    class ErrorDialogFragment: DialogFragment() {

        companion object {
           fun newInstance(error: Throwable): ErrorDialogFragment {
               val fragment = ErrorDialogFragment()
               val args = Bundle()
               args.putSerializable(ERROR_KEY, error)
               fragment.arguments = args
               return fragment;
           }
           private const val ERROR_KEY = "Error"
           const val TAG = "ErrorDialogFragment"
        }


        override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
            val builder = AlertDialog.Builder(activity!!)

            val error: Throwable? = arguments?.getSerializable(ERROR_KEY) as? Throwable
            val message = formatDbStatusError(activity, error)
            builder.setTitle(R.string.error_alert_title).setMessage(message)
                    .setPositiveButton(R.string.ok_button_title) {_,_->  }

            return builder.create()
        }
    }

}
