# DOOF Vault — TUI Design (Phase 2)

Terminal UI built with Lanterna. Replaces Swing and the console harness. Keyboard-only, works on a local terminal, over SSH and on a headless console. No emojis anywhere in the UI.

## 1. Requirements
- Minimum terminal size 80x24. Smaller shows a centered `terminal too small (need 80x24, have WxH)` message until resized. Layout reflows on resize.
- Unicode box borders by default; `--ascii` flag (or auto-detect failure) falls back to `+ - |`.
- Colors per §2. `NO_COLOR` env var (or `--no-color`) gives a monochrome UI; meaning is never carried by color alone (see §6).
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
|                      ######   #####   #####  ##        vault v2.0           |
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
- The vault path is shown so the user knows which file is being opened.
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
                |    [x] digit      [ ] symbol                |
                |                                             |
                |  [TAB] next  [ENTER] create  [ESC] quit     |
                +---------------------------------------------+
```
- Requirement checklist updates live while typing.
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
- List sorted by site (case-insensitive). Long values truncated with `...`.
- Empty vault shows `vault is empty - press [a] to add an entry`. Search with no hits shows `no matches for "xyz" - [ESC] clear`.
- Reveal shows the password with `(hides in 10s)` countdown, then re-masks.
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
- Edit mode: password field empty means "keep current".
- Duplicate site + user shows an inline `err:` before saving.
- ESC with unsaved changes asks `discard changes? [y/N]`.

### 3.5 Other dialogs
- Delete: status bar `err: delete github.com (mave@doof.quest)? [y/N]`, default No.
- Change master password: `[p]` opens current / new / confirm modal with the same checklist as 3.2; shows `re-encrypting N entries...` while running.
- Help: `[?]` overlay listing every key binding; any key closes it.

## 4. Key bindings
| Key | Action | Where |
|---|---|---|
| `j` / `k`, arrows | move selection | list |
| `g g` / `G` | first / last entry | list |
| `/` | focus search; `ESC` clears it | list |
| `a` / `e` / `d` | add / edit / delete | list |
| `s` | reveal password 10s | list |
| `c` | copy password (20s clear) | list |
| `u` | copy username | list |
| `n` | show notes | list |
| `p` | change master password | list |
| `l` | lock now | anywhere unlocked |
| `?` | help overlay | anywhere |
| `q` | quit (clears screen, wipes keys) | list, unlock |
| `TAB` / `shift+TAB` | next / previous field | modals |
| `g` / `ctrl+g` | generate password (`g` on the generator row, `ctrl+g` from any field) | entry modal |
| `v` / `ctrl+v` | toggle password visibility (`v` on the generator row, `ctrl+v` from any field) | entry modal |
| `ENTER` / `ESC` | confirm / cancel | modals |

Note: inside text fields `g` and `v` are ordinary characters, so the entry modal only treats them as commands on the generator row (reached with TAB). `ctrl+v` works from any field in most terminals, but some (Windows Terminal, GNOME Terminal with its default bindings) take `ctrl+v` as paste and never send it to the app; `v` on the generator row is the reliable toggle.

## 5. Clipboard
- `c` and `u` use the Java AWT clipboard when a desktop session exists. Password copies clear after 20s (only if the clipboard still holds our value).
- No desktop session (SSH, headless): status `err: clipboard unavailable - press [s] to reveal`. Reveal and paste-in work everywhere.

## 6. UX rules
1. Never rely on color alone: messages are prefixed `ok:` / `err:`, state is written out (`[UNLOCKED]`, `[LOCKING]`), checkboxes use `[x]` / `[ ]`.
2. Status messages auto-dismiss after 5s; errors stay until the next keypress.
3. Every screen shows its key hints in the footer; `?` shows all of them.
4. Destructive actions default to No.
5. Any keypress resets the idle auto-lock timer (5 min). Auto-lock clears the screen and returns to Unlock.
6. Secrets are never shown unless asked for (reveal, view toggle) and always re-mask on a timer.
7. Long operations (unlock KDF, rotation) show a working indicator so the UI never looks frozen.
8. No emojis; plain ASCII/box-drawing only.

## 7. Package layout
```
com.doof.passwordmanager.ui.tui
  TuiApp          entry point, terminal setup, main loop, resize handling
  Theme           palette, NO_COLOR / --ascii handling
  ScreenManager   switches screens, owns idle timer and status bar
  screen/         UnlockScreen, CreateScreen, VaultScreen, HelpOverlay
  modal/          EntryModal, ConfirmPrompt, ChangePasswordModal
  widget/         StrengthBar, PasswordGenerator, Countdown
```
UI talks only to `ApplicationConnector`; no crypto or DAO access from `ui`.
