# How a release works

This page explains, in plain words, how a new version of Dokusho gets from your laptop to
an APK people can download from GitHub.

## The short version

1. You change the version number in the code.
2. You put a **tag** on that commit, like `v0.2.0`, and push it.
3. GitHub sees the tag and builds the app by itself.
4. A few minutes later a new **Release** page appears with the APK files.
5. The README's Download badge and the app's update checker now point to it.

You never build or upload the APK yourself.

## What is a tag?

A tag is a name sticker on one commit. `v0.2.0` means "this exact commit is version 0.2.0".
Normal pushes to `main` do **not** make a release. Only pushing a tag that starts with `v`
does.

## Step by step

### 1. Bump the version

Open `app/build.gradle.kts` and change both numbers:

```kotlin
versionCode = 2          // goes up by 1 every release, never reused
versionName = "0.2.0"    // what people see
```

`versionCode` is for Android: a phone only installs an update when this number is bigger.
`versionName` is for humans and must match the tag without the `v`.

### 2. Commit and push

```bash
git add app/build.gradle.kts
git commit -m "Release 0.2.0"
git push origin main
```

Wait until the **Build & Test** check on GitHub is green (Actions tab). If it is red, fix
that first; a broken build makes a broken release.

### 3. Tag and push the tag

```bash
git tag -a v0.2.0 -m "Dokusho 0.2.0"
git push origin v0.2.0
```

This is the button that starts the release.

### 4. Watch it build

GitHub → **Actions** → **Release**. It runs three jobs:

| Job | What it does |
|-----|--------------|
| Build | builds the normal APKs, signs them with the Dokusho key, includes crash reporting |
| Build (FOSS) | builds one APK without Firebase and without the update checker |
| Create GitHub Release | makes the release page and attaches all APKs |

It takes about 10 to 20 minutes.

### 5. Done

The release appears at https://github.com/Globeeeee/dokusho/releases. Files on it:

| File | Who should install it |
|------|-----------------------|
| `dokusho-v0.2.0.apk` | anyone not sure which to pick (works on every phone, bigger) |
| `dokusho-arm64-v8a-v0.2.0.apk` | almost every modern phone (smaller) |
| `dokusho-armeabi-v7a-v0.2.0.apk` | very old 32-bit phones |
| `dokusho-x86-v0.2.0.apk`, `dokusho-x86_64-v0.2.0.apk` | emulators and a few tablets |
| `dokusho-v0.2.0-foss.apk` | people who want no Firebase and no update checks |

Phones that already have Dokusho see "update available" the next time the app checks.

## Keys and secrets

### Why a key is needed

Every release APK is **signed**. Think of the signature as a wax seal: Android checks that
each update carries the same seal as the version already installed. Same seal, update
installs. Different seal, Android refuses and the user has to uninstall first (and loses
their library). So the key must stay the same forever and must never leak.

### Which keys exist

There are two separate signing keys and two other secrets. Each has one job.

| Name | What it is | Used for | Where it lives |
|------|------------|----------|----------------|
| **App key** | `dokusho-app.jks` (a keystore file) + its password and alias | signing the Dokusho app APK | `~/.dokusho/signing/` and secrets on the `dokusho` repo |
| **Extension key** | `dokusho-extensions.jks` + its passwords and alias | signing the extension APKs | `~/.dokusho/signing/` and secrets on `dokusho-extensions-source` |
| **Firebase config** | `google-services.json` | crash reports in release builds | `~/.dokusho/signing/` and secret on the `dokusho` repo |
| **Bot token** | a GitHub token | lets the extensions build push to the index repo | only in GitHub secrets on `dokusho-extensions-source` |

What is in `~/.dokusho/signing/` on your Mac:

| File | Contents |
|------|----------|
| `dokusho-app.jks` | the app key itself |
| `dokusho-app.properties` | its password (`storePassword`, `keyPassword`) and alias (`keyAlias`) |
| `dokusho-app.fingerprint` | the key's public fingerprint, safe to share |
| `dokusho-extensions.jks` | the extension key |
| `dokusho-extensions.env` | its passwords and alias |
| `dokusho-extensions.fingerprint` | its public fingerprint, also written in `repo.json` of the index repo |
| `google-services.json` | Firebase config for project `dokusho-reader` |

