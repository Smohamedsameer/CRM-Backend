package com.leadquote.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "customer_responses")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CustomerResponse {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @com.fasterxml.jackson.annotation.JsonIgnore
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "quotation_id", nullable = false)
    private Quotation quotation;

    /** ACCEPTED or CHANGES_REQUESTED */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private QuotationStatus responseType;

    @Column(length = 2000)
    private String changeRequestNotes;

    /** The admin's WhatsApp reply text, set once someone answers this request from the Client Request page. */
    @Column(length = 2000)
    private String adminReply;
    private LocalDateTime repliedAt;

    private LocalDateTime createdAt;

    @PrePersist
    void onCreate() {
        createdAt = LocalDateTime.now();
    }
}