package com.asata.org.oro.identity;

import com.asata.org.oro.PostgresIntegrationTest;
import com.asata.org.oro.TestTokens;
import com.asata.org.oro.context.Actor;
import com.asata.org.oro.enums.Product;
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
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The names an Oro person may see: its own and those of the people of its conversations (a request's sender too, a
 * member who left), never a stranger's; Umoja's people are named by Umoja.
 */
@AutoConfigureMockMvc
class PeopleTest extends PostgresIntegrationTest {

    @Autowired
    private MockMvc mvc;

    /** an Asata account with its names, and its identity in Oro */
    private UUID person(String username, String given, String surname, List<UUID> accounts) {
        UUID account = UUID.randomUUID();
        admin.update("INSERT INTO asata_user(id, username, email) VALUES (?, ?, ?)", account, username, username + "@example.org");
        admin.update("INSERT INTO asata_user_personal_info(user_id, given_name, surname) VALUES (?, ?, ?)", account, given, surname);
        accounts.add(account);
        return identities.provision(Product.ORO, account.toString());
    }

    private String people(UUID asAccount, List<UUID> of) throws Exception {
        String ids = String.join(",", of.stream().map(UUID::toString).toList()) + ",erased-123,not-an-id";
        return mvc.perform(get("/people").param("userIds", ids)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + TestTokens.oro(asAccount.toString())))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
    }

    @Test
    void theNamesOfThePeopleOfOnesConversationsOnly() throws Exception {
        List<UUID> accounts = new java.util.ArrayList<>();
        UUID amani = person("amani", "Amani", "Mwangi", accounts);
        UUID baraka = person("baraka", "Baraka", "Otieno", accounts);
        UUID nia = person("nia", "Nia", "Mensah", accounts);
        UUID kofi = person("kofi", "Kofi", "Boateng", accounts);
        UUID amaniAccount = accounts.get(0), barakaAccount = accounts.get(1), niaAccount = accounts.get(2), kofiAccount = accounts.get(3);
        // Amani and Baraka in a group; Nia, a stranger, asks Amani; Kofi was in a group with Amani and left it
        conversations.createGroup(Actor.oro(amani), "Family", null, List.of(baraka));
        UUID old = conversations.createGroup(Actor.oro(amani), "Old", null, List.of(kofi)).getId();
        acceptPending();
        UUID request = conversations.openDirect(Actor.oro(nia), amani).getId();
        say(Actor.oro(nia), request, "Hello Amani");
        conversations.leave(Actor.oro(kofi), old);

        String seenByAmani = people(amaniAccount, accounts);
        assertThat((List<String>) JsonPath.read(seenByAmani, "$[*].userId"))
                .containsExactly(amaniAccount.toString(), barakaAccount.toString(), niaAccount.toString(), kofiAccount.toString());
        assertThat((String) JsonPath.read(seenByAmani, "$[1].givenName")).isEqualTo("Baraka");
        assertThat((String) JsonPath.read(seenByAmani, "$[1].surname")).isEqualTo("Otieno");
        assertThat((String) JsonPath.read(seenByAmani, "$[1].username")).isEqualTo("baraka");

        // Baraka shares nothing with Nia nor Kofi
        assertThat((List<String>) JsonPath.read(people(barakaAccount, accounts), "$[*].userId"))
                .containsExactly(amaniAccount.toString(), barakaAccount.toString());
        // Kofi left: he no longer sees Amani's name
        assertThat((List<String>) JsonPath.read(people(kofiAccount, accounts), "$[*].userId"))
                .containsExactly(kofiAccount.toString());
    }

    @Test
    void umojasPeopleAreNamedByUmoja() throws Exception {
        mvc.perform(get("/people").param("userIds", UUID.randomUUID().toString())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + TestTokens.umoja("umoja-jordan"))
                        .header("x-organization-id", ACME.toString()))
                .andExpect(status().isForbidden());
    }
}
