package eu.kanade.tachiyomi.ui.reader.viewer.novel

import android.os.Build
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.DragInteraction
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.VerticalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
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
import coil3.compose.LocalPlatformContext
import coil3.gif.AnimatedImageDecoder
import coil3.network.NetworkHeaders
import coil3.network.httpHeaders
import coil3.request.ImageRequest
import dev.icerock.moko.resources.StringResource
import eu.kanade.tachiyomi.data.tts.TtsPlaybackService
import eu.kanade.tachiyomi.data.tts.TtsState
import eu.kanade.tachiyomi.data.tts.TtsStatus
import eu.kanade.tachiyomi.ui.reader.loader.NovelPage
import eu.kanade.tachiyomi.ui.reader.model.ReaderChapter
import eu.kanade.tachiyomi.ui.reader.model.ReaderPage
import eu.kanade.tachiyomi.ui.reader.model.ViewerChapters
import eu.kanade.tachiyomi.ui.reader.setting.ReaderPreferences
import eu.kanade.tachiyomi.ui.reader.setting.ReaderPreferences.NovelFont
import eu.kanade.tachiyomi.ui.reader.setting.ReaderPreferences.NovelReadingMode
import eu.kanade.tachiyomi.ui.reader.setting.ReaderPreferences.NovelTheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withTimeoutOrNull
import mihon.icons.materialsymbols.MaterialSymbols
import mihon.icons.materialsymbols.rounded.Close
import mihon.icons.materialsymbols.rounded.Schedule
import mihon.icons.materialsymbols.rounded.Settings
import mihon.icons.materialsymbols.rounded.SkipNext
import mihon.icons.materialsymbols.rounded.SkipPrevious
import mihon.icons.materialsymbols.roundedfilled.Pause
import mihon.icons.materialsymbols.roundedfilled.PlayArrow
import tachiyomi.core.common.preference.Preference
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.components.CheckboxItem
import tachiyomi.presentation.core.components.HeadingItem
import tachiyomi.presentation.core.components.SettingsChipRow
import tachiyomi.presentation.core.components.SliderItem
import tachiyomi.presentation.core.i18n.stringResource
import tachiyomi.presentation.core.util.collectAsState
import kotlin.math.abs
import kotlin.math.roundToInt

private val HEADER_HEIGHT = 40.dp
private val FOOTER_HEIGHT = 32.dp

