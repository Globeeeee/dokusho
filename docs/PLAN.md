# Dokusho - plan

Web novel reader for Android, built on Mihon. Reads novels from public sites through
installable extensions, downloads them for offline reading, exports EPUB and PDF, and reads
aloud in the background.

Status (2026-10-22): phases 0 to 3 done and confirmed on device. Phase 4: 27 extensions built
(AO3, SpaceBattles, Sufficient Velocity, FanFiction.net, FictionPress and Webnovel added for
v0.1.7, device checks pending). Phase 5 export built for v0.1.7. Phase 6 complete in code
(long press to listen, sentence audio cache, engine fallback); v0.1.8 committed but not tagged.
Phase 7: all items built except Cloudflare edge cases; not yet compiled or checked on device.
Next: build and device checks, then decide manga support (see Later).

| Phase                      | State                                                                     |
| -------------------------- | ------------------------------------------------------------------------- |
| 0. Build green and branded | done                                                                      |
| 1. Text source contract    | done, confirmed on device                                                 |
| 2. Reader                  | done, confirmed on device (v0.1.5)                                        |
| 3. Offline downloads       | done, confirmed on device (v0.1.5); stuck-download fix in v0.1.7          |
| 4. First extensions        | 27 built; AO3, forums, FanFiction.net, Webnovel new, device check pending |
| 5. Export EPUB and PDF     | built for v0.1.7; device check pending                                    |
| 6. Background TTS          | complete in code; screen-off and phone-call check pending                 |
| 7. Polish and release      | built except Cloudflare edge cases; build and device check pending        |
| 8. Manga next to novels    | planned, decisions open                                                   |

## Decisions taken

| Topic      | Decision                                                                                                                                                                                             | Why                                                                                             |
| ---------- | ---------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- | ----------------------------------------------------------------------------------------------- |
| Platform   | Android only                                                                                                                                                                                         | Mihon-style extension APKs only work there                                                      |
| Language   | Kotlin, fork of Mihon `deeb9b2` (0.21.2)                                                                                                                                                             | Keeps library, browse, updates, download queue, extension installer, backup                     |
| Name, id   | Dokusho (読書, "reading"), app id `app.dokusho`, confirmed                                                                                                                                           | One Japanese word like Mihon; "Shosetsu" and "Yomu" are taken by other readers                  |
| Extensions | Keiyoushi toolchain (convention plugins, `dokusho {}` DSL, KSP, `DokushoSource`) in our own repo, new lib version `2.0` with a text API                                                              | Proven build and publish pipeline; new lib version keeps manga APKs from loading                |
| Reader     | Compose paragraph list, not WebView                                                                                                                                                                  | Paragraph index doubles as progress, TTS highlight and export block model                       |
| Downloads  | `chapter.html` per chapter inside Mihon's existing folder layout                                                                                                                                     | Download queue, cache index and notifier stay as they are                                       |
| Export     | EPUB default, PDF optional; split is a setting, default EPUB whole book and PDF every 100 chapters                                                                                                   | Qasim asked for a changeable default                                                            |
| TTS        | Android system `TextToSpeech` behind a `TtsEngine` interface; Supertonic 3 (ONNX, on-demand 300 to 400 MB download) as a later optional engine                                                       | Free, offline, works today; Supertonic upstream is archived and still skips words on short text |
| Trackers   | Off for now. Personal use, reading and listening first                                                                                                                                               | Qasim has no AniList or MAL account; revisit later                                              |
| Paid sites | Webnovel and WuxiaWorld through the user's own login, locked chapters stay locked                                                                                                                    | No paywall bypass                                                                               |
| Telemetry  | Firebase Crashlytics and Analytics in CI and GitHub release builds, project under Qasim's Google account; FOSS and local builds have none. Config via `GOOGLE_SERVICES_JSON` secret, never committed | Qasim asked for his own Firebase; crash reports matter for a scraping app                       |
| Updater    | On for release builds, reads GitHub releases of `Globeeeee/dokusho`                                                                                                                                  | Free update channel without a store                                                             |
| Releases   | Pushing a `vX.Y.Z` tag builds, signs and publishes APKs; `versionCode` only ever goes up. Process in `RELEASING.md`                                                                                  | One command per release, no manual uploads                                                      |
| Signing    | Two PKCS12 keys, app and extensions, kept only in `~/.dokusho/signing/` and GitHub secrets; one password per key                                                                                     | Separate keys limit the damage of a leak                                                        |
| Secrets    | `docs/SECRETS.md`: never in git, chat or logs; pre-commit guard, gitleaks in CI, agent permission denies                                                                                             | Asked for on 2026-10-19                                                                         |
| Name       | Kept "Dokusho" for now although `dokushoapp/dokusho` (a desktop manga reader) and `dokusho.app` exist                                                                                                | Personal use first; revisit before any public listing                                           |
| JDK        | 21, Android Studio's bundled JBR locally (`.claude/settings.local.json`), Temurin 21 in CI                                                                                                           | Metro Gradle plugin 1.4.5 refuses JDK 17                                                        |

