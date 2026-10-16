package com.leadquote.repository;

import com.leadquote.entity.QuotationPdfFile;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface QuotationPdfFileRepository extends JpaRepository<QuotationPdfFile, Long> {

    Optional<QuotationPdfFile> findByQuotationId(Long quotationId);

    /** Bulk delete: does not load the PDF bytes. */
    @Modifying
    @Query("delete from QuotationPdfFile f where f.quotationId = :quotationId")
    void deleteByQuotationId(@Param("quotationId") Long quotationId);
}
