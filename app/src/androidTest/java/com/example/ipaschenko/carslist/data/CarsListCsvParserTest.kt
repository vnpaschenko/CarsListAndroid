package com.example.ipaschenko.carslist.data

import android.support.test.InstrumentationRegistry
import android.support.test.runner.AndroidJUnit4
import org.junit.Test

import org.junit.Assert.*
import org.junit.runner.RunWith

/**
 * Test parsing our assets
 */
@RunWith(AndroidJUnit4::class)
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