/** [highlight] is the opaque read-aloud colour; the sentence and word use it at two strengths. */
private data class NovelColors(val background: Color, val text: Color, val muted: Color, val highlight: Color = text)

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
    jumps: SharedFlow<ReaderPage>,
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
    val highlightChoice by preferences.novelHighlight.collectAsState()
    val pageTurn by preferences.novelPageTurn.collectAsState()
    val readerTheme by preferences.readerTheme.collectAsState()
    val showPageNumber by preferences.showPageNumber.collectAsState()
    var showTtsSettings by rememberSaveable { mutableStateOf(false) }

    val themeColors = theme.resolve(readerTheme, isSystemInDarkTheme()).colors()
    val colors = if (customColors) {
        val text = Color(customText)
        NovelColors(Color(customBackground), text, text.copy(alpha = 0.6f))
    } else {
        themeColors
    }.let { base -> highlightChoice.argb?.let { base.copy(highlight = Color(it)) } ?: base }
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

    // Read-aloud session for this novel. The paragraph being spoken is highlighted. Taps keep the
    // usual zones, so the middle of the screen still opens the reader menu while the voice plays.
    val ttsState by TtsStatus.state.collectAsState()
    val tts = ttsState?.takeIf { it.mangaId == chapter.chapter.mangaId }

    // The reader follows the voice until the user scrolls or turns pages; play, resume, the
    // read-aloud controls, or the spoken paragraph coming back on screen pick it up again.
    var followTts by remember { mutableStateOf(true) }
    LaunchedEffect(tts?.playing) {
        if (tts?.playing == true) followTts = true
    }
    // The voice moved on to the next chapter: open it too, so the list or pages can keep up.
    LaunchedEffect(tts?.chapterId) {
        val current = tts?.takeIf { it.playing && followTts } ?: return@LaunchedEffect
        if (current.chapterId == chapters.nextChapter?.chapter?.id) actions.onNextChapter()
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(colors.background),
    ) {
        // Key on the chapter so scroll and pager state start fresh at the requested paragraph.
        if (mode == NovelReadingMode.SCROLL) {
            // Not keyed on the chapter: the list spans neighbouring chapters and keeps its position.
            ContinuousScrollReader(
                chapters, styles, colors, margin, showPageNumber, jumps, commands, actions, tts,
                following = followTts,
                onFollowChange = { followTts = it },
            )
        } else {
            // Key on the chapter so the pager starts fresh at the requested paragraph.
            key(chapter, mode) {
                val start = chapter.requestedPage.coerceIn(0, (blocks.size - 1).coerceAtLeast(0))
                val chapterJumps = remember(chapter) { jumps.filter { it.chapter == chapter }.map { it.index } }
                PagedReader(
                    blocks, title, start, styles, colors, margin, mode, hasNext, hasPrevious, showPageNumber,
                    chapterJumps, commands, actions, tts?.takeIf { it.chapterId == chapter.chapter.id },
                    pageTurn = pageTurn,
                    following = followTts,
                    onFollowChange = { followTts = it },
                )
            }
        }

        if (tts != null && !menuVisible) {
            TtsControls(
                tts = tts,
                colors = colors,
                onControl = { followTts = true },
                onSettings = { showTtsSettings = true },
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .windowInsetsPadding(WindowInsets.navigationBars)
                    .padding(bottom = 24.dp),
            )
        }
    }

    if (showTtsSettings) {
        TtsSettingsSheet(preferences = preferences, onDismiss = { showTtsSettings = false })
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
    jumps: SharedFlow<ReaderPage>,
    commands: MutableSharedFlow<NovelCommand>,
    actions: NovelReaderActions,
    tts: TtsState?,
    following: Boolean,
    onFollowChange: (Boolean) -> Unit,
) {
    // Rebuilt right away for a new chapter set, so effects below never see the previous window,
    // then again whenever a neighbouring chapter finishes loading.
    var scrollItems by remember(chapters) { mutableStateOf(buildScrollItems(chapters)) }
    LaunchedEffect(chapters) {
        val window = listOfNotNull(chapters.prevChapter, chapters.currChapter, chapters.nextChapter)
        combine(window.map { it.stateFlow }) { buildScrollItems(chapters) }.collect { scrollItems = it }
    }
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
            // A jump, not reading on: the chapter being left must not be finished or made current again.
            lastShown = null
            listState.scrollToItem(indexOfBlock(chapters.currChapter, chapters.currChapter.requestedPage))
        }
    }
    LaunchedEffect(listState) {
        // A jump can arrive before the list has the chapter it targets; wait for it briefly.
        jumps.collectLatest { page ->
            val index = withTimeoutOrNull(2_000) {
                snapshotFlow {
                    currentItems.indexOfFirst {
                        it is BlockItem && it.chapter == page.chapter && it.index == page.index
                    }
                }.first { it >= 0 }
            } ?: return@collectLatest
            lastShown = null
            listState.scrollToItem(index)
        }
    }
    LaunchedEffect(listState) {
        commands.collect { command ->
            if (tts?.playing == true) onFollowChange(false)
            val step = listState.layoutInfo.viewportSize.height * 0.85f
            listState.animateScrollBy(if (command == NovelCommand.Next) step else -step)
        }
    }
    LaunchedEffect(listState) {
        listState.interactionSource.interactions.collect {
            if (it is DragInteraction.Start) onFollowChange(false)
        }
    }
    // Keep the sentence being read aloud on screen while following. Keyed on the sentence, not only the
    // paragraph, so a paragraph taller than the screen keeps scrolling as the voice moves down it.
    LaunchedEffect(tts?.chapterId, tts?.blockIndex, tts?.spokenStart, tts?.playing, following, scrollItems) {
        val current = tts?.takeIf { it.playing } ?: return@LaunchedEffect
        val index = currentItems.indexOfFirst {
            it is BlockItem && it.chapter.chapter.id == current.chapterId && it.index == current.blockIndex
        }
        if (index < 0) return@LaunchedEffect
        val layout = listState.layoutInfo
        val item = layout.visibleItemsInfo.firstOrNull { it.index == index }
        if (!following) {
            // The user scrolled back to the voice; follow it again from here.
            if (item != null) onFollowChange(true)
            return@LaunchedEffect
        }
        // Off screen: bring the paragraph in first, so its layout can be measured below.
        if (item == null) listState.scrollToItem(index)
        val placed = listState.layoutInfo.visibleItemsInfo.firstOrNull { it.index == index } ?: return@LaunchedEffect
        val textLength = (currentItems[index] as BlockItem).block.text.length
        // ponytail: the sentence's place in a tall paragraph is estimated from its character range, not the
        // text layout; close enough to centre it.
        fun at(offset: Int): Int {
            if (textLength <= 0) return 0
            return (placed.size * offset.coerceIn(0, textLength).toFloat() / textLength).toInt()
        }
        val sentenceStart = at(current.spokenStart.coerceAtLeast(0))
        val sentenceEnd = if (current.spokenEnd > current.spokenStart) at(current.spokenEnd) else placed.size
        val sentenceMiddle = placed.offset + (sentenceStart + sentenceEnd) / 2
        val viewportMiddle = (listState.layoutInfo.viewportStartOffset + listState.layoutInfo.viewportEndOffset) / 2
        // Each new sentence glides to the middle of the screen; the list's ends stop it where they must.
        val delta = sentenceMiddle - viewportMiddle
        if (abs(delta) > CENTRE_TOLERANCE_PX) listState.animateScrollBy(delta.toFloat())
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
                    is BlockItem -> BlockText(
                        block = item.block,
                        styles = styles,
                        colors = colors,
                        highlighted = tts != null &&
                            tts.chapterId == item.chapter.chapter.id &&
                            tts.blockIndex == item.index,
                        spokenStart = tts?.spokenStart ?: -1,
                        spokenEnd = tts?.spokenEnd ?: -1,
                        wordStart = tts?.wordStart ?: -1,
                        wordEnd = tts?.wordEnd ?: -1,
                    )
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
    jumps: Flow<Int>,
    commands: MutableSharedFlow<NovelCommand>,
    actions: NovelReaderActions,
    /** Read-aloud state when the voice is inside this chapter. */
    tts: TtsState?,
    /** Book-style turn between horizontal pages (Settings: Page turn effect). */
    pageTurn: Boolean,
    following: Boolean,
    onFollowChange: (Boolean) -> Unit,
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
            commands.collect { command ->
                if (tts?.playing == true) onFollowChange(false)
                pagerState.turn(command == NovelCommand.Next)
            }
        }
        LaunchedEffect(pagerState) {
            pagerState.interactionSource.interactions.collect {
                if (it is DragInteraction.Start) onFollowChange(false)
            }
        }
        // Turn pages along with the voice while following, to the page holding the spoken sentence.
        LaunchedEffect(tts?.blockIndex, tts?.spokenStart, tts?.playing, following, pages) {
            val current = tts?.takeIf { it.playing } ?: return@LaunchedEffect
            val spoken = current.spokenStart.coerceAtLeast(0)
            val target = pages.indexOfLast { page ->
                page.slices.any { it.block == current.blockIndex && it.start <= spoken }
            }.takeIf { it >= 0 }
                ?: pages.indexOfFirst { it.lastBlock >= current.blockIndex }.takeIf { it >= 0 }
                ?: return@LaunchedEffect
            if (!following) {
                if (target == pagerState.currentPage) onFollowChange(true)
                return@LaunchedEffect
            }
            if (target != pagerState.currentPage) pagerState.animateScrollToPage(target)
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
                            page.slices.forEach { slice ->
                                SliceText(
                                    slice = slice,
                                    styles = styles,
                                    colors = colors,
                                    highlighted = tts != null && tts.blockIndex == slice.block,
                                    spokenStart = tts?.spokenStart ?: -1,
                                    spokenEnd = tts?.spokenEnd ?: -1,
                                    wordStart = tts?.wordStart ?: -1,
                                    wordEnd = tts?.wordEnd ?: -1,
                                )
                            }
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
            HorizontalPager(
                state = pagerState,
                reverseLayout = reversed,
                // The page underneath must already be composed while the top one turns over it.
                beyondViewportPageCount = if (pageTurn) 1 else 0,
                modifier = Modifier.fillMaxSize(),
            ) { page ->
                if (pageTurn) {
                    Box(Modifier.fillMaxSize().pageTurn(pagerState, page, reversed, colors.background)) {
                        pageContent(page)
                    }
                } else {
                    pageContent(page)
                }
            }
        }
    }
}

