package com.leadquote.repository;

import com.leadquote.entity.Enquiry;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface EnquiryRepository extends JpaRepository<Enquiry, Long> {
    Optional<Enquiry> findByLeadId(Long leadId);
}
