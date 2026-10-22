package com.asata.org.oro.message.repository;

import com.asata.org.oro.message.entity.Message;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** under RLS for oro_app: only the messages a member may read (after its history_from_seq) are found */
@Repository
public interface MessageRepository extends JpaRepository<Message, UUID> {

    /** the message of an idempotency key, served by uq_oro_message_client */
    Optional<Message> findByConversationIdAndSenderIdAndClientMessageId(UUID conversationId, UUID senderId, String clientMessageId);

    /** the newest messages: a backward scan of uq_oro_message_seq (conversation_id, seq) */
    @Query(value = """
            SELECT * FROM oro_message WHERE conversation_id = :conversationId
            ORDER BY seq DESC LIMIT :size""", nativeQuery = true)
    List<Message> findNewest(@Param("conversationId") UUID conversationId, @Param("size") int size);

    /** the messages before a cursor (keyset pagination, no OFFSET), newest first */
    @Query(value = """
            SELECT * FROM oro_message WHERE conversation_id = :conversationId AND seq < :beforeSeq
            ORDER BY seq DESC LIMIT :size""", nativeQuery = true)
    List<Message> findBefore(@Param("conversationId") UUID conversationId, @Param("beforeSeq") long beforeSeq,
                             @Param("size") int size);

    /** the messages after a cursor (catching up after a reconnection), oldest first */
    @Query(value = """
            SELECT * FROM oro_message WHERE conversation_id = :conversationId AND seq > :afterSeq
            ORDER BY seq LIMIT :size""", nativeQuery = true)
    List<Message> findAfter(@Param("conversationId") UUID conversationId, @Param("afterSeq") long afterSeq,
                            @Param("size") int size);
}
