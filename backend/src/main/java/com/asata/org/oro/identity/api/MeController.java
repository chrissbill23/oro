package com.asata.org.oro.identity.api;

import com.asata.org.oro.context.Actor;
import com.asata.org.oro.identity.dto.MeDTO;
import com.asata.org.oro.identity.service.IdentityService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequiredArgsConstructor
public class MeController implements MeApi {

    private final IdentityService identities;
    private final com.asata.org.oro.identity.service.ErasureService erasures;
    private final com.asata.org.oro.account.AccountService accounts;
    private final com.asata.org.oro.identity.service.ExportService exports;

    @Override
    public MeDTO me(Actor actor) {
        String userId = identities.externalIds(actor.product(), List.of(actor.identityId())).get(actor.identityId());
        return new MeDTO(actor.identityId(), actor.product(), userId, actor.organizationId());
    }

    @Override
    public void erase(Actor actor, EraseRequest request) {
        if (actor.product() != com.asata.org.oro.enums.Product.ORO) {
            throw new com.asata.org.oro.exception.Forbidden("Your organization manages your data in Umoja: ask its administrators");
        }
        // the identity of an Oro account: its external id is the id of the Asata account
        String userId = identities.externalIds(actor.product(), List.of(actor.identityId())).get(actor.identityId());
        UUID account = UUID.fromString(userId);
        accounts.confirmPassword(account, request.password());
        erasures.request(actor.product(), userId, "self");
        accounts.closeOroSessions(account);
    }

    @Override
    public void export(Actor actor, jakarta.servlet.http.HttpServletResponse response) throws java.io.IOException {
        if (actor.product() != com.asata.org.oro.enums.Product.ORO) {
            throw new com.asata.org.oro.exception.Forbidden("Your organization manages your data in Umoja: ask its administrators");
        }
        ExportResponses.write(response, actor.identityId(), exports);
    }
}
