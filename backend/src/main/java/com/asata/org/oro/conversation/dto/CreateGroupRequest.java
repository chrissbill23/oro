package com.asata.org.oro.conversation.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.List;

/** memberUserIds: accounts of the caller's product (and organization, for Umoja), besides the caller, its owner */
public record CreateGroupRequest(@NotBlank @Size(max = 128) String name, @Size(max = 1024) String description,
                                 @Size(max = 256) List<@NotBlank String> memberUserIds) {
}
