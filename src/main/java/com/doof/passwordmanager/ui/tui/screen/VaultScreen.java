package com.doof.passwordmanager.ui.tui.screen;

import com.doof.passwordmanager.service.EntryService.EntrySummary;
import com.doof.passwordmanager.ui.tui.Draw;
import com.doof.passwordmanager.ui.tui.Theme;
import com.doof.passwordmanager.ui.tui.TuiContext;
import com.doof.passwordmanager.ui.tui.TuiScreen;
import com.doof.passwordmanager.ui.tui.widget.StatusBar;
import com.googlecode.lanterna.TerminalSize;
import com.googlecode.lanterna.graphics.TextGraphics;
import com.googlecode.lanterna.input.KeyStroke;
import com.googlecode.lanterna.input.KeyType;

import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * docs/TUI_DESIGN.md §3.3: entry list, live search, details pane, footer and status bar.
 * Holds only site/user summaries; passwords and notes are never loaded here (reveal comes in step 4).
 */
public final class VaultScreen implements TuiScreen {

    static final String TITLE = "doof vault";
    static final String STATE = "[UNLOCKED]";
    static final String MASK = "************";  // fixed length: does not reveal the password length
    static final String FOOTER = "[j/k]move [/]search [l]ock [?]help [q]uit";
    static final String EMPTY = "vault is empty - press [a] to add an entry";

    /** Keys from §4 that later steps implement; pressing one says so instead of doing nothing. */
    static final Map<Character, String> NOT_YET = Map.of(
            'a', "add", 'e', "edit", 'd', "delete", 's', "reveal", 'c', "copy",
            'u', "copy user", 'n', "notes", 'p', "change password");

    static final Comparator<EntrySummary> ORDER = Comparator
            .comparing(EntrySummary::getSite, String.CASE_INSENSITIVE_ORDER)
            .thenComparing(EntrySummary::getUser, String.CASE_INSENSITIVE_ORDER)
            .thenComparingLong(EntrySummary::getId);

    private final TuiContext ctx;
    private final DateTimeFormatter dateFormat;
    private final StatusBar status = new StatusBar();
    private List<EntrySummary> all;
    private List<EntrySummary> view;
    private int selected;
    private int scroll;
    private int listRows = 10;  // updated on every draw; used for paging
    private final StringBuilder query = new StringBuilder();
    private boolean searching;
    private boolean pendingG;

    public VaultScreen(TuiContext ctx) {
        this(ctx, null);
    }

    /** {@code notice} goes to the status bar, e.g. "ok: vault unlocked". */
    public VaultScreen(TuiContext ctx, String notice) {
        this.ctx = ctx;
        this.dateFormat = DateTimeFormatter.ofPattern("yyyy-MM-dd").withZone(ctx.zone);
        reload();
        if (notice != null) status.show(notice, ctx.now());
    }

    /** Reloads summaries from the service, keeping the selected entry when possible. */
    void reload() {
        List<EntrySummary> loaded = new ArrayList<>(ctx.connector.listEntries());
        loaded.sort(ORDER);
        all = loaded;
        refilter();
    }

    private void refilter() {
        Long keep = current() == null ? null : current().getId();
        String q = query.toString().trim().toLowerCase(Locale.ROOT);
        if (q.isEmpty()) {
            view = all;
        } else {
            view = new ArrayList<>();
            for (EntrySummary e : all) {
                if (e.getSite().toLowerCase(Locale.ROOT).contains(q) || e.getUser().toLowerCase(Locale.ROOT).contains(q)) {
                    view.add(e);
                }
            }
        }
        selected = 0;
        if (keep != null) {
            for (int i = 0; i < view.size(); i++) {
                if (view.get(i).getId() == keep) {
                    selected = i;
                    break;
                }
            }
        }
    }

    private EntrySummary current() {
        return view == null || view.isEmpty() ? null : view.get(Math.min(selected, view.size() - 1));
    }

    private void move(int delta) {
        if (view.isEmpty()) return;
        selected = Math.max(0, Math.min(view.size() - 1, selected + delta));
    }

