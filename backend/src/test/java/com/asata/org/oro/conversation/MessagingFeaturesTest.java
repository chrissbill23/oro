package com.asata.org.oro.conversation;

import com.asata.org.oro.PostgresIntegrationTest;
import com.asata.org.oro.context.Actor;
import com.asata.org.oro.conversation.dto.ConversationSummary;
import com.asata.org.oro.conversation.entity.Conversation;
import com.asata.org.oro.enums.MessageType;
import com.asata.org.oro.enums.Product;
import com.asata.org.oro.exception.Conflict;
import com.asata.org.oro.exception.NotFound;
import com.asata.org.oro.identity.service.OrganizationSyncService;
import com.asata.org.oro.identity.service.OrganizationSyncService.TeamChat;
import com.asata.org.oro.live.LiveHub;
import com.asata.org.oro.live.LiveOutboxEventHandler;
import com.asata.org.oro.message.entity.Message;
import com.asata.org.oro.outbox.service.ClaimedEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.verify;

/**
 * What the messaging page of Umoja relies on: the chats of the teams kept by the Umoja integration, the list with its
 * previews, mute, rename, reactions, search, the read times, and the live events.
 */
class MessagingFeaturesTest extends PostgresIntegrationTest {

    private static final UUID DESIGN = UUID.fromString("77777777-0000-4000-8000-000000000001");

    @Autowired
    private OrganizationSyncService sync;
    @Autowired
    private LiveOutboxEventHandler liveHandler;
    @MockitoSpyBean
    private LiveHub hub;

    private UUID jordan;
    private UUID amara;
    private UUID kwame;
    private Actor jordanActor;
    private Actor amaraActor;

    @BeforeEach
    void acme() {
        sync.sync(ACME, List.of("umoja-jordan", "umoja-amara", "umoja-kwame"), List.of());
        jordan = identities.find(Product.UMOJA, "umoja-jordan").orElseThrow();
        amara = identities.find(Product.UMOJA, "umoja-amara").orElseThrow();
        kwame = identities.find(Product.UMOJA, "umoja-kwame").orElseThrow();
        jordanActor = Actor.umoja(jordan, ACME);
        amaraActor = Actor.umoja(amara, ACME);
    }

    private ConversationSummary summary(Actor actor, UUID conversationId) {
        return conversations.list(actor).stream().filter(c -> c.id().equals(conversationId)).findFirst().orElseThrow();
    }

    private List<String> systemEvents(Actor actor, UUID conversationId) {
        return messages.page(actor, conversationId, null, 50).messages().reversed().stream()
                .filter(m -> m.getType() == MessageType.SYSTEM).map(Message::getBody).toList();
    }

    @Test
    void theChatOfATeamFollowsItsNameAndMembers() {
        var created = sync.sync(ACME, List.of("umoja-jordan", "umoja-amara", "umoja-kwame"),
                List.of(new TeamChat(DESIGN, "Design", List.of("umoja-jordan", "umoja-amara"))));
        assertThat(created.chatsCreated()).isEqualTo(1);
        ConversationSummary chat = conversations.list(jordanActor).getFirst();
        assertThat(chat.teamId()).isEqualTo(DESIGN);
        assertThat(chat.name()).isEqualTo("Design");
        assertThat(chat.memberCount()).isEqualTo(2);
        // its founders join without a word
        assertThat(systemEvents(jordanActor, chat.id())).isEmpty();

        // Kwame joins the team, Amara leaves it, the team is renamed
        var changed = sync.sync(ACME, List.of("umoja-jordan", "umoja-amara", "umoja-kwame"),
                List.of(new TeamChat(DESIGN, "Product & Design", List.of("umoja-jordan", "umoja-kwame"))));
        assertThat(changed.joined()).isEqualTo(1);
        assertThat(changed.left()).isEqualTo(1);
        assertThat(summary(jordanActor, chat.id()).name()).isEqualTo("Product & Design");
        assertThat(systemEvents(jordanActor, chat.id())).containsExactly("member.joined", "member.left");
        assertThatThrownBy(() -> messages.page(amaraActor, chat.id(), null, 20)).isInstanceOf(NotFound.class);
        // Kwame reads the whole history of the team
        assertThat(messages.page(Actor.umoja(kwame, ACME), chat.id(), null, 20).messages()).hasSize(2);

        // nothing changes, nothing is written
        var again = sync.sync(ACME, List.of("umoja-jordan", "umoja-amara", "umoja-kwame"),
                List.of(new TeamChat(DESIGN, "Product & Design", List.of("umoja-jordan", "umoja-kwame"))));
        assertThat(List.of(again.granted(), again.revoked(), again.chatsCreated(), again.joined(), again.left()))
                .containsOnly(0);

        // its members and name are the team's, not the users'
        assertThatThrownBy(() -> conversations.addMember(jordanActor, chat.id(), amara, true)).isInstanceOf(Conflict.class);
        assertThatThrownBy(() -> conversations.leave(jordanActor, chat.id())).isInstanceOf(Conflict.class);
        assertThatThrownBy(() -> conversations.rename(jordanActor, chat.id(), "Mine", null)).isInstanceOf(Conflict.class);
        // nor can the API make one: oro_app may not write team_id
        assertRefused(() -> asUser(jordanActor, jdbc -> jdbc.update("""
                INSERT INTO oro_conversation(id, product_id, context_type, organization_id, conversation_type, name,
                                             team_id, created_by)
                VALUES (gen_random_uuid(), 'UMOJA', 'organization', ?, 'group', 'Fake', gen_random_uuid(), ?)""",
                ACME, jordan)), "permission denied");

        // the team is gone: the chat keeps its history and loses its members
        sync.sync(ACME, List.of("umoja-jordan", "umoja-amara", "umoja-kwame"), List.of());
        assertThat(conversations.list(jordanActor)).isEmpty();
    }