/**
 * A book-style turn between horizontal pages: the page on top follows the finger with a shadow on its
 * edge and a slight tilt, while the page it covers or uncovers stays in place, sinks a little and dims.
 * [reversed] mirrors it for right-to-left pages.
 */
private fun Modifier.pageTurn(state: PagerState, page: Int, reversed: Boolean, background: Color): Modifier {
    return graphicsLayer {
        // > 0: the page under the turn (it stays put). < 0: the page on top (it slides as usual).
        val offset = (state.currentPage - page) + state.currentPageOffsetFraction
        val direction = if (reversed) -1f else 1f
        if (offset > 0f) {
            val depth = offset.coerceIn(0f, 1f)
            translationX = depth * size.width * direction
            scaleX = 1f - PAGE_TURN_SINK * depth
            scaleY = 1f - PAGE_TURN_SINK * depth
        } else {
            val lift = (-offset).coerceIn(0f, 1f)
            cameraDistance = 12f * density
            transformOrigin = TransformOrigin(if (reversed) 0f else 1f, 0.5f)
            rotationY = PAGE_TURN_TILT * lift * direction
        }
    }.drawWithContent {
        val offset = (state.currentPage - page) + state.currentPageOffsetFraction
        drawRect(background)
        drawContent()
        if (offset > 0f) {
            // The covered page dims as the top page closes over it.
            drawRect(Color.Black.copy(alpha = PAGE_TURN_DIM * offset.coerceIn(0f, 1f)))
        } else if (offset < 0f) {
            // Shadow cast by the turning page's leading edge onto the page below.
            val shadow = PAGE_TURN_SHADOW.toPx()
            val leftEdge = !reversed
            val x = if (leftEdge) -shadow else size.width
            drawRect(
                brush = Brush.horizontalGradient(
                    colors = if (leftEdge) {
                        listOf(Color.Transparent, Color.Black.copy(alpha = 0.25f))
                    } else {
                        listOf(Color.Black.copy(alpha = 0.25f), Color.Transparent)
                    },
                    startX = x,
                    endX = x + shadow,
                ),
                topLeft = Offset(x, 0f),
                size = Size(shadow, size.height),
            )
        }
    }
}

