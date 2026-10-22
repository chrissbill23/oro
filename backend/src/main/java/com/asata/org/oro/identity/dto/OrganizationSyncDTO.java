package com.asata.org.oro.identity.dto;

/** what a synchronization changed: accesses granted and revoked, team chats created, members who joined and left them */
public record OrganizationSyncDTO(int granted, int revoked, int chatsCreated, int joined, int left) {
}
