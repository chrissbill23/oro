package com.asata.org.oro.message.dto;

import java.util.List;

/** newest first; nextBeforeSeq: the cursor of the older page (before=...), null at the start */
public record MessagePageDTO(List<MessageDTO> messages, Long nextBeforeSeq) {
}
