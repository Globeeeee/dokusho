# Dokusho - web novel reader for Android (Mihon fork, Kotlin)

The developer is **Qasim**. Open each reply with his name once, no more.
Scope and phases live in `docs/PLAN.md`. Read it before any FEATURE work.

## How to use these rules

1. Read the prompt, pick **one mode** from the table. `/dokusho-route` does it for you.
2. Apply **Always-on** below on every code change. No file reads needed.
3. Read **only** the packs that mode lists, from `.claude/rules/`. Never read them all.
4. Inside a pack, read only the sections the task needs.
5. Non-trivial work: open with `Mode: FEATURE - ...`.

Priority when signals collide: PRIVACY > SOURCE-API > FEATURE > BUGFIX > QUICK. The user
saying `ponytail only` / `full feature` / `quick` overrides the table.

| Mode | Prompt signals | Packs to read |
|------|----------------|---------------|
| QUICK | typo, one string, rename, color, "quick fix" | none |
| EXPLAIN | how/what/why, explain, review, rate | none, read the code |
| BUGFIX | crash, broken, wrong UI, flaky test, ANR | `development.md` §Debugging |
| FEATURE | new screen, flow, setting, worker | `architecture.md` · `development.md` §Evaluate §New features §Testing |
| SOURCE-API | `source-api`, `NovelSource`, extension loader, lib version, `Page` | `novel.md` · `architecture.md` §Extensions |
| READER | reader, viewer, typography, progress, TTS | `novel.md` §Reader §TTS · `architecture.md` §Reader |
| DATA | SQLDelight, migration, repository, backup | `architecture.md` §Data · `development.md` §Migrations |
| PRIVACY | login, cookies, tokens, Webnovel, WuxiaWorld, logs | mode packs + `development.md` §Security |
| UPSTREAM | merge upstream, Mihon update, conflict | `project.md` §Upstream, skill `/dokusho-upstream-sync` |
| NATIVE | manifest, permissions, foreground service, signing, build types | `project.md` §Build |
| TESTS | test, coverage | `development.md` §Testing |
| DOCS | README, rules, plan | none |

## Always-on (every code change)

- Commits: author is Qasim only. Never add `Co-Authored-By`, "Generated with" or any AI attribution trailer to commits or PRs.

**Ponytail ladder** - stop at the first rung that holds:
YAGNI → reuse what Mihon already has (grep `app/`, `domain/`, `data/`, `presentation-core/`) →
Kotlin stdlib, AndroidX, Compose → a dependency already in `gradle/libs.versions.toml` →
one line → only then minimum new code.

Smallest correct change. No drive-by refactors of Mihon code. Keep fork changes in **new
files** where possible (new viewer, new loader, new service) and touch Mihon files only at
the seam, so `upstream/main` merges stay cheap. Deletion beats addition. Intentional
shortcut gets `// ponytail: <ceiling>`. No `Note:` / `This ensures...` comments.

**Never cut:** paywall and login boundaries (user's own session only, locked chapters stay
locked) · reading progress durability · migrations · accessibility · responsive layout
(phones, tablets, large text) · anything asked for.

| Rule | Do |
|------|-----|
| Naming | Mihon classes still say `Manga`, `SManga`, `MangaScreen`. Keep those names in existing code. New code and all user copy say novel, chapter, source. |
| Layers | Compose screen → ScreenModel (Voyager) → domain interactor → repository (`data/`) → SQLDelight or source. UI never calls a repository or a source directly. |
| DI | Metro: `@Inject`, `@SingleIn(AppScope)`, `@ContributesBinding`. Extensions only see what `MetroInjektRegistrar` exposes. No service locators in new code. |
| Strings | `i18n/src/commonMain/moko-resources/base/strings.xml` → `MR.strings.*`. English first, never hardcode copy in Compose. |
| Logging | `logcat { }` only, never `println` or `Log.*`. Never log cookies, tokens, account emails, chapter text or URLs with session params. |
| Secrets | Never in source, commits, chat, logs or tool output. Local copies only in `~/.dokusho/signing/`; CI uses GitHub secrets; repos hold `.env.example` with names only. Pass values from files (`--body "$(cat f)"`), never as literals. Never read `.env`, keystores or `~/.dokusho` into context. Policy: `docs/SECRETS.md` in the app repo. |
| Generated | Never hand-edit `build/`, SQLDelight generated classes, Metro generated code. |
| Data | New columns = new `data/src/main/sqldelight/tachiyomi/migrations/<n>.sqm` + schema file. CI runs `verifySqlDelightMigration`. |

## Map

| Area | Path |
|------|------|
| App entry, DI graph | `app/src/main/java/eu/kanade/tachiyomi/App.kt` · `app/src/main/java/mihon/app/di/AppGraph.kt` |
| Extension contract | `source-api/src/main/kotlin/eu/kanade/tachiyomi/source/` (`Source`, `online/HttpSource`, `model/`) |
| Extension loading | `app/src/main/java/eu/kanade/tachiyomi/extension/` (`util/ExtensionLoader.kt` holds lib versions and meta keys) |
| Extension store index | `data/src/main/java/mihon/data/extension/service/ExtensionStoreService.kt` |
| Reader | `app/src/main/java/eu/kanade/tachiyomi/ui/reader/` (`ReaderActivity`, `ReaderViewModel`, `loader/`, `viewer/`) |
| Downloads | `app/src/main/java/eu/kanade/tachiyomi/data/download/` |
| Chapter cache | `app/src/main/java/eu/kanade/tachiyomi/data/cache/ChapterCache.kt` |
| Database | `data/src/main/sqldelight/tachiyomi/` (`data/*.sq`, `migrations/`, `view/`) |
| Domain | `domain/src/main/java/tachiyomi/domain/<feature>/{model,repository,interactor}` |
| Workers, notifications | `app/src/main/java/eu/kanade/tachiyomi/data/{library,backup,download}/*Worker.kt` · `data/notification/Notifications.kt` |
| Network | `core/common/src/main/kotlin/eu/kanade/tachiyomi/network/` |
| Strings | `i18n/src/commonMain/moko-resources/base/strings.xml` |
| Build config | `app/build.gradle.kts` · `gradle/libs.versions.toml` · `gradle/mihon.versions.toml` · `gradle/build-logic/` |

## Verify before push

Prefer `/dokusho-ship-check` (scoped). Full gate:
`./gradlew spotlessCheck :app:testDebugUnitTest verifySqlDelightMigration :app:assembleDebug -Pdist=local`.
Install and smoke on a device with `adb install -r app/build/outputs/apk/debug/*universal*.apk`.

## Economy

Delegate wide searches to the `scout` agent. Read the smallest slice (`sed -n`, `grep -n`),
never whole 1,000-line files. Gradle output is capped; read the tail. Use `context7` for
Compose, media3, WorkManager or SQLDelight API questions before guessing.
