package com.asata.org.oro.live;

import com.asata.org.oro.PostgresIntegrationTest;
import com.asata.org.oro.TestTokens;
import com.asata.org.oro.context.Actor;
import com.asata.org.oro.conversation.entity.Conversation;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Whom an Oro person's presence is shared with: the people of its conversations, both members (a request or an
 * invitation not answered yet does not count, nor someone who left), and no block between them.
 */
@AutoConfigureMockMvc
class PresenceContactsTest extends PostgresIntegrationTest {

    @Autowired
    private PresenceContacts contacts;
    @Autowired
    private LiveHub hub;
    @Autowired
    private MockMvc mvc;

    private final List<UUID> streams = new java.util.ArrayList<>();

    @AfterEach
    void closeStreams() {
        streams.forEach(hub::close);
    }

    private void online(UUID identity, String userId) {
        hub.connect(Actor.oro(identity), userId, Instant.now().plusSeconds(600), contacts.of(identity));
        streams.add(identity);
    }

    @Test
    void theContactsOfAnOroPerson() {
        UUID amani = oroUser("amani");
        UUID baraka = oroUser("baraka");
        UUID nia = oroUser("nia");
        UUID kofi = oroUser("kofi");
        // Amani and Baraka: a conversation, accepted
        UUID direct = conversations.openDirect(Actor.oro(amani), baraka).getId();
        acceptPending();
        // Nia asked Amani: a request not answered, no contact yet
        conversations.openDirect(Actor.oro(nia), amani);
        // Kofi was in a group with Amani, and left it
        Conversation group = conversations.createGroup(Actor.oro(amani), "Old", null, List.of(kofi));
        conversations.accept(Actor.oro(kofi), group.getId());
        conversations.leave(Actor.oro(kofi), group.getId());

        assertThat(contacts.of(amani)).containsExactly(baraka);
        assertThat(contacts.of(baraka)).containsExactly(amani);
        assertThat(contacts.of(nia)).isEmpty();
        assertThat(contacts.of(kofi)).isEmpty();
        assertThat(direct).isNotNull();
    }

    @Test
    void presenceOverHttpAndABlock() throws Exception {
        UUID amani = oroUser("amani");
        UUID baraka = oroUser("baraka");
        UUID nia = oroUser("nia");
        conversations.openDirect(Actor.oro(amani), baraka);
        acceptPending();
        online(amani, "oro-amani");
        online(baraka, "oro-baraka");
        online(nia, "oro-nia");

        mvc.perform(get("/presence").header(HttpHeaders.AUTHORIZATION, "Bearer " + TestTokens.oro("oro-amani")))
                .andExpect(status().isOk()).andExpect(jsonPath("$").value(org.hamcrest.Matchers.containsInAnyOrder("oro-amani", "oro-baraka")));
        // Nia, a stranger: herself only
        mvc.perform(get("/presence").header(HttpHeaders.AUTHORIZATION, "Bearer " + TestTokens.oro("oro-nia")))
                .andExpect(jsonPath("$").value(org.hamcrest.Matchers.contains("oro-nia")));

        // Amani blocks Baraka: they no longer see each other
        mvc.perform(put("/blocks/oro-baraka").header(HttpHeaders.AUTHORIZATION, "Bearer " + TestTokens.oro("oro-amani")))
                .andExpect(status().isNoContent());
        assertThat(contacts.of(amani)).isEmpty();
        mvc.perform(get("/presence").header(HttpHeaders.AUTHORIZATION, "Bearer " + TestTokens.oro("oro-baraka")))
                .andExpect(jsonPath("$").value(org.hamcrest.Matchers.contains("oro-baraka")));
        mvc.perform(delete("/blocks/oro-baraka").header(HttpHeaders.AUTHORIZATION, "Bearer " + TestTokens.oro("oro-amani")))
                .andExpect(status().isNoContent());
        assertThat(contacts.of(amani)).containsExactly(baraka);
    }
}
