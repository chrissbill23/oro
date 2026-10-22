package com.asata.org.oro.security;

import com.asata.org.oro.PostgresIntegrationTest;
import com.asata.org.oro.context.Actor;
import com.asata.org.oro.conversation.entity.Conversation;
import com.asata.org.oro.enums.Product;
import com.asata.org.oro.exception.NotFound;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Oro and Umoja never meet: their identities, conversations and members stay in their product, whatever the
 * application asks (the services) and whatever a bug writes (raw rows, refused by the constraints).
 */
class ProductIsolationTest extends PostgresIntegrationTest {

    private UUID amani;
    private UUID baraka;
    private UUID jordan;
    private UUID amara;
    private Actor amaniActor;
    private Actor jordanActor;

    @BeforeEach
    void identities() {
        amani = oroUser("amani");
        baraka = oroUser("baraka");
        jordan = umojaUser("jordan", ACME);
        amara = umojaUser("amara", ACME);
        amaniActor = Actor.oro(amani);
        jordanActor = Actor.umoja(jordan, ACME);
    }

    @Test
    void twoProductsCanHaveTheSameExternalUserIdWithoutSharingAnIdentity() {
        UUID oro = identities.provision(Product.ORO, "user-42");
        UUID umoja = identities.provision(Product.UMOJA, "user-42");

        assertThat(oro).isNotEqualTo(umoja);
        // provisioning is idempotent
        assertThat(identities.provision(Product.ORO, "user-42")).isEqualTo(oro);
        assertThat(identities.resolve(Product.ORO, "user-42")).isEqualTo(oro);
        assertThat(identities.resolve(Product.UMOJA, "user-42")).isEqualTo(umoja);
        assertThat(count("SELECT count(*) FROM oro_messaging_identity WHERE external_user_id = 'user-42'")).isEqualTo(2);
    }

    @Test
    void anOroIdentityCannotJoinAUmojaConversation() {
        Conversation design = conversations.createGroup(jordanActor, "Design", null, List.of(amara));

        // the service: the Oro identity does not exist for a Umoja actor (RLS)
        assertThatThrownBy(() -> conversations.addMember(jordanActor, design.getId(), amani, true))
                .isInstanceOf(NotFound.class);
        // a raw row, either product: the composite foreign keys refuse it
        assertRefused(() -> admin.update("""
                INSERT INTO oro_conversation_member(conversation_id, product_id, user_id) VALUES (?, 'UMOJA', ?)""",
                design.getId(), amani), "fk_oro_conversation_member_user");
        assertRefused(() -> admin.update("""
                INSERT INTO oro_conversation_member(conversation_id, product_id, user_id) VALUES (?, 'ORO', ?)""",
                design.getId(), amani), "fk_oro_conversation_member_conversation");
    }

    @Test
    void aUmojaIdentityCannotJoinAnOroConversation() {
        Conversation family = conversations.createGroup(amaniActor, "Family", null, List.of(baraka));

        assertThatThrownBy(() -> conversations.addMember(amaniActor, family.getId(), jordan, true))
                .isInstanceOf(NotFound.class);
        assertThatThrownBy(() -> conversations.openDirect(amaniActor, jordan)).isInstanceOf(NotFound.class);
        assertThatThrownBy(() -> conversations.createGroup(amaniActor, "Mixed", null, List.of(jordan)))
                .isInstanceOf(NotFound.class);
        assertRefused(() -> admin.update("""
                INSERT INTO oro_conversation_member(conversation_id, product_id, user_id) VALUES (?, 'ORO', ?)""",
                family.getId(), jordan), "fk_oro_conversation_member_user");
    }

    @Test
    void aConversationCannotReferenceACreatorOfAnotherProduct() {
        assertRefused(() -> admin.update("""
                INSERT INTO oro_conversation(product_id, context_type, organization_id, conversation_type, name, created_by)
                VALUES ('UMOJA', 'organization', ?, 'group', 'Stolen', ?)""", ACME, amani),
                "fk_oro_conversation_created_by");
        // nor a direct pair across the products
        assertRefused(() -> admin.update("""
                INSERT INTO oro_conversation(product_id, context_type, conversation_type, direct_identity_low,
                                             direct_identity_high, created_by)
                VALUES ('ORO', 'personal', 'direct', LEAST(?::uuid, ?::uuid), GREATEST(?::uuid, ?::uuid), ?)""",
                amani, jordan, amani, jordan, amani), "fk_oro_conversation_direct");
        // nor can the API create one for somebody else
        assertRefused(() -> asUser(amaniActor, jdbc -> jdbc.update("""
                INSERT INTO oro_conversation(id, product_id, context_type, conversation_type, name, created_by)
                VALUES (gen_random_uuid(), 'ORO', 'personal', 'group', 'Not mine', ?)""", baraka)),
                "row-level security");
    }

    @Test
    void anInvalidOrganizationContextIsRejected() {
        // a personal conversation has no organization, an organization one has its organization
        assertRefused(() -> admin.update("""
                INSERT INTO oro_conversation(product_id, context_type, organization_id, conversation_type, name, created_by)
                VALUES ('ORO', 'personal', ?, 'group', 'Family', ?)""", ACME, amani), "ck_oro_conversation_context");
        assertRefused(() -> admin.update("""
                INSERT INTO oro_conversation(product_id, context_type, conversation_type, name, created_by)
                VALUES ('UMOJA', 'organization', 'group', 'Design', ?)""", jordan), "ck_oro_conversation_context");
        assertRefused(() -> admin.update("""
                INSERT INTO oro_conversation(product_id, context_type, organization_id, conversation_type, name, created_by)
                VALUES ('UMOJA', 'personal', ?, 'group', 'Design', ?)""", ACME, jordan), "ck_oro_conversation_context");
        // an actor carries the context of its product
        assertThatThrownBy(() -> Actor.umoja(jordan, null)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new Actor(Product.ORO, amani, ACME)).isInstanceOf(IllegalArgumentException.class);
        // an organization the creator has no access to
        assertRefused(() -> conversations.createGroup(Actor.umoja(jordan, GLOBEX), "Globex", null, List.of()),
                "has access to the organization");
    }

    @Test
    void aMemberOfAnOrganizationConversationHasAccessToTheOrganization() {
        UUID bob = umojaUser("bob", GLOBEX);
        Conversation design = conversations.createGroup(jordanActor, "Design", null, List.of(amara));

        assertRefused(() -> conversations.addMember(jordanActor, design.getId(), bob, true),
                "has access to the organization");
        assertRefused(() -> conversations.openDirect(jordanActor, bob), "has access to the organization");
    }
}
