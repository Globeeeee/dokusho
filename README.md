# Dokusho

**Dokusho** (読書, "reading") is a free, open source web novel reader for Android. It is built
on the [Mihon](https://github.com/mihonapp/mihon) code base, which was made for manga; Dokusho
turns it into a reader for text novels.

Dokusho ships no content. It reads novels from the sites you already use, through extensions
you install. Sites that need an account use your own login, and chapters your account cannot
read stay locked.

## What it will do

| Area | Dokusho |
|------|---------|
| Reading | Paragraph-based reader with font, size, line height, margins and paper, sepia, dark and black themes. Progress saved per paragraph |
| Library | Categories, updates, history, tracking with AniList and MyAnimeList |
| Sources | Installable extensions from an extension repo, like Mihon. See the repos below |
| Offline | Download chapters; read in airplane mode |
| Export | EPUB (default) or PDF, whole novel or by chapter range, split size is a setting |
| Listen | Text-to-speech that keeps playing with the screen off, with lock-screen controls. Android system voices first, better on-device voices planned |

## Status

Early development. Phase 0 (rebrand, icon, CI) is nearly done; nothing reads novels yet. Phases, decisions and risks: [docs/PLAN.md](docs/PLAN.md).

## The three repos

| Repo | Role |
|------|------|
| [dokusho](https://github.com/Globeeeee/dokusho) | the app (this repo) |
| [dokusho-extensions-source](https://github.com/Globeeeee/dokusho-extensions-source) | source code of the novel extensions |
| [dokusho-extensions](https://github.com/Globeeeee/dokusho-extensions) | the published extension index that the app reads |

Extension repo URL to add in the app once extensions exist:

```
https://github.com/Globeeeee/dokusho-extensions/raw/repo/index.pb
```

## Build from source

A fresh clone builds a debug APK with no secrets and no Firebase. Release signing and crash
reporting are optional and described below.

### 1. Requirements

| Tool | Version | Notes |
|------|---------|-------|
| JDK | **21** | JDK 17 fails (the Metro Gradle plugin needs 21). Android Studio's bundled JBR works |
| Android SDK | platform 37.2, build-tools 36+ | Gradle downloads missing platforms itself once licenses are accepted |
| Git | any | SSH remote recommended |
| Device or emulator | Android 8.0+ | for installing the APK |

macOS with Android Studio installed:

```bash
export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"
export ANDROID_HOME="$HOME/Library/Android/sdk"
yes | "$ANDROID_HOME/cmdline-tools/latest/bin/sdkmanager" --licenses
```

Linux: install Temurin 21 and the SDK command-line tools, then set the same two variables.
Instead of `ANDROID_HOME` you can write `sdk.dir=/path/to/sdk` into `local.properties`
(gitignored).

### 2. Clone and build

```bash
git clone git@github.com:Globeeeee/dokusho.git
cd dokusho
git remote add upstream https://github.com/mihonapp/mihon.git   # for /dokusho-upstream-sync
./gradlew :app:assembleDebug -Pdist=local
```

The first build downloads Gradle and dependencies, roughly 10 minutes. Output:
`app/build/outputs/apk/debug/app-<abi>-debug.apk` (package `app.dokusho.dev`, label Dokusho).

### 3. Install

```bash
adb install -r app/build/outputs/apk/debug/app-arm64-v8a-debug.apk   # most phones
# app-universal-debug.apk works everywhere but is larger
```

### 4. Check before a pull request

```bash
./gradlew spotlessCheck :app:testDebugUnitTest verifySqlDelightMigration :app:assembleDebug -Pdist=local
```

`./gradlew spotlessApply` fixes formatting.

### Optional: crash reporting (Firebase)

Only the maintainer's builds ship Firebase. To build with it locally, put the project's
`google-services.json` at `app/google-services.json` (gitignored, never commit it) and run:

```bash
./gradlew :app:assembleRelease -Pdist=ci
```

Without the file, use `-Pinclude-telemetry=false` or stay on `-Pdist=local`, which never
includes it. CI reads it from the `GOOGLE_SERVICES_JSON` secret.

### Optional: signed release build

Copy `keystore.properties.example` to `keystore.properties` (gitignored), point it at your
own keystore kept outside the repo, then `./gradlew :app:assembleRelease -Pdist=local`.
Tagged releases are signed in CI from repository secrets; see [docs/SECRETS.md](docs/SECRETS.md).

### Build variants

| Command | Package | Notes |
|---------|---------|-------|
| `assembleDebug -Pdist=local` | `app.dokusho.dev` | everyday development, green icon |
| `assembleRelease -Pdist=local` | `app.dokusho` | minified, signed with the debug key unless `keystore.properties` exists |
| `assembleFoss -Pdist=foss` | `app.dokusho.foss` | no Firebase, no updater |

### Troubleshooting

| Error | Fix |
|-------|-----|
| `Dependency requires at least JVM runtime version 21` | set `JAVA_HOME` to a JDK 21 |
| `SDK location not found` | set `ANDROID_HOME` or `sdk.dir` in `local.properties` |
| `File google-services.json is missing` | you asked for telemetry; add the file or pass `-Pinclude-telemetry=false` |
| `Failed to install the following SDK components ... licences` | run the `sdkmanager --licenses` line above |
| spotless failures | `./gradlew spotlessApply` |

### Working with Claude Code

Rules, skills and hooks live in `CLAUDE.md` and `.claude/`. Copy
`.claude/settings.local.json.example` to `.claude/settings.local.json` (gitignored) and set
your JDK 21 path there so agent-run Gradle commands use it.

## Project layout

```text
app/                 UI, reader, downloads, extension manager, workers
source-api/          the extension contract
core/, data/, domain/  network, database (SQLDelight), models and interactors
i18n/                strings
docs/PLAN.md         scope, phases, decisions
```

## Contributing

Issues and pull requests are welcome once phase 1 lands. Keep changes small, keep Mihon's
code where it works, and never add a way around a paywall or a login.

## Credits

Dokusho is a fork of Mihon, itself a fork of Tachiyomi. Extension tooling comes from
[Keiyoushi](https://github.com/keiyoushi/extensions-source). Thanks to everyone who built them.

## License

Apache License 2.0, see [LICENSE](LICENSE).
