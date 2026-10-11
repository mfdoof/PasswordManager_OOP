package com.doof.passwordmanager.ui.tui.screen;

import com.doof.passwordmanager.service.EntryService.EntrySummary;
import com.doof.passwordmanager.ui.tui.Draw;
import com.doof.passwordmanager.ui.tui.Theme;
import com.doof.passwordmanager.ui.tui.TuiContext;
import com.doof.passwordmanager.ui.tui.TuiScreen;
import com.doof.passwordmanager.ui.tui.widget.PasswordGenerator;
import com.doof.passwordmanager.ui.tui.widget.SecretField;
import com.doof.passwordmanager.ui.tui.widget.StrengthMeter;
import com.doof.passwordmanager.ui.tui.widget.TextField;
import com.doof.passwordmanager.util.ValidationException;
import com.googlecode.lanterna.SGR;
import com.googlecode.lanterna.TerminalPosition;
import com.googlecode.lanterna.TerminalSize;
import com.googlecode.lanterna.graphics.TextGraphics;
import com.googlecode.lanterna.input.KeyStroke;
import com.googlecode.lanterna.input.KeyType;

import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Locale;

/**
 * docs/TUI_DESIGN.md §3.4: add / edit entry. Password and notes are char[] fields wiped on save,
 * cancel, lock and auto-lock. In edit mode an empty password or notes field keeps the current value.
 * <p>
 * {@code g} and {@code v} are ordinary characters inside the fields, so they act as commands on the
 * generator row (reached with TAB); {@code ctrl+g} / {@code ctrl+v} work from anywhere in the form.
 */
public final class EntryModal implements TuiScreen {

    static final int WIDTH = 53;
    static final int HEIGHT = 15;
    static final int LABEL_WIDTH = 11;  // "password > "
    static final String HINTS = "[TAB] next  [ENTER] save  [ESC] cancel";
    static final String DISCARD = "discard changes? [y/N]";
    static final String KEEP = "(keep current)";

    enum Field { SITE, USER, PASSWORD, NOTES, GENERATOR }

    enum GenItem { LENGTH, UPPER, LOWER, DIGITS, SYMBOLS }

    private final TuiContext ctx;
    private final VaultScreen parent;
    private final Long editId;  // null = add
    private final String initialSite;
    private final String initialUser;

    private final TextField site = new TextField(256);
    private final TextField user = new TextField(256);
    private final SecretField password = new SecretField();
    private final SecretField notes = new SecretField();
    private Field focus = Field.SITE;
    private boolean showPassword;
    private String error;
    private boolean confirmDiscard;

    private GenItem genItem = GenItem.LENGTH;
    private final StringBuilder lengthText = new StringBuilder(String.valueOf(PasswordGenerator.DEFAULT_LENGTH));
    private boolean lengthFresh = true;  // first digit typed replaces the length
    private boolean upper = true, lower = true, digits = true, symbols = true;
    private final SecureRandom random = new SecureRandom();

    /** Add mode. */
    public EntryModal(TuiContext ctx, VaultScreen parent) {
        this(ctx, parent, null);
    }

    /** Edit mode when {@code existing} is not null: site and user prefilled, password and notes empty = keep. */
    public EntryModal(TuiContext ctx, VaultScreen parent, EntrySummary existing) {
        this.ctx = ctx;
        this.parent = parent;
        this.editId = existing == null ? null : existing.getId();
        this.initialSite = existing == null ? "" : existing.getSite();
        this.initialUser = existing == null ? "" : existing.getUser();
        site.set(initialSite);
        user.set(initialUser);
    }

    // --- input ---

    @Override
    public Action handleKey(KeyStroke key) {
        if (confirmDiscard) {
            confirmDiscard = false;
            if (key.getKeyType() == KeyType.Character && Character.toLowerCase(key.getCharacter()) == 'y') {
                close();
            }
            return Action.REDRAW;  // anything else = No (the default)
        }
        error = null;  // inline errors stay until the next key

        if (key.getKeyType() == KeyType.Character && key.isCtrlDown()) {
            switch (Character.toLowerCase(key.getCharacter())) {
                case 'g' -> generate();
                case 'v' -> showPassword = !showPassword;
                default -> { }
            }
            return Action.REDRAW;
        }

        switch (key.getKeyType()) {
            case Escape -> {
                if (isDirty()) confirmDiscard = true;
                else close();
            }
            case Enter -> save();
            case Tab, ArrowDown -> moveFocus(1);
            case ReverseTab, ArrowUp -> moveFocus(-1);
            default -> {
                if (focus == Field.GENERATOR) generatorKey(key);
                else fieldKey(key);
            }
        }
        return Action.REDRAW;
    }

