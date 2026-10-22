package com.asata.org.oro.context;

import com.asata.org.oro.enums.Product;

import java.util.Objects;
import java.util.UUID;

/**
 * Who a request acts for, as the API resolved it from an authenticated token of its product: the product, the
 * messaging identity (from the product's external user id, see IdentityService.resolve) and, for Umoja, the
 * organization the request is scoped to, verified by the API from the Umoja token (never taken from the request body).
 * An Oro actor has no organization; a Umoja actor always has one (every Umoja conversation belongs to one).
 */
public record Actor(Product product, UUID identityId, UUID organizationId) {

    public Actor {
        Objects.requireNonNull(product, "product");
        Objects.requireNonNull(identityId, "identityId");
        if (product == Product.UMOJA && organizationId == null) {
            throw new IllegalArgumentException("a Umoja actor acts in an organization");
        }
        if (product == Product.ORO && organizationId != null) {
            throw new IllegalArgumentException("an Oro actor has no organization");
        }
    }

    public static Actor oro(UUID identityId) {
        return new Actor(Product.ORO, identityId, null);
    }

    public static Actor umoja(UUID identityId, UUID organizationId) {
        return new Actor(Product.UMOJA, identityId, organizationId);
    }
}
