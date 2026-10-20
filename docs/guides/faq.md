# FAQ

## Where do I add the extension store?

**More** → **Settings** → **Browse** → **Extension stores** → **Add extension store**, then
paste:

```
https://github.com/Globeeeee/dokusho-extensions/raw/repo/index.pb
```

Full steps in [getting started](getting-started.md#1-add-the-extension-store-once).

## Can I use Mihon or Tachiyomi extensions?

No. Those extensions read manga and do not load in Dokusho. Dokusho only installs its own novel
extensions.

## A site shows a Cloudflare check or keeps failing

Dokusho opens the check on screen; tick the box if one appears. If it loops, open the novel in
WebView and wait for the site to load, or clear cookies. See
[troubleshooting](troubleshooting.md#cloudflare).

## A site does not load at all on my network

Try **More** → **Settings** → **Advanced** → **DNS over HTTPS (DoH)** first. If the site is
blocked by your provider, **More** → **Settings** → **VPN** sends Dokusho's traffic (WebView
included) through a SOCKS5 or HTTP proxy server you enter. Other apps are not affected, and it
applies after restarting the app. Use **Check connection** to test it. Only use a server you
trust: it sees the sites you open.

## Why is a chapter locked?

Paid or early-access chapters stay locked. Dokusho never bypasses a paywall. Log in to the site
in WebView with your own account; chapters your account can read then open, download and read
aloud normally.

## Why is a chapter's date empty?

The site does not publish a date for that chapter, or the extension cannot read it. Dokusho shows
nothing rather than guess.

## Can I read my own EPUB or TXT files?

Not as text yet. [Local source](local-source.md) opens image folders, archives and the pictures
inside an EPUB. Text import is planned.

## Does Dokusho sync with AniList or MyAnimeList?

Not for now; trackers are off. Use [backups](backups.md) to keep your library and progress safe.

## Can I export a novel to EPUB or PDF?

Yes. On the novel's page tap the three-dot menu, then **Export**. For several novels at once use
**More** → **Settings** → **Data and storage** → **Novel export** → **Export novels**. Files go to
`exports/<Title>/` in your storage location. See the [export guide](export.md).
**Export** → **Library List** in the same screen still saves your library's titles, authors and
artists as a CSV file.

## Listen stops when the screen turns off

Some phones stop background apps to save battery. Allow Dokusho to run in the background in
Android's battery settings for the app. More on listening in the [reader guide](reader.md#listen-read-aloud).

## Where do I report a bug or ask for a site?

Open an issue on GitHub: [Globeeeee/dokusho issues](https://github.com/Globeeeee/dokusho/issues).
Say which Dokusho version you use (**More** → **Settings** → **About**), the site, the novel and
what you did. Never post passwords, cookies or account details.