    private void moveFocus(int delta) {
        Field[] all = Field.values();
        focus = all[(focus.ordinal() + delta + all.length) % all.length];
        if (focus == Field.GENERATOR && genItem == GenItem.LENGTH) lengthFresh = true;
    }

    private void fieldKey(KeyStroke key) {
        boolean back = key.getKeyType() == KeyType.Backspace;
        boolean typed = key.getKeyType() == KeyType.Character && !key.isAltDown();
        if (!back && !typed) return;
        switch (focus) {
            case SITE -> { if (back) site.backspace(); else site.append(key.getCharacter()); }
            case USER -> { if (back) user.backspace(); else user.append(key.getCharacter()); }
            case PASSWORD -> { if (back) password.backspace(); else password.append(key.getCharacter()); }
            case NOTES -> { if (back) notes.backspace(); else notes.append(key.getCharacter()); }
            default -> { }
        }
    }

    private void generatorKey(KeyStroke key) {
        GenItem[] items = GenItem.values();
        switch (key.getKeyType()) {
            case ArrowLeft, ArrowRight -> {
                int d = key.getKeyType() == KeyType.ArrowRight ? 1 : -1;
                genItem = items[(genItem.ordinal() + d + items.length) % items.length];
                lengthFresh = true;
            }
            case Backspace -> {
                if (genItem == GenItem.LENGTH && lengthText.length() > 0) {
                    lengthText.setLength(lengthText.length() - 1);
                    lengthFresh = false;
                }
            }
            case Character -> {
                char c = key.getCharacter();
                if (c == 'g') generate();
                else if (c == 'v') showPassword = !showPassword;
                else if (c == '+' || c == '=') setLength(length() + 1);
                else if (c == '-') setLength(length() - 1);
                else if (c == ' ') toggleSet();
                else if (Character.isDigit(c) && genItem == GenItem.LENGTH) {
                    if (lengthFresh) lengthText.setLength(0);
                    if (lengthText.length() < 3) lengthText.append(c);
                    lengthFresh = false;
                }
            }
            default -> { }
        }
    }

    private void toggleSet() {
        switch (genItem) {
            case UPPER -> upper = !upper;
            case LOWER -> lower = !lower;
            case DIGITS -> digits = !digits;
            case SYMBOLS -> symbols = !symbols;
            default -> { }
        }
    }

