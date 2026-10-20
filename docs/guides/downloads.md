# Downloads

Downloaded chapters open without internet, images included.

## Download chapters

- **One chapter**: tap the download icon on its row in the chapter list.
- **Several**: long press a chapter, tap more to select them, then **Download** in the bottom bar.
- **In bulk**: tap the download icon at the top of the novel's page and pick **Next chapter**,
  **Next 5 chapters**, **Next 10 chapters**, **Next 25 chapters**, **Unread** or **Bookmarked**.
- **From the library**: long press novels, then **Download** in the bottom bar.

**More** → **Download queue** shows progress; pause, reorder or cancel there.

Dokusho warns before very large batches, because many requests at once can get you slowed down
or blocked by the site. See [troubleshooting](troubleshooting.md#large-updates-and-downloads).

Locked chapters are not downloaded; they fail with **Locked on the site, not downloaded**. Unlock
them on the site with your own account first.

## The downloaded badge

A downloaded chapter shows a check icon on its row instead of the download arrow; tap it and pick
**Delete** to remove the download. In the library, turn on **Downloaded chapters** under
**Display** (the filter icon) to see how many chapters of each novel are downloaded.

**More** → **Downloaded only** hides everything that is not downloaded, handy on a plane.

## Where they are stored

Downloads go in the `downloads` folder of your storage location (**More** → **Settings** →
**Data and storage** → **Storage location**):

```
<storage location>/downloads/<Source (LANG)>/<Novel title>/<Chapter>/
    chapter.html
    meta.json
    image_001.jpg
```

`chapter.html` is the text, `meta.json` the title and address, and each picture in the chapter
is saved next to them. If a picture fails to download, it still loads from the site when you
are online.

After reinstalling Dokusho, select the same storage location again and your downloads come back.

## Download settings

**More** → **Settings** → **Downloads**:

| Setting | Does |
|---------|------|
| **Only on Wi-Fi** | Holds downloads until you are on Wi-Fi |
| **Concurrent source downloads** | How many sources download at the same time |
| **Delete chapters** → **After manually marked as read** | Deletes a download when you mark it read |
| **Delete chapters** → **After reading automatically delete** | Deletes the last read chapter, or the 2nd to 5th last, once you finish reading |
| **Allow deleting bookmarked chapters** | Lets the two options above remove bookmarked chapters |
| **Excluded categories** | Novels in these categories are never deleted automatically |
| **Auto-download** → **Download new chapters** | Downloads new chapters found by library updates, for the categories you pick |
| **Auto-download** → **Skip downloading duplicate read chapters** | Skips a new chapter that matches one you already read |
| **Download ahead** → **Auto download while reading** | While you read, downloads the next 2, 3, 5 or 10 unread chapters |

Download ahead only starts when the current chapter and the next one are already downloaded.

**Save as CBZ archive**, **Split tall images** and **Concurrent page downloads** are for image
chapters and change nothing for novels.

## Without downloading

Chapters you open online are kept in a 100 MB chapter cache, so recent ones reopen quickly and
may open offline as text. Only downloads are sure to work offline, with their images. Clear the
cache in **More** → **Settings** → **Data and storage** → **Clear chapter cache**.
