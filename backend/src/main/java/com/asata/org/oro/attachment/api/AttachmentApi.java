package com.asata.org.oro.attachment.api;

import com.asata.org.oro.context.Actor;
import com.asata.org.oro.message.dto.MessageDTO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.UUID;

@Tag(name = "Attachments", description = "The files of the messages: uploaded first, then sent with a message")
public interface AttachmentApi {

    @Operation(summary = "Upload a file",
            description = "Into a conversation the caller is an active member of; then send a message with its id")
    @PostMapping(path = "/conversations/{conversationId}/attachments", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    MessageDTO.AttachmentDTO upload(@Parameter(hidden = true) Actor actor, @PathVariable UUID conversationId,
                                    @RequestPart("file") MultipartFile file);

    @Operation(summary = "Download a file", description = "Of a message the caller may read, once uploaded and clean")
    @GetMapping("/attachments/{attachmentId}/content")
    ResponseEntity<InputStreamResource> download(@Parameter(hidden = true) Actor actor, @PathVariable UUID attachmentId);
}