## Repos

| Local folder                 | GitHub                              | Role                                                                                                                                                     |
| ---------------------------- | ----------------------------------- | -------------------------------------------------------------------------------------------------------------------------------------------------------- |
| `dokusho/`                   | Globeeeee/dokusho                   | App. `upstream` remote = mihonapp/mihon. This file is the master plan                                                                                    |
| `dokusho-extensions-source/` | Globeeeee/dokusho-extensions-source | Extension modules, core, compiler, publish scripts (from keiyoushi/extensions-source `314a0d1b`, manga modules removed). Own slice in its `docs/PLAN.md` |
| `dokusho-extensions/`        | Globeeeee/dokusho-extensions        | Published index on branch `repo`: `index.pb`, `index.json`, `repo.json`. APKs on Releases                                                                |

## What Mihon gives us unchanged

Extension discovery, store index parsing (legacy and v2 protobuf), signature and trust
checks, private and Shizuku install · network stack with Cloudflare and WebView interceptors,
rate limiting, DoH · SQLDelight schema for manga, chapter, history, category, track ·
library, browse, search, updates, history, categories UI · library update worker and
new-chapter notifications · download queue, worker, notifier · Metro DI · preferences ·
storage manager · i18n (moko) · backup (metadata only) · CI build and test steps.

## What must change for text

| Subsystem                                                       | Mihon had                                                 | Change                                                                                | Done                                  |
| --------------------------------------------------------------- | --------------------------------------------------------- | ------------------------------------------------------------------------------------- | ------------------------------------- |
| `source-api`                                                    | `Page` is image only                                      | `NovelSource` + `ChapterContent(html, title, locked)`; `HttpNovelSource`; lib 2.0     | ✅                                    |
| `ExtensionLoader`                                               | `SUPPORTED_LIB_VERSIONS = [1.4, 1.6]`, `tachiyomi.*` keys | `[2.0]`, `dokusho.*` keys                                                             | ✅                                    |
| Reader                                                          | Pager, webtoon and WebGPU image viewers                   | `NovelViewer` (Compose), `NovelPageLoader`, text settings, progress = paragraph index | ✅                                    |
| `ChapterCache`                                                  | Page-list JSON + image files                              | Chapter HTML keyed by chapter URL                                                     | ⬜ chapters are fetched on every open |
| Downloader, DownloadProvider, DownloadCache, DownloadPageLoader | Image files or CBZ                                        | `chapter.html` + `meta.json`                                                          | ⬜ phase 3                            |
| `LocalSource`, `core:archive`                                   | EPUB images only                                          | EPUB text spine import; `.txt` and `.html`                                            | ⬜ phase 7                            |
| Export                                                          | CSV library export only                                   | `ExportWorker`, EPUB via `ZipWriter`, PDF via `PdfDocument`                           | ⬜ phase 5                            |
| Media                                                           | None                                                      | `TtsPlaybackService`, MediaSession                                                    | ⬜ phase 6                            |
| Branding                                                        | `app.mihon`, Mihon strings and links                      | `app.dokusho`, strings, icon, workflows, updater                                      | ✅ except 7 help links to mihon.app   |

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
- [x] Package `eu.kanade.tachiyomi` kept on purpose (verified 2026-10-20): extension class names depend on it
- [x] Added beyond the original list: app release key and secrets, release pipeline, v0.1.0 published and its signature checked, `RELEASING.md`, `docs/SECRETS.md`, gitleaks CI, pre-commit guard, README Download and Add extensions sections, inherited upstream tags removed locally (`remote.upstream.tagOpt --no-tags`)

- Done when: debug APK installs, opens, an extension repo URL can be added. **Met 2026-10-19**: v0.1.0 installed on Qasim's phone and the Dokusho extension store added (Royal Road listed).

### 1. Text source contract (2 days)

