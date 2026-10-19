---
name: dokusho-feature
description: Build a feature in the Dokusho app the house way - plan the seam with Mihon, new files first, Metro DI, strings, migration, tests, PLAN.md status. Use when Qasim says full feature, add screen, implement phase N, wire the reader, downloader, export or TTS.
---

# Feature

Rules: `CLAUDE.md` always-on, `.claude/rules/architecture.md`, `.claude/rules/novel.md` when
the work touches text, `.claude/rules/development.md` §New features §Testing.

## Do

1. Name the `docs/PLAN.md` phase and mode. PRIVACY if logins, cookies or paid sites are involved.
2. Ponytail: grep Mihon for an existing screen, interactor, worker or preference that already
   does most of it. Say what you reuse.
3. Write the seam plan before code: which **new** files, which Mihon files get a minimal edit
   and the one-line reason for each edit.
4. Build in this order: domain model and interactor → data (repo impl, migration) → service
   or worker → ScreenModel → Compose screen → strings → settings.
5. Metro annotations on every new singleton. Strings in `base/strings.xml`. No `println`.
6. Tests for pure logic; manual device steps listed for UI, reader and TTS.
7. `./gradlew spotlessApply`, then `/dokusho-ship-check`.
8. Update the status line at the top of `docs/PLAN.md` and tick the phase bullet.

Report: phase · mode · new files · Mihon files touched and why · tests · device steps · next step.

## Do not

- Refactor or reformat Mihon code you did not need to touch
- Rename `Manga*` classes
- Add a dependency without saying why it beats what the version catalog already has
- Put chapter content in the backup
- Fetch, display or export chapters the user's account cannot read