Never open these in a chat, paste them anywhere, or copy them into a repo. To look at one
yourself, open it in a text editor on your own Mac.

### Which GitHub secret holds what

On the `dokusho` repo (Settings → Secrets and variables → Actions):

| Secret | Comes from |
|--------|------------|
| `SIGNING_KEY` | `dokusho-app.jks`, turned into text with `base64` |
| `KEY_STORE_PASSWORD` | `storePassword` in `dokusho-app.properties` |
| `KEY_PASSWORD` | `keyPassword` in `dokusho-app.properties` |
| `ALIAS` | `keyAlias` in `dokusho-app.properties` (it is `dokusho`) |
| `GOOGLE_SERVICES_JSON` | the whole `google-services.json` file |

On the `dokusho-extensions-source` repo:

| Secret or variable | Comes from |
|--------------------|------------|
| `SIGNING_KEY` | `dokusho-extensions.jks` as base64 |
| `KEY_STORE_PASSWORD`, `KEY_PASSWORD`, `ALIAS` | `dokusho-extensions.env` |
| `BOT_PAT` | a GitHub token (see below) |
| `SIGNING_KEY_FINGERPRINT` (variable, not secret) | `dokusho-extensions.fingerprint` |

GitHub never shows a secret again after it is saved. You can only replace it.

### How to put a secret on GitHub

Always feed the value from the file, never type it on the command line (the command line is
saved in your shell history):

```bash
S=~/.dokusho/signing
gh secret set SIGNING_KEY --repo Globeeeee/dokusho < <(base64 < $S/dokusho-app.jks)
gh secret set KEY_STORE_PASSWORD --repo Globeeeee/dokusho < <(grep '^storePassword=' $S/dokusho-app.properties | cut -d= -f2-)
gh secret set KEY_PASSWORD --repo Globeeeee/dokusho < <(grep '^keyPassword=' $S/dokusho-app.properties | cut -d= -f2-)
gh secret set ALIAS --repo Globeeeee/dokusho < <(grep '^keyAlias=' $S/dokusho-app.properties | cut -d= -f2-)
gh secret set GOOGLE_SERVICES_JSON --repo Globeeeee/dokusho < $S/google-services.json
gh secret list --repo Globeeeee/dokusho     # shows names only, never values
```

Or by hand: GitHub → repo → Settings → Secrets and variables → Actions → New repository
secret, then paste.

### How the keys were made (and how to make new ones)

You only do this once per key. **Making a new app key means existing installs cannot
update**, so only do it for a brand new app or if the old key is lost or leaked.

`keytool` comes with Java. With Android Studio installed:

```bash
KT="/Applications/Android Studio.app/Contents/jbr/Contents/Home/bin/keytool"
S=~/.dokusho/signing
mkdir -p $S && chmod 700 $S
PASS=$(openssl rand -base64 30 | tr -d '/+=' | cut -c1-32)     # a random password
"$KT" -genkeypair -keystore $S/dokusho-app.jks -storetype PKCS12 \
  -alias dokusho -keyalg RSA -keysize 4096 -validity 10000 \
  -storepass "$PASS" -keypass "$PASS" -dname "CN=Dokusho, O=Dokusho, C=PK"
printf 'storePassword=%s\nkeyPassword=%s\nkeyAlias=dokusho\nstoreFile=%s/dokusho-app.jks\n' \
  "$PASS" "$PASS" "$S" > $S/dokusho-app.properties
chmod 600 $S/dokusho-app.jks $S/dokusho-app.properties
unset PASS
```

What the options mean: `-alias` is the key's name inside the file, `RSA 4096` is the type and
strength, `-validity 10000` means it is valid for about 27 years. Then set the secrets as
shown above.

The public fingerprint (safe to share, used to prove which key signed something):

