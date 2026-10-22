package com.asata.org.oro.conversation.api;

import com.asata.org.oro.context.Actor;
import com.asata.org.oro.conversation.dto.*;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

/** the conversations of the caller: personal (Oro), or of the organization of the request (Umoja) */
@Tag(name = "Conversations", description = "Direct conversations and groups, their members and read positions")
@RequestMapping("/conversations")
public interface ConversationApi {

    @Operation(summary = "My conversations", description = "Active ones, the latest message first, with their unread counts")
    @GetMapping
    List<ConversationSummary> list(@Parameter(hidden = true) Actor actor);

    @Operation(summary = "Open a direct conversation",
            description = "With another user of the product (and organization); the existing one if there is one")
    @PostMapping("/direct")
    ConversationDTO openDirect(@Parameter(hidden = true) Actor actor, @Valid @RequestBody OpenDirectRequest request);

    @Operation(summary = "Create a group", description = "The caller is its owner")
    @PostMapping("/groups")
    @ResponseStatus(HttpStatus.CREATED)
    ConversationDTO createGroup(@Parameter(hidden = true) Actor actor, @Valid @RequestBody CreateGroupRequest request);

    @Operation(summary = "A conversation", description = "With its active members; 404 when the caller is not one")
    @GetMapping("/{conversationId}")
    ConversationDTO get(@Parameter(hidden = true) Actor actor, @PathVariable UUID conversationId);

    @Operation(summary = "Add a member to a group", description = "By its owner or an admin, who chooses whether the new member sees the history")
    @PostMapping("/{conversationId}/members")
    @ResponseStatus(HttpStatus.CREATED)
    MemberDTO addMember(@Parameter(hidden = true) Actor actor, @PathVariable UUID conversationId,
                        @Valid @RequestBody AddMemberRequest request);

    @Operation(summary = "Remove a member, or leave",
            description = "The caller's own user id: it leaves the group; another one: removed by the owner or an admin")
    @DeleteMapping("/{conversationId}/members/{userId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void removeMember(@Parameter(hidden = true) Actor actor, @PathVariable UUID conversationId, @PathVariable String userId);

    @Operation(summary = "Mark as read", description = "Up to seq")
    @PutMapping("/{conversationId}/read")
    ReadStateDTO markRead(@Parameter(hidden = true) Actor actor, @PathVariable UUID conversationId,
                          @Valid @RequestBody PositionRequest request);

    @Operation(summary = "Rename a group", description = "Its owner or an admin; not a team chat (its name is the team's)")
    @PutMapping("/{conversationId}")
    ConversationDTO rename(@Parameter(hidden = true) Actor actor, @PathVariable UUID conversationId,
                           @Valid @RequestBody RenameRequest request);

    @Operation(summary = "Mute or unmute", description = "For the caller: until a time, or null to unmute")
    @PutMapping("/{conversationId}/mute")
    ConversationDTO mute(@Parameter(hidden = true) Actor actor, @PathVariable UUID conversationId,
                         @RequestBody MuteRequest request);

    @Operation(summary = "Typing", description = "The other members see it at once on their streams; nothing is stored")
    @PostMapping("/{conversationId}/typing")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void typing(@Parameter(hidden = true) Actor actor, @PathVariable UUID conversationId);

    @Operation(summary = "Mark as delivered", description = "Up to seq: the messages reached a device of the caller")
    @PutMapping("/{conversationId}/delivered")
    ReadStateDTO markDelivered(@Parameter(hidden = true) Actor actor, @PathVariable UUID conversationId,
                               @Valid @RequestBody PositionRequest request);
}
