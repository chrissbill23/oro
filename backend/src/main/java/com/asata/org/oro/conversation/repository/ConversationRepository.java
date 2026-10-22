package com.asata.org.oro.conversation.repository;

import com.asata.org.oro.conversation.entity.Conversation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

/** under RLS for oro_app: only the conversations of the current identity are found */
@Repository
public interface ConversationRepository extends JpaRepository<Conversation, UUID> {

    /**
     * The conversation, its row locked until the end of the transaction, as a send takes the next seq (FOR NO KEY
     * UPDATE: the lock of an update of a column that is not a key, so the inserts that reference the conversation are
     * not blocked). The sends of a conversation, and the members who join, wait for each other.
     */
    @Query(value = "SELECT * FROM oro_conversation WHERE id = :id FOR NO KEY UPDATE", nativeQuery = true)
    Optional<Conversation> lockById(@Param("id") UUID id);
}
