package com.leadquote.entity;

/** Which way a WhatsApp message travelled. Rows created before the chat module have a null direction = OUTBOUND. */
public enum MessageDirection {
    OUTBOUND,
    INBOUND
}
