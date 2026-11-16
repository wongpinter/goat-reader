package com.wongpinter.goatreader.data.epub

import org.readium.r2.streamer.parser.epub.EpubPositionsService

/** Synthetic position spacing; this does not alter chapter, TOC, or page semantics. */
internal object EpubPositionPolicy {
    const val LARGE_FILE_BYTES = 128L * 1024 * 1024
    const val LARGE_ARCHIVE_ENTRIES = 1_024
    const val DEFAULT_POSITION_BYTES = 1_024
    const val LARGE_POSITION_BYTES = 16 * 1_024

    fun positionBytes(fileBytes: Long, archiveEntries: Int): Int =
        if (fileBytes >= LARGE_FILE_BYTES || archiveEntries >= LARGE_ARCHIVE_ENTRIES) {
            LARGE_POSITION_BYTES
        } else {
            DEFAULT_POSITION_BYTES
        }

    fun strategy(fileBytes: Long, archiveEntries: Int): EpubPositionsService.ReflowableStrategy =
        EpubPositionsService.ReflowableStrategy.ArchiveEntryLength(
            positionBytes(fileBytes, archiveEntries),
        )
}
