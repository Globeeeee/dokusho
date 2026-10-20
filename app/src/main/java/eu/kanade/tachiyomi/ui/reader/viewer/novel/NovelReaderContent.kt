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
import androidx.compose.foundation.lazy.items
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
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextIndent
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import dev.icerock.moko.resources.StringResource
import eu.kanade.tachiyomi.ui.reader.loader.NovelPage
import eu.kanade.tachiyomi.ui.reader.model.ReaderChapter
import eu.kanade.tachiyomi.ui.reader.model.ViewerChapters
import eu.kanade.tachiyomi.ui.reader.setting.ReaderPreferences
import eu.kanade.tachiyomi.ui.reader.setting.ReaderPreferences.NovelFont
import eu.kanade.tachiyomi.ui.reader.setting.ReaderPreferences.NovelReadingMode
import eu.kanade.tachiyomi.ui.reader.setting.ReaderPreferences.NovelTheme
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import tachiyomi.core.common.preference.Preference
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.components.CheckboxItem
import tachiyomi.presentation.core.components.HeadingItem
import tachiyomi.presentation.core.components.SettingsChipRow
import tachiyomi.presentation.core.components.SliderItem
import tachiyomi.presentation.core.i18n.stringResource
import tachiyomi.presentation.core.util.collectAsState
import kotlin.math.roundToInt

