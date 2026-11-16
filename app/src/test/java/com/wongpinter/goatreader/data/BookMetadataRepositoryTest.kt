package com.wongpinter.goatreader.data

import android.content.Context
import android.content.ContentResolver
import android.net.Uri
import java.io.ByteArrayOutputStream
import com.wongpinter.goatreader.data.model.*
import com.wongpinter.goatreader.data.metadata.CoverEdit
import com.wongpinter.goatreader.data.parser.BookParsers
import com.wongpinter.goatreader.ui.library.searchBooks
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.mockito.Mockito.*
import java.io.File

class BookMetadataRepositoryTest {
    @get:Rule val temp = TemporaryFolder()
    private fun context(): Context = mock(Context::class.java).also {
        `when`(it.filesDir).thenReturn(temp.root)
        `when`(it.cacheDir).thenReturn(File(temp.root, "cache").apply { mkdirs() })
    }
    private fun source(): File = File(temp.root, "books/original.fb2").apply {
        parentFile!!.mkdirs()
        writeText("""<FictionBook xmlns="http://www.gribuser.ru/xml/fictionbook/2.0"><description><title-info><book-title>Original</book-title><annotation><p>old-search-word</p></annotation><lang>en</lang></title-info></description><body><section><p>Unchanged text</p></section></body></FictionBook>""")
    }
    private fun book(file: File) = Book(
        "book", "Original", format = BookFormat.FB2, fileName = file.name, addedAtMillis = 42L,
        description = "old-search-word", language = "en", contentHash = ContentHash.of(file), sizeBytes = file.length(),
        progress = ReadingProgress(chapterIndex = 0, elementIndex = 0, fraction = .5f),
        quotes = listOf(Quote("quote", "Unchanged text", 0, 1, note = "Private note")),
        bookmarks = listOf(Bookmark("mark", 0, 0, "Unchanged text", 1)), rating = 4, review = "Private review",
    )

    @Test fun `file library search and cache update together while personal records stay byte identical`() = runTest {
        val ctx = context()
        val repository = BookRepository(ctx)
        val file = source()
        repository.addBookForRestore(book(file))
        val before = repository.books.value.single()
        val content = repository.loadContent(before)
        val userBytes = File(temp.root, "userdata.json").readBytes()
        val progressBytes = File(temp.root, "progress.json").readBytes()
        val draft = repository.editableMetadata(before.id).second.copy(title = "Edited title", description = "new-search-word", language = "uk")
        val saved = repository.saveBookMetadata(before.id, before.fileName, draft)
        assertNotEquals(before.fileName, saved.fileName)
        assertNotEquals(before.contentHash, saved.contentHash)
        val stored = repository.bookFileFor(saved)!!
        assertEquals(ContentHash.of(stored), saved.contentHash)
        assertEquals(stored.length(), saved.sizeBytes)
        assertEquals("new-search-word", BookParsers.parseMetadata(stored, BookFormat.FB2).description)
        val destination = mock(Uri::class.java)
        val resolver = mock(ContentResolver::class.java)
        val exported = ByteArrayOutputStream()
        `when`(ctx.contentResolver).thenReturn(resolver)
        `when`(resolver.openOutputStream(destination, "wt")).thenReturn(exported)
        repository.exportBook(saved.id, destination)
        assertArrayEquals(stored.readBytes(), exported.toByteArray())
        assertEquals(listOf(saved), searchBooks(repository.books.value, "new-search-word"))
        assertTrue(searchBooks(repository.books.value, "old-search-word").isEmpty())
        assertEquals(saved, BookRepository(ctx).books.value.single())
        assertArrayEquals(userBytes, File(temp.root, "userdata.json").readBytes())
        assertArrayEquals(progressBytes, File(temp.root, "progress.json").readBytes())
        assertEquals(before.quotes, saved.quotes)
        assertEquals(before.bookmarks, saved.bookmarks)
        assertEquals(before.progress, saved.progress)
        val reopened = repository.loadContent(saved)
        assertNotSame(content, reopened)
        assertEquals("uk", reopened.language)
        assertTrue("Previous index backup still has a readable book file", file.exists())
        assertEquals("Original", BookParsers.parseMetadata(file, BookFormat.FB2).title)
    }

