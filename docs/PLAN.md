# Dokusho - plan

Web novel reader for Android, built on Mihon. Reads novels from public sites through
installable extensions, downloads them for offline reading, exports EPUB and PDF, and reads
aloud in the background.

Status (2026-10-19): repos created and pushed, Mihon-only files removed, Claude Code config
in place. Phase 0 done and released as v0.1.0. Phase 1 done (text contract, Royal Road published). Phase 2 reader shipped in v0.2.0.

## Decisions taken

| Topic | Decision | Why |
|-------|----------|-----|
| Platform | Android only | Mihon-style extension APKs only work there |
| Language | Kotlin, fork of Mihon `deeb9b2` (0.21.2) | Keeps library, browse, updates, download queue, extension installer, backup |
| Name, id | Dokusho (読書, "reading"), app id `app.dokusho`, confirmed | One Japanese word like Mihon; "Shosetsu" and "Yomu" are taken by other readers |
| Extensions | Keiyoushi toolchain (convention plugins, `dokusho {}` DSL, KSP, `DokushoSource`) in our own repo, new lib version `2.0` with a text API | Proven build and publish pipeline; new lib version keeps manga APKs from loading |
| Reader | Compose paragraph list, not WebView | Paragraph index doubles as progress, TTS highlight and export block model |
| Downloads | `chapter.html` per chapter inside Mihon's existing folder layout | Download queue, cache index and notifier stay as they are |
| Export | EPUB default, PDF optional; split is a setting, default EPUB whole book and PDF every 100 chapters | Qasim asked for a changeable default |
| TTS | Android system `TextToSpeech` behind a `TtsEngine` interface; Supertonic 3 (ONNX, on-demand 300 to 400 MB download) as a later optional engine | Free, offline, works today; Supertonic upstream is archived and still skips words on short text |
| Trackers | Off for now. Personal use, reading and listening first | Qasim has no AniList or MAL account; revisit later |
| Paid sites | Webnovel and WuxiaWorld through the user's own login, locked chapters stay locked | No paywall bypass |
| Telemetry | Firebase Crashlytics and Analytics in CI and GitHub release builds, project under Qasim's Google account; FOSS and local builds have none. Config via `GOOGLE_SERVICES_JSON` secret, never committed | Qasim asked for his own Firebase; crash reports matter for a scraping app |
| Updater | On for release builds, reads GitHub releases of `Globeeeee/dokusho` | Free update channel without a store |
| JDK | 21, Android Studio's bundled JBR locally (`.claude/settings.local.json`), Temurin 21 in CI | Metro Gradle plugin 1.4.5 refuses JDK 17 |

## Repos

| Local folder | GitHub | Role |
|--------------|--------|------|
| `dokusho/` | Globeeeee/dokusho | App. `upstream` remote = mihonapp/mihon. This file is the master plan |
| `dokusho-extensions-source/` | Globeeeee/dokusho-extensions-source | Extension modules, core, compiler, publish scripts (from keiyoushi/extensions-source `314a0d1b`, manga modules removed). Own slice in its `docs/PLAN.md` |
| `dokusho-extensions/` | Globeeeee/dokusho-extensions | Published index on branch `repo`: `index.pb`, `index.json`, `repo.json`. APKs on Releases |

## What Mihon gives us unchanged

Extension discovery, store index parsing (legacy and v2 protobuf), signature and trust
checks, private and Shizuku install · network stack with Cloudflare and WebView interceptors,
rate limiting, DoH · SQLDelight schema for manga, chapter, history, category, track ·
library, browse, search, updates, history, categories UI · library update worker and
new-chapter notifications · download queue, worker, notifier · Metro DI · preferences ·
storage manager · i18n (moko) · backup (metadata only) · CI build and test steps.

## What must change for text

