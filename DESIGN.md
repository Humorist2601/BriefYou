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
