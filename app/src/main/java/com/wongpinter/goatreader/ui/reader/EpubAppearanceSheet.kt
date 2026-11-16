package com.wongpinter.goatreader.ui.reader

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import com.wongpinter.goatreader.R
import com.wongpinter.goatreader.data.AppSettings
import com.wongpinter.goatreader.data.AppTheme
import com.wongpinter.goatreader.data.effectiveTheme
import com.wongpinter.goatreader.data.PageMargins
import com.wongpinter.goatreader.data.ReaderFont
import com.wongpinter.goatreader.ui.theme.AtkinsonHyperlegibleNextFamily
import com.wongpinter.goatreader.data.ReaderSettings
import com.wongpinter.goatreader.data.ReaderTextAlignment
import com.wongpinter.goatreader.data.ReadingMode
import com.wongpinter.goatreader.data.ChapterPull
import java.util.Locale
import kotlinx.coroutines.launch
import kotlin.math.roundToInt
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun EpubAppearanceSheet(
    visible: Boolean,
    settings: ReaderSettings,
    appSettings: AppSettings,
    reflowable: Boolean,
    onSettingsChange: (((ReaderSettings) -> ReaderSettings) -> Unit),
    onAppSettingsChange: (((AppSettings) -> AppSettings) -> Unit),
    onDismissRequest: () -> Unit,
) {
    if (!visible) return

    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()
    val maxHeight = LocalConfiguration.current.screenHeightDp.dp * 0.92f
    ModalBottomSheet(
        onDismissRequest = onDismissRequest,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface,
        tonalElevation = 3.dp,
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .heightIn(max = maxHeight)
                .verticalScroll(rememberScrollState())
                .padding(start = 24.dp, end = 24.dp, bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Column(Modifier.weight(1f)) {
                    Text("Reading appearance", style = MaterialTheme.typography.titleLarge)
                    Text(
                        "Text and layout are saved per book. Page theme applies throughout the app.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                IconButton(
                    onClick = {
                        scope.launch {
                            sheetState.hide()
                            onDismissRequest()
                        }
                    },
                    modifier = Modifier.size(48.dp),
                ) {
                    Text(
                        "×",
                        modifier = Modifier.semantics { contentDescription = "Close appearance settings" },
                        style = MaterialTheme.typography.headlineSmall,
                    )
                }
            }

            if (!reflowable) {
                Card(Modifier.fillMaxWidth()) {
                    Text(
                        "This EPUB uses fixed pages. Its page design is preserved, so text and spacing controls are unavailable.",
                        Modifier.padding(16.dp),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            } else {
                AppearancePreview(settings)

                AppearanceSection(stringResource(R.string.reader_quick_styles)) {
                    val presets = ReaderStylePreset.entries
                    val labels = listOf(
                        stringResource(R.string.reader_preset_compact),
                        stringResource(R.string.reader_preset_comfortable),
                        stringResource(R.string.reader_preset_large_text),
                    )
                    val selected = presets.indexOfFirst(settings::matches)
                    BoxWithConstraints(Modifier.fillMaxWidth()) {
                        val stacked = maxWidth < 360.dp || LocalConfiguration.current.fontScale > 1.25f
                        if (stacked) {
                            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                presets.forEachIndexed { index, preset ->
                                    FilterChip(
                                        selected = selected == index,
                                        onClick = {
                                            editTypography(onSettingsChange) { it.withPreset(preset) }
                                        },
                                        label = { Text(labels[index], maxLines = 1, overflow = TextOverflow.Ellipsis) },
                                        modifier = Modifier.fillMaxWidth(),
                                    )
                                }
                            }
                        } else {
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                presets.forEachIndexed { index, preset ->
                                    FilterChip(
                                        selected = selected == index,
                                        onClick = {
                                            editTypography(onSettingsChange) { it.withPreset(preset) }
                                        },
                                        label = { Text(labels[index], maxLines = 1, overflow = TextOverflow.Ellipsis) },
                                        modifier = Modifier.weight(1f),
                                    )
                                }
                            }
                        }
                    }
                    Text(
                        stringResource(R.string.reader_quick_styles_hint),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                AppearanceSection("App page theme") {
                    val activeTheme = appSettings.effectiveTheme(isSystemInDarkTheme())
                    val labels = listOf(
                        AppTheme.WHITE to stringResource(R.string.theme_light),
                        AppTheme.SEPIA to stringResource(R.string.theme_beige),
                        AppTheme.OLED to stringResource(R.string.theme_midnight),
                    )
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        labels.forEach { (theme, label) ->
                            FilterChip(
                                selected = activeTheme == theme,
                                onClick = {
                                    onAppSettingsChange {
                                        it.copy(theme = theme, followSystemTheme = false)
                                    }
                                },
                                label = { Text(label, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                                modifier = Modifier.weight(1f),
                            )
                        }
                    }
                }

                AppearanceSection("Font") {
                    val fonts = listOf(
                        ReaderFont.SERIF to "Serif",
                        ReaderFont.ATKINSON to stringResource(R.string.reader_font_atkinson),
                        ReaderFont.SANS to "Sans",
                    )
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        fonts.forEach { (font, label) ->
                            FilterChip(
                                selected = when (font) {
                                    ReaderFont.SERIF -> settings.font in setOf(ReaderFont.LITERATA, ReaderFont.SERIF, ReaderFont.CUSTOM)
                                    ReaderFont.ATKINSON -> settings.font == ReaderFont.ATKINSON
                                    else -> settings.font == ReaderFont.SANS
                                },
                                onClick = { editTypography(onSettingsChange) { it.copy(font = font) } },
                                label = {
                                    Text(
                                        label,
                                        fontFamily = fontFamily(font),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                },
                                modifier = Modifier.weight(1f),
                            )
                        }
                    }
                    if (settings.font == ReaderFont.ATKINSON) {
                        Text(
                            stringResource(R.string.reader_font_atkinson_description),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    if (settings.font == ReaderFont.CUSTOM) {
                        Text(
                            "Your saved custom font is unavailable in the EPUB renderer. Choose Serif, Atkinson, or Sans to use a bundled font.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    StepSlider(
                        label = "Text size",
                        valueLabel = "${settings.fontSizeSp.roundToInt()} sp",
                        value = settings.fontSizeSp.coerceIn(12f, 32f),
                        range = 12f..32f,
                        steps = 19,
                        onValueChange = { value ->
                            editTypography(onSettingsChange) {
                                it.copy(fontSizeSp = value.roundToInt().toFloat())
                            }
                        },
                    )
                }

                AppearanceSection("Spacing and layout") {
                    StepSlider(
                        label = "Line spacing",
                        valueLabel = String.format(Locale.getDefault(), "%.1f×", settings.lineHeight),
                        value = settings.lineHeight.coerceIn(1f, 2f),
                        range = 1f..2f,
                        steps = 9,
                        onValueChange = { value ->
                            editTypography(onSettingsChange) {
                                it.copy(lineHeight = (value * 10f).roundToInt() / 10f)
                            }
                        },
                    )
                    StepSlider(
                        label = "Side margins",
                        valueLabel = "${settings.sideMarginDp} dp",
                        value = settings.sideMarginDp.toFloat(),
                        range = ReaderSettings.MIN_SIDE_MARGIN_DP.toFloat()..ReaderSettings.MAX_SIDE_MARGIN_DP.toFloat(),
                        steps = 19,
                        onValueChange = { value ->
                            editTypography(onSettingsChange) {
                                it.copy(pageMarginDp = value.roundToInt(), pageMargins = PageMargins.NORMAL)
                            }
                        },
                    )
                    AppearanceSection(stringResource(R.string.reader_text_alignment)) {
                        val alignments = ReaderTextAlignment.entries
                        val labels = listOf(
                            stringResource(R.string.reader_alignment_start),
                            stringResource(R.string.reader_alignment_justified),
                        )
                        BoxWithConstraints(Modifier.fillMaxWidth()) {
                            val stacked = maxWidth < 360.dp || LocalConfiguration.current.fontScale > 1.25f
                            if (stacked) {
                                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    alignments.forEachIndexed { index, alignment ->
                                        FilterChip(
                                            selected = settings.textAlignment == alignment,
                                            onClick = {
                                                onSettingsChange { it.copy(textAlignment = alignment) }
                                            },
                                            label = { Text(labels[index], maxLines = 1, overflow = TextOverflow.Ellipsis) },
                                            modifier = Modifier.fillMaxWidth(),
                                        )
                                    }
                                }
                            } else {
                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    alignments.forEachIndexed { index, alignment ->
                                        FilterChip(
                                            selected = settings.textAlignment == alignment,
                                            onClick = {
                                                onSettingsChange { it.copy(textAlignment = alignment) }
                                            },
                                            label = { Text(labels[index], maxLines = 1, overflow = TextOverflow.Ellipsis) },
                                            modifier = Modifier.weight(1f),
                                        )
                                    }
                                }
                            }
                        }
                        Text(
                            stringResource(R.string.reader_alignment_hint),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Text(
                        "EPUB margins scale with the page width.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text("Reading flow", style = MaterialTheme.typography.titleSmall)
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(
                            selected = settings.readingMode == ReadingMode.PAGES,
                            onClick = { editTypography(onSettingsChange) { it.copy(readingMode = ReadingMode.PAGES) } },
                            label = { Text("Pages") },
                            modifier = Modifier.weight(1f),
                        )
                        FilterChip(
                            selected = settings.readingMode == ReadingMode.SCROLL,
                            onClick = { editTypography(onSettingsChange) { it.copy(readingMode = ReadingMode.SCROLL) } },
                            label = { Text("Scroll") },
                            modifier = Modifier.weight(1f),
                        )
                    }
                    if (settings.readingMode == ReadingMode.SCROLL) {
                        Text(stringResource(R.string.reader_chapter_pull), style = MaterialTheme.typography.titleSmall)
                        val pulls = ChapterPull.entries
                        listOf(
                            R.string.reader_chapter_pull_off,
                            R.string.reader_chapter_pull_deliberate,
                            R.string.reader_chapter_pull_extra,
                        ).forEachIndexed { index, label ->
                            FilterChip(
                                selected = settings.chapterPull == pulls[index],
                                onClick = { onSettingsChange { it.copy(chapterPull = pulls[index]) } },
                                label = { Text(stringResource(label)) },
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }
                        Text(
                            stringResource(R.string.reader_chapter_pull_hint),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }

                HorizontalDivider()
                Text(
                    "Publisher formatting is disabled for EPUB reading so its justified paragraphs cannot stretch word gaps.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun AppearancePreview(settings: ReaderSettings) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("TYPE PREVIEW", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
            Text(
                "A quiet page, made comfortable for a longer read.",
                style = MaterialTheme.typography.bodyLarge.copy(
                    fontFamily = fontFamily(settings.font),
                    fontSize = settings.fontSizeSp.coerceIn(12f, 32f).sp,
                    lineHeight = (settings.fontSizeSp * settings.lineHeight).coerceIn(12f, 64f).sp,
                ),
            )
        }
    }
}

@Composable
private fun AppearanceSection(title: String, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(title, style = MaterialTheme.typography.titleMedium)
        content()
    }
}

@Composable
private fun StepSlider(
    label: String,
    valueLabel: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    steps: Int,
    onValueChange: (Float) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
            Text(valueLabel, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Slider(
            value = value,
            onValueChange = onValueChange,
            valueRange = range,
            steps = steps,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 48.dp)
                .semantics { contentDescription = label },
        )
    }
}

private fun editTypography(
    onSettingsChange: (((ReaderSettings) -> ReaderSettings) -> Unit),
    transform: (ReaderSettings) -> ReaderSettings,
) {
    onSettingsChange { current -> transform(current).copy(bookStyles = false) }
}

private fun fontFamily(font: ReaderFont): FontFamily = when (font) {
    ReaderFont.LITERATA, ReaderFont.SERIF, ReaderFont.CUSTOM -> FontFamily.Serif
    ReaderFont.SANS -> FontFamily.SansSerif
    ReaderFont.ATKINSON -> AtkinsonHyperlegibleNextFamily
}
