package com.asata.org.oro.conversation.dto;

import jakarta.validation.constraints.PositiveOrZero;

/** read or delivered up to seq (never back, never beyond the last message) */
public record PositionRequest(@PositiveOrZero long seq) {
}
