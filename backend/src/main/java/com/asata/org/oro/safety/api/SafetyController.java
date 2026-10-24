package com.asata.org.oro.safety.api;

import com.asata.org.oro.context.Actor;
import com.asata.org.oro.enums.Product;
import com.asata.org.oro.exception.NotFound;
import com.asata.org.oro.identity.service.IdentityService;
import com.asata.org.oro.safety.BlockService;
import com.asata.org.oro.safety.ReportService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/** blocking people (Oro), reporting messages (Oro and Umoja) */
@Tag(name = "Safety", description = "Blocks and reports")
@RestController
@RequiredArgsConstructor
public class SafetyController {

    private final BlockService blocks;
    private final ReportService reports;
    private final IdentityService identities;

    public record ReportRequest(@NotNull UUID conversationId, @NotNull List<UUID> messageIds, @NotNull String category,
                                String reason) {
    }

    @Operation(summary = "The people I blocked (Oro)")
    @GetMapping("/blocks")
    public List<String> blocked(@Parameter(hidden = true) Actor actor) {
        List<UUID> ids = blocks.blocked(actor);
        Map<UUID, String> userIds = ids.isEmpty() ? Map.of() : identities.externalIds(actor.product(), ids);
        return ids.stream().map(userIds::get).toList();
    }

    @Operation(summary = "Block someone (Oro)", description = "No direct conversation nor group with me from them; their "
            + "waiting request declined. They are not told")
    @PutMapping("/blocks/{userId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void block(@Parameter(hidden = true) Actor actor, @PathVariable String userId) {
        blocks.block(actor, identity(actor, userId));
    }

    @Operation(summary = "Unblock someone (Oro)")
    @DeleteMapping("/blocks/{userId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void unblock(@Parameter(hidden = true) Actor actor, @PathVariable String userId) {
        blocks.unblock(actor, identity(actor, userId));
    }

    @Operation(summary = "Report messages", description = "1 to 20 messages of a conversation I read (a request too): "
            + "a copy kept 90 days for review; category: spam, harassment, illegal, other")
    @PostMapping("/reports")
    @ResponseStatus(HttpStatus.CREATED)
    public Map<String, UUID> report(@Parameter(hidden = true) Actor actor, @Valid @RequestBody ReportRequest request) {
        return Map.of("id", reports.report(actor, request.conversationId(), request.messageIds(), request.category(), request.reason()));
    }

    private UUID identity(Actor actor, String userId) {
        if (actor.product() != Product.ORO) {
            throw new com.asata.org.oro.exception.Forbidden("Your organization decides who you meet in Umoja");
        }
        return identities.find(actor.product(), userId).orElseThrow(() -> new NotFound("User not found"));
    }
}
