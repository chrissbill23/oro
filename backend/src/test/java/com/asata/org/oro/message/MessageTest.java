package com.asata.org.oro.message;

import com.asata.org.oro.PostgresIntegrationTest;
import com.asata.org.oro.context.Actor;
import com.asata.org.oro.conversation.entity.Conversation;
import com.asata.org.oro.exception.Conflict;
import com.asata.org.oro.exception.Forbidden;
import com.asata.org.oro.message.dto.MessagePage;
import com.asata.org.oro.message.dto.SendMessage;
import com.asata.org.oro.message.dto.SendResult;
import com.asata.org.oro.message.entity.Message;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.LongStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** sending: atomic with its outbox event, idempotent, ordered without gaps; the sender is always the actor */
class MessageTest extends PostgresIntegrationTest {

    private UUID amani;
    private UUID baraka;
    private Actor amaniActor;
    private Actor barakaActor;
    private UUID direct;

    @BeforeEach
    void directConversation() {
        amani = oroUser("amani");
        baraka = oroUser("baraka");
        amaniActor = Actor.oro(amani);
        barakaActor = Actor.oro(baraka);
        direct = conversations.openDirect(amaniActor, baraka).getId();
    }

    private long lastSeq() {
        return count("SELECT last_message_seq FROM oro_conversation WHERE id = ?", direct);
    }

    private long createdEvents() {
        return count("SELECT count(*) FROM oro_outbox_event WHERE event_type = 'message.created'");
    }

    @Test
    void aDuplicateIdempotencyKeyDoesNotCreateADuplicateMessage() {
        SendMessage request = SendMessage.text("client-key-0001", "Hello");

        SendResult first = messages.send(amaniActor, direct, request);
        SendResult retry = messages.send(amaniActor, direct, request);

        assertThat(first.duplicate()).isFalse();
        assertThat(retry.duplicate()).isTrue();
        assertThat(retry.message().getId()).isEqualTo(first.message().getId());
        assertThat(count("SELECT count(*) FROM oro_message")).isEqualTo(1);
        assertThat(createdEvents()).isEqualTo(1);
        assertThat(lastSeq()).isEqualTo(1);
        // the key is the sender's: the same key from the other member is another message
        assertThat(messages.send(barakaActor, direct, request).duplicate()).isFalse();
        // and the database refuses a second row anyway
        assertRefused(() -> admin.update("""
                INSERT INTO oro_message(conversation_id, product_id, seq, sender_id, client_message_id, body)
                VALUES (?, 'ORO', 1, ?, 'client-key-0001', 'Again')""", direct, amani), "uq_oro_message");
    }

    @Test
    void concurrentRetriesOfTheSameSendStoreOneMessage() throws Exception {
        SendMessage request = SendMessage.text("client-key-0002", "Only once");

        List<UUID> ids = concurrently(10, i -> messages.send(amaniActor, direct, request).message().getId());

        assertThat(ids).containsOnly(ids.getFirst());
        assertThat(count("SELECT count(*) FROM oro_message")).isEqualTo(1);
        assertThat(createdEvents()).isEqualTo(1);
    }

    @Test
    void concurrentSendsTakeConsecutiveSeqsWithoutGaps() throws Exception {
        List<Long> seqs = concurrently(20, i -> say(i % 2 == 0 ? amaniActor : barakaActor, direct, "Message " + i)
                .message().getSeq());

        assertThat(seqs).containsExactlyInAnyOrderElementsOf(LongStream.rangeClosed(1, 20).boxed().toList());
        assertThat(lastSeq()).isEqualTo(20);
        assertThat(createdEvents()).isEqualTo(20);
    }

    @Test
    void aFailedTransactionLeavesNeitherTheMessageNorItsEvent() {
        // the caller's transaction fails after the send
        assertThatThrownBy(() -> tx.executeWithoutResult(status -> {
            say(amaniActor, direct, "Lost");
            throw new IllegalStateException("a failure after the send");
        })).isInstanceOf(IllegalStateException.class);
        // the database refuses the message itself (too long), after the seq moved on
        assertRefused(() -> say(amaniActor, direct, "x".repeat(16001)), "ck_oro_message_body");

        assertThat(count("SELECT count(*) FROM oro_message")).isZero();
        assertThat(createdEvents()).isZero();
        assertThat(lastSeq()).isZero();
        // the next one takes seq 1: no gap
        assertThat(say(amaniActor, direct, "Kept").message().getSeq()).isEqualTo(1);
    }

