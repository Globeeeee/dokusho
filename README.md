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

Early development, nothing to install yet. The build is being brought up and branded
(phase 0). Phases, decisions and risks: [docs/PLAN.md](docs/PLAN.md).

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

Requires JDK 21 and the Android SDK (minimum Android 8.0 on device).

```bash
./gradlew :app:assembleDebug -Pdist=local
adb install -r app/build/outputs/apk/debug/app-universal-debug.apk
```

Full check before a pull request:

```bash
./gradlew spotlessCheck :app:testDebugUnitTest verifySqlDelightMigration :app:assembleDebug -Pdist=local
```

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
