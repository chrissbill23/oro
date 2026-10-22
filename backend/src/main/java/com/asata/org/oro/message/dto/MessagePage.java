package com.asata.org.oro.message.dto;

import com.asata.org.oro.message.entity.Message;

import java.util.List;

/** a page of messages, newest first; nextBeforeSeq: the cursor of the next (older) page, null at the start */
public record MessagePage(List<Message> messages, Long nextBeforeSeq) {
}