private const val PAGE_TURN_SINK = 0.05f
private const val PAGE_TURN_DIM = 0.35f
private const val PAGE_TURN_TILT = -14f
private val PAGE_TURN_SHADOW = 18.dp

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

/**
 * Glow behind the sentence being spoken and a stronger one behind the spoken word. All indexes are
 * in the whole paragraph; [localStart] is where this text starts inside it.
 */
private const val SENTENCE_GLOW = 0.28f

/** Closer than this to the middle and the list stays put, so tiny nudges don't jitter the text. */
private const val CENTRE_TOLERANCE_PX = 24
private const val WORD_GLOW = 0.5f

private fun AnnotatedString.highlightSpoken(
    sentenceStart: Int,
    sentenceEnd: Int,
    wordStart: Int,
    wordEnd: Int,
    localStart: Int,
    color: Color,
): AnnotatedString {
    fun range(start: Int, end: Int): IntRange? {
        if (end <= start) return null
        val from = (start - localStart).coerceIn(0, text.length)
        val to = (end - localStart).coerceIn(from, text.length)
        return if (to <= from) null else from until to
    }
    val sentence = range(sentenceStart, sentenceEnd)
    val word = range(wordStart, wordEnd)
    if (sentence == null && word == null) return this
    return AnnotatedString.Builder(this).apply {
        sentence?.let { addStyle(SpanStyle(background = color.copy(alpha = SENTENCE_GLOW)), it.first, it.last + 1) }
        word?.let { addStyle(SpanStyle(background = color.copy(alpha = WORD_GLOW)), it.first, it.last + 1) }
    }.toAnnotatedString()
}

