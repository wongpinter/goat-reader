package com.wongpinter.goatreader.reader

import androidx.compose.ui.text.AnnotatedString
import androidx.lifecycle.viewModelScope
import com.wongpinter.goatreader.data.AppSettings
import com.wongpinter.goatreader.data.BookRepository
import com.wongpinter.goatreader.data.ReaderSettings
import com.wongpinter.goatreader.data.SettingsRepository
import com.wongpinter.goatreader.data.StatsRepository
import com.wongpinter.goatreader.data.model.Book
import com.wongpinter.goatreader.data.model.ContentElement
import com.wongpinter.goatreader.data.model.ReadingLogEntry
import com.wongpinter.goatreader.ui.reader.BookPage
import com.wongpinter.goatreader.ui.reader.PagePart
import com.wongpinter.goatreader.ui.reader.PaginationCache
import com.wongpinter.goatreader.ui.reader.ReaderItem
import com.wongpinter.goatreader.ui.reader.ReaderViewModel
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withContext
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.mockito.BDDMockito.given
import org.mockito.Mockito.mock

@OptIn(ExperimentalCoroutinesApi::class)
class ReaderOpeningPaginationTest {
    @get:Rule val temporary = TemporaryFolder()
    private val dispatcher = StandardTestDispatcher()
    private val models = mutableListOf<ReaderViewModel>()
    private lateinit var repository: BookRepository
    private lateinit var preferences: SettingsRepository
    private val settings = ReaderSettings()
    private val items = List(4) { index ->
        ReaderItem(index / 2, ContentElement.Paragraph(AnnotatedString("Paragraph $index")))
    }

    @Before fun setup() {
        Dispatchers.setMain(dispatcher)
        repository = mock(BookRepository::class.java)
        given(repository.books).willReturn(MutableStateFlow<List<Book>>(emptyList()))
        given(repository.readingLog).willReturn(MutableStateFlow<List<ReadingLogEntry>>(emptyList()))
        preferences = mock(SettingsRepository::class.java)
        given(preferences.appSettings).willReturn(MutableStateFlow(AppSettings()))
    }

    @After fun cleanup() = runTest(dispatcher) {
        try {
            val jobs = models.mapNotNull { it.viewModelScope.coroutineContext[Job] }
            jobs.forEach { it.cancel() }
            jobs.joinAll()
        } finally {
            Dispatchers.resetMain()
        }
    }

    private fun model() = ReaderViewModel(
        repository, preferences, mock(StatsRepository::class.java), "large-book", temporary.root,
    ).also { models += it }

    private fun pages(vararg indices: Int) = indices.map { index ->
        val element = items[index].element as ContentElement.Paragraph
        BookPage(
            parts = listOf(PagePart(index, element, element.text, charStart = 0, charEnd = element.text.length)),
            firstItemIndex = index,
        )
    }

    @Test fun `windowed pagination keeps only its bounded pages and skips the whole-book pass`() = runTest(dispatcher) {
        val reader = model()
        var fullPassCalled = false

        reader.ensurePagination(
            key = "window-0",
            settings = settings,
            items = items,
            quick = { pages(0, 1) },
            quickRange = 0..1,
            windowed = true,
            full = { fullPassCalled = true; error("window mode must not paginate the whole book") },
        )

        val firstWindow = reader.pagination.first { it?.key == "window-0" }!!
        assertTrue(firstWindow.windowed)
        assertTrue(firstWindow.partial)
        assertEquals(0..1, firstWindow.itemRange)
        runCurrent()
        assertFalse(fullPassCalled)

        reader.ensurePagination(
            key = "window-final",
            settings = settings,
            items = items,
            quick = { pages(2, 3) },
            quickRange = 2..3,
            windowed = true,
            full = { fullPassCalled = true; error("window mode must not paginate the whole book") },
        )
        val finalWindow = reader.pagination.first { it?.key == "window-final" }!!
        assertTrue(finalWindow.windowed)
        assertFalse(finalWindow.partial)
        assertEquals(2..3, finalWindow.itemRange)
        runCurrent()
        assertFalse(fullPassCalled)
    }

    @Test fun `first opening publishes readable partial pages before the full pass finishes`() = runTest(dispatcher) {
        val reader = model()
        val quickPages = pages(2, 3)
        val allPages = pages(0, 1, 2, 3)
        val fullStarted = CompletableDeferred<Job>()
        val releaseFull = CompletableDeferred<Unit>()

        reader.ensurePagination("layout", settings, items,
            quick = { quickPages }, quickRange = 2..3,
            full = {
                fullStarted.complete(currentCoroutineContext()[Job]!!)
                releaseFull.await()
                allPages
            },
        )

        val paginationJob = fullStarted.await()
        val partial = reader.pagination.value!!
        assertTrue(partial.partial)
        assertSame(quickPages, partial.pages)
        assertEquals(2..3, partial.itemRange)
        assertNull(PaginationCache.load(PaginationCache.fileFor(temporary.root, "large-book"), "layout", items))

        releaseFull.complete(Unit)
        paginationJob.join()
        val complete = reader.pagination.value!!
        assertFalse(complete.partial)
        assertSame(allPages, complete.pages)
        assertNull(complete.itemRange)
        assertEquals(4, PaginationCache.load(PaginationCache.fileFor(temporary.root, "large-book"), "layout", items)!!.size)
    }

