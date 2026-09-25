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

    @com.fasterxml.jackson.annotation.JsonIgnore
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "lead_id", nullable = false)
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

    private LocalDateTime sentAt;
    private LocalDateTime deliveredAt;
    private LocalDateTime readAt;
    private LocalDateTime createdAt;

    @PrePersist
    void onCreate() {
        createdAt = LocalDateTime.now();
    }
}