@Composable
private fun BlockText(
    block: NovelBlock,
    styles: NovelStyles,
    colors: NovelColors,
    highlighted: Boolean = false,
    spokenStart: Int = -1,
    spokenEnd: Int = -1,
    wordStart: Int = -1,
    wordEnd: Int = -1,
) {
    fun shown(text: AnnotatedString) = if (highlighted) {
        text.highlightSpoken(spokenStart, spokenEnd, wordStart, wordEnd, 0, colors.highlight)
    } else {
        text
    }
    when (block) {
        NovelBlock.Divider -> HorizontalDivider(Modifier.padding(vertical = 8.dp), color = colors.muted)
        is NovelBlock.Heading -> Text(shown(block.text), style = styles.heading)
        is NovelBlock.Quote -> Text(shown(block.text), style = styles.quote)
        is NovelBlock.Paragraph -> Text(shown(block.text), style = styles.body)
        is NovelBlock.Image -> NovelImage(
            url = block.url,
            contentScale = ContentScale.FillWidth,
            placeholder = colors.muted.copy(alpha = 0.12f),
            reserveHeight = true,
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp),
        )
    }
}

@Composable
private fun SliceText(
    slice: PageSlice,
    styles: NovelStyles,
    colors: NovelColors,
    highlighted: Boolean = false,
    spokenStart: Int = -1,
    spokenEnd: Int = -1,
    wordStart: Int = -1,
    wordEnd: Int = -1,
) {
    val shown = if (highlighted) {
        slice.text.highlightSpoken(
            spokenStart,
            spokenEnd,
            wordStart,
            wordEnd,
            slice.start,
            colors.highlight,
        )
    } else {
        slice.text
    }
    when (slice.kind) {
        SliceKind.Divider -> HorizontalDivider(Modifier.padding(vertical = 8.dp), color = colors.muted)
        SliceKind.Heading -> Text(shown, style = styles.heading)
        SliceKind.Quote -> Text(shown, style = styles.quote)
        SliceKind.Body -> Text(shown, style = styles.body)
        SliceKind.Image -> NovelImage(
            url = slice.url,
            contentScale = ContentScale.Fit,
            placeholder = colors.muted.copy(alpha = 0.12f),
            reserveHeight = false,
            modifier = Modifier.fillMaxSize(),
        )
    }
}

/** The source's request headers (Referer, User-Agent); many image hosts refuse requests without them. */
internal val LocalNovelImageHeaders = staticCompositionLocalOf { NetworkHeaders.EMPTY }

/**
 * A chapter image through the app's Coil loader (shared OkHttp client, memory and disk cache), sized
 * to its slot. Until it loads, a tinted box holds its place so the text below does not jump.
 */
