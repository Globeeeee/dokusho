# Project pack

Read for NATIVE, UPSTREAM, or when you need build commands and conventions.

## Identity

| | |
|--|--|
| App id | `app.dokusho` (`app/build.gradle.kts` `applicationId`), build-type suffixes `.dev` (debug), `.foss`, `.debug` (nightly), `.benchmark` |
| Namespace | `eu.kanade.tachiyomi`, unchanged on purpose: extensions-lib class names depend on it |
| Name | Dokusho. Strings in `i18n/.../base/strings.xml` (`app_name`) |
| Repos | app `Globeeeee/dokusho` · extensions `Globeeeee/dokusho-extensions-source` · index `Globeeeee/dokusho-extensions` branch `repo` |
| Extension repo URL | `https://github.com/Globeeeee/dokusho-extensions/raw/repo/index.pb` |

## Build

| Command | Use |
|---------|-----|
| `./gradlew :app:assembleDebug -Pdist=local` | debug APK, universal + ABI splits in `app/build/outputs/apk/debug/` |
| `./gradlew :app:assembleFoss -Pdist=foss` | no Firebase, no updater |
| `./gradlew spotlessApply` / `spotlessCheck` | ktlint formatting, CI fails on check |
| `./gradlew :app:testDebugUnitTest` | unit tests |
| `./gradlew verifySqlDelightMigration` | schema vs migrations |
| `./gradlew :app:lintDebug` | Android lint, slow, when asked |
| `adb install -r app/build/outputs/apk/debug/app-universal-debug.apk` | device install |

`-Pdist` values: `local`, `ci`, `github`, `foss` (`gradle/build-logic/.../BuildConfig.kt`).
JDK: CI uses 21 (`.github/.java-version`); local 17 works until proven otherwise.
Signing: `keystore.properties` at repo root (gitignored) or env when `DOKUSHO_GITHUB_RELEASE=true`.

## Native

- Permissions and services in `app/src/main/AndroidManifest.xml`. Foreground types in use:
  `dataSync` (WorkManager), `shortService` (extension install). TTS adds `mediaPlayback`.
- New notification channel → `Notifications.kt` constant + `createChannels`.
- Icon: `app/src/main/res/mipmap-*`, adaptive. Replace Mihon's before any release.
- CI: `.github/workflows/build.yml` (spotless, unit tests, migration check, release build with
  `-Pdist=ci`) and `release.yml` (tags `v*`, guard must read `Globeeeee/dokusho`).

## Upstream

- `upstream` remote = `mihonapp/mihon`. Fork point `deeb9b2` (0.21.2, 2026-10-14).
- Monthly: `git fetch upstream && git merge upstream/main`, resolve, full gate, smoke on device.
  Skill `/dokusho-upstream-sync` walks it.
- To keep merges cheap: new behaviour in new files, Mihon files touched only at the seam,
  no reformatting of untouched Mihon code, no renames of Mihon classes before phase 7.

## MCP

`.mcp.json` wires `context7` (library docs). Each server costs tokens every turn; disable it
with `/mcp` for QUICK sessions. GitHub goes through the `gh` CLI, no MCP needed.
