# Brief You — handover guide

Everything needed to change, fix or release Brief You without any earlier chat history. It is
written for two readers at once: **you** (the owner), and **an AI assistant** (Claude or any
other) that you hand this repository to. If you start a new chat, give it this repository and
say: *"Read HANDOVER.md first, then …"*.

Last updated: November 2026, at **Brief You 0.16.2.9**.

---

## 1. What Brief You is

- An Android RSS reader: a **fork of [Read You](https://github.com/ReadYouApp/ReadYou)** by Ashinch
  (Kotlin, Jetpack Compose, Material 3), redesigned with **Material 3 Expressive** and extended
  with its own features: Triage, on-device AI (Gemma), read aloud, a calmer digest notification,
  muted keywords and duplicate hiding, Read Later, reading stats, backup and more.
  The README lists every feature in detail; it is kept up to date and is the feature reference.
- Repository: **github.com/Humorist2601/BriefYou** (owner: Humorist2601). Branch `main` is the
  released app.
- App IDs: **`com.ay.briefyou`** (release), **`com.ay.briefyou.nightly`** (test builds, installs
  next to it). The code's Kotlin package is still Read You's `me.ash.reader` (on purpose, see §3).
- Phones: Android 8.0+ (minSdk 26), 64-bit ARM only (`arm64-v8a`), targetSdk 34, compileSdk 36.
- The owner uses: a **Local account** (no sync service), ~300–400 articles a day, Triage first
  thing, GrapheneOS on a Pixel 9 Pro (Android 16).

---

## 2. How to work on it (rules the owner set)

These are the owner's standing preferences. A new assistant should follow them.

1. **Ask before committing.** Every commit, test branches included, needs the owner's OK
   ("commit" from the owner = approval for that change).
2. **Ask questions before building a feature**, and show a **preview/mock-up for anything
   visual** before building it. Keep it if they like it; remove it cleanly if they don't.
3. **Keep the README updated** with every user-visible change, in the same commit.
   The README's hero, feature cards, section headers and feature tables (`TABLES` in the script) are pictures (light and dark) in
   `.github/assets/readme/`, drawn by `.github/readme/make_assets.py` (Google Sans Flex outlines,
   Material Symbols in `.github/readme/icons/`). To change their text or add one, edit the lists in
   the script and run `python3 .github/readme/make_assets.py` from the repository root.
   All feature tables share one name-column width (`NAME_W`), so they line up. The screenshot
   strip has a dark twin, `.github/assets/screenshots-dark.png`, made from `screenshots.png` by
   darkening the background around the phones and turning the white label pills into dark tonal
   ones; remake it whenever the screenshots change.
4. **Watch every build to the end.** After a push, follow the GitHub Actions run until it
   finishes; read failures, fix them, push again, and report the result (and the release name).
5. **Plain language** in replies: what changed and what the owner will notice, not jargon.
6. **Commit messages:** a short title, then bullet points of what changed for the user. Commits
   have been authored as `Claude <noreply@anthropic.com>` (owner's choice); keep or change that
   as the owner prefers.
7. **Never commit straight to `main` for experiments.** Use a test branch (it builds a Nightly),
   let the owner try it, then merge to `main` with a full changelog (that makes a release).
8. When the owner says something "causes more issues", remove it entirely rather than patching
   around it.
9. **New screens and sheets look like the existing ones** (the owner wants Brief You to feel
   cohesive, down to the last corner). Pages: `RYScaffold` with the collapsing `DisplayText`
   title, the tonal back circle, hero cards (32 dp), cards and tiles (28 dp). Connected lists come
   from `brief/ui/Groups.kt`: `groupShape` (24 dp outer, 6 dp inner) on pages and sheets,
   `nestedGroupShape` (16 / 4) inside dialogs and cards, rows `Group.Gap` (3 dp) apart. Bottom
   sheets: a Title Large title (normal weight), an optional Body Medium muted subtitle, a divider,
   24 dp sides, opened fully (`skipPartiallyExpanded`; the AI sheet's 70 % is the one exception).
   Dialogs: `InWindowAlertDialog` (Read You's `RYDialog` draws through it), the standard title, no
   icon. Labels: pills (`RoundedCornerShape(50)`), Label Medium, 10 × 4 dp padding; tertiary for
   what's yours or watched, neutral for plain info. Tiles, cards, banners (Read You's `Banner`: the Starred / Unread / All
   openers and the Settings banners; their icon sits on a turning cookie, `BannerCookie`, and the openers' arrow
   nudges right, `pressNudge`) and the tour's buttons press with `PressFeel`.
   Corners come from one scale, `Corner` in `Groups.kt` (6 / 16 / 24 / 28 / 32, pills full).
   Settings sections are `SettingGroup` cards (`brief/ui/SettingGroup.kt`): Read You's `SettingItem`
   and `SelectableSettingGroupItem` draw no background and pad themselves for the card when inside
   one; every row's title is 16 sp. Banners are `bannerContainer()`: Read You's lavender in light
   mode, the tinted primary container in dark mode.
   Feeds page: groups joined via `GroupWithFeedsContainer(shape, top)`, with
   `GroupIconTile` and `CountPill` (`brief/feeds/GroupTile.kt`); group icons are chosen in the group's
   options and kept in `GroupIcons` (prefs `group_icons`, images in `files/group_icons`; in backups as
   `group_icons.json`, by group name; Triage's places sheet shows them too). Feed and group icons can be Material icons (outlined, 54; `brief/feeds/SymbolIcons.kt`, stored as `icon:<name>`;
   or any emoji typed in the same dialog). Day labels show a count (`brief/list/DayCount.kt`, passed to Read You's
   `StickyHeader`, which draws a faint line only while articles pass under it: `rememberScrolledUnder`). Option
   sheets (hold a feed or group) are Material 3 sheets: `OptionSheet`/`SheetSwitch`/`SheetHeader`/
   `SheetTile`/`SheetActions` in `brief/ui/OptionSheet.kt`, with `FeedOptions`/`SettingTiles`/`GroupChips`
   (`brief/feeds/FeedOptions.kt`); they ignore the keyboard. **Every other sheet is built from the same pieces**
   (`brief/ui/SheetParts.kt`): `SheetTitle` (title, a muted line, divider), `ChoiceRow` (a joined list; the chosen
   one filled in `primaryContainer` with a tick), `JoinedChoices`/`JoinedLabel` (a joined row of buttons, like the
   sleep timer's) and `ValueStepper` (a value large in a pill with − and +). Sheets use 24 dp sides, 16 dp at the
   bottom, and open fully. Read You's `RadioDialog` (36 settings) now draws `ChoiceSheet` (`brief/ui/ChoiceSheet.kt`);
   the Text sheet has a live preview, a joined slider group and joined font/alignment buttons; the Translate sheet
   shows each language in its own name (`TranslationPrefs.ownName`); the Number and Speed sheets use the stepper
   (Speed adds quick picks); the Sponsor sheet is in the same design. Add a feed is a floating dialog
   (`AddFeedDialog`) using the same `FeedOptions`, in its own window like the sheets' dialogs (in the app's window
   the keyboard's slide re-laid out the feeds page every frame). `SubscribeState.Configure` compares its `SyndFeed` by
   identity: Rome's reflective `equals` over every entry made each tap in the found-feed dialog lag. Every `InWindowAlertDialog` (so RYDialog and
   TextFieldDialog too) is drawn in a full-screen window of its own that doesn't move for the keyboard: the
   dialog moves above it inside, the cursor's handle stays put, and nothing behind is re-laid out per keyboard
   frame. `DialogHost` now only carries `ScreenOverlay`; `OptionSheetDialogs` is kept but no longer needed. Read You's `FeedOptionView` and `BottomDrawer` are no longer used
   by the feeds page. Empty lists: `EmptyListState`
   (`brief/list/EmptyState.kt`, shown from `BriefFlowOverlays`). Card colour: surfaceContainerHigh. Number tiles (Filters, Reading stats) are one `StatTile` (`brief/ui/StatTile.kt`):
   icon on a cookie, wide number, left-aligned.

---

## 3. How the fork is organised (important)

Brief You stays mergeable with Read You's ongoing development. Three rules make that work:

1. **Brief You's own code lives in `app/src/main/java/me/ash/reader/brief/`** (and its own
   resources: `res/values/strings_fork.xml`, `res/values/themes_fork.xml`, drawables named
   `ic_shortcut_*` etc.). Read You never touches these, so they never conflict.
2. **Read You's files are changed only with small hooks**, and **every changed block must contain
   the words `Brief You`** (a `// Brief You` comment on the line, or `<!-- Brief You … -->`,
   `# Brief You` in TOML). That way a merge conflict always shows whose line is whose.
3. **Rewritten Read You files** have a Brief You copy instead; Read You's original stays unused
   and untouched. The list is in **`.github/fork/copies.txt`** (e.g. Read You's
   `NotificationHelper.kt` → `brief/notifications/NotificationHelper.kt`, `RYWebView.kt` →
   `brief/webview/RYWebView.kt`, `BottomBar.kt` → `brief/reading/ReadingBar.kt`).

**Checking it:** `.github/fork/fork_check.py`

```sh
# Base = the Read You commit Brief You is built on (currently d2b979cc).
# Find it again with: git fetch https://github.com/Ashinch/ReadYou.git main && git merge-base HEAD FETCH_HEAD
FORK_WORKTREE=1 python3 .github/fork/fork_check.py check d2b979cc   # includes uncommitted edits
```

It must say "All changes to Read You's files are marked". Every CI build runs it too (as
warnings). About 67 Read You files have marked hooks today (MainActivity, FlowPage, FeedsPage,
ReadingPage, SyncWorker, RssHelper, the account services, AndroidManifest, build.gradle.kts, …).

**Read You updates:** the **`sync.yaml`** workflow runs daily (03:17 UTC), merges Read You's new
commits into a **`read-you-sync`** branch and opens/updates a pull request called *Read You
update*, with a merge-risk report (which hooked/copied files Read You changed). Nothing is built
or released until the owner merges it. If Read You changed a file listed in `copies.txt`, copy
the fix over into the Brief You copy by hand. If the merge conflicts, the run fails and lists the
files. For the PR to open by itself: *Settings → Actions → General → Allow GitHub Actions to
create and approve pull requests*.

---

## 4. Code map — `app/src/main/java/me/ash/reader/brief/`

| Folder | What's there |
| --- | --- |
| `ai/` | On-device AI. `GemmaSummarizer.kt`: loads Gemma through **LiteRT-LM** (`com.google.ai.edge.litertlm`, version in `libs.versions.toml`), GPU first, falls back to CPU and remembers a failed GPU for 30 days (`KEY_GPU_FAILED_AT`); loads in the background 2.5 s after the app comes to the foreground (`onAppForeground`, called from `BriefActivity`) if battery/memory allow, stays loaded while the app is open, unloads 3 minutes after the app leaves the screen; streams text. A request never waits forever: no first word within 3 minutes, or 60 s of silence after that, ends it with "The model stopped answering. Try again."; an answer the model didn't stop in time (`stuck`) is closed when it finally stops, and the next request waits up to 15 s for it, then says the model is still busy. `SummarySheet.kt` (the 70 % sheet with key-point cards, Ask, Regenerate, Read), `SummaryViewModel.kt` (article summary or list gist; gist = newest 40 unread), `AiSettingsPage.kt` (import the model file, edit the instructions). The model is **not bundled**: the user downloads `gemma-4-E2B-it.litertlm` (~2.6 GB) and imports it. `ModelDownload.kt`: the in-app download (a WorkManager job in the foreground, `dataSync`, resumable with Range, straight into `files/models`, then renamed into place) and `PhoneCheck` (memory size as sold, free space, mobile data). `ArticleText.kt`: the text the AI gets; a teaser (under ~1,500 characters) is swapped for the full article through Read You's `ReaderCacheHelper`. |
| `app/` | `BriefActivity.kt` (called from MainActivity: splash, in-window dialogs, shortcuts publishing, spare web view, Saved Articles refresh), `BriefNavigation.kt` (around Navigation 3: back preview, the shared filter bar, screen transitions), `BriefShortcuts.kt` (app shortcuts Triage / AI gist / Unread / Sync now, also used by the digest's Triage button), `BriefSync.kt` (after-sync work: filters, duplicates, icons). |
| `backup/` | Full backup/restore to one file (subscriptions, starred articles, filters, settings, AI instructions, custom icons, feed order, …). `AutoBackup.kt`: automatic backups (prefs `auto_backup`: frequency Off/Daily/Weekly, folder tree URI from the document picker with a persisted permission, keep 1–30, last time and size). `AutoBackupWorker` (periodic, needs charging and battery not low) calls `AutoBackup.run`, which writes `BriefYou-backup-<date>.zip` through `DocumentsContract` (no storage permission) and deletes older ones beyond *keep* (by name, the date sorts). The rows (`AutoBackupSettingItems`) sit under Back up / Restore on the account page; *Back up now* uses the same `run`. `AndroidApp` calls `AutoBackup.schedule`. |
| `data/` | `BriefArticleDao.kt`: Brief You's own Room queries (Read You's DB lists it with one line). |
| `feeds/` | Swipeable Starred/Unread/All pages on Feeds (`BriefFeeds.kt`, `FilterFeedLists.kt` — lists stay live with `SharingStarted.Eagerly` so returning never shows stale groups), feed/group sort (`FeedOrdering`, `FeedSort`), feed accent colours from icons (`FeedAccent`; a white or grey icon keeps its faint tint, muted, and a colourless article picture gives a muted reading page; an unreadable icon or picture keeps Brief You's own colours), custom feed icons (`FeedIconOverrides`, `FeedIconDialog`), the feed's own picture vs the website's icon (`FeedPictures`: noted by feed address in prefs `brief_feed_pictures` when a feed is found and on each local sync, from its RSS image / Atom logo, iTunes cover or Atom icon; `IconSourceButtons`, the joined "Feed's picture" | "Website icon" in Add a feed and Change icon; a chosen picture is kept as a `FeedIconOverrides` value (its address), so it survives syncs that rewrite icons; Add a feed's choice is noted by address and becomes an override at the next `reapply`; feeds added earlier get the choice once a local sync notes their picture), weekly retry for missing site icons (`IconLookups`). |
| `filters/` | Muted keywords and duplicate stories (`BriefFilters.kt`), the Filters screen with the "recently filtered" log and Undo. |
| `list/` | Article list additions: `BriefFlow.kt` (Triage/gist state), `FlowFab.kt` (AI gist / Triage pill), `InboxZero.kt`. |
| `offline/` | **Offline copies of starred articles** (Read later and Triage saves are starred too). `OfflineCopies.kt`: stores the full text (feeds set to Full content) in `files/offline/text` and each picture once, by address hash, in `files/offline/images` (+ `.type` file), with a per-article list in `lists/`; `prune` removes copies of unstarred articles and unused pictures. `OfflineWorker.kt`: WorkManager job (setting `OfflineCopies.Mode`: Off, Mobile, Wifi (default) or Both, chosen in Account → Syncing: `OfflineSettingsItem.kt`; Off cancels the job). `AndroidApp` watches the starred ids (debounced 2 s) and schedules it, so starring, old stars after the update, and app start all use one path. Offline use: `webview/WebViewClient.kt` serves a stored picture before the network; `ReaderCacheHelper.readFullContent` falls back to the stored text. Stored outside the cache on purpose (Android may clear caches). |
| `media/` | `ArticleMedia.kt`: an article's podcast episode or video, episode picture and length, and comments link (`ArticleExtras`), plus playback positions, in a small Room database of Brief You's own (`brief_media`, version 1, so Read You's tables and schema stay as they are). Filled by `FeedMedia.fromSyndEntry` (hooked into `RssHelper.buildArticleFromSyndEntry`: RSS enclosures, Media RSS, iTunes tags, `<comments>`) and `fromEnclosures` (the Google Reader sync's `enclosure` list). Articles from before: the audio or video file sits in `Article.img` (Read You took the first attachment as the picture), so `ArticleMedia.of` falls back to it, and `MediaFiles` keeps such files out of pictures (`FeedContent.listImage`, Triage, offline copies, `RssHelper.findThumbnail`). |
| `player/` | **The podcast and video player** (Media3 1.11). `PlayerService` (a `MediaSessionService`: ExoPlayer, its own media notification with back/forward 30 s, speed and stop as custom session commands; its session activity follows the playing article via `PlayerIntents`, which read aloud's notification uses too; `MainActivity` leaves an already open article as it is and keeps the list's filter for these; it keeps Read You's half-second wait before opening a different article: adding the new reading entry in the same frame as removing the old one put the list/reader scaffold's shared navigator in two transitions at once and crashed ("SeekableTransitionState has been used in different Transitions"); read aloud keeps `ReadAloudService`). `http://` media are played as `https://`; a playback error sets `State.error` (the card and sheet show Try again / Open in browser). Notification: ±30 in the back/forward slots, speed and stop as overflow custom commands; speed icons are our own number drawables for every speed (`SpeedIcons`, `ic_brief_speed_*`, made from Poppins Bold at one size), used by read aloud too. The service keeps the place itself on pause and stop. Each notification has its own tap link (`PlayerIntents.open(readAloud =)`). `play()` uses a token so a slower earlier load can't win. Media RSS `medium` isn't trusted for web pages or Flash (`MediaFiles.isPage`). A chosen feed picture follows the feed's new cover in `reapply`. Android's playback resumption: `onPlaybackResumption` returns the last item at its kept speed and attaches the app (`BriefPlayer.attach`) (`BriefPlayer.lastItem`, prefs `last_*`), with Media3's `MediaButtonReceiver` in the manifest; `BriefPlayer.adopt` takes over an item the app didn't start. Motion: the card's play button and wave only move while the card is on screen (`onGloballyPositioned`), the pill's button turns for 4 s after play (`spinFor`) and settles on one of the cookie's nine turns. The sleep timer's end moves on while paused. Sheets and full screen close when picture-in-picture starts. Video shapes are kept per article (prefs `brief_video_shapes`); `BriefSync.afterSync` forgets shapes, attachments and places of removed articles. The player's database exports its schema (CI uploads it as the `brief-schemas-<sha>` artifact; commit it to `app/schemas` when it changes, and write a migration instead of relying on the destructive fallback). The speed number icons come from `tools/make_speed_icons.py`. Only Brief You, its notification and Android's trusted controllers can connect to the player session. Feed-given addresses are web-only (`WebUrl`). One shared speed sheet and label (`brief/ui/SpeedSheet.kt`) for read aloud and the player. The bottom bar and video host watch only the parts of the player and read aloud state they need. Item details found while reading feeds are held in memory (`ArticleMedia.found` → `flush`, three tries 5 s apart) and only saved for items that became articles. Add a feed only keeps a feed's picture on subscribe (`FeedPictures.foundForAdding` / `keep`); deleting a feed removes its custom icon, image file and picture (`FeedIconOverrides.feedDeleted`). `BriefCleanup.sweepIfDue` (after a sync, as often as the account's Keep archived articles, monthly for Always) sweeps what else is gone: attachments/places/shapes of missing articles, the resume item, custom icons and image files of missing feeds, noted pictures of missing feeds, group icons and pictures of missing groups (deleting a group or choosing its website icon removes them at once), website-icon failures older than a week, Read You's saved full-text pages (`cache/readability/<account>/<sha256(id)>.html`) of missing articles, and what deleted accounts left (their pages, `cache/diff/<account>`, their `feed_order` key). Once after an update (not waiting for the sweep): the old feed-colour caches, and the `archived_article` notes of accounts that aren't local. **Cleared-article notes:** Read You keeps the link of every cleared article (`archived_article`) so a local feed doesn't add it again, and never forgot them. Now only local accounts keep them (`clearKeepArchivedArticles`), and `ArchivedLinks.tidy` (after each local feed's sync) forgets a link once the feed hasn't listed it for 5 syncs in a row and at least 3 days; a feed that lists nothing (couldn't be read) isn't counted. The counts are in the player database (`archived_miss`, version 2, an AutoMigration from 1). Read You's database is at version 9: `MIGRATION_8_9` adds the `archived_article.feedId` index. Version 8's schema file was never kept, and the 7 → 8 AutoMigration needs it once there is a 9: `app/schemas/…AndroidDatabase/8.json` is rebuilt from 7.json plus 8's two article indexes (its identity hash is a stand-in; only the tables matter for migrations). If Read You adds database versions of its own, renumber ours when merging. CI now uploads every schema (`brief-schemas-<sha>`); commit the new ones to `app/schemas`. **Opening an article with an episode or video doesn't mark it read** (`ArticleListReaderViewModel.markOpened`, `ArticleMedia.keepsUnread`); the player marks episodes and videos read past the chosen share. `BriefPlayer` (the app's side, a `MediaController`: state, play/resume from the saved position, speed in prefs `brief_player` (`speed` for podcasts, `speed_video` for videos), sleep timer with a fade, mark episodes and videos as read past the chosen share; pauses read aloud, and read aloud's `read`/`resume` pause it). `PlayerUi.kt`: the article's card (`ArticleMediaCard`, in `Content`'s `afterHeadline` slot), the speed sheet and the sleep timer sheet (separate, opening fully expanded; the speed pill and the moon are the same size), the Expressive `PlayButton` (Twine's: circle → turning `Cookie9Sided` morph, bouncy size, `primary` → `primaryContainer`), `MediaControls` (the other buttons spring in, each in its own weighted slot so play stays centred), `NowPlayingPill` + `NowPlayingSheet` (drawn above the shared bottom bar; `FilterBarSpace` keeps room for it so lists and the AI gist / Triage button move up; the pill's bottom padding is `FloatingBarGap - FILTER_BAR_TOP_PADDING` so the gaps above and below it are both 12 dp; it keeps its last content while sliding away). The reading bar's read aloud button stays read aloud. `VideoUi.kt`: `VideoFrame` (the video in its own shape, capped at 3:4, with PiP and full screen on its corner; in the card and in `NowPlayingSheet`, which takes the picture while open via `VideoWindow.inSheet`), full screen (a dialog, sideways) and picture-in-picture (`VideoHost` in `BriefRoot`; `MainActivity` handles size changes itself and supports PiP). `PlayerSettings.kt`: *Usage → Audio and video*. |
| `webview/YouTubeCards.kt` | **YouTube cards**: the reading web view replaces embedded YouTube players (`embed/`, `v/`, `shorts/`, also youtube-nocookie) with a card (thumbnail `i.ytimg.com/vi/<id>/hqdefault.jpg`; in the middle a wide play tile in `--link-text-color` with a "YouTube" chip under it; Videos corners/padding; no YouTube logo or red, which is their trademark) by script before the picture placeholders; a tap (and any YouTube watch link) opens a plain `ACTION_VIEW` so the YouTube app takes it. Why: YouTube refuses embeds without a Referer ("Error 153"), and the page is loaded with no base URL; giving it one would block `http` pictures (mixed content) and the phone font. The picture-tap script and placeholders skip `.brief-yt`. The text-only reader already did this (Read You's `VideoTagHunter`). |
| list position | **Back from an article** (single pane) the list pane is composed again, so `FlowPage`'s return effect runs every time: it leaves the list alone if the article's row is fully on screen, else jumps to it. It used to compare and jump by the paging item number, but with pinned (sticky) date headings a day's gap above its heading is a row of its own in the `LazyColumn`, so the list's row numbers run ahead: the row was never "on screen" and the jump landed a screen or so higher each time. It now finds the row by its key (the article id) and scrolls to the list's own row (`brief/list/ListIndex.kt` `lazyIndexOf`; two-pane uses it too). Nightly stutter log notes: "list: article opened from row", "list: shown at row", "list: moved by itself from … to …". |
| `notifications/` | **The digest** (`NotificationHelper.kt`): one notification per sync, stored in SharedPreferences, posted once after the sync. InboxStyle list, one line per feed ("Variety (5) · newest title"); **Android 16 shows at most 6 lines and ignores bold**, so >6 feeds = 5 lines + "+N more: …". Buttons: Mark all read (`DigestActionReceiver`) and Triage. |
| `readaloud/` | Text-to-speech by sentences with a media notification/lock-screen controls (`ReadAloudService`), progress ring buttons. |
| `reading/` | The reading page: colours from the article picture (`BriefReading.kt`), header, top bar, floating reading bar, next-article card, image viewer, reading time (`ReadingTime.kt`, one-pass counter), **`FeedContent.kt`** (tidies every article's HTML: complete URLs, removes share junk, lead-picture split; cached with `prepared()`, done off the main thread with `forReaderInBackground()`), **`EmailContent.kt`** (newsletters read as feeds: detects emails, turns layout tables into plain blocks, removes hidden preheaders/Outlook XML/pixels/social/footers, makes big text headings, no lead picture; `adjust()` is hooked into `RssHelper.buildArticleFromSyndEntry` for the list text, picture, author and the "view in browser" link), screen transitions (`PaneTransitions.kt`). |
| `readlater/` | **Read Later**: `ReadLaterActivity` (Share menu entry "Read Later": fetches the page, saves it starred+unread with full text into a never-synced **Saved Articles** feed, shows a sheet that closes after 3 s). `ReadLater.kt` does the saving. Local accounts only. The saved feed has URL `briefyou://saved-for-later`; `LocalRssService` and OPML export skip it. Its icon is `icon:bookmark`, drawn by `FeedIcon.kt`. |
| `splash/` | Animated splash (card stack). |
| `tour/` | `BriefTour.kt`: the first-run tour (replaces Read You's welcome page through one hook in `AppEntry`'s `Route.Startup`, which takes the tour off the back stack when you start, so back on Feeds closes the app; the tour (first run and from Tips & support) opens with a Restore page and then the AI page (the two with *Later*, together): Restore takes the zip from *Back up everything* (`BackupService.restore` through `TourEntryPoint`, the same as an account's *Restore from backup*) or, still, a settings JSON from Troubleshooting's *Export as JSON*; the agreement is on its last page) and a tour button in *Settings → Tips & support* (a full-screen dialog). `BriefHints.kt`: one-time hint bubbles (`BriefHint`, a `Popup` placed against its parent Box; one at a time; remembered in `brief_hints` prefs, with one shared change counter so every bubble sees *Got it*); `ShowHintsAgain` (under the round buttons in Tips & support) resets them. |
| `share/` | `Newspaper.kt`: an article as a newspaper front page (hold Share on the reading page, or the list's long-press menu). One `NewspaperMaker` lives in `BriefNavigationHost` (`LocalNewspaperMaker`); it runs the steps (full text via `ArticleText`, *In brief* via `summarizeForCard`, the page), shows `PressSheet` after 0.5 s, and takes the picture off screen. The picture is a toned black-and-white photo with a faint dot screen, drawn on the CPU; the page is straight, cut with a transparent zig-zag top and bottom, and captured with `OffscreenPictureAt` at a fixed 1600 px (a 400 dp page at 4×, font scale 1). `BriefYouMark` on shared pictures is the themed-icon glyph in the accent colour on a cookie; the newspaper's is black and white on a squircle. Masthead: Literata at weight 900. |
| `stats/` | Reading stats page and data; `MonthRecap.kt` is the monthly recap (Stats card, Past months, home row). `YearRecap.kt` is the year in review (first week of January in place of December's card, then first under Past months); it reuses the month card's heading, tiles and rows. Days also count opens per feed and filtered articles (for the recap). |
| `ai/Translation.kt` | **Translate an article** with Gemma. `TranslationState` (per article, reset when the article changes): parses the shown HTML with Jsoup, translates the title and the leaf text blocks (`p, h1–h6, li, figcaption, blockquote, td, th`) in batches as numbered paragraphs (`[3] text`, `GemmaSummarizer.translate`): a small first batch (450 characters) so words show quickly, then about 1,800. While the model writes, each paragraph that is complete (the next `[n]` has begun) replaces its original with `Element.text` (links and bold/italic words are sent as numbered tags `<1>…</1>`, and `rebuild` turns them back into the original elements; broken tags give plain text) and the HTML is published. The web view is given the page as it was when the run began (`shownHtml`, blocks marked `data-bt`) and each finished paragraph is swapped in with JavaScript from `patches` (`RYWebView`, via `LocalTranslation`), so the page never reloads; `htmlFor(webView)` picks it (the native reader gets the latest HTML). Blocks still waiting carry `opacity:.45` until translated or until the run ends (`generation` stops a replaced run publishing). `ReadingPage` shows `translation.html` / `title` instead of the article's while `showing`; `TranslationBanner` goes under the headline via `Content`'s `afterHeadline` slot; `TranslateController` is the top bar button (tap: translate, stop, or flip original/translation; hold: `TranslateLanguageSheet`). Language in prefs `translate` (default: the phone's). |
| `triage/` | **Triage** (`TriageScreen.kt`, the biggest file): card stack, swipe right read / left save / up open / down skip, undo, auto-advance with a wavy ring, next-group end screen, haptics. A run moves through groups (or feeds, when opened from a feed) in the Feeds order. In a group run the groups sheet is drawn like the Feeds page (tile, count pill, arrow); a group opens to its feeds and picking one calls `TriageSession.startFeedRun`, which turns the run into a run of feeds (state reset, the group kept in `previous` for Undo; the same call turns a feed run back into a group run when a group is picked in the feed run's sheet, which lists the groups with the current feed open; `Saved` also keeps `knownPlaces` and the start totals for that). `TriageSession` keeps the run: visited places, cards left behind per place (`leftIn`), sizes for the run bar, totals, and saved states so Undo can step back into the previous group. Header: "Triage" + a "2 of 5" chip (opens `TriagePlacesSheet`: jump, skip, reopen groups left with cards) + group · cards left. After a group, the bar becomes `RunBar` (one piece per group). `TriageSummary.kt`: long-press AI summary on a card. |
| `ui/` | `BackPreview.kt` (predictive back like stock Android, scrim 35 % dark / 12 % light), `SharedFilterBar.kt` + `BriefFilterBar.kt` (the bottom Starred/Unread/All bar drawn once above all screens), `InWindowDialog.kt`, `MaterialYouScheme.kt`, `TopBarButtons.kt`, `SharePicture.kt` (share a composable as a PNG: Triage card, monthly recap), `PressFeel.kt` (Triage's press for tiles and cards: squeeze, tighter corners, the cookie turns, spring back, matching Triage's Next card and buttons; used by the Filters and Reading stats tiles, the sort tiles and the recap cards), `Type.kt` (Google Sans Flex). |
| `update/` | **App update download** (GitHub build only). `UpdateDownload.kt`: like the AI model download, a WorkManager job with a progress notification that resumes after drops; saves to `cache/latest.apk` (the FileProvider path Read You already had), checks it's this app and newer, then a notification / the update dialog's Install button opens Android's installer. `UpdateDialog.kt` shows Download → percent → Install. `app/src/github/AndroidManifest.xml` adds `REQUEST_INSTALL_PACKAGES` (not in the Play build). `AppService.checkUpdate` picks the `.apk` asset of the release. `UpdateCheck.kt`: the "Check for updates when the app starts" switch in Tips & support (SharedPreferences, default on; `AndroidApp` respects it). `AndroidApp` no longer deletes `latest.apk` at start (that would remove a downloaded update): `readyApk` removes it only when stale. |
| `webview/` | The article web view (Brief You's copy): `RYWebView.kt` (styles cached with `remember`, one web view per article), `WebViewPool.kt` (a spare web view made at a quiet moment so opening an article doesn't stutter; closed web views freed after the animation), `ImagePlaceholders.kt` (boxes + Expressive shape-morphing loader while pictures load; small pictures centred). |

Other places worth knowing:
- `ui/component/FeedIcon.kt` (Read You file, hooks): emoji icons (`emoji:` prefix) and the
  Saved Articles bookmark (`icon:bookmark`).
- `ui/page/home/flow/FlowPage.kt` (hooks): the article list; contains the "don't scroll if the
  article is still on screen" fix.
- `domain/service/SyncWorker.kt` (hook): calls `BriefSync` after each sync. It and `WidgetUpdateWorker` reschedule from now when the next run is far beyond the interval (`brief/app/StaleSchedule.kt`; the clock was set ahead and back).
- `app/src/nightly/`: Nightly's own name/icon resources and its manifest (the sample-digest receiver).
- `app/src/main/baseline-prof.txt`: compiles all app code ahead of time on install.

---

## 5. Builds, versions and releases

There is **no local build in the assistant's environment**: GitHub Actions is the compiler.
(On a computer with Android Studio you can build normally: `./gradlew assembleGithubRelease`,
or `assembleGithubNightly`.)

| Workflow | When | What |
| --- | --- | --- |
| `build.yaml` "Build" | push to `main` (not when a push only changes documents: `.md` files, the README's pictures in `.github/assets` and `.github/readme`, issue templates, FUNDING or LICENSE) | Builds `githubRelease` and publishes a **GitHub release "Brief You 0.16.2.N"**, marked latest. N = this workflow's run number (also the versionCode). Release notes = the **last commit message on main** (so a merge commit with a full changelog becomes the release notes). |
| `test.yaml` "Test build" | push to **any other branch** (not `read-you-sync`; not documents-only pushes, same list as build.yaml) | Reuses build.yaml with `nightly: true`: builds **Brief You Nightly** and replaces the single **"nightly" prerelease** (its notes = that commit's message). |
| `sync.yaml` | daily | Read You update pull request (see §3). |
| `clean.yaml` | by hand only | Keeps only the latest of everything: the newest release (and the Nightly), the newest run of each workflow (none of removed workflows), the kept releases' tags, and branches with work not yet in main (merged branches are deleted; never `main` or `read-you-sync`). Starts as a dry run. |

Removed (Nov 2026): Read You's `build_pull_request.yaml` and `testing.yml` (they never ran: Read You update pull requests are opened by a workflow, which doesn't trigger others, and other branches are built by `test.yaml`), and `notification-preview.yml` with its script and the Nightly-only `DigestPreviewReceiver` (a one-off for the digest screenshots; in the git history if notifications change again).

**Version names:** Read You's version + build number: `0.16.2.9` = Read You 0.16.2, build 9.
Each build installs as an update (Obtainium works with the releases).

**Signing:** repository secrets `KEYSTORE_BASE64`, `KEYSTORE_PASSWORD`, `KEY_ALIAS`. Without them
builds are signed with Read You's public key (a warning in the build). **Never lose the keystore**
— without it, future releases can't update installed copies.

**Reading build results from the command line** (what the assistant did):

```sh
gh api "repos/Humorist2601/BriefYou/actions/runs?head_sha=$(git rev-parse HEAD)" \
  --jq '.workflow_runs[] | "\(.id) \(.name) \(.status) \(.conclusion)"'
# Compile errors appear as annotations on the failed job:
gh api repos/Humorist2601/BriefYou/actions/runs/<run id>/jobs --jq '.jobs[] | "\(.id) \(.conclusion)"'
gh api repos/Humorist2601/BriefYou/check-runs/<job id>/annotations --jq '.[] | "\(.path):\(.start_line) \(.message)"'
gh api repos/Humorist2601/BriefYou/releases --jq '.[0:2][] | "\(.tag_name) \(.name) \(.published_at)"'
```

The build also lists Kotlin warnings: **Brief You's own code should have 0 warnings** (Read
You's warnings are left alone on purpose).

**README pictures** are SVGs drawn with Google Sans Flex outlines and Material Symbols
(`tools/readme/make_assets.py`: hero, feature tiles, section headers; `tools/readme/make_media.py`:
the Podcasts and videos header and table, and the Listen and watch tile, in the same style). Run
them with an output folder, the Material Symbols rounded SVG folder (npm
`@material-symbols/svg-400`), `app/src/main/res/font/google_sans_flex.ttf` and
`.github/assets/icon-round.png`, then copy only the files you meant to change. The tables
(`t-*.svg`) of the other sections came from an earlier script that wasn't kept; `make_media.py`'s
`table()` draws the same layout.

**Usual flow for a change:**
1. `git checkout -b <topic>` from `main`; make the change; run the fork check; update README.
2. Ask the owner → commit → push → watch the Test build → the owner tries **Brief You Nightly**.
3. When approved: `git checkout main && git merge --no-ff <topic>` with a message like
   *"Brief You 0.16.2.N: …"* followed by a full changelog since the last release → push → watch
   the Build → report the release. Delete the merged branch.

**Planned next (agreed with the owner, November 2026), in this order:**
0. **Done (tested by the owner 16 Nov 2026): player round two, on `player`; merged to main with the approved changelog.**
   - Audio and video share one layout: seek row with the times beside it, then speed · back 30 ·
     play · forward 30 · sleep timer (both get speed and sleep). Audio card shorter (~176 dp:
     44 dp picture, 64 dp play). Video card: picture-in-picture and full screen as a small joined
     pair on the video's top corner, length in the other corner until it starts.
   - The pill keeps its picture; the full-controls sheet for a video shows the video large.
   - Videos keep their own shape: card, picture-in-picture (within Android's limits), full
     screen sideways only for wide videos.
   - Fixes: speed no longer carried from podcasts unless chosen for the video; a failed file
     shows "couldn't play" with Try again and Open in browser (also on the pill); `http://`
     addresses are tried as `https://` first (the app stays https only); opening
     picture-in-picture back into the app no longer pauses (pause only when the small window
     is closed, i.e. the activity is stopped); a finished episode's card no longer says
     "Picks up at" the end; no loading shape while paused.
   - Notifications: tapping the podcast or read aloud notification opens the playing article
     (and check the white overlay seen on the way); the podcast notification gets speed and
     stop like read aloud's.
   - Feed's picture or website icon: a small two-part joined button ("Feed's picture" |
     "Website icon", the one in use lit in `primaryContainer`, like the video player's joined
     buttons) under the address in Add a feed (only when the feed has its own picture; the
     icon at the top follows), and the same two-part button in place of Change icon's last
     button ("Use the website's icon"). Today the website's icon always replaces the feed's
     own picture.
   - Then: changelog approval, README picture for podcasts, merge to main.
1. **Done: one player for podcasts, read aloud and video** (mockups first), built on top of
   `background-loading`: Media3 in the background; podcasts and video get their own media
   notification, read aloud keeps its own (separate notifications, one thing playing at a
   time); the in-app controls and the "now playing" pill are shared; podcast
   episodes (the feed item's audio attachment, and `<audio>` in articles) and video files
   (video attachments, `<video>` in articles; YouTube stays in its own embedded player);
   speed, ±30 s skip, sleep timer, resume where you stopped, mark as read after a set share;
   a floating "now playing" pill above the bottom bar; video full screen and
   picture-in-picture, with Material 3 Expressive controls (wavy progress, shaped play button,
   joined button groups); its settings in *Settings → Usage → Audio and video*. Mockups:
   the "Brief You player mockups" canvas (claude.ai artifact). Fixes on the way: Read You takes a feed item's first attachment as its
   picture whatever its type (a podcast's MP3 became the list picture). Alongside it (from
   Twine, the owner's pick): a Comments button in the reader when a feed item links to its
   comments (RSS `<comments>`), sharing the player's database change. Then merge into `main`
   and release everything together, after the owner approves the changelog.
2. Update the dependencies on a new branch, one step at a time, each step a Nightly the owner
   tries before the next:
   - **A.** Build tools and Compose: Gradle 9.x, Android Gradle Plugin 9.5.x (its built-in
     Kotlin changes the build setup), Kotlin 2.4.x with KSP to match, compileSdk 37, Compose
     1.12 (BOM 2026.09.00 or newer), Material 3 1.5.0-rc01 (Expressive names and behaviour
     changed since alpha18: check every screen).
   - **B.** AndroidX libraries (Room, Paging, Lifecycle, Navigation, WorkManager, DataStore,
     Core, Activity, Browser, Media, Glance, Navigation 3, adaptive), OkHttp 5.x stable (it was
     on 5.0.0-alpha.12), Hilt, Telephoto, LiteRT-LM, Coil 2.7.
   - **C.** The big jumps, one at a time: Coil 3, Retrofit 3, AboutLibraries 15, MaterialKolor 5.
     Raising targetSdk (34) is a separate, tested step (edge-to-edge and back-gesture rules).
3. Optional: automatic update pull requests (Dependabot or Renovate) if the owner agrees. (A
   startup profile already exists: `app/src/main/baseline-prof.txt` covers all of the app's code;
   on a freshly installed Nightly it only takes effect after Android's background compiling.)

---

## 6. Pitfalls already hit (save yourself the time)

- **Kotlin trailing lambdas:** adding a parameter *after* a function's last lambda parameter
  breaks every call that uses trailing-lambda syntax (happened twice: `GroupItem`, and
  `backPreviewTransition { … }` → use named arguments).
- **`@Composable` calls inside `remember { }` don't compile** (e.g.
  `colorScheme.surfaceColorAtElevation(...)` is composable in this code base): compute outside.
- **There is no `R.string.app_name`** (the app name comes from the manifest/build). Use
  `context.applicationInfo.loadLabel(context.packageManager)`.
- **Maven Central rate limits** (HTTP 429) can hit dependency downloads from some environments;
  Google's mirror `https://maven-central.storage-download.googleapis.com/maven2/` works.
- **New libraries must match the Compose/Kotlin versions** (Compose 1.11.1 from the stable BOM 2026.05.00, Material 3 pinned to 1.5.0-alpha18, Kotlin 2.2). Compose 1.12+ needs AGP 9.1 and compileSdk 37.
  E.g. the Haze blur library 2.x/1.7.3 needed newer Compose; 1.6.10 was the compatible one.
  (Blur was tried and dropped at the owner's request.)
- **Notifications on Android 16:** InboxStyle shows **6 lines max** and **drops bold spans**;
  MessagingStyle shows only ~3 messages without bold. (A stock-emulator screenshot workflow, `notification-preview.yml`, was used for this; it's in the git history.)
- **Things the owner rejected (don't re-propose without reason):** predictive "card" back
  animation for closing an article (caused issues — closing an article uses the 0.16.2.6
  behaviour), a blurred top/bottom bar, AI preloading, a ranked "daily briefing", stories mode,
  catch-up mode, listen mode, recap/quiz, notebook, a Delete button for saved articles (unstar +
  "Keep archived articles" removes them).
- **Saved articles (Read Later)** are starred, so "Keep archived articles" never removes them
  until they're unstarred and read.
- The **"Read You update"** PR can change files Brief You copied (`copies.txt`): port the fix.
- **A draw modifier that wraps a whole list redraws every row when it changes.** The scrollbar
  thumb (shows on open, moves as rows arrive, fades after 0.8 s) re-recorded the whole article
  list each time: 10–33 ms draw frames when opening a list. The scrolled content now sits in its
  own `graphicsLayer()` under the thumb (`VerticalScrollIndicator.kt`). Anything drawn over a big
  list or page that animates needs the same.
- **The article's web view must never scroll itself.** It sits inside the reading page's scroll at
  the article's full height; while the article grows it can be briefly shorter than its text, and a
  quick fling then scrolled the text inside it, under the heading. `WebViewLayout` puts it back at
  the top and asks for its height again.
- **Never create a value inside `CompositionLocalProvider(X provides …)`.** Read You's list pane
  handed down `Executors.newSingleThreadExecutor()` written in the provider: each recomposition of
  the pane (every tap, slide and back step) made a new executor, a new value for the whole list,
  so every visible row was rebuilt (and a thread was left behind). It's `remember`ed once in
  `ArticleListReaderPage` now. A changed handed-down value disables skipping for everything below
  (rows rebuilt with none of their own inputs changed are the sign of it).
- **Moving pictures inside the article web view are repainted every frame.** The pictures'
  loading shapes (an animated SVG) slowed scrolling and left blank frames on picture-heavy pages;
  they hold still while the page scrolls (`brief-scrolling` class, toggled from `Content`'s scroll
  state in `RYWebView`).
- **Restyling a long article repaints all of it** (100–240 ms frames). A picture colour that
  arrives after the article was handed to its web view only fades the screen around it; the
  article keeps the colours it opened with (`BriefReadingTheme`).
- **The first article after a start is cold.** A list's first three articles are tidied ahead
  (`FeedContent.prepareAhead`, cached by `forReaderInBackground`); tidying a tiny made-up article at
  start didn't warm it enough (removed). Don't warm the spare web view
  by loading a page into it: tried in 0.16.2.119, it flashed white on the first article and made
  its frames slower (99–162 ms), without making the page load sooner.
- **`ModalBottomSheet` opens its own window**; making and tearing it down costs 30–60 ms frames on
  the main thread. Triage's places sheet uses `brief/ui/InWindowSheet.kt` (`InWindowModalSheet`:
  Material's in-window `BottomSheet` + `Scrim`, same look and gestures) — it must sit in a
  full-window parent.
- **Lists are prepared on the press.** `FeedsPage` → `FeedsViewModel.warmList` →
  `ArticlePagingListUseCase.prepare(filterState)`: the pager is made (own scope) and its first page
  read while the finger is down; the filter change on tap adopts it when equal, so `LazyPagingItems`
  shows the cached rows on its first composition (Paging 3.3 `cachedPagingData`). A press that isn't
  opened has its scope cancelled. Rows reuse their article when a reload brings an equal one
  (`remember(item.articleWithFeed)` in `ArticleList`), each row is one accessibility node, and the
  long-press menu is built only once opened.
- **Articles are prepared on the press** (`brief/reading/ArticlePress.kt`, called from the row's
  pointer handler in `SwipeableArticleItem`): the view model's `prepareArticle` reads the article
  and tidies its text (taken by `initData` when the same one opens), the top picture is fetched
  into Coil's cache at the page's size, and the picture's colour is worked out (the reading theme
  uses a known colour from its first frame, no fade).
- **Article pictures are answered at once** (`brief/webview/WebViewClient.streamImage`): fetched on
  a small pool and streamed in. Waiting in `shouldInterceptRequest` held up the page's other
  requests (the reading font too: text invisible for seconds on picture-heavy pages).
- **Callbacks made inside a `LazyListScope` builder are new every time** (it isn't a composable,
  so nothing is remembered): every row recomposes. Make them in the composable (`onArticleClick`
  in `FlowPage`).
- **Don't build `Regex(...)` or Coil `ImageRequest`s per row per recomposition** — build once
  (`extractDomain`) / `remember` them (`RYAsyncImage`).
- **Nightly stutter log:** Tips & support → *Copy stutter log* (`brief/perf/PerfLog.kt`): slow
  frames split by part, plus main-thread samples while stuck, plus "what happened" markers
  (`PerfLog.note`: new pager, list rows, row built, slide done, feed colour ready). On Android
  "draw" includes Compose measure/layout and building lazy rows; "wait before" = main thread busy
  before the frame.

---

## 7. How to investigate problems (techniques that worked)

- **Picker icons** (`feeds/SymbolIcons.kt`) are Material Symbols (outlined, fill 0), stored as path data in `SymbolPaths.kt` and drawn with `ImageVector.Builder`; the stored `icon:<name>` keys did not change. (The "outlined" Material Icons have solid shapes, such as the paw, globe and note.) New icons: take the path from the `symbols/android/<name>/materialsymbolsoutlined/<name>_24px.xml` file of google/material-design-icons.
- **Number settings** use `brief/ui/NumberSheet.kt` (− / + stepper, slider, Custom value, Reset; `onReset` for settings that follow another, like the video ones). **Triage auto-advance** settings (action and delay, `TriageAuto` in `triage/TriageAuto.kt`, shown on the Usage page) share the `triage` SharedPreferences with the long-press delay menu; the screen reads the action once when it opens.
- **Reading page picture/video settings in the web view:** `brief/webview/MediaStyle.kt` adds the CSS Read You's `WebViewStyle` doesn't have (Maximize for pictures, the Videos page's corners/padding/maximize; `SAME_AS_IMAGES` = -1 means follow the Images values); subhead alignment is `--subhead-align` in `WebViewStyle`. The video preferences are in `ReadingVideoPreferences.kt` and registered in `DataStoreExt.kt`. CSS class names: `brief-box` is the picture placeholder (`ImagePlaceholders`), `brief-note` is a newsletter's boxed note — keep them apart.
- **Newsletter tidying** (`EmailContent.kt`) can be tried on a real email's HTML without a phone: copy the file into a small JVM Gradle project with `org.jsoup:jsoup:1.11.3`, stub `Article`, and print `tidy(doc, title)`. HTML of an email: politicalemails.org/messages/<id>/html_body (links are replaced by `#` there). The extras (buttons, boxes, dividers, logo and repeated-headline removal, kept "Read more") only run when the **Tidy newsletters** setting is on (`EmailContent.tidyMore`, loaded in `AndroidApp.onCreate`, switch in `NewsletterSettingItem.kt` on the Reading page). Buttons, bordered boxes and divider lines are noted from the email's styles before they are stripped, then styled by classes in `WebViewStyle.kt`; `forReader(html, link, title)` takes the title to drop a repeated headline.
- **Stutters / animation bugs:** ask the owner for a **screen recording**. Extract frames and
  timestamps with ffmpeg (`ffprobe -show_entries frame=pts_time`), compute frame-to-frame
  differences; a gap of > 12 ms between frames during an animation at 120 Hz is a dropped frame.
  Contact sheets (`ffmpeg -vf "fps=6,scale=160:-1,tile=10x4"`) show what happened when.
  Past causes: building a web view or heavy strings in the first frame of a transition, work on
  the main thread during an animation, three lists built at once, database writes mid-animation,
  stale data swapping in after returning to a screen, a list scroll jump after closing an article.
- **How the lists load (Brief You):** list and Triage queries leave the article text out
  (`'' AS rawDescription, NULL AS fullContent` in `ArticleDao`'s paging queries); the text is
  read by id where it is needed (opening an article in `ArticleListReaderViewModel.initData`,
  `ArticleText.withText`/`withTextOf` for the AI and the newspaper). Never write a list item back
  to the database whole. Reading times are worked out once per article in the background and
  kept in `files/reading_minutes.txt` (`ListReadingTime`). Triage loads 200 cards at a time and
  carries on after the rows used (`TriageSession.rowsUsed`); pressing a place in its sheet or a
  feed/group on the Feeds page starts loading on the finger-down (`brief/ui/FingerDown.kt`).
  The list screen's state is ready from its first frame and never the previous feed's list
  (`ArticleListReaderViewModel.flowUiState`).
- **Database versions:** version 8 adds the `(accountId, isUnread, date)` and
  `(feedId, isUnread, date)` article indexes (`AutoMigration(7, 8)`, built on the first start
  after the update). The list queries use a fixed `ORDER BY date DESC`, with `…Ascending` twins
  for the Unread sort setting, so the indexes can be used. Commit `app/schemas/…/8.json` from a
  local build when one is available.
- **Speed of pure code** (no phone needed): copy the Kotlin file into a small JVM Gradle project
  and time old vs new (done for reading time, FeedContent, the picture-loader CSS).
- **Notifications:** the removed `notification-preview.yml` (git history) ran a stock emulator and took screenshots.
- **Visual previews for the owner:** small HTML mock-ups rendered to PNG with Playwright/Chromium,
  or Claude's design canvas.

---

## 8. Starting a new chat (copy and paste)

> I maintain **Brief You**, an Android RSS reader forked from Read You:
> https://github.com/Humorist2601/BriefYou. Read `HANDOVER.md` and `README.md` in the repository
> first and follow the rules in HANDOVER.md §2 (ask before committing, previews before UI
> changes, keep the README updated, watch every build to the end, plain language).
> The problem / change I want: **[describe it; attach a screen recording or screenshot for
> anything visual]**. My phone: Pixel 9 Pro, GrapheneOS (Android 16), Local account.

The new assistant needs access to the repository (and, to push and watch builds, a GitHub
token or the GitHub CLI logged in as the owner).

## Security notes

- **Certificates are checked.** `cachingHttpClient` used to trust every certificate by default (Read You's behaviour); it now checks them normally. A self-hosted sync server with a self-signed certificate needs an option added before it can work again.
- **Scripts inside articles are blocked.** The reading web view's page (`brief/webview/BriefWebViewHtml.kt`) carries a content policy (`script-src 'nonce-…'`) with a new one-time key on each load; only the app's own `<script>` has it. Scripts the app runs from outside (`evaluateJavascript`: picture taps, Translate swaps) are not affected. Inline `onerror` handlers are blocked too, so a picture's fallback address is a `data-fallback` attribute that `ImagePlaceholders.SCRIPT` reads. Anything new that needs a script inside the page must go in that script or in `evaluateJavascript`.
- **Builds never use Read You's public signing key.** `build.yaml` stops when the `KEYSTORE_BASE64` secret is missing.
- **Cloud backup leaves out the database, the AI model and the offline copies** (`res/xml/backup_rules.xml`, `data_extraction_rules.xml`).
- **The AI model download is fixed to one commit and one SHA-256** (`ModelDownload`: `COMMIT`, `SHA256`); the file is checked before it is installed. To use a newer model, change both together.
