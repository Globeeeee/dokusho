# Dokusho

Free, open source web novel reader for Android, built on [Mihon](https://github.com/mihonapp/mihon).

- Read novels from the sites you already use, through installable extensions
- Download chapters for offline reading
- Export to EPUB or PDF, whole book or by chapter range
- Listen with text-to-speech that keeps playing with the screen off

Dokusho ships no content. Sources that need an account use your own login, and chapters
your account cannot read stay locked.

## Status

Early development. See [docs/PLAN.md](docs/PLAN.md) for phases.

## Extensions

Extension source: [dokusho-extensions-source](https://github.com/Globeeeee/dokusho-extensions-source).
Published index (add in Settings → Browse → Extension repos):

```
https://github.com/Globeeeee/dokusho-extensions/raw/repo/index.pb
```

## Build

```bash
./gradlew :app:assembleDebug -Pdist=local
adb install -r app/build/outputs/apk/debug/app-universal-debug.apk
```

Requires JDK 17 or 21 and the Android SDK. Full gate:

```bash
./gradlew spotlessCheck :app:testDebugUnitTest verifySqlDelightMigration :app:assembleDebug -Pdist=local
```

## Credits

Dokusho is a fork of Mihon, itself a fork of Tachiyomi. Extension tooling comes from
[Keiyoushi](https://github.com/keiyoushi/extensions-source). Thank you to everyone who built them.

## License

Apache License 2.0, see [LICENSE](LICENSE).