    @Test fun `cached full pagination loads after the preview and skips whole book measurement`() = runTest(dispatcher) {
        val cachedPages = pages(0, 1, 2, 3)
        PaginationCache.save(PaginationCache.fileFor(temporary.root, "large-book"), "layout", items, cachedPages)
        val reader = model()
        val previewMeasured = CompletableDeferred<Unit>()

        reader.ensurePagination("layout", settings, items,
            quick = { previewMeasured.complete(Unit); pages(2, 3) }, quickRange = 2..3,
            full = { error("A valid cache must skip full pagination") },
        )

        previewMeasured.await()
        val complete = reader.pagination.first { it?.key == "layout" && !it.partial }!!
        assertFalse(complete.partial)
        assertNull(complete.itemRange)
        assertEquals(listOf(0, 1, 2, 3), complete.pages.map { it.firstItemIndex })
    }

    @Test fun `background work waits until the current preview has reached a frame`() = runTest(dispatcher) {
        val reader = model()
        val started = CompletableDeferred<Job>()
        val finish = CompletableDeferred<Unit>()
        reader.ensurePagination("layout", settings, items,
            quick = { pages(2, 3) }, quickRange = 2..3, awaitPreviewPresentation = true,
            full = {
                started.complete(currentCoroutineContext()[Job]!!)
                finish.await()
                pages(0, 1, 2, 3)
            },
        )
        val preview = reader.pagination.first { it?.partial == true }!!
        runCurrent()
        assertFalse(started.isCompleted)
        // A disposed/stale pager cannot acknowledge the newer holder.
        reader.onPaginationPresented(ReaderViewModel.PaginationHolder("layout", pages(2), settings, partial = true))
        runCurrent()
        assertFalse(started.isCompleted)
        reader.onPaginationPresented(preview)
        val job = started.await()
        finish.complete(Unit)
        job.join()
        assertFalse(reader.pagination.value!!.partial)
    }

    @Test fun `an empty quick result does not replace loading with an empty page holder`() = runTest(dispatcher) {
        val reader = model()
        val fullStarted = CompletableDeferred<Job>()
        val releaseFull = CompletableDeferred<Unit>()
        val allPages = pages(0, 1, 2, 3)

        reader.ensurePagination("layout", settings, items,
            quick = { emptyList() }, quickRange = 2..3,
            full = {
                fullStarted.complete(currentCoroutineContext()[Job]!!)
                releaseFull.await()
                allPages
            },
        )

        val paginationJob = fullStarted.await()
        assertNull(reader.pagination.value)
        releaseFull.complete(Unit)
        paginationJob.join()
        assertSame(allPages, reader.pagination.value!!.pages)
        assertFalse(reader.pagination.value!!.partial)
    }

    @Test fun `a presented prefix expands beyond quick pages while remaining incomplete`() = runTest(dispatcher) {
        val reader = model()
        val started = CompletableDeferred<Job>()
        val finish = CompletableDeferred<Unit>()
        val allPrefixPages = pages(0, 1, 2, 3)
        reader.ensurePagination("prefix", settings, items,
            quick = { pages(0) }, quickRange = 0..3,
            awaitPreviewPresentation = true, contentComplete = false,
            full = {
                started.complete(currentCoroutineContext()[Job]!!)
                finish.await()
                allPrefixPages
            },
        )
        val first = reader.pagination.first { it != null }!!
        assertEquals(1, first.pages.size)
        reader.onPaginationPresented(first)
        val job = started.await()
        finish.complete(Unit)
        job.join()
        val expanded = reader.pagination.value!!
        assertTrue(expanded.partial)
        assertEquals(allPrefixPages.dropLast(1), expanded.pages)
        assertEquals(items.indices, expanded.itemRange)
        assertFalse(PaginationCache.fileFor(temporary.root, "large-book").exists())
    }

    @Test fun `cancelled older full computation cannot overwrite a newer layout`() = runTest(dispatcher) {
        val reader = model()
        val oldStarted = CompletableDeferred<Job>()
        val releaseOld = CompletableDeferred<Unit>()
        val newerPages = pages(0, 2)

        reader.ensurePagination("old-layout", settings, items,
            full = {
                oldStarted.complete(currentCoroutineContext()[Job]!!)
                // Model a measurement operation that finishes after cancellation.
                withContext(NonCancellable) { releaseOld.await() }
                pages(0, 1, 2, 3)
            },
        )
        val olderJob = oldStarted.await()
        try {
            reader.ensurePagination("new-layout", settings.copy(fontSizeSp = 25f), items,
                full = { newerPages },
            )
            val newer = reader.pagination.first { it?.key == "new-layout" }!!
            assertSame(newerPages, newer.pages)

            releaseOld.complete(Unit)
            olderJob.join()
            runCurrent()
            assertSame(newer, reader.pagination.value)
        } finally {
            releaseOld.complete(Unit)
        }
    }
}
