# Privacy

Dokusho is a reader app. It has no account system and no server of its own.

## What stays on your device

Your library, reading history, progress, settings, downloads, exports and backups. Logins
you make to novel sites happen in the in-app browser; the cookies stay on your device and are
only sent to that site.

## What leaves your device

| What | To whom | When |
|------|---------|------|
| Requests for novel pages | the site an extension reads from | when you browse, update or download |
| Crash reports and anonymous usage events (Firebase Crashlytics and Analytics) | Google Firebase, project owned by the Dokusho maintainer | only in builds downloaded from the Dokusho GitHub releases; turn it off in Settings → Advanced. The FOSS build and builds from source contain no Firebase code |
| Update check | GitHub API | when the app checks for a new version |
| Tracker sync (AniList, MyAnimeList) | the tracker you log in to | only after you connect it |

Crash reports never include chapter text, cookies, passwords or account details.

## Contact

Open an issue at https://github.com/Globeeeee/dokusho/issues.
