package com.asata.org.oro.safety;

import com.asata.org.oro.PostgresIntegrationTest;
import com.asata.org.oro.TestTokens;
import com.asata.org.oro.context.Actor;
import com.asata.org.oro.conversation.entity.Conversation;
import com.asata.org.oro.exception.Conflict;
import com.asata.org.oro.exception.Forbidden;
import com.asata.org.oro.identity.service.ErasureService;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.BadSqlGrammarException;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Who may reach whom in Oro (ORO-SEC-013): a stranger's first message waits in the requests, a stranger's group is an
 * invitation, blocks stop both, and reports keep a copy of what was reported.
 */
@AutoConfigureMockMvc
class SafetyTest extends PostgresIntegrationTest {

    @Autowired
    private MockMvc mvc;
    @Autowired
    private ReportService reports;
    @Autowired
    private ErasureService erasures;

    private UUID amani;
    private UUID nia;
    private UUID baraka;
    private Actor amaniActor;
    private Actor niaActor;
    private Actor barakaActor;

    @BeforeEach
    void people() {
        amani = oroUser("amani");
        nia = oroUser("nia");
        baraka = oroUser("baraka");
        amaniActor = Actor.oro(amani);
        niaActor = Actor.oro(nia);
        barakaActor = Actor.oro(baraka);
    }

    private ResultActions call(MockHttpServletRequestBuilder request, String userId, String json) throws Exception {
        request.header(HttpHeaders.AUTHORIZATION, "Bearer " + TestTokens.oro(userId));
        if (json != null) {
            request.contentType(MediaType.APPLICATION_JSON).content(json);
        }
        return mvc.perform(request);
    }

    @Test
    void aStrangersFirstMessageWaitsInTheRequests() throws Exception {
        UUID direct = conversations.openDirect(niaActor, amani).getId();
        say(niaActor, direct, "Hi Amani, we met at the conference");
        // one message until it is accepted
        assertThatThrownBy(() -> say(niaActor, direct, "Hello?")).isInstanceOf(Conflict.class)
                .hasMessageContaining("waits for an answer");

        // Amani: not in the list, in the requests; reads it, cannot answer before accepting
        assertThat(conversations.list(amaniActor)).isEmpty();
        call(get("/conversations/requests"), "oro-amani", null).andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(direct.toString()))
                .andExpect(jsonPath("$[0].invitedByUserId").value("oro-nia"))
                .andExpect(jsonPath("$[0].lastMessage.body").value("Hi Amani, we met at the conference"));
        assertThat(messages.page(amaniActor, direct, null, 10).messages()).hasSize(1);
        assertThatThrownBy(() -> say(amaniActor, direct, "Who are you?")).isInstanceOf(Conflict.class)
                .hasMessageContaining("Accept");

