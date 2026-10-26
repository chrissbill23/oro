package com.asata.org.oro.live.api;

import com.asata.org.oro.context.Actor;
import com.asata.org.oro.identity.service.IdentityService;
import com.asata.org.oro.live.LiveHub;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@RestController
@RequiredArgsConstructor
public class LiveController implements LiveApi {

    private final LiveHub hub;
    private final IdentityService identities;
    private final com.asata.org.oro.live.PresenceContacts contacts;

    @Override
    public SseEmitter stream(Actor actor) {
        String userId = identities.externalIds(actor.product(), List.of(actor.identityId())).get(actor.identityId());
        // the stream lasts as long as the token that opens it
        Instant expiresAt = SecurityContextHolder.getContext().getAuthentication() instanceof JwtAuthenticationToken token
                ? token.getToken().getExpiresAt() : Instant.now();
        // Oro's people: their contacts read first, out of the hub's lock (a query; many streams open at once after a restart)
        return actor.product() == com.asata.org.oro.enums.Product.ORO
                ? hub.connect(actor, userId, expiresAt, contacts.of(actor.identityId()))
                : hub.connect(actor, userId, expiresAt);
    }

    @Override
    public Set<String> presence(Actor actor) {
        if (actor.product() == com.asata.org.oro.enums.Product.ORO) {
            // the people one shares a conversation with, and oneself
            Set<UUID> among = new java.util.HashSet<>(contacts.of(actor.identityId()));
            among.add(actor.identityId());
            return hub.onlineAmong(among);
        }
        return hub.online(actor.product(), actor.organizationId());
    }
}
