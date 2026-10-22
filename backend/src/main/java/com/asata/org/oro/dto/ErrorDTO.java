package com.asata.org.oro.dto;

import java.time.OffsetDateTime;
import java.util.List;

/** the body of every error response */
public record ErrorDTO(OffsetDateTime createdAt, String requestId, String url, int status, String summary, String message,
                       List<String> messages) {
}
