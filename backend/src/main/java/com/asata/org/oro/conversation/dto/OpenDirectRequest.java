package com.asata.org.oro.conversation.dto;

import jakarta.validation.constraints.NotBlank;

/** userId: the account, in the caller's product, to talk with */
public record OpenDirectRequest(@NotBlank String userId) {
}
