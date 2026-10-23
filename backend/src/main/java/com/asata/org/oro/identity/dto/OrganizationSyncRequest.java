package com.asata.org.oro.identity.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

import java.util.List;
import java.util.UUID;

/**
 * An organization of Umoja as it is now: all its members (user ids of Umoja; the others lose their access) and all
 * its teams with their members, the lead first (the chats of the teams that are not listed lose their members).
 * version: the order of the snapshot (Umoja: microseconds since the epoch when it read it, one read at a time per
 * organization); a snapshot older than the last one applied changes nothing.
 */
public record OrganizationSyncRequest(@NotNull @PositiveOrZero Long version,
                                      @NotNull @Size(max = 100000) List<@NotBlank String> memberUserIds,
                                      @NotNull @Size(max = 10000) List<@Valid Team> teams) {

    public record Team(@NotNull UUID teamId, @NotBlank @Size(max = 128) String name,
                       @NotNull @Size(max = 100000) List<@NotBlank String> memberUserIds) {
    }
}
