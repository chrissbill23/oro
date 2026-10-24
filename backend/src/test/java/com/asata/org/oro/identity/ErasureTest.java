package com.asata.org.oro.identity;

import com.asata.org.oro.PostgresIntegrationTest;
import com.asata.org.oro.TestTokens;
import com.asata.org.oro.context.Actor;
import com.asata.org.oro.conversation.entity.Conversation;
import com.asata.org.oro.enums.MessageType;
import com.asata.org.oro.enums.Product;
import com.asata.org.oro.identity.service.ErasureService;
import com.asata.org.oro.identity.service.OrganizationSyncService;
import com.asata.org.oro.message.dto.SendMessage;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Erasing a person's data in Oro: asked by Umoja for one of its people, or by an Oro person with its password. Its
 * messages become deletions, its reactions and uploads go, the system messages and mentions of others name an
 * anonymous id, and nothing that stays links to the person; the others' messages are not marked edited.
 */
@AutoConfigureMockMvc
class ErasureTest extends PostgresIntegrationTest {

    private static final String UMOJA_KEY = "test-umoja-integration-key-for-the-tests-0123456789";

    @Autowired
    private MockMvc mvc;
    @Autowired
    private ErasureService erasures;
    @Autowired
    private OrganizationSyncService sync;
    @Autowired
    private PasswordEncoder passwordEncoder;

    private String text(String sql, Object... args) {
        return admin.queryForObject(sql, String.class, args);
    }

    @Test
    void umojaErasesOneOfItsPeople() throws Exception {
        sync.sync(ACME, 1, List.of("umoja-jordan", "umoja-amara", "umoja-kwame"), List.of());
        UUID jordan = identities.find(Product.UMOJA, "umoja-jordan").orElseThrow();
        UUID amara = identities.find(Product.UMOJA, "umoja-amara").orElseThrow();
        UUID kwame = identities.find(Product.UMOJA, "umoja-kwame").orElseThrow();
        Actor jordanActor = Actor.umoja(jordan, ACME);
        Actor amaraActor = Actor.umoja(amara, ACME);
        // "Amara joined, added by Jordan" in the group's history
        Conversation group = conversations.createGroup(jordanActor, "Design", null, List.of(kwame));
        conversations.addMember(jordanActor, group.getId(), amara, true);
        assertThat(admin.queryForObject("SELECT count(*) FROM oro_message WHERE message_type = 'system' AND metadata->>'userId' = 'umoja-amara'",
                Integer.class)).isEqualTo(1);
        UUID amaraSaid = say(amaraActor, group.getId(), "My private notes on the launch").message().getId();
        say(amaraActor, group.getId(), "And my phone number");
        UUID jordanSaid = messages.send(jordanActor, group.getId(), new SendMessage("key-" + UUID.randomUUID(), MessageType.TEXT,
                "@Amara thanks", List.of("umoja-amara", "umoja-kwame"), List.of())).message().getId();
        messages.react(amaraActor, jordanSaid, "👍", true);
        UUID upload = attachments.register(amaraActor, group.getId(), "notes.pdf", "application/pdf", 1000).getId();

        mvc.perform(post("/internal/identities/umoja-amara/erase").header("x-integration-key", UMOJA_KEY))
                .andExpect(status().isAccepted());
        // at once: deactivated, out of its conversations
        assertThat(text("SELECT status FROM oro_messaging_identity WHERE id = ?", amara)).isEqualTo("deactivated");
        assertThat(admin.queryForObject("SELECT left_at IS NOT NULL FROM oro_conversation_member WHERE user_id = ?", Boolean.class, amara)).isTrue();

        assertThat(erasures.erasePending()).isEqualTo(1);

        // its messages: deletions, their text gone
        assertThat(admin.queryForObject("SELECT count(*) FROM oro_message WHERE sender_id = ? AND (deleted_at IS NULL OR body IS NOT NULL)",
                Integer.class, amara)).isZero();
        assertThat(text("SELECT deleted_by::text FROM oro_message WHERE id = ?", amaraSaid)).isEqualTo(amara.toString());
        // its reaction, its upload
        assertThat(admin.queryForObject("SELECT count(*) FROM oro_message_reaction WHERE user_id = ?", Integer.class, amara)).isZero();
        assertThat(admin.queryForObject("SELECT deleted_at IS NOT NULL FROM oro_attachment WHERE id = ?", Boolean.class, upload)).isTrue();
        // Jordan's message: the text stays, the mention is anonymous, not marked edited
        Map<String, Object> jordans = admin.queryForMap("SELECT body, metadata::text AS metadata, edited_at FROM oro_message WHERE id = ?", jordanSaid);
        assertThat(jordans).containsEntry("body", "@Amara thanks").containsEntry("edited_at", null);
        assertThat((String) jordans.get("metadata")).contains("erased-" + amara, "umoja-kwame");
        // the identity: anonymous; nothing in Oro names the person any more
        assertThat(text("SELECT external_user_id FROM oro_messaging_identity WHERE id = ?", amara)).isEqualTo("erased-" + amara);
        assertThat(admin.queryForObject("SELECT count(*) FROM oro_message WHERE metadata::text LIKE '%umoja-amara%'", Integer.class)).isZero();
        assertThat(admin.queryForObject("SELECT count(*) FROM oro_messaging_identity WHERE external_user_id = 'umoja-amara'", Integer.class)).isZero();
        assertThat(admin.queryForObject("SELECT count(*) FROM oro_organization_access WHERE identity_id = ?", Integer.class, amara)).isZero();
        // the system messages still say who added whom, anonymously for her
        assertThat(admin.queryForList("SELECT metadata->>'byUserId' FROM oro_message WHERE message_type = 'system' AND metadata->>'userId' = ?",
                String.class, "erased-" + amara)).containsExactly("umoja-jordan");
        assertThat(admin.queryForObject("SELECT messages_erased FROM oro_erasure WHERE identity_id = ?", Long.class, amara)).isEqualTo(2);

        // done: nothing more to do, asking again changes nothing
        assertThat(erasures.erasePending()).isZero();
        mvc.perform(post("/internal/identities/umoja-amara/erase").header("x-integration-key", UMOJA_KEY))
                .andExpect(status().isAccepted());
        // she comes back: a new identity, in none of the old conversations
        sync.sync(ACME, 2, List.of("umoja-jordan", "umoja-amara", "umoja-kwame"), List.of());
        UUID back = identities.find(Product.UMOJA, "umoja-amara").orElseThrow();
        assertThat(back).isNotEqualTo(amara);
        assertThat(conversations.list(Actor.umoja(back, ACME))).isEmpty();
    }