    @Test
    void someoneWhoLeavesTheOrganizationLosesItsConversations() {
        Conversation direct = conversations.openDirect(jordanActor, amara);
        var result = sync.sync(ACME, List.of("umoja-jordan", "umoja-kwame"), List.of());
        assertThat(result.revoked()).isEqualTo(1);
        assertThat(organizationAccess.isGranted(amara, ACME)).isFalse();
        assertThatThrownBy(() -> messages.page(amaraActor, direct.getId(), null, 20)).isInstanceOf(NotFound.class);
        // back: a new access, but not its old memberships
        sync.sync(ACME, List.of("umoja-jordan", "umoja-amara", "umoja-kwame"), List.of());
        assertThat(organizationAccess.isGranted(amara, ACME)).isTrue();
        assertThat(conversations.list(amaraActor)).isEmpty();
    }

    @Test
    void theListShowsTheLastMessageTheOtherOneAndWhatIsUnread() {
        Conversation direct = conversations.openDirect(jordanActor, amara);
        say(jordanActor, direct.getId(), "Can you send me the flows before crit?");
        say(jordanActor, direct.getId(), "x".repeat(300));

        ConversationSummary mine = summary(amaraActor, direct.getId());
        assertThat(mine.otherUserId()).isEqualTo("umoja-jordan");
        assertThat(mine.unreadCount()).isEqualTo(2);
        assertThat(mine.memberCount()).isEqualTo(2);
        assertThat(mine.lastMessage().senderUserId()).isEqualTo("umoja-jordan");
        assertThat(mine.lastMessage().body()).hasSize(200);
        assertThat(summary(jordanActor, direct.getId()).unreadCount()).isZero();

        // read: the time is kept, and the members hear it
        conversations.markRead(amaraActor, direct.getId(), 2);
        assertThat(conversations.members(jordanActor, direct.getId()))
                .filteredOn(member -> member.getUserId().equals(amara))
                .singleElement().satisfies(member -> {
                    assertThat(member.getLastReadSeq()).isEqualTo(2);
                    assertThat(member.getLastReadAt()).isNotNull();
                });
        verify(hub).send(anyCollection(), eq(Product.UMOJA), eq(ACME),
                argThat(event -> "read".equals(event.get("type")) && "umoja-amara".equals(event.get("userId"))));
    }

    @Test
    void aMemberMutesAndTheOwnerRenamesAGroup() {
        Conversation group = conversations.createGroup(jordanActor, "Onboarding", null, List.of(amara));
        OffsetDateTime until = OffsetDateTime.now().plusHours(8);
        conversations.mute(amaraActor, group.getId(), until);
        assertThat(summary(amaraActor, group.getId()).mutedUntil()).isNotNull();
        assertThat(summary(jordanActor, group.getId()).mutedUntil()).isNull();
        conversations.mute(amaraActor, group.getId(), null);
        assertThat(summary(amaraActor, group.getId()).mutedUntil()).isNull();

        assertThatThrownBy(() -> conversations.rename(amaraActor, group.getId(), "Mine", null))
                .hasMessageContaining("Only the owner and the admins");
        conversations.rename(jordanActor, group.getId(), "Onboarding redesign", "Q4");
        ConversationSummary renamed = summary(amaraActor, group.getId());
        assertThat(renamed.name()).isEqualTo("Onboarding redesign");
        assertThat(renamed.lastMessage().type()).isEqualTo(MessageType.SYSTEM);
        assertThat(renamed.lastMessage().metadata()).contains("\"name\": \"Onboarding redesign\"", "\"byUserId\": \"umoja-jordan\"");
    }

