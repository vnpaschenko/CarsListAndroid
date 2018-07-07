package com.vnp.vision.carslist.data

import android.support.test.InstrumentationRegistry
import android.support.test.runner.AndroidJUnit4
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class CarsListHtmlParserTest {
    @Test
    fun testParsing() {
        val context = InstrumentationRegistry.getTargetContext()
        val stream = context.assets.open("CarsList.csv")
        val parser = CarsListHtmlParser(stream)
    }
}