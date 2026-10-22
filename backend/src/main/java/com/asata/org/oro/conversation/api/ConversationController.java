package com.asata.org.oro.conversation.api;

import com.asata.org.oro.context.Actor;
import com.asata.org.oro.conversation.dto.*;
import com.asata.org.oro.conversation.entity.Conversation;
import com.asata.org.oro.conversation.entity.ConversationMember;
import com.asata.org.oro.conversation.service.ConversationService;
import com.asata.org.oro.identity.service.IdentityService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.RestController;

import java.util.*;

@RestController
@RequiredArgsConstructor
public class ConversationController implements ConversationApi {

    private final ConversationService conversations;
    private final IdentityService identities;

    @Override
    public List<ConversationSummary> list(Actor actor) {
        return conversations.list(actor);
    }

    @Override
    public ConversationDTO openDirect(Actor actor, OpenDirectRequest request) {
        UUID other = identities.resolve(actor.product(), request.userId());
        return toDto(actor, conversations.openDirect(actor, other));
    }

    @Override
    public ConversationDTO createGroup(Actor actor, CreateGroupRequest request) {
        List<UUID> memberIds = request.memberUserIds() == null ? List.of() : request.memberUserIds().stream()
                .distinct().map(userId -> identities.resolve(actor.product(), userId)).toList();
        return toDto(actor, conversations.createGroup(actor, request.name(), request.description(), memberIds));
    }

    @Override
    public ConversationDTO get(Actor actor, UUID conversationId) {
        return toDto(actor, conversations.get(actor, conversationId));
    }

    @Override
    public MemberDTO addMember(Actor actor, UUID conversationId, AddMemberRequest request) {
        UUID identityId = identities.resolve(actor.product(), request.userId());
        ConversationMember member = conversations.addMember(actor, conversationId, identityId, request.shareHistory());
        return new MemberDTO(request.userId(), member.getRole(), member.isSharedHistory(), member.getJoinedAt());
    }

    @Override
    public void removeMember(Actor actor, UUID conversationId, String userId) {
        UUID identityId = identities.resolve(actor.product(), userId);
        if (identityId.equals(actor.identityId())) {
            conversations.leave(actor, conversationId);
        } else {
            conversations.removeMember(actor, conversationId, identityId);
        }
    }

    @Override
    public ReadStateDTO markRead(Actor actor, UUID conversationId, PositionRequest request) {
        ConversationMember member = conversations.markRead(actor, conversationId, request.seq());
        return new ReadStateDTO(member.getLastReadSeq(), member.getLastDeliveredSeq());
    }

    @Override
    public ReadStateDTO markDelivered(Actor actor, UUID conversationId, PositionRequest request) {
        ConversationMember member = conversations.markDelivered(actor, conversationId, request.seq());
        return new ReadStateDTO(member.getLastReadSeq(), member.getLastDeliveredSeq());
    }

    private ConversationDTO toDto(Actor actor, Conversation conversation) {
        List<ConversationMember> members = conversations.members(actor, conversation.getId());
        Set<UUID> ids = new HashSet<>();
        ids.add(conversation.getCreatedBy());
        members.forEach(member -> ids.add(member.getUserId()));
        Map<UUID, String> userIds = identities.externalIds(actor.product(), ids);
        return new ConversationDTO(conversation.getId(), conversation.getType(), conversation.getContextType(),
                conversation.getOrganizationId(), conversation.getName(), conversation.getDescription(),
                userIds.get(conversation.getCreatedBy()), conversation.getLastMessageSeq(), conversation.getLastMessageAt(),
                conversation.getCreatedAt(), members.stream()
                .map(member -> new MemberDTO(userIds.get(member.getUserId()), member.getRole(), member.isSharedHistory(),
                        member.getJoinedAt()))
                .toList());
    }
}
