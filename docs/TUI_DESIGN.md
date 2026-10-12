# DOOF Vault — TUI Design

Terminal UI built with Lanterna (Phase 2, implemented). It replaced the Swing UI and the console harness. Keyboard-only, works on a local terminal, over SSH and on a headless console. No emojis anywhere in the UI.

## 1. Requirements
- Minimum terminal size 80x24. Smaller shows a centered `terminal too small (need 80x24, have WxH)` message until resized. Layout reflows on resize.
- Content is capped at 120 columns and centered; wider terminals get empty margins. Modals are centered over the content.
- Unicode box borders when the locale is UTF-8 (`LC_ALL`, then `LC_CTYPE`, then `LANG`); otherwise, or with `--ascii`, `+ - |`.
- Colors per §2: exact RGB when `COLORTERM` is `truecolor`/`24bit`, nearest xterm-256 color otherwise. A non-empty `NO_COLOR`, `--no-color` or `TERM=dumb` gives a monochrome UI; meaning is never carried by color alone (see §6).
- On lock and on quit the screen is cleared so no entry data is left in the terminal.

## 2. Palette
| Role | Hex | Used for |
|---|---|---|
| background | `#0d0b14` | everything |
| text | `#e0d7f5` | body text |
| primary | `#a277ff` | logo, selected row, `>` cursor, focused field |
| dim | `#6b5b95` | borders, titles, hints, timestamps |
| success | `#61ffca` | `ok:` messages, strength bar (strong) |
| error | `#ff6188` | `err:` messages, delete confirm, lock warning |

## 3. Screens

### 3.1 Unlock
```
+------------------------------------------------------------------------------+
|                                                                              |
|                      ######   #####   #####  #######                        |
|                      ##   ## ##   ## ##   ## ##                             |
|                      ##   ## ##   ## ##   ## #####                          |
|                      ##   ## ##   ## ##   ## ##                             |
|                      ######   #####   #####  ##        vault v2.0.1         |
|                                                                              |
|                +- unlock ----------------------------------+                |
|                |                                           |                |
|                |  > master password: ************_         |                |
|                |                                           |                |
|                |  [ENTER] unlock    [ESC] quit             |                |
|                +-------------------------------------------+                |
|                                                                              |
|                err: invalid password (attempt 3) - wait 4s                    |
|                                                                              |
|  vault: ~/.doof/vault.db                                                     |
+------------------------------------------------------------------------------+
```
- The vault path is shown so the user knows which file is being opened. A permission warning from opening the file (group/other access) is shown as an `err:` line above it.
- `q` is a password character here; `ESC` quits. Unlock runs Argon2id on a worker thread with an `unlocking` spinner.
- During a throttle wait, input is disabled and a live countdown replaces the message.

### 3.2 Create vault (first run)
```
                +- create vault ------------------------------+
                |                                             |
                |  > new master password: ************        |
                |  > confirm:             ************_       |
                |                                             |
                |    strength [########--] strong             |
                |    [x] 12+ chars  [x] upper  [x] lower      |
                |    [x] digit      [ ] symbol (optional)     |
                |                                             |
                |  [TAB] next  [ENTER] create  [ESC] quit     |
                +---------------------------------------------+
```
- Requirement checklist updates live while typing. Required: 12+ chars, upper, lower, digit (enforced by `VaultService`); symbol is optional. The strength bar is a rough estimate (length x character classes).
- Confirm mismatch shown inline under the confirm field.

### 3.3 Vault view
```
+- doof vault ---------------------------------- [UNLOCKED] | auto-lock 4:32 -+
| / search: git_                                                    3 of 42    |
+------------------------------------------------------------------------------+
|   SITE                      USERNAME                     UPDATED             |
| > github.com                mave@doof.quest              2026-10-11          |
|   gitlab.com                mave@doof.quest              2026-08-02          |
|   gitea.doof.quest          admin                        2026-07-19          |
|                                                                              |
+- details ---------------------------------------------------------------------+
| site:     github.com                                                         |
| user:     mave@doof.quest                                                    |
| password: ************    [s] reveal  [c] copy  [u] copy user                |
| notes:    (hidden)        [n] show notes                                     |
+------------------------------------------------------------------------------+
| [a]dd [e]dit [d]el [/]search [l]ock [?]help [q]uit       ok: copied (18s)    |
+------------------------------------------------------------------------------+
```
- List sorted by site (case-insensitive), then user. Long values truncated with `...`.
- The frame is only as tall as needed: the list has one row per entry (minimum 3, at most what fits), the details pane sits directly under it, and the space below the frame stays empty. The height follows the total entry count, so it does not jump while searching.
- Columns fit their content: SITE up to ~35% and USERNAME up to ~45% of the width, UPDATED fixed at 10.
- The count reads `N entries` without a query and `N of M` while filtering.
- A status message too long for the gap next to the footer keys takes the whole footer row.
- Empty vault shows `vault is empty - press [a] to add an entry`. Search with no hits shows `no matches for "xyz" - [ESC] clear`.
- Reveal shows the password (`s`) or notes (`n`, newlines as spaces, `(none)` if empty) with a `(hides in Ns)` countdown, then re-masks. Moving the selection, searching, help, lock and quit re-mask immediately.
- Auto-lock countdown turns error color and shows `[LOCKING]` in the last 30s.

