package com.asata.org.oro.conversation.dto;

import java.time.OffsetDateTime;

/** until: the end of the mute, null to unmute */
public record MuteRequest(OffsetDateTime until) {
}
