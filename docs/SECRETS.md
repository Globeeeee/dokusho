# Secrets

How credentials are stored, used and rotated across the Dokusho repos. The same rules apply to
`dokusho-extensions-source` and `dokusho-extensions`; this file is the single reference.

## Rules

1. No secret is ever committed, pasted into an issue, a PR, a chat, a log or a notification.
   Not a password, keystore, token, cookie, API key, service-account file or `.env`.
2. Secrets live in exactly two places: the owner's machine under `~/.dokusho/signing/`
   (mode 700, files 600) and GitHub Actions secrets. Nothing else.
3. Repos carry names only: `.env.example`, `keystore.properties.example`. Real files are
   gitignored (`.env`, `.env.*`, `*.jks`, `*.keystore`, `*.p12`, `*.pem`, `*.key`,
   `keystore.properties`, `signingkey.jks`, `google-services.json`, `service-account*.json`).
4. Three guards enforce it: a pre-commit and pre-push hook that rejects secret file names and
   secret-looking content, gitleaks on every push and PR, and agent permissions that deny
   reading `.env`, keystores and `~/.dokusho`.
5. When a value must be passed to a tool, pass it from a file or a variable, never as a
   literal on the command line: `gh secret set NAME --body "$(cat file)"`, or `< file`.
6. Public by design, fine to commit: the signing certificate fingerprint (`repo.json`), the
   `applicationId`, package names, repo URLs.
7. A suspected leak is treated as real: rotate first, investigate second.

## Inventory

| Secret | Used by | Local | CI |
|--------|---------|-------|----|
| Extension signing keystore | `dokusho-extensions-source` release builds | `~/.dokusho/signing/dokusho-extensions.jks` | secret `SIGNING_KEY` (base64) |
| Its store and key passwords, alias | same | `~/.dokusho/signing/dokusho-extensions.env` | secrets `KEY_STORE_PASSWORD`, `KEY_PASSWORD`, `ALIAS` |
| Its certificate fingerprint | app trusts the index with it | `~/.dokusho/signing/dokusho-extensions.fingerprint` | variable `SIGNING_KEY_FINGERPRINT` (public) |
| Index push token | publish job pushes to `dokusho-extensions` | none | secret `BOT_PAT` |
| App release keystore | `dokusho` release builds (phase 7) | `~/.dokusho/signing/dokusho-app.jks` + `keystore.properties` | secrets `storeFile`, `storePassword`, `keyAlias`, `keyPassword` with `MIHON_GITHUB_RELEASE=true` |
| Firebase config | not used; telemetry is off | none | none |

## Local use

Extensions, release build signed with your key:

```bash
cp .env.example .env            # fill in from ~/.dokusho/signing/dokusho-extensions.env
scripts/with-env.sh ./gradlew :src:en:<name>:assembleRelease
```

App, local signed build: copy `keystore.properties.example` to `keystore.properties` and fill it in.
Debug builds need neither.

## Rotation

| Event | Do |
|-------|----|
| `BOT_PAT` expired, revoked or leaked | create a fine-grained PAT (repo `dokusho-extensions`, Contents: read and write), `gh secret set BOT_PAT --repo Globeeeee/dokusho-extensions-source --body "$(cat token.txt)"`, delete `token.txt` |
| Keystore password leaked | passwords cannot be changed without re-keying; generate a new keystore, update secrets and `repo.json` fingerprint, and accept that users must reinstall extensions |
| Keystore file lost | same as above; there is no recovery, so back up `~/.dokusho/signing/` |
| Secret committed by mistake | rotate it immediately, then rewrite history (`git filter-repo`) and force-push with lease; a revert is not enough |

## Backup

`~/.dokusho/signing/` holds the only copy of the keystores. Back it up to an encrypted
location (password manager attachment or an encrypted disk image). Never to a repo, a chat or
plain cloud storage.
