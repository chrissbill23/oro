package com.asata.org.oro.conversation.service;

import com.asata.org.oro.context.Actor;
import com.asata.org.oro.conversation.entity.Conversation;
import com.asata.org.oro.conversation.entity.ConversationMember;
import com.asata.org.oro.conversation.repository.ConversationMemberRepository;
import com.asata.org.oro.conversation.repository.ConversationRepository;
import com.asata.org.oro.enums.IdentityStatus;
import com.asata.org.oro.exception.NotFound;
import com.asata.org.oro.identity.repository.MessagingIdentityRepository;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * The authorization checks of the application, before RLS (the second line): a conversation is reachable only in the
 * product and the organization of the actor, by an active member of it. Whatever fails, the answer is NotFound: an
 * id guessed by someone else tells nothing, not even that it exists.
 */
@Component
@RequiredArgsConstructor
public class ConversationAccess {

    private final ConversationRepository conversations;
    private final ConversationMemberRepository members;
    private final MessagingIdentityRepository identities;
    private final MeterRegistry meters;

    public Conversation requireConversation(Actor actor, UUID conversationId) {
        return inContext(actor, conversations.findById(conversationId));
    }

    /** the conversation, locked for a send or a change of its sequence */
    public Conversation lockConversation(Actor actor, UUID conversationId) {
        // oro.conversation.lock: the wait for the row of the conversation (the sends of a conversation take turns)
        return meters.timer("oro.conversation.lock").record(() -> inContext(actor, conversations.lockById(conversationId)));
    }

    /** an active member that accepted its request or invitation: one that may send, react, type, add, rename */
    public ConversationMember requireAcceptedMember(Actor actor, Conversation conversation) {
        ConversationMember member = requireActiveMember(actor, conversation);
        if (member.getPendingSince() != null) {
            throw new com.asata.org.oro.exception.Conflict("Accept the request or the invitation first");
        }
        return member;
    }

    public ConversationMember requireActiveMember(Actor actor, Conversation conversation) {
        return members.findActive(conversation.getId(), actor.identityId())
                .orElseThrow(() -> new NotFound("Conversation not found"));
    }

    /** an active identity of the product of the actor (RLS hides the others) */
    public void requireActiveIdentity(UUID identityId) {
        identities.findById(identityId)
                .filter(identity -> identity.getStatus() == IdentityStatus.ACTIVE)
                .orElseThrow(() -> new NotFound("Messaging identity not found"));
    }

    private Conversation inContext(Actor actor, Optional<Conversation> conversation) {
        return conversation
                .filter(c -> c.getProduct() == actor.product() && Objects.equals(c.getOrganizationId(), actor.organizationId()))
                .orElseThrow(() -> new NotFound("Conversation not found"));
    }
}
