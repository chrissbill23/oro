package com.asata.org.oro.identity.api;

import com.asata.org.oro.enums.Product;
import com.asata.org.oro.exception.NotFound;
import com.asata.org.oro.identity.dto.IdentityDTO;
import com.asata.org.oro.identity.service.IdentityService;
import com.asata.org.oro.identity.service.OrganizationAccessService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequiredArgsConstructor
public class IntegrationController implements IntegrationApi {

    private final IdentityService identities;
    private final OrganizationAccessService organizationAccess;

    @Override
    public IdentityDTO provision(Product product, String userId) {
        return new IdentityDTO(identities.provision(product, userId), product, userId);
    }

    @Override
    public void suspend(Product product, String userId) {
        identities.suspend(existing(product, userId));
    }

    @Override
    public void reactivate(Product product, String userId) {
        identities.reactivate(existing(product, userId));
    }

    @Override
    public void deactivate(Product product, String userId) {
        identities.deactivate(existing(product, userId));
    }

    @Override
    public void grantOrganizationAccess(Product product, UUID organizationId, String userId) {
        requireUmoja(product);
        organizationAccess.grant(identities.provision(product, userId), organizationId);
    }

    @Override
    public void revokeOrganizationAccess(Product product, UUID organizationId, String userId) {
        requireUmoja(product);
        identities.find(product, userId).ifPresent(identityId -> organizationAccess.revoke(identityId, organizationId));
    }

    private UUID existing(Product product, String userId) {
        return identities.find(product, userId).orElseThrow(() -> new NotFound("Messaging identity not found"));
    }

    private static void requireUmoja(Product product) {
        if (product != Product.UMOJA) {
            throw new IllegalArgumentException("Only Umoja has organizations");
        }
    }
}
