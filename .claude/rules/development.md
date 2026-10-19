# Development pack

Read only the sections your mode needs.

| Mode | Sections |
|------|----------|
| BUGFIX | Debugging |
| FEATURE | Evaluate · New features · Testing |
| DATA | Migrations |
| PRIVACY | Security |
| TESTS | Testing |

## Evaluate (FEATURE)

Ponytail first: does Mihon already have it? Grep `app/`, `domain/`, `presentation-core/` for
the noun. Offer two approaches with a one-line pick. Prefer a new file over editing a 1,000-line
Mihon file. State which Mihon files you will touch and why each touch is unavoidable.

## New features (FEATURE)

Before code: three to five sentences of flow · screen → ScreenModel → interactor → repo or
source · strings you will add to `base/strings.xml` · migrations if any · which `PLAN.md`
phase this belongs to.

Checklist: Compose screen in `eu.kanade.presentation.<feature>` · ScreenModel in
`eu.kanade.tachiyomi.ui.<feature>` · interactor in `domain` · repository impl in `data` with
Metro annotations · strings · settings in the matching `*Preferences` class · unit tests ·
`spotlessApply` · `PLAN.md` status line updated.

## Migrations (DATA)

1. Edit the `.sq` schema file under `data/src/main/sqldelight/tachiyomi/data/`.
2. Add `migrations/<next>.sqm` with the `ALTER` statements; never edit an existing `.sqm`.
3. Update the domain model, mapper and repository.
4. `./gradlew verifySqlDelightMigration` must pass.
5. Backup: add a `@ProtoNumber` field only if the new column must survive restore.

## Security (PRIVACY)

- Sites with accounts (Webnovel, WuxiaWorld): login happens in the app WebView, cookies stay
  in the shared CookieJar, the extension sends them with its requests. The app never stores
  passwords. Locked chapters show a locked state and are never fetched by another route.
- No chapter text, URLs with session params, cookies or tokens in `logcat`, crash reports or
  notifications. Export files contain only chapters the user could read in the app.
- Downloaded content stays under the user's chosen storage root; nothing in world-readable dirs.
- Error-path tests for login expiry (401 → locked state, not a crash).

## Testing (TESTS / FEATURE)

| What | How |
|------|-----|
| Pure logic (block parser, EPUB writer, export splitter) | JVM unit tests in the owning module, `./gradlew :<module>:testDebugUnitTest` |
| Interactors | unit tests with fake repositories |
| Compose screens | preview functions; screenshot tests are not set up |
| Reader and TTS | manual on device: install, airplane mode, screen off, phone call |
| Migrations | `verifySqlDelightMigration` |

Put tests next to Mihon's: `app/src/test/`, `domain/src/test/`, `core/common/src/test/`.

## Debugging (BUGFIX)

One-sentence root cause before editing. Reproduce → `adb logcat -s Dokusho:* AndroidRuntime:E`
→ trace screen → ScreenModel → interactor → repo or source → fix the layer that is wrong →
verify on device.

| Symptom | Look at |
|---------|---------|
| Extension not listed | `ExtensionLoader` feature and lib-version checks, signature vs store `signingKey` |
| Chapter opens blank | `NovelChapterLoader` source resolution, `ChapterCache` key, sanitizer stripped everything |
| Progress lost | `last_page_read` write debounce, `onPause` path in `ReaderViewModel` |
| Download "failed" | `Downloader` success check expects `chapter.html`, `DownloadCache` not recognising the dir |
| TTS stops with screen off | foreground service type, wake lock, audio focus loss, Doze on the OEM |
| Build fails after upstream merge | version catalog conflicts, `gradle/build-logic` plugin changes |

Grep every caller of the function you touch. One shared guard beats patching N call sites.
Stop without a reproduction.