        call(post("/conversations/" + direct + "/accept"), "oro-amani", null).andExpect(status().isOk());
        assertThat(conversations.list(amaniActor)).hasSize(1);
        assertThat(conversations.requests(amaniActor)).isEmpty();
        say(amaniActor, direct, "Hello Nia");
        say(niaActor, direct, "Glad you remember");
    }

    @Test
    void peopleWhoShareAGroupAreNoStrangers() {
        Conversation family = conversations.createGroup(amaniActor, "Family", null, List.of(baraka));
        conversations.accept(barakaActor, family.getId());
        UUID direct = conversations.openDirect(barakaActor, amani).getId();
        say(barakaActor, direct, "Dinner on Sunday?");
        say(barakaActor, direct, "At 7");
        assertThat(conversations.requests(amaniActor)).isEmpty();
        say(amaniActor, direct, "Yes");
    }

    @Test
    void aDeclinedRequestEndsAndABlockStopsTheNext() throws Exception {
        UUID direct = conversations.openDirect(niaActor, amani).getId();
        say(niaActor, direct, "Buy my course");
        call(post("/conversations/" + direct + "/decline"), "oro-amani", null).andExpect(status().isNoContent());
        assertThat(conversations.requests(amaniActor)).isEmpty();
        assertThatThrownBy(() -> say(niaActor, direct, "Please?")).isInstanceOf(Forbidden.class);

        // asking again: a new request, declined with a block
        conversations.openDirect(niaActor, amani);
        assertThat(conversations.requests(amaniActor)).hasSize(1);
        call(post("/conversations/" + direct + "/decline?block=true"), "oro-amani", null).andExpect(status().isNoContent());
        assertThatThrownBy(() -> conversations.openDirect(niaActor, amani)).isInstanceOf(Forbidden.class)
                .hasMessage(com.asata.org.oro.conversation.service.ConversationService.CANNOT_MESSAGE);
        call(get("/blocks"), "oro-amani", null).andExpect(jsonPath("$[0]").value("oro-nia"));
    }

    @Test
    void aBlockStopsDirectMessagesBothWaysAndGroupAdditions() throws Exception {
        UUID direct = conversations.openDirect(amaniActor, baraka).getId();
        conversations.accept(barakaActor, direct);
        say(barakaActor, direct, "Hello");

        call(put("/blocks/oro-baraka"), "oro-amani", null).andExpect(status().isNoContent());
        assertThatThrownBy(() -> say(barakaActor, direct, "Why?")).isInstanceOf(Forbidden.class);
        assertThatThrownBy(() -> say(amaniActor, direct, "Bye")).isInstanceOf(Forbidden.class);
        assertThatThrownBy(() -> conversations.openDirect(barakaActor, amani)).isInstanceOf(Forbidden.class);
        Conversation barakas = conversations.createGroup(barakaActor, "Party", null, List.of());
        assertThatThrownBy(() -> conversations.addMember(barakaActor, barakas.getId(), amani, true)).isInstanceOf(Forbidden.class);
        // the database refuses it as well, past the service
        assertThat(rawRefused(() -> asUser(barakaActor, jdbc -> jdbc.update("""
                INSERT INTO oro_conversation_member(id, conversation_id, product_id, user_id, role, shared_history, added_by)
                VALUES (gen_random_uuid(), ?, 'ORO', ?, 'member', true, ?)""", barakas.getId(), amani, baraka)))).isTrue();
        // Baraka never sees Amani's blocks
        assertThat(rawCount(barakaActor, "SELECT count(*) FROM oro_identity_block")).isZero();

        call(delete("/blocks/oro-baraka"), "oro-amani", null).andExpect(status().isNoContent());
        say(barakaActor, direct, "Friends again?");
    }

    @Test
    void aStrangersGroupIsAnInvitation() throws Exception {
        Conversation club = conversations.createGroup(niaActor, "Book club", null, List.of());
        conversations.addMember(niaActor, club.getId(), amani, false);
        say(niaActor, club.getId(), "Welcome!");
        call(get("/conversations/requests"), "oro-amani", null).andExpect(jsonPath("$[0].name").value("Book club"))
                .andExpect(jsonPath("$[0].invitedByUserId").value("oro-nia"));
        // no "joined" while invited
        assertThat(admin.queryForObject("SELECT count(*) FROM oro_message WHERE conversation_id = ? AND message_type = 'system'",
                Integer.class, club.getId())).isZero();
        assertThatThrownBy(() -> say(amaniActor, club.getId(), "Hi")).isInstanceOf(Conflict.class);

        conversations.accept(amaniActor, club.getId());
        assertThat(admin.queryForObject("SELECT metadata->>'byUserId' FROM oro_message WHERE conversation_id = ? AND message_type = 'system'",
                String.class, club.getId())).isEqualTo("oro-nia");
        say(amaniActor, club.getId(), "Thanks");

        // Baraka declines his
        conversations.addMember(niaActor, club.getId(), baraka, false);
        conversations.decline(barakaActor, club.getId());
        assertThat(conversations.list(barakaActor)).isEmpty();
        assertThat(conversations.requests(barakaActor)).isEmpty();
    }

    @Test
    void aReportKeepsACopyOfWhatWasReported() throws Exception {
        UUID direct = conversations.openDirect(niaActor, amani).getId();
        UUID spam = say(niaActor, direct, "Click here to win a phone").message().getId();
        String report = call(post("/reports"), "oro-amani", """
                {"conversationId":"%s","messageIds":["%s"],"category":"spam","reason":"a scam"}""".formatted(direct, spam))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        UUID reportId = UUID.fromString(JsonPath.read(report, "$.id"));

        // the sender deletes it: the copy stays
        messages.delete(niaActor, spam);
        assertThat(admin.queryForObject("SELECT messages->0->>'body' FROM oro_report WHERE id = ?", String.class, reportId))
                .isEqualTo("Click here to win a phone");
        assertThat(admin.queryForObject("SELECT reported_id FROM oro_report WHERE id = ?", UUID.class, reportId)).isEqualTo(nia);
        // the API never reads the reports, nor reports what it cannot read
        assertThatThrownBy(() -> asUser(amaniActor, jdbc -> jdbc.queryForList("SELECT * FROM oro_report")))
                .isInstanceOf(BadSqlGrammarException.class);
        call(post("/reports"), "oro-baraka", """
                {"conversationId":"%s","messageIds":["%s"],"category":"spam"}""".formatted(direct, spam))
                .andExpect(status().isNotFound());
        call(post("/reports"), "oro-amani", """
                {"conversationId":"%s","messageIds":["%s"],"category":"rude"}""".formatted(direct, spam))
                .andExpect(status().isBadRequest());

        // 90 days later: gone
        admin.update("UPDATE oro_report SET expires_at = now() - interval '1 minute'");
        assertThat(reports.purgeExpired()).isEqualTo(1);
    }

    @Test
    void umojasPeopleHaveNoBlocks() throws Exception {
        mvc.perform(put("/blocks/umoja-amara").header(HttpHeaders.AUTHORIZATION, "Bearer " + TestTokens.umoja("umoja-jordan"))
                .header("x-organization-id", ACME.toString())).andExpect(status().isForbidden());
    }

    @Test
    void anErasureTakesItsBlocks() throws Exception {
        call(put("/blocks/oro-nia"), "oro-amani", null).andExpect(status().isNoContent());
        erasures.request(com.asata.org.oro.enums.Product.ORO, "oro-nia", "integration");
        erasures.erasePending();
        assertThat(admin.queryForObject("SELECT count(*) FROM oro_identity_block", Integer.class)).isZero();
    }

    private static boolean rawRefused(Runnable action) {
        try {
            action.run();
            return false;
        } catch (RuntimeException e) {
            return true;
        }
    }
}
