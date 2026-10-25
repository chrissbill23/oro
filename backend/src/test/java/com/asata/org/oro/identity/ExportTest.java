package com.asata.org.oro.identity;

import com.asata.org.oro.PostgresIntegrationTest;
import com.asata.org.oro.TestTokens;
import com.asata.org.oro.context.Actor;
import com.asata.org.oro.conversation.entity.Conversation;
import com.asata.org.oro.enums.Product;
import com.asata.org.oro.identity.service.OrganizationSyncService;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The export of a person's data (ORO-LCH-018): what it wrote and did, never the messages of the others; by pages, so
 * every message is in it whatever their number.
 */
@AutoConfigureMockMvc
class ExportTest extends PostgresIntegrationTest {

    private static final String UMOJA_KEY = "test-umoja-integration-key-for-the-tests-0123456789";

    @Autowired
    private MockMvc mvc;
    @Autowired
    private OrganizationSyncService sync;

    private String export(String userId) throws Exception {
        return mvc.perform(get("/me/export").header(HttpHeaders.AUTHORIZATION, "Bearer " + TestTokens.oro(userId)))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"oro-export.json\""))
                .andExpect(header().string(HttpHeaders.CACHE_CONTROL, "no-store"))
                .andReturn().getResponse().getContentAsString();
    }

    @Test
    void anOroPersonExportsWhatItWroteAndDidNotTheOthers() throws Exception {
        UUID amani = oroUser("amani");
        UUID baraka = oroUser("baraka");
        oroUser("nia");
        Actor amaniActor = Actor.oro(amani);
        Conversation family = conversations.createGroup(amaniActor, "Family", null, List.of(baraka));
        acceptPending();
        say(amaniActor, family.getId(), "Dinner at 7");
        UUID gone = say(amaniActor, family.getId(), "My old address").message().getId();
        messages.delete(amaniActor, gone);
        UUID barakas = say(Actor.oro(baraka), family.getId(), "Baraka's own words").message().getId();
        messages.react(amaniActor, barakas, "👍", true);
        attachments.register(amaniActor, family.getId(), "menu.pdf", "application/pdf", 1200);
        mvc.perform(put("/blocks/oro-nia").header(HttpHeaders.AUTHORIZATION, "Bearer " + TestTokens.oro("oro-amani")))
                .andExpect(status().isNoContent());

        String file = export("oro-amani");

        assertThat((String) JsonPath.read(file, "$.format")).isEqualTo("oro-export/1");
        assertThat((String) JsonPath.read(file, "$.identity.userId")).isEqualTo("oro-amani");
        assertThat((List<String>) JsonPath.read(file, "$.conversations[*].name")).containsExactly("Family");
        assertThat((List<String>) JsonPath.read(file, "$.messagesSent[*].body")).containsExactly("Dinner at 7", null);
        // the deleted one: a deletion, its text gone
        assertThat((String) JsonPath.read(file, "$.messagesSent[1].deletedAt")).isNotNull();
        assertThat((String) JsonPath.read(file, "$.reactions[0].emoji")).isEqualTo("👍");
        assertThat((String) JsonPath.read(file, "$.filesSent[0].fileName")).isEqualTo("menu.pdf");
        assertThat((List<String>) JsonPath.read(file, "$.blocked[*].userId")).containsExactly("oro-nia");
        // never the words of the others
        assertThat(file).doesNotContain("Baraka's own words");
    }

    @Test
    void everyMessageWhateverTheirNumber() throws Exception {
        UUID amani = oroUser("amani");
        UUID baraka = oroUser("baraka");
        Conversation notes = conversations.createGroup(Actor.oro(amani), "Notes", null, List.of(baraka));
        acceptPending();
        int count = com.asata.org.oro.identity.service.ExportServiceAccess.page() * 2 + 5;
        for (int i = 0; i < count; i++) {
            say(Actor.oro(amani), notes.getId(), "note " + i);
        }
        List<String> bodies = JsonPath.read(export("oro-amani"), "$.messagesSent[*].body");
        assertThat(bodies).hasSize(count).startsWith("note 0", "note 1").endsWith("note " + (count - 1));
        assertThat(bodies).doesNotHaveDuplicates();
    }

    @Test
    void umojasPeopleAskTheirOrganizationWhichAsksOro() throws Exception {
        sync.sync(ACME, 1, List.of("umoja-jordan", "umoja-amara"), List.of());
        UUID jordan = identities.find(Product.UMOJA, "umoja-jordan").orElseThrow();
        UUID amara = identities.find(Product.UMOJA, "umoja-amara").orElseThrow();
        Conversation design = conversations.createGroup(Actor.umoja(jordan, ACME), "Design", null, List.of(amara));
        say(Actor.umoja(jordan, ACME), design.getId(), "The new logo");

        mvc.perform(get("/me/export").header(HttpHeaders.AUTHORIZATION, "Bearer " + TestTokens.umoja("umoja-jordan"))
                .header("x-organization-id", ACME.toString())).andExpect(status().isForbidden());

        String file = mvc.perform(get("/internal/identities/umoja-jordan/export").header("x-integration-key", UMOJA_KEY))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertThat((List<String>) JsonPath.read(file, "$.messagesSent[*].body")).containsExactly("The new logo");
        assertThat((List<String>) JsonPath.read(file, "$.organizations[*].organizationId")).containsExactly(ACME.toString());

        mvc.perform(get("/internal/identities/umoja-nobody/export").header("x-integration-key", UMOJA_KEY))
                .andExpect(status().isNotFound());
        mvc.perform(get("/internal/identities/umoja-jordan/export")).andExpect(status().isUnauthorized());
    }
}
