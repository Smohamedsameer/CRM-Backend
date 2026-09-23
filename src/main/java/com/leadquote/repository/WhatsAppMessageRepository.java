package com.leadquote.repository;

import com.leadquote.entity.WhatsAppMessage;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface WhatsAppMessageRepository extends JpaRepository<WhatsAppMessage, Long> {
    Optional<WhatsAppMessage> findByProviderMessageId(String providerMessageId);
}
