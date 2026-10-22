package com.asata.org.oro.message;

import com.asata.org.oro.message.service.Emojis;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;

/** a reaction is one emoji, whatever it is, and nothing else */
class EmojisTest {

    @ParameterizedTest
    @ValueSource(strings = {"👍", "❤️", "❤", "🎉", "🙌🏾", "👩🏽‍💻", "👨‍👩‍👧‍👦", "🏳️‍🌈", "🇨🇩", "🇬🇭", "🏴󠁧󠁢󠁳󠁣󠁴󠁿", "☕", "©️", "🫠", "🧑🏿‍🤝‍🧑🏻"})
    void oneEmojiIsAReaction(String emoji) {
        assertThat(Emojis.isOne(emoji)).as(emoji).isTrue();
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "a", "ok", "1", "#", "1️⃣", "👍👍", "👍 ", "🇨", "🇨🇩🇬🇭", "é", "→", "<script>", "‍", "️",
            "🏻", "👍a"})
    void anythingElseIsNot(String text) {
        assertThat(Emojis.isOne(text)).as(text).isFalse();
    }
}
