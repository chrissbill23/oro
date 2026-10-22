package com.asata.org.oro.live.api;

import com.asata.org.oro.context.Actor;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.Set;

@Tag(name = "Live", description = "What changes, as it changes: the stream of the caller, and who is online")
public interface LiveApi {

    @Operation(summary = "The stream of the caller (Server-Sent Events)",
            description = "JSON events: hello (who is online), presence, message (created, edited, deleted, reaction), "
                    + "conversation (created, updated, members), typing, read. Ids and numbers only: read the content "
                    + "through the API. Closed after 30 minutes: open it again.")
    @GetMapping(path = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    SseEmitter stream(@Parameter(hidden = true) Actor actor);

    @Operation(summary = "Who is online", description = "The users of the caller's context with a stream open")
    @GetMapping("/presence")
    Set<String> presence(@Parameter(hidden = true) Actor actor);
}
