package com.leadquote.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "whatsapp_messages")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class WhatsAppMessage {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Null for a customer who wrote to us but is not in our leads: those chats are shown under Chat > Other. */
    @com.fasterxml.jackson.annotation.JsonIgnore
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "lead_id")
    private Lead lead;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private WhatsAppMessageType type;

    private String toPhone;

    /** WhatsApp message id (wamid) returned by the Graph API, used to correlate webhook status updates. */
    private String providerMessageId;

    @Enumerated(EnumType.STRING)
    @Builder.Default
    private MessageDeliveryStatus deliveryStatus = MessageDeliveryStatus.QUEUED;

    @Column(length = 4000)
    private String payloadSummary;

    @Column(length = 2000)
    private String errorMessage;

    /** Exact JSON payload sent to the Graph API /messages endpoint, so a retry can replay it
     * verbatim (template messages, document messages, etc.) instead of being rebuilt as plain text. */
    @Column(columnDefinition = "TEXT")
    private String requestPayload;

    private int retryCount;

    // ---- Chat module (all nullable, so existing rows and ddl-auto=update keep working) ----

    /** OUTBOUND (us -> customer) or INBOUND (customer -> us). Null on old rows = OUTBOUND. */
    @Enumerated(EnumType.STRING)
    private MessageDirection direction;

    /** Free text: an employee's chat reply, or the text of a customer's inbound message. */
    @Column(columnDefinition = "TEXT")
    private String body;

    /** The customer's WhatsApp number exactly as Meta reports it (digits incl. country code). Replies go here. */
    private String contactPhone;

    /** The sender's WhatsApp profile name (from the webhook), shown for people who are not in our leads. */
    private String contactName;

    /** Employee who typed a chat reply. */
    private String sentBy;

    /** Inbound messages only: false until an employee opens the chat. Null (old rows / outbound) counts as seen. */
    private Boolean agentSeen;

    /** File sent from the Chat screen (the bytes live in chat_attachments, loaded only when opened). */
    private String attachmentName;
    private String attachmentMime;
    private Long attachmentSize;

    /** "Delete for everyone": the message is removed from the CRM chat for every employee (it stays on the customer's WhatsApp). */
    private Boolean deleted;
    private String deletedBy;
    private LocalDateTime deletedAt;

    private LocalDateTime sentAt;
    private LocalDateTime deliveredAt;
    private LocalDateTime readAt;
    private LocalDateTime createdAt;

    @PrePersist
    void onCreate() {
        createdAt = LocalDateTime.now();
    }
}