- [x] `NovelSource` (standalone interface), `ChapterContent(html, title, locked)`, `HttpNovelSource` in `source-api`, proguard keeps
- [x] Loader: feature `dokusho.extension`, `dokusho.*` meta-data, `SUPPORTED_LIB_VERSIONS = [2.0]`
- [x] App calls `getChapterContent` for `NovelSource` (`NovelPageLoader`, shipped with the reader in v0.1.1)
- [x] Extension repo: `:extensions-lib` stub module, lib 2.0 toolchain, `DokushoSource` implements `NovelSource`, novel template in `ext-bootstrap.py` (details in its plan)
- [x] Royal Road built, signed and published to the store index
- [x] Done when: an extension installs from the store and lists in the app. Confirmed by Qasim on device: six extensions install, list and browse (2026-10-20).

### 2. Reader (1 week)

- [x] `NovelPageLoader`: chapter HTML → safelist → blocks, one reader page per paragraph (progress = paragraph index)
- [x] `NovelViewer` (Compose): reading modes Scroll, Pages left to right, Pages right to left, Pages top to bottom; tap zones; volume keys; end-of-chapter next/previous; locked chapters shown as a message
- [x] Text settings sheet (Aa): font size, line spacing, margins, themes paper/sepia/dark/black
- [x] Progress: first visible paragraph into `last_page_read`, position restored on reopen; chapter marked read when its last paragraph is reached (not at 95 percent; the end of a chapter is the clearer signal)
- [x] Next chapter preloads at 60 percent; Next and Previous chapter buttons at the chapter end (own end screen instead of `ChapterTransition`)
- [x] Keep screen on and fullscreen: Mihon's existing reader settings apply to the novel viewer unchanged
- [x] Released in v0.1.1
- [x] v0.1.3: theme **Auto** (default) follows General → Background color, so a black reader stays black; the reader settings dialog's Reading mode tab shows the novel options for novel sources; Settings → Reader has a Novel reader group; Show page number shows progress in scroll mode; scrolling past the end or turning past the last page opens the next chapter
- [x] Font choice: Serif, Sans-serif, Monospace, System default (v0.1.4)
- [x] Images inside chapters: http/https `img` kept, shown inline in scroll mode and one per page in page modes (v0.1.4)
- [x] Chapter text cached in `ChapterCache` (separate key from page lists, locked chapters never cached), so reopening is instant (v0.1.4)
- [x] General tab shows only text options for novel sources (background, page number, fullscreen, cutout, keep screen on); image-only options hidden (v0.1.4)
- [x] Done when: a 3,000-word chapter scrolls smoothly, position restores, next chapter preloads. Confirmed by Qasim on device with v0.1.5 (2026-10-20).

### 3. Offline downloads (3 days)

- [x] Downloader branch for `NovelSource`: fetch content, write `chapter.html` + `meta.json` into the chapter folder; locked or empty chapters fail with a clear error
- [x] Images in the chapter saved next to the text (`image_001.jpg` ...) and the HTML pointed at them, so chapters read fully offline; an image that fails keeps its web address
- [x] Download index recognises the folders (downloaded badge) with no change; the reader opens downloaded chapters from disk and resolves local images
- [x] Delete-after-read works on the folder; download-ahead also counts a novel chapter read from disk
- [x] Done when: downloaded chapters open in airplane mode and show the downloaded badge. Verified by Qasim on a Pixel 9 Pro XL with v0.1.5 (2026-10-20).
- [x] v0.1.7: downloads no longer stick at loading: a failed chapter no longer blocks new ones, the queue waits for an allowed network and starts on its own, a Cloudflare check fails the chapter instead of waiting minutes in the background, retries start from a clean folder
- [ ] Device check: download from Browse, from the library and from Updates, offline then online, Wi-Fi only on mobile data

### 4. First extensions (3 days, then ongoing)

Detail and site list in `dokusho-extensions-source/docs/PLAN.md`.

