package com.leadquote.repository;

import com.leadquote.entity.Lead;
import com.leadquote.entity.LeadStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
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

    // ---- Dashboard analytics ----
    @Query("select l.status, count(l) from Lead l group by l.status")
    List<Object[]> countGroupedByStatus();

    @Query("select l.source, count(l) from Lead l group by l.source")
    List<Object[]> countGroupedBySource();

    @Query("select l.createdAt from Lead l where l.createdAt >= :from")
    List<LocalDateTime> findCreatedAtSince(@Param("from") LocalDateTime from);
}
