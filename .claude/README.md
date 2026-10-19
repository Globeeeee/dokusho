# .claude

Claude Code config for the Dokusho app. Same shape as the SMH and eReader setups: a short
router in `/CLAUDE.md`, packs loaded per mode, skills loaded on invocation, cheap hooks.

## What loads when

| File | Loaded |
|------|--------|
| `/CLAUDE.md` | every turn: router, always-on rules, path map |
| `docs/PLAN.md` | when the router or a skill says so (FEATURE work) |
| `.claude/rules/*.md` | only the pack the mode lists |
| `.claude/skills/*/SKILL.md` | name and description listed, body loads when invoked |
| `.claude/agents/scout.md` | when a wide search is delegated |
| `.claude/settings.json`, `/.mcp.json` | at startup |

## Packs

| Pack | For |
|------|-----|
| `architecture.md` | modules, layers, Metro DI, extensions, reader, downloads, data, workers |
| `novel.md` | the text design: `NovelSource`, sanitising, reader, downloads, export, TTS |
| `development.md` | evaluate, new features, migrations, security, testing, debugging |
| `project.md` | identity, build commands, native, upstream, MCP |

## Skills

| Skill | Use |
|-------|-----|
| `/dokusho-route` | classify a prompt: mode, phase, packs, plan or execute |
| `/dokusho-feature` | build a feature with the Mihon seam plan |
| `/dokusho-ship-check` | scoped spotless, tests, migration check, debug build, device smoke |
| `/dokusho-upstream-sync` | merge mihonapp/mihon safely |

## Hooks

| Event | Script | Effect |
|-------|--------|--------|
| PreToolUse on `git commit` / `git push` | `hooks/pre-push-guard.sh` | denies when a keystore, `println(` or a likely secret is staged |
| Stop | `hooks/stop-kotlin-gate.sh` | greps changed Kotlin for `println`, `Log.*`, wildcard imports, leftover TODOs. No Gradle, so it costs nothing when clean |

No format-on-save hook: ktlint runs through Gradle and takes seconds per call. Run
`./gradlew spotlessApply` before a commit instead (ship-check does it).

## Settings

- Gradle output is forced plain and quiet, and `bashOutputMaxChars` caps it.
- `permissions.allow` covers the Gradle tasks and `adb` reads this project uses. Release
  builds, installs, pushes and merges ask. Keystores and `build/` are denied.
- MCP: `context7` only. Add others with `claude mcp add` if the per-turn cost is worth it.

## Personal overrides

`.claude/settings.local.json`, gitignored.
