package com.asata.org.oro.conversation.entity;

import com.asata.org.oro.enums.LeftReason;
import com.asata.org.oro.enums.MemberRole;
import com.asata.org.oro.enums.Product;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.DynamicUpdate;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * A period of an identity in a conversation (table oro_conversation_member), from joinedAt to leftAt: leaving ends it,
 * rejoining is a new one. Only an active member (leftAt null) reads the conversation, the messages after
 * historyFromSeq: 0 when the admin who added it shared the history, else the last message when it joined. The
 * positions (joinedSeq, lastReadSeq, lastDeliveredSeq) are set and clamped by the trigger oro_conversation_member_rules:
 * refresh the entity after a flush to read them.
 */
@Getter
@Setter
@Entity
@DynamicUpdate
@Table(name = "oro_conversation_member")
public class ConversationMember {

    @Id
    private UUID id;

    @Column(name = "conversation_id", nullable = false, updatable = false)
    private UUID conversationId;

    @Convert(converter = Product.DbConverter.class)
    @Column(name = "product_id", nullable = false, updatable = false, length = 16)
    private Product product;

    @Column(name = "user_id", nullable = false, updatable = false)
    private UUID userId;

    @Convert(converter = MemberRole.DbConverter.class)
    @Column(nullable = false, length = 16)
    private MemberRole role = MemberRole.MEMBER;

    @Column(name = "shared_history", nullable = false, updatable = false)
    private boolean sharedHistory;

    @Column(name = "joined_seq", insertable = false, updatable = false)
    private long joinedSeq;

    /** generated: 0 with a shared history, else joinedSeq */
    @Column(name = "history_from_seq", insertable = false, updatable = false)
    private long historyFromSeq;

    @Column(name = "last_delivered_seq", insertable = false)
    private long lastDeliveredSeq;

    @Column(name = "last_read_seq", insertable = false)
    private long lastReadSeq;

    /** when lastReadSeq last moved on (trigger) */
    @Column(name = "last_read_at", insertable = false, updatable = false)
    private OffsetDateTime lastReadAt;

    @Column(name = "muted_until")
    private OffsetDateTime mutedUntil;

    @Column(name = "added_by", updatable = false)
    private UUID addedBy;

    @Column(name = "joined_at", insertable = false, updatable = false)
    private OffsetDateTime joinedAt;

    @Column(name = "left_at")
    private OffsetDateTime leftAt;

    @Convert(converter = LeftReason.DbConverter.class)
    @Column(name = "left_reason", length = 16)
    private LeftReason leftReason;

    @Column(name = "updated_at", insertable = false, updatable = false)
    private OffsetDateTime updatedAt;

    public boolean isActive() {
        return leftAt == null;
    }

    public boolean canManageMembers() {
        return role == MemberRole.OWNER || role == MemberRole.ADMIN;
    }
}
