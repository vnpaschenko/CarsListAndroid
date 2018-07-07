package com.example.ipaschenko.carslist.data

class CarsListParsingException(errorCode: ErrorCode? = null, cause: Throwable? = null): Exception(cause)  {

    enum class ErrorCode {
        PARSING_ERROR,
        INCORRECT_STRUCTURE,
        EMPTY_DATA,
        INCORRECT_VALUE,
        UNKNOWN_ERROR
    }

    val errorCode = errorCode ?: ErrorCode.UNKNOWN_ERROR
}
