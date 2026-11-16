package com.wongpinter.goatreader.data.epub

import org.junit.Assert.assertEquals
import org.junit.Test

class EpubPositionPolicyTest {
    @Test
    fun `small books keep fine position spacing`() {
        assertEquals(
            EpubPositionPolicy.DEFAULT_POSITION_BYTES,
            EpubPositionPolicy.positionBytes(1_000_000, 250),
        )
    }

    @Test
    fun `large files use sparse synthetic positions`() {
        assertEquals(
            EpubPositionPolicy.LARGE_POSITION_BYTES,
            EpubPositionPolicy.positionBytes(EpubPositionPolicy.LARGE_FILE_BYTES, 1),
        )
    }

    @Test
    fun `chapter dense archive uses sparse positions even when compressed file is small`() {
        assertEquals(
            EpubPositionPolicy.LARGE_POSITION_BYTES,
            EpubPositionPolicy.positionBytes(23_467_050, 7_218),
        )
    }

    @Test
    fun `many archive entries select sparser position spacing`() {
        assertEquals(
            EpubPositionPolicy.LARGE_POSITION_BYTES,
            EpubPositionPolicy.positionBytes(40_000_000, EpubPositionPolicy.LARGE_ARCHIVE_ENTRIES),
        )
    }
}