| Subsystem | Today | Change |
|-----------|-------|--------|
| `source-api` | `Page` is image only | Add `NovelSource` + `ChapterContent(html)`; `HttpNovelSource` with request/parse hooks; lib version 2.0; feature `dokusho.extension`, meta keys `dokusho.extension.*` |
| `ExtensionLoader` | `SUPPORTED_LIB_VERSIONS = [1.4, 1.6]`, `tachiyomi.*` keys | `[2.0]`, new keys. Everything else in the loader stays |
| Reader | Pager, webtoon and WebGPU image viewers | `NovelViewer` (Compose), `NovelChapterLoader`, typography settings, progress = paragraph index in `last_page_read` |
| `ChapterCache` | Page-list JSON + image files | Chapter HTML keyed by chapter URL |
| Downloader, DownloadProvider, DownloadCache, DownloadPageLoader | Image files or CBZ, success = image count | `chapter.html` + `meta.json`, success = non-empty file |
| `LocalSource`, `core:archive` | EPUB images only | EPUB text spine import; `.txt` and `.html` folders |
| Export | CSV library export only | New `ExportWorker`, EPUB via `ZipWriter`, PDF via `PdfDocument` |
| Media | None | `TtsPlaybackService` foreground `mediaPlayback`, MediaSession, notification channel |
| Branding | `app.mihon`, Mihon strings, release guard on `mihonapp/mihon` | `app.dokusho`, strings, icon, workflow guard, updater and telemetry off |

Design detail for each row: `.claude/rules/novel.md`.

## Phases

### 0. Build green and branded (1 to 2 days)
- [x] Repos, remotes, Mihon community files removed, Claude Code config
- [x] `./gradlew :app:assembleDebug -Pdist=local` passes with JDK 21 (2026-10-19, five APKs)
- [x] App id `app.dokusho` (dev `app.dokusho.dev`), version 0.1.0 (code 1), name Dokusho in every locale, deep-link schemes `dokusho` and `app.dokusho`
- [x] Original launcher icon (adaptive, themed, splash, notification glyph, dev variant), SVG master in `docs/assets/icon.svg`
- [x] `release.yml` guard and APK names, updater on `Globeeeee/dokusho`, About links, privacy policy in `docs/PRIVACY.md`
- [x] Mihon's tracked `google-services.json` removed; CI writes ours from a secret or builds without telemetry
- [x] Firebase project `dokusho-reader` under Qasim's account, Android apps `app.dokusho`, `.dev`, `.debug`, secret `GOOGLE_SERVICES_JSON` set (config kept at `~/.dokusho/signing/google-services.json`)
- [x] Trackers switched off for personal use (no tracker UI); setup moved to Later
- [x] Mihon's donation popup and "Support us" menu hidden; its strings left untouched on purpose since they describe Mihon's own campaign
- [ ] Package `eu.kanade.tachiyomi` stays: extensions-lib compatibility depends on it
- Done when: debug APK installs, opens, an extension repo URL can be added.

### 1. Text source contract (2 days)
- [x] `NovelSource` (standalone interface), `ChapterContent(html, title, locked)`, `HttpNovelSource` in `source-api`, proguard keeps
- [x] Loader: feature `dokusho.extension`, `dokusho.*` meta-data, `SUPPORTED_LIB_VERSIONS = [2.0]`
- [ ] App calls `getChapterContent` for `NovelSource` (lands with the reader, phase 2)
- `source-api`: `NovelSource`, `ChapterContent`, `HttpNovelSource`; `getPageList` returns empty for novel sources.
- Loader constants and lib version; `consumer-proguard.pro` keeps.
- Extension repo: `:extensions-lib` stub module mirroring the new API, `libVersion = "2.0"`, generated manifest emits `dokusho.*` keys, `DokushoSource` gains `getChapterContent`.
- Done when: a hello-world extension built from the extension repo installs and lists in the app.

### 2. Reader (1 week)
- [x] `NovelPageLoader`: chapter HTML → safelist → blocks, one reader page per paragraph (progress = paragraph index)
- [x] `NovelViewer` (Compose): reading modes Scroll, Pages left to right, Pages right to left, Pages top to bottom; tap zones; volume keys; end-of-chapter next/previous; locked chapters shown as a message
- [x] Text settings sheet (Aa): font size, line spacing, margins, themes paper/sepia/dark/black
- [ ] Font family choice, keep-screen-on toggle in the sheet, images inside chapters
- `NovelChapterLoader` → cache → `List<Block>` parsed with jsoup from sanitized HTML.
- `NovelViewer`: LazyColumn, tap zones, volume-key scroll, progress bar, chapter transitions reuse `ChapterTransition`.
- Settings: font family, size, line height, paragraph spacing, margins, themes (paper, sepia, dark, black), keep screen on, fullscreen.
- Progress: first visible paragraph index into `last_page_read`; mark read at 95 percent.
- Hide image actions (save, share, set cover, crop, split) for novel sources.
- Done when: a 3,000-word chapter scrolls smoothly, position restores, next chapter preloads.

### 3. Offline downloads (3 days)
- Downloader branch for `NovelSource`: fetch content, write `chapter.html` + `meta.json`.
- Provider, cache and page loader recognise the new layout; delete-after-read and download-ahead keep working.
- Done when: downloaded chapters open in airplane mode and show the downloaded badge.

