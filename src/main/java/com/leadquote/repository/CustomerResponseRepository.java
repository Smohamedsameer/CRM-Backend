package com.leadquote.repository;

import com.leadquote.entity.CustomerResponse;
import com.leadquote.entity.QuotationStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface CustomerResponseRepository extends JpaRepository<CustomerResponse, Long> {
    List<CustomerResponse> findByResponseTypeOrderByCreatedAtDesc(QuotationStatus responseType);
}
