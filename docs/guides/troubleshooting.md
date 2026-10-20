# Troubleshooting

## Cloudflare

Some sites (Scribble Hub, Royal Road) sit behind a Cloudflare check.

- When a check needs you, Dokusho opens it on screen. Tick the box if one appears; the page
  closes by itself once the site lets you through.
- Still failing? Open the novel or source, tap the menu and **Open in WebView**, wait for the
  site to load fully, go back and retry.
- **More** → **Settings** → **Advanced** → **Clear cookies** resets a stuck check. Then open the
  site in WebView again.
- Updating Android System WebView from the Play Store helps when checks keep looping.

## "Unable to resolve host" or a site never loads

Your network or provider may be blocking the site (Wattpad is often blocked this way).

1. **More** → **Settings** → **Advanced** → **DNS over HTTPS** → **Cloudflare**, then retry.
2. If it still fails, try mobile data, or a trusted VPN app such as Cloudflare 1.1.1.1 with WARP.
   Dokusho also has its own proxy setting in **More** → **Settings** → **VPN**; see the
   [FAQ](faq.md#a-site-does-not-load-at-all-on-my-network).

## Timeouts

A slow site can time out on the first try. Pull down to refresh or tap **Retry**. Project
Gutenberg is busy at times; try again a minute later.

## Library update errors

When an update finishes with errors, tap the notification to open the error log. Common causes:
the site is down, a Cloudflare check is waiting (open the novel in WebView), or the extension
needs an update (**Browse** → **Extensions**).

## Large updates and downloads

Dokusho warns before updating or downloading a lot at once, because many requests in a short
time can get you rate limited or blocked by the site. Smaller batches, or a category per batch,
avoid that. See [library updates](library.md#library-updates) and [downloads](downloads.md).

## Storage

Pick where Dokusho keeps downloads and backups in **More** → **Settings** → **Data and storage**
→ **Storage location**. Choose a folder you own (for example `Documents/Dokusho`). Downloads
live in its `downloads` folder, one folder per site, then novel, then chapter.

If downloads vanish after reinstalling, select the same folder again; Dokusho picks them up.
The same folder holds `autobackup` ([backups](backups.md)) and `local`
([Local source](local-source.md)).

## Locked chapters

Paid or early-access chapters show as locked. Dokusho never bypasses paywalls. Log in to the
site in WebView with your own account; chapters your account can read then open normally.
