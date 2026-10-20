# Backups

A backup is one small file with your library, progress and settings. Use it to move to a new
phone or to recover after a reset. Trackers are off in Dokusho, so a backup is the only copy of
your library and reading progress outside the phone.

## Create a backup

1. **More** → **Settings** → **Data and storage** → **Create backup**.
2. Pick what to include (see the table below) and confirm.
3. Choose where to save the file. It is named like `app.dokusho_2026-10-21_14-30.tachibk`.

Keep a copy somewhere other than the phone, such as cloud storage or a computer.

## Automatic backups

**More** → **Settings** → **Data and storage** → **Automatic backup frequency**: off, every 6
hours, every 12 hours (the default), daily, every 2 days or weekly. They go in the `autobackup`
folder of your storage location, and Dokusho keeps the 4 newest. The time of the last one shows
under the setting.

## What a backup holds

| Option | Contents |
|--------|----------|
| **Library entries** | Novels in your library, with their details |
| **Chapters** | Read and bookmarked chapters and reading progress |
| **Tracking** | Tracker links; empty while trackers are off |
| **History** | When you last read each novel |
| **Categories** | Your categories and which novels are in them |
| **All read entries** | Progress on novels you read but did not add to your library |
| **App settings** | Reader, library, download and other settings |
| **Extension stores** | The stores you added, such as the Dokusho store |
| **Source settings** | Each extension's own settings |
| **Include sensitive settings** | Private values such as the VPN password. Off by default; keep the file private if you turn it on |

A backup does **not** hold:

- Chapter text or images. Downloads stay in the `downloads` folder; copy that folder yourself if
  you want them on the new phone.
- The extensions themselves. Install them again from **Browse** → **Extensions**.
- Logins and cookies from WebView. Log in to the sites again.
- Files in the `local` folder used by [Local source](local-source.md).

## Restore a backup

1. On the new phone, install Dokusho and pick a storage location.
2. **More** → **Settings** → **Data and storage** → **Restore backup** and select the file.
3. Pick what to restore: **Library**, **Categories**, **App settings**, **Extension stores**,
   **Source settings**, then tap **Restore**.
4. If the screen lists **Missing sources**, install those extensions from **Browse** →
   **Extensions**. Novels from a missing source show in the library but cannot load chapters
   until the extension is installed.

Restoring adds to what is already in the app; it does not wipe your current library.
