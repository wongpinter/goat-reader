package com.wongpinter.goatreader.data.parser

import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory
import java.util.concurrent.CancellationException
import java.util.zip.ZipEntry
import java.util.zip.ZipFile

/**
 * Grants an entry-count extension only to dense, large-spine EPUB packages.
 * The central directory, container and OPF remain independently bounded.
 */
internal object EpubArchiveEntryPreflight {
    private const val CONTAINER_PATH = "META-INF/container.xml"
    private const val MAX_CONTAINER_BYTES = 64L * 1024
    private const val MAX_ATTRIBUTE_CHARS = 4_096
    private val URI_SCHEME = Regex("^([A-Za-z][A-Za-z0-9+.-]*):")
    private val PROPERTY_WHITESPACE = Regex("\\s+")

    private data class PackageShape(
        val manifestItems: Int,
        val spineItems: Int,
        val manifestHrefs: Set<String>,
        val navigationHrefs: Set<String>,
    )

    /** Returns the package-derived cap, or null when the large profile is unverified. */
    fun dynamicEntryLimit(zip: ZipFile, limits: ReaderResourceLimits): Int? {
        val maxPreflightBytes = minOf(MAX_CONTAINER_BYTES, limits.maxPackageXmlBytes) +
            limits.maxPackageXmlBytes
        if (maxPreflightBytes > limits.maxArchiveReadBytes) return null

        val entryCount = zip.size()
        if (entryCount <= limits.maxArchiveEntries ||
            entryCount > limits.maxLargeEpubArchiveEntries ||
            !centralDirectoryWithinLimits(zip, limits)
        ) {
            return null
        }

        val container = uniqueEntry(zip, CONTAINER_PATH) ?: return null
        val opfPath = readRootfilePath(zip, container, limits) ?: return null
        if (!isSafeArchivePath(opfPath)) return null
        val packageEntry = uniqueEntry(zip, opfPath) ?: return null
        val shape = readPackageShape(zip, packageEntry, limits) ?: return null
        val opfDirectory = opfPath.substringBeforeLast('/', "")
        for (href in shape.manifestHrefs) {
            val rawPath = href.substringBefore('#').substringBefore('?')
            val isNavigation = href in shape.navigationHrefs
            val scheme = URI_SCHEME.find(rawPath)?.groupValues?.get(1)
            if (scheme != null) {
                if (!isNavigation && (scheme.equals("http", ignoreCase = true) ||
                    scheme.equals("https", ignoreCase = true))
                ) {
                    continue
                }
                return null
            }
            if (rawPath.startsWith('/') || '\\' in rawPath || '\u0000' in rawPath) return null
            val resolved = EpubParser.resolvePath(opfDirectory, href)
            if (!isSafeArchivePath(resolved)) return null
            val resource = zip.getEntry(resolved) ?: return null
            if (resource.isDirectory) return null
            if (isNavigation && resource.size > limits.maxPackageXmlBytes) return null
        }

        if (shape.spineItems < limits.minLargeEpubSpineItems ||
            shape.manifestItems < shape.spineItems
        ) {
            return null
        }

        val packageDerivedCap = (
            shape.manifestItems.toLong() + limits.maxLargeEpubUnmanifestedEntries
        ).coerceAtMost(limits.maxLargeEpubArchiveEntries.toLong()).toInt()
        return packageDerivedCap.takeIf { entryCount <= it }
    }

    private fun centralDirectoryWithinLimits(zip: ZipFile, limits: ReaderResourceLimits): Boolean {
        var declaredBytes = 0L
        val entries = zip.entries()
        while (entries.hasMoreElements()) {
            val entry = entries.nextElement()
            val size = entry.size
            val compressedSize = entry.compressedSize
            if (size < 0L || compressedSize < 0L ||
                size > limits.maxLargeEpubEntryBytes ||
                size > limits.maxArchiveDeclaredBytes - declaredBytes
            ) {
                return false
            }
            declaredBytes += size
            if (size >= limits.compressionRatioCheckFromBytes) {
                val maxExpanded = if (compressedSize > Long.MAX_VALUE / limits.maxCompressionRatio) {
                    Long.MAX_VALUE
                } else {
                    maxOf(1L, compressedSize) * limits.maxCompressionRatio
                }
                if (size > maxExpanded) return false
            }
        }
        return true
    }

    private fun uniqueEntry(zip: ZipFile, path: String): ZipEntry? {
        var result: ZipEntry? = null
        val entries = zip.entries()
        while (entries.hasMoreElements()) {
            val entry = entries.nextElement()
            if (entry.isDirectory || entry.name != path) continue
            if (result != null) return null
            result = entry
        }
        return result
    }

    private fun readRootfilePath(
        zip: ZipFile,
        entry: ZipEntry,
        limits: ReaderResourceLimits,
    ): String? = parseXmlEntry(
        zip = zip,
        entry = entry,
        maxBytes = minOf(MAX_CONTAINER_BYTES, limits.maxPackageXmlBytes),
        limits = limits,
        label = "EPUB container preflight",
    ) { parser ->
        var hasContainerRoot = false
        var rootfilePath: String? = null
        while (parser.eventType != XmlPullParser.END_DOCUMENT) {
            if (parser.eventType == XmlPullParser.DOCDECL) return@parseXmlEntry null
            if (parser.eventType == XmlPullParser.START_TAG) {
                if (parser.depth == 1 && parser.name == "container") hasContainerRoot = true
                if (parser.name == "rootfile" && rootfilePath == null) {
                    rootfilePath = parser.getAttributeValue(null, "full-path")
                }
            }
            parser.nextToken()
        }
        rootfilePath?.takeIf { hasContainerRoot && it.isNotBlank() }
    }

