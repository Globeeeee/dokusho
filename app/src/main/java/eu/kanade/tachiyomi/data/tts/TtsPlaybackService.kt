package eu.kanade.tachiyomi.data.tts

import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ServiceInfo
import android.graphics.Bitmap
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.PowerManager
import android.support.v4.media.MediaMetadataCompat
import android.support.v4.media.session.MediaSessionCompat
import android.support.v4.media.session.PlaybackStateCompat
import android.view.KeyEvent
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import androidx.core.content.getSystemService
import coil3.asDrawable
import coil3.imageLoader
import coil3.request.ImageRequest
import coil3.request.allowHardware
import dev.zacsweers.metro.Inject
import eu.kanade.domain.chapter.model.toSChapter
import eu.kanade.domain.source.interactor.GetIncognitoState
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.data.cache.ChapterCache
import eu.kanade.tachiyomi.data.download.DownloadProvider
import eu.kanade.tachiyomi.data.download.Downloader
import eu.kanade.tachiyomi.data.notification.Notifications
import eu.kanade.tachiyomi.source.NovelSource
import eu.kanade.tachiyomi.ui.reader.ReaderActivity
import eu.kanade.tachiyomi.ui.reader.setting.ReaderPreferences
import eu.kanade.tachiyomi.ui.reader.viewer.novel.NovelHtml
import eu.kanade.tachiyomi.util.system.getBitmapOrNull
import eu.kanade.tachiyomi.util.system.notificationBuilder
import eu.kanade.tachiyomi.util.system.notify
import eu.kanade.tachiyomi.util.system.toast
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import logcat.LogPriority
import mihon.app.di.appGraph
import tachiyomi.core.common.i18n.stringResource
import tachiyomi.core.common.util.system.logcat
import tachiyomi.domain.chapter.interactor.GetChapter
import tachiyomi.domain.chapter.interactor.UpdateChapter
import tachiyomi.domain.chapter.model.Chapter
import tachiyomi.domain.chapter.model.ChapterUpdate
import tachiyomi.domain.history.interactor.GetNextChapters
import tachiyomi.domain.history.interactor.UpsertHistory
import tachiyomi.domain.history.model.HistoryUpdate
import tachiyomi.domain.manga.interactor.GetManga
import tachiyomi.domain.manga.model.Manga
import tachiyomi.domain.source.service.SourceManager
import tachiyomi.i18n.MR
import kotlin.time.Clock
import mihon.icons.materialsymbols.R as MaterialSymbolsR

/**
 * Reads a novel chapter aloud in the background (phase 6 of the plan). A foreground media service
 * with a media session, so playback keeps going with the screen off, shows media controls in the
 * notification, reacts to headset buttons and pauses for calls through audio focus.
 *
 * The queue is the speakable blocks of the current chapter; finishing a chapter marks it read,
 * writes history and continues into the next one from [GetNextChapters]. The reader observes
 * [TtsStatus] to highlight the paragraph being spoken.
 */
class TtsPlaybackService : Service() {

    @Inject lateinit var sourceManager: SourceManager

    @Inject lateinit var chapterCache: ChapterCache

    @Inject lateinit var downloadProvider: DownloadProvider

    @Inject lateinit var readerPreferences: ReaderPreferences

    @Inject lateinit var getManga: GetManga

    @Inject lateinit var getChapter: GetChapter

    @Inject lateinit var getNextChapters: GetNextChapters

    @Inject lateinit var updateChapter: UpdateChapter

    @Inject lateinit var upsertHistory: UpsertHistory

    @Inject lateinit var getIncognitoState: GetIncognitoState

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private var cover: Bitmap? = null
    private var coverFor: Long? = null
    private var coverJob: Job? = null
    private lateinit var engine: TtsEngine
    private var engineId: String? = null
    private lateinit var mediaSession: MediaSessionCompat
    private lateinit var audioManager: AudioManager
    private var focusRequest: AudioFocusRequest? = null
    private var wakeLock: PowerManager.WakeLock? = null

    // The active session; all mutated on the main thread.
    private var manga: Manga? = null
    private var chapter: Chapter? = null
    private var queue: List<QueueItem> = emptyList()
    private var queuePos = 0
    private var playing = false
    private var playbackJob: Job? = null
    private var startJob: Job? = null
    private var sleepJob: Job? = null
    private var sleepAtMillis: Long? = null
    private var incognito = false
    private var chapterStartedAt = 0L
    private var prefetch: Pair<Long, List<QueueItem>>? = null
    private var prefetchJob: Job? = null
    private var spokenStart = -1
    private var spokenEnd = -1
    private var wordStart = -1
    private var wordEnd = -1

