package com.asata.org.oro.message.service;

import java.text.BreakIterator;
import java.util.Locale;

/**
 * What a reaction may be: one emoji, as Unicode defines them (its emoji properties, not a list), with its variations
 * (skin tones, ZWJ sequences such as 👩‍💻, flags). Never text, digits, several emojis, nor a keycap (1️⃣: an ASCII digit,
 * which the database refuses too).
 */
public final class Emojis {

    /** 16 UTF-16 units at most: the column, and the longest sequences in use (a family, a flag of a region) */
    public static final int MAX_LENGTH = 16;

    private Emojis() {
    }

    public static boolean isOne(String text) {
        if (text == null || text.isEmpty() || text.length() > MAX_LENGTH) {
            return false;
        }
        // one grapheme cluster: what a reader sees as one character (BreakIterator follows Unicode's rules since JDK 20)
        BreakIterator characters = BreakIterator.getCharacterInstance(Locale.ROOT);
        characters.setText(text);
        if (characters.next() != text.length()) {
            return false;
        }
        boolean pictographic = false;
        int regionalIndicators = 0;
        for (int codePoint : text.codePoints().toArray()) {
            if (codePoint < 0x80) {
                return false;
            }
            if (Character.isExtendedPictographic(codePoint)) {
                pictographic = true;
            } else if (codePoint >= 0x1F1E6 && codePoint <= 0x1F1FF) {
                regionalIndicators++;
            } else if (!Character.isEmojiComponent(codePoint) && !Character.isEmoji(codePoint)) {
                return false;
            }
        }
        // a pictograph, or a flag (two regional indicators)
        return pictographic || regionalIndicators == 2;
    }
}