### 4. First extensions (3 days, then ongoing)
Detail and site list in `dokusho-extensions-source/docs/PLAN.md`.
- Batch 1: RoyalRoad, ScribbleHub (clean public HTML).
- Batch 2: shadowslave.space, freewebnovel.com, novellive.com, wuxiaworld.eu, readnovelfull.com, novelhi.com, noveltrust.com (candidates from a web search on 2026-10-19; verify each is live and has usable HTML before building). Several run WordPress themes, so a `madara-novel` multisrc theme should cover more than one.
- Batch 3: Webnovel and WuxiaWorld with WebView login, cookie reuse, locked chapters shown as locked.
- Publish: signing keystore + secrets, `publish-repo.py` already points at `Globeeeee/dokusho-extensions`.
- Done when: `index.pb` loads in the app and one extension installs from it.

MVP = phases 0 to 4, roughly 3 weeks.

### 5. Export EPUB and PDF (4 days)
- `ExportWorker` (foreground, dataSync) with progress notification and cancel.
- EPUB 3: `mimetype` stored first, `container.xml`, OPF, nav, one XHTML per chapter, CSS, cover from Coil cache.
- PDF: `PdfDocument`, A5, `StaticLayout` pagination, chapter headings, page numbers.
- UI: novel menu "Export" sheet: format, range (all, downloaded, from-to), split; defaults from settings.
- Missing chapters fetched during export when "download missing" is on, honouring the source rate limit.
- Output to `<storage root>/exports/<Title>/` through SAF.
- Done when: a 300-chapter novel exports to one EPUB that opens in another reader and to three PDFs.

### 6. Background TTS (1 week)
- `TtsEngine` interface; `AndroidTtsEngine` first.
- `TtsPlaybackService`: foreground `mediaPlayback`, `MediaSessionCompat`, MediaStyle notification (play, pause, previous and next paragraph, next chapter), headset buttons, audio focus, sleep timer, partial wake lock.
- Queue = blocks of the current chapter; auto-advance via `GetNextChapters`; progress and history updated as it reads; next chapter prefetched.
- Reader: Listen button, current paragraph highlighted, tap a paragraph to start there.
- Later: `SupertonicEngine` with ONNX Runtime Android, on-demand model download on Wi-Fi, paragraph-level synthesis with one-ahead buffering and per-chapter WAV cache.
- Done when: playback continues with the screen off for a full chapter and survives a phone call.

### Later (not scheduled)
- Trackers: register Dokusho OAuth clients on AniList and MyAnimeList with `dokusho://anilist-auth` and `dokusho://myanimelist-auth` redirects, put the ids in CI secrets, set `TrackerManager.trackers` back to `listOf(aniList, myAnimeList)`. Remove or replace the Mihon-registered client credentials still embedded for Hikka, Bangumi, Kitsu, Shikimori and Suwayomi.
- MangaUpdates tracker (covers LOTM and Reverend Insanity as Novel, not Shadow Slave): needs only a free account, no OAuth registration; enable with `listOf(mangaUpdates)` in `TrackerManager.trackers`. NovelUpdates has no public API.
- Import from a tracker list: read the user's MangaUpdates (or AniList) list, search each title across installed extensions, add matches to the library. Revisit when trackers come back; until then library and progress stay local only.
- Public release: store listing, F-Droid, translations.

### 7. Polish and release
- Strings: English `base` first ("manga" to "novel"); other locales fall back where untranslated.
- Local import: EPUB and TXT folders into `LocalSource`.
- Release workflow on `v*` tags, keystore secrets, GitHub Releases. F-Droid later.

## Risks

| Risk | Mitigation |
|------|------------|
| Aggregators behind Cloudflare or JS rendering | Mihon's Cloudflare and WebView interceptors, `TurnstileHelper`, `runWebViewBlocking` |
| Aggregator domains change or die | `baseUrl { mirrors }` in the extension DSL, keep source ids stable |
| Webnovel anti-scraping and account terms | Only the user's own session, modest rate limit, locked chapters never fetched |
| Upstream drift | Keep changes in new files where possible, merge `upstream/main` monthly (`/dokusho-upstream-sync`) |
| 70 locales still say "manga" | English first, accept mixed copy, tackle locales last |
| TTS word skipping on Supertonic | Ship system TTS first; Supertonic optional, whole paragraphs only |
