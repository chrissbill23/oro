package com.asata.org.oro.message.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record EditMessageRequest(@NotBlank @Size(max = 16000) String body) {
}
