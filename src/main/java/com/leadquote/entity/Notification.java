package com.leadquote.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/**
 * In-app notification shown as a bell icon in the employee navbar. Created whenever a customer
 * submits the enquiry form, or requests changes to a sent quotation.
 */
@Entity
@Table(name = "notifications")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Notification {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 40)
    private NotificationType type;

    @Column(nullable = false)
    private String title;

    @Column(length = 1000)
    private String message;

    /** Reference to the related record so the frontend can deep-link (e.g. lead id). */
    private String referenceType;
    private Long referenceId;

    /** Frontend route to open when the notification is clicked, e.g. /leads/12 */
    private String link;

    // Mapped to "is_read" rather than "read", which is a reserved word in MySQL.
    @Builder.Default
    @Column(name = "is_read", nullable = false)
    private boolean read = false;

    private LocalDateTime createdAt;

    @PrePersist
    void onCreate() {
        createdAt = LocalDateTime.now();
    }
}
