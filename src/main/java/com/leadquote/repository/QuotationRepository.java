package com.leadquote.repository;

import com.leadquote.entity.Quotation;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface QuotationRepository extends JpaRepository<Quotation, Long> {
    Optional<Quotation> findBySecureToken(String secureToken);
    Optional<Quotation> findTopByLeadIdOrderByCreatedAtDesc(Long leadId);
    long countByQuotationNumberStartingWith(String prefix);
}
