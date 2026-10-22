package com.asata.org.oro.message.dto;

import com.asata.org.oro.message.entity.Message;

/**
 * The message stored by a send; duplicate: a retry, the message was already stored. Stored only: being committed does
 * not mean delivered (a device acknowledged it: last_delivered_seq) nor read (last_read_seq).
 */
public record SendResult(Message message, boolean duplicate) {
}
