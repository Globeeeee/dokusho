package eu.kanade.tachiyomi.ui.reader.viewer.novel

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.VerticalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Slider
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import eu.kanade.tachiyomi.ui.reader.loader.NovelPage
import eu.kanade.tachiyomi.ui.reader.model.ViewerChapters
import eu.kanade.tachiyomi.ui.reader.setting.ReaderPreferences
import eu.kanade.tachiyomi.ui.reader.setting.ReaderPreferences.NovelFont
import eu.kanade.tachiyomi.ui.reader.setting.ReaderPreferences.NovelReadingMode
import eu.kanade.tachiyomi.ui.reader.setting.ReaderPreferences.NovelTheme
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.i18n.stringResource
import tachiyomi.presentation.core.util.collectAsState
import kotlin.math.roundToInt

private val HEADER_HEIGHT = 40.dp
private val FOOTER_HEIGHT = 32.dp

// Velocity (px/s) of a fling past the chapter end that opens the next chapter.
private const val FLING_TO_NEXT = 2500f

private data class NovelColors(val background: Color, val text: Color, val muted: Color)

/** AUTO follows General > Background color: 0 white, 1 black, 2 gray, 3 follow the system. */
private fun NovelTheme.resolve(readerTheme: Int, systemDark: Boolean): NovelTheme = when {
    this != NovelTheme.AUTO -> this
    readerTheme == 1 -> NovelTheme.BLACK
    readerTheme == 2 -> NovelTheme.DARK
    readerTheme == 3 && systemDark -> NovelTheme.BLACK
    else -> NovelTheme.PAPER
}

private fun NovelTheme.colors() = when (this) {
    NovelTheme.AUTO, NovelTheme.PAPER -> NovelColors(Color(0xFFFBF6EC), Color(0xFF2B2620), Color(0xFF8A8173))
    NovelTheme.SEPIA -> NovelColors(Color(0xFFF2E5C9), Color(0xFF4A3B28), Color(0xFF8C7656))
    NovelTheme.DARK -> NovelColors(Color(0xFF1E1E1E), Color(0xFFD9D4CC), Color(0xFF8E8A84))
    NovelTheme.BLACK -> NovelColors(Color.Black, Color(0xFFC8C8C8), Color(0xFF7A7A7A))
}

private data class NovelStyles(val body: TextStyle, val heading: TextStyle, val quote: TextStyle, val spacing: Int)

@Composable
internal fun NovelReaderContent(
    chapters: ViewerChapters,
    preferences: ReaderPreferences,
    menuVisible: Boolean,
    jumps: SharedFlow<Int>,
    commands: MutableSharedFlow<NovelCommand>,
    actions: NovelReaderActions,
) {
    val fontSize by preferences.novelFontSize.collectAsState()
    val lineHeight by preferences.novelLineHeight.collectAsState()
    val margin by preferences.novelHorizontalMargin.collectAsState()
    val theme by preferences.novelTheme.collectAsState()
    val mode by preferences.novelReadingMode.collectAsState()
    val font by preferences.novelFont.collectAsState()
    val readerTheme by preferences.readerTheme.collectAsState()
    val showPageNumber by preferences.showPageNumber.collectAsState()
    var showSettings by rememberSaveable { mutableStateOf(false) }

    val colors = theme.resolve(readerTheme, isSystemInDarkTheme()).colors()
    val styles = remember(fontSize, lineHeight, colors, font) {
        val body = TextStyle(
            fontSize = fontSize.sp,
            lineHeight = (fontSize * lineHeight).sp,
            fontFamily = when (font) {
                NovelFont.SERIF -> FontFamily.Serif
                NovelFont.SANS -> FontFamily.SansSerif
                NovelFont.MONO -> FontFamily.Monospace
                NovelFont.SYSTEM -> FontFamily.Default
            },
            color = colors.text,
        )
        NovelStyles(
            body = body,
            heading = body.copy(
                fontSize = (fontSize * 1.3f).sp,
                lineHeight = (fontSize * 1.3f * 1.3f).sp,
                fontWeight = FontWeight.Bold,
            ),
            quote = body.copy(fontStyle = FontStyle.Italic),
            spacing = (fontSize * 0.8f).roundToInt(),
        )
    }

    val chapter = chapters.currChapter
    val blocks = remember(chapter) { chapter.pages.orEmpty().filterIsInstance<NovelPage>().map { it.block } }
    val title = chapter.chapter.name
    val hasNext = chapters.nextChapter != null
    val hasPrevious = chapters.prevChapter != null

    Box(
        Modifier
            .fillMaxSize()
            .background(colors.background),
    ) {
        // Key on the chapter so scroll and pager state start fresh at the requested paragraph.
        key(chapter, mode) {
            val start = chapter.requestedPage.coerceIn(0, (blocks.size - 1).coerceAtLeast(0))
            if (mode == NovelReadingMode.SCROLL) {
                ScrollReader(
                    blocks, title, start, styles, colors, margin, hasNext, hasPrevious, showPageNumber, jumps, commands,
                    actions,
                )
            } else {
                PagedReader(
                    blocks, title, start, styles, colors, margin, mode, hasNext, hasPrevious, showPageNumber, jumps,
                    commands, actions,
                )
            }
        }

        if (menuVisible) {
            SmallFloatingActionButton(
                onClick = { showSettings = true },
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(top = 96.dp, end = 16.dp),
            ) {
                Text("Aa", fontWeight = FontWeight.Bold)
            }
        }
    }

    if (showSettings) {
        NovelSettingsSheet(preferences = preferences, onDismiss = { showSettings = false })
    }
}

