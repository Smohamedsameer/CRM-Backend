package com.leadquote.repository;

import com.leadquote.entity.Lead;
import com.leadquote.entity.LeadStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface LeadRepository extends JpaRepository<Lead, Long> {

    Optional<Lead> findByLeadCode(String leadCode);

    Optional<Lead> findByEnquiryToken(String enquiryToken);

    boolean existsByLeadCode(String leadCode);

    @Query("select l from Lead l where " +
            "(:status is null or l.status = :status) and " +
            "(:search is null or lower(l.customerName) like %:search% " +
            "  or lower(l.companyName) like %:search% " +
            "  or lower(l.phone) like %:search% " +
            "  or lower(l.leadCode) like %:search%)")
    Page<Lead> search(@Param("status") LeadStatus status, @Param("search") String search, Pageable pageable);

    long countByStatus(LeadStatus status);
}