private val HEADER_HEIGHT = 40.dp
private val FOOTER_HEIGHT = 32.dp

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
    val paragraphSpacing by preferences.novelParagraphSpacing.collectAsState()
    val justify by preferences.novelJustify.collectAsState()
    val indent by preferences.novelIndent.collectAsState()
    val customColors by preferences.novelCustomColors.collectAsState()
    val customBackground by preferences.novelBackgroundColor.collectAsState()
    val customText by preferences.novelTextColor.collectAsState()
    val readerTheme by preferences.readerTheme.collectAsState()
    val showPageNumber by preferences.showPageNumber.collectAsState()
    var showSettings by rememberSaveable { mutableStateOf(false) }

    val themeColors = theme.resolve(readerTheme, isSystemInDarkTheme()).colors()
    val colors = if (customColors) {
        val text = Color(customText)
        NovelColors(Color(customBackground), text, text.copy(alpha = 0.6f))
    } else {
        themeColors
    }
    val styles = remember(fontSize, lineHeight, colors, font, paragraphSpacing, justify, indent) {
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
            textAlign = if (justify) TextAlign.Justify else TextAlign.Start,
            textIndent = if (indent) TextIndent(firstLine = (fontSize * 1.5f).sp) else TextIndent.None,
        )
        NovelStyles(
            body = body,
            heading = body.copy(
                fontSize = (fontSize * 1.3f).sp,
                lineHeight = (fontSize * 1.3f * 1.3f).sp,
                fontWeight = FontWeight.Bold,
            ),
            quote = body.copy(fontStyle = FontStyle.Italic),
            spacing = paragraphSpacing,
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
        if (mode == NovelReadingMode.SCROLL) {
            // Not keyed on the chapter: the list spans neighbouring chapters and keeps its position.
            ContinuousScrollReader(chapters, styles, colors, margin, showPageNumber, jumps, commands, actions)
        } else {
            // Key on the chapter so the pager starts fresh at the requested paragraph.
            key(chapter, mode) {
                val start = chapter.requestedPage.coerceIn(0, (blocks.size - 1).coerceAtLeast(0))
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

private sealed interface ScrollItem {
    val key: String
}

private class ChapterTitleItem(val chapter: ReaderChapter) : ScrollItem {
    override val key = "title-${chapter.chapter.id}"
}

private class BlockItem(val chapter: ReaderChapter, val index: Int, val block: NovelBlock) : ScrollItem {
    override val key = "block-${chapter.chapter.id}-$index"
}

private class TailItem(val loading: Boolean) : ScrollItem {
    override val key = "tail"
    val message get() = if (loading) MR.strings.novel_loading_chapter else MR.strings.novel_no_next_chapter
}

/** Previous, current and next chapters in one list, in reading order, for the ones already loaded. */
private fun buildScrollItems(chapters: ViewerChapters): List<ScrollItem> = buildList {
    listOfNotNull(chapters.prevChapter, chapters.currChapter, chapters.nextChapter).forEach { chapter ->
        val blocks = chapter.pages?.filterIsInstance<NovelPage>() ?: return@forEach
        add(ChapterTitleItem(chapter))
        blocks.forEach { add(BlockItem(chapter, it.index, it.block)) }
    }
    val next = chapters.nextChapter
    when {
        next == null -> add(TailItem(loading = false))
        next.pages == null -> add(TailItem(loading = true))
    }
}

/**
 * Scroll mode as one continuous strip across chapters, like the webtoon viewer: scrolling past the
 * end of a chapter runs straight into the next one, and up into the previous one.
 */
@Composable
private fun ContinuousScrollReader(
    chapters: ViewerChapters,
    styles: NovelStyles,
    colors: NovelColors,
    margin: Int,
    showPageNumber: Boolean,
    jumps: SharedFlow<Int>,
    commands: MutableSharedFlow<NovelCommand>,
    actions: NovelReaderActions,
) {
    // Rebuild when a neighbouring chapter finishes loading, not only when the chapter set changes.
    val itemsFlow = remember(chapters) {
        val window = listOfNotNull(chapters.prevChapter, chapters.currChapter, chapters.nextChapter)
        combine(window.map { it.stateFlow }) { buildScrollItems(chapters) }
    }
    val scrollItems by itemsFlow.collectAsState(initial = buildScrollItems(chapters))
    val currentItems by rememberUpdatedState(scrollItems)
    val currentChapters by rememberUpdatedState(chapters)

    fun indexOfBlock(chapter: ReaderChapter, block: Int): Int {
        val list = currentItems
        val exact = list.indexOfFirst { it is BlockItem && it.chapter == chapter && it.index == block }
        if (exact >= 0) return exact
        return list.indexOfFirst { it is ChapterTitleItem && it.chapter == chapter }.coerceAtLeast(0)
    }

    val start = chapters.currChapter.requestedPage
    val listState = rememberLazyListState(
        initialFirstVisibleItemIndex = if (start > 0) {
            indexOfBlock(chapters.currChapter, start)
        } else {
            scrollItems.indexOfFirst { it is ChapterTitleItem && it.chapter == chapters.currChapter }.coerceAtLeast(0)
        },
    )
    var progress by remember { mutableIntStateOf(0) }
    var lastShown by remember { mutableStateOf<Pair<ReaderChapter, Int>?>(null) }

    // Report the paragraph at the top of the screen; moving into another chapter makes it current.
    LaunchedEffect(listState) {
        snapshotFlow { listState.firstVisibleItemIndex to listState.canScrollForward }
            .distinctUntilChanged()
            .collect { (first, canScrollForward) ->
                val list = currentItems
                val item = if (!canScrollForward) list.lastOrNull { it is BlockItem } else list.getOrNull(first)
                val (chapter, block) = when (item) {
                    is BlockItem -> item.chapter to item.index
                    is ChapterTitleItem -> item.chapter to 0
                    else -> return@collect
                }
                val previous = lastShown
                val window = currentChapters.run { listOfNotNull(prevChapter, currChapter, nextChapter) }
                val movedForward = previous != null && window.indexOf(chapter) > window.indexOf(previous.first)
                if (previous != null && previous.first != chapter && movedForward) {
                    // Leaving a chapter forwards: finish it first, so it is marked read even after a fast fling.
                    previous.first.pages?.lastIndex?.let { actions.onBlockShownIn(previous.first, it) }
                }
                actions.onBlockShownIn(chapter, block)
                lastShown = chapter to block
                val size = chapter.pages?.size ?: 0
                progress = if (size == 0) 0 else (block + 1) * 100 / size
                if (size > 0 && block >= size * 6 / 10) actions.onPreloadNext()
                if (block <= 2) actions.onPreloadPrevious()
            }
    }
    // A chapter chosen from the menu (not reached by scrolling) moves the list to it.
    LaunchedEffect(chapters.currChapter) {
        val visible = (currentItems.getOrNull(listState.firstVisibleItemIndex) as? BlockItem)?.chapter
            ?: (currentItems.getOrNull(listState.firstVisibleItemIndex) as? ChapterTitleItem)?.chapter
        if (visible != chapters.currChapter) {
            listState.scrollToItem(indexOfBlock(chapters.currChapter, chapters.currChapter.requestedPage))
        }
    }
    LaunchedEffect(listState) {
        jumps.collect { listState.scrollToItem(indexOfBlock(currentChapters.currChapter, it)) }
    }
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
                .tapZones(
                    vertical = false,
                    reversed = false,
                    actions = actions,
                    onPrevious = { commands.tryEmit(NovelCommand.Previous) },
                    onNext = { commands.tryEmit(NovelCommand.Next) },
                ),
        ) {
            items(scrollItems, key = { it.key }) { item ->
                when (item) {
                    is ChapterTitleItem -> Column {
                        Spacer(Modifier.height(8.dp))
                        HorizontalDivider(color = colors.muted.copy(alpha = 0.3f))
                        Spacer(Modifier.height(24.dp))
                        Text(
                            item.chapter.chapter.name,
                            style = styles.heading,
                            modifier = Modifier.padding(bottom = 8.dp),
                        )
                    }
                    is BlockItem -> BlockText(item.block, styles, colors)
                    is TailItem -> Box(
                        Modifier
                            .fillMaxWidth()
                            .padding(vertical = 48.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            stringResource(item.message),
                            color = colors.muted,
                        )
                    }
                }
            }
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
    val paragraphSpacing by preferences.novelParagraphSpacing.collectAsState()
    val margin by preferences.novelHorizontalMargin.collectAsState()
    val theme by preferences.novelTheme.collectAsState()
    val mode by preferences.novelReadingMode.collectAsState()
    val font by preferences.novelFont.collectAsState()

    SettingsChipRow(MR.strings.novel_reading_mode) {
        NovelReadingMode.entries.forEach { value ->
            FilterChip(
                selected = mode == value,
                onClick = { preferences.novelReadingMode.set(value) },
                label = { Text(stringResource(value.labelRes)) },
            )
        }
    }

    SettingsChipRow(MR.strings.novel_font) {
        listOf(
            NovelFont.SERIF to MR.strings.novel_font_serif,
            NovelFont.SANS to MR.strings.novel_font_sans,
            NovelFont.MONO to MR.strings.novel_font_mono,
            NovelFont.SYSTEM to MR.strings.novel_font_system,
        ).forEach { (value, label) ->
            FilterChip(
                selected = font == value,
                onClick = { preferences.novelFont.set(value) },
                label = { Text(stringResource(label)) },
            )
        }
    }

    SliderItem(
        value = fontSize,
        valueRange = 12..32,
        label = stringResource(MR.strings.novel_font_size),
        onChange = { preferences.novelFontSize.set(it) },
        pillColor = MaterialTheme.colorScheme.surfaceContainerHighest,
    )
    // Line spacing is stored as a float; the slider works in tenths.
    SliderItem(
        value = (lineHeight * 10).roundToInt(),
        valueRange = 12..22,
        label = stringResource(MR.strings.novel_line_spacing),
        valueString = "%.1f".format(lineHeight),
        onChange = { preferences.novelLineHeight.set(it / 10f) },
        pillColor = MaterialTheme.colorScheme.surfaceContainerHighest,
    )
    SliderItem(
        value = paragraphSpacing,
        valueRange = 0..40,
        label = stringResource(MR.strings.novel_paragraph_spacing),
        onChange = { preferences.novelParagraphSpacing.set(it) },
        pillColor = MaterialTheme.colorScheme.surfaceContainerHighest,
    )
    SliderItem(
        value = margin,
        valueRange = 8..48,
        label = stringResource(MR.strings.novel_margins),
        onChange = { preferences.novelHorizontalMargin.set(it) },
        pillColor = MaterialTheme.colorScheme.surfaceContainerHighest,
    )
    CheckboxItem(label = stringResource(MR.strings.novel_justify), pref = preferences.novelJustify)
    CheckboxItem(label = stringResource(MR.strings.novel_indent), pref = preferences.novelIndent)

    SettingsChipRow(MR.strings.novel_theme) {
        listOf(
            NovelTheme.AUTO to MR.strings.novel_theme_auto,
            NovelTheme.PAPER to MR.strings.novel_theme_paper,
            NovelTheme.SEPIA to MR.strings.novel_theme_sepia,
            NovelTheme.DARK to MR.strings.novel_theme_dark,
            NovelTheme.BLACK to MR.strings.novel_theme_black,
        ).forEach { (value, label) ->
            FilterChip(
                selected = theme == value,
                onClick = { preferences.novelTheme.set(value) },
                label = { Text(stringResource(label)) },
            )
        }
    }
}

/** Text and background colors for novels; shown at the top of the Custom filter tab. */
@Composable
internal fun ColumnScope.NovelColorSettings(preferences: ReaderPreferences) {
    val customColors by preferences.novelCustomColors.collectAsState()
    CheckboxItem(label = stringResource(MR.strings.novel_custom_colors), pref = preferences.novelCustomColors)
    if (!customColors) return

    SettingsChipRow(MR.strings.novel_color_presets) {
        COLOR_PRESETS.forEach { (label, colors) ->
            FilterChip(
                selected = false,
                onClick = {
                    preferences.novelBackgroundColor.set(colors.first)
                    preferences.novelTextColor.set(colors.second)
                },
                label = { Text(stringResource(label)) },
            )
        }
    }
    ColorSliders(MR.strings.novel_background_color, preferences.novelBackgroundColor)
    ColorSliders(MR.strings.novel_text_color, preferences.novelTextColor)
}

@Composable
private fun ColorSliders(title: StringResource, preference: Preference<Int>) {
    val color by preference.collectAsState()
    HeadingItem(title)
    listOf(
        MR.strings.color_filter_r_value to 16,
        MR.strings.color_filter_g_value to 8,
        MR.strings.color_filter_b_value to 0,
    ).forEach { (label, shift) ->
        SliderItem(
            value = (color shr shift) and 0xFF,
            valueRange = 0..255,
            steps = 0,
            label = stringResource(label),
            onChange = { value -> preference.set((color and (0xFF shl shift).inv()) or (value shl shift)) },
            pillColor = MaterialTheme.colorScheme.surfaceContainerHighest,
        )
    }
}

private val COLOR_PRESETS = listOf(
    MR.strings.novel_theme_paper to (0xFFFBF6EC.toInt() to 0xFF2B2620.toInt()),
    MR.strings.novel_theme_sepia to (0xFFF2E5C9.toInt() to 0xFF4A3B28.toInt()),
    MR.strings.novel_preset_night to (0xFF1A1612.toInt() to 0xFFE6C9A0.toInt()),
    MR.strings.novel_theme_black to (0xFF000000.toInt() to 0xFFC8C8C8.toInt()),
)