@Composable
private fun ScrollReader(
    blocks: List<NovelBlock>,
    title: String,
    start: Int,
    styles: NovelStyles,
    colors: NovelColors,
    margin: Int,
    hasNext: Boolean,
    hasPrevious: Boolean,
    showPageNumber: Boolean,
    jumps: SharedFlow<Int>,
    commands: MutableSharedFlow<NovelCommand>,
    actions: NovelReaderActions,
) {
    // Item 0 is the chapter title, items 1..n the blocks, then the end-of-chapter footer.
    val listState = rememberLazyListState(initialFirstVisibleItemIndex = if (start > 0) start + 1 else 0)
    val density = LocalDensity.current
    // Pulling past the end of the chapter by this much opens the next one, like Mihon's webtoon mode.
    val continuePx = with(density) { 96.dp.toPx() }
    val continueToNext = remember(listState, hasNext) {
        object : NestedScrollConnection {
            private var pulled = 0f
            private var done = false

            override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset {
                if (!hasNext || done) return Offset.Zero
                if (available.y < 0 && !listState.canScrollForward) {
                    pulled -= available.y
                    if (pulled >= continuePx) {
                        done = true
                        actions.onNextChapter()
                    }
                } else if (available.y > 0 || listState.canScrollForward) {
                    pulled = 0f
                }
                return Offset.Zero
            }

            override suspend fun onPostFling(consumed: Velocity, available: Velocity): Velocity {
                if (hasNext && !done && available.y < -FLING_TO_NEXT && !listState.canScrollForward) {
                    done = true
                    actions.onNextChapter()
                }
                return Velocity.Zero
            }
        }
    }
    var progress by remember { mutableIntStateOf(0) }

    LaunchedEffect(listState, blocks) {
        snapshotFlow {
            if (!listState.canScrollForward) {
                blocks.lastIndex
            } else {
                (listState.firstVisibleItemIndex - 1).coerceAtLeast(
                    0,
                )
            }
        }
            .distinctUntilChanged()
            .collect { index ->
                actions.onBlockShown(index)
                progress = if (blocks.isEmpty()) 0 else (index + 1) * 100 / blocks.size
                if (blocks.isNotEmpty() && index >= blocks.size * 6 / 10) actions.onPreloadNext()
            }
    }
    LaunchedEffect(listState) { jumps.collect { listState.scrollToItem(it + 1) } }
    LaunchedEffect(listState) {
        commands.collect { command ->
            val step = listState.layoutInfo.viewportSize.height * 0.85f
            listState.animateScrollBy(if (command == NovelCommand.Next) step else -step)
        }
    }

    Box(Modifier.fillMaxSize()) {
        LazyColumn(
            state = listState,
            contentPadding = PaddingValues(horizontal = margin.dp, vertical = 32.dp),
            verticalArrangement = Arrangement.spacedBy(styles.spacing.dp),
            modifier = Modifier
                .fillMaxSize()
                .nestedScroll(continueToNext)
                .tapZones(vertical = false, reversed = false, actions = actions, onPrevious = {
                    commands.tryEmit(NovelCommand.Previous)
                }, onNext = { commands.tryEmit(NovelCommand.Next) }),
        ) {
            item { Text(title, style = styles.heading, modifier = Modifier.padding(bottom = 8.dp)) }
            itemsIndexed(blocks) { _, block -> BlockText(block, styles, colors) }
            item { ChapterEnd(colors, hasNext, hasPrevious, actions) }
        }
        if (showPageNumber) {
            Text(
                "$progress%",
                color = colors.muted,
                style = MaterialTheme.typography.labelSmall,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 8.dp),
            )
        }
    }
}

