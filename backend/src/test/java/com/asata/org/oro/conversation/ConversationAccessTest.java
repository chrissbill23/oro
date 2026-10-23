package com.asata.org.oro.conversation;

import com.asata.org.oro.PostgresIntegrationTest;
import com.asata.org.oro.context.Actor;
import com.asata.org.oro.conversation.dto.ConversationSummary;
import com.asata.org.oro.conversation.entity.Conversation;
import com.asata.org.oro.conversation.entity.ConversationMember;
import com.asata.org.oro.enums.LeftReason;
import com.asata.org.oro.exception.Conflict;
import com.asata.org.oro.exception.NotFound;
import com.asata.org.oro.message.entity.Message;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** who reads a conversation: its active members only, in their product and organization, after their history start */
class ConversationAccessTest extends PostgresIntegrationTest {

    private UUID amani;
    private UUID baraka;
    private UUID chausiku;
    private Actor amaniActor;
    private Actor barakaActor;
    private Actor chausikuActor;

    @BeforeEach
    void oroUsers() {
        amani = oroUser("amani");
        baraka = oroUser("baraka");
        chausiku = oroUser("chausiku");
        amaniActor = Actor.oro(amani);
        barakaActor = Actor.oro(baraka);
        chausikuActor = Actor.oro(chausiku);
    }

    private long visibleMessages(Actor actor, UUID conversationId) {
        return rawCount(actor, "SELECT count(*) FROM oro_message WHERE conversation_id = ?", conversationId);
    }

    private List<Long> seqs(Actor actor, UUID conversationId) {
        return messages.page(actor, conversationId, null, 50).messages().stream().map(Message::getSeq).toList();
    }

    @Test
    void aUserCannotReadAConversationTheyDoNotBelongTo() {
        Conversation direct = conversations.openDirect(amaniActor, baraka);
        say(amaniActor, direct.getId(), "Just for Baraka");

        assertThatThrownBy(() -> messages.page(chausikuActor, direct.getId(), null, 20)).isInstanceOf(NotFound.class);
        assertThatThrownBy(() -> say(chausikuActor, direct.getId(), "Hello?")).isInstanceOf(NotFound.class);
        assertThat(conversations.list(chausikuActor)).isEmpty();
        // RLS alone, a raw read by the guessed id
        assertThat(visibleMessages(chausikuActor, direct.getId())).isZero();
        assertThat(rawCount(chausikuActor, "SELECT count(*) FROM oro_conversation_member WHERE conversation_id = ?",
                direct.getId())).isZero();
        assertThat(visibleMessages(barakaActor, direct.getId())).isEqualTo(1);
    }

    @Test
    void aUserCannotReadTheMessagesOfAnotherOrganization() {
        UUID jordan = umojaUser("jordan", ACME);
        UUID amara = umojaUser("amara", ACME);
        UUID bob = umojaUser("bob", GLOBEX);
        Actor jordanActor = Actor.umoja(jordan, ACME);
        Conversation design = conversations.createGroup(jordanActor, "Design", null, List.of(amara));
        say(jordanActor, design.getId(), "Acme only");
        Conversation globex = conversations.createGroup(Actor.umoja(bob, GLOBEX), "Globex", null, List.of());

        // Bob, in his organization or claiming Acme: nothing
        assertThatThrownBy(() -> messages.page(Actor.umoja(bob, GLOBEX), design.getId(), null, 20)).isInstanceOf(NotFound.class);
        assertThatThrownBy(() -> messages.page(Actor.umoja(bob, ACME), design.getId(), null, 20)).isInstanceOf(NotFound.class);
        assertThat(visibleMessages(Actor.umoja(bob, ACME), design.getId())).isZero();
        // Jordan, scoped to another organization than the conversation's: nothing either
        assertThatThrownBy(() -> messages.page(Actor.umoja(jordan, GLOBEX), design.getId(), null, 20)).isInstanceOf(NotFound.class);
        assertThatThrownBy(() -> messages.page(jordanActor, globex.getId(), null, 20)).isInstanceOf(NotFound.class);
        // the list of a context holds its organization only
        assertThat(conversations.list(Actor.umoja(jordan, GLOBEX))).isEmpty();
        assertThat(conversations.list(jordanActor)).extracting(ConversationSummary::id).containsExactly(design.getId());
    }

