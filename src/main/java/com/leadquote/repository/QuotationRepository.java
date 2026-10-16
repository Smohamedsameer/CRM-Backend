package com.leadquote.repository;

import com.leadquote.entity.Quotation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface QuotationRepository extends JpaRepository<Quotation, Long> {
    Optional<Quotation> findBySecureToken(String secureToken);
    Optional<Quotation> findTopByLeadIdOrderByCreatedAtDesc(Long leadId);
    long countByQuotationNumberStartingWith(String prefix);

    /** Every existing quotation number with this prefix, used to work out the next running number. */
    @Query("select q.quotationNumber from Quotation q where q.quotationNumber like concat(:prefix, '%')")
    List<String> findAllNumbersStartingWith(@Param("prefix") String prefix);
}