- [x] Royal Road: published (2.0.1)
- [x] Scribble Hub: built and pushed for publishing (2.0.1)
- [x] Wattpad and WuxiaWorld (official, free chapters; locked ones marked) built and pushed for publishing
- [x] 2026-10-21: 21 extensions built and published after a full audit (shared sanitiser, chapter dates, WuxiaWorld login token for the user's own unlocked chapters); list in the extensions plan
- [x] Archive of Our Own, SpaceBattles and Sufficient Velocity (XenForo theme), FanFiction.net and FictionPress (shared theme) built for v0.1.7; selectors checked live or through the Wayback Machine, device check pending
- [x] Webnovel built: chapter list from the server-rendered catalog page, text from the official content API, locked chapters stay locked, no decryption
- [ ] Webnovel device check with the user's login: do unlocked paid chapters come back readable or encrypted? If encrypted, paid Webnovel chapters cannot be read in Dokusho
- [x] Publish pipeline: extension key and secrets, CI builds, signs and writes `index.pb` and `index.json`; fixed the stub variant and the PKCS12 key password on the way
- [x] `index.pb` loads in the app and lists Royal Road (seen on v0.1.0)

MVP = phases 0 to 4, roughly 3 weeks.

### 5. Export EPUB and PDF (4 days)

- [x] `ExportWorker` (foreground, dataSync, channel `CHANNEL_EXPORT`) with progress notification, cancel action, and Open and Share on the finished files; skipped chapters (locked, not downloaded, failed) listed in the result.
- [x] EPUB 3: `mimetype` stored first with no extra field, `container.xml`, OPF (stable `urn:dokusho:<source>:<url hash>`), `nav.xhtml` and `toc.ncx`, one `chNNNN.xhtml` per chapter from the reader's block parser, CSS, cover from the cover cache or Coil disk cache, images from the download folder or caches. Written with `java.util.zip`, not `ZipWriter`: libarchive adds extra fields that move the `mimetype` bytes off the offset strict readers check.
- [x] PDF: `PdfDocument`, A5, `StaticLayout` pagination, chapter headings, page numbers, images downsampled to the text width.
- [x] UI: novel menu "Export" sheet: format, range (all, downloaded, from-to), split, download missing; defaults from settings (Data and storage, Novel export).
- [x] Settings: "Export novels" lists library novels (search, select one, several or all) and exports them one after another with the same options.
- [x] Missing chapters fetched during export when "download missing" is on, through the source's own client and rate limit; locked chapters never exported.
- [x] Output to `<storage root>/exports/<Title>/` through SAF, files `<Title> - ch0001-0100.epub`.
- [x] Unit tests: EPUB package (mimetype first and stored, OPF manifest and spine, nav, XHTML escaping), range, split and file names.
- [ ] Done when: a 300-chapter novel exports to one EPUB that opens in another reader and to three PDFs. Built, device check pending.

### 6. Background TTS (1 week)

- `TtsEngine` interface; `AndroidTtsEngine` first.
- `TtsPlaybackService`: foreground `mediaPlayback`, `MediaSessionCompat`, MediaStyle notification (play, pause, previous and next paragraph, next chapter), headset buttons, audio focus, sleep timer, partial wake lock.
- Queue = blocks of the current chapter; auto-advance via `GetNextChapters`; progress and history updated as it reads; next chapter prefetched.
- Reader: Listen button, current paragraph highlighted, tap a paragraph to start there.
- Later: `SupertonicEngine` with ONNX Runtime Android, on-demand model download on Wi-Fi, paragraph-level synthesis with one-ahead buffering and per-chapter WAV cache.
- Done when: playback continues with the screen off for a full chapter and survives a phone call.

- [x] Built in v0.1.6: `TtsEngine` with Android TTS and Supertonic (on-demand model download), `TtsPlaybackService` (mediaPlayback, MediaSession, notification controls, audio focus, wake lock, sleep timer), auto-advance to the next chapter, word highlight and auto-scroll, voice picker
- [x] v0.1.7: novel cover in the media player, notification and lock screen (Settings, Reader, Show novel cover)
- [x] v0.1.8: Supertonic plays on one long-lived audio track with read-ahead (no gap per sentence), loads with basic graph optimisation and more threads, reads "..." as a pause and skips wordless clips, joins very short sentences; highlight follows the frame actually heard; scroll mode keeps the spoken sentence centred, page modes turn to its page; highlight colour choice; chapters finished by the voice stay marked read; notification sounds duck the voice instead of restarting the sentence
- [x] v0.1.8: page turn effect for the left to right and right to left page modes
- [x] Sentence audio cache on disk (`TtsClipCache`, 16-bit PCM in the app cache, 300 MB, least recently played dropped first): Supertonic always, system engines with **Save spoken audio** (they render to WAV and Dokusho plays it through the same `ClipTtsEngine` player)
- [x] Engine status line (ready, no voice for the language, cloud only, failed to start), **Install voices** shortcut, **Play sample**, in the listen sheet and Settings, Reader
- [x] A system engine that fails to start or errors mid-chapter falls back to the device default with a message; paid engines (eReader Prestigio, subscription cloud voices) hidden
- [x] Crash fixed: changing a setting while Supertonic played released its audio track under the playback thread (`IllegalStateException` in `getPlaybackHeadPosition`, from the device crash log)
- [x] Long press a paragraph to read aloud from it (seeks when the voice is already in that chapter), in scroll and page modes
- [x] Fixes: earbud play and pause with system engines (Dokusho plays silence while the engine speaks, so Android routes media buttons to it); engine and pitch changes applied once the slider settles, not on every step; a system engine cancelled while starting is no longer kept as a dead engine; voice lists from third-party engines can no longer crash settings
- [ ] Device check: Google "Speech Recognition and Synthesis" reads aloud live and with Save spoken audio; earbud play and pause with each engine
- [ ] Done-when check on device: a full chapter with the screen off, and a phone call in the middle

### 8. Manga next to novels (planned, not started; about 2 weeks)

Goal: Mihon's manga, from Keiyoushi extensions, works in Dokusho as it does in Mihon, while novels
and manga never mix in Library, Browse, Extensions, Updates, History or Migrate. Everything stays on
the device as in Mihon: library, progress, history, categories and downloads in the local database
and storage folder, no account, no Dokusho server.

Repo for manga: `https://github.com/keiyoushi/extensions/raw/repo/index.pb` (checked 2026-10-22:
reachable, gzipped protobuf index, about 110 KB, a format `ExtensionStoreService` already reads).

Design:

- **Kind**: every entry is a Novel or a Manga. A source is a novel source when it implements
  `NovelSource` (lib 2.0 extensions, Local text chapters), otherwise manga. Stored per entry in a new
  `manga.content_kind` column (`20.sqm`, default novel for existing rows), set on insert from the source,
  carried in backups as a new `@ProtoNumber` field so a restore without the extension still sorts right.
- **Extension loader**: accept both features side by side: `dokusho.extension` with lib 2.0 (novels)
  and Mihon's `tachiyomi.extension` with lib 1.4 to 1.6 (manga), each with its own meta-data keys.
  Signature trust and the store's `signingKey` check stay as they are; a Keiyoushi extension is
  trusted through the Keiyoushi repo's key. This reverses the "new lib version keeps manga APKs from
  loading" decision once approved.
- **Repos**: Extension repos lists each repo with its kind, from the lib versions in its index.
  The default novel repo stays; the Keiyoushi repo is added by the user, never preinstalled.
- **One Novels | Manga switch** at the top of Library, Updates, History, Browse (Sources,
  Extensions, Migrate), remembered per screen. Categories, filters and sort are per kind.
  Search across sources searches only the current kind. Library update and auto-download run for
  both kinds; their notifications name the kind.
- **Reader**: manga opens in Mihon's image viewers (pager, webtoon) with their reading modes,
  crop, color filters and settings; novels keep the novel viewer. Listen, Export and text settings
  stay on novels. Manga downloads use Mihon's image path (`saveChaptersAsCBZ`, `splitTallImages`,
  concurrent pages), so those settings come back under a Manga heading.
