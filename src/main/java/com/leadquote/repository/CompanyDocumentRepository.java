package com.leadquote.repository;

import com.leadquote.dto.CompanyDocumentResponse;
import com.leadquote.entity.CompanyDocument;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface CompanyDocumentRepository extends JpaRepository<CompanyDocument, Long> {

    /** Metadata only - skips the LONGBLOB column. */
    @Query("select new com.leadquote.dto.CompanyDocumentResponse(d.id, d.category, d.docType, d.fileName, "
            + "d.contentType, d.sizeBytes, d.uploadedAt) from CompanyDocument d order by d.uploadedAt desc")
    List<CompanyDocumentResponse> findAllMetadata();
}
