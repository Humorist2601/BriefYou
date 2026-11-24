# Brief You — design basis

The **Home screen** (branch `screens/home`, Nightly 1.0.19 onward) is the owner's approved design and the
**basis for every screen designed after it**. When you design or change a screen, start from what is written
here, reuse the same components and tokens, and only add something new when nothing here fits. If you think
Home itself should change, say so and ask first: the owner signed this off on a real phone.

The owner's working rules (mock-up first, ask before committing, README note) are in
[HANDOVER.md §2](HANDOVER.md#2-how-to-work-on-it-rules-the-owner-set). This file is only about how it looks, moves and reads.

## 1. Principles

1. **Calm and finished.** Nothing blinks, nothing nags. Motion is slow or springy, never busy.
2. **Say each thing once.** A number or fact appears in one place on a screen (the hero says what is in
   your articles, the row below says how many feeds and groups there are). Check every new sentence against
   what is already on screen.
3. **One system, no one-offs.** Same corner scale, same tonal colours, same press feel, same fades everywhere.
4. **Honest states.** Every state is real (syncing, offline, failed, first sync, empty). No placeholder
   that claims something is true when it is not (a first sync is "first sync", not "caught up").
5. **Material 3 Expressive**, with the app's own touches below. Use theme colours and the app's type;
   no hard-coded colours except the white/black tints of the hero's background shapes.

## 2. Layout (Home, top to bottom)

| Part | Rule |
|---|---|
| Top bar | Settings circle (40 dp) at the left; Sort, Filters and Add as one connected pill of 48 × 40 dp buttons at the right (`TonalCircleButton`, `TopBarButtonGroup`). The account name moves up into the bar as the page scrolls (collapsing title). Red dot on Settings = app update. |
| Page title | `DisplayText`: the big "Brief You" (the account name), tappable to switch account. About 48 dp of space above it. |
| Hero card | One card per page, 16 dp from the edges. See §4. |
| Feed row | 30 dp side padding (lines up with the groups' chips). Big feed count (headlineSmall, bold, onSurface) + "feeds in N groups" (labelLarge, onSurfaceVariant, baseline-aligned) on the left; a **40 × 32 dp** round pill (surfaceContainerHigh) with the unfold-more / unfold-less icon on the right (the two icons cross-fade and scale). |
| Groups | One connected list. Rows are `groupShape(index, count)`: big corners (24 dp) on the outside, small (6 dp) between, a 3 dp gap. A group row: icon + capital label, then the big count; up to **4** overlapping feed icons then a "+N" circle, then a round chevron chip. An open group's container uses the **same colour as the group row** (surfaceContainerHigh), so it reads as one card; feed rows inside sit on `surface` with a surfaceContainerHigh count chip. |
| Bottom bar | A floating glass-style pill, 64 dp high, 16 dp above the system bar (`BriefFilterBar`, `BarScreenOffset`). Three filters (Starred, Unread, All); the selected one is an accent pill with its name; the pill follows a swipe between pages and a finger dragged along the bar, ticks at each filter, and wobbles a little (kept small). |
| Edges | The list fades into the bars at both edges with the same S-curve (`edgeFadeBrush`): 28 dp under the top bar, and the height of the bottom bar + offset + navigation bar + 14 dp at the bottom. Never a straight-line fade, never a visible edge. A brush drawn into a smaller rectangle needs its own height. |

Corner scale (`Corner`): Inner 6, Small 16, Card 24, Tile 28, Banner 32 dp. Buttons, chips and pills are fully round.
Group corners: `Group.Outer` 24 / `Group.Inner` 6 / `Group.Gap` 3 (nested lists inside smaller things: 16 / 4).

## 3. Colour and type

- Surfaces: the page is `surface`; cards and rows are `surfaceContainerHigh`; quiet chips and pills inside them are
  `surfaceContainerHigh` or `surfaceContainer`. Text: `onSurface` for the main fact, `onSurfaceVariant` for support,
  `primary` for section labels.
- Hero: `bannerContainer()` (primaryContainer in dark, primaryFixed in light) with a **diagonal gradient** from that
  colour toward `surface` (dark, 35%) or `primary` (light, 22%). Content colour is `contentColorFor(container)`.
- Errors only for real problems: `errorContainer` / `onErrorContainer` chips ("failed · retry", "sign in").
- Type: the page title and the hero's big word use Google Sans Wide (ExtraBold, 22 sp for the hero word); everything
  else is the app's normal font. The hero count is the normal font, **plain bold**, auto-sized to fit the cookie.
  Chip text is labelMedium SemiBold with equal-width digits.
- Words are lower-case sentences ("mostly in Technology"), numbers in the sentence are bold, a group or feed name is
  under a thin wavy line.

## 4. The hero card

- Shape: rounded 32 dp, shrinking to 18 dp while pressed (press feel, §6). Padding 14 / 16 / 16 dp.
- Top row (28 dp high): greeting icon + greeting in capitals (morning, afternoon, evening, night) at the left; one chip at the right.
- Body: a **92 dp cookie** (104 dp for five or more characters) holding the count (a nine-sided Material cookie,
  still, turns a little when pressed), then the big word and one sentence of up to two lines.
- Background: three faint Material shapes turning slowly (a 12-sided cookie top right, a pill bottom left, a rounded square between;
  36 s, 28 s reversed, 44 s per turn). Drawn once and only the layer turns. They **pause** while a sheet is open or the app is not in front.
- Chip: ink at 12% on the card; while syncing it fills left to right (ink 32%) with "17 of 40" and a spinning icon, and keeps
  the width of its longest text. Error chips use the error colours.
- A tap gives the app's click sound and a short context-click vibration, then opens the article list (or Add feed when there are no feeds).

### States (first match wins)

1. **Setup:** no feeds ("+" cookie, "add a feed"); counting (dots, "counting…"); first sync (dots, "first sync", "getting your articles…").
2. **Sync and connection** (replace the sentence and chip, in this order): syncing; sign-in expired (red, tap opens the account, accounts with a server only);
   offline (also a Wi-Fi without internet); sync failed (red, tap retries).
3. **Unread page:** caught up (tick) · new since you last opened · oldest is N days old (30 or more) · mostly from <feed> (one group) ·
   mostly in <group> (that group holds at least 40%) or <group> has the most · in <group>.
4. **Starred page:** the same group wording; nothing starred (star icon, "no stars yet").
5. **All page:** "articles", "N came out today" (or "nothing new today").
6. **Chip when nothing is wrong:** the age of the last sync ("just now", "5m ago", "2h ago", "3d ago"); grey with a cloud-off icon and
   "synced …" when it is more than twice the sync interval old (at least an hour; never with manual sync).

Not wanted (the owner found them too much): reading-time, streak, recap and trend sentences. Keep the card to these states.

## 5. Writing

- Short, lower-case, plain. No exclamation marks, no emoji.
- A sentence has to add something the screen does not already show. No repeated counts.
- Keep sentences to one line where possible, so a state change never makes a card taller or shorter.

## 6. Motion and feel

- **Press feel** (`rememberPressFeel`): cards and tiles squeeze (96%, a gentler 98% for big cards) and their corners tighten, with a springy
  return (damping 0.45, medium-low stiffness). Use it on every tappable card or tile.
- Icons that swap cross-fade and scale (0.6 → 1), not cut.
- Springs, not linear tweens, for anything that responds to a finger. Slow linear turns only for the background shapes.
- Haptics: a tick at each filter while dragging the bottom bar; a context click on the hero and other main taps. Nothing buzzes on its own.
- Pause anything that animates forever while a sheet is open or the app is in the background.

## 7. Building a new screen from this

1. Reuse: `groupShape` lists, `Corner` sizes, `TonalCircleButton` / `TopBarButtonGroup`, `DisplayText` title + collapsing title,
   `edgeFadeBrush`, `rememberPressFeel`, `bannerContainer()`, the hero's chip and cookie parts.
2. Start every screen with a mock-up in light **and** dark and show it before building.
3. List the screen's real states (empty, loading, error, offline) and design each, as Home does.
4. Check the screen against §1 (say each thing once) before showing it.
5. Test on the Nightly; send a screenshot back; fix differences from the mock-up.
6. Add the user-visible part to the README in the same commit.

## 8. Credit

The floating bottom pill's movement (`NavPill.kt`) and the card gradient idea come from Trainable (GPL-3.0); keep their credit comments.

## 9. The rest of the app: what is left to redesign

Home is done. Everything below is still to do, on the branch `screens/home`, one surface at a time. The merge to `main`
is the **last step**, after the whole design is done and the owner says so. Do not merge earlier.

**Order (agreed as a suggestion; the owner picks each next one):**

1. **Shared sheet and dialog building blocks** (`brief/ui`: `OptionSheet`, `ChoiceSheet`, `NumberSheet`, `SpeedSheet`, `SheetParts`,
   `InWindowSheet`, `InWindowDialog`; `ui/component/base`: `BYDialog`, `RadioDialog`, `TextFieldDialog`, `BottomDrawer`).
   Restyle these once and about 23 sheets and 55 dialogs follow.
2. **Article list** (`ui/page/home/flow`, `brief/list`): list, day headers, Read all bar, search, pull to sync / load. It is where the hero card leads.
3. **Reading page** (`brief/reading`): header, top bar, reading bar, images, extras.
4. **Settings pages** (about 20 routes under `ui/page/settings` plus Filters, Notifications, Reading stats, AI model): reuse the joined group cards.
5. **Triage** (`brief/triage`): has its own style; the owner decides how much to change.
6. **Startup and first run** (splash, restore backup, AI check, tour, hints).
7. **Player** (now-playing pill and card, controls, video), **recaps** (month and year), **Newspaper** share page.

**Surfaces to ask about first** (partly outside the app's own UI): the two home-screen **widgets** (`ui/widget`) and the digest
**notification**. They can only follow the design in wording, colour and icon.

**For every surface:** list its real states (empty, loading, error, offline), mock it in light and dark, show it, build it on the
Nightly, get a screenshot back, fix differences, add the README note. Dialogs and sheets are designed as one family first
(item 1), then checked on each screen that uses them.

## 10. Sheets: one head, one body, one foot

The language every bottom sheet follows, taken from Home (the hero's cookie, the joined group cards, the Banner corner, primary section
labels, the edge fades, the press feel). Official Material 3 values were not fetchable (the spec site needs JavaScript), so the container
values below are the Compose defaults the app already uses. Dialogs are in §10.6.

### 10.1 Sheet or dialog

- **Sheet:** content tied to the screen you are on, longer lists, sliders and text editing: Account switcher, Sort, Filters, Edit group / feed,
  AI gist / summary and its prompt, Translate, Reader settings, Stats month, Now playing, number / speed / sleep timer, Triage places. It
  applies as you change it and closes by swipe, scrim or back. **A sheet is never taller than 70% of the screen**: it is as tall as its
  content up to that (`Modifier.sheetMaxHeight()`), and what does not fit scrolls inside with the edge fade (`sheetScrollFade`).
- **Dialog:** every settings choice (a short list you pick from and are done: the setting's own icon on the cookie, joined rows, a scroll with
  the edge fade inside if long), confirmations, one short text, notices, the Add account forms, Change icon and the Newspaper's
  progress. Tapping the dim area closes every dialog (Add feed too). A dialog opened from a sheet opens **above** it. A sheet never opens
  a sheet, and a dialog never opens a sheet.

### 10.2 Container

- `ModalBottomSheet` with `SheetShape` (top corners **Banner 32 dp**), Material's `surfaceContainerLow`, default scrim and handle, no divider,
  centred and at most 640 dp wide on a tablet. Full-height state only (`rememberFullSheetState`); the sheet wraps its content.
- Cards inside are `surfaceContainerHigh` (as Home's cards on `surface`); quiet pills and fields inside cards are `surfaceContainerHighest`.
- The player sheet keeps its cover colours and Triage keeps its own window, but both follow the same anatomy and corner.

### 10.3 Anatomy, top to bottom

Cards sit **16 dp** from the edges (as on Home). Section labels and text inset **30 dp** (Home's feed row lines up with the chips the same way).

1. **Handle** (Material's).
2. **Head**, one of two kinds, never mixed:
   - **Identity head** for a thing with a name and live facts (group, feed, a feed being added): one card, `surfaceContainerHigh`, Tile 28 dp,
     16 dp padding. A **56 dp cookie** (nine-sided, `primaryContainer`, the thing's icon inside) or the thing's own picture (a feed's round icon);
     title `headlineSmall` SemiBold, **one line**, ellipsis; under it **one fact line** (`bodyMedium`, `onSurfaceVariant`: a group's feed count, a feed's address; the unread number is
     not repeated, Home already shows it and it depends on the page's filter). Anything editable says so: a 24 dp `primary` **pencil badge** on the icon opens the icon picker; a 40 dp round
     `surfaceContainerHighest` **pencil button** at the card's end renames; an address has its own small pencil. Pencils are hidden where
     the account cannot rename or edit.
   - **Plain head** for everything else (pickers, numbers, content): a 44 dp cookie with an icon, title `titleLarge` SemiBold, an optional hint
     (`bodyMedium`, `onSurfaceVariant`, at most two lines), 18 dp below. No card.
3. **Body**: sections of `labelLarge` SemiBold `primary` label (8 dp above its content, 18 dp between sections), then one of these blocks,
   and only these:
   - **Tiles** (60 dp, three in a row, label under; lit = `primaryContainer` with corners morphing 16 → 30 dp; press feel). A tile that only
     acts (a group applying to all its feeds) is never lit; the label above says what it applies to ("For all 23 feeds").
   - **Joined list** (`groupShape`: 24 / 6, 3 dp gap; rows at least 58 dp; the chosen row `primaryContainer` with a check) for choices.
   - **Chips row** (sideways, 8 dp gap, `fadingEdgesHorizontal` 32 dp, a round 36 dp "+" at the end) for choosing among many names.
   - **Value card** (label and value on one line, slider or stepper below; `Custom` / `Reset` as a joined pair under it).
   - **Joined buttons** (26 / 10 dp corners, 52 dp) for two or three exclusive modes.
   - **Content card** for text the sheet shows (summary, translation), and a **filled field** (Small 16 dp, `surfaceContainerHighest`).
4. **Foot**, only when the sheet has actions: **pinned**, it does not scroll. At most two round 48 dp buttons side by side: tonal for a
   normal action, `errorContainer` for one that deletes or clears, **filled only** for the one button that commits. A sheet that applies as
   you change it has no foot (no Cancel, no Save).

### 10.4 Behaviour

- Only the body scrolls; head and foot stay. A scrolling body fades into the head and the foot with `edgeFadeBrush`, never a hard cut.
- Selection changes tick (`SegmentTick`); a commit gives the context click. Cards and tiles use `rememberPressFeel`. Icons that swap cross-fade.
- A confirm dialog opened from a sheet closes the sheet after it is confirmed.
- Pause anything that turns forever behind the sheet (Home's background shapes already do).

### 10.5 Writing

Title is the name of the thing or the job ("Sort", "Font size"), never a sentence. A hint is one line where possible and says something the
sheet does not already show: Home's "The following options apply to all feeds in this group" becomes the section label "For all 23 feeds".

### 10.6 Dialogs (same family)

Banner 32 dp, `surfaceContainerHigh`, a 52 dp cookie (red for delete/clear), title `headlineSmall` SemiBold, left-aligned; a text Cancel and
one round filled 48 dp main button (`DialogConfirmButton`, red when `danger`). A feed's own picture is drawn as it is (`plainIcon`).
Fields are filled and rounded, everywhere (account dialogs included).

### 10.7 Where each sheet stands against this

| Sheet | Head | Body | Foot | Change needed |
|---|---|---|---|---|
| Group options | Identity | tiles (acting), chips | Clear · Delete | new identity head with badge and pencil; label "For all N feeds"; foot pinned |
| Feed options | Identity (icon, name, address pencils) | tiles (lit), chips | Clear · Unsubscribe | as above |
| Choice (radio dialogs) | now a dialog (`ChoiceDialog`, icons in `SettingIcons`) | joined list | Cancel | done |
| Number, Speed | Plain | value card, joined Custom / Reset | none | sides 16 dp |
| Sort, Sleep timer | Plain | tiles, joined buttons | none | section labels `primary` (Sort's are muted today) |
| Summary, Translate | Plain | content card, filled field | none | sides 16 dp, content in a card |
| Reader text settings | Plain | value cards, tiles | none | slider icons, head icon, 52 dp button → 48 dp round |
| Newspaper, Month recap | Plain | content | optional | check |
| Triage places | Plain | joined list | none | `InWindowModalSheet` to `SheetShape`, drop its own title and divider |
| Now playing | Own colours | player | none | corner only |
| Read later activity | Plain | joined list | none | check |

### 10.8 Menus (`AnimatedDropdownMenu(bare = true)`)

A menu has no box of its own: it is separate pieces on `surfaceContainerHigh` with a 1 dp shadow, 3 dp apart (`Group.Gap`, as in a joined group; `MenuParts.kt`).
- `MenuStrip`: a 28 dp card of `MenuRoundButton`s (52 dp circles, icon only, 24 dp icon, a content description for each; a long press shows it as a `TransientHint`, the hint bubble's look without "Got it", for about 2 s, fading out). A button that is on
  (read, starred) is `primaryContainer`, the others `surfaceContainerHighest`.
- `MenuRow`: its own 20 dp card, icon and text (Mark above / Mark below as read, above and below the strip).
- Choices of one setting (Triage "Skip after"): one 28 dp card with a `primary` title and 72 x 56 dp chips in a grid; the chosen chip is round (28 dp)
  and solid `primary` (card colours: see the last line of this section), the others 16 dp on `surfaceContainerHigh`.
- Menus: article long-press (above row, strip of Read, Star, Share, Newspaper, below row), Triage "Skip after", image viewer (Save, Share link, Share image).
- A menu drawn as one card (Triage "Skip after") never matches the screen behind it: `surfaceContainerHighest` with a 1 dp `outlineVariant` border and a deeper shadow (8 dp), its chips on `surfaceContainerHigh`, the chosen one solid `primary`. Menus made of separate pieces already stand apart; any new card-shaped menu follows this surface.
- Stat tiles (Reading stats, Filters page): full-size `StatTile`s, 12 dp apart, equal height (`IntrinsicSize.Min` + `fillMaxHeight`); one is always picked (`tertiaryContainer`, the others `surfaceContainerHigh`) and decides what shows below. Filters: Duplicates, Muted, Watched. Watched on Home: an eye badge by the group name; in a feed row only the number, in a 40 x 28 dp `tertiaryContainer` chip like the unread count.
