# Architecture pack

Read for FEATURE, SOURCE-API, READER and DATA work. Only the sections your mode needs.

## Modules (`settings.gradle.kts`)

| Module | Owns |
|--------|------|
| `app` | all UI, reader, downloads, extension manager, backup, workers, trackers |
| `source-api` | the extension contract, compiled into the app and mirrored by the extensions-lib stub |
| `source-local` | `LocalSource` for files on disk |
| `core:common` | OkHttp stack (Cloudflare, WebView, rate limit, DoH), preferences, storage, `ImageUtil` |
| `core:archive` | libarchive reader, `EpubReader`, `ZipWriter` |
| `core:metro` | DI qualifiers |
| `core-metadata` | `ComicInfo.xml` models (manga-specific, candidate for removal later) |
| `data` | SQLDelight database, repository impls, extension store network service |
| `domain` | models, repository interfaces, interactors, `StorageManager` |
| `i18n` | moko-resources strings, about 70 locales |
| `presentation-core`, `presentation-widget` | shared Compose components, theme, Glance widget |
| `telemetry` | Firebase vs noop source sets, chosen by `-Pdist` |
| `baseline-profile` | startup profile |

Versions: Kotlin 2.4, AGP 9.4, Compose BOM 2026.10, SQLDelight 2.4, Metro 1.4, min SDK 26,
target 36, compile 37.2 (`gradle/mihon.versions.toml`).

## Layers and DI

Compose screen (`eu.kanade.presentation.*`) → Voyager `ScreenModel` (`eu.kanade.tachiyomi.ui.*`)
→ interactor (`tachiyomi.domain.*.interactor`) → repository interface (`tachiyomi.domain.*.repository`)
→ `*RepositoryImpl` in `data/` over SQLDelight queries, or a `Source`.

Metro DI: graph `mihon/app/di/AppGraph.kt`, bindings in `AppBindings.kt` and per-module
`@BindingContainer`s. Pattern for a new singleton:

```kotlin
@Inject
@SingleIn(AppScope)
@ContributesBinding(AppScope)
class ChapterContentRepositoryImpl(private val handler: DatabaseHandler) : ChapterContentRepository
```

Extensions get dependencies through `MetroInjektRegistrar` (Application, Context, Json,
ProtoBuf, XML, NetworkHelper, JavaScriptEngine). Add there only if an extension must see it.

## Extensions

- Contract: `source-api` `Source` → `CatalogueSource` → `HttpSource` → `ParsedHttpSource`.
  Models `SManga`, `SChapter`, `Page`, `SMangaUpdate`, `memo` JSON on manga and chapter.
- Loader: `ExtensionLoader.kt` constants: feature `tachiyomi.extension`, meta-data
  `tachiyomi.extension.class|factory|nsfw`, `tachiyomix.name|extensionLib|contentWarning`,
  `SUPPORTED_LIB_VERSIONS = listOf(1.4, 1.6)`. Dokusho changes these to `dokusho.*` and `[2.0]`
  (see `novel.md`).
- Classes load through `DelegateLastClassLoaderCompat` from the APK; signature SHA-256 is
  checked against the store's `signingKey`, then `TrustExtension`.
- Store index: `ExtensionStoreService.kt` accepts legacy `index.min.json` arrays, legacy
  `repo.json` with `index_v2`, JSON v2, or gzipped protobuf v2.
- Install: `ExtensionInstaller.kt` (legacy intent, private copy, PackageInstaller, Shizuku).
- Keep all of this. Only constants and the lib version change.

## Reader

- `ReaderActivity` (View-based, 940 lines) hosts a `Viewer` chosen by `ReadingMode.toViewer`.
  `ReaderViewModel` (1,150 lines) owns chapter navigation, progress, history, download-ahead.
- Loaders: `ChapterLoader` picks `DownloadPageLoader`, local loaders, or `HttpPageLoader`.
  `ReaderPage` extends `Page` with `stream: () -> InputStream`.
- Progress: `chapter.last_page_read` int, `updateChapterProgress` debounced, history
  `read_at` / `read_duration`.
- Reusable for novels: navigation, skip-read, history, download-ahead, delete-after-read,
  chapter transitions. Image-only: all three viewers, `ReaderPageImageView`, crop, split,
  color filters, save/share/set-cover.

## Downloads

- `Downloader` → per chapter `getPageList`, image files into `<chapter>_tmp/`, verify count,
  `ComicInfo.xml`, rename or zip to `.cbz`, `.nomedia`.
- Layout: `<storage root>/downloads/<Source (LANG)>/<Title>/<scanlator_chapter>{/ | .cbz}`.
  Names from `DownloadProvider`. Index: `DownloadCache` protobuf at `cacheDir/dl_index_cache_v3`.
- Queue: `DownloadStore` (prefs JSON), `DownloadWorker` (foreground dataSync), `DownloadNotifier`.
- Read-back: `DownloadPageLoader` → `DownloadManager.buildPageList`.

## Data

- SQLDelight async, `AndroidxSqliteDriver` over `BundledSQLiteDriver`, WAL, file `tachiyomi.db`.
- Tables: `manga`, `chapter`, `history`, `category`, `manga_category`, `manga_track`,
  `excluded_scanlator`, `extension_store`, `source`. Views `libraryView`, `historyView`, `updatesView`.
- Migrations `migrations/1.sqm` to `19.sqm`; next is `20.sqm`. Preference migrations in
  `app/src/main/java/mihon/core/migration/migrations/`.
- Backup: gzipped protobuf `.tachibk`, metadata only. Do not put chapter content in it.

## Background work

CoroutineWorkers in `app/.../data/{library,backup,download}` and
`eu/kanade/domain/track/service`. Foreground type is `dataSync` everywhere. Notification
channels in `data/notification/Notifications.kt`, created by `createChannels`. There is no
media service, MediaSession or audio code anywhere yet.