@Composable
private fun NovelImage(
    url: String?,
    contentScale: ContentScale,
    placeholder: Color,
    reserveHeight: Boolean,
    modifier: Modifier = Modifier,
) {
    val context = LocalPlatformContext.current
    val headers = LocalNovelImageHeaders.current
    val request = remember(url, headers) {
        ImageRequest.Builder(context)
            .data(url)
            .httpHeaders(headers)
            // The app's decoder is still-only; this one also plays GIF and animated WebP
            .apply {
                if (Build.VERSION.SDK_INT >=
                    Build.VERSION_CODES.P
                ) {
                    decoderFactory(AnimatedImageDecoder.Factory())
                }
            }
            .build()
    }
    var loaded by remember(url) { mutableStateOf(false) }
    AsyncImage(
        model = request,
        contentDescription = null,
        contentScale = contentScale,
        onSuccess = { loaded = true },
        modifier = if (loaded) {
            modifier
        } else {
            modifier
                .then(if (reserveHeight) Modifier.aspectRatio(4f / 3f) else Modifier)
                .background(placeholder)
        },
    )
}

private val SLEEP_PRESETS = listOf(5, 15, 30, 45, 60)

/**
 * Floating controls for the read-aloud session: paragraph skips, play and pause, sleep timer,
 * speed and stop. The bar uses the chapter colors. The notification mirrors the actions.
 */
@Composable
private fun TtsControls(
    tts: TtsState,
    colors: NovelColors,
    onSettings: () -> Unit,
    modifier: Modifier = Modifier,
    /** Any read-aloud control was used; the reader follows the voice again. */
    onControl: () -> Unit = {},
) {
    val context = LocalContext.current
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    var timerOpen by remember { mutableStateOf(false) }
    val deadline = tts.sleepAtMillis
    LaunchedEffect(deadline) {
        if (deadline == null) return@LaunchedEffect
        while (System.currentTimeMillis() < deadline) {
            now = System.currentTimeMillis()
            delay(1_000)
        }
        now = System.currentTimeMillis()
    }
    val remaining = deadline?.let { (it - now).coerceAtLeast(0L) }
    val bar = lerp(colors.background, colors.text, 0.12f)
    val icon = colors.text

    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(32.dp),
        color = bar,
        contentColor = icon,
        shadowElevation = 10.dp,
        border = BorderStroke(1.dp, icon.copy(alpha = 0.2f)),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 4.dp),
        ) {
            TtsIconButton(
                onClick = {
                    onControl()
                    TtsPlaybackService.previousParagraph(context)
                },
                icon = MaterialSymbols.Rounded.SkipPrevious,
                description = stringResource(MR.strings.tts_previous_paragraph),
                tint = icon,
            )
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(icon)
                    .clickable(
                        onClick = {
                            onControl()
                            TtsPlaybackService.playPause(context)
                        },
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    if (tts.playing) {
                        MaterialSymbols.RoundedFilled.Pause
                    } else {
                        MaterialSymbols.RoundedFilled.PlayArrow
                    },
                    contentDescription = stringResource(
                        if (tts.playing) MR.strings.action_pause else MR.strings.action_resume,
                    ),
                    tint = colors.background,
                    modifier = Modifier.size(26.dp),
                )
            }
            TtsIconButton(
                onClick = {
                    onControl()
                    TtsPlaybackService.nextParagraph(context)
                },
                icon = MaterialSymbols.Rounded.SkipNext,
                description = stringResource(MR.strings.tts_next_paragraph),
                tint = icon,
            )
            Box(
                Modifier
                    .padding(horizontal = 4.dp)
                    .width(1.dp)
                    .height(22.dp)
                    .background(icon.copy(alpha = 0.25f)),
            )
            Box {
                TextButton(
                    onClick = { timerOpen = true },
                    contentPadding = PaddingValues(horizontal = 8.dp),
                ) {
                    Icon(
                        MaterialSymbols.Rounded.Schedule,
                        contentDescription = stringResource(MR.strings.tts_sleep_timer),
                        tint = if (remaining != null) icon else icon.copy(alpha = 0.7f),
                        modifier = Modifier.size(20.dp),
                    )
                    if (remaining != null) {
                        Text(
                            formatRemaining(remaining),
                            color = icon,
                            style = MaterialTheme.typography.labelLarge,
                            modifier = Modifier.padding(start = 4.dp),
                        )
                    }
                }
                DropdownMenu(
                    expanded = timerOpen,
                    onDismissRequest = { timerOpen = false },
                    containerColor = bar,
                ) {
                    DropdownMenuItem(
                        text = { Text(stringResource(MR.strings.tts_sleep_off), color = icon) },
                        onClick = {
                            timerOpen = false
                            TtsPlaybackService.setSleepTimer(context, 0)
                        },
                    )
                    SLEEP_PRESETS.forEach { minutes ->
                        DropdownMenuItem(
                            text = {
                                Text(stringResource(MR.strings.tts_sleep_minutes, minutes), color = icon)
                            },
                            onClick = {
                                timerOpen = false
                                TtsPlaybackService.setSleepTimer(context, minutes)
                            },
                        )
                    }
                }
            }
            TextButton(
                onClick = {
                    val steps = listOf(0.75f, 1f, 1.25f, 1.5f, 1.75f, 2f, 2.5f)
                    val next = steps.firstOrNull { it > tts.speed + 0.01f } ?: steps.first()
                    TtsPlaybackService.setSpeed(context, next)
                },
                contentPadding = PaddingValues(horizontal = 8.dp),
            ) {
                Text(tts.speed.asSpeedLabel(), color = icon, style = MaterialTheme.typography.labelLarge)
            }
            TtsIconButton(
                onClick = onSettings,
                icon = MaterialSymbols.Rounded.Settings,
                description = stringResource(MR.strings.tts_settings),
                tint = icon,
            )
            TtsIconButton(
                onClick = { TtsPlaybackService.stop(context) },
                icon = MaterialSymbols.Rounded.Close,
                description = stringResource(MR.strings.action_stop),
                tint = icon,
            )
        }
    }
}

