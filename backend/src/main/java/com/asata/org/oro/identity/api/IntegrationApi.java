package com.asata.org.oro.identity.api;

import com.asata.org.oro.enums.Product;
import com.asata.org.oro.identity.dto.IdentityDTO;
import com.asata.org.oro.identity.dto.OrganizationSyncDTO;
import com.asata.org.oro.identity.dto.OrganizationSyncRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

/**
 * For the trusted integration of a product, authenticated by its key (x-integration-key): it acts for its own product
 * only. Never called by a user.
 */
@Tag(name = "Integration", description = "Trusted integrations of the products: identities and Umoja organization accesses")
@RequestMapping("/internal")
public interface IntegrationApi {

    @Operation(summary = "Provision an identity", description = "Idempotent: the identity of an account of the product")
    @PutMapping("/identities/{userId}")
    IdentityDTO provision(@Parameter(hidden = true) @AuthenticationPrincipal Product product, @PathVariable String userId);

    @Operation(summary = "Suspend an identity", description = "It keeps its conversations but reads and sends nothing")
    @PostMapping("/identities/{userId}/suspend")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void suspend(@Parameter(hidden = true) @AuthenticationPrincipal Product product, @PathVariable String userId);

    @Operation(summary = "Reactivate a suspended identity")
    @PostMapping("/identities/{userId}/reactivate")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void reactivate(@Parameter(hidden = true) @AuthenticationPrincipal Product product, @PathVariable String userId);

    @Operation(summary = "Deactivate an identity", description = "Final: it leaves all its conversations")
    @PostMapping("/identities/{userId}/deactivate")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void deactivate(@Parameter(hidden = true) @AuthenticationPrincipal Product product, @PathVariable String userId);

    @Operation(summary = "Erase the data of an identity", description = "Its messages become deletions, its reactions "
            + "and files go, nothing that stays links to the person; deactivated at once, erased within a minute. 202, "
            + "whether Oro had something of it or not. With organizationId: in the conversations of that organization "
            + "only (asked by the organization); the identity, its access and its other organizations untouched")
    @PostMapping("/identities/{userId}/erase")
    @org.springframework.web.bind.annotation.ResponseStatus(org.springframework.http.HttpStatus.ACCEPTED)
    void erase(@Parameter(hidden = true) @AuthenticationPrincipal Product product, @PathVariable String userId,
               @RequestParam(required = false) UUID organizationId);

    @Operation(summary = "Export the data of an identity", description = "For the organization to answer a request of "
            + "access: what the person wrote and did in Oro, as JSON (as GET /me/export). With organizationId: in the "
            + "conversations of that organization only (asked by the organization). 404 when Oro has nothing of it")
    @GetMapping(value = "/identities/{userId}/export", produces = org.springframework.http.MediaType.APPLICATION_JSON_VALUE)
    void export(@Parameter(hidden = true) @AuthenticationPrincipal Product product, @PathVariable String userId,
                @RequestParam(required = false) UUID organizationId,
                @Parameter(hidden = true) jakarta.servlet.http.HttpServletResponse response) throws java.io.IOException;

    @Operation(summary = "Synchronize an organization (Umoja)",
            description = "Its members (the others lose their access and leave its conversations) and the chats of its teams "
                    + "(their names and members); idempotent, sent on every change and periodically. One at a time per "
                    + "organization; a snapshot older than the last applied (version) changes nothing: applied false")
    @PutMapping("/organizations/{organizationId}")
    OrganizationSyncDTO syncOrganization(@Parameter(hidden = true) @AuthenticationPrincipal Product product,
                                         @PathVariable UUID organizationId, @Valid @RequestBody OrganizationSyncRequest request);

    @Operation(summary = "Grant an organization access (Umoja)",
            description = "Idempotent; the user may then take part in the conversations of the organization")
    @PutMapping("/organizations/{organizationId}/members/{userId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void grantOrganizationAccess(@Parameter(hidden = true) @AuthenticationPrincipal Product product,
                                 @PathVariable UUID organizationId, @PathVariable String userId);

    @Operation(summary = "Revoke an organization access (Umoja)",
            description = "Idempotent; the user leaves the conversations of the organization at once")
    @DeleteMapping("/organizations/{organizationId}/members/{userId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void revokeOrganizationAccess(@Parameter(hidden = true) @AuthenticationPrincipal Product product,
                                  @PathVariable UUID organizationId, @PathVariable String userId);
}
