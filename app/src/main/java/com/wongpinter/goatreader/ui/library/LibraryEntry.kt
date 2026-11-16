package com.wongpinter.goatreader.ui.library

import com.wongpinter.goatreader.data.model.Book
import com.wongpinter.goatreader.data.model.BookFormat
import com.wongpinter.goatreader.data.model.ReadingStatus
import com.wongpinter.goatreader.data.model.Shelf
import com.wongpinter.goatreader.data.model.bookOrderKey
import com.wongpinter.goatreader.data.model.shelfOrderKey
import com.wongpinter.goatreader.data.model.sortTs

/**
 * One slot in the library grid: either a loose book or a shelf. The screen gets
 * a single stream of these so it never has to splice two lists itself.
 */
sealed interface LibraryEntry {
    /**
     * UI identity — the LazyGrid key and the drag bounds-map key. Type-prefixed
     * so book ids and shelf ids can never collide, and so the key changes when
     * a book joins a shelf.
     */
    val id: String

    /** Position in the single descending stream shared by books and shelves. */
    val sortTs: Long

    data class BookEntry(val book: Book) : LibraryEntry {
        override val id: String get() = bookOrderKey(book.id)
        override val sortTs: Long get() = book.sortTs
    }

    data class ShelfEntry(val shelf: Shelf, val books: List<Book>) : LibraryEntry {
        override val id: String get() = shelfOrderKey(shelf.id)
        override val sortTs: Long get() = shelf.sortTs
    }
}

internal enum class LibraryBrowseSort { LIBRARY, TITLE, RECENTLY_ADDED }

internal enum class LibraryStatusFilter(val status: ReadingStatus?) {
    ALL(null), UNMARKED(ReadingStatus.NONE), WANT_TO_READ(ReadingStatus.WANT_TO_READ),
    READING(ReadingStatus.READING), FINISHED(ReadingStatus.FINISHED), ABANDONED(ReadingStatus.ABANDONED),
}

/** Filtering shows individual books, including those on shelves, without changing membership. */
internal fun filterLibraryBooks(
    books: List<Book>, query: String, filter: LibraryStatusFilter, format: BookFormat? = null,
): List<LibraryEntry> = searchBooks(books, query)
    .filter { (filter.status == null || it.status == filter.status) && (format == null || it.format == format) }
    .map { LibraryEntry.BookEntry(it) }

/** Sorts the visible entries without changing the underlying library or shelf order. */
internal fun sortLibraryEntries(entries: List<LibraryEntry>, sort: LibraryBrowseSort): List<LibraryEntry> = when (sort) {
    LibraryBrowseSort.LIBRARY -> entries
    LibraryBrowseSort.TITLE -> entries.sortedWith(
        compareBy<LibraryEntry, String>(String.CASE_INSENSITIVE_ORDER) {
            when (it) {
                is LibraryEntry.BookEntry -> it.book.title
                is LibraryEntry.ShelfEntry -> it.shelf.name
            }
        }.thenBy { it.id },
    )
    LibraryBrowseSort.RECENTLY_ADDED -> entries.sortedWith(
        compareByDescending<LibraryEntry> {
            when (it) {
                is LibraryEntry.BookEntry -> it.book.addedAtMillis
                is LibraryEntry.ShelfEntry -> it.shelf.createdAtMillis
            }
        }.thenBy { it.id },
    )
}

/**
 * Merges books and shelves into the grid order. Defensive by design: a member
 * id that no longer resolves is dropped rather than faked, so a torn read
 * (books updated, shelves not yet) can never hide a book.
 *
 * A shelf with one book, or none, is still a shelf — the user can make an empty
 * one and fill it later — so nothing here dissolves a shelf for being small.
 */
internal fun buildEntries(books: List<Book>, shelves: List<Shelf>): List<LibraryEntry> {
    if (shelves.isEmpty()) {
        // `books` already arrives sorted from the repository.
        return books.map { LibraryEntry.BookEntry(it) }
    }

    val byId = books.associateBy { it.id }
    val shelved = HashSet<String>()
    val shelfEntries = ArrayList<LibraryEntry>(shelves.size)

    for (shelf in shelves) {
        val members = shelf.bookIds.mapNotNull { byId[it] }.filter { it.id !in shelved }
        members.forEach { shelved += it.id }
        shelfEntries += LibraryEntry.ShelfEntry(shelf, members)
    }

    val loose: List<LibraryEntry> = books
        .filter { it.id !in shelved }
        .map { LibraryEntry.BookEntry(it) }

    // thenBy(id) matters: a book taken out of a shelf keeps its own timestamp
    // and can tie with the shelf's seeded sortKey, and TimSort stability only
    // preserves the incoming order, which is disk order.
    return (loose + shelfEntries).sortedWith(
        compareByDescending<LibraryEntry> { it.sortTs }.thenBy { it.id },
    )
}
