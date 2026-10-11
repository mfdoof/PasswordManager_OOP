package com.doof.passwordmanager.ui.tui.widget;

import com.doof.passwordmanager.ui.tui.Theme;
import com.doof.passwordmanager.util.PasswordPolicy;
import com.googlecode.lanterna.graphics.TextGraphics;

/** Strength bar + master password checklist (Create and Change master password), 3 rows. */
public final class PolicyView {

    private PolicyView() {}

    /** Draws at {@code col}: row = strength, row+1 / row+2 = checklist. Does not keep {@code pw}. */
    public static void draw(TextGraphics g, Theme t, int col, int row, char[] pw) {
        StrengthMeter.Level level = StrengthMeter.level(pw);
        g.setForegroundColor(t.dim);
        g.putString(col, row, "strength ");
        g.setForegroundColor(level.compareTo(StrengthMeter.Level.STRONG) >= 0 ? t.success
                : level.compareTo(StrengthMeter.Level.FAIR) <= 0 && level != StrengthMeter.Level.NONE ? t.error : t.text);
        g.putString(col + 9, row, StrengthMeter.bar(pw) + " " + level.label);

        PasswordPolicy.Checks c = PasswordPolicy.check(pw);
        g.setForegroundColor(t.text);
        g.putString(col, row + 1, box(c.length) + " " + PasswordPolicy.MASTER_MIN_LENGTH + "+ chars  "
                + box(c.upper) + " upper  " + box(c.lower) + " lower");
        g.putString(col, row + 2, box(c.digit) + " digit      " + box(c.symbol) + " symbol (optional)");
    }

    private static String box(boolean checked) {
        return checked ? "[x]" : "[ ]";
    }
}
