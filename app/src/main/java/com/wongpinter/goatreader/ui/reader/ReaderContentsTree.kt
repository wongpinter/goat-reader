package com.wongpinter.goatreader.ui.reader

/** Precomputed hierarchy for the flat reading-order TOC. No recursive row scans. */
internal class ReaderContentsTree private constructor(val depth: IntArray) {
    constructor(depths: List<Int>) : this(IntArray(depths.size) { depths[it].coerceAtLeast(0) })
    constructor(size: Int, depthAt: (Int) -> Int) : this(IntArray(size) { depthAt(it).coerceAtLeast(0) })
    val parent = IntArray(depth.size) { -1 }
    val isGroup = BooleanArray(depth.size)

    init {
        val ancestors = ArrayDeque<Int>()
        depth.indices.forEach { index ->
            while (ancestors.isNotEmpty() && depth[ancestors.last()] >= depth[index]) {
                ancestors.removeLast()
            }
            ancestors.lastOrNull()?.let {
                parent[index] = it
                isGroup[it] = true
            }
            ancestors.addLast(index)
        }
    }

    fun ancestorsOf(index: Int): List<Int> = buildList {
        var ancestor = parent.getOrElse(index) { -1 }
        while (ancestor >= 0) {
            add(ancestor)
            ancestor = parent[ancestor]
        }
    }

    /** Hidden descendants never become zero-height LazyColumn items. */
    fun visibleIndices(isCollapsed: (Int) -> Boolean): List<Int> = buildList {
        var hiddenBelow: Int? = null
        depth.indices.forEach { index ->
            val hiddenDepth = hiddenBelow
            if (hiddenDepth != null && depth[index] > hiddenDepth) return@forEach
            hiddenBelow = null
            add(index)
            if (isGroup[index] && isCollapsed(index)) hiddenBelow = depth[index]
        }
    }

    /** Primitive indices keep large chapter lists from boxing every visible row. */
    fun visibleIndicesArray(isCollapsed: (Int) -> Boolean): IntArray {
        var hiddenBelow: Int? = null
        var count = 0
        depth.indices.forEach { index ->
            val hiddenDepth = hiddenBelow
            if (hiddenDepth != null && depth[index] > hiddenDepth) return@forEach
            hiddenBelow = null
            count++
            if (isGroup[index] && isCollapsed(index)) hiddenBelow = depth[index]
        }
        val visible = IntArray(count)
        var cursor = 0
        hiddenBelow = null
        depth.indices.forEach { index ->
            val hiddenDepth = hiddenBelow
            if (hiddenDepth != null && depth[index] > hiddenDepth) return@forEach
            hiddenBelow = null
            visible[cursor++] = index
            if (isGroup[index] && isCollapsed(index)) hiddenBelow = depth[index]
        }
        return visible
    }

    /** Map a navigation entry to a lazy row, falling back to its visible parent. */
    fun visiblePositionOf(index: Int?, visibleIndices: IntArray): Int {
        var entry = index?.takeIf { it in depth.indices } ?: return 0
        while (entry >= 0) {
            val position = visibleIndices.binarySearch(entry)
            if (position >= 0) return position
            entry = parent[entry]
        }
        return 0
    }

    fun visiblePositionOf(index: Int?, visibleIndices: List<Int>): Int {
        var entry = index?.takeIf { it in depth.indices } ?: return 0
        while (entry >= 0) {
            val position = visibleIndices.binarySearch(entry)
            if (position >= 0) return position
            entry = parent[entry]
        }
        return 0
    }
}
