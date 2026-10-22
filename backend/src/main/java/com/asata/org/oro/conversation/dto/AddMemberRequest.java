package com.asata.org.oro.conversation.dto;

import jakarta.validation.constraints.NotBlank;

/** shareHistory: true, the member reads the messages sent before; false (privacy first), only the next ones */
public record AddMemberRequest(@NotBlank String userId, boolean shareHistory) {
}
