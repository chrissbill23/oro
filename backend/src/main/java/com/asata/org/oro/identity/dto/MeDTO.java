package com.asata.org.oro.identity.dto;

import com.asata.org.oro.enums.Product;

import java.util.UUID;

/** the caller as Oro knows it: userId is the id of its account in its product */
public record MeDTO(UUID identityId, Product product, String userId, UUID organizationId) {
}