@Composable
private fun TtsIconButton(
    onClick: () -> Unit,
    icon: ImageVector,
    description: String,
    tint: Color,
) {
    IconButton(onClick = onClick, modifier = Modifier.size(40.dp)) {
        Icon(icon, contentDescription = description, tint = tint, modifier = Modifier.size(22.dp))
    }
}

private fun formatRemaining(ms: Long): String {
    val total = (ms / 1000L).toInt().coerceAtLeast(0)
    val minutes = total / 60
    val seconds = total % 60
    return if (minutes >= 60) {
        "%d:%02d:%02d".format(minutes / 60, minutes % 60, seconds)
    } else {
        "%d:%02d".format(minutes, seconds)
    }
}

private fun Float.asSpeedLabel(): String =
    "%.2f".format(this).trimEnd('0').trimEnd('.', ',') + "×"

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
    /** Index of [text] inside the paragraph, so a sentence can be highlighted across a page break. */
    val start: Int = 0,
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
            current += PageSlice(index, SliceKind.Image, AnnotatedString(""), url = block.url)
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
        var offset = 0
        while (remaining.isNotEmpty()) {
            val layout = measurer.measure(remaining, style, constraints = Constraints(maxWidth = widthPx))
            val available = heightPx - used
            if (layout.size.height <= available) {
                current += PageSlice(index, kind, remaining, offset)
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
            current += PageSlice(index, kind, remaining.subSequence(0, end), offset)
            offset += end
            newPage()
            remaining = remaining.subSequence(end, remaining.length).let { rest ->
                val skip = rest.text.indexOfFirst { !it.isWhitespace() }
                if (skip < 0) {
                    AnnotatedString("")
                } else {
                    offset += skip
                    rest.subSequence(skip, rest.length)
                }
            }
        }
    }
    newPage()
    return pages
}

// Settings

/** Novel reading options; shown in the reader settings dialog's Reading mode tab. */
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

    HeadingItem(MR.strings.tts_settings)
    TtsSettingsContent(preferences)

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
    if (mode == NovelReadingMode.PAGED_LTR || mode == NovelReadingMode.PAGED_RTL) {
        CheckboxItem(label = stringResource(MR.strings.novel_page_turn), pref = preferences.novelPageTurn)
    }
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
