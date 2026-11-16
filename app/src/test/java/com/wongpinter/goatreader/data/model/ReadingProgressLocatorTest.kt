package com.wongpinter.goatreader.data.model

import kotlinx.serialization.json.Json
import kotlinx.serialization.encodeToString
import kotlinx.serialization.decodeFromString
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ReadingProgressLocatorTest {
    @Test
    fun `old progress records without a Readium locator still decode`() {
        val progress = Json.decodeFromString<ReadingProgress>("""{"chapterIndex":7,"fraction":0.25}""")

        assertEquals(7, progress.chapterIndex)
        assertEquals(0.25f, progress.fraction)
        assertNull(progress.epubLocatorJson)
    }

    @Test
    fun `Readium locator persists with existing progress`() {
        val expected = ReadingProgress(chapterIndex = 7207, fraction = 0.99f, epubLocatorJson = "{\"href\":\"c.xhtml\"}")

        val decoded = Json.decodeFromString<ReadingProgress>(Json.encodeToString(expected))

        assertEquals(expected, decoded)
    }
}
