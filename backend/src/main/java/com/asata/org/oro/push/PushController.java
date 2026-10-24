package com.asata.org.oro.push;

import com.asata.org.oro.context.Actor;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/** the browsers that take the caller's notifications (Web Push) */
@Tag(name = "Push", description = "Web Push notifications, when away")
@RestController
@RequiredArgsConstructor
@EnableConfigurationProperties(PushProperties.class)
public class PushController {

    private final PushKeys keys;
    private final PushSubscriptions subscriptions;

    public record Keys(@NotNull String p256dh, @NotNull String auth) {
    }

    /** as the browser gives it (PushSubscription.toJSON()) */
    public record SubscriptionRequest(@NotNull String endpoint, @NotNull @Valid Keys keys) {
    }

    @Operation(summary = "Oro's public push key", description = "The applicationServerKey of pushManager.subscribe (base64url)")
    @GetMapping("/push/key")
    public Map<String, String> key() {
        return Map.of("publicKey", keys.publicKey());
    }

    @Operation(summary = "Subscribe this browser", description = "Its PushSubscription; the notifications come when no "
            + "stream of the caller is open: who and where, never the text. A push service of Google, Mozilla, Apple or "
            + "Microsoft only")
    @PutMapping("/push/subscriptions")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void subscribe(@Parameter(hidden = true) Actor actor, @Valid @RequestBody SubscriptionRequest request,
                          HttpServletRequest http) {
        subscriptions.subscribe(actor, request.endpoint(), request.keys().p256dh(), request.keys().auth(),
                http.getHeader(HttpHeaders.USER_AGENT));
    }

    @Operation(summary = "Unsubscribe a browser", description = "By its endpoint")
    @DeleteMapping("/push/subscriptions")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void unsubscribe(@Parameter(hidden = true) Actor actor, @RequestParam String endpoint) {
        subscriptions.unsubscribe(actor, endpoint);
    }
}
