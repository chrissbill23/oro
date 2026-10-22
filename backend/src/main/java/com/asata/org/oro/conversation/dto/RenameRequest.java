package com.asata.org.oro.conversation.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record RenameRequest(@NotBlank @Size(max = 128) String name, @Size(max = 1024) String description) {
}
