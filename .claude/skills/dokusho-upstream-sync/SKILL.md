---
name: dokusho-upstream-sync
description: Merge mihonapp/mihon upstream into Dokusho safely - fetch, review what changed in the files we touch, merge, resolve, full gate, device smoke. Use when Qasim says sync upstream, merge Mihon, update from upstream, or a Mihon release came out.
---

# Upstream sync

Fork point `deeb9b2` (0.21.2). `upstream` remote = `mihonapp/mihon`.

## Do

1. `git status` must be clean. Work on a branch `upstream-sync-<date>`.
2. `git fetch upstream` then `git log --oneline HEAD..upstream/main | wc -l` and
   `git diff --stat HEAD...upstream/main -- <paths we touch>` where the paths are:
   `source-api/`, `app/src/main/java/eu/kanade/tachiyomi/extension/util/ExtensionLoader.kt`,
   `app/src/main/java/eu/kanade/tachiyomi/ui/reader/`, `app/src/main/java/eu/kanade/tachiyomi/data/download/`,
   `data/src/main/sqldelight/`, `gradle/`, `app/build.gradle.kts`, `.github/workflows/`.
3. Read upstream's commit subjects for those paths. Call out anything that changes the
   extension contract, lib versions, the store index format, or migrations numbering.
4. `git merge upstream/main`. Resolve conflicts keeping Dokusho behaviour; when upstream
   renumbered a migration, renumber ours above it.
5. Full gate: `./gradlew spotlessCheck :app:testDebugUnitTest verifySqlDelightMigration :app:assembleDebug -Pdist=local`.
6. Device smoke: install over the previous build (migration path), open library, open a
   chapter, start TTS, check an extension still loads.
7. Report: commits merged · conflicts and how resolved · contract or migration changes ·
   gate result · anything to follow up in `PLAN.md`.

## Do not

- `git merge` with uncommitted work
- Resolve a conflict by taking a whole file from one side without reading it
- Rebase published history
- Push without the gate and the smoke