@Composable
private fun PagedReader(
    blocks: List<NovelBlock>,
    title: String,
    start: Int,
    styles: NovelStyles,
    colors: NovelColors,
    margin: Int,
    mode: NovelReadingMode,
    hasNext: Boolean,
    hasPrevious: Boolean,
    showPageNumber: Boolean,
    jumps: SharedFlow<Int>,
    commands: MutableSharedFlow<NovelCommand>,
    actions: NovelReaderActions,
) {
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val density = LocalDensity.current
        val measurer = rememberTextMeasurer()
        val widthPx = constraints.maxWidth - with(density) { (margin * 2).dp.roundToPx() }
        val heightPx = constraints.maxHeight - with(density) { (HEADER_HEIGHT + FOOTER_HEIGHT).roundToPx() }
        val spacingPx = with(density) { styles.spacing.dp.roundToPx() }

        val pages = remember(blocks, styles, widthPx, heightPx) {
            paginate(blocks, measurer, styles, widthPx.coerceAtLeast(1), heightPx.coerceAtLeast(1), spacingPx)
        }
        // One extra page at the end for the next-chapter controls.
        val initialPage = pages.indexOfFirst { it.lastBlock >= start }.coerceAtLeast(0)
        val pagerState = rememberPagerState(initialPage = initialPage) { pages.size + 1 }

        LaunchedEffect(pagerState, pages) {
            snapshotFlow { pagerState.currentPage }.distinctUntilChanged().collect { page ->
                val block = pages.getOrNull(page)?.firstBlock ?: blocks.lastIndex
                actions.onBlockShown(block)
                if (page >= pages.size * 6 / 10) actions.onPreloadNext()
                // Turning past the last page continues into the next chapter.
                if (page == pages.size && hasNext) actions.onNextChapter()
            }
        }
        LaunchedEffect(pagerState, pages) {
            jumps.collect { index ->
                pagerState.scrollToPage(pages.indexOfFirst { it.lastBlock >= index }.coerceAtLeast(0))
            }
        }
        LaunchedEffect(pagerState) {
            commands.collect { command -> pagerState.turn(command == NovelCommand.Next) }
        }

        val reversed = mode == NovelReadingMode.PAGED_RTL
        val vertical = mode == NovelReadingMode.PAGED_VERTICAL
        val pageContent: @Composable (Int) -> Unit = { index ->
            Column(
                Modifier
                    .fillMaxSize()
                    .tapZones(
                        vertical = vertical,
                        reversed = reversed,
                        actions = actions,
                        onPrevious = { commands.tryEmit(NovelCommand.Previous) },
                        onNext = { commands.tryEmit(NovelCommand.Next) },
                    )
                    .padding(horizontal = margin.dp),
            ) {
                Box(Modifier.height(HEADER_HEIGHT).fillMaxWidth(), contentAlignment = Alignment.CenterStart) {
                    Text(title, color = colors.muted, style = MaterialTheme.typography.labelMedium, maxLines = 1)
                }
                Box(Modifier.weight(1f).fillMaxWidth()) {
                    val page = pages.getOrNull(index)
                    if (page == null) {
                        ChapterEnd(colors, hasNext, hasPrevious, actions, Modifier.align(Alignment.Center))
                    } else {
                        Column(verticalArrangement = Arrangement.spacedBy(styles.spacing.dp)) {
                            page.slices.forEach { slice -> SliceText(slice, styles, colors) }
                        }
                    }
                }
                Box(Modifier.height(FOOTER_HEIGHT).fillMaxWidth(), contentAlignment = Alignment.Center) {
                    if (showPageNumber && index < pages.size) {
                        Text(
                            "${index + 1} / ${pages.size}",
                            color = colors.muted,
                            style = MaterialTheme.typography.labelSmall,
                        )
                    }
                }
            }
        }

        if (vertical) {
            VerticalPager(state = pagerState, modifier = Modifier.fillMaxSize()) { pageContent(it) }
        } else {
            HorizontalPager(state = pagerState, reverseLayout = reversed, modifier = Modifier.fillMaxSize()) {
                pageContent(it)
            }
        }
    }
}

private suspend fun PagerState.turn(forward: Boolean) {
    val target = (currentPage + if (forward) 1 else -1).coerceIn(0, pageCount - 1)
    if (target != currentPage) animateScrollToPage(target)
}

