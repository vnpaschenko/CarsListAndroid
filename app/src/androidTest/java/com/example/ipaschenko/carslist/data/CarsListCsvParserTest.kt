package com.example.ipaschenko.carslist.data

import android.support.test.InstrumentationRegistry
import org.junit.Test

import org.junit.Assert.*

/**
 * Test parsing our assets
 */
class CarsListCsvParserTest {
    @Test
    fun testParsing() {
        val context = InstrumentationRegistry.getTargetContext()
        val stream = context.assets.open("CarsList.csv")
        val parser = CarsListCsvParser(stream)

        val list = parser.toList()
        assertNotNull(list)
    }

}