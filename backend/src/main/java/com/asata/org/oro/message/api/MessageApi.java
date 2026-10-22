package com.asata.org.oro.message.api;

import com.asata.org.oro.context.Actor;
import com.asata.org.oro.message.dto.EditMessageRequest;
import com.asata.org.oro.message.dto.MessageDTO;
import com.asata.org.oro.message.dto.MessagePageDTO;
import com.asata.org.oro.message.dto.SendMessageRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@Tag(name = "Messages", description = "The messages of a conversation the caller is an active member of")
public interface MessageApi {

    @Operation(summary = "A page of messages",
            description = "Newest first: the newest ones, then before=nextBeforeSeq for older ones; or after=seq, "
                    + "oldest first, to catch up after a reconnection")
    @GetMapping("/conversations/{conversationId}/messages")
    MessagePageDTO page(@Parameter(hidden = true) Actor actor, @PathVariable UUID conversationId,
                        @RequestParam(required = false) Long before, @RequestParam(required = false) Long after,
                        @RequestParam(defaultValue = "50") int size);

    @Operation(summary = "Send a message",
            description = "201: stored (not yet delivered); 200: a retry with the same clientMessageId, the stored message")
    @PostMapping("/conversations/{conversationId}/messages")
    ResponseEntity<MessageDTO> send(@Parameter(hidden = true) Actor actor, @PathVariable UUID conversationId,
                                    @Valid @RequestBody SendMessageRequest request);

    @Operation(summary = "A message", description = "One the caller may read: after a change on its stream")
    @GetMapping("/messages/{messageId}")
    MessageDTO get(@Parameter(hidden = true) Actor actor, @PathVariable UUID messageId);

    @Operation(summary = "React to a message", description = "With one emoji (any, skin tones and flags included); once per emoji")
    @PutMapping("/messages/{messageId}/reactions/{emoji}")
    MessageDTO react(@Parameter(hidden = true) Actor actor, @PathVariable UUID messageId, @PathVariable String emoji);

    @Operation(summary = "Take back a reaction")
    @DeleteMapping("/messages/{messageId}/reactions/{emoji}")
    MessageDTO unreact(@Parameter(hidden = true) Actor actor, @PathVariable UUID messageId, @PathVariable String emoji);

    @Operation(summary = "Search the messages",
            description = "The ones the caller may read containing the text (2 to 200 characters), newest first, 30 at most; "
                    + "in one conversation with conversationId")
    @GetMapping("/search/messages")
    List<MessageDTO> search(@Parameter(hidden = true) Actor actor, @RequestParam String q,
                            @RequestParam(required = false) UUID conversationId);

    @Operation(summary = "Edit a message", description = "Its sender only, a text message not deleted")
    @PutMapping("/messages/{messageId}")
    MessageDTO edit(@Parameter(hidden = true) Actor actor, @PathVariable UUID messageId,
                    @Valid @RequestBody EditMessageRequest request);

    @Operation(summary = "Delete a message", description = "Its sender only: its content is erased, its place kept")
    @DeleteMapping("/messages/{messageId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void delete(@Parameter(hidden = true) Actor actor, @PathVariable UUID messageId);
}
