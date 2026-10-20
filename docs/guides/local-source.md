# Local source

**Local source** shows files you put on the phone yourself, next to the extension sources in
**Browse** → **Sources**. Each folder becomes one entry in Dokusho, each file or subfolder inside
it a chapter.

## Text EPUB and TXT are not supported yet

Local source still works the way Mihon built it, for images:

- A chapter opens in the image reader, not the novel text reader. Text settings and **Listen**
  are not available there.
- An **EPUB** shows only the pictures inside it. A text-only EPUB opens with no pages.
- **TXT** and **HTML** files are ignored and do not show up as chapters.

Reading your own EPUB and TXT files as text is planned. Until then, Local source is useful for
illustrated volumes, artbooks and comics. For text, add the novel from an extension instead.

## Where the folder is

The folder is called `local`, inside the storage location you picked when you first set up
Dokusho:

```
<storage location>/local
```

Check or change the storage location in **More** → **Settings** → **Data and storage** →
**Storage location**. Dokusho creates `local` (next to `downloads` and `autobackup`) on its own.
If you move the storage location, move the `local` folder with it.

## Folder layout

One folder per series, then one file or folder per chapter:

```
local/
├── My Series/
│   ├── cover.jpg
│   ├── ComicInfo.xml
│   ├── Chapter 1.cbz
│   ├── Chapter 2/
│   │   ├── 001.jpg
│   │   └── 002.jpg
│   └── Volume 3.epub
└── Another Series/
    └── ...
```

- The series folder name is the title, unless `ComicInfo.xml` gives another one.
- Files and folders whose name starts with a dot are skipped.
- A series folder needs at least one chapter inside before it shows anything.

## Supported chapter formats

| Format | What Dokusho shows |
|--------|--------------------|
| Folder of images | Each image as a page, in name order |
| `.zip`, `.cbz` | Images inside the archive |
| `.rar`, `.cbr` | Images inside the archive |
| `.7z`, `.cb7` | Images inside the archive |
| `.tar`, `.cbt` | Images inside the archive |
| `.epub` | Only the images inside, in reading order |

Any other file type in a series folder is ignored.

## Cover

Put an image named `cover` (`cover.jpg`, `cover.png`, `cover.webp` and so on) in the series
folder. Without one, Dokusho copies the first image of a chapter and saves it as `cover.jpg`.

## Details: ComicInfo.xml

Title, author, description, genres and status come from a `ComicInfo.xml` file in the series
folder. Every field is optional:

```xml
<?xml version="1.0" encoding="UTF-8"?>
<ComicInfo xmlns:ty="http://www.w3.org/2001/XMLSchema">
  <Series>My Series</Series>
  <Writer>Author Name</Writer>
  <Penciller>Illustrator Name</Penciller>
  <Summary>One or two paragraphs about the story.</Summary>
  <Genre>Fantasy, Adventure</Genre>
  <ty:PublishingStatusTachiyomi>Ongoing</ty:PublishingStatusTachiyomi>
</ComicInfo>
```

| Field | Shows as |
|-------|----------|
| `Series` | Title |
| `Writer` | Author |
| `Penciller`, `Inker`, `Colorist`, `Letterer`, `CoverArtist` | Artist |
| `Summary` | Description |
| `Genre`, `Tags` | Genres |
| `PublishingStatusTachiyomi` | Status: `Ongoing`, `Completed`, `Licensed`, `Publishing finished`, `Cancelled`, `On hiatus` or `Unknown` |

- No `ComicInfo.xml` in the series folder? Dokusho copies the first one it finds inside a
  chapter archive or chapter folder.
- An older `details.json` file still works once: Dokusho reads it, writes a `ComicInfo.xml` from
  it and deletes the JSON.
- For an EPUB chapter, the author and description come from the EPUB itself.

Pull down on the entry's page to reload details after you edit the file.

## Chapter names and order

- The chapter name is the file name without its extension, or the folder name.
- A `ComicInfo.xml` inside a chapter can set its `Title`, `Number` and date (`Year`, `Month`,
  `Day`). An EPUB chapter takes its title and date from the EPUB.
- The chapter number is read from the name, so `Chapter 12.cbz` is chapter 12. Name files
  `Chapter 001`, `Chapter 002` and so on to keep the order clear.
- Without a date in the file, the chapter date is when the file was last changed.

## Browsing

Open **Browse** → **Sources** → **Local source**. **Popular** lists every series by title,
**Latest** shows series changed in the last 7 days, and search matches folder names. Add a series
to your library like any other novel; pull down on its page to pick up new chapter files.
