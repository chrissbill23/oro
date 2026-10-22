package com.asata.org.oro.identity.api;

import com.asata.org.oro.context.Actor;
import com.asata.org.oro.identity.dto.MeDTO;
import com.asata.org.oro.identity.service.IdentityService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequiredArgsConstructor
public class MeController implements MeApi {

    private final IdentityService identities;

    @Override
    public MeDTO me(Actor actor) {
        String userId = identities.externalIds(actor.product(), List.of(actor.identityId())).get(actor.identityId());
        return new MeDTO(actor.identityId(), actor.product(), userId, actor.organizationId());
    }
}
