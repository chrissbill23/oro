package com.asata.org.oro.conversation.dto;

/** the positions of the caller in a conversation: read and delivered up to these seqs */
public record ReadStateDTO(long lastReadSeq, long lastDeliveredSeq) {
}
