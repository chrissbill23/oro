package com.asata.org.oro.identity.api;

import com.asata.org.oro.enums.Product;
import com.asata.org.oro.exception.NotFound;
import com.asata.org.oro.identity.dto.IdentityDTO;
import com.asata.org.oro.identity.dto.OrganizationSyncDTO;
import com.asata.org.oro.identity.dto.OrganizationSyncRequest;
import com.asata.org.oro.identity.service.IdentityService;
import com.asata.org.oro.identity.service.OrganizationAccessService;
import com.asata.org.oro.identity.service.OrganizationSyncService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequiredArgsConstructor
public class IntegrationController implements IntegrationApi {

    private final IdentityService identities;
    private final OrganizationAccessService organizationAccess;
    private final OrganizationSyncService organizationSync;

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
    public OrganizationSyncDTO syncOrganization(Product product, UUID organizationId, OrganizationSyncRequest request) {
        requireUmoja(product);
        OrganizationSyncService.Result result = organizationSync.sync(organizationId, request.memberUserIds(),
                request.teams().stream()
                        .map(team -> new OrganizationSyncService.TeamChat(team.teamId(), team.name(), team.memberUserIds()))
                        .toList());
        return new OrganizationSyncDTO(result.granted(), result.revoked(), result.chatsCreated(), result.joined(), result.left());
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
