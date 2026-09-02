# Privacy Policy — Nooz

> **This is the working copy, not the hosted one.** The URL Play Console points at
> is **https://asystemofcells.com/nooz/privacy**, a standalone page under
> `asystemofcells/apps/asoc-com/public/nooz/privacy/`. It sits in `public/`, which
> Astro copies verbatim, so no analytics script can reach it. Keep the two in sync
> by hand.

**Last updated: 29 August 2026**

Nooz is a news reader for Android (package `dev.asystemofcells.nooz`), made by
A System of Cells. This policy describes exactly what the app does with data. It is
short because the app does very little.

## The short version

Nooz has no accounts, no advertising, no analytics and no tracking. It does not
build a profile of you. We operate no server, so there is nowhere for your data to
go even if we wanted it.

## What the app collects

**Nothing.** No personal information, no device identifiers, no reading history, no
usage statistics. The app has no telemetry and no crash reporting.

## What is sent off your device, and to whom

**Only the feed requests you asked for.** When you add a source, the app fetches
that source's feed directly from its own server, over HTTPS where the source offers
it. That publisher's server sees a normal request from your device, in the same way
it would if you opened the feed in a browser.

We are not in that conversation. There is no proxy of ours in front of it, and no
copy of what you fetch reaches us.

Optional full-text extraction fetches the article page from the same publisher when
a feed only gives you a summary. Same rule: it is a direct request to that
publisher, triggered by you.

No other data is transmitted anywhere. There is no other network activity in the app.

## What is stored, and where

Your sources, your read state, your settings and any cached article text are stored
in the app's private on-device storage. All of it stays on your device. Uninstalling
Nooz deletes all of it.

The "what you missed" view is computed on your device from that local data. It is
never uploaded, and it is never compared against anyone else.

## What the app deliberately does not do

- No push notifications. The app does not request the notification permission.
- No engagement ranking, no streaks, no badges.
- No infinite scroll. Feeds end, and the app marks where.
- No account, and no way to create one.

## Permissions, and why each exists

| Permission | Why |
|---|---|
| `INTERNET` | To fetch the feeds you added, and nothing else. |
| `ACCESS_NETWORK_STATE` | To avoid attempting a fetch while offline. |

## Children

Nooz is not directed at children and collects no personal information from anyone,
including children.

## Changes

If this policy changes, the "Last updated" date above changes with it, and the
revised policy is published at this same URL.

## Contact

Nooz is made by **A System of Cells**. Questions about this policy or the app:
nooz@asystemofcells.com