    @Test
    void aMemberWhoLeavesCanNoLongerReadNorSendNorComeBackAlone() {
        Conversation family = conversations.createGroup(amaniActor, "Family", null, List.of(baraka, chausiku));
        say(amaniActor, family.getId(), "Before leaving");

        conversations.leave(chausikuActor, family.getId());

        assertThatThrownBy(() -> messages.page(chausikuActor, family.getId(), null, 20)).isInstanceOf(NotFound.class);
        assertThatThrownBy(() -> say(chausikuActor, family.getId(), "Still here?")).isInstanceOf(NotFound.class);
        assertThat(visibleMessages(chausikuActor, family.getId())).isZero();
        // past the services: rejoining alone and sending are refused by the triggers (before RLS)
        assertRefused(() -> asUser(chausikuActor, jdbc -> jdbc.update("""
                INSERT INTO oro_conversation_member(id, conversation_id, product_id, user_id) VALUES (gen_random_uuid(), ?, 'ORO', ?)""",
                family.getId(), chausiku)), "only the owner and the admins add members");
        assertRefused(() -> asUser(chausikuActor, jdbc -> jdbc.update("""
                INSERT INTO oro_message(id, conversation_id, product_id, seq, sender_id, client_message_id, body)
                VALUES (gen_random_uuid(), ?, 'ORO', 1, ?, 'raw-key-0001', 'Sneaky')""", family.getId(), chausiku)),
                "active member");
        assertThat(count("SELECT count(*) FROM oro_conversation_member WHERE user_id = ? AND left_reason = 'left'", chausiku)).isEqualTo(1);
    }

    @Test
    void aRemovedMemberLosesAccessAndARejoinFollowsTheHistoryChoice() {
        Conversation family = conversations.createGroup(amaniActor, "Family", null, List.of(baraka));
        say(amaniActor, family.getId(), "One");
        say(amaniActor, family.getId(), "Two");

        conversations.removeMember(amaniActor, family.getId(), baraka);
        assertThatThrownBy(() -> messages.page(barakaActor, family.getId(), null, 20)).isInstanceOf(NotFound.class);
        assertThat(visibleMessages(barakaActor, family.getId())).isZero();
        say(amaniActor, family.getId(), "Three, while Baraka is away");

        // added back, privacy first: only what comes next (the system messages of the removal, 3, and of the return, 5)
        ConversationMember back = conversations.addMember(amaniActor, family.getId(), baraka, false);
        assertThat(back.getHistoryFromSeq()).isEqualTo(4);
        say(amaniActor, family.getId(), "Four");
        assertThat(seqs(barakaActor, family.getId())).containsExactly(6L, 5L);
        assertThat(visibleMessages(barakaActor, family.getId())).isEqualTo(2);
        // two periods: the removed one stays ended
        assertThat(count("SELECT count(*) FROM oro_conversation_member WHERE user_id = ?", baraka)).isEqualTo(2);
    }

    @Test
    void theAdminChoosesWhetherANewMemberSeesTheHistory() {
        Conversation family = conversations.createGroup(amaniActor, "Family", null, List.of());
        say(amaniActor, family.getId(), "One");
        say(amaniActor, family.getId(), "Two");

        conversations.addMember(amaniActor, family.getId(), baraka, true);
        conversations.addMember(amaniActor, family.getId(), chausiku, false);
        say(amaniActor, family.getId(), "Three");

        // 3 and 4: the system messages of their arrivals
        assertThat(seqs(barakaActor, family.getId())).containsExactly(5L, 4L, 3L, 2L, 1L);
        assertThat(seqs(chausikuActor, family.getId())).containsExactly(5L, 4L);
        assertThat(visibleMessages(chausikuActor, family.getId())).isEqualTo(2);
        // the history of a membership never changes afterwards
        assertRefused(() -> admin.update("UPDATE oro_conversation_member SET shared_history = TRUE WHERE user_id = ?", chausiku),
                "never change");
    }

    @Test
    void aRevokedOrganizationAccessEndsTheMembershipsInTheOrganization() {
        UUID jordan = umojaUser("jordan", ACME, GLOBEX);
        UUID amara = umojaUser("amara", ACME, GLOBEX);
        Actor amaraActor = Actor.umoja(amara, ACME);
        Conversation design = conversations.createGroup(Actor.umoja(jordan, ACME), "Design", null, List.of(amara));
        Conversation globex = conversations.createGroup(Actor.umoja(jordan, GLOBEX), "Globex", null, List.of(amara));

        organizationAccess.revoke(amara, ACME);

        assertThatThrownBy(() -> messages.page(amaraActor, design.getId(), null, 20)).isInstanceOf(NotFound.class);
        assertThat(count("SELECT count(*) FROM oro_conversation_member WHERE user_id = ? AND left_reason = 'access_revoked'", amara))
                .isEqualTo(1);
        // the other organization is untouched
        assertThat(messages.page(Actor.umoja(amara, GLOBEX), globex.getId(), null, 20).messages()).isEmpty();
        // granted again, a member comes back only when an admin adds it
        organizationAccess.grant(amara, ACME);
        assertThatThrownBy(() -> messages.page(amaraActor, design.getId(), null, 20)).isInstanceOf(NotFound.class);
    }