    private fun readPackageShape(
        zip: ZipFile,
        entry: ZipEntry,
        limits: ReaderResourceLimits,
    ): PackageShape? = parseXmlEntry(
        zip = zip,
        entry = entry,
        maxBytes = limits.maxPackageXmlBytes,
        limits = limits,
        label = "EPUB package preflight",
    ) { parser ->
        var hasPackageRoot = false
        var manifestDepth = -1
        var spineDepth = -1
        var manifestItems = 0
        var spineItems = 0
        val manifestIds = HashSet<String>()
        val manifestHrefs = HashSet<String>()
        val navigationHrefs = HashSet<String>()
        val spineRefs = ArrayList<String>()

        while (parser.eventType != XmlPullParser.END_DOCUMENT) {
            if (parser.eventType == XmlPullParser.DOCDECL) return@parseXmlEntry null
            when (parser.eventType) {
                XmlPullParser.START_TAG -> when {
                    parser.depth == 1 && parser.name == "package" -> hasPackageRoot = true
                    parser.depth == 2 && parser.name == "manifest" -> {
                        if (manifestDepth != -1) return@parseXmlEntry null
                        manifestDepth = parser.depth
                    }
                    parser.depth == 2 && parser.name == "spine" -> {
                        if (spineDepth != -1) return@parseXmlEntry null
                        spineDepth = parser.depth
                    }
                    manifestDepth != -1 && parser.depth == manifestDepth + 1 &&
                        parser.name == "item" -> {
                        val id = parser.getAttributeValue(null, "id")
                            ?.takeIf { it.isNotBlank() && it.length <= MAX_ATTRIBUTE_CHARS }
                            ?: return@parseXmlEntry null
                        val href = parser.getAttributeValue(null, "href")
                            ?.takeIf { it.isNotBlank() && it.length <= MAX_ATTRIBUTE_CHARS }
                            ?: return@parseXmlEntry null
                        val mediaType = parser.getAttributeValue(null, "media-type").orEmpty()
                        val properties = parser.getAttributeValue(null, "properties").orEmpty()
                        if (mediaType.length > 128 || properties.length > MAX_ATTRIBUTE_CHARS) {
                            return@parseXmlEntry null
                        }
                        if (!manifestIds.add(id) || !manifestHrefs.add(href)) {
                            return@parseXmlEntry null
                        }
                        val isNavigation = mediaType.equals(
                            "application/x-dtbncx+xml",
                            ignoreCase = true,
                        ) || properties.isNotEmpty() &&
                            properties.split(PROPERTY_WHITESPACE).any { it == "nav" }
                        if (isNavigation) {
                            navigationHrefs += href
                            if (navigationHrefs.size > limits.maxLargeEpubNavigationResources) {
                                return@parseXmlEntry null
                            }
                        }
                        manifestItems++
                        if (manifestItems > limits.maxLargeEpubArchiveEntries) {
                            return@parseXmlEntry null
                        }
                    }
                    spineDepth != -1 && parser.depth == spineDepth + 1 &&
                        parser.name == "itemref" -> {
                        val idref = parser.getAttributeValue(null, "idref")
                            ?.takeIf { it.isNotBlank() && it.length <= MAX_ATTRIBUTE_CHARS }
                            ?: return@parseXmlEntry null
                        spineRefs += idref
                        spineItems++
                        if (spineItems > limits.maxLargeEpubArchiveEntries) {
                            return@parseXmlEntry null
                        }
                    }
                }

                XmlPullParser.END_TAG -> {
                    if (parser.depth == manifestDepth && parser.name == "manifest") {
                        manifestDepth = -1
                    }
                    if (parser.depth == spineDepth && parser.name == "spine") {
                        spineDepth = -1
                    }
                }
            }
            parser.nextToken()
        }

        if (!hasPackageRoot || manifestItems == 0 || spineItems == 0 ||
            spineRefs.any { it !in manifestIds }
        ) {
            null
        } else {
            PackageShape(manifestItems, spineItems, manifestHrefs, navigationHrefs)
        }
    }

    private inline fun <T> parseXmlEntry(
        zip: ZipFile,
        entry: ZipEntry,
        maxBytes: Long,
        limits: ReaderResourceLimits,
        label: String,
        parse: (XmlPullParser) -> T,
    ): T? {
        if (entry.size < 0L || entry.size > maxBytes || !withinCompressionRatio(entry, limits)) {
            return null
        }
        return try {
            zip.getInputStream(entry).use { raw ->
                val parser = XmlPullParserFactory.newInstance()
                    .apply { isNamespaceAware = true }
                    .newPullParser()
                parser.setInput(ThrowingBoundedInputStream(raw, maxBytes, label), null)
                parse(parser)
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (interrupted: InterruptedException) {
            Thread.currentThread().interrupt()
            throw interrupted
        } catch (_: Exception) {
            null
        }
    }

    private fun withinCompressionRatio(entry: ZipEntry, limits: ReaderResourceLimits): Boolean {
        val size = entry.size
        val compressedSize = entry.compressedSize
        if (size < limits.compressionRatioCheckFromBytes || compressedSize < 0L) return true
        val ratio = limits.maxCompressionRatio
        val maxExpanded = if (compressedSize > Long.MAX_VALUE / ratio) {
            Long.MAX_VALUE
        } else {
            maxOf(1L, compressedSize) * ratio
        }
        return size <= maxExpanded
    }
}
