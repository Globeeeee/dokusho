package eu.kanade.tachiyomi.ui.reader.viewer.novel

import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.ComposeView
import coil3.network.NetworkHeaders
import dev.icerock.moko.resources.StringResource
import eu.kanade.presentation.theme.TachiyomiTheme
import eu.kanade.tachiyomi.ui.reader.ReaderActivity
import eu.kanade.tachiyomi.ui.reader.model.ReaderChapter
import eu.kanade.tachiyomi.ui.reader.model.ReaderPage
import eu.kanade.tachiyomi.ui.reader.model.ViewerChapters
import eu.kanade.tachiyomi.ui.reader.setting.ReaderPreferences.NovelReadingMode
import eu.kanade.tachiyomi.ui.reader.setting.ReadingMode
import eu.kanade.tachiyomi.ui.reader.viewer.Viewer
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import mihon.app.di.appGraph
import tachiyomi.i18n.MR

/**
 * Viewer for text chapters from a NovelSource. Each paragraph is one [ReaderPage], so the reader's
 * progress, history and read marking work unchanged.
 */
class NovelViewer(val activity: ReaderActivity) : Viewer {

    private val readerPreferences by lazy { activity.appGraph.readerPreferences }
    private val scope = MainScope()

    private val chapters = MutableStateFlow<ViewerChapters?>(null)
    private val jumps = MutableSharedFlow<ReaderPage>(extraBufferCapacity = 1)
    private val commands = MutableSharedFlow<NovelCommand>(extraBufferCapacity = 1)

    private val actions = object : NovelReaderActions {
        override fun onBlockShown(index: Int) {
            val pages = chapters.value?.currChapter?.pages ?: return
            pages.getOrNull(index.coerceIn(0, pages.lastIndex))?.let(activity::onPageSelected)
        }

        override fun onBlockShownIn(chapter: ReaderChapter, index: Int) {
            val pages = chapter.pages ?: return
            pages.getOrNull(index.coerceIn(0, pages.lastIndex))?.let(activity::onPageSelected)
        }

        override fun onToggleMenu() = activity.toggleMenu()

        override fun onPreloadPrevious() {
            val previous = chapters.value?.prevChapter ?: return
            if (previous.state is ReaderChapter.State.Wait) activity.requestPreloadChapter(previous)
        }

        override fun onPreloadNext() {
            val next = chapters.value?.nextChapter ?: return
            if (next.state is ReaderChapter.State.Wait) activity.requestPreloadChapter(next)
        }

        override fun onNextChapter() = openChapter(chapters.value?.nextChapter, atEnd = false)

        override fun onPreviousChapter() = openChapter(chapters.value?.prevChapter, atEnd = true)
    }

    private val view = ComposeView(activity).apply {
        setContent {
            TachiyomiTheme {
                val current by chapters.collectAsState()
                val menuVisible by activity.viewModel.state.collectAsState()
                val imageHeaders = remember {
                    val headers = activity.viewModel.getSource()?.headers
                    NetworkHeaders.Builder()
                        .apply { headers?.forEach { (name, value) -> add(name, value) } }
                        .build()
                }
                current?.let {
                    CompositionLocalProvider(LocalNovelImageHeaders provides imageHeaders) {
                        NovelReaderContent(
                            chapters = it,
                            preferences = readerPreferences,
                            menuVisible = menuVisible.menuVisible,
                            jumps = jumps,
                            commands = commands,
                            actions = actions,
                        )
                    }
                }
            }
        }
    }

    override fun getView(): View = view

    override fun destroy() {
        scope.cancel()
    }

    override fun setChapters(chapters: ViewerChapters) {
        this.chapters.value = chapters
    }

    override fun moveToPage(page: ReaderPage) {
        if (page.chapter == chapters.value?.currChapter) jumps.tryEmit(page)
    }

    override fun handleKeyEvent(event: KeyEvent): Boolean {
        val isVolume = event.keyCode == KeyEvent.KEYCODE_VOLUME_DOWN || event.keyCode == KeyEvent.KEYCODE_VOLUME_UP
        if (!isVolume || !readerPreferences.readWithVolumeKeys.get()) return false
        if (event.action == KeyEvent.ACTION_UP) {
            val down = event.keyCode == KeyEvent.KEYCODE_VOLUME_DOWN
            val forward = down != readerPreferences.readWithVolumeKeysInverted.get()
            commands.tryEmit(if (forward) NovelCommand.Next else NovelCommand.Previous)
        }
        return true
    }

    override fun handleGenericMotionEvent(event: MotionEvent): Boolean = false

    /** Loads [chapter] if needed, then makes it current at its first or last paragraph. */
    private fun openChapter(chapter: ReaderChapter?, atEnd: Boolean) {
        chapter ?: return
        scope.launch {
            if (chapter.state !is ReaderChapter.State.Loaded) {
                activity.requestPreloadChapter(chapter)
                chapter.stateFlow.first { it is ReaderChapter.State.Loaded || it is ReaderChapter.State.Error }
            }
            val pages = chapter.pages ?: return@launch
            val target = if (atEnd) pages.lastOrNull() else pages.firstOrNull()
            target?.let(activity::onPageSelected)
        }
    }
}

internal enum class NovelCommand { Next, Previous }

internal val NovelReadingMode.labelRes: StringResource
    get() = when (this) {
        NovelReadingMode.SCROLL -> MR.strings.novel_mode_scroll
        NovelReadingMode.PAGED_LTR -> MR.strings.novel_mode_paged_ltr
        NovelReadingMode.PAGED_RTL -> MR.strings.novel_mode_paged_rtl
        NovelReadingMode.PAGED_VERTICAL -> MR.strings.novel_mode_paged_vertical
    }

/** The image reading mode whose icon matches a novel mode, for the reader's bottom bar. */
internal fun NovelReadingMode.asReadingMode(): ReadingMode = when (this) {
    NovelReadingMode.SCROLL -> ReadingMode.WEBTOON
    NovelReadingMode.PAGED_LTR -> ReadingMode.LEFT_TO_RIGHT
    NovelReadingMode.PAGED_RTL -> ReadingMode.RIGHT_TO_LEFT
    NovelReadingMode.PAGED_VERTICAL -> ReadingMode.VERTICAL
}

internal interface NovelReaderActions {
    fun onBlockShown(index: Int)

    /** A paragraph of [chapter] reached the top of the screen; used by the continuous scroll mode. */
    fun onBlockShownIn(chapter: ReaderChapter, index: Int)
    fun onToggleMenu()
    fun onPreloadNext()
    fun onPreloadPrevious()
    fun onNextChapter()
    fun onPreviousChapter()
}
