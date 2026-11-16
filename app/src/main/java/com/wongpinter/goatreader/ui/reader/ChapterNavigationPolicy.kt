package com.wongpinter.goatreader.ui.reader

/** Binary-searches sorted TOC spine indices for the adjacent chapter around a spine position. */
internal fun adjacentChapterEntryIndex(
    currentSpineIndex: Int,
    chapterSpineIndices: List<Int>,
    forward: Boolean,
): Int? {
    if (currentSpineIndex < 0 || chapterSpineIndices.isEmpty()) return null
    var low = 0
    var high = chapterSpineIndices.size
    while (low < high) {
        val middle = (low + high) ushr 1
        val beforeTarget = if (forward) {
            chapterSpineIndices[middle] <= currentSpineIndex
        } else {
            chapterSpineIndices[middle] < currentSpineIndex
        }
        if (beforeTarget) low = middle + 1 else high = middle
    }
    val target = if (forward) low else low - 1
    return target.takeIf { it in chapterSpineIndices.indices }
}

internal data class ChapterEndClearancePlan(
    val atDocumentEnd: Boolean,
    val beforeFragments: List<String>,
)

/** Places clearance at TOC chapter boundaries, including anchor boundaries inside one XHTML file. */
internal fun chapterEndClearancePlan(
    currentSpineIndex: Int,
    lastSpineIndex: Int,
    chapterStarts: List<Pair<Int, String>>,
): ChapterEndClearancePlan {
    if (currentSpineIndex < 0) return ChapterEndClearancePlan(false, emptyList())

    val sameDocumentStarts = chapterStarts.filter { it.first == currentSpineIndex }
    val nextDocumentChapter = chapterStarts.firstOrNull { it.first > currentSpineIndex }?.first
    val atDocumentEnd = when {
        nextDocumentChapter != null -> nextDocumentChapter == currentSpineIndex + 1
        else -> currentSpineIndex == lastSpineIndex
    }
    val beforeFragments = sameDocumentStarts.drop(1).mapNotNull { (_, href) ->
        href.substringAfter('#', "").takeIf(String::isNotBlank)
    }.distinct()

    return ChapterEndClearancePlan(atDocumentEnd, beforeFragments)
}
