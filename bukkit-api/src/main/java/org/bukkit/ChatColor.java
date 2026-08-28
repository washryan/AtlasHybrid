package org.bukkit;

/** Minimal legacy colors required by the current compatibility subset. */
public enum ChatColor {
    RED('c'),
    GREEN('a'),
    YELLOW('e');

    public static final char COLOR_CHAR = '\u00a7';
    private final char code;

    ChatColor(char code) {
        this.code = code;
    }

    public char getChar() {
        return code;
    }

    @Override
    public String toString() {
        return new String(new char[] { COLOR_CHAR, code });
    }
}
