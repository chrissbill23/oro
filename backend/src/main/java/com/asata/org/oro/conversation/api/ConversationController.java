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
    private final com.asata.org.oro.safety.BlockService blocks;

    @Override
    public List<ConversationSummary> list(Actor actor) {
        return conversations.list(actor);
    }

    @Override
    public List<ConversationSummary> requests(Actor actor) {
        return conversations.requests(actor);
    }

    @Override
    public MemberDTO accept(Actor actor, UUID conversationId) {
        ConversationMember mine = conversations.accept(actor, conversationId);
        return memberDto(identities.externalIds(actor.product(), List.of(mine.getUserId())).get(mine.getUserId()), mine);
    }

    @Override
    public void decline(Actor actor, UUID conversationId, boolean block) {
        UUID inviter = block ? conversations.inviterOfPending(actor, conversationId) : null;
        conversations.decline(actor, conversationId);
        if (inviter != null) {
            blocks.block(actor, inviter);
        }
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
        return memberDto(request.userId(), member);
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
    public ConversationDTO rename(Actor actor, UUID conversationId, RenameRequest request) {
        return toDto(actor, conversations.rename(actor, conversationId, request.name(), request.description()));
    }

    @Override
    public ConversationDTO mute(Actor actor, UUID conversationId, MuteRequest request) {
        conversations.mute(actor, conversationId, request.until());
        return toDto(actor, conversations.get(actor, conversationId));
    }

    @Override
    public void typing(Actor actor, UUID conversationId) {
        conversations.typing(actor, conversationId, identities.externalIds(actor.product(), List.of(actor.identityId()))
                .get(actor.identityId()));
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
        ConversationMember mine = members.stream().filter(member -> member.getUserId().equals(actor.identityId()))
                .findFirst().orElseThrow();
        return new ConversationDTO(conversation.getId(), conversation.getType(), conversation.getContextType(),
                conversation.getOrganizationId(), conversation.getName(), conversation.getDescription(),
                conversation.getTeamId(), userIds.get(conversation.getCreatedBy()), conversation.getLastMessageSeq(),
                conversation.getLastMessageAt(), conversation.getCreatedAt(), mine.getRole(), mine.getMutedUntil(),
                members.stream().map(member -> memberDto(userIds.get(member.getUserId()), member)).toList());
    }

    private static MemberDTO memberDto(String userId, ConversationMember member) {
        return new MemberDTO(userId, member.getRole(), member.isSharedHistory(), member.getJoinedAt(),
                member.getLastReadSeq(), member.getLastReadAt(), member.getPendingSince() != null);
    }
}
