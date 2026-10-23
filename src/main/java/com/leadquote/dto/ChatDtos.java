package com.leadquote.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.List;

/** Response/request shapes for the chat module (kept together so the chat feature is easy to find). */
public final class ChatDtos {

    private ChatDtos() {
    }

    /** One row of the conversation list on the left of the chat screen. other = a number that is not in our leads
     * (leadId/leadCode are then null and phone is the number, which is also the key of that chat). */
    public record ConversationDto(Long leadId, String leadCode, String customerName, String phone,
                                  String lastMessage, String lastDirection, Instant lastAt,
                                  long unread, boolean windowOpen, boolean other) {
    }

    /** One chat bubble. kind = CUSTOMER (inbound), REPLY (typed or a file sent by an employee), TEMPLATE (welcome/quotation/...) or DELETED. */
    public record MessageDto(Long id, String direction, String kind, String text, String status,
                             String errorMessage, String sentBy, Instant at,
                             String attachmentName, String attachmentMime, Long attachmentSize) {
    }

    public record LeadInfo(Long id, String leadCode, String customerName, String phone) {
    }

    /** windowOpen = the customer wrote to us in the last 24h, so a free-text reply is allowed. */
    public record ThreadDto(LeadInfo lead, boolean other, boolean windowOpen, Instant windowClosesAt, List<MessageDto> messages) {
    }

    public record SendRequest(@NotBlank @Size(max = 4096) String text) {
    }
}
