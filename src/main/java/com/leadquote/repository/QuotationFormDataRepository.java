package com.leadquote.repository;

import com.leadquote.entity.QuotationFormData;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface QuotationFormDataRepository extends JpaRepository<QuotationFormData, Long> {

    Optional<QuotationFormData> findByQuotationId(Long quotationId);

    @Modifying
    @Query("delete from QuotationFormData f where f.quotationId = :quotationId")
    void deleteByQuotationId(@Param("quotationId") Long quotationId);
}
