package com.leadquote.entity;

import jakarta.persistence.*;
import lombok.*;

/**
 * Cover photo chosen for a lead's quotation. Kept in its own table (id = lead id) so the heavy
 * image is never loaded with ordinary lead queries.
 */
@Entity
@Table(name = "lead_cover_images")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class LeadCoverImage {

    @Id
    @Column(name = "lead_id")
    private Long leadId;

    @Column(name = "content_type", nullable = false, length = 100)
    private String contentType;

    @Lob
    @Column(nullable = false, columnDefinition = "LONGBLOB")
    private byte[] data;
}
