package com.asata.org.oro.identity.dto;

import com.asata.org.oro.enums.Product;

import java.util.UUID;

public record IdentityDTO(UUID identityId, Product product, String userId) {
}