    @Test
    void membersReactWithTheReactionsOfOro() {
        Conversation group = conversations.createGroup(jordanActor, "Design", null, List.of(amara));
        UUID message = say(jordanActor, group.getId(), "New tokens are live").message().getId();

        messages.react(amaraActor, message, "👍", true);
        messages.react(jordanActor, message, "👍", true);
        messages.react(jordanActor, message, "🎉", true);
        // twice is once
        messages.react(jordanActor, message, "🎉", true);
        var reactions = messages.extras(amaraActor, List.of(message)).reactions().get(message);
        assertThat(reactions).extracting(r -> r.emoji() + r.userIds())
                .containsExactly("👍[umoja-amara, umoja-jordan]", "🎉[umoja-jordan]");
        assertThat(count("SELECT count(*) FROM oro_outbox_event WHERE event_type = 'reaction.changed'")).isEqualTo(3);

        messages.react(jordanActor, message, "🎉", false);
        assertThat(messages.extras(amaraActor, List.of(message)).reactions().get(message)).hasSize(1);
        assertThatThrownBy(() -> messages.react(amaraActor, message, "💩", true)).isInstanceOf(IllegalArgumentException.class);
        // not a member: nothing to react to
        assertThatThrownBy(() -> messages.react(Actor.umoja(kwame, ACME), message, "👍", true)).isInstanceOf(NotFound.class);
        // RLS alone: no reaction as someone else
        assertRefused(() -> asUser(amaraActor, jdbc -> jdbc.update("""
                INSERT INTO oro_message_reaction(message_id, conversation_id, product_id, user_id, emoji)
                VALUES (?, ?, 'UMOJA', ?, '🔥')""", message, group.getId(), jordan)), "row-level security");

        // deleted: its reactions go with its content
        messages.delete(jordanActor, message);
        assertThat(count("SELECT count(*) FROM oro_message_reaction WHERE message_id = ?", message)).isZero();
        assertThatThrownBy(() -> messages.react(amaraActor, message, "👍", true)).isInstanceOf(Conflict.class);
    }

    @Test
    void theSearchFindsWhatTheMemberMayReadOnly() {
        Conversation group = conversations.createGroup(jordanActor, "Design", null, List.of());
        say(jordanActor, group.getId(), "The onboarding flows are ready");
        say(jordanActor, group.getId(), "100% done_ with the tokens");
        conversations.addMember(jordanActor, group.getId(), amara, false);
        say(jordanActor, group.getId(), "Onboarding crit at 3pm");
        Conversation other = conversations.openDirect(jordanActor, kwame);
        say(jordanActor, other.getId(), "Onboarding notes for Kwame");

        assertThat(messages.search(jordanActor, "onboarding", null)).extracting(Message::getBody)
                .containsExactly("Onboarding notes for Kwame", "Onboarding crit at 3pm", "The onboarding flows are ready");
        assertThat(messages.search(jordanActor, "onboarding", group.getId())).hasSize(2);
        // Amara joined without the history, and is not in the direct conversation
        assertThat(messages.search(amaraActor, "onboarding", null)).extracting(Message::getBody)
                .containsExactly("Onboarding crit at 3pm");
        // the wildcards are text
        assertThat(messages.search(jordanActor, "0% done_", null)).hasSize(1);
        assertThat(messages.search(jordanActor, "%%", null)).isEmpty();
        assertThatThrownBy(() -> messages.search(jordanActor, "o", null)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> messages.search(amaraActor, "notes", other.getId())).isInstanceOf(NotFound.class);
    }

    @Test
    void theOutboxEventsReachTheMembersAndWhoLeft() {
        Conversation group = conversations.createGroup(jordanActor, "Design", null, List.of(amara, kwame));
        UUID message = say(jordanActor, group.getId(), "Hello").message().getId();
        conversations.removeMember(jordanActor, group.getId(), kwame);

        liveHandler.handle(new ClaimedEvent(UUID.randomUUID(), Product.UMOJA, "message.created", "message", message,
                "{\"conversationId\":\"%s\",\"messageId\":\"%s\",\"seq\":1}".formatted(group.getId(), message), 1));
        verify(hub).send(argThat(ids -> ids.containsAll(List.of(jordan, amara)) && !ids.contains(kwame)),
                eq(Product.UMOJA), eq(ACME), eq(Map.of("type", "message", "change", "created", "messageId", message,
                        "seq", 1L, "conversationId", group.getId())));

        liveHandler.handle(new ClaimedEvent(UUID.randomUUID(), Product.UMOJA, "member.removed", "conversation", group.getId(),
                "{\"conversationId\":\"%s\",\"memberId\":\"%s\"}".formatted(group.getId(), kwame), 1));
        verify(hub).send(argThat(ids -> ids.containsAll(List.of(jordan, amara, kwame))), eq(Product.UMOJA), eq(ACME),
                argThat(event -> "member.removed".equals(event.get("change"))));
    }

    @Test
    void typingIsForTheOtherMembersOnly() {
        Conversation direct = conversations.openDirect(jordanActor, amara);
        conversations.typing(jordanActor, direct.getId(), "umoja-jordan");
        verify(hub).send(eq(List.of(amara)), eq(Product.UMOJA), eq(ACME),
                eq(Map.of("type", "typing", "conversationId", direct.getId(), "userId", "umoja-jordan")));
        assertThatThrownBy(() -> conversations.typing(Actor.umoja(kwame, ACME), direct.getId(), "umoja-kwame"))
                .isInstanceOf(NotFound.class);
    }
}