/** Left or top third goes back, right or bottom third goes forward, the middle toggles the menu. */
private fun Modifier.tapZones(
    vertical: Boolean,
    reversed: Boolean,
    actions: NovelReaderActions,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
): Modifier = pointerInput(vertical, reversed) {
    detectTapGestures { offset ->
        val fraction = if (vertical) offset.y / size.height else offset.x / size.width
        when {
            fraction < 1f / 3 -> if (reversed) onNext() else onPrevious()
            fraction > 2f / 3 -> if (reversed) onPrevious() else onNext()
            else -> actions.onToggleMenu()
        }
    }
}

@Composable
private fun BlockText(block: NovelBlock, styles: NovelStyles, colors: NovelColors) {
    when (block) {
        NovelBlock.Divider -> HorizontalDivider(Modifier.padding(vertical = 8.dp), color = colors.muted)
        is NovelBlock.Heading -> Text(block.text, style = styles.heading)
        is NovelBlock.Quote -> Text(block.text, style = styles.quote)
        is NovelBlock.Paragraph -> Text(block.text, style = styles.body)
        is NovelBlock.Image -> AsyncImage(
            model = block.url,
            contentDescription = null,
            contentScale = ContentScale.FillWidth,
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp),
        )
    }
}

@Composable
private fun SliceText(slice: PageSlice, styles: NovelStyles, colors: NovelColors) {
    when (slice.kind) {
        SliceKind.Divider -> HorizontalDivider(Modifier.padding(vertical = 8.dp), color = colors.muted)
        SliceKind.Heading -> Text(slice.text, style = styles.heading)
        SliceKind.Quote -> Text(slice.text, style = styles.quote)
        SliceKind.Body -> Text(slice.text, style = styles.body)
        SliceKind.Image -> AsyncImage(
            model = slice.url,
            contentDescription = null,
            contentScale = ContentScale.Fit,
            modifier = Modifier.fillMaxSize(),
        )
    }
}

@Composable
private fun ChapterEnd(
    colors: NovelColors,
    hasNext: Boolean,
    hasPrevious: Boolean,
    actions: NovelReaderActions,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 48.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(stringResource(MR.strings.novel_end_of_chapter), color = colors.muted)
        if (hasNext) {
            Button(onClick = actions::onNextChapter) { Text(stringResource(MR.strings.novel_next_chapter)) }
        } else {
            Text(stringResource(MR.strings.novel_no_next_chapter), color = colors.text)
        }
        if (hasPrevious) {
            TextButton(onClick = actions::onPreviousChapter) { Text(stringResource(MR.strings.novel_previous_chapter)) }
        }
    }
}

// Pagination

private enum class SliceKind { Body, Heading, Quote, Divider, Image }

private data class PageSlice(
    val block: Int,
    val kind: SliceKind,
    val text: AnnotatedString,
    val url: String? = null,
)

private data class TextPage(val slices: List<PageSlice>) {
    val firstBlock get() = slices.first().block
    val lastBlock get() = slices.last().block
}

/**
 * Splits [blocks] into pages that fit [heightPx], breaking long paragraphs at line boundaries.
 * Measures with the same styles and width the page renders with.
 */
private fun paginate(
    blocks: List<NovelBlock>,
    measurer: TextMeasurer,
    styles: NovelStyles,
    widthPx: Int,
    heightPx: Int,
    spacingPx: Int,
): List<TextPage> {
    val pages = mutableListOf<TextPage>()
    val current = mutableListOf<PageSlice>()
    var used = 0

    fun newPage() {
        if (current.isNotEmpty()) pages += TextPage(current.toList())
        current.clear()
        used = 0
    }

    blocks.forEachIndexed { index, block ->
        if (block is NovelBlock.Image) {
            newPage()
            current += PageSlice(index, SliceKind.Image, AnnotatedString(""), block.url)
            newPage()
            return@forEachIndexed
        }
        if (block is NovelBlock.Divider) {
            val dividerHeight = spacingPx * 2
            if (used + dividerHeight > heightPx) newPage()
            current += PageSlice(index, SliceKind.Divider, AnnotatedString(""))
            used += dividerHeight + spacingPx
            return@forEachIndexed
        }
        val (kind, style) = when (block) {
            is NovelBlock.Heading -> SliceKind.Heading to styles.heading
            is NovelBlock.Quote -> SliceKind.Quote to styles.quote
            else -> SliceKind.Body to styles.body
        }
        var remaining = block.text
        while (remaining.isNotEmpty()) {
            val layout = measurer.measure(remaining, style, constraints = Constraints(maxWidth = widthPx))
            val available = heightPx - used
            if (layout.size.height <= available) {
                current += PageSlice(index, kind, remaining)
                used += layout.size.height + spacingPx
                break
            }
            var fit = 0
            while (fit < layout.lineCount && layout.getLineBottom(fit) <= available) fit++
            if (fit == 0) {
                if (current.isNotEmpty()) {
                    newPage()
                    continue
                }
                fit = 1 // a single line taller than the page; show it anyway
            }
            val end = layout.getLineEnd(fit - 1, visibleEnd = false).coerceAtLeast(1)
            current += PageSlice(index, kind, remaining.subSequence(0, end))
            newPage()
            remaining = remaining.subSequence(end, remaining.length).let { rest ->
                val skip = rest.text.indexOfFirst { !it.isWhitespace() }
                if (skip < 0) AnnotatedString("") else rest.subSequence(skip, rest.length)
            }
        }
    }
    newPage()
    return pages
}

