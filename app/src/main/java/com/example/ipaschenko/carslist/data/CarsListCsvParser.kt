package com.example.ipaschenko.carslist.data

import java.io.BufferedReader
import java.io.InputStream
import java.io.InputStreamReader
import java.util.NoSuchElementException

class CarsListCsvParser(inputStream: InputStream, delimiter: String = ","): Iterable<CarInfo> {

    class ParseException(val lineNumber: Int): Exception()

    private val mIterator = CarsIterator(inputStream, delimiter)

    override fun iterator(): Iterator<CarInfo> = mIterator

    private class CarsIterator(inputStream: InputStream, delimiter: String): Iterator<CarInfo> {
        private val mReader = BufferedReader(InputStreamReader(inputStream))
        private val mDelimiter = delimiter

        private var mCurrentRow: List<String>? = null
        private var mCurrentLineLineNumber = 0
        private var mNextRowFetched = false

        override fun hasNext(): Boolean {

            if (!mNextRowFetched) {

                if (mCurrentLineLineNumber == 0) {
                    mCurrentRow = readFirstRow()
                } else {
                    mCurrentRow = readNextRow()
                }

                mNextRowFetched = true
            }

            return mCurrentRow != null
        }

        override fun next(): CarInfo {
            if (!hasNext()) {
                throw NoSuchElementException()
            }

            if (!mCurrentRow.isValidCarInfo) {
                throw ParseException(mCurrentLineLineNumber)
            }

            // TODO: Parse number here

            val carInfo = CarInfo()
            carInfo.number = mCurrentRow!!.carNumber

//            carInfo.numberPrefix = parts.first
//            carInfo.numberRoot = parts.second
//            carInfo.numberSuffix = parts.third

            carInfo.modelName = mCurrentRow!!.carModelName
            carInfo.color = mCurrentRow!!.carColor
            carInfo.owner = mCurrentRow!!.carOwnerName
            carInfo.phone = mCurrentRow!!.carOwnerPhone

            mNextRowFetched = false

            return carInfo
        }

        private fun readFirstRow(): List<String>? {
            var row = readNextRow()
            if (!row.isValidCarInfo) {
                // Row is incorrect, return it to force next() throw
                return row
            }

            // We can have the header first
            if (row!!.carNumber.count { it.isDigit() } < 3) {
                row = readNextRow()
            }

            return row
        }

        private fun readNextRow(): List<String>? {
            var row: List<String>? = null
            while (true) {
                val csvLine = mReader.readLine()
                mCurrentLineLineNumber++
                csvLine ?: break
                if (csvLine.isBlank()) {
                    continue
                }

                row = csvLine.split(mDelimiter, ignoreCase = true)

                // Skip empty lines
                if (!row.any { it.isNotBlank() }) {
                    continue
                }

                if (row.isValidCarInfo) {
                    break
                }
            }

            return row
        }
    }
}

//** Private extensions for row fields **//
private val List<String>?.isValidCarInfo: Boolean
    get() = this?.size == 5

private val List<String>.carModelName: String
    get() = this[0]

private val List<String>.carColor: String
    get() = this[1]

private val List<String>.carNumber: String
    get() = this[2]

private val List<String>.carOwnerName: String
    get() = this[3]

private val List<String>.carOwnerPhone: String
    get() = this[4]