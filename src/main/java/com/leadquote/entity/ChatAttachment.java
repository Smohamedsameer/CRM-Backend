package com.leadquote.entity;

import jakarta.persistence.*;
import lombok.*;

/**
 * The bytes of a file sent from the Chat screen. Kept in its own table (not on whatsapp_messages) so that
 * loading a conversation never pulls megabytes of file data - it is read only when someone opens the file.
 * Stored in the database because Railway's disk is wiped on every deploy.
 */
@Entity
@Table(name = "chat_attachments", indexes = @Index(name = "idx_chat_attachment_message", columnList = "message_id", unique = true))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ChatAttachment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "message_id", nullable = false)
    private Long messageId;

    @Lob
    @Column(name = "data", nullable = false, columnDefinition = "LONGBLOB")
    private byte[] data;
}