    @Test fun `plain text details and cover stay in library while book bytes and progress remain unchanged`() = runTest {
        val ctx = context()
        val repository = BookRepository(ctx)
        val dir = File(temp.root, "books").apply { mkdirs() }
        listOf(BookFormat.TXT, BookFormat.MD).forEachIndexed { index, format ->
            val file = File(dir, "plain$index.${format.name.lowercase()}").apply { writeText("Original text $index") }
            val before = Book("plain$index", "Original", format = format, fileName = file.name,
                addedAtMillis = 42, progress = ReadingProgress(fraction = 0.5f))
            repository.addBookForRestore(before)
            val bytes = file.readBytes()
            val draft = repository.editableMetadata(before.id).second.copy(
                title = "Edited $index", authors = listOf("Author"), genres = listOf("Fiction"),
                description = "Searchable description", language = "en",
            )
            val cover = byteArrayOf(1, 2, 3)
            val saved = repository.saveBookMetadata(before.id, before.fileName, draft, CoverEdit.Replace(cover))
            assertArrayEquals(bytes, file.readBytes())
            assertEquals(before.fileName, saved.fileName)
            assertEquals(before.progress, saved.progress)
            assertEquals("Edited $index", saved.title)
            assertArrayEquals(cover, repository.coverFileFor(saved)!!.readBytes())
            assertEquals(draft, repository.editableMetadata(before.id).second)
            assertEquals(saved, BookRepository(ctx).books.value.first { it.id == before.id })
            val removed = repository.saveBookMetadata(before.id, before.fileName, draft, CoverEdit.Remove)
            assertNull(removed.coverFileName)
            assertArrayEquals(bytes, file.readBytes())
        }
    }

    @Test fun `stale plain text editor cannot overwrite newer metadata or cover`() = runTest {
        val repository = BookRepository(context())
        val file = File(temp.root, "books/plain.txt").apply { parentFile!!.mkdirs(); writeText("Text") }
        val before = Book("plain", "Original", format = BookFormat.TXT, fileName = file.name, addedAtMillis = 1)
        repository.addBookForRestore(before)
        val snapshot = repository.books.value.single()
        val draft = repository.editableMetadata(snapshot.id).second
        val saved = repository.saveBookMetadata(snapshot.id, snapshot.fileName, draft.copy(title = "New"),
            CoverEdit.Replace(byteArrayOf(1, 2, 3)), expectedBook = snapshot)
        val stale = runCatching {
            repository.saveBookMetadata(snapshot.id, snapshot.fileName, draft.copy(title = "Stale"),
                CoverEdit.Replace(byteArrayOf(4, 5)), expectedBook = snapshot)
        }
        assertTrue(stale.isFailure)
        assertEquals(saved, repository.books.value.single())
        assertArrayEquals(byteArrayOf(1, 2, 3), repository.coverFileFor(saved)!!.readBytes())
        assertEquals("Text", file.readText())
        assertEquals(1, File(temp.root, "covers").listFiles()!!.size)
    }

    @Test fun `failed index write leaves original file library and draft usable`() = runTest {
        val repository = BookRepository(context())
        val source = source()
        repository.addBookForRestore(book(source))
        val before = repository.books.value.single()
        val index = File(temp.root, "library.json").readBytes()
        File(temp.root, "library.json.tmp").mkdirs()
        File(temp.root, "library.json.tmp/block").writeText("force a write failure")
        val result = runCatching {
            repository.saveBookMetadata(before.id, before.fileName, repository.editableMetadata(before.id).second.copy(title = "Must not appear"))
        }
        assertTrue(result.isFailure)
        assertEquals(before, repository.books.value.single())
        assertArrayEquals(index, File(temp.root, "library.json").readBytes())
        assertEquals(listOf("original.fb2"), File(temp.root, "books").list()!!.toList())
        assertEquals("Original", BookParsers.parseMetadata(source, BookFormat.FB2).title)
    }

    @Test fun `stale editor cannot overwrite a newer save and clearing description removes search hits`() = runTest {
        val repository = BookRepository(context())
        repository.addBookForRestore(book(source()))
        val (before, draft) = repository.editableMetadata("book")
        val saved = repository.saveBookMetadata("book", before.fileName, draft.copy(description = ""), CoverEdit.Remove)
        assertNull(saved.description)
        assertTrue(searchBooks(repository.books.value, "old-search-word").isEmpty())
        assertTrue(runCatching { repository.saveBookMetadata("book", before.fileName, draft.copy(title = "Stale")) }.isFailure)
        assertEquals(saved, repository.books.value.single())
    }
}
