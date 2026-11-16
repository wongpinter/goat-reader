package com.wongpinter.goatreader.ui.library

import com.wongpinter.goatreader.data.model.Book
import com.wongpinter.goatreader.data.model.BookFormat
import com.wongpinter.goatreader.data.model.ReadingStatus
import com.wongpinter.goatreader.data.model.Shelf
import org.junit.Assert.assertEquals
import org.junit.Test

class LibraryBrowseSortTest {
    @Test fun `status filter combines with search and preserves source books`() {
        val reading = Book("r", "Dune", format = BookFormat.EPUB, addedAtMillis = 1, status = ReadingStatus.READING)
        val finished = Book("f", "Dune Messiah", format = BookFormat.EPUB, addedAtMillis = 2, status = ReadingStatus.FINISHED)
        val other = Book("o", "Other", format = BookFormat.TXT, addedAtMillis = 3, status = ReadingStatus.READING)
        val books = listOf(reading, finished, other)
        assertEquals(listOf("b:r"), filterLibraryBooks(books, "dune", LibraryStatusFilter.READING).map { it.id })
        assertEquals(listOf("b:f"), filterLibraryBooks(books, "", LibraryStatusFilter.FINISHED).map { it.id })
        assertEquals(listOf("b:o"), filterLibraryBooks(books, "", LibraryStatusFilter.READING, BookFormat.TXT).map { it.id })
        assertEquals(emptyList<String>(), filterLibraryBooks(books, "dune", LibraryStatusFilter.READING, BookFormat.TXT).map { it.id })
        assertEquals(listOf("b:r", "b:f"), filterLibraryBooks(books, "", LibraryStatusFilter.ALL, BookFormat.EPUB).map { it.id })
        assertEquals(listOf(reading, finished, other), books)
        assertEquals(emptyList<String>(), filterLibraryBooks(books, "missing", LibraryStatusFilter.READING).map { it.id })
    }

    @Test fun `sorting changes presentation without changing entries or shelf members`() {
        val z = Book("z", "Zebra", format = BookFormat.EPUB, addedAtMillis = 5)
        val a = Book("a", "alpha", format = BookFormat.TXT, addedAtMillis = 8)
        val shelf = LibraryEntry.ShelfEntry(Shelf("s", "Books", listOf("z"), createdAtMillis = 3), listOf(z))
        val input = listOf(LibraryEntry.BookEntry(z), shelf, LibraryEntry.BookEntry(a))
        assertEquals(input, sortLibraryEntries(input, LibraryBrowseSort.LIBRARY))
        assertEquals(listOf("b:a", "s:s", "b:z"), sortLibraryEntries(input, LibraryBrowseSort.TITLE).map { it.id })
        assertEquals(listOf("b:a", "b:z", "s:s"), sortLibraryEntries(input, LibraryBrowseSort.RECENTLY_ADDED).map { it.id })
        assertEquals(listOf(z), shelf.books)
    }
}