// Settings

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun NovelSettingsSheet(preferences: ReaderPreferences, onDismiss: () -> Unit) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(stringResource(MR.strings.novel_text_settings), style = MaterialTheme.typography.titleMedium)
            NovelSettingsPage(preferences)
        }
    }
}

/** Novel reading options; also shown in the reader settings dialog's Reading mode tab. */
@Composable
internal fun ColumnScope.NovelSettingsPage(preferences: ReaderPreferences) {
    val fontSize by preferences.novelFontSize.collectAsState()
    val lineHeight by preferences.novelLineHeight.collectAsState()
    val margin by preferences.novelHorizontalMargin.collectAsState()
    val theme by preferences.novelTheme.collectAsState()
    val mode by preferences.novelReadingMode.collectAsState()
    val font by preferences.novelFont.collectAsState()

    Column(
        Modifier.padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Label(stringResource(MR.strings.novel_reading_mode))
        ChipRow(
            options = listOf(
                NovelReadingMode.SCROLL to stringResource(MR.strings.novel_mode_scroll),
                NovelReadingMode.PAGED_LTR to stringResource(MR.strings.novel_mode_paged_ltr),
                NovelReadingMode.PAGED_RTL to stringResource(MR.strings.novel_mode_paged_rtl),
                NovelReadingMode.PAGED_VERTICAL to stringResource(MR.strings.novel_mode_paged_vertical),
            ),
            selected = mode,
            onSelect = { preferences.novelReadingMode.set(it) },
        )

        Label("${stringResource(MR.strings.novel_font_size)}: $fontSize")
        Slider(
            value = fontSize.toFloat(),
            onValueChange = { preferences.novelFontSize.set(it.roundToInt()) },
            valueRange = 12f..32f,
            steps = 19,
        )

        Label("${stringResource(MR.strings.novel_line_spacing)}: ${"%.1f".format(lineHeight)}")
        Slider(
            value = lineHeight,
            onValueChange = { preferences.novelLineHeight.set((it * 10).roundToInt() / 10f) },
            valueRange = 1.2f..2.2f,
            steps = 9,
        )

        Label("${stringResource(MR.strings.novel_margins)}: $margin")
        Slider(
            value = margin.toFloat(),
            onValueChange = { preferences.novelHorizontalMargin.set(it.roundToInt()) },
            valueRange = 8f..48f,
            steps = 9,
        )

        Label(stringResource(MR.strings.novel_font))
        ChipRow(
            options = listOf(
                NovelFont.SERIF to stringResource(MR.strings.novel_font_serif),
                NovelFont.SANS to stringResource(MR.strings.novel_font_sans),
                NovelFont.MONO to stringResource(MR.strings.novel_font_mono),
                NovelFont.SYSTEM to stringResource(MR.strings.novel_font_system),
            ),
            selected = font,
            onSelect = { preferences.novelFont.set(it) },
        )

        Label(stringResource(MR.strings.novel_theme))
        ChipRow(
            options = listOf(
                NovelTheme.AUTO to stringResource(MR.strings.novel_theme_auto),
                NovelTheme.PAPER to stringResource(MR.strings.novel_theme_paper),
                NovelTheme.SEPIA to stringResource(MR.strings.novel_theme_sepia),
                NovelTheme.DARK to stringResource(MR.strings.novel_theme_dark),
                NovelTheme.BLACK to stringResource(MR.strings.novel_theme_black),
            ),
            selected = theme,
            onSelect = { preferences.novelTheme.set(it) },
        )
    }
}

@Composable
private fun Label(text: String) {
    Spacer(Modifier.height(8.dp))
    Text(text, style = MaterialTheme.typography.labelLarge)
}

@Composable
private fun <T> ChipRow(options: List<Pair<T, String>>, selected: T, onSelect: (T) -> Unit) {
    Row(
        Modifier.horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        options.forEach { (value, label) ->
            FilterChip(selected = value == selected, onClick = { onSelect(value) }, label = { Text(label) })
        }
        Spacer(Modifier.width(8.dp))
    }
}
