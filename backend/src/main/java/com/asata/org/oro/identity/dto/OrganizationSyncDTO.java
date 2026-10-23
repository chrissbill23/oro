package com.asata.org.oro.identity.dto;

/**
 * what a synchronization changed: accesses granted and revoked, team chats created, members who joined and left them;
 * applied: false when the snapshot was older than the last one applied (nothing changed)
 */
public record OrganizationSyncDTO(boolean applied, int granted, int revoked, int chatsCreated, int joined, int left) {
}
