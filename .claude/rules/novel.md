# Novel pack

The text-specific design. Read for SOURCE-API and READER work. These are decisions from
`docs/PLAN.md`; change them there first if they need to change.

## Source contract (lib 2.0)

```kotlin
// source-api/src/main/kotlin/eu/kanade/tachiyomi/source/NovelSource.kt
interface NovelSource : CatalogueSource {
    suspend fun getChapterContent(chapter: SChapter): ChapterContent
}

// source-api/src/main/kotlin/eu/kanade/tachiyomi/source/model/ChapterContent.kt
class ChapterContent(
    val html: String,          // sanitized subset: p, h1-h4, em, strong, br, hr, img, blockquote
    val title: String? = null, // chapter heading if the site has one
)

// source-api/src/main/kotlin/eu/kanade/tachiyomi/source/online/HttpNovelSource.kt
abstract class HttpNovelSource : HttpSource(), NovelSource {
    protected open fun chapterContentRequest(chapter: SChapter): Request = GET(baseUrl + chapter.url, headers)
    protected abstract fun chapterContentParse(response: Response): ChapterContent
    override suspend fun getChapterContent(chapter: SChapter) =
        chapterContentParse(client.newCall(chapterContentRequest(chapter)).awaitSuccess())
    override suspend fun getPageList(chapter: SChapter): List<Page> = emptyList()
}
```

Loader constants (`ExtensionLoader.kt`): feature `dokusho.extension`, meta-data
`dokusho.extension.class`, `dokusho.extension.factory`, `dokusho.extension.nsfw`,
`dokusho.name`, `dokusho.extensionLib`, `dokusho.contentWarning`, `SUPPORTED_LIB_VERSIONS = listOf(2.0)`.
Manga APKs then fail the feature check and never load. `consumer-proguard.pro` keeps the new types.

The extensions-lib stub in `dokusho-extensions-source/extensions-lib` mirrors `source-api`
exactly. Change both in the same PR.

## Content sanitising

Extensions return already-clean HTML. The app still runs it through one jsoup
`Safelist` (`core/common` → `NovelHtml.sanitize`) before caching: allowed tags above,
`img src` absolute only, no inline styles, no scripts, no `data:` URIs over 1 MB.
Parse once into `List<Block>` (`Paragraph(text: AnnotatedString)`, `Heading(level, text)`,
`Image(url)`, `Divider`, `Quote`). Blocks are the unit for rendering, progress, TTS and export.

## Reader

- `NovelViewer : Viewer` wraps a `ComposeView` with a `LazyColumn` of blocks.
- `NovelChapterLoader : PageLoader` resolves content: downloaded `chapter.html` → `ChapterCache` →
  `NovelSource.getChapterContent`. Returns one `ReaderPage` whose stream is the HTML; the
  viewer parses it to blocks.
- Progress = index of the first fully visible block, stored in `chapter.last_page_read`.
  Chapter marked read when the last block becomes visible. No new column needed.
- Settings (`ReaderPreferences`): font family (system, serif, sans, OpenDyslexic later), size
  12 to 32 sp, line height 1.2 to 2.0, paragraph spacing, horizontal margin, theme (paper,
  sepia, dark, black), keep screen on, volume-key scroll, tap zones (prev / menu / next).
- Chapter transitions reuse `ChapterTransition`. Prefetch the next chapter after 60 percent.
- Hide for novel sources: crop, split, color filter, rotation, save, share, set as cover.

## Downloads

- `Downloader`: `if (source is NovelSource)` branch writes `chapter.html` and `meta.json`
  (title, url, fetched_at) in the chapter folder. Success = non-empty `chapter.html`.
- `DownloadCache.isChapterDir` also accepts a folder containing `chapter.html`.
- `DownloadPageLoader`: if `chapter.html` exists, hand it to `NovelChapterLoader`.
- No `ComicInfo.xml`, no CBZ for novels.

## Export

- `ExportWorker` (CoroutineWorker, foreground dataSync, channel `CHANNEL_EXPORT`).
- Input: manga id, chapter range, format, split. Source of text: downloaded `chapter.html`
  first, else fetch through the source with its rate limit when "download missing" is on.
- EPUB 3 via `ZipWriter`: `mimetype` stored uncompressed first, `META-INF/container.xml`,
  `OEBPS/content.opf`, `nav.xhtml`, `ch0001.xhtml` ..., `style.css`, cover from Coil disk cache.
- PDF via `android.graphics.pdf.PdfDocument`: A5, `StaticLayout` pagination, heading per
  chapter, footer page numbers.
- Settings: default format (EPUB), split (EPUB 0 = whole book, PDF 100 chapters), output
  folder `<storage root>/exports/<Title>/`.
- File names: `<Title> - ch0001-0100.epub`. Never a slash or em dash in a name.

## TTS

- `TtsEngine` interface: `init()`, `voices()`, `speak(id, text)`, `stop()`, `setRate`, `setPitch`,
  `events: Flow<TtsEvent>` (started, done, error). `AndroidTtsEngine` wraps `TextToSpeech`
  with `UtteranceProgressListener`. `SupertonicEngine` later, same interface, PCM through `AudioTrack`.
- `TtsPlaybackService`: foreground service, `foregroundServiceType="mediaPlayback"`,
  `FOREGROUND_SERVICE_MEDIA_PLAYBACK` permission, `MediaSessionCompat`, MediaStyle notification
  with play/pause, previous block, next block, next chapter, speed. Audio focus, headset
  buttons, sleep timer, partial wake lock while playing.
- Queue = blocks of the current chapter. Speak one block per utterance. On chapter end, load
  the next through `GetNextChapters` and keep going. Update `last_page_read` and history as
  blocks complete, so the reader reopens where the voice stopped.
- Reader: Listen action in the reader menu, current block highlighted, tap a block to start there.
- Never read aloud or log locked or paywalled text the user cannot see in the reader.