    @Test
    void aDeactivatedIdentityLeavesEverything() {
        Conversation direct = conversations.openDirect(amaniActor, baraka);
        Conversation family = conversations.createGroup(amaniActor, "Family", null, List.of(baraka));

        identities.deactivate(baraka);

        assertThat(count("SELECT count(*) FROM oro_conversation_member WHERE user_id = ? AND left_at IS NULL", baraka)).isZero();
        assertThat(count("SELECT count(*) FROM oro_conversation_member WHERE user_id = ? AND left_reason = ?", baraka,
                LeftReason.DEACTIVATED.code())).isEqualTo(2);
        assertThatThrownBy(() -> messages.page(barakaActor, direct.getId(), null, 20)).isInstanceOf(NotFound.class);
        assertThatThrownBy(() -> conversations.addMember(amaniActor, family.getId(), baraka, true)).isInstanceOf(NotFound.class);
        assertRefused(() -> admin.update("UPDATE oro_messaging_identity SET status = 'active', deactivated_at = NULL WHERE id = ?", baraka),
                "stays deactivated");
    }

    @Test
    void aSuspendedIdentityKeepsItsMembershipsButReadsNothing() {
        Conversation direct = conversations.openDirect(amaniActor, baraka);
        say(amaniActor, direct.getId(), "Hello");

        identities.suspend(baraka);
        assertThat(visibleMessages(barakaActor, direct.getId())).isZero();
        assertThatThrownBy(() -> say(barakaActor, direct.getId(), "Me?")).isInstanceOf(NotFound.class);
        assertRefused(() -> asUser(barakaActor, jdbc -> jdbc.update("""
                INSERT INTO oro_message(id, conversation_id, product_id, seq, sender_id, client_message_id, body)
                VALUES (gen_random_uuid(), ?, 'ORO', 1, ?, 'raw-key-0002', 'Me?')""", direct.getId(), baraka)),
                "active member");

        identities.reactivate(baraka);
        assertThat(visibleMessages(barakaActor, direct.getId())).isEqualTo(1);
    }

    @Test
    void concurrentCreationsOfTheSameDirectConversationMakeOne() throws Exception {
        List<UUID> ids = concurrently(16, i -> (i % 2 == 0
                ? conversations.openDirect(amaniActor, baraka)
                : conversations.openDirect(barakaActor, amani)).getId());

        assertThat(ids).containsOnly(ids.getFirst());
        assertThat(count("SELECT count(*) FROM oro_conversation WHERE conversation_type = 'direct'")).isEqualTo(1);
        assertThat(count("SELECT count(*) FROM oro_conversation_member WHERE conversation_id = ? AND left_at IS NULL", ids.getFirst()))
                .isEqualTo(2);
        // a raw duplicate is refused by the unique index
        assertRefused(() -> admin.update("""
                INSERT INTO oro_conversation(product_id, context_type, conversation_type, direct_identity_low, direct_identity_high, created_by)
                VALUES ('ORO', 'personal', 'direct', LEAST(?::uuid, ?::uuid), GREATEST(?::uuid, ?::uuid), ?)""",
                amani, baraka, amani, baraka, amani), "uq_oro_conversation_direct_personal");
    }

    @Test
    void theSamePairHasOneDirectConversationPerOrganization() {
        UUID jordan = umojaUser("jordan", ACME, GLOBEX);
        UUID amara = umojaUser("amara", ACME, GLOBEX);

        Conversation acme = conversations.openDirect(Actor.umoja(jordan, ACME), amara);
        Conversation globex = conversations.openDirect(Actor.umoja(jordan, GLOBEX), amara);

        assertThat(acme.getId()).isNotEqualTo(globex.getId());
        assertThat(conversations.openDirect(Actor.umoja(amara, ACME), jordan).getId()).isEqualTo(acme.getId());
        assertThatThrownBy(() -> conversations.leave(Actor.umoja(jordan, ACME), acme.getId())).isInstanceOf(Conflict.class);
    }

