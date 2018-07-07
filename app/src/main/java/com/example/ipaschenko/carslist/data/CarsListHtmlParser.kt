package com.example.ipaschenko.carslist.data

import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import org.jsoup.select.Elements
import java.io.InputStream
import java.util.*

// Parser for Cars List in HTML format
// Constructor throws CarsListParsingException
class CarsListHtmlParser (inputStream: InputStream, charset: String = "utf8"): Iterable<CarInfo> {

    private val iterator = CarsIterator(inputStream, charset)

    override fun iterator(): Iterator<CarInfo> {
        return iterator;
    }

    private class CarsIterator(inputStream: InputStream, charset: String): Iterator<CarInfo> {
        private val tableRows: Elements
        private val rowsCount: Int
        private var currentRow: Int = 0
        init {
            var possibleError: CarsListParsingException.ErrorCode =
                    CarsListParsingException.ErrorCode.PARSING_ERROR

            try {
                // Parse the table
                val doc: Document = Jsoup.parse(inputStream, charset, "")

                // Read the table
                possibleError =  CarsListParsingException.ErrorCode.INCORRECT_STRUCTURE
                val tableBody = doc.select("table")[0].child(0);

                tableRows = tableBody.children()
                rowsCount = tableRows.size

                moveToNextRow(false)

            } catch (e: Throwable) {
                throw CarsListParsingException(possibleError, e)
            }

            if (currentRow >= rowsCount) {
                throw CarsListParsingException(CarsListParsingException.ErrorCode.EMPTY_DATA)
            }
        }

        private fun moveToNextRow(skipCurrent: Boolean) {

            if (skipCurrent) {
                currentRow ++
            }

            while (currentRow < rowsCount) {
                val columns = tableRows[currentRow].children()
                if (columns.size < 5 || columns[0].tagName() == "th") {
                    currentRow ++
                } else {
                    break
                }
            }
        }

        override fun hasNext(): Boolean = currentRow < rowsCount

        override fun next(): CarInfo {
            if (!hasNext()) {
                throw NoSuchElementException()
            }

            val columns = tableRows[currentRow].children()
            moveToNextRow(true)

            // Fill the Car Info
            val carInfo = CarInfo()
            try {
                val rawNumber = columns[4].textValue
                val number = CarNumber.fromString(rawNumber, true) ?:
                        throw NullPointerException()

                carInfo.number = rawNumber
                carInfo.numberRoot = number.root
                carInfo.numberPrefix = number.prefix
                carInfo.numberSuffix = number.suffix
                carInfo.numberAttributes = if (number.isCustom) NUMBER_ATTRIBUTE_CUSTOM else 0

                carInfo.modelName = columns[2].textValue
                carInfo.color = columns[3].textValue
                carInfo.owner = columns[0].textValue
                carInfo.phone = columns[1].textValue

            } catch (e: Throwable) {
                throw CarsListParsingException(CarsListParsingException.ErrorCode.INCORRECT_VALUE)
            }

            return carInfo
        }
    }
}

//** Private extensions for row fields **//
private val Element.textValue: String
    get() = this.text().trim()

