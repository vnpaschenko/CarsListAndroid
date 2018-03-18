package com.example.ipaschenko.carslist.data

import org.junit.Assert.*
import org.junit.Test

/**
 * CarNumber creation tests
 */
class CarNumberTest {
    @Test
    fun testCreation() {
        // Standard Ukrainian
        var str = "АИ 1234 се"
        var number = CarNumber.fromString(str, true)
        assertEquals(number!!.root, "1234")
        assertEquals(number.prefix!!, "A*")
        assertEquals(number.suffix!!, "CE")
        assertFalse(number.isCustom)

        // Unknown Euroean
        str = "123 JsTH"
        number = CarNumber.fromString(str, true)
        assertEquals(number!!.root, "123")
        assertNull(number.prefix)
        assertEquals(number.suffix!!, "JSTH")
        assertFalse(number.isCustom)

        // Custom
        str = "ВОВАН"
        number = CarNumber.fromString(str, true)
        assertEquals(number!!.root, "BOBAH")
        assertNull(number.prefix)
        assertNull(number.suffix)
        assertTrue(number.isCustom)

        // Custom with index
        str = "11 КОЛЯН"
        number = CarNumber.fromString(str, true)
        assertEquals(number!!.root, "KO**H")
        assertEquals(number.prefix, "11")
        assertNull(number.suffix)
        assertTrue(number.isCustom)

        // Some incorrect strings
        assertNull(CarNumber.fromString("12", true))
        assertNull(CarNumber.fromString("ad", true))
        assertNull(CarNumber.fromString("ad 12", true))
        assertNull(CarNumber.fromString("ad 123 fdrg 234 r", true))
    }

    @Test
    fun testPartsMatching() {
        var number = CarNumber.fromString("АИ 1234 се", true)
        var matches = number!!.matchWithParts("A#", "CE")
        assertEquals(matches, 7)

        number = CarNumber.fromString("saАA 1234 #еjg", true)
        matches = number!!.matchWithParts("AA", "CE")

        assertEquals(matches, 7)

        number = CarNumber.fromString("1234 cеjg", true)
        matches = number!!.matchWithParts("AA", "CE")
        assertEquals(matches, 4)

        number = CarNumber.fromString("AB 1234 cеjg", true)
        matches = number!!.matchWithParts("AA", "CE")
        assertEquals(matches, 4)

        number = CarNumber.fromString("AB 1234 xеjg", true)
        matches = number!!.matchWithParts("AA", "CE")
        assertEquals(matches, 0)

        number = CarNumber.fromString("A$ 1234", true)
        matches = number!!.matchWithParts("AA", "CE")
        assertEquals(matches, 3)

        number = CarNumber.fromString("1234", true)
        matches = number!!.matchWithParts("AA", "CE")
        assertEquals(matches, 0)

    }
}