- **Wording**: shared screens say novel or manga from the switch (strings with a kind variant);
  manga-only Mihon strings come back for manga screens.
- **Local source**: already splits by chapter: text files and text EPUBs as novels, CBZ, folders and
  picture EPUBs as manga. With the kind column, a Local series is filed by its first chapter.

Steps:

- [ ] Decide the open questions below
- [ ] `content_kind` column, migration `20.sqm`, domain model, mapper, backup field, `verifySqlDelightMigration`
- [ ] Loader: both features and lib ranges; extension model and store entries carry the kind
- [ ] Novels | Manga switch component; Library, Updates, History, Browse, Extensions, Migrate filtered by it
- [ ] Reader, downloads and settings: manga paths re-enabled under a Manga heading, novel-only actions gated
- [ ] Strings with novel and manga variants; guides: manga section, adding the Keiyoushi repo
- [ ] Unit tests: kind from source, migration default, backup round trip with kind, loader accepts both features
- [ ] Done when: with the Keiyoushi repo added, a manga extension installs from the Manga side, a manga
  reads, downloads and restores from backup, and the Novels side of every screen shows no manga

Open decisions (Qasim owns them):

| Decision | Recommendation | Counter-argument |
| -------- | -------------- | ---------------- |
| How to separate | One Novels, Manga switch per screen | A bottom tab per kind is clearer at a glance, but doubles navigation and the upstream merge surface |
| Where the kind lives | `content_kind` column per entry | Working it out from the source needs no migration, but entries from an uninstalled extension land on the wrong side |
| Order | After phases 6 and 7 are built and checked on device | Starting now saves a round of testing, but stacks the biggest change on code never compiled |
| Telemetry | Keep as Mihon: crash reports and analytics in GitHub builds, both switchable in Settings, none in the FOSS build | "Everything local" could mean both off by default, so nothing leaves the phone unless turned on |