    @Test
    void anOroPersonErasesItsDataWithItsPassword() throws Exception {
        UUID account = UUID.randomUUID();
        admin.update("INSERT INTO asata_user(id, username, email, email_verified) VALUES (?, 'nia', 'nia@example.org', true)", account);
        admin.update("INSERT INTO asata_user_credential(user_id, password_hash) VALUES (?, ?)", account, passwordEncoder.encode("nia's passphrase"));
        // a session of Oro, and one of Umoja (the account is shared)
        admin.update("INSERT INTO asata_user_refresh_token(user_id, token_hash, family_id, expires_at, issuer) VALUES (?, ?, gen_random_uuid(), now() + interval '1 day', 'oro')",
                account, "c".repeat(64));
        admin.update("INSERT INTO asata_user_refresh_token(user_id, token_hash, family_id, expires_at) VALUES (?, ?, gen_random_uuid(), now() + interval '1 day')",
                account, "d".repeat(64));
        String token = TestTokens.oro(account.toString());
        mvc.perform(get("/me").header(HttpHeaders.AUTHORIZATION, "Bearer " + token)).andExpect(status().isOk());
        UUID nia = identities.find(Product.ORO, account.toString()).orElseThrow();
        Conversation family = conversations.createGroup(Actor.oro(nia), "Family", null, List.of(oroUser("baraka")));
        say(Actor.oro(nia), family.getId(), "Our address is ...");

        mvc.perform(post("/me/erase").header(HttpHeaders.AUTHORIZATION, "Bearer " + token).contentType(MediaType.APPLICATION_JSON)
                .content("{\"password\":\"wrong\"}")).andExpect(status().isUnauthorized());
        assertThat(admin.queryForObject("SELECT failed_attempts FROM asata_user_credential WHERE user_id = ?", Integer.class, account)).isEqualTo(1);
        mvc.perform(post("/me/erase").header(HttpHeaders.AUTHORIZATION, "Bearer " + token).contentType(MediaType.APPLICATION_JSON)
                .content("{\"password\":\"nia's passphrase\"}")).andExpect(status().isAccepted());

        // signed out of Oro, still in Umoja; its Asata account stays
        assertThat(admin.queryForObject("SELECT count(*) FROM asata_user_refresh_token WHERE user_id = ? AND revoked_at IS NULL AND issuer = 'oro'",
                Integer.class, account)).isZero();
        assertThat(admin.queryForObject("SELECT count(*) FROM asata_user_refresh_token WHERE user_id = ? AND revoked_at IS NULL AND issuer IS NULL",
                Integer.class, account)).isEqualTo(1);
        assertThat(admin.queryForObject("SELECT count(*) FROM asata_user WHERE id = ?", Integer.class, account)).isEqualTo(1);
        // its token no longer opens the old identity
        erasures.erasePending();
        assertThat(admin.queryForObject("SELECT count(*) FROM oro_message WHERE sender_id = ? AND body IS NOT NULL", Integer.class, nia)).isZero();
        assertThat(text("SELECT external_user_id FROM oro_messaging_identity WHERE id = ?", nia)).isEqualTo("erased-" + nia);
    }

    @Test
    void umojasPeopleAskTheirOrganization() throws Exception {
        sync.sync(ACME, 1, List.of("umoja-jordan"), List.of());
        mvc.perform(post("/me/erase").header(HttpHeaders.AUTHORIZATION, "Bearer " + TestTokens.umoja("umoja-jordan"))
                        .header("x-organization-id", ACME.toString()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"password\":\"anything\"}"))
                .andExpect(status().isForbidden());
    }

    /** the flag of the erasure is no way for a user to hide an edit: only the logins of oro_system have it */
    @Test
    void theFlagOfTheErasureHidesNoEditOfAUser() {
        UUID baraka = oroUser("baraka");
        Conversation group = conversations.createGroup(Actor.oro(baraka), "Mine", null, List.of());
        UUID said = say(Actor.oro(baraka), group.getId(), "Hello").message().getId();
        asUser(Actor.oro(baraka), jdbc -> {
            jdbc.queryForObject("SELECT set_config('oro.erasing', 'on', true)", String.class);
            return jdbc.update("UPDATE oro_message SET metadata = '{\"mentions\": []}' WHERE id = ?", said);
        });
        assertThat(admin.queryForObject("SELECT edited_at IS NOT NULL FROM oro_message WHERE id = ?", Boolean.class, said)).isTrue();
    }
}
