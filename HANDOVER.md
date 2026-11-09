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
   Article lists: `brief/list/ListCard.kt` (`LocalListCard` set per row by Read You's `ArticleList`
   from its neighbours: each day one joined card; `LocalSingleFeedList` hides the feed name on a
   feed's own list). Feeds page: groups joined via `GroupWithFeedsContainer(shape, top)`, with
   `GroupIconTile` and `CountPill` (`brief/feeds/GroupTile.kt`). Empty lists: `EmptyListState`
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
| `ai/` | On-device AI. `GemmaSummarizer.kt`: loads Gemma through **LiteRT-LM** (`com.google.ai.edge.litertlm`, version in `libs.versions.toml`), GPU first, falls back to CPU and remembers a failed GPU for 30 days (`KEY_GPU_FAILED_AT`); loads in the background 2.5 s after the app comes to the foreground (`onAppForeground`, called from `BriefActivity`) if battery/memory allow, stays loaded while the app is open, unloads 3 minutes after the app leaves the screen; streams text. `SummarySheet.kt` (the 70 % sheet with key-point cards, Ask, Regenerate, Read), `SummaryViewModel.kt` (article summary or list gist; gist = newest 40 unread), `AiSettingsPage.kt` (import the model file, edit the instructions). The model is **not bundled**: the user downloads `gemma-4-E2B-it.litertlm` (~2.6 GB) and imports it. `ModelDownload.kt`: the in-app download (a WorkManager job in the foreground, `dataSync`, resumable with Range, straight into `files/models`, then renamed into place) and `PhoneCheck` (memory size as sold, free space, mobile data). `ArticleText.kt`: the text the AI gets; a teaser (under ~1,500 characters) is swapped for the full article through Read You's `ReaderCacheHelper`. |
| `app/` | `BriefActivity.kt` (called from MainActivity: splash, in-window dialogs, shortcuts publishing, spare web view, Saved Articles refresh), `BriefNavigation.kt` (around Navigation 3: back preview, the shared filter bar, screen transitions), `BriefShortcuts.kt` (app shortcuts Triage / AI gist / Unread / Sync now, also used by the digest's Triage button), `BriefSync.kt` (after-sync work: filters, duplicates, icons). |
| `backup/` | Full backup/restore to one file (subscriptions, starred articles, filters, settings, AI instructions, custom icons, feed order, …). |
| `data/` | `BriefArticleDao.kt`: Brief You's own Room queries (Read You's DB lists it with one line). |
| `feeds/` | Swipeable Starred/Unread/All pages on Feeds (`BriefFeeds.kt`, `FilterFeedLists.kt` — lists stay live with `SharingStarted.Eagerly` so returning never shows stale groups), feed/group sort (`FeedOrdering`, `FeedSort`), feed accent colours from icons (`FeedAccent`), custom feed icons (`FeedIconOverrides`, `FeedIconDialog`), weekly retry for missing site icons (`IconLookups`). |
| `filters/` | Muted keywords and duplicate stories (`BriefFilters.kt`), the Filters screen with the "recently filtered" log and Undo. |
| `list/` | Article list additions: `BriefFlow.kt` (Triage/gist state), `FlowFab.kt` (AI gist / Triage pill), `InboxZero.kt`. |
| `notifications/` | **The digest** (`NotificationHelper.kt`): one notification per sync, stored in SharedPreferences, posted once after the sync. InboxStyle list, one line per feed ("Variety (5) · newest title"); **Android 16 shows at most 6 lines and ignores bold**, so >6 feeds = 5 lines + "+N more: …". Buttons: Mark all read (`DigestActionReceiver`) and Triage. |
| `readaloud/` | Text-to-speech by sentences with a media notification/lock-screen controls (`ReadAloudService`), progress ring buttons. |
| `reading/` | The reading page: colours from the article picture (`BriefReading.kt`), header, top bar, floating reading bar, next-article card, image viewer, reading time (`ReadingTime.kt`, one-pass counter), **`FeedContent.kt`** (tidies every article's HTML: complete URLs, removes share junk, lead-picture split; cached with `prepared()`, done off the main thread with `forReaderInBackground()`), **`EmailContent.kt`** (newsletters read as feeds: detects emails, turns layout tables into plain blocks, removes hidden preheaders/Outlook XML/pixels/social/footers, makes big text headings, no lead picture; `adjust()` is hooked into `RssHelper.buildArticleFromSyndEntry` for the list text, picture, author and the "view in browser" link), screen transitions (`PaneTransitions.kt`). |
| `readlater/` | **Read Later**: `ReadLaterActivity` (Share menu entry "Read Later": fetches the page, saves it starred+unread with full text into a never-synced **Saved Articles** feed, shows a sheet that closes after 3 s). `ReadLater.kt` does the saving. Local accounts only. The saved feed has URL `briefyou://saved-for-later`; `LocalRssService` and OPML export skip it. Its icon is `icon:bookmark`, drawn by `FeedIcon.kt`. |
| `splash/` | Animated splash (card stack). |
| `tour/` | `BriefTour.kt`: the first-run tour (replaces Read You's welcome page through one hook in `AppEntry`'s `Route.Startup`; the agreement is on its last page) and a tour button in *Settings → Tips & support* (a full-screen dialog). `BriefHints.kt`: one-time hint bubbles (`BriefHint`, a `Popup` placed against its parent Box; one at a time; remembered in `brief_hints` prefs, with one shared change counter so every bubble sees *Got it*); `ShowHintsAgain` (under the round buttons in Tips & support) resets them. |
| `share/` | `Newspaper.kt`: an article as a newspaper front page (hold Share on the reading page, or the list's long-press menu). One `NewspaperMaker` lives in `BriefNavigationHost` (`LocalNewspaperMaker`); it runs the steps (full text via `ArticleText`, *In brief* via `summarizeForCard`, the page), shows `PressSheet` after 0.5 s, and takes the picture off screen. The picture is a toned black-and-white photo with a faint dot screen, drawn on the CPU; the page is straight, cut with a transparent zig-zag top and bottom, and captured with `OffscreenPictureAt` at a fixed 1600 px (a 400 dp page at 4×, font scale 1). `BriefYouMark` on shared pictures is the themed-icon glyph in the accent colour on a cookie; the newspaper's is black and white on a squircle. Masthead: Literata at weight 900. |
| `stats/` | Reading stats page and data; `MonthRecap.kt` is the monthly recap (Stats card, Past months, home row). `YearRecap.kt` is the year in review (first week of January in place of December's card, then first under Past months); it reuses the month card's heading, tiles and rows. Days also count opens per feed and filtered articles (for the recap). |
| `triage/` | **Triage** (`TriageScreen.kt`, the biggest file): card stack, swipe right read / left save / up open / down skip, undo, auto-advance with a wavy ring, next-group end screen, haptics. A run moves through groups (or feeds, when opened from a feed) in the Feeds order. `TriageSession` keeps the run: visited places, cards left behind per place (`leftIn`), sizes for the run bar, totals, and saved states so Undo can step back into the previous group. Header: "Triage" + a "2 of 5" chip (opens `TriagePlacesSheet`: jump, skip, reopen groups left with cards) + group · cards left. After a group, the bar becomes `RunBar` (one piece per group). `TriageSummary.kt`: long-press AI summary on a card. |
| `ui/` | `BackPreview.kt` (predictive back like stock Android, scrim 35 % dark / 12 % light), `SharedFilterBar.kt` + `BriefFilterBar.kt` (the bottom Starred/Unread/All bar drawn once above all screens), `InWindowDialog.kt`, `MaterialYouScheme.kt`, `TopBarButtons.kt`, `SharePicture.kt` (share a composable as a PNG: Triage card, monthly recap), `PressFeel.kt` (Triage's press for tiles and cards: squeeze, tighter corners, the cookie turns, spring back, matching Triage's Next card and buttons; used by the Filters and Reading stats tiles, the sort tiles and the recap cards), `Type.kt` (Google Sans Flex). |
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

**Usual flow for a change:**
1. `git checkout -b <topic>` from `main`; make the change; run the fork check; update README.
2. Ask the owner → commit → push → watch the Test build → the owner tries **Brief You Nightly**.
3. When approved: `git checkout main && git merge --no-ff <topic>` with a message like
   *"Brief You 0.16.2.N: …"* followed by a full changelog since the last release → push → watch
   the Build → report the release. Delete the merged branch.

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
- **New libraries must match the Compose/Kotlin versions** (Compose BOM 2025.10, Kotlin 2.2).
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

---

## 7. How to investigate problems (techniques that worked)

- **Stutters / animation bugs:** ask the owner for a **screen recording**. Extract frames and
  timestamps with ffmpeg (`ffprobe -show_entries frame=pts_time`), compute frame-to-frame
  differences; a gap of > 12 ms between frames during an animation at 120 Hz is a dropped frame.
  Contact sheets (`ffmpeg -vf "fps=6,scale=160:-1,tile=10x4"`) show what happened when.
  Past causes: building a web view or heavy strings in the first frame of a transition, work on
  the main thread during an animation, three lists built at once, database writes mid-animation,
  stale data swapping in after returning to a screen, a list scroll jump after closing an article.
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