    @Override
    public Action handleKey(KeyStroke key) {
        status.onKey();
        boolean afterG = pendingG;
        pendingG = false;
        KeyType type = key.getKeyType();

        // Keys that work the same in both modes.
        switch (type) {
            case ArrowDown -> { move(1); return Action.REDRAW; }
            case ArrowUp -> { move(-1); return Action.REDRAW; }
            case PageDown -> { move(listRows); return Action.REDRAW; }
            case PageUp -> { move(-listRows); return Action.REDRAW; }
            default -> { }
        }

        if (searching) {
            switch (type) {
                case Escape -> {
                    query.setLength(0);
                    searching = false;
                    refilter();
                }
                case Enter -> searching = false;
                case Backspace -> {
                    if (query.length() > 0) {
                        query.setLength(query.length() - 1);
                        refilter();
                    }
                }
                case Character -> {
                    if (key.isCtrlDown() || key.isAltDown()) return Action.NONE;
                    query.append(key.getCharacter());
                    refilter();
                }
                default -> { return Action.NONE; }
            }
            return Action.REDRAW;
        }

        switch (type) {
            case Escape -> {
                if (query.length() == 0) return Action.REDRAW;
                query.setLength(0);
                refilter();
                return Action.REDRAW;
            }
            case Home -> { selected = 0; return Action.REDRAW; }
            case End -> { move(view.size()); return Action.REDRAW; }
            case Character -> { }
            default -> { return Action.REDRAW; }
        }
        if (key.isCtrlDown() || key.isAltDown()) return Action.REDRAW;

        char c = key.getCharacter();
        switch (c) {
            case 'j' -> move(1);
            case 'k' -> move(-1);
            case 'g' -> {
                if (afterG) selected = 0;
                else pendingG = true;
            }
            case 'G' -> move(view.size());
            case '/' -> searching = true;
            case '?' -> ctx.show(new HelpOverlay(ctx, this));
            case 'l' -> {
                ctx.connector.lockVault();
                ctx.show(new UnlockScreen(ctx, "ok: vault locked"));
            }
            case 'q' -> {
                ctx.connector.lockVault();
                return Action.QUIT;
            }
            default -> {
                String name = NOT_YET.get(c);
                if (name != null) {
                    status.show("err: [" + c + "] " + name + " is not available yet", ctx.now());
                }
            }
        }
        return Action.REDRAW;
    }

    @Override
    public boolean tick(long now) {
        if (!ctx.connector.isUnlocked()) {  // auto-lock fired in VaultService
            ctx.show(new UnlockScreen(ctx, "ok: vault auto-locked"));
            return true;
        }
        return status.tick(now);
    }

    @Override
    public void draw(TextGraphics g, TerminalSize size, Theme t) {
        int w = size.getColumns();
        int h = size.getRows();
        int detailsTop = h - 8;            // divider row of the details pane
        int listTop = 4;
        listRows = Math.max(1, detailsTop - listTop);

        // Frame and dividers.
        g.setForegroundColor(t.dim);
        Draw.box(g, t, 0, 0, w, h, null);
        Draw.divider(g, t, 0, 2, w);
        Draw.divider(g, t, 0, detailsTop, w);
        g.putString(2, detailsTop, " details ");
        Draw.divider(g, t, 0, h - 3, w);

        // Header.
        g.setForegroundColor(t.primary);
        g.putString(2, 0, " " + TITLE + " ");
        g.setForegroundColor(t.success);
        g.putString(w - STATE.length() - 3, 0, STATE);

        drawSearchRow(g, t, w);
        drawList(g, t, w, listTop);
        drawDetails(g, t, w, detailsTop + 1);

        // Footer: key hints left, status right.
        g.setForegroundColor(t.dim);
        g.putString(2, h - 2, FOOTER);
        String msg = status.text();
        if (msg != null) {
            int room = w - 4 - FOOTER.length() - 2;
            String shown = Draw.truncate(msg, Math.max(0, room));
            g.setForegroundColor(msg.startsWith("err:") ? t.error : t.success);
            g.putString(w - 2 - shown.length(), h - 2, shown);
        }
    }

