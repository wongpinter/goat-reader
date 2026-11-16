@file:OptIn(org.readium.r2.shared.ExperimentalReadiumApi::class)

package com.wongpinter.goatreader.ui.reader

import android.app.AlertDialog
import android.text.Editable
import android.text.TextWatcher
import android.os.Bundle
import android.os.Build
import android.os.SystemClock
import android.view.Gravity
import android.graphics.drawable.GradientDrawable
import android.view.View
import android.view.ViewGroup
import android.webkit.WebView
import android.widget.BaseAdapter
import android.widget.ImageButton
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ListView
import android.widget.TextView
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.ComposeView
import androidx.fragment.app.FragmentActivity
import androidx.fragment.app.FragmentContainerView
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.lifecycleScope
import com.wongpinter.goatreader.GoatReaderApp
import com.wongpinter.goatreader.data.AppSettings
import com.wongpinter.goatreader.data.ReaderSettings
import com.wongpinter.goatreader.data.ReadingMode
import com.wongpinter.goatreader.data.model.Book
import com.wongpinter.goatreader.data.model.BookFormat
import com.wongpinter.goatreader.data.model.ReadingProgress
import com.wongpinter.goatreader.data.model.ReaderChromeStyle
import com.wongpinter.goatreader.data.effectiveTheme
import com.wongpinter.goatreader.data.epub.EpubEngine
import com.wongpinter.goatreader.data.epub.EpubReaderPreferences
import com.wongpinter.goatreader.ui.theme.isDark
import com.wongpinter.goatreader.data.epub.EpubSession
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import org.readium.r2.navigator.epub.EpubNavigatorFragment
import org.readium.r2.navigator.epub.css.FontStyle as ReadiumFontStyle
import org.readium.r2.navigator.preferences.FontFamily as ReadiumFontFamily
import org.readium.r2.shared.publication.Layout
import org.readium.r2.shared.publication.Link
import org.readium.r2.shared.publication.Locator
import org.readium.r2.shared.publication.services.positions

/** Readium owns the EPUB viewport; Goat Reader retains the library and saved progress. */
class EpubReaderActivity : FragmentActivity() {
    private val repository by lazy { (application as GoatReaderApp).bookRepository }
    private val settingsRepository by lazy { (application as GoatReaderApp).settingsRepository }
    private var readerSettings by mutableStateOf(ReaderSettings())
    private var appSettings by mutableStateOf(AppSettings())
    private var settingsWriter: ReaderSettingsWriter? = null
    private var reflowable by mutableStateOf(true)
    private var appearanceSheetVisible by mutableStateOf(false)
    private var readerChromeVisible = true
    private var chromeHideJob: Job? = null
    private var systemBarTopInset = 0
    private var systemBarBottomInset = 0
    private var systemBarLeftInset = 0
    private var systemBarRightInset = 0
    private lateinit var appearanceOverlay: ComposeView
    private lateinit var topBar: LinearLayout
    private lateinit var bottomBar: LinearLayout
    private lateinit var topEdgeScrim: View
    private lateinit var bottomEdgeScrim: View
    private lateinit var readerRoot: ChapterSwipeFrameLayout
    private lateinit var previousNavigationButton: ImageButton
    private lateinit var nextNavigationButton: ImageButton
    private val chromeButtons = mutableListOf<ImageButton>()
    private val engine by lazy { EpubEngine(this) }
    private lateinit var container: FragmentContainerView
    private lateinit var titleView: TextView
    private lateinit var progressView: TextView
    private var book: Book? = null
    private var session: EpubSession? = null
    private var readingOrderIndexByHref: Map<String, Int> = emptyMap()
    private var readingOrderLastIndex: Int = -1
    private var chapterNavigationEntries: List<Pair<Int, Link>> = emptyList()
    private var chapterNavigationSpineIndices: List<Int> = emptyList()
    private var navigator: EpubNavigatorFragment? = null
    private var pendingLocator: Locator? = null
    private var endClearanceRequest: String? = null
    private var saveJob: Job? = null
    private var lastSavedLocatorJson: String? = null
    private var readingLogId: String? = null
    private var readingStartedAt: Long = 0L
    private var openingStartedAt = 0L

    override fun onCreate(savedInstanceState: Bundle?) {
        // Readium's fragment needs a live Publication-backed factory and has no default constructor.
        // Drop its serialized fragment entry before FragmentActivity attempts restoration.
        savedInstanceState?.remove(FRAGMENT_STATE_KEY)
        savedInstanceState?.getBundle(SAVED_STATE_REGISTRY_KEY)?.remove(FRAGMENT_STATE_KEY)
        super.onCreate(savedInstanceState)
        val bookId = intent.getStringExtra(EXTRA_BOOK_ID)
        val selected = bookId?.let(repository::bookById)
        if (selected == null || selected.format != BookFormat.EPUB) {
            finish()
            return
        }
        book = selected
        WindowCompat.setDecorFitsSystemWindows(window, false)
        readerSettings = selected.readerSettings ?: ReaderSettings.defaultFor(BookFormat.EPUB)
        val writer = ReaderSettingsWriter(lifecycleScope) { updated ->
            repository.saveReaderSettings(selected.id, updated)
        }
        settingsWriter = writer
        lifecycleScope.launch {
            writer.pending.collect { pending ->
                if (pending != null) {
                    readerSettings = pending
                    updateNavigationChrome()
                    submitCurrentPreferences()
                }
            }
        }
        lifecycleScope.launch {
            settingsRepository.appSettings.collect { updated ->
                appSettings = updated
                applyChromeTheme()
                submitCurrentPreferences()
            }
        }
        pendingLocator = savedInstanceState?.getString(STATE_LOCATOR_JSON)?.let { json ->
            runCatching { Locator.fromJSON(JSONObject(json)) }.getOrNull()
        }
        createReaderViews(selected)
        openPublication(selected)
    }

