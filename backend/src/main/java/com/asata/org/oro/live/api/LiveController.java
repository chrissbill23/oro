package com.asata.org.oro.live.api;

import com.asata.org.oro.context.Actor;
import com.asata.org.oro.identity.service.IdentityService;
import com.asata.org.oro.live.LiveHub;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;
import java.util.Set;

@RestController
@RequiredArgsConstructor
public class LiveController implements LiveApi {

    private final LiveHub hub;
    private final IdentityService identities;

    @Override
    public SseEmitter stream(Actor actor) {
        String userId = identities.externalIds(actor.product(), List.of(actor.identityId())).get(actor.identityId());
        return hub.connect(actor, userId);
    }

    @Override
    public Set<String> presence(Actor actor) {
        return hub.online(actor.product(), actor.organizationId());
    }
}