    @Test
    void unreadCountsComeFromTheReadPosition() {
        Conversation direct = conversations.openDirect(amaniActor, baraka);
        for (int i = 1; i <= 3; i++) {
            say(barakaActor, direct.getId(), "Message " + i);
        }

        assertThat(conversations.list(amaniActor)).singleElement().extracting(ConversationSummary::unreadCount).isEqualTo(3L);
        assertThat(conversations.list(barakaActor)).singleElement().extracting(ConversationSummary::unreadCount).isEqualTo(0L);
        conversations.markRead(amaniActor, direct.getId(), 2);
        assertThat(conversations.list(amaniActor)).singleElement().extracting(ConversationSummary::unreadCount).isEqualTo(1L);
        // never back, never beyond the last message
        assertThat(conversations.markRead(amaniActor, direct.getId(), 1).getLastReadSeq()).isEqualTo(2);
        ConversationMember read = conversations.markRead(amaniActor, direct.getId(), 99);
        assertThat(read.getLastReadSeq()).isEqualTo(3);
        assertThat(read.getLastDeliveredSeq()).isEqualTo(3);
        assertThat(conversations.list(amaniActor)).singleElement().extracting(ConversationSummary::unreadCount).isEqualTo(0L);
    }

    @Test
    void theOwnerWhoLeavesPassesTheOwnershipOn() {
        // the first who joined succeeds (members added together tie, then the id decides)
        Conversation family = conversations.createGroup(amaniActor, "Family", null, List.of(baraka));
        conversations.addMember(amaniActor, family.getId(), chausiku, true);

        conversations.leave(amaniActor, family.getId());

        assertThat(admin.queryForObject("SELECT user_id FROM oro_conversation_member WHERE conversation_id = ? AND role = 'owner' AND left_at IS NULL",
                UUID.class, family.getId())).isEqualTo(baraka);
    }

    /** the API posts system messages through a function that writes only what did happen, as who did it */
    @Test
    void aSystemMessageTellsOnlyWhatHappened() {
        Conversation family = conversations.createGroup(amaniActor, "Family", null, List.of(baraka, chausiku));
        UUID id = family.getId();
        String post = "SELECT message_id FROM oro_post_system_message(?, ?, ?)";

        // Baraka, a plain member: nobody was removed nor renamed by him, Chausiku did not leave, he added nobody
        assertRefused(() -> asUser(barakaActor, jdbc -> jdbc.queryForObject(post, UUID.class, id, "member.removed", chausiku)),
                "a system message tells what happened");
        assertRefused(() -> asUser(barakaActor, jdbc -> jdbc.queryForObject(post, UUID.class, id, "conversation.renamed", null)),
                "only the owner and the admins rename a group");
        assertRefused(() -> asUser(barakaActor, jdbc -> jdbc.queryForObject(post, UUID.class, id, "member.left", chausiku)),
                "a system message tells what happened");
        assertRefused(() -> asUser(barakaActor, jdbc -> jdbc.queryForObject(post, UUID.class, id, "member.joined", chausiku)),
                "a system message tells what happened");
        // a stranger, and an unknown event
        assertRefused(() -> asUser(Actor.umoja(umojaUser("jordan", ACME), ACME),
                jdbc -> jdbc.queryForObject(post, UUID.class, id, "member.joined", baraka)), "system messages are posted in a group of the product");
        assertRefused(() -> asUser(amaniActor, jdbc -> jdbc.queryForObject(post, UUID.class, id, "member.promoted", baraka)),
                "unknown system event");
        assertThat(count("SELECT count(*) FROM oro_message WHERE conversation_id = ?", id)).isZero();

        // what did happen, by who did it: the metadata comes from the rows
        conversations.removeMember(amaniActor, id, chausiku);
        assertThat(admin.queryForObject("SELECT metadata::text FROM oro_message WHERE conversation_id = ? AND message_type = 'system'",
                String.class, id)).contains("\"event\": \"member.removed\"", "\"userId\": \"oro-chausiku\"", "\"byUserId\": \"oro-amani\"");
    }