### 3.4 Entry modal (add / edit)
```
            +- add entry ---------------------------------------+
            |                                                   |
            |  site     > github.com                            |
            |  user     > mave@doof.quest                       |
            |  password > ********************   [v] view       |
            |             strength [##########] very strong     |
            |  notes    > _                                     |
            |                                                   |
            |  +- generate ----------------------------------+  |
            |  | length: 20  [x] A-Z [x] a-z [x] 0-9 [x] !@# |  |
            |  | [g] generate into password field            |  |
            |  +---------------------------------------------+  |
            |                                                   |
            |  [TAB] next  [ENTER] save  [ESC] cancel           |
            +---------------------------------------------------+
```
- Edit mode: site and user are prefilled; an empty password or notes field means "keep current" and shows `(keep current)`.
- Generator row (reached with TAB): left/right select the length or a character set (selected item in reverse video), space toggles a set, `+`/`-` or digits set the length (8-128; the first digit replaces), `g` generates (SecureRandom, at least one character from each enabled set; refused if all sets are off), `v` shows/hides the password.
- Empty site, user or (when adding) password shows an inline `err:`; service validation messages (e.g. a site with spaces) appear the same way. A failed save keeps the input.
- Duplicate site + user shows an inline `err:` before saving.
- ESC with unsaved changes asks `discard changes? [y/N]`.

### 3.5 Other dialogs
- Delete: status bar `err: delete github.com (mave@doof.quest)? [y/N]`, default No.
- Change master password: `[p]` opens current / new / confirm modal with the same checklist as 3.2; shows `re-encrypting N entries` with a spinner while running (ESC is ignored until it finishes). A wrong current password shows `err: wrong current password (attempt N) - wait Ns` with input disabled during the wait; success shows `ok: master password changed` and the session continues.
- Help: `[?]` overlay listing every key binding; any key closes it.

## 4. Key bindings
| Key | Action | Where |
|---|---|---|
| `j` / `k`, arrows | move selection | list |
| `PgUp` / `PgDn`, `Home` / `End` | page, first, last | list |
| `g g` / `G` | first / last entry | list |
| `/` | focus search; `ESC` clears it | list |
| `a` / `e` / `d` | add / edit / delete | list |
| `s` | reveal password 10s | list |
| `c` | copy password (20s clear) | list |
| `u` | copy username | list |
| `n` | show notes | list |
| `p` | change master password | list |
| `l` | lock now | list |
| `?` | help overlay (any key closes) | list |
| `q` | quit (clears screen, wipes keys) | list |
| `ESC` | quit | unlock, create |
| `y` | confirm delete (anything else = No) | delete prompt |
| `TAB` / `shift+TAB` | next / previous field | modals |
| `g` / `ctrl+g` | generate password (`g` on the generator row, `ctrl+g` from any field) | entry modal |
| `v` / `ctrl+v` | toggle password visibility (`v` on the generator row, `ctrl+v` from any field) | entry modal |
| `ENTER` / `ESC` | confirm / cancel | modals |

Note: inside text fields `g` and `v` are ordinary characters, so the entry modal only treats them as commands on the generator row (reached with TAB). `ctrl+v` works from any field in most terminals, but some (Windows Terminal, GNOME Terminal with its default bindings) take `ctrl+v` as paste and never send it to the app; `v` on the generator row is the reliable toggle.

## 5. Clipboard
- `c` and `u` use the Java AWT clipboard when a desktop session exists. A copied password is cleared after 20s, and also on lock and on quit, only if the clipboard still holds our value (SHA-256 compare). The status counts down `ok: copied (Ns)`, then `ok: clipboard cleared`. Usernames are not cleared; copying one cancels a pending password clear.
- No desktop session (headless, or SSH without `DISPLAY`, where the clipboard would be the remote machine's): status `err: clipboard unavailable - press [s] to reveal`. Reveal and paste-in work everywhere.
- On macOS the app sets `apple.awt.UIElement` so using the clipboard does not add a Dock icon.

## 6. UX rules
1. Never rely on color alone: messages are prefixed `ok:` / `err:`, state is written out (`[UNLOCKED]`, `[LOCKING]`), checkboxes use `[x]` / `[ ]`.
2. Status messages auto-dismiss after 5s; errors stay until the next keypress.
3. Every screen shows its key hints (vault view: footer for list actions, details pane for `s`/`c`/`u`/`n`); `?` shows all of them.
4. Destructive actions default to No.
5. Any keypress resets the idle auto-lock timer (5 min). Auto-lock and `l` clear our clipboard value, close and wipe open forms, blank the screen and return to Unlock (`ok: auto-locked after 5 min idle` / `ok: vault locked`).
6. Secrets are never shown unless asked for (reveal, view toggle) and always re-mask on a timer.
7. Long operations (unlock KDF, rotation) show a working indicator so the UI never looks frozen.
8. No emojis; plain ASCII/box-drawing only.

## 7. Package layout
```
com.doof.passwordmanager.ui.tui
  TuiApp          entry point: opens the vault, terminal setup, main loop, resize handling
  TuiOptions      --vault / --ascii / --no-color
  Theme           palette, color mode (truecolor / 256 / none), border characters
  ScreenManager   current screen, min-size check, 120-column cap, activity hook, rendering
  TuiContext      connector, worker thread, clock, clipboard; idle-lock check and the lock path
  Draw            box / divider / truncation helpers
  screen/         UnlockScreen, CreateScreen (+ LockLayout), VaultScreen, EntryModal,
                  ChangePasswordModal, HelpOverlay, Outcome (worker results)
  widget/         SecretField, TextField, StrengthMeter, PolicyView, PasswordGenerator, StatusBar
  clip/           SecureClipboard (auto-clear), ClipboardBackend, AwtClipboardBackend
```
UI talks only to `ApplicationConnector`; no crypto or DAO access from `ui`.
