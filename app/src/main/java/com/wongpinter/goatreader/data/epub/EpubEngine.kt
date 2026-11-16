@file:OptIn(org.readium.r2.shared.ExperimentalReadiumApi::class, org.readium.r2.shared.InternalReadiumApi::class)

package com.wongpinter.goatreader.data.epub

import android.content.Context
import java.io.Closeable
import java.io.File
import java.io.IOException
import java.util.zip.ZipFile
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import org.readium.r2.navigator.epub.EpubNavigatorFactory
import org.readium.r2.shared.publication.Publication
import org.readium.r2.shared.publication.services.PositionsService
import org.readium.r2.shared.publication.services.isRestricted
import org.readium.r2.shared.publication.services.positions
import org.readium.r2.shared.publication.services.positionsServiceFactory
import org.readium.r2.shared.util.Try
import org.readium.r2.shared.util.asset.AssetRetriever
import org.readium.r2.shared.util.getOrElse
import org.readium.r2.shared.util.http.DefaultHttpClient
import org.readium.r2.shared.util.http.HttpError
import org.readium.r2.shared.util.http.HttpRequest
import org.readium.r2.shared.util.http.HttpTry
import org.readium.r2.streamer.PublicationOpener
import org.readium.r2.streamer.parser.epub.EpubParser as ReadiumEpubParser

/** Opens local EPUB files through Readium without materializing spine XHTML in Goat Reader. */
internal class EpubEngine(context: Context) {
    private val httpClient = DefaultHttpClient(
        callback = object : DefaultHttpClient.Callback {
            override suspend fun onStartRequest(request: HttpRequest): HttpTry<HttpRequest> =
                Try.failure(HttpError.IO(IOException("Remote EPUB resources are disabled")))
        },
    )
    private val assets = AssetRetriever(context.applicationContext.contentResolver, httpClient)

    suspend fun open(file: File): EpubSession {
        require(file.isFile) { "EPUB file is missing" }
        var prepared: EpubSession? = null
        try {
            return withContext(Dispatchers.IO) {
                openOnIo(file).also { prepared = it }
            }
        } catch (error: Throwable) {
            // A dispatcher handoff can lose a completed result if cancellation wins the race.
            prepared?.close()
            throw error
        }
    }

    private suspend fun openOnIo(file: File): EpubSession {
        val started = System.nanoTime()
        val fileBytes = file.length()
        // ZipFile reads the central directory only; entry count also catches tiny, chapter-heavy EPUBs.
        val archiveEntries = ZipFile(file).use(ZipFile::size)
        val positionBytes = EpubPositionPolicy.positionBytes(fileBytes, archiveEntries)
        val asset = assets.retrieve(file).getOrElse { throw IOException(it.toString()) }
        val assetReady = System.nanoTime()
        val opener = PublicationOpener(
            publicationParser = ReadiumEpubParser(
                httpClient,
                EpubPositionPolicy.strategy(fileBytes, archiveEntries),
            ),
            onCreatePublication = {
                val original = servicesBuilder.positionsServiceFactory
                if (original != null) {
                    servicesBuilder.positionsServiceFactory = { serviceContext ->
                        (original(serviceContext) as? PositionsService)?.let(::CachedEpubPositions)
                    }
                }
            },
        )
        var publication: Publication? = null
        try {
            publication = opener.open(asset, allowUserInteraction = false)
                .getOrElse { throw IOException(it.toString()) }
            currentCoroutineContext().ensureActive()
            if (!publication.conformsTo(Publication.Profile.EPUB) || publication.isRestricted) {
                throw IOException("Unsupported or restricted EPUB")
            }
            val publicationReady = System.nanoTime()
            // Readium 3.2's navigator requests positions during fragment creation; warm on IO first.
            val positionCount = publication.positions().size
            currentCoroutineContext().ensureActive()
            val positionsReady = System.nanoTime()
            return EpubSession(
                publication = publication,
                openMetrics = EpubOpenMetrics(
                    fileBytes = fileBytes,
                    archiveEntries = archiveEntries,
                    assetMs = (assetReady - started) / 1_000_000,
                    publicationMs = (publicationReady - assetReady) / 1_000_000,
                    positionsMs = (positionsReady - publicationReady) / 1_000_000,
                    sectionCount = publication.readingOrder.size,
                    positionCount = positionCount,
                    positionBytes = positionBytes,
                ),
            )
        } catch (error: Throwable) {
            if (publication != null) publication.close() else asset.close()
            throw error
        }
    }
}

internal data class EpubOpenMetrics(
    val fileBytes: Long,
    val archiveEntries: Int,
    val assetMs: Long,
    val publicationMs: Long,
    val positionsMs: Long,
    val sectionCount: Int,
    val positionCount: Int,
    val positionBytes: Int,
)

internal class EpubSession internal constructor(
    val publication: Publication,
    val openMetrics: EpubOpenMetrics,
) : Closeable {
    private val closed = AtomicBoolean(false)
    val tableOfContents = publication.tableOfContents

    // Readium's navigator repeatedly copies its TOC for UI use. Keep the original publication
    // available for TOC display and give the navigator a lightweight TOC-free view.
    private val navigatorPublication = Publication(
        manifest = publication.manifest.copy(tableOfContents = emptyList()),
        container = publication.container,
        servicesBuilder = Publication.ServicesBuilder(
            positions = { publication.findService(PositionsService::class) },
        ),
    )

    val navigatorFactory = EpubNavigatorFactory(navigatorPublication)

    override fun close() {
        if (closed.compareAndSet(false, true)) publication.close()
    }
}