    @Test
    void aUserCannotSendAsAnotherUser() {
        Message barakas = say(barakaActor, direct, "Mine").message();

        // past the services, as oro_app for Amani: RLS refuses another sender and hides the rows to change
        assertRefused(() -> asUser(amaniActor, jdbc -> {
            jdbc.update("UPDATE oro_conversation SET last_message_seq = last_message_seq + 1 WHERE id = ?", direct);
            return jdbc.update("""
                    INSERT INTO oro_message(id, conversation_id, product_id, seq, sender_id, client_message_id, body)
                    VALUES (gen_random_uuid(), ?, 'ORO', 2, ?, 'forged-key-01', 'I am Baraka')""", direct, baraka);
        }), "row-level security");
        Integer updated = asUser(amaniActor, jdbc -> jdbc.update("UPDATE oro_message SET body = 'Changed' WHERE id = ?",
                barakas.getId()));
        assertThat(updated).isZero();
        // nor the sender itself can be changed
        assertRefused(() -> asUser(barakaActor, jdbc -> jdbc.update("UPDATE oro_message SET sender_id = ? WHERE id = ?",
                amani, barakas.getId())), "permission denied");
        assertThatThrownBy(() -> messages.edit(amaniActor, barakas.getId(), "Changed")).isInstanceOf(Forbidden.class);
        assertThatThrownBy(() -> messages.delete(amaniActor, barakas.getId())).isInstanceOf(Forbidden.class);
        assertThat(admin.queryForObject("SELECT body FROM oro_message WHERE id = ?", String.class, barakas.getId())).isEqualTo("Mine");
    }

    @Test
    void aMessageTakesASeqTheConversationGave() {
        assertRefused(() -> asUser(amaniActor, jdbc -> jdbc.update("""
                INSERT INTO oro_message(id, conversation_id, product_id, seq, sender_id, client_message_id, body)
                VALUES (gen_random_uuid(), ?, 'ORO', 7, ?, 'raw-key-0007', 'Skipping ahead')""", direct, amani)),
                "taken from its conversation");
        assertRefused(() -> asUser(amaniActor, jdbc -> jdbc.update(
                "UPDATE oro_conversation SET last_message_seq = last_message_seq + 5 WHERE id = ?", direct)), "one message at a time");
    }

    @Test
    void pagesGoBackWithACursor() {
        for (int i = 1; i <= 25; i++) {
            say(i % 2 == 0 ? barakaActor : amaniActor, direct, "Message " + i);
        }

        MessagePage newest = messages.page(barakaActor, direct, null, 10);
        assertThat(newest.messages()).extracting(Message::getSeq).containsExactlyElementsOf(range(25, 16));
        MessagePage older = messages.page(barakaActor, direct, newest.nextBeforeSeq(), 10);
        assertThat(older.messages()).extracting(Message::getSeq).containsExactlyElementsOf(range(15, 6));
        MessagePage oldest = messages.page(barakaActor, direct, older.nextBeforeSeq(), 10);
        assertThat(oldest.messages()).extracting(Message::getSeq).containsExactlyElementsOf(range(5, 1));
        assertThat(oldest.nextBeforeSeq()).isNull();
        // the catch-up after a reconnection
        assertThat(messages.after(barakaActor, direct, 22, 10)).extracting(Message::getSeq).containsExactly(23L, 24L, 25L);
    }

    @Test
    void aDeletedMessageKeepsItsPlaceButNotItsContent() {
        Message message = messages.send(amaniActor, direct, new SendMessage("client-key-0003",
                com.asata.org.oro.enums.MessageType.TEXT, "A secret", List.of("oro-baraka"), List.of())).message();
        say(amaniActor, direct, "After");

        Message deleted = messages.delete(amaniActor, message.getId());

        assertThat(deleted.isDeleted()).isTrue();
        Map<String, Object> row = admin.queryForMap("SELECT body, metadata::text AS metadata, deleted_by FROM oro_message WHERE id = ?",
                message.getId());
        assertThat(row.get("body")).isNull();
        assertThat(row.get("metadata")).isEqualTo("{}");
        assertThat(row.get("deleted_by")).isEqualTo(amani);
        assertThat(messages.page(barakaActor, direct, null, 10).messages())
                .extracting(Message::getSeq, Message::getBody, Message::isDeleted)
                .containsExactly(org.assertj.core.groups.Tuple.tuple(2L, "After", false),
                        org.assertj.core.groups.Tuple.tuple(1L, null, true));
        assertThatThrownBy(() -> messages.edit(amaniActor, message.getId(), "Back")).isInstanceOf(Conflict.class);
        // even raw: no undelete, no content on a tombstone
        assertRefused(() -> asUser(amaniActor, jdbc -> jdbc.update(
                "UPDATE oro_message SET deleted_at = NULL, deleted_by = NULL WHERE id = ?", message.getId())), "stays deleted");
        assertRefused(() -> admin.update("UPDATE oro_message SET body = 'A secret' WHERE id = ?", message.getId()), "stays deleted");
        // the events hold ids, never the content
        assertThat(admin.queryForList("SELECT payload::text FROM oro_outbox_event", String.class))
                .isNotEmpty().noneMatch(payload -> payload.contains("secret"));
    }

    @Test
    void editingKeepsTheOrderAndMarksTheMessage() {
        Message message = say(amaniActor, direct, "Helo").message();

        Message edited = messages.edit(amaniActor, message.getId(), "Hello");

        assertThat(edited.getBody()).isEqualTo("Hello");
        assertThat(edited.getEditedAt()).isNotNull();
        assertThat(edited.getSeq()).isEqualTo(1);
        assertThat(count("SELECT count(*) FROM oro_outbox_event WHERE event_type = 'message.edited'")).isEqualTo(1);
    }

    private static List<Long> range(long from, long to) {
        return LongStream.rangeClosed(to, from).map(i -> from + to - i).boxed().toList();
    }
}