    private fun createReaderViews(book: Book) {
        container = FragmentContainerView(this).apply { id = View.generateViewId() }
        titleView = TextView(this).apply {
            text = book.title
            textSize = 16f
            setTextColor(0xFF201A16.toInt())
            maxLines = 1
            ellipsize = android.text.TextUtils.TruncateAt.END
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(12), 0, dp(8), 0)
        }
        progressView = TextView(this).apply {
            text = getString(com.wongpinter.goatreader.R.string.reader_loading_chapters, 0)
            textSize = 12f
            setTextColor(0xFF51463D.toInt())
            gravity = Gravity.CENTER
            setPadding(dp(8), 0, dp(8), 0)
        }

        topBar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            addView(chromeButton(com.wongpinter.goatreader.R.drawable.ic_reader_arrow_back, com.wongpinter.goatreader.R.string.reader_back) { finish() }, LinearLayout.LayoutParams(dp(48), dp(48)))
            addView(titleView, LinearLayout.LayoutParams(0, dp(48), 1f))
            addView(chromeButton(com.wongpinter.goatreader.R.drawable.ic_reader_typography, com.wongpinter.goatreader.R.string.reader_settings) { showAppearanceSheet() }, LinearLayout.LayoutParams(dp(48), dp(48)))
            addView(chromeButton(com.wongpinter.goatreader.R.drawable.ic_reader_contents, com.wongpinter.goatreader.R.string.reader_contents) { showContents() }, LinearLayout.LayoutParams(dp(48), dp(48)))
        }
        previousNavigationButton = chromeButton(
            com.wongpinter.goatreader.R.drawable.ic_reader_arrow_back,
            com.wongpinter.goatreader.R.string.reader_previous_chapter,
        ) {
            if (readerSettings.readingMode == ReadingMode.SCROLL) {
                navigateChapter(ChapterSwipeDirection.PREVIOUS)
            } else {
                navigator?.goBackward(true)
            }
        }
        nextNavigationButton = chromeButton(
            com.wongpinter.goatreader.R.drawable.ic_reader_arrow_forward,
            com.wongpinter.goatreader.R.string.reader_next_chapter,
        ) {
            if (readerSettings.readingMode == ReadingMode.SCROLL) {
                navigateChapter(ChapterSwipeDirection.NEXT)
            } else {
                navigator?.goForward(true)
            }
        }
        topEdgeScrim = edgeScrim(top = true)
        bottomEdgeScrim = edgeScrim(top = false)
        bottomBar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            addView(previousNavigationButton, LinearLayout.LayoutParams(dp(48), dp(48)))
            addView(progressView, LinearLayout.LayoutParams(0, dp(48), 1f))
            addView(nextNavigationButton, LinearLayout.LayoutParams(dp(48), dp(48)))
        }
        appearanceOverlay = ComposeView(this).apply {
            visibility = View.GONE
            setContent {
                com.wongpinter.goatreader.ui.theme.GoatReaderTheme(
                    theme = appSettings.effectiveTheme(isSystemDark()),
                    dynamicColor = appSettings.dynamicColor,
                ) {
                    EpubAppearanceSheet(
                        visible = appearanceSheetVisible,
                        settings = readerSettings,
                        appSettings = appSettings,
                        reflowable = reflowable,
                        onSettingsChange = ::updateReaderSettings,
                        onAppSettingsChange = ::updateAppSettings,
                        onDismissRequest = ::dismissAppearanceSheet,
                    )
                }
            }
        }
        readerRoot = ChapterSwipeFrameLayout(
            context = this,
            onChapterSwipe = { direction -> navigateChapter(direction) },
            onReaderTap = ::toggleReaderChrome,
            captureChapterSwipe = ::captureChapterSwipeHandler,
        )
        val root = readerRoot
        root.addView(container, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        root.addView(topEdgeScrim, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(176), Gravity.TOP))
        root.addView(bottomEdgeScrim, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(176), Gravity.BOTTOM))
        root.addView(topBar, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(48), Gravity.TOP))
        root.addView(bottomBar, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(48), Gravity.BOTTOM))
        root.addView(appearanceOverlay, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        ViewCompat.setOnApplyWindowInsetsListener(root) { _, insets ->
            val safeBars = WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout()
            val systemBars = insets.getInsetsIgnoringVisibility(safeBars)
            systemBarTopInset = systemBars.top
            systemBarBottomInset = systemBars.bottom
            systemBarLeftInset = systemBars.left
            systemBarRightInset = systemBars.right
            updateChromeGeometry(animate = false)
            insets
        }
        setContentView(root)
        applyChromeTheme()
        ViewCompat.requestApplyInsets(root)
        updateNavigationChrome()
    }

    private fun showAppearanceSheet() {
        chromeHideJob?.cancel()
        appearanceOverlay.visibility = View.VISIBLE
        appearanceSheetVisible = true
        updateNavigationChrome()
    }

    private fun dismissAppearanceSheet() {
        appearanceSheetVisible = false
        appearanceOverlay.visibility = View.GONE
        updateNavigationChrome()
        scheduleReaderChromeAutoHide()
    }

    private fun toggleReaderChrome() {
        if (!appearanceSheetVisible) setReaderChromeVisible(!readerChromeVisible)
    }

    private fun setReaderChromeVisible(visible: Boolean) {
        chromeHideJob?.cancel()
        chromeHideJob = null
        if (readerChromeVisible == visible) {
            if (visible) scheduleReaderChromeAutoHide()
            return
        }
        readerChromeVisible = visible
        WindowCompat.getInsetsController(window, window.decorView).apply {
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            if (visible) show(WindowInsetsCompat.Type.systemBars())
            else hide(WindowInsetsCompat.Type.systemBars())
        }
        updateChromeGeometry(animate = true)
        ViewCompat.requestApplyInsets(readerRoot)
        if (visible) scheduleReaderChromeAutoHide()
    }

    private fun scheduleReaderChromeAutoHide() {
        chromeHideJob?.cancel()
        if (!readerChromeVisible || appearanceSheetVisible || navigator == null ||
            getSystemService(android.view.accessibility.AccessibilityManager::class.java)?.isEnabled == true
        ) return
        chromeHideJob = lifecycleScope.launch {
            delay(READER_CHROME_AUTO_HIDE_DELAY_MILLIS)
            if (lifecycle.currentState.isAtLeast(androidx.lifecycle.Lifecycle.State.RESUMED) &&
                readerChromeVisible && !appearanceSheetVisible
            ) {
                setReaderChromeVisible(false)
            }
        }
    }

    private fun updateChromeGeometry(animate: Boolean) {
        if (!::topBar.isInitialized || !::bottomBar.isInitialized || !::container.isInitialized) return
        val floatingInset = if (appSettings.readerChromeStyle == ReaderChromeStyle.FLOATING) dp(16) else 0
        listOf(topBar, bottomBar).forEach { bar ->
            (bar.layoutParams as? FrameLayout.LayoutParams)?.let { params ->
                val left = systemBarLeftInset + floatingInset
                val right = systemBarRightInset + floatingInset
                if (params.leftMargin != left || params.rightMargin != right) {
                    params.leftMargin = left
                    params.rightMargin = right
                    bar.layoutParams = params
                }
            }
        }
        val topTarget = if (readerChromeVisible) {
            systemBarTopInset.toFloat()
        } else {
            (systemBarTopInset - dp(48)).toFloat()
        }
        val bottomTarget = if (readerChromeVisible) {
            -systemBarBottomInset.toFloat()
        } else {
            (dp(48) - systemBarBottomInset).toFloat()
        }
        (container.layoutParams as? FrameLayout.LayoutParams)?.let { params ->
            // The controls overlay the page; only the stable system-safe insets
            // constrain Readium, regardless of whether the chrome is visible.
            val topMargin = systemBarTopInset
            val bottomMargin = systemBarBottomInset
            if (params.topMargin != topMargin || params.bottomMargin != bottomMargin) {
                params.topMargin = topMargin
                params.bottomMargin = bottomMargin
                container.layoutParams = params
            }
        }
        if (animate) {
            if (readerChromeVisible) {
                topBar.visibility = View.VISIBLE
                bottomBar.visibility = View.VISIBLE
                topBar.importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_AUTO
                bottomBar.importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_AUTO
                topBar.alpha = 0f
                bottomBar.alpha = 0f
                topBar.translationY = topTarget - dp(48)
                bottomBar.translationY = bottomTarget + dp(48)
            } else {
                topBar.importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS
                bottomBar.importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS
            }
            topBar.animate().cancel()
            bottomBar.animate().cancel()
            topBar.animate().translationY(topTarget).alpha(if (readerChromeVisible) 1f else 0f)
                .setDuration(READER_CHROME_ANIMATION_MILLIS)
                .withEndAction { if (!readerChromeVisible) topBar.visibility = View.GONE }
                .start()
            bottomBar.animate().translationY(bottomTarget).alpha(if (readerChromeVisible) 1f else 0f)
                .setDuration(READER_CHROME_ANIMATION_MILLIS)
                .withEndAction { if (!readerChromeVisible) bottomBar.visibility = View.GONE }
                .start()
        } else {
            topBar.visibility = if (readerChromeVisible) View.VISIBLE else View.GONE
            bottomBar.visibility = if (readerChromeVisible) View.VISIBLE else View.GONE
            topBar.importantForAccessibility = if (readerChromeVisible) {
                View.IMPORTANT_FOR_ACCESSIBILITY_AUTO
            } else View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS
            bottomBar.importantForAccessibility = if (readerChromeVisible) {
                View.IMPORTANT_FOR_ACCESSIBILITY_AUTO
            } else View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS
            topBar.alpha = if (readerChromeVisible) 1f else 0f
            bottomBar.alpha = if (readerChromeVisible) 1f else 0f
            topBar.translationY = topTarget
            bottomBar.translationY = bottomTarget
        }
    }

    private fun updateReaderSettings(transform: (ReaderSettings) -> ReaderSettings) {
        settingsWriter?.update(readerSettings, transform)
    }

    private fun updateAppSettings(transform: (AppSettings) -> AppSettings) {
        lifecycleScope.launch { settingsRepository.updateApp(transform) }
    }

    private fun submitCurrentPreferences() {
        endClearanceRequest = null
        navigator?.submitPreferences(
            EpubReaderPreferences.from(readerSettings, appSettings.effectiveTheme(isSystemDark())),
        )
        pendingLocator?.let(::applyScrollEndClearance)
    }

    /** Add clearance at actual TOC chapter boundaries, including intra-document anchors. */
    private fun applyScrollEndClearance(locator: Locator, retriesRemaining: Int = 3) {
        if (!::container.isInitialized) return
        val href = locator.href.toString().substringBefore('#')
        val spineIndex = readingOrderIndexByHref[href] ?: return
        val enabled = reflowable && readerSettings.readingMode == ReadingMode.SCROLL
        val plan = chapterEndClearancePlan(
            currentSpineIndex = spineIndex,
            lastSpineIndex = readingOrderLastIndex,
            chapterStarts = chapterNavigationEntries.map { (index, link) -> index to link.href.toString() },
        )
        val request = "$href|$enabled|${plan.atDocumentEnd}|${plan.beforeFragments.joinToString()}"
        if (endClearanceRequest == request) return

        val webView = findEpubWebView(container)
        if (webView == null) {
            if (retriesRemaining > 0 && !isFinishing) {
                container.postDelayed({ applyScrollEndClearance(locator, retriesRemaining - 1) }, 120L)
            }
            return
        }

        endClearanceRequest = request
        val fragments = JSONArray().apply { plan.beforeFragments.forEach(::put) }
        val script = """(function(){
            var body = document.body;
            if (document.readyState === 'loading' || !body) return false;
            var existing = document.querySelectorAll('[data-goatreader-end-clearance]');
            for (var i = existing.length - 1; i >= 0; i--) existing[i].remove();
            if (!$enabled) return true;
            function makeSpacer() {
                var spacer = document.createElement('div');
                spacer.setAttribute('data-goatreader-end-clearance', '');
                spacer.setAttribute('aria-hidden', 'true');
                spacer.style.cssText = 'display:block;clear:both;width:100%;height:${END_CLEARANCE_CSS_PX}px;';
                return spacer;
            }
            if (${plan.atDocumentEnd}) body.appendChild(makeSpacer());
            var fragments = $fragments;
            var allAnchorsFound = true;
            fragments.forEach(function(encoded) {
                var fragment;
                try { fragment = decodeURIComponent(encoded); } catch (_) { fragment = encoded; }
                var target = document.getElementById(fragment) || document.getElementsByName(fragment)[0];
                if (!target || !target.parentNode) { allAnchorsFound = false; return; }
                target.parentNode.insertBefore(makeSpacer(), target);
            });
            return allAnchorsFound;
        })();"""
        webView.evaluateJavascript(script) { result ->
            if (result != "true" && retriesRemaining > 0 && !isFinishing) {
                endClearanceRequest = null
                webView.postDelayed({ applyScrollEndClearance(locator, retriesRemaining - 1) }, 120L)
            }
        }
    }

    private fun updateNavigationChrome() {
        if (!::previousNavigationButton.isInitialized || !::nextNavigationButton.isInitialized) return
        val scrolling = readerSettings.readingMode == ReadingMode.SCROLL
        if (::readerRoot.isInitialized) {
            readerRoot.chapterSwipesEnabled = scrolling && !appearanceSheetVisible &&
                readerSettings.chapterPull.minimumTravelDp != null
            readerSettings.chapterPull.minimumTravelDp?.let { readerRoot.minimumTravelDp = it }
            readerRoot.chapterSwipeBoundaryProvider = ::currentEpubChapterSwipeBoundaries
            readerRoot.chapterSwipeBoundaries = currentEpubChapterSwipeBoundaries()
        }
        if (scrolling) {
            previousNavigationButton.contentDescription = getString(com.wongpinter.goatreader.R.string.reader_previous_chapter)
            nextNavigationButton.contentDescription = getString(com.wongpinter.goatreader.R.string.reader_next_chapter)
            previousNavigationButton.isEnabled = adjacentChapterLink(ChapterSwipeDirection.PREVIOUS) != null
            nextNavigationButton.isEnabled = adjacentChapterLink(ChapterSwipeDirection.NEXT) != null
        } else {
            previousNavigationButton.contentDescription = getString(com.wongpinter.goatreader.R.string.reader_previous_page)
            previousNavigationButton.isEnabled = navigator != null
            nextNavigationButton.contentDescription = getString(com.wongpinter.goatreader.R.string.reader_next_page)
            nextNavigationButton.isEnabled = navigator != null
        }
    }

    private fun currentEpubChapterSwipeBoundaries(): ChapterSwipeBoundaries {
        val webView = if (::container.isInitialized) findEpubWebView(container) else null
        val canPrevious = adjacentChapterLink(ChapterSwipeDirection.PREVIOUS) != null
        val canNext = adjacentChapterLink(ChapterSwipeDirection.NEXT) != null
        val boundaries = epubChapterSwipeBoundaries(
            atResourceStart = epubVisualEdge(webView?.canScrollVertically(-1)),
            atResourceEnd = epubVisualEdge(webView?.canScrollVertically(1)),
            canNavigatePrevious = canPrevious,
            canNavigateNext = canNext,
        )
        return boundaries
    }

    private fun findEpubWebView(view: View): WebView? {
        if (view is WebView) return view.takeIf { it.isShown && it.width > 0 && it.height > 0 }
        if (view is ViewGroup) {
            for (index in 0 until view.childCount) {
                findEpubWebView(view.getChildAt(index))?.let { return it }
            }
        }
        return null
    }

    private fun captureChapterSwipeHandler(): (ChapterSwipeDirection) -> Unit {
        val targets = mapOf(
            ChapterSwipeDirection.PREVIOUS to adjacentChapterLink(ChapterSwipeDirection.PREVIOUS),
            ChapterSwipeDirection.NEXT to adjacentChapterLink(ChapterSwipeDirection.NEXT),
        )
        return { direction -> navigateChapter(direction, targets[direction]) }
    }

    private fun navigateChapter(
        direction: ChapterSwipeDirection,
        target: Link? = adjacentChapterLink(direction),
    ) {
        if (readerSettings.readingMode != ReadingMode.SCROLL) return
        val destination = target ?: return
        if (appSettings.haptics) {
            readerRoot.performHapticFeedback(android.view.HapticFeedbackConstants.CONFIRM)
        }
        navigator?.go(destination, true)
    }

    private fun adjacentChapterLink(direction: ChapterSwipeDirection): Link? {
        val locator = pendingLocator ?: return null
        val currentSpineIndex = readingOrderIndexByHref[locator.href.toString().substringBefore('#')] ?: return null
        val entryIndex = adjacentChapterEntryIndex(
            currentSpineIndex = currentSpineIndex,
            chapterSpineIndices = chapterNavigationSpineIndices,
            forward = direction == ChapterSwipeDirection.NEXT,
        ) ?: return null
        return chapterNavigationEntries.getOrNull(entryIndex)?.second
    }

    private fun prepareChapterNavigation(opened: EpubSession) {
        val readingOrder = opened.publication.readingOrder
        readingOrderLastIndex = readingOrder.lastIndex
        val indicesByHref = LinkedHashMap<String, Int>(readingOrder.size)
        readingOrder.forEachIndexed { index, link ->
            val href = link.href.toString().substringBefore('#')
            if (href !in indicesByHref) indicesByHref[href] = index
        }
        readingOrderIndexByHref = indicesByHref

        val tocEntries = flattenContents(opened.tableOfContents.ifEmpty { readingOrder })
            .mapNotNull { row ->
                val index = indicesByHref[row.link.href.toString().substringBefore('#')]
                index?.let { it to row.link }
            }
            .distinctBy { it.second.href.toString() }
            .sortedBy { it.first }
        chapterNavigationEntries = tocEntries.ifEmpty {
            readingOrder.mapIndexed { index, link -> index to link }
        }
        chapterNavigationSpineIndices = chapterNavigationEntries.map { it.first }
    }

    private fun isSystemDark(): Boolean =
        resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK ==
            android.content.res.Configuration.UI_MODE_NIGHT_YES

    private fun applyChromeTheme() {
        if (!::topBar.isInitialized || !::bottomBar.isInitialized) return
        val theme = appSettings.effectiveTheme(isSystemDark())
        val colors = readerColors(theme)
        val chrome = colors.chrome.toArgb()
        val edgeInk = colors.background.copy(alpha = 0.94f).toArgb()
        val edgeMid = colors.background.copy(alpha = 0.54f).toArgb()
        topEdgeScrim.background = GradientDrawable(
            GradientDrawable.Orientation.TOP_BOTTOM,
            intArrayOf(edgeInk, edgeMid, android.graphics.Color.TRANSPARENT),
        )
        bottomEdgeScrim.background = GradientDrawable(
            GradientDrawable.Orientation.TOP_BOTTOM,
            intArrayOf(android.graphics.Color.TRANSPARENT, edgeMid, edgeInk),
        )
        listOf(topEdgeScrim, bottomEdgeScrim).forEach { scrim ->
            scrim.visibility = if (reflowable) View.VISIBLE else View.GONE
            scrim.importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
        }
        // The EPUB viewport stops at the safe system-bar insets. Paint the
        // underlying full-window canvas with the same page surface so the
        // top and bottom edges remain full-bleed while text stays protected.
        readerRoot.setBackgroundColor(colors.background.toArgb())
        val floating = appSettings.readerChromeStyle == ReaderChromeStyle.FLOATING
        listOf(topBar, bottomBar).forEach { bar ->
            bar.background = android.graphics.drawable.GradientDrawable().apply {
                setColor(chrome)
                cornerRadius = if (floating) dp(24).toFloat() else 0f
            }
        }
        updateChromeGeometry(animate = false)
        titleView.setTextColor(colors.onChrome.toArgb())
        progressView.setTextColor(colors.onChromeVariant.toArgb())
        val iconColor = colors.onChrome.toArgb()
        val disabledIconColor = colors.onChrome.copy(alpha = 0.38f).toArgb()
        val iconTint = android.content.res.ColorStateList(
            arrayOf(intArrayOf(-android.R.attr.state_enabled), intArrayOf()),
            intArrayOf(disabledIconColor, iconColor),
        )
        chromeButtons.forEach { button ->
            button.imageTintList = iconTint
            if (floating) {
                val face = android.graphics.drawable.GradientDrawable().apply {
                    shape = android.graphics.drawable.GradientDrawable.OVAL
                    setColor(colors.control.toArgb())
                }
                button.background = android.graphics.drawable.RippleDrawable(
                    android.content.res.ColorStateList.valueOf(colors.onChrome.copy(alpha = 0.12f).toArgb()),
                    android.graphics.drawable.InsetDrawable(face, dp(6)),
                    null,
                )
            } else {
                val ripple = android.util.TypedValue()
                if (this.theme.resolveAttribute(android.R.attr.selectableItemBackgroundBorderless, ripple, true)) {
                    button.setBackgroundResource(ripple.resourceId)
                }
            }
        }
        window.statusBarColor = android.graphics.Color.TRANSPARENT
        window.navigationBarColor = android.graphics.Color.TRANSPARENT
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            window.isStatusBarContrastEnforced = false
            window.isNavigationBarContrastEnforced = false
        }
        WindowCompat.getInsetsController(window, window.decorView).apply {
            isAppearanceLightStatusBars = !theme.isDark()
            isAppearanceLightNavigationBars = !theme.isDark()
        }
    }

    private fun openPublication(book: Book) {
        openingStartedAt = SystemClock.elapsedRealtime()
        lifecycleScope.launch {
            try {
                appSettings = settingsRepository.appSettings.first()
                applyChromeTheme()
                val file = withContext(Dispatchers.IO) { repository.bookFileFor(book) }
                    ?: error("The EPUB file is missing")
                val opened = engine.open(file)
                session = opened
                repository.markStarted(book.id)
                readingLogId = repository.bookById(book.id)?.activeReadingLogId
                attachNavigator(book, opened)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                showError(error.message ?: "Unable to open this EPUB")
            }
        }
    }

    private suspend fun attachNavigator(book: Book, opened: EpubSession) {
        prepareChapterNavigation(opened)
        val positions = opened.publication.positions()
        val requestedChapter = intent.getStringExtra(EXTRA_BOOKMARK_ID)
            ?.let { id -> book.bookmarks.firstOrNull { it.id == id && !it.orphaned }?.chapterIndex }
            ?: intent.getStringExtra(EXTRA_QUOTE_ID)
                ?.let { id -> book.quotes.firstOrNull { it.id == id }?.chapterIndex }
        val chapterHref = requestedChapter?.let { opened.publication.readingOrder.getOrNull(it)?.href?.toString() }
            ?.substringBefore('#')
        val restored = pendingLocator?.takeIf { locator ->
            readingOrderIndexByHref.containsKey(locator.href.toString().substringBefore('#'))
        }
        val initial = restored ?: chapterHref?.let { href ->
            positions.firstOrNull { it.href.toString().substringBefore('#') == href }
        } ?: book.progress.epubLocatorJson?.let { json ->
            runCatching { Locator.fromJSON(JSONObject(json)) }.getOrNull()
        } ?: positions.minByOrNull { locator ->
            val progression = locator.locations.totalProgression ?: locator.locations.progression ?: 0.0
            kotlin.math.abs(progression - book.progress.fraction.toDouble())
        }
        val initialPreferences = EpubReaderPreferences.from(
            readerSettings,
            appSettings.effectiveTheme(isSystemDark()),
        )
        reflowable = opened.navigatorFactory.createPreferencesEditor(initialPreferences).layout == Layout.REFLOWABLE
        if (::topEdgeScrim.isInitialized) {
            val scrimVisibility = if (reflowable) View.VISIBLE else View.GONE
            topEdgeScrim.visibility = scrimVisibility
            bottomEdgeScrim.visibility = scrimVisibility
        }
        val fragmentFactory = opened.navigatorFactory.createFragmentFactory(
            initialLocator = initial,
            initialPreferences = initialPreferences,
            configuration = EpubNavigatorFragment.Configuration {
                // In Scroll mode, chapter navigation uses the deliberate vertical pulls.
                // Horizontal turns can be accidental and remain available in paginated mode.
                disablePageTurnsWhileScrolling = true
                servedAssets += "fonts/.*"
                addFontFamilyDeclaration(ReadiumFontFamily("Atkinson Hyperlegible Next")) {
                    addFontFace {
                        addSource("fonts/atkinson_hyperlegible_next.ttf", preload = true)
                        setFontStyle(ReadiumFontStyle.NORMAL)
                        setFontWeight(200..800)
                    }
                    addFontFace {
                        addSource("fonts/atkinson_hyperlegible_next_italic.ttf")
                        setFontStyle(ReadiumFontStyle.ITALIC)
                        setFontWeight(200..800)
                    }
                }
            },
        )
        val fragment = fragmentFactory.instantiate(
            classLoader,
            EpubNavigatorFragment::class.java.name,
        ) as EpubNavigatorFragment
        navigator = fragment
        updateNavigationChrome()
        supportFragmentManager.beginTransaction()
            .replace(container.id, fragment, NAVIGATOR_TAG)
            .commit()
        setReaderChromeVisible(true)
        android.util.Log.i(
            TAG,
            "opened sections=${opened.openMetrics.sectionCount} entries=${opened.openMetrics.archiveEntries} " +
                "positions=${opened.openMetrics.positionCount} spacing=${opened.openMetrics.positionBytes} " +
                "openMs=${SystemClock.elapsedRealtime() - openingStartedAt}",
        )
        if (lifecycle.currentState.isAtLeast(androidx.lifecycle.Lifecycle.State.RESUMED)) {
            readingStartedAt = SystemClock.elapsedRealtime()
        }
        lifecycleScope.launch {
            fragment.currentLocator.collect { locator ->
                pendingLocator = locator
                applyScrollEndClearance(locator)
                updateNavigationChrome()
                titleView.text = locator.title ?: book.title
                val total = locator.locations.totalProgression ?: locator.locations.progression ?: 0.0
                progressView.text = "${(total * 100).toInt().coerceIn(0, 100)}%"
                scheduleProgressSave(book, locator)
            }
        }
    }

    private fun scheduleProgressSave(book: Book, locator: Locator) {
        val json = locator.toJSON().toString()
        if (json == lastSavedLocatorJson) return
        saveJob?.cancel()
        saveJob = lifecycleScope.launch {
            delay(PROGRESS_SAVE_DELAY_MILLIS)
            persistProgress(book, locator, json)
        }
    }

    private suspend fun persistProgress(book: Book, locator: Locator, json: String) {
        if (json == lastSavedLocatorJson) return
        val chapterIndex = readingOrderIndexByHref[locator.href.toString().substringBefore('#')] ?: 0
        val fraction = (locator.locations.totalProgression ?: locator.locations.progression ?: 0.0)
            .toFloat().coerceIn(0f, 1f)
        repository.saveProgressForReading(
            book.id,
            ReadingProgress(
                chapterIndex = chapterIndex,
                fraction = fraction,
                epubLocatorJson = json,
            ),
            readingLogId,
        )
        lastSavedLocatorJson = json
    }

    private fun showContents() {
        val opened = session ?: return
        val reader = navigator ?: return
        chromeHideJob?.cancel()
        val rows = flattenContents(opened.tableOfContents.ifEmpty { opened.publication.readingOrder })
        if (rows.isEmpty()) return
        val dialogColors = readerColors(appSettings.effectiveTheme(isSystemDark()))
        val filteredRows = rows.toMutableList()
        val adapter = object : BaseAdapter() {
            override fun getCount(): Int = filteredRows.size
            override fun getItem(position: Int): Any = filteredRows[position].link
            override fun getItemId(position: Int): Long = position.toLong()
            override fun getView(position: Int, convertView: View?, parent: ViewGroup): View =
                (convertView as? TextView ?: TextView(this@EpubReaderActivity)).apply {
                    val row = filteredRows[position]
                    text = row.link.title?.takeIf(String::isNotBlank) ?: row.link.href.toString()
                    textSize = 16f
                    setTextColor(dialogColors.onChrome.toArgb())
                    setPadding(dp(16 + row.depth * 18), dp(12), dp(16), dp(12))
                    maxLines = 2
                }
        }
        val search = EditText(this).apply {
            hint = "Search chapters"
            setSingleLine(true)
            setTextColor(dialogColors.onChrome.toArgb())
            setHintTextColor(dialogColors.onChromeVariant.toArgb())
            backgroundTintList = android.content.res.ColorStateList.valueOf(dialogColors.accent.toArgb())
            setPadding(dp(16), dp(8), dp(16), dp(8))
        }
        search.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(text: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(text: CharSequence?, start: Int, before: Int, count: Int) {
                val query = text?.toString()?.trim().orEmpty()
                filteredRows.clear()
                if (query.isEmpty()) filteredRows.addAll(rows) else {
                    filteredRows.addAll(rows.filter { row ->
                        (row.link.title ?: row.link.href.toString()).contains(query, ignoreCase = true)
                    })
                }
                adapter.notifyDataSetChanged()
            }
            override fun afterTextChanged(text: Editable?) = Unit
        })
        val list = ListView(this).apply {
            this.adapter = adapter
            setBackgroundColor(dialogColors.chrome.toArgb())
            divider = android.graphics.drawable.ColorDrawable(dialogColors.track.toArgb())
            dividerHeight = dp(1)
        }
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            addView(search, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
            addView(list, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
        }
        val dialog = AlertDialog.Builder(this)
            .setTitle("Contents (${rows.size})")
            .setView(content)
            .setNegativeButton("Close", null)
            .create()
        list.setOnItemClickListener { _, _, position, _ ->
            reader.go(filteredRows[position].link, true)
            dialog.dismiss()
        }
        dialog.setOnDismissListener { scheduleReaderChromeAutoHide() }
        dialog.setOnShowListener {
            dialog.window?.setBackgroundDrawable(
                android.graphics.drawable.GradientDrawable().apply {
                    setColor(dialogColors.chrome.toArgb())
                    cornerRadius = dp(28).toFloat()
                },
            )
            val titleId = resources.getIdentifier("alertTitle", "id", "android")
            if (titleId != 0) {
                dialog.findViewById<TextView>(titleId)?.setTextColor(dialogColors.onChrome.toArgb())
            }
            dialog.getButton(AlertDialog.BUTTON_NEGATIVE)
                ?.setTextColor(dialogColors.accent.toArgb())
        }
        dialog.show()
        android.util.Log.i(TAG, "toc rows=${rows.size}")
    }

    private fun flattenContents(roots: List<Link>): List<TocRow> {
        val result = ArrayList<TocRow>(roots.size)
        val pending = ArrayDeque<TocRow>()
        roots.asReversed().forEach { pending.addLast(TocRow(it, 0)) }
        while (pending.isNotEmpty()) {
            val row = pending.removeLast()
            result += row
            row.link.children.asReversed().forEach { pending.addLast(TocRow(it, row.depth + 1)) }
        }
        return result
    }

    private fun showError(message: String) {
        val view = TextView(this).apply {
            text = "$message\n\nTap Back to return to your library."
            textSize = 18f
            gravity = Gravity.CENTER
            setPadding(dp(24), dp(24), dp(24), dp(24))
        }
        setContentView(FrameLayout(this).apply { addView(view, FrameLayout.LayoutParams(-1, -1)) })
    }

    override fun onResume() {
        super.onResume()
        if (navigator != null) {
            readingStartedAt = SystemClock.elapsedRealtime()
            scheduleReaderChromeAutoHide()
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        // Navigator fragments carry a live Publication and cannot be restored from Bundle alone.
        // Reopen the session from the saved source Locator instead.
        pendingLocator?.let { outState.putString(STATE_LOCATOR_JSON, it.toJSON().toString()) }
        outState.remove(FRAGMENT_STATE_KEY)
        outState.getBundle(SAVED_STATE_REGISTRY_KEY)?.remove(FRAGMENT_STATE_KEY)
    }

    override fun onPause() {
        chromeHideJob?.cancel()
        chromeHideJob = null
        val started = readingStartedAt
        readingStartedAt = 0L
        val currentBook = book
        val seconds = if (started > 0L) (SystemClock.elapsedRealtime() - started) / 1_000L else 0L
        val locator = pendingLocator
        saveJob?.cancel()
        try {
            val progress = locator?.let { current ->
                val chapterIndex = readingOrderIndexByHref[current.href.toString().substringBefore('#')] ?: 0
                val fraction = (current.locations.totalProgression ?: current.locations.progression ?: 0.0)
                    .toFloat().coerceIn(0f, 1f)
                ReadingProgress(chapterIndex = chapterIndex, fraction = fraction, epubLocatorJson = current.toJSON().toString())
            }
            if (currentBook != null && (progress != null || seconds > 0)) {
                repository.enqueueReadingExitSnapshot(currentBook.id, progress, seconds, readingLogId)
            }
        } finally {
            super.onPause()
        }
    }

    override fun onDestroy() {
        chromeHideJob?.cancel()
        WindowCompat.getInsetsController(window, window.decorView).apply {
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_DEFAULT
            show(WindowInsetsCompat.Type.systemBars())
        }
        saveJob?.cancel()
        navigator = null
        try {
            super.onDestroy()
        } finally {
            session?.close()
            session = null
        }
    }

    private fun edgeScrim(top: Boolean) = View(this).apply {
        isClickable = false
        isFocusable = false
        importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
        background = GradientDrawable(
            GradientDrawable.Orientation.TOP_BOTTOM,
            if (top) intArrayOf(android.graphics.Color.BLACK, android.graphics.Color.TRANSPARENT)
            else intArrayOf(android.graphics.Color.TRANSPARENT, android.graphics.Color.BLACK),
        )
    }

    private fun chromeButton(icon: Int, description: Int, onClick: () -> Unit) = ImageButton(this).apply {
        chromeButtons += this
        setImageResource(icon)
        contentDescription = getString(description)
        val ripple = android.util.TypedValue()
        if (theme.resolveAttribute(android.R.attr.selectableItemBackgroundBorderless, ripple, true)) {
            setBackgroundResource(ripple.resourceId)
        } else {
            background = null
        }
        setPadding(dp(12), dp(12), dp(12), dp(12))
        setOnClickListener {
            scheduleReaderChromeAutoHide()
            onClick()
        }
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    private data class TocRow(val link: Link, val depth: Int)

    companion object {
        const val EXTRA_BOOK_ID = "com.wongpinter.goatreader.extra.READIUM_BOOK_ID"
        const val EXTRA_QUOTE_ID = "com.wongpinter.goatreader.extra.READIUM_QUOTE_ID"
        const val EXTRA_BOOKMARK_ID = "com.wongpinter.goatreader.extra.READIUM_BOOKMARK_ID"
        private const val NAVIGATOR_TAG = "goat-reader-readium-epub"
        private const val FRAGMENT_STATE_KEY = "android:support:fragments"
        private const val SAVED_STATE_REGISTRY_KEY = "androidx.lifecycle.BundlableSavedStateRegistry.key"
        private const val STATE_LOCATOR_JSON = "goat-reader.epub.saved-locator"
        private const val PROGRESS_SAVE_DELAY_MILLIS = 1_200L
        private const val READER_CHROME_AUTO_HIDE_DELAY_MILLIS = 3_500L
        private const val READER_CHROME_ANIMATION_MILLIS = 220L
        private const val END_CLEARANCE_CSS_PX = 176
        private const val TAG = "GoatReaderEpub"
    }
}