    private void drawSearchRow(TextGraphics g, Theme t, int w) {
        String count = query.length() == 0
                ? (all.size() == 1 ? "1 entry" : all.size() + " entries")
                : view.size() + " of " + all.size();
        int countCol = w - 3 - count.length();
        if (searching || query.length() > 0) {
            g.setForegroundColor(searching ? t.primary : t.dim);
            g.putString(2, 1, "/");
            g.setForegroundColor(t.text);
            String label = " search: ";
            String q = Draw.truncateLeft(query.toString(), Math.max(0, countCol - 4 - label.length() - 2));
            g.putString(3, 1, label + q);
            if (searching) {
                g.setForegroundColor(t.primary);
                g.putString(3 + label.length() + q.length(), 1, "_");
            }
        } else {
            g.setForegroundColor(t.dim);
            g.putString(2, 1, "/ search");
        }
        g.setForegroundColor(t.dim);
        g.putString(countCol, 1, count);
    }

    private void drawList(TextGraphics g, Theme t, int w, int top) {
        int inner = w - 4;
        int dateW = 10;
        int rest = Math.max(10, inner - 2 - dateW - 4);
        int siteW = rest * 45 / 100;
        int userW = rest - siteW;
        int siteCol = 4;
        int userCol = siteCol + siteW + 2;
        int dateCol = userCol + userW + 2;

        g.setForegroundColor(t.dim);
        g.putString(siteCol, 3, "SITE");
        g.putString(userCol, 3, "USERNAME");
        g.putString(dateCol, 3, "UPDATED");

        if (all.isEmpty()) {
            g.setForegroundColor(t.text);
            Draw.centered(g, top + listRows / 2, w, EMPTY);
            return;
        }
        if (view.isEmpty()) {
            g.setForegroundColor(t.text);
            Draw.centered(g, top + listRows / 2, w,
                    Draw.truncate("no matches for \"" + query + "\" - [ESC] clear", w - 4));
            return;
        }

        selected = Math.min(selected, view.size() - 1);
        if (selected < scroll) scroll = selected;
        if (selected >= scroll + listRows) scroll = selected - listRows + 1;
        scroll = Math.max(0, Math.min(scroll, Math.max(0, view.size() - listRows)));

        for (int i = 0; i < listRows && scroll + i < view.size(); i++) {
            EntrySummary e = view.get(scroll + i);
            boolean sel = scroll + i == selected;
            int row = top + i;
            g.setForegroundColor(sel ? t.primary : t.text);
            g.putString(2, row, sel ? ">" : " ");
            g.putString(siteCol, row, Draw.truncate(e.getSite(), siteW));
            g.putString(userCol, row, Draw.truncate(e.getUser(), userW));
            g.setForegroundColor(sel ? t.primary : t.dim);
            g.putString(dateCol, row, dateFormat.format(e.getUpdatedAt()));
        }
    }

    private void drawDetails(TextGraphics g, Theme t, int w, int top) {
        EntrySummary e = current();
        if (e == null) return;
        int valueCol = 12;
        int room = w - valueCol - 2;
        g.setForegroundColor(t.dim);
        g.putString(2, top, "site:");
        g.putString(2, top + 1, "user:");
        g.putString(2, top + 2, "password:");
        g.putString(2, top + 3, "notes:");
        g.setForegroundColor(t.text);
        g.putString(valueCol, top, Draw.truncate(e.getSite(), room));
        g.putString(valueCol, top + 1, Draw.truncate(e.getUser(), room));
        g.putString(valueCol, top + 2, MASK);
        g.setForegroundColor(t.dim);
        g.putString(valueCol, top + 3, "(hidden)");
    }

    /** Test hooks. */
    EntrySummary selectedEntry() {
        return current();
    }

    boolean isSearching() {
        return searching;
    }
}
