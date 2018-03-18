package com.example.ipaschenko.carslist.data

import android.content.Context
import com.example.ipaschenko.carslist.R


// Unsupported number excection. Currently custom numbers are no supported
class UnsupportedNumberException(val number: String): Exception()

// Database status
data class ErrorInfo(val error: Throwable, var handled: Boolean = false)
data class DbStatus(val isDataAvailable: Boolean, val errorInfo: ErrorInfo?)

/**
 * Format Db status error to human readable string
 */
fun formatDbStatusError(context: Context, error: Throwable?): String =
    when (error) {
        is UnsupportedNumberException ->
            String.format(context.getString(R.string.unsupported_number_message_format),
                    error.number)

        is CarsListCsvParser.ParseException->
            String.format(context.getString(R.string.data_parse_error_message_format),
                    error.lineNumber)

        else -> error?.localizedMessage ?: context.getString(R.string.unknown_error_message)
    }
