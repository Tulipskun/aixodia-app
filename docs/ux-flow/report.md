# UX Flow Critique — 2026-11-25

Code-grounded pass (no live browser pass: this is a Compose app whose device was
off the tailnet at the time). Every finding points at a file and a label; nothing
here is inferred from memory of the screens.

## Friction scorecard

| Journey | Steps (actual / ideal) | Needless decisions | Dead ends | Verdict |
|---|---|---|---|---|
| See the machines → open a chat | 2 / 2 | — | — | minimal |
| Fix a provider with a dead key | 4 / 3 (was 5 / 3) | — | was: home had no route | acceptable |
| Restart a session | 2 / 2 | was: a refresh arrow that restarted | — | minimal |
| Open a terminal, then leave | 2 / 2 | — | — | minimal |
| Open the desktop, then leave | 2 / 2 | — | — | minimal |
| Browse an engine's files | 3 / 3 | which engine? (see D2) | — | acceptable |
| Rename / delete an engine | 3 / 3 | — | — | minimal |
| Read a device's state (Droidime) | 2 / 2 | — | two disabled verbs | acceptable |
| Settings → connection → sign in | 3 / 3 | — | — | minimal |

## Conflicts found and fixed

### F1 — the bar's picture, label and act were three different things
`ChatScreen`'s top-right control wore `Icons.Default.Refresh` and the label
"Push now?", and its action opened a dialog that **ends the session and starts
the next one**. On the engines list the same control really did refresh, so the
same glyph meant two different acts in two screens.

Fixed: `ConsoleBar` now takes `onAction` / `actionLabel` / `actionIcon`. The chat
passes `Icons.Default.RestartAlt` + "Restart session"; the lists still pass
`Refresh`. The dialog and its snacks lost the jargon: "Restart this session?",
"Restart requested", "Restart failed".

### F2 — a control over the machine was hidden one level inside another journey
Providers was reachable only from the drawer **of a chat**, while Settings — its
sibling, both are "controls over the machine" — was reachable from both drawers
and from the settings row. A reader landing on the home list (the front door) had
no path to the keys at all.

Fixed: `Place.Providers` is a top-level place (rendered by `MainActivity`, like
`Place.Droidime`), and every drawer carries the same four rows: Engines,
Droidime, Providers, Settings. Back returns to whatever opened it — the history
already knew how.

### F3 — the chat drawer had one door
The drawer listed chats, Providers and Settings, but nothing led back to the
engine list except the system back button. The composer had an "Engines" command;
the drawer did not.

Fixed: an "Engines" row at the foot of the chat drawer (`nav.home()`), beside
Providers and Settings.

### F4 — one pane, two names
The engine card's action was labelled **AI**; the rest of the app calls that pane
**Chat** (`Place.Chat`, "Chat menu", "New chat", `chat_name`).

Fixed: the action reads **Chat**.

### F5 — hardcoded label in a resource-driven menu
The composer's "Engines" command was the only row in `composerCommands` that was
not a string resource.

Fixed: `R.string.engines`.

## Noticed, not changed

### D1 — the same list is stated twice on one screen
`GoalsCard` (above the composer) and the goal/plan note drawn at the head of the
turn (inside the thread) show the same items. The note is the text the model was
given; the card is the persistent, structured view. Recommendation: keep the card
as canonical and let the note stay only where the request carried it — but the
thread note was asked for explicitly, so it is left as is.

### D2 — one screen, two targets (resolved)
Files opened from a card's **More** (having selected that engine) and from
**Settings → Files** (using the stored selection), and the screen never said
which engine it was browsing. The Settings row has been removed: a file browser
belongs to the engine whose card opened it, and one entry point is enough. Files
now exists only behind a card's More.

### D3 — a card with two dead verbs
`DroidimeScreen` draws "View screen" / "Control screen" disabled, then a
paragraph of apology. Recommendation: either wire them to the device's own
origin, or replace the pair with one line naming what is missing.

## Duplicate paths, after the changes

| Goal | Paths | Recommendation |
|---|---|---|
| Providers | both drawers | keep both — same depth, same result |
| Settings | both drawers, settings command | keep |
| Backup & restore | composer command, Settings row | keep both; same screen, same target |
| Files | card More, Settings row | keep, but say whose files (D2) |
| Engines list | drawer row, composer command, system back | keep — all three are one step |