    /** raw SQL as oro_app, past the checks of the services: the roles hold in the database too */
    @Test
    void theRolesAreTheDatabasesToCheckToo() {
        UUID dede = oroUser("dede");
        Conversation family = conversations.createGroup(amaniActor, "Family", null, List.of(baraka, chausiku));
        Conversation direct = conversations.openDirect(amaniActor, baraka);
        UUID id = family.getId();
        // Chausiku administers the group (as the owner would make her)
        asUser(amaniActor, jdbc -> jdbc.update(
                "UPDATE oro_conversation_member SET role = 'admin' WHERE conversation_id = ? AND user_id = ? AND left_at IS NULL", id, chausiku));

        // Baraka, a plain member: no role, no removal, no rename, no addition, nobody else's positions
        assertRefused(() -> asUser(barakaActor, jdbc -> jdbc.update(
                "UPDATE oro_conversation_member SET role = 'owner' WHERE conversation_id = ? AND user_id = ? AND left_at IS NULL", id, baraka)),
                "a member does not change its own role");
        assertRefused(() -> asUser(barakaActor, jdbc -> jdbc.update(
                "UPDATE oro_conversation_member SET role = 'member' WHERE conversation_id = ? AND user_id = ? AND left_at IS NULL", id, chausiku)),
                "only the owner changes the roles");
        assertRefused(() -> asUser(barakaActor, jdbc -> jdbc.update("""
                UPDATE oro_conversation_member SET left_at = now(), left_reason = 'removed'
                WHERE conversation_id = ? AND user_id = ? AND left_at IS NULL""", id, amani)),
                "the owner and the admins remove members");
        assertRefused(() -> asUser(barakaActor, jdbc -> jdbc.update(
                "UPDATE oro_conversation SET name = 'Mine now' WHERE id = ?", id)),
                "only the owner and the admins rename a group");
        assertRefused(() -> asUser(barakaActor, jdbc -> jdbc.update("""
                INSERT INTO oro_conversation_member(id, conversation_id, product_id, user_id, role, shared_history, added_by)
                VALUES (?, ?, 'ORO', ?, 'member', TRUE, ?)""", UUID.randomUUID(), id, dede, baraka)),
                "only the owner and the admins add members");
        assertRefused(() -> asUser(barakaActor, jdbc -> jdbc.update(
                "UPDATE oro_conversation_member SET muted_until = now() + interval '1 day' WHERE conversation_id = ? AND user_id = ? AND left_at IS NULL",
                id, amani)), "the positions and the mute of a member are its own");
        assertRefused(() -> asUser(barakaActor, jdbc -> jdbc.update("""
                UPDATE oro_conversation_member SET left_at = now(), left_reason = 'access_revoked'
                WHERE conversation_id = ? AND user_id = ? AND left_at IS NULL""", id, baraka)),
                "a member ends its own membership by leaving");
        assertRefused(() -> asUser(barakaActor, jdbc -> jdbc.update("""
                UPDATE oro_conversation_member SET left_at = now(), left_reason = 'left'
                WHERE conversation_id = ? AND user_id = ? AND left_at IS NULL""", direct.getId(), baraka)),
                "a direct conversation and a team chat are not left nor removed from");
        // an admin adds as a member only, and never removes the owner
        assertRefused(() -> asUser(chausikuActor, jdbc -> jdbc.update("""
                INSERT INTO oro_conversation_member(id, conversation_id, product_id, user_id, role, shared_history, added_by)
                VALUES (?, ?, 'ORO', ?, 'admin', TRUE, ?)""", UUID.randomUUID(), id, dede, chausiku)),
                "only the owner and the admins add members, as members");
        assertRefused(() -> asUser(chausikuActor, jdbc -> jdbc.update("""
                UPDATE oro_conversation_member SET left_at = now(), left_reason = 'removed'
                WHERE conversation_id = ? AND user_id = ? AND left_at IS NULL""", id, amani)),
                "the owner and the admins remove members");
        assertThat(count("SELECT count(*) FROM oro_conversation_member WHERE conversation_id = ? AND role = 'owner' AND left_at IS NULL", id))
                .isEqualTo(1);
        assertThat(admin.queryForObject("SELECT name FROM oro_conversation WHERE id = ?", String.class, id)).isEqualTo("Family");

        // what the roles allow still works
        asUser(chausikuActor, jdbc -> jdbc.update(
                "UPDATE oro_conversation SET name = 'The family' WHERE id = ?", id));
        asUser(chausikuActor, jdbc -> jdbc.update("""
                UPDATE oro_conversation_member SET left_at = now(), left_reason = 'removed'
                WHERE conversation_id = ? AND user_id = ? AND left_at IS NULL""", id, baraka));
        assertThat(count("SELECT count(*) FROM oro_conversation_member WHERE conversation_id = ? AND left_at IS NULL", id))
                .isEqualTo(2);
    }
}
