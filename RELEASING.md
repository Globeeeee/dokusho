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

## What makes the APK trusted

Every release APK is **signed** with the Dokusho key. Android uses the signature to know an
update really comes from the same app. If the key changed, Android would refuse the update
and users would have to uninstall first.

- The key lives only in `~/.dokusho/signing/dokusho-app.jks` on your Mac and in GitHub
  secrets. It is never in the code. See [docs/SECRETS.md](docs/SECRETS.md).
- **Back up `~/.dokusho/signing/`.** Losing the key means no more updates for existing installs.

GitHub uses these secrets during the release (Settings → Secrets and variables → Actions):

| Secret | What it is |
|--------|------------|
| `SIGNING_KEY` | the key file, encoded as text |
| `KEY_STORE_PASSWORD`, `KEY_PASSWORD`, `ALIAS` | the key's passwords and name |
| `GOOGLE_SERVICES_JSON` | Firebase settings for crash reports |

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