#### What leaves the phone today (checked 2026-10-22)

Library, progress, history, categories, downloads, exports, TTS audio and backups are all local, as in
Mihon. Network use is limited to: sources and their WebView, extension repos, the app update check
(GitHub Releases), the one-time Supertonic model download, and Firebase crash reports and analytics in
GitHub release builds (on by default, switchable under Settings, absent from the FOSS build).

### Later (not scheduled)

- Trackers: register Dokusho OAuth clients on AniList and MyAnimeList with `dokusho://anilist-auth` and `dokusho://myanimelist-auth` redirects, put the ids in CI secrets, set `TrackerManager.trackers` back to `listOf(aniList, myAnimeList)`. Remove or replace the Mihon-registered client credentials still embedded for Hikka, Bangumi, Kitsu, Shikimori and Suwayomi.
- MangaUpdates tracker (covers LOTM and Reverend Insanity as Novel, not Shadow Slave): needs only a free account, no OAuth registration; enable with `listOf(mangaUpdates)` in `TrackerManager.trackers`. NovelUpdates has no public API.
- Import from a tracker list: read the user's MangaUpdates (or AniList) list, search each title across installed extensions, add matches to the library. Revisit when trackers come back; until then library and progress stay local only.
- Public release: store listing, F-Droid, translations. Settle the name first (see Decisions).

- [x] Help links point to `docs/guides/` instead of mihon.app, including Local source help (v0.1.6); guides for local source, reader and TTS, downloads, backups, library and FAQ
- [x] VPN setting (Settings → VPN): SOCKS5 or HTTP proxy for Dokusho's OkHttp and WebView traffic, connection check. A built-in Dokusho server is an open decision.

### 7. Polish and release

- Strings: English `base` first ("manga" to "novel"); other locales fall back where untranslated.
- Local import: EPUB and TXT folders into `LocalSource`.

Open items found while writing the help guides (2026-10-21):

- [x] Local source opens text: TXT and HTML files as one chapter each, a text EPUB as one chapter per section (images unpacked to the cache), in the novel reader with Listen and export; picture EPUBs, archives and folders stay in the image reader
- [x] Novel reader honours **Tap zones** and **Invert tap zones** (pages use the pager setting, scroll the webtoon one), also offered in the novel Reading mode tab
- [x] Hidden download settings that do nothing for novels: Save as CBZ, Split tall images, Concurrent page downloads
- [x] Listen from a tapped paragraph: long press, done in phase 6
- [x] Wording: every English "entry" or "entries" says novel (37 strings); library CSV export named `dokusho_library.csv`
- [x] Migration strings (`migrationConfigScreen.*`, `migrationListScreen.*`) already in base `strings.xml` from Mihon (dot names, underscores in code)
- [x] Backup screen hides the empty **Tracking** option while trackers are off
- [x] Supertonic reads the novel in the source's language (all 31 it supports), detects the script when the source covers several, and hands a language it can't read (Chinese, Thai) to the system voice
- [x] Removed Mihon's unused donation popup and Support us screen
- [ ] Cloudflare: form posts on sites that reject OkHttp after the WebView passed, and checks that need a tap while the app is in the background, still fail

- [x] Release workflow on `v*` tags, keystore secrets, GitHub Releases (done in phase 0; v0.1.0 and v0.1.1 published). F-Droid later.

## Risks

| Risk                                     | Mitigation                                                                                         |
| ---------------------------------------- | -------------------------------------------------------------------------------------------------- |
| Sites behind Cloudflare or JS rendering  | Mihon's Cloudflare and WebView interceptors, `TurnstileHelper`, `runWebViewBlocking`               |
| Official domain hops                     | `baseUrl { mirrors }` in the extension DSL for legitimate host changes, keep source ids stable     |
| Webnovel anti-scraping and account terms | Only the user's own session, modest rate limit, locked chapters never fetched                      |
| Upstream drift                           | Keep changes in new files where possible, merge `upstream/main` monthly (`/dokusho-upstream-sync`) |
| 70 locales still say "manga"             | English first, accept mixed copy, tackle locales last                                              |
| TTS word skipping on Supertonic          | Ship system TTS first; Supertonic optional, whole paragraphs only                                  |