    /** Set when a call or another app takes audio; the earbud play button and focus gain resume. */
    private var resumeAfterFocus = false

    /** [text] is what the engine speaks; [display] is the paragraph as the reader shows it. */
    private class QueueItem(val blockIndex: Int, private val speech: SpeechText) {
        val text get() = speech.spoken
        val display get() = speech.display
        fun displayRange(start: Int, end: Int) = speech.displayStart(start) to speech.displayEnd(end)
    }

    private val focusListener = AudioManager.OnAudioFocusChangeListener { change ->
        when (change) {
            AudioManager.AUDIOFOCUS_LOSS -> {
                resumeAfterFocus = false
                pause(keepFocus = true)
            }
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT,
            // Speech can't duck under other audio and stay intelligible; pause instead.
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK,
            -> if (playing) {
                resumeAfterFocus = true
                pause(keepFocus = true)
            }
            AudioManager.AUDIOFOCUS_GAIN -> if (resumeAfterFocus) {
                resumeAfterFocus = false
                play()
            }
        }
    }

    /** Unplugging the headphones pauses instead of reading out loud on the speaker. */
    private val noisyReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action == AudioManager.ACTION_AUDIO_BECOMING_NOISY) pause()
        }
    }

    override fun onCreate() {
        super.onCreate()
        appGraph.inject(this)
        openEngine()
        audioManager = getSystemService()!!
        mediaSession = MediaSessionCompat(this, "DokushoTts").apply {
            setFlags(
                MediaSessionCompat.FLAG_HANDLES_MEDIA_BUTTONS or
                    MediaSessionCompat.FLAG_HANDLES_TRANSPORT_CONTROLS,
            )
            setPlaybackToLocal(AudioManager.STREAM_MUSIC)
            setCallback(
                object : MediaSessionCompat.Callback() {
                    override fun onPlay() = play()
                    override fun onPause() {
                        // A headset pause is the listener stopping, not another app borrowing audio.
                        resumeAfterFocus = false
                        pause()
                    }
                    override fun onSkipToNext() = nextParagraph()
                    override fun onSkipToPrevious() = previousParagraph()
                    override fun onStop() = stopSession()

                    override fun onMediaButtonEvent(mediaButtonEvent: Intent): Boolean {
                        val event = mediaButtonEvent.mediaKey() ?: return super.onMediaButtonEvent(mediaButtonEvent)
                        if (event.action != KeyEvent.ACTION_DOWN) return true
                        when (event.keyCode) {
                            KeyEvent.KEYCODE_HEADSETHOOK,
                            KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE,
                            -> {
                                resumeAfterFocus = false
                                if (playing) pause() else play()
                                return true
                            }
                            KeyEvent.KEYCODE_MEDIA_PLAY -> {
                                play()
                                return true
                            }
                            KeyEvent.KEYCODE_MEDIA_PAUSE -> {
                                resumeAfterFocus = false
                                pause()
                                return true
                            }
                            KeyEvent.KEYCODE_MEDIA_NEXT -> {
                                nextParagraph()
                                return true
                            }
                            KeyEvent.KEYCODE_MEDIA_PREVIOUS -> {
                                previousParagraph()
                                return true
                            }
                        }
                        return super.onMediaButtonEvent(mediaButtonEvent)
                    }
                },
                Handler(Looper.getMainLooper()),
            )
        }
        ContextCompat.registerReceiver(
            this,
            noisyReceiver,
            IntentFilter(AudioManager.ACTION_AUDIO_BECOMING_NOISY),
            ContextCompat.RECEIVER_NOT_EXPORTED,
        )
        readerPreferences.ttsShowCover.changes()
            .drop(1)
            .onEach {
                coverFor = null
                updateMetadata()
            }
            .launchIn(scope)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> {
                // minSdk is below Q, where startForeground has no type parameter; the compat call handles both.
                ServiceCompat.startForeground(
                    this,
                    Notifications.ID_TTS,
                    buildNotification(stringResource(MR.strings.tts_preparing)),
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK,
                )
                val mangaId = intent.getLongExtra(EXTRA_MANGA_ID, -1L)
                val chapterId = intent.getLongExtra(EXTRA_CHAPTER_ID, -1L)
                val blockIndex = intent.getIntExtra(EXTRA_BLOCK_INDEX, 0)
                if (mangaId == -1L || chapterId == -1L) {
                    stopSession()
                } else {
                    startSession(mangaId, chapterId, blockIndex)
                }
            }
            ACTION_PLAY_PAUSE -> if (playing) pause() else play()
            ACTION_PLAY -> play()
            ACTION_PAUSE -> pause()
            ACTION_PREVIOUS_PARAGRAPH -> previousParagraph()
            ACTION_NEXT_PARAGRAPH -> nextParagraph()
            ACTION_NEXT_CHAPTER -> nextChapter()
            ACTION_SLEEP -> setSleepTimer(intent.getIntExtra(EXTRA_SLEEP_MINUTES, 0))
            ACTION_SPEED -> setSpeed(intent.getFloatExtra(EXTRA_SPEED, 1f))
            ACTION_APPLY -> applySettings()
            ACTION_STOP -> stopSession()
        }
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        playbackJob?.cancel()
        scope.cancel()
        engine.release()
        mediaSession.release()
        unregisterReceiver(noisyReceiver)
        abandonFocus()
        releaseWakeLock()
        TtsStatus.update(null)
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    // Session

    private fun startSession(mangaId: Long, chapterId: Long, blockIndex: Int) {
        // Tapping a paragraph of the chapter already being read is a seek, not a reload.
        if (chapter?.id == chapterId && queue.isNotEmpty()) {
            seekTo(blockIndex)
            return
        }
        startJob?.cancel()
        startJob = scope.launch {
            if (TtsEngines.isSupertonic(readerPreferences.ttsEngine.get())) {
                SupertonicRuntime.warm(this@TtsPlaybackService)
            }
            stopPlayback()
            val manga = getManga.await(mangaId)
            val chapter = getChapter.await(chapterId)
            if (manga == null || chapter == null) {
                stopSession()
                return@launch
            }
            incognito = getIncognitoState.await(manga.source)
            val loaded = withContext(Dispatchers.IO) { loadQueue(manga, chapter) }
            if (loaded.isNullOrEmpty()) {
                toast(MR.strings.tts_nothing_to_read)
                stopSession()
                return@launch
            }
            this@TtsPlaybackService.manga = manga
            this@TtsPlaybackService.chapter = chapter
            queue = loaded
            queuePos = loaded.indexOfFirst { it.blockIndex >= blockIndex }.takeIf { it >= 0 } ?: 0
            prefetch = null
            prefetchJob?.cancel()
            chapterStartedAt = System.currentTimeMillis()
            updateMetadata()
            play()
        }
    }

    private fun seekTo(blockIndex: Int) {
        val target = queue.indexOfFirst { it.blockIndex >= blockIndex }.takeIf { it >= 0 } ?: queue.lastIndex
        stopPlayback()
        queuePos = target
        play()
    }

    private fun play() {
        if (playing || queue.isEmpty()) return
        if (queuePos !in queue.indices) queuePos = queue.indices.last
        if (!requestFocus()) return
        playing = true
        acquireWakeLock()
        mediaSession.isActive = true
        publishState()
        playbackJob = scope.launch {
            applyEngineSettings()
            primeSupertonic()
            if (!engine.init()) {
                toast(MR.strings.tts_engine_unavailable)
                stopSession()
                return@launch
            }
            applyEngineSettings()
            while (playing) {
                val item = queue.getOrNull(queuePos)
                if (item == null) {
                    if (!advanceChapter()) {
                        stopSession()
                        return@launch
                    }
                    primeSupertonic()
                    continue
                }
                spokenStart = -1
                spokenEnd = -1
                wordStart = -1
                wordEnd = -1
                publishState(item.blockIndex)
                saveProgress(item.blockIndex, completesChapter = false)
                updateNotification()
                maybePrefetchNext()
                val spoken = withContext(Dispatchers.IO) {
                    engine.speak(item.text) { from, to ->
                        val (start, end) = item.displayRange(from, to)
                        val bounds = sentenceBounds(item.display, start, end)
                        scope.launch(Dispatchers.Main.immediate) {
                            if (!playing || queue.getOrNull(queuePos)?.blockIndex != item.blockIndex) return@launch
                            if (wordStart == start && wordEnd == end) return@launch
                            spokenStart = bounds.first
                            spokenEnd = bounds.second
                            wordStart = start
                            wordEnd = end
                            publishState(item.blockIndex, session = false)
                        }
                    }
                }
                if (!playing) return@launch // paused or stopped mid-utterance
                if (!spoken) {
                    toast(MR.strings.tts_engine_unavailable)
                    pause()
                    return@launch
                }
                queuePos++
            }
        }
    }

    private fun pause(keepFocus: Boolean = false) {
        if (!playing) {
            if (!keepFocus) abandonFocus()
            if (sleepAtMillis == null) releaseWakeLock()
            return
        }
        playing = false
        if (!keepFocus) resumeAfterFocus = false
        playbackJob?.cancel()
        playbackJob = null
        engine.stop()
        if (!keepFocus) abandonFocus()
        // A running sleep timer still has to fire if reading is paused.
        if (sleepAtMillis == null) releaseWakeLock()
        publishState()
        updateNotification()
    }

    /** Stops speech but keeps the queue and position, for seeks and chapter switches. */
    private fun stopPlayback() {
        playing = false
        playbackJob?.cancel()
        playbackJob = null
        engine.stop()
    }

    private fun previousParagraph() {
        if (queue.isEmpty()) return
        seekToPosition(queuePos - 1)
    }

    private fun nextParagraph() {
        if (queue.isEmpty()) return
        seekToPosition(queuePos + 1)
    }

    private fun seekToPosition(position: Int) {
        val wasPlaying = playing
        stopPlayback()
        queuePos = position.coerceIn(0, queue.lastIndex)
        publishState(queue[queuePos].blockIndex)
        if (wasPlaying) play() else updateNotification()
    }

    private fun nextChapter() {
        scope.launch {
            val wasPlaying = playing
            stopPlayback()
            if (advanceChapter()) {
                if (wasPlaying) play() else updateNotification()
            } else {
                stopSession()
            }
        }
    }

    /**
     * Finishes the current chapter (read flag, history) and loads the next one into the queue.
     * Returns false when there is no readable next chapter.
     */
    private suspend fun advanceChapter(): Boolean {
        val manga = manga ?: return false
        val previous = chapter ?: return false
        saveProgress(queue.lastOrNull()?.blockIndex ?: 0, completesChapter = true)
        writeHistory(previous.id)

        // The prefetch may still be loading the next chapter; finish it rather than racing it.
        prefetchJob?.join()
        val (next, loaded) = try {
            val next = getNextChapters.await(manga.id, previous.id, onlyUnread = false)
                .firstOrNull { it.id != previous.id }
                ?: return false
            next to (
                prefetch?.takeIf { it.first == next.id }?.second
                    ?: withContext(Dispatchers.IO) { loadQueue(manga, next) }
                )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            logcat(LogPriority.ERROR, e) { "TTS failed to open the next chapter" }
            return false
        }
        if (loaded.isNullOrEmpty()) return false

        chapter = next
        queue = loaded
        queuePos = 0
        prefetch = null
        chapterStartedAt = System.currentTimeMillis()
        updateMetadata()
        publishState(loaded.first().blockIndex)
        return true
    }

    /** Loads the next chapter as soon as this one starts, and synthesizes it while this one plays. */
    private fun maybePrefetchNext() {
        if (queue.isEmpty() || prefetch != null || prefetchJob?.isActive == true) return
        val manga = manga ?: return
        val current = chapter ?: return
        prefetchJob = scope.launch {
            val loaded = withContext(Dispatchers.IO) {
                val next = getNextChapters.await(manga.id, current.id, onlyUnread = false)
                    .firstOrNull { it.id != current.id }
                    ?: return@withContext null
                val text = loadQueue(manga, next) ?: return@withContext null
                next.id to text
            } ?: return@launch
            if (chapter?.id != current.id || prefetch != null) return@launch
            prefetch = loaded
            primeSupertonic()
        }
    }

    /** Current chapter first, then the next one, so speech starts on audio that is already ready. */
    private fun primeSupertonic() {
        if (!TtsEngines.isSupertonic(readerPreferences.ttsEngine.get())) return
        SupertonicRuntime.plan(
            this,
            readerPreferences.ttsVoice.get(),
            readerPreferences.ttsSpeed.get(),
            queue.drop(queuePos).map { it.text },
            prefetch?.second?.map { it.text }.orEmpty(),
        )
    }

    /**
     * The chapter's speakable paragraphs: downloaded text first, then the chapter cache, then the
     * source (cached for the reader, like [eu.kanade.tachiyomi.ui.reader.loader.NovelPageLoader]).
     * Null when the source isn't a novel source or the chapter is locked.
     */
    private suspend fun loadQueue(manga: Manga, chapter: Chapter): List<QueueItem>? {
        val source = sourceManager.get(manga.source) as? NovelSource ?: return null
        val html = try {
            downloadedText(manga, chapter)
                ?: chapterCache.getChapterText(chapter)
                ?: run {
                    val content = source.getChapterContent(chapter.toSChapter())
                    if (content.locked) return null
                    content.html.also { if (it.isNotBlank()) chapterCache.putChapterText(chapter, it) }
                }
        } catch (e: Exception) {
            logcat(LogPriority.ERROR, e) { "TTS failed to load chapter text" }
            return null
        }
        return speakableBlocks(NovelHtml.parse(html)).map { (index, speech) ->
            QueueItem(index, speech)
        }
    }

    private suspend fun downloadedText(manga: Manga, chapter: Chapter): String? {
        val source = sourceManager.get(manga.source) ?: return null
        val dir = downloadProvider.findChapterDir(
            chapter.name,
            chapter.scanlator,
            chapter.url,
            manga.title,
            source,
        ) ?: return null
        return dir.findFile(Downloader.NOVEL_CHAPTER_FILE)
            ?.openInputStream()
            ?.use { it.bufferedReader().readText() }
    }

    // Progress and history

    private fun saveProgress(blockIndex: Int, completesChapter: Boolean) {
        if (incognito) return
        val chapter = chapter ?: return
        val read = chapter.read || completesChapter
        scope.launch(Dispatchers.IO) {
            updateChapter.await(
                ChapterUpdate(chapter.id) {
                    this.read = read
                    lastPageRead = blockIndex
                },
            )
        }
    }

    private suspend fun writeHistory(chapterId: Long) {
        if (incognito) return
        val duration = (System.currentTimeMillis() - chapterStartedAt).coerceAtLeast(0)
        upsertHistory.await(HistoryUpdate(chapterId, Clock.System.now(), duration))
    }

    // Controls

    private fun setSleepTimer(minutes: Int) {
        sleepJob?.cancel()
        if (minutes <= 0) {
            sleepJob = null
            sleepAtMillis = null
            if (!playing) releaseWakeLock()
        } else {
            val endsAt = System.currentTimeMillis() + minutes * 60_000L
            sleepAtMillis = endsAt
            acquireWakeLock()
            sleepJob = scope.launch {
                while (true) {
                    val left = endsAt - System.currentTimeMillis()
                    if (left <= 0L) break
                    delay(minOf(left, 15_000L))
                    updateNotification()
                }
                if (sleepAtMillis != endsAt) return@launch
                sleepAtMillis = null
                pause()
            }
        }
        publishState()
        updateNotification()
    }

    private fun setSpeed(speed: Float) {
        val value = speed.coerceIn(0.5f, 3f)
        readerPreferences.ttsSpeed.set(value)
        engine.setSpeed(value)
        publishState()
        // The new rate applies from the next utterance; restart the current one so it's immediate.
        if (playing) seekToPosition(queuePos)
    }

    /** Picks up an engine, voice or pitch change made in settings while a session is open. */
    private fun applySettings() {
        val wanted = readerPreferences.ttsEngine.get()
        if (wanted != engineId) {
            val wasPlaying = playing
            stopPlayback()
            engine.release()
            openEngine()
            if (wasPlaying) play() else publishState()
            return
        }
        applyEngineSettings()
        if (playing) seekToPosition(queuePos) else publishState()
    }

    private fun openEngine() {
        val id = readerPreferences.ttsEngine.get()
        engineId = id
        engine = if (TtsEngines.isSupertonic(id)) {
            SupertonicTtsEngine(this)
        } else {
            AndroidTtsEngine(this, TtsEngines.systemPackage(id))
        }
        applyEngineSettings()
    }

    private fun applyEngineSettings() {
        if (!::engine.isInitialized) return
        engine.setSpeed(readerPreferences.ttsSpeed.get())
        engine.setPitch(readerPreferences.ttsPitch.get())
        engine.setVoice(readerPreferences.ttsVoice.get().ifBlank { null })
    }

    private fun stopSession() {
        startJob?.cancel()
        val finished = chapter
        stopPlayback()
        if (finished != null) {
            // NonCancellable detaches the write from [scope]: stopSelf destroys the service right away.
            scope.launch(Dispatchers.IO + NonCancellable) { writeHistory(finished.id) }
        }
        chapter = null
        manga = null
        queue = emptyList()
        TtsStatus.update(null)
        mediaSession.isActive = false
        abandonFocus()
        releaseWakeLock()
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    // Audio focus and wake lock

    private fun requestFocus(): Boolean {
        val request = focusRequest ?: AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build(),
            )
            .setOnAudioFocusChangeListener(focusListener)
            .build()
            .also { focusRequest = it }
        return audioManager.requestAudioFocus(request) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
    }

    private fun abandonFocus() {
        focusRequest?.let { audioManager.abandonAudioFocusRequest(it) }
    }

    private fun acquireWakeLock() {
        if (wakeLock?.isHeld == true) return
        wakeLock = getSystemService<PowerManager>()!!
            .newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "dokusho:tts")
            .also { it.acquire(6 * 60 * 60 * 1000L) }
    }

    private fun releaseWakeLock() {
        wakeLock?.takeIf { it.isHeld }?.release()
        wakeLock = null
    }

    // State, media session and notification

    private fun publishState(blockIndex: Int? = null, session: Boolean = true) {
        val manga = manga ?: return
        val chapter = chapter ?: return
        val index = blockIndex ?: queue.getOrNull(queuePos)?.blockIndex ?: 0
        TtsStatus.update(
            TtsState(
                mangaId = manga.id,
                chapterId = chapter.id,
                blockIndex = index,
                playing = playing,
                speed = readerPreferences.ttsSpeed.get(),
                sleepAtMillis = sleepAtMillis,
                spokenStart = spokenStart,
                spokenEnd = spokenEnd,
                wordStart = wordStart,
                wordEnd = wordEnd,
            ),
        )
        if (!session) return
        // Speed 0 while paused is what headset buttons use to decide play versus pause.
        val state = if (playing) PlaybackStateCompat.STATE_PLAYING else PlaybackStateCompat.STATE_PAUSED
        val rate = if (playing) readerPreferences.ttsSpeed.get() else 0f
        mediaSession.setPlaybackState(
            PlaybackStateCompat.Builder()
                .setActions(
                    PlaybackStateCompat.ACTION_PLAY or
                        PlaybackStateCompat.ACTION_PAUSE or
                        PlaybackStateCompat.ACTION_PLAY_PAUSE or
                        PlaybackStateCompat.ACTION_SKIP_TO_NEXT or
                        PlaybackStateCompat.ACTION_SKIP_TO_PREVIOUS or
                        PlaybackStateCompat.ACTION_STOP,
                )
                .setState(state, 0L, rate)
                .build(),
        )
    }

    private fun updateMetadata() {
        publishMetadata()
        loadCover()
    }

    private fun publishMetadata() {
        mediaSession.setMetadata(
            MediaMetadataCompat.Builder()
                .putString(MediaMetadataCompat.METADATA_KEY_TITLE, chapter?.name)
                .putString(MediaMetadataCompat.METADATA_KEY_ARTIST, manga?.title)
                .apply { cover?.let { putBitmap(MediaMetadataCompat.METADATA_KEY_ALBUM_ART, it) } }
                .build(),
        )
    }

    /** Fetches the novel cover once per novel, through the app's image loader and its cover cache. */
    private fun loadCover() {
        val current = manga
        if (current == null || !readerPreferences.ttsShowCover.get()) {
            if (cover != null || coverFor != null) {
                cover = null
                coverFor = null
                coverJob?.cancel()
                refreshCover()
            }
            return
        }
        if (coverFor == current.id) return
        coverFor = current.id
        coverJob?.cancel()
        coverJob = scope.launch {
            val request = ImageRequest.Builder(this@TtsPlaybackService)
                .data(current)
                .size(COVER_SIZE)
                .allowHardware(false)
                .build()
            val bitmap = imageLoader.execute(request).image?.asDrawable(resources)?.getBitmapOrNull()
            if (manga?.id != current.id) return@launch
            cover = bitmap
            refreshCover()
        }
    }

    private fun refreshCover() {
        publishMetadata()
        if (TtsStatus.state.value != null) updateNotification()
    }

    private fun updateNotification() {
        notify(Notifications.ID_TTS, buildNotification())
    }

    private fun buildNotification(text: String? = null) = notificationBuilder(Notifications.CHANNEL_TTS) {
        setSmallIcon(R.drawable.ic_dokusho)
        setContentTitle(chapter?.name ?: stringResource(MR.strings.tts_notification_title))
        setContentText(text ?: manga?.title)
        setLargeIcon(cover)
        setOngoing(playing)
        setShowWhen(false)
        setSilent(true)
        setCategory(NotificationCompat.CATEGORY_TRANSPORT)
        setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
        sleepAtMillis?.let { endsAt ->
            val leftMs = endsAt - System.currentTimeMillis()
            if (leftMs > 0L) {
                val minutesLeft = ((leftMs + 59_999L) / 60_000L).toInt()
                setSubText(stringResource(MR.strings.tts_sleep_in, minutesLeft))
            }
        }
        manga?.let {
            setContentIntent(
                PendingIntent.getActivity(
                    this@TtsPlaybackService,
                    0,
                    ReaderActivity.newIntent(this@TtsPlaybackService, it.id, chapter?.id),
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
                ),
            )
        }
        setDeleteIntent(actionIntent(ACTION_STOP))
        addAction(
            MaterialSymbolsR.drawable.rounded_skip_previous,
            stringResource(MR.strings.tts_previous_paragraph),
            actionIntent(ACTION_PREVIOUS_PARAGRAPH),
        )
        if (playing) {
            addAction(
                MaterialSymbolsR.drawable.rounded_filled_pause,
                stringResource(MR.strings.action_pause),
                actionIntent(ACTION_PAUSE),
            )
        } else {
            addAction(
                MaterialSymbolsR.drawable.rounded_filled_play_arrow,
                stringResource(MR.strings.action_resume),
                actionIntent(ACTION_PLAY),
            )
        }
        addAction(
            MaterialSymbolsR.drawable.rounded_skip_next,
            stringResource(MR.strings.tts_next_paragraph),
            actionIntent(ACTION_NEXT_PARAGRAPH),
        )
        addAction(
            MaterialSymbolsR.drawable.rounded_book,
            stringResource(MR.strings.novel_next_chapter),
            actionIntent(ACTION_NEXT_CHAPTER),
        )
        addAction(
            MaterialSymbolsR.drawable.rounded_close,
            stringResource(MR.strings.action_stop),
            actionIntent(ACTION_STOP),
        )
        setStyle(
            androidx.media.app.NotificationCompat.MediaStyle()
                .setMediaSession(mediaSession.sessionToken)
                .setShowActionsInCompactView(0, 1, 2),
        )
    }.build()

    private fun actionIntent(action: String): PendingIntent = PendingIntent.getService(
        this,
        action.hashCode(),
        Intent(this, TtsPlaybackService::class.java).setAction(action),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    /**
     * The sentence that contains [start, end), so the word being spoken shows inside its sentence.
     */
    private fun sentenceBounds(text: String, start: Int, end: Int): Pair<Int, Int> {
        if (text.isEmpty()) return 0 to 0
        val anchor = start.coerceIn(0, text.lastIndex)
        val closing = end.coerceIn(anchor, text.lastIndex)
        var from = 0
        var index = 0
        while (index < anchor) {
            if (isSentenceEnd(text, index)) {
                val next = skipAfterSentence(text, index)
                if (next <= anchor) from = next
            }
            index++
        }
        var to = text.length
        index = closing
        while (index < text.length) {
            if (isSentenceEnd(text, index)) {
                to = skipAfterSentence(text, index).let { next ->
                    // Keep the terminator and closing quotes; drop only the following space.
                    var endAt = index + 1
                    while (endAt < text.length && text[endAt] in "\"'”’)") endAt++
                    endAt.coerceAtMost(next)
                }
                break
            }
            index++
        }
        return from to to.coerceAtLeast(from)
    }

    private fun isSentenceEnd(text: String, index: Int): Boolean {
        val char = text[index]
        if (char !in ".!?") return false
        val next = text.getOrNull(index + 1) ?: return true
        if (next.isDigit() || next == '.' || next == '!' || next == '?') return false
        return true
    }

    private fun skipAfterSentence(text: String, period: Int): Int {
        var next = period + 1
        while (next < text.length && text[next] in "\"'”’)") next++
        while (next < text.length && text[next].isWhitespace()) next++
        return next
    }

    companion object {
        private const val COVER_SIZE = 512

        private const val ACTION_START = "eu.kanade.tachiyomi.TTS_START"
        private const val ACTION_PLAY_PAUSE = "eu.kanade.tachiyomi.TTS_PLAY_PAUSE"
        private const val ACTION_PLAY = "eu.kanade.tachiyomi.TTS_PLAY"
        private const val ACTION_PAUSE = "eu.kanade.tachiyomi.TTS_PAUSE"
        private const val ACTION_PREVIOUS_PARAGRAPH = "eu.kanade.tachiyomi.TTS_PREVIOUS_PARAGRAPH"
        private const val ACTION_NEXT_PARAGRAPH = "eu.kanade.tachiyomi.TTS_NEXT_PARAGRAPH"
        private const val ACTION_NEXT_CHAPTER = "eu.kanade.tachiyomi.TTS_NEXT_CHAPTER"
        private const val ACTION_SLEEP = "eu.kanade.tachiyomi.TTS_SLEEP"
        private const val ACTION_SPEED = "eu.kanade.tachiyomi.TTS_SPEED"
        private const val ACTION_APPLY = "eu.kanade.tachiyomi.TTS_APPLY"
        private const val ACTION_STOP = "eu.kanade.tachiyomi.TTS_STOP"

        private const val EXTRA_MANGA_ID = "manga_id"
        private const val EXTRA_CHAPTER_ID = "chapter_id"
        private const val EXTRA_BLOCK_INDEX = "block_index"
        private const val EXTRA_SLEEP_MINUTES = "sleep_minutes"
        private const val EXTRA_SPEED = "speed"

        /** Starts reading [chapterId] aloud at [blockIndex]; a call for the active chapter seeks. */
        fun start(context: Context, mangaId: Long, chapterId: Long, blockIndex: Int) {
            val intent = Intent(context, TtsPlaybackService::class.java)
                .setAction(ACTION_START)
                .putExtra(EXTRA_MANGA_ID, mangaId)
                .putExtra(EXTRA_CHAPTER_ID, chapterId)
                .putExtra(EXTRA_BLOCK_INDEX, blockIndex)
            context.startForegroundService(intent)
        }

        /**
         * The reader switched [mangaId] to [chapterId] with its chapter buttons. A voice reading
         * another chapter of it moves along when playing, and ends when paused.
         */
        fun followReader(context: Context, mangaId: Long?, chapterId: Long?) {
            val state = TtsStatus.state.value ?: return
            if (mangaId == null || chapterId == null) return
            if (state.mangaId != mangaId || state.chapterId == chapterId) return
            if (state.playing) start(context, mangaId, chapterId, 0) else stop(context)
        }

        fun playPause(context: Context) = send(context, ACTION_PLAY_PAUSE)
        fun previousParagraph(context: Context) = send(context, ACTION_PREVIOUS_PARAGRAPH)
        fun nextParagraph(context: Context) = send(context, ACTION_NEXT_PARAGRAPH)
        fun nextChapter(context: Context) = send(context, ACTION_NEXT_CHAPTER)
        fun stop(context: Context) = send(context, ACTION_STOP)

        fun setSleepTimer(context: Context, minutes: Int) {
            context.startService(
                Intent(context, TtsPlaybackService::class.java)
                    .setAction(ACTION_SLEEP)
                    .putExtra(EXTRA_SLEEP_MINUTES, minutes),
            )
        }

        fun setSpeed(context: Context, speed: Float) {
            context.startService(
                Intent(context, TtsPlaybackService::class.java)
                    .setAction(ACTION_SPEED)
                    .putExtra(EXTRA_SPEED, speed),
            )
        }

        /** Applies engine, voice, pitch and speed preferences to a session that is already open. */
        fun applySettings(context: Context) {
            if (TtsStatus.state.value == null) return
            context.startService(Intent(context, TtsPlaybackService::class.java).setAction(ACTION_APPLY))
        }

        private fun send(context: Context, action: String) {
            context.startService(Intent(context, TtsPlaybackService::class.java).setAction(action))
        }
    }
}

@Suppress("DEPRECATION")
private fun Intent.mediaKey(): KeyEvent? = getParcelableExtra(Intent.EXTRA_KEY_EVENT)
