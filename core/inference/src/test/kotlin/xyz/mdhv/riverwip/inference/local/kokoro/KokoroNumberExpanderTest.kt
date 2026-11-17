package xyz.mdhv.riverwip.inference.local.kokoro

import org.junit.Assert.assertEquals
import org.junit.Test

/** CHARACTERIZATION (current behaviour, not a reference-Kokoro oracle) of [KokoroNumberExpander]. */
class KokoroNumberExpanderTest {

    private fun check(expected: String, input: String) = assertEquals(input, expected, KokoroNumberExpander.expand(input))

    @Test fun cardinals() {
        check("zero", "0")
        check("seven", "7")
        check("nineteen", "19")
        check("twenty", "20")
        check("twenty-one", "21")
        check("one hundred", "100")
        check("one hundred one", "101")
        check("one thousand", "1000")
        check("one thousand two hundred thirty-four", "1,234")
        check("one million", "1000000")
    }

    @Test fun negativeDecimalPercent() {
        check("minus five", "-5")
        check("three point one four", "3.14")
        check("zero point five", ".5")
        check("fifty percent", "50%")
        check("twelve point five percent", "12.5%")
    }

    @Test fun dollars() {
        check("one dollar", "$1")
        check("five dollars", "$5")
        check("five dollars and fifty cents", "$5.50")
        check("five dollars and fifty cents", "$5.5")
        check("one dollar and one cent", "$1.01")
        check("five dollars", "$5.00")
        check("one thousand dollars", "$1,000")
    }

    @Test fun ordinals() {
        check("first", "1st")
        check("second", "2nd")
        check("third", "3rd")
        check("fourth", "4th")
        check("twelfth", "12th")
        check("twentieth", "20th")
        check("twenty-first", "21st")
        check("one hundred first", "101st")
    }

    @Test fun spellDigitsAndLongRuns() {
        assertEquals("one two three", KokoroNumberExpander.spellDigits("123"))
        // More than 15 digits is read digit by digit instead of grouped.
        assertEquals(
            "one two three four five six seven eight nine zero one two three four five six",
            KokoroNumberExpander.expand("1234567890123456"),
        )
    }
}
