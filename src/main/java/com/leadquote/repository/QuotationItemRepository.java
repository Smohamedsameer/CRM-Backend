package com.leadquote.repository;

import com.leadquote.entity.QuotationItem;
import org.springframework.data.jpa.repository.JpaRepository;

public interface QuotationItemRepository extends JpaRepository<QuotationItem, Long> {
}
