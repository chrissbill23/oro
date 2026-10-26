package com.asata.org.oro.identity;

import com.asata.org.oro.PostgresIntegrationTest;
import com.asata.org.oro.context.Actor;
import com.asata.org.oro.conversation.entity.Conversation;
import com.asata.org.oro.enums.MessageType;
import com.asata.org.oro.enums.Product;
import com.asata.org.oro.identity.service.ErasureService;
import com.asata.org.oro.identity.service.OrganizationSyncService;
import com.asata.org.oro.message.dto.SendMessage;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * A Umoja person in two organizations: what one organization exports or erases is in its conversations only; the
 * other organization's never leaves it, nor changes.
 */
@AutoConfigureMockMvc
class OrganizationScopeTest extends PostgresIntegrationTest {

    private static final String UMOJA_KEY = "test-umoja-integration-key-for-the-tests-0123456789";

    @Autowired
    private MockMvc mvc;
    @Autowired
    private OrganizationSyncService sync;
    @Autowired
    private ErasureService erasures;

    private UUID jordan;
    private Conversation acmeDesign;
    private Conversation globexBoard;
    private UUID amarasMention;

    @BeforeEach
    void jordanInTwoOrganizations() {
        sync.sync(ACME, 1, List.of("umoja-jordan", "umoja-amara"), List.of());
        sync.sync(GLOBEX, 1, List.of("umoja-jordan", "umoja-kofi"), List.of());
        jordan = identities.find(Product.UMOJA, "umoja-jordan").orElseThrow();
        UUID amara = identities.find(Product.UMOJA, "umoja-amara").orElseThrow();
        UUID kofi = identities.find(Product.UMOJA, "umoja-kofi").orElseThrow();
        Actor jordanAtAcme = Actor.umoja(jordan, ACME);
        Actor jordanAtGlobex = Actor.umoja(jordan, GLOBEX);

        acmeDesign = conversations.createGroup(jordanAtAcme, "Design", null, List.of(amara));
        say(jordanAtAcme, acmeDesign.getId(), "Acme's new logo");
        amarasMention = messages.send(Actor.umoja(amara, ACME), acmeDesign.getId(), new SendMessage("key-" + UUID.randomUUID(),
                MessageType.TEXT, "@Jordan looks good", List.of("umoja-jordan"), List.of())).message().getId();
        messages.react(jordanAtAcme, amarasMention, "👍", true);
        attachments.register(jordanAtAcme, acmeDesign.getId(), "acme.pdf", "application/pdf", 1000);

        globexBoard = conversations.createGroup(jordanAtGlobex, "Board", null, List.of(kofi));
        say(jordanAtGlobex, globexBoard.getId(), "Globex's budget");
        attachments.register(jordanAtGlobex, globexBoard.getId(), "globex.pdf", "application/pdf", 1000);
    }

    @Test
    void anOrganizationExportsWhatIsInItsConversationsOnly() throws Exception {
        String file = mvc.perform(get("/internal/identities/umoja-jordan/export").param("organizationId", ACME.toString())
                        .header("x-integration-key", UMOJA_KEY))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();

        assertThat((String) JsonPath.read(file, "$.organizationId")).isEqualTo(ACME.toString());
        assertThat((List<String>) JsonPath.read(file, "$.messagesSent[*].body")).containsExactly("Acme's new logo");
        assertThat((List<String>) JsonPath.read(file, "$.conversations[*].name")).containsExactly("Design");
        assertThat((List<String>) JsonPath.read(file, "$.organizations[*].organizationId")).containsExactly(ACME.toString());
        assertThat((List<String>) JsonPath.read(file, "$.filesSent[*].fileName")).containsExactly("acme.pdf");
        assertThat((List<String>) JsonPath.read(file, "$.reactions[*].emoji")).containsExactly("👍");
        assertThat(file).doesNotContain("Globex", "globex", GLOBEX.toString(), "blocked", "notificationDevices");

        // without the organization: everything (Umoja's own, for the person's whole account)
        String all = mvc.perform(get("/internal/identities/umoja-jordan/export").header("x-integration-key", UMOJA_KEY))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertThat((List<String>) JsonPath.read(all, "$.messagesSent[*].body")).containsExactly("Acme's new logo", "Globex's budget");
    }

    @Test
    void anOrganizationErasesWhatIsInItsConversationsOnly() throws Exception {
        for (int i = 0; i < 2; i++) {
            mvc.perform(post("/internal/identities/umoja-jordan/erase").param("organizationId", ACME.toString())
                    .header("x-integration-key", UMOJA_KEY)).andExpect(status().isAccepted());
        }
        // asked twice: once
        assertThat(admin.queryForObject("SELECT count(*) FROM oro_organization_erasure", Integer.class)).isEqualTo(1);
        String anonymous = admin.queryForObject("SELECT anonymous_id FROM oro_organization_erasure", String.class);
        assertThat(erasures.erasePending()).isEqualTo(1);

        // at Acme: its messages deletions, its reaction and upload gone, Amara's mention of it anonymous
        assertThat(admin.queryForList("SELECT body FROM oro_message WHERE conversation_id = ? AND sender_id = ?", String.class,
                acmeDesign.getId(), jordan)).containsOnlyNulls();
        assertThat(admin.queryForObject("SELECT count(*) FROM oro_message_reaction WHERE user_id = ?", Integer.class, jordan)).isZero();
        assertThat(admin.queryForObject("SELECT metadata::text FROM oro_message WHERE id = ?", String.class, amarasMention))
                .contains(anonymous).doesNotContain("umoja-jordan").doesNotContain("erased-" + jordan);
        assertThat(admin.queryForObject("SELECT edited_at FROM oro_message WHERE id = ?", Object.class, amarasMention)).isNull();
        // the "joined" messages of Acme name the anonymous id; Globex's still name Jordan
        assertThat(admin.queryForObject("""
                SELECT count(*) FROM oro_message m JOIN oro_conversation c ON c.id = m.conversation_id
                WHERE c.organization_id = ? AND m.metadata::text LIKE '%umoja-jordan%'""", Integer.class, ACME)).isZero();

        // at Globex: nothing changed
        assertThat(admin.queryForList("SELECT body FROM oro_message WHERE conversation_id = ? AND sender_id = ?", String.class,
                globexBoard.getId(), jordan)).containsExactly("Globex's budget");
        assertThat(admin.queryForObject("SELECT count(*) FROM oro_attachment WHERE file_name = 'globex.pdf' AND deleted_at IS NULL",
                Integer.class)).isEqualTo(1);
        assertThat(admin.queryForObject("SELECT count(*) FROM oro_attachment WHERE file_name = 'acme.pdf' AND deleted_at IS NULL",
                Integer.class)).isZero();
        // the identity: as it was
        Map<String, Object> identity = admin.queryForMap("SELECT external_user_id, status FROM oro_messaging_identity WHERE id = ?", jordan);
        assertThat(identity).containsEntry("external_user_id", "umoja-jordan").containsEntry("status", "active");
        // done: the anonymous id forgotten, nothing links it to Jordan
        assertThat(admin.queryForObject("SELECT anonymous_id FROM oro_organization_erasure", String.class)).isNull();
    }
}