```bash
"$KT" -list -v -keystore $S/dokusho-app.jks \
  -storepass "$(grep '^storePassword=' $S/dokusho-app.properties | cut -d= -f2-)" | grep SHA256
```

The extension key is made the same way with the name `dokusho-extensions`; its steps and the
index fingerprint are in `dokusho-extensions-source/.claude/rules/publish.md`.

### How to get the Firebase config

The project is `dokusho-reader` under your Google account.

- Website: [console.firebase.google.com](https://console.firebase.google.com) → Dokusho →
  gear icon → Project settings → Your apps → Android app `app.dokusho` → download
  `google-services.json`. It contains all three app variants (`app.dokusho`, `.dev`, `.debug`).
- Command line:

```bash
firebase apps:list ANDROID --project dokusho-reader
firebase apps:sdkconfig ANDROID <app id from the list> --project dokusho-reader --out ~/.dokusho/signing/google-services.json
```

Then update the `GOOGLE_SERVICES_JSON` secret from that file. The Firebase config is less
secret than a signing key (it ships inside the APK), but keep it out of the repo anyway.

### How to make the bot token

Needed only when extensions start publishing.

1. GitHub → your avatar → Settings → Developer settings → Personal access tokens →
   **Fine-grained tokens** → Generate new token.
2. Name `dokusho-extensions-publish`, expiry 1 year.
3. Repository access: **Only select repositories** → `dokusho-extensions`.
4. Permissions → Contents: **Read and write**. Nothing else.
5. Generate, copy it once, then save it straight into the secret:
   `gh secret set BOT_PAT --repo Globeeeee/dokusho-extensions-source` and paste when asked.
6. Put a calendar reminder for the expiry date.

### Backup and a new computer

The keys exist in exactly two places: your Mac and GitHub (which cannot give them back).
If your Mac dies, the app key is gone unless you backed it up.

- **Back up** the whole `~/.dokusho/signing/` folder to an encrypted place: a password
  manager attachment (1Password, Bitwarden) or an encrypted disk image. Not to a repo, not to
  plain Google Drive, not to a chat.
- **New Mac:** restore the folder to `~/.dokusho/signing/`, then run
  `chmod 700 ~/.dokusho/signing && chmod 600 ~/.dokusho/signing/*`. Nothing else is needed;
  GitHub already has the secrets.

### If a key leaks

Treat it as real even if you are not sure. Full steps are in
[docs/SECRETS.md](docs/SECRETS.md) (Rotation). In short: make a new key, replace the GitHub
secrets, and accept that users must reinstall once.

## Something went wrong

| Problem | What to do |
|---------|------------|
| Release job is red | open it in Actions, read the first red step. Fix, then delete the tag and tag again (below) |
| Tag already exists | pick the next number, or delete the bad one first |
| Phone says "App not installed" when updating | the new `versionCode` is not higher than the installed one, or the key differs |
| Badge in README still shows the old version | wait a few minutes; the badge image is cached |

Delete a tag and its release to redo it:

```bash
gh release delete v0.2.0 --repo Globeeeee/dokusho --yes
git push origin --delete v0.2.0
git tag -d v0.2.0
```

Then fix, commit, push, and tag again.

## Version numbers, in short

`MAJOR.MINOR.PATCH`, like `0.2.0`.

- **PATCH** (`0.2.1`): small fixes.
- **MINOR** (`0.3.0`): new features, like the reader or text-to-speech.
- **MAJOR** (`1.0.0`): when Dokusho is ready for everyday use by others.

While it starts with `0.`, things can still change a lot between versions.

## Where it is set up

| File | Role |
|------|------|
| `.github/workflows/release.yml` | the release recipe GitHub follows when a `v` tag is pushed |
| `.github/workflows/build.yml` | the check that runs on every push to `main` |
| `app/build.gradle.kts` | version numbers and signing setup |
| `app/src/main/java/eu/kanade/tachiyomi/data/updater/AppUpdateChecker.kt` | where the app looks for new releases |
