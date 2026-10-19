---
name: dokusho-ship-check
description: Scoped Gradle gate for the Dokusho app - spotless, unit tests, migration check, debug build, device smoke. Use when finishing FEATURE, BUGFIX, DATA or SOURCE-API work, or when Qasim says ship-check, verify, build it, ready to merge.
---

# Ship check

Scoped to what changed. Gradle is slow; never run the whole gate for a one-string edit.

## Do

1. `git status --porcelain` plus files edited this session.
2. Run only the matching rows, in order:

| Touched | Command |
|---------|---------|
| Any `.kt` or `.kts` | `./gradlew spotlessCheck` (on failure `spotlessApply`, then re-check) |
| `domain/`, `data/`, `core/`, `source-api/` logic | `./gradlew :<module>:testDebugUnitTest` |
| `.sq` or `.sqm` | `./gradlew verifySqlDelightMigration` |
| `app/` or any module the app depends on | `./gradlew :app:assembleDebug -Pdist=local` |
| Manifest, services, permissions | assembleDebug, then `adb install -r` and launch |
| Reader, downloads, TTS, export | device smoke: open a chapter, airplane mode, screen off, back to app |
| Before a push, non-QUICK | full gate from `CLAUDE.md` |
| Always | `grep -rn "println(" --include=*.kt app domain data core source-api` must be clean |

3. Fix failures you introduced. Pre-existing lint debt is not yours unless it blocks.
4. Report: what ran · pass or fail with the failing task name · leftover risk Gradle cannot see
   (OEM battery killers, paywall edge paths, large text layouts).

## Do not

- Run `:app:lintDebug` or `assembleRelease` unless asked
- Edit `gradle/libs.versions.toml` to make a build pass
- Delete or skip a failing test
- Paste keystore paths or passwords into the report
