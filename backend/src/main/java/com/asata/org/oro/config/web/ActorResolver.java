package com.asata.org.oro.config.web;

import com.asata.org.oro.config.security.SecurityProperties;
import com.asata.org.oro.context.Actor;
import com.asata.org.oro.enums.Product;
import com.asata.org.oro.exception.Forbidden;
import com.asata.org.oro.exception.Unauthorized;
import com.asata.org.oro.identity.service.IdentityService;
import com.asata.org.oro.identity.service.OrganizationAccessService;
import lombok.RequiredArgsConstructor;
import org.springframework.core.MethodParameter;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;

import java.util.UUID;

/**
 * The Actor of a request, a parameter of the controllers, from what Oro verified only: the product of the token's
 * issuer (its signature checked with that product's secret), the account in its subject (its identity, created at
 * its first request: a valid token of the product proves the account), and for Umoja the organization of the header
 * x-organization-id, which must be one the identity has a current access to (fed by Umoja). Umoja tokens carry no
 * organization: the header chooses among the accesses, it never grants one.
 */
@Component
@RequiredArgsConstructor
public class ActorResolver implements HandlerMethodArgumentResolver {

    public static final String ORGANIZATION_HEADER = "x-organization-id";

    private final SecurityProperties properties;
    private final IdentityService identities;
    private final OrganizationAccessService organizationAccess;
    private final ActorCache cache;

    @Override
    public boolean supportsParameter(MethodParameter parameter) {
        return parameter.getParameterType() == Actor.class;
    }

    @Override
    public Actor resolveArgument(MethodParameter parameter, ModelAndViewContainer container, NativeWebRequest request,
                                 WebDataBinderFactory binderFactory) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (!(authentication instanceof JwtAuthenticationToken token) || token.getToken().getSubject() == null) {
            throw new Unauthorized("Authentication required");
        }
        Product product = properties.productOfIssuer(token.getToken().getClaimAsString("iss"))
                .orElseThrow(() -> new Unauthorized("Authentication required"));
        // the identity and the access, from the cache when they were read a few seconds ago (ActorCache)
        String subject = token.getToken().getSubject();
        UUID identityId = cache.identity(product, subject);
        if (identityId == null) {
            identityId = identities.resolveOrProvision(product, subject);
            cache.putIdentity(product, subject, identityId);
        }
        String header = request.getHeader(ORGANIZATION_HEADER);
        if (product == Product.ORO) {
            if (header != null) {
                throw new IllegalArgumentException("Oro conversations have no organization: no " + ORGANIZATION_HEADER);
            }
            return Actor.oro(identityId);
        }
        if (header == null || header.isBlank()) {
            throw new IllegalArgumentException("A Umoja request names its organization in " + ORGANIZATION_HEADER);
        }
        UUID organizationId = UUID.fromString(header.strip());
        if (!cache.granted(identityId, organizationId)) {
            if (!organizationAccess.isGranted(identityId, organizationId)) {
                throw new Forbidden("No access to this organization");
            }
            cache.putGranted(identityId, organizationId);
        }
        return Actor.umoja(identityId, organizationId);
    }
}
