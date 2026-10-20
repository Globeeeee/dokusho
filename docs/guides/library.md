# Library

The **Library** tab holds the novels you added with **Add to library** (the heart on a novel's
page). Long press a novel to select it; the bottom bar then offers **Set categories**, **Mark as
read**, **Mark as unread**, **Download**, **Delete** and **Migrate**.

## Categories

Categories are tabs in your library, for example Reading, On hold, Finished.

- Create, rename, reorder and delete them in **More** → **Categories** (also **More** →
  **Settings** → **Library** → **Edit categories**).
- Put novels in categories: select them in the library and tap **Set categories**.
- **More** → **Settings** → **Library** → **Default category** decides where new novels go, or
  has Dokusho ask every time you add one.
- **Per-category settings for sort** lets each category keep its own sort order.

## Filter, sort and display

Tap the filter icon on the **Library** tab.

| Tab | Options |
|-----|---------|
| **Filter** | **Downloaded**, **Unread**, **Started**, **Bookmarked**, **Completed**. Tap once to include, again to exclude |
| **Sort** | **Alphabetically**, **Total chapters**, **Last read**, **Last update check**, **Unread count**, **Latest chapter**, **Chapter fetch date**, **Date added**, **Random** |
| **Display** | List or grid layouts, columns, badges (**Downloaded chapters**, **Unread chapters**, **Local source**, language), category tabs and item counts, continue reading button |

The search icon on the library finds novels by title.

## Library updates

A library update checks every novel's source for new chapters. New ones show in the **Updates**
tab.

- Pull down on **Library** to update the category on screen.
- The menu (⋮) on **Library** has **Update library** for everything.

Automatic updates live in **More** → **Settings** → **Library** → **Global update**:

| Setting | Does |
|---------|------|
| **Automatic updates** | Off (the default), every 12 hours, daily, every 2 days, every 3 days or weekly |
| **Automatic updates device restrictions** | Only on Wi-Fi, only on unmetered network, only when charging |
| **Categories** | Which categories to update; excluded categories win over included ones |
| **Automatically refresh metadata** | Also fetches new covers and descriptions |
| **Smart update** | Skips novels with unread chapters, novels you have not started, novels marked Completed, or novels not expected to have a new chapter yet |
| **Show unread count on Updates icon** | Badge on the **Updates** tab |

Updating hundreds of novels at once can get you rate limited by a site. Dokusho warns you first;
see [troubleshooting](troubleshooting.md#large-updates-and-downloads). If an update ends with
errors, see [library update errors](troubleshooting.md#library-update-errors).

## Moving a novel to another source

If a site stops working or another one is further ahead, migrate the novel. Reading progress
moves with it.

1. Open **Browse** → **Migrate** and pick the old source, then the novels to move. For one
   novel you can also open it and use the menu (⋮) → **Migrate**, or select novels in the
   library and tap **Migrate**.
2. Under **Data to migrate**, keep **Chapters** (read status and progress) and **Categories**.
   **Custom cover** and **Notes** are optional. **Delete downloads of current entry after
   migration** removes the old downloads.
3. Pick the sources to search, in priority order. **Advanced search mode** splits the title into
   keywords for a wider search; **Match based on chapter number** picks the match furthest
   ahead. Both are slower and send more requests to the sites.
4. Dokusho searches and shows the best match for each novel with its latest chapter. The menu on
   each row has **Search manually**, **Don't migrate**, **Migrate now** and **Copy now**.
5. Tap **Migrate** to replace the old entry, or **Copy** to keep both.

Chapters are matched by chapter number, so progress carries over best between sources that
number chapters the same way.

## Tracking

Trackers (AniList, MyAnimeList and others) are switched off in Dokusho for now. Library and
progress stay on the phone; keep them safe with [backups](backups.md).
