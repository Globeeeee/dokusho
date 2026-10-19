---
name: dokusho-route
description: Clean up a raw prompt for the Dokusho app and route it - pick the CLAUDE.md mode, the PLAN.md phase, the packs to read, and plan mode versus execute. Use when Qasim runs /dokusho-route, asks to route or clean up a prompt, or asks which mode or phase fits.
---

# Route

Modes live in `CLAUDE.md`, phases in `docs/PLAN.md`. Do not invent a second system.

## Do

1. Take the raw prompt (or the latest task if he only said `/dokusho-route`).
2. Classify: mode from the `CLAUDE.md` table, priority PRIVACY > SOURCE-API > FEATURE > BUGFIX
   > QUICK; phase from `PLAN.md`. Obey `ponytail only` / `full feature` / `quick`.
3. Pick how to run it:

| Case | Run as |
|------|--------|
| EXPLAIN / DOCS | answer directly |
| FEATURE, SOURCE-API, READER touching more than three Mihon files | plan mode first, then `/dokusho-feature` |
| FEATURE with a clear seam | `/dokusho-feature` |
| UPSTREAM | `/dokusho-upstream-sync` |
| BUGFIX without a repro | reproduce on device first |
| QUICK / TESTS / BUGFIX with cause known | execute |

4. Rewrite the prompt to name: phase, the Mihon seam (which existing class or file), new files,
   strings, migration yes or no, and what done looks like on the device.
5. Output:

```markdown
Mode: <MODE> - <one line>
Phase: <n. name>
Packs: <files and sections>
Run as: <plan | /dokusho-feature | execute>
Ready prompt:
> <rewritten prompt>
```

6. Stop. Execute only when Qasim says go.
