package com.example.ipaschenko.carslist.data

import android.content.Context
import com.example.ipaschenko.carslist.R


// Unsupported number excection. Currently custom numbers are no supported
class UnsupportedNumberException(val number: String): Exception()

// Database status
class ErrorInfo(val error: Throwable, var handled: Boolean = false)

enum class DbProcessingState {
    INITIALIZING,
    INITIALIZED,
    UPDATING,
    UPDATED
}

class DbStatus(val processingState: DbProcessingState, val availableRecordsCount: Int,
               val errorInfo: ErrorInfo?) {
    val isDataAvailable: Boolean
        get() = this.availableRecordsCount > 0
}

/**
 * Format Db status error to human readable string
 */
fun formatDbStatusError(context: Context, error: Throwable?): String {
    var message: String? = null

    if (error is CarsListParsingException) {
        when (error.errorCode) {
            CarsListParsingException.ErrorCode.PARSING_ERROR ->
                message = context.getString(R.string.data_parse_error_message)
            CarsListParsingException.ErrorCode.INCORRECT_STRUCTURE ->
                message = context.getString(R.string.unsupported_document_structure_message)
            CarsListParsingException.ErrorCode.EMPTY_DATA ->
                message = context.getString(R.string.data_is_empty_message)
            CarsListParsingException.ErrorCode.INCORRECT_VALUE ->
                message = context.getString(R.string.incorrect_value_message)
            else ->
                message = context.getString(R.string.unknown_error_message)
        }
    }


    return message ?: context.getString(R.string.unknown_error_message)
}
