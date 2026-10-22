package com.asata.org.oro.conversation.repository;

import com.asata.org.oro.conversation.entity.ConversationMember;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ConversationMemberRepository extends JpaRepository<ConversationMember, UUID> {

    /** the active membership, served by uq_oro_conversation_member_active */
    @Query("SELECT m FROM ConversationMember m WHERE m.conversationId = :conversationId AND m.userId = :userId AND m.leftAt IS NULL")
    Optional<ConversationMember> findActive(@Param("conversationId") UUID conversationId, @Param("userId") UUID userId);

    /** the active members of a conversation, the first ones first */
    @Query("SELECT m FROM ConversationMember m WHERE m.conversationId = :conversationId AND m.leftAt IS NULL ORDER BY m.joinedAt, m.id")
    List<ConversationMember> findActiveMembers(@Param("conversationId") UUID conversationId);
}