    /** Current length, or -1 if the typed text is not a number. */
    private int length() {
        try {
            return Integer.parseInt(lengthText.toString());
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    private void setLength(int n) {
        int clamped = Math.max(PasswordGenerator.MIN_LENGTH, Math.min(PasswordGenerator.MAX_LENGTH,
                n < 0 ? PasswordGenerator.DEFAULT_LENGTH : n));
        lengthText.setLength(0);
        lengthText.append(clamped);
        lengthFresh = true;
    }

    private void generate() {
        int n = length();
        if (n < PasswordGenerator.MIN_LENGTH || n > PasswordGenerator.MAX_LENGTH) {
            error = "err: length must be " + PasswordGenerator.MIN_LENGTH + "-" + PasswordGenerator.MAX_LENGTH;
            return;
        }
        if (!upper && !lower && !digits && !symbols) {
            error = "err: enable at least one character set";
            return;
        }
        char[] generated = PasswordGenerator.generate(n, upper, lower, digits, symbols, random);
        try {
            password.set(generated);
        } finally {
            Arrays.fill(generated, '\0');
        }
    }

    // --- save / close ---

    private boolean isDirty() {
        return !site.text().equals(initialSite) || !user.text().equals(initialUser)
                || !password.isEmpty() || !notes.isEmpty();
    }

    private void save() {
        String s = site.text().trim();
        String u = user.text().trim();
        if (s.isEmpty()) {
            fail("err: site is required", Field.SITE);
            return;
        }
        if (u.isEmpty()) {
            fail("err: user is required", Field.USER);
            return;
        }
        if (editId == null && password.isEmpty()) {
            fail("err: password is required - type one or press [ctrl+g]", Field.PASSWORD);
            return;
        }
        for (EntrySummary e : ctx.connector.listEntries()) {
            boolean same = e.getSite().equalsIgnoreCase(s) && e.getUser().equalsIgnoreCase(u);
            if (same && (editId == null || e.getId() != editId)) {
                fail("err: this site + user already exists", Field.USER);
                return;
            }
        }

        // Copies go to the service (which wipes them), so a failed save keeps what was typed.
        char[] pw = password.isEmpty() ? null : password.peek();
        char[] notesChars = notes.isEmpty() ? null : notes.peek();
        // EntryService takes notes as a String (encoded into the encrypted payload).
        String notesValue = notesChars == null ? null : new String(notesChars);
        long id;
        try {
            if (editId == null) {
                id = ctx.connector.addEntry(s, u, pw, notesValue);
            } else {
                id = editId;
                ctx.connector.updateEntry(id, s, u, pw, notesValue);
            }
        } catch (ValidationException ex) {
            fail("err: " + lowerFirst(ex.getMessage()), null);
            return;
        } catch (RuntimeException ex) {
            fail("err: could not save: " + ex.getMessage(), null);
            return;
        } finally {
            if (pw != null) Arrays.fill(pw, '\0');
            if (notesChars != null) Arrays.fill(notesChars, '\0');
        }
        wipe();
        parent.afterChange(id, (editId == null ? "ok: added " : "ok: saved ") + s);
        ctx.show(parent);
    }

    private void fail(String message, Field field) {
        error = message;
        if (field != null) focus = field;
    }

    private static String lowerFirst(String s) {
        if (s == null || s.isEmpty()) return "invalid input";
        String t = s.endsWith(".") ? s.substring(0, s.length() - 1) : s;
        return t.substring(0, 1).toLowerCase(Locale.ROOT) + t.substring(1);
    }

    private void close() {
        wipe();
        ctx.show(parent);
    }

    private void wipe() {
        password.clear();
        notes.clear();
    }

    @Override
    public void onHide() {
        wipe();
    }

    private long lastSessionSecond = -1;

    @Override
    public boolean tick(long nowMillis) {
        if (!ctx.connector.isUnlocked()) {  // auto-lock while the form is open: close it, wipe everything
            wipe();
            ctx.lockToUnlock(VaultScreen.AUTO_LOCKED);
            return true;
        }
        long second = parent.sessionSecond();  // keep the header countdown behind the form ticking
        if (second != lastSessionSecond) {
            lastSessionSecond = second;
            return true;
        }
        return false;
    }

    // --- drawing ---

    @Override
    public void draw(TextGraphics g, TerminalSize size, Theme t) {
        parent.draw(g, size, t);

        int col = (size.getColumns() - WIDTH) / 2;
        int top = parent.modalTop(size.getRows(), HEIGHT);
        g.setBackgroundColor(t.background);
        g.fillRectangle(new TerminalPosition(col, top), new TerminalSize(WIDTH, HEIGHT), ' ');
        g.setForegroundColor(t.primary);
        Draw.box(g, t, col, top, WIDTH, HEIGHT, editId == null ? "add entry" : "edit entry");

        int valueCol = col + 2 + LABEL_WIDTH;
        int valueWidth = WIDTH - 2 - LABEL_WIDTH - 3;
        int passwordWidth = valueWidth - 10;

        label(g, t, col, top + 2, "site", Field.SITE);
        text(g, t, valueCol, top + 2, site.tail(valueWidth - 1), focus == Field.SITE);

        label(g, t, col, top + 3, "user", Field.USER);
        text(g, t, valueCol, top + 3, user.tail(valueWidth - 1), focus == Field.USER);

        label(g, t, col, top + 4, "password", Field.PASSWORD);
        secret(g, t, valueCol, top + 4, password, passwordWidth - 1, showPassword, focus == Field.PASSWORD);
        g.setForegroundColor(t.dim);
        g.putString(col + WIDTH - 11, top + 4, showPassword ? "[v] hide" : "[v] view");

        char[] pw = password.peek();
        try {
            StrengthMeter.Level level = StrengthMeter.level(pw);
            g.setForegroundColor(t.dim);
            g.putString(valueCol, top + 5, "strength ");
            g.setForegroundColor(level.compareTo(StrengthMeter.Level.STRONG) >= 0 ? t.success
                    : level == StrengthMeter.Level.NONE ? t.dim : level.compareTo(StrengthMeter.Level.FAIR) <= 0 ? t.error : t.text);
            g.putString(valueCol + 9, top + 5, StrengthMeter.bar(pw) + " " + level.label);
        } finally {
            Arrays.fill(pw, '\0');
        }

        label(g, t, col, top + 6, "notes", Field.NOTES);
        secret(g, t, valueCol, top + 6, notes, valueWidth - 1, true, focus == Field.NOTES);

        if (error != null) {
            g.setForegroundColor(t.error);
            g.putString(col + 2, top + 7, Draw.truncate(error, WIDTH - 4));
        }

        drawGenerator(g, t, col + 2, top + 8);

        if (confirmDiscard) {
            g.setForegroundColor(t.error);
            g.putString(col + 2, top + 13, DISCARD);
        } else {
            g.setForegroundColor(t.dim);
            g.putString(col + 2, top + 13, HINTS);
        }
    }

    private void label(TextGraphics g, Theme t, int col, int row, String name, Field field) {
        boolean focused = focus == field;
        g.setForegroundColor(t.text);
        g.putString(col + 2, row, name);
        g.setForegroundColor(focused ? t.primary : t.dim);
        g.putString(col + 2 + LABEL_WIDTH - 2, row, ">");
    }

    private void text(TextGraphics g, Theme t, int col, int row, String value, boolean focused) {
        g.setForegroundColor(t.text);
        g.putString(col, row, value);
        if (focused) {
            g.setForegroundColor(t.primary);
            g.putString(col + value.length(), row, "_");
        }
    }

    /** Masked or visible, drawn cell by cell from the char[] (no String of the value). */
    private void secret(TextGraphics g, Theme t, int col, int row, SecretField f, int width, boolean visible,
                        boolean focused) {
        int n = Math.min(f.length(), width);
        if (n == 0 && editId != null && !focused) {
            g.setForegroundColor(t.dim);
            g.putString(col, row, KEEP);
            return;
        }
        g.setForegroundColor(t.text);
        int start = f.length() - n;  // show the end, where typing happens
        for (int i = 0; i < n; i++) {
            char c = f.charAt(start + i);
            g.setCharacter(col + i, row, visible ? c : '*');
        }
        if (focused) {
            g.setForegroundColor(t.primary);
            g.putString(col + n, row, "_");
        }
    }

    private void drawGenerator(TextGraphics g, Theme t, int col, int row) {
        int w = WIDTH - 4;
        boolean focused = focus == Field.GENERATOR;
        g.setForegroundColor(focused ? t.primary : t.dim);
        Draw.box(g, t, col, row, w, 4, focused ? "generate - [<>] pick [space] toggle" : "generate");

        int x = col + 2;
        // "length: 20  [x] A-Z [x] a-z [x] 0-9 [x] !@#" (43 cols, fits the 45-col box interior)
        x = genItem(g, t, x, row + 1, "length: " + String.format("%-3s", lengthText), GenItem.LENGTH, focused);
        x = genItem(g, t, x, row + 1, box(upper) + " A-Z", GenItem.UPPER, focused);
        x = genItem(g, t, x, row + 1, box(lower) + " a-z", GenItem.LOWER, focused);
        x = genItem(g, t, x, row + 1, box(digits) + " 0-9", GenItem.DIGITS, focused);
        genItem(g, t, x, row + 1, box(symbols) + " !@#", GenItem.SYMBOLS, focused);

        g.setForegroundColor(t.dim);
        g.putString(col + 2, row + 2, focused ? "[g] generate  [v] view  [+/-] length" : "[g] generate into password field");
    }

    /** Draws one generator item; the selected one is shown in reverse video (not color alone). */
    private int genItem(TextGraphics g, Theme t, int x, int row, String text, GenItem item, boolean focused) {
        boolean selected = focused && genItem == item;
        g.setForegroundColor(selected ? t.primary : t.text);
        if (selected) g.enableModifiers(SGR.REVERSE);
        g.putString(x, row, text);
        if (selected) g.disableModifiers(SGR.REVERSE);
        return x + text.length() + 1;
    }

    private static String box(boolean on) {
        return on ? "[x]" : "[ ]";
    }

    /** Test hooks. */
    SecretField passwordField() {
        return password;
    }

    SecretField notesField() {
        return notes;
    }

    Field focus() {
        return focus;
    }
}
