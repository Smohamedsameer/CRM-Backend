package com.leadquote.controller;

import com.leadquote.config.WhatsAppProperties;
import com.leadquote.entity.MessageDeliveryStatus;
import com.leadquote.entity.WhatsAppMessage;
import com.leadquote.repository.WhatsAppMessageRepository;
import com.leadquote.service.ChatService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * Handles the WhatsApp Cloud API webhook: verification (GET) and event delivery (POST).
 * Configure this URL in the Meta App dashboard, together with WHATSAPP_WEBHOOK_VERIFY_TOKEN.
 */
@RestController
@RequestMapping("/api/webhooks/whatsapp")
@RequiredArgsConstructor
@Slf4j
public class WhatsAppWebhookController {

    private final WhatsAppProperties whatsAppProperties;
    private final WhatsAppMessageRepository whatsAppMessageRepository;
    private final ChatService chatService;

    @GetMapping
    public ResponseEntity<String> verify(@RequestParam("hub.mode") String mode,
                                          @RequestParam("hub.verify_token") String verifyToken,
                                          @RequestParam("hub.challenge") String challenge) {
        if ("subscribe".equals(mode) && whatsAppProperties.getWebhookVerifyToken().equals(verifyToken)) {
            return ResponseEntity.ok(challenge);
        }
        return ResponseEntity.status(403).build();
    }

    @SuppressWarnings("unchecked")
    @PostMapping
    public ResponseEntity<Void> receive(@RequestBody Map<String, Object> payload) {
        try {
            List<Map<String, Object>> entries = (List<Map<String, Object>>) payload.get("entry");
            if (entries == null) return ResponseEntity.ok().build();

            for (Map<String, Object> entry : entries) {
                List<Map<String, Object>> changes = (List<Map<String, Object>>) entry.get("changes");
                if (changes == null) continue;

                for (Map<String, Object> change : changes) {
                    Map<String, Object> value = (Map<String, Object>) change.get("value");
                    if (value == null) continue;

                    List<Map<String, Object>> statuses = (List<Map<String, Object>>) value.get("statuses");
                    if (statuses != null) {
                        statuses.forEach(this::applyStatusUpdate);
                    }

                    // Inbound customer messages: stored so they show up in the Chat screen.
                    List<Map<String, Object>> inbound = (List<Map<String, Object>>) value.get("messages");
                    if (inbound != null) {
                        for (Map<String, Object> inboundMessage : inbound) {
                            try {
                                chatService.recordInbound(inboundMessage);
                            } catch (Exception ex) {
                                log.error("Failed to store inbound WhatsApp message: {}", ex.getMessage());
                            }
                        }
                    }
                }
            }
        } catch (Exception ex) {
            log.error("Failed to process WhatsApp webhook payload: {}", ex.getMessage());
        }
        return ResponseEntity.ok().build();
    }

    private void applyStatusUpdate(Map<String, Object> statusEvent) {
        String messageId = String.valueOf(statusEvent.get("id"));
        String status = String.valueOf(statusEvent.get("status")); // sent | delivered | read | failed

        whatsAppMessageRepository.findByProviderMessageId(messageId).ifPresent(message -> {
            switch (status) {
                case "delivered" -> {
                    message.setDeliveryStatus(MessageDeliveryStatus.DELIVERED);
                    message.setDeliveredAt(LocalDateTime.now());
                }
                case "read" -> {
                    message.setDeliveryStatus(MessageDeliveryStatus.READ);
                    message.setReadAt(LocalDateTime.now());
                }
                case "failed" -> message.setDeliveryStatus(MessageDeliveryStatus.FAILED);
                case "sent" -> message.setDeliveryStatus(MessageDeliveryStatus.SENT);
                default -> { /* ignore unknown status */ }
            }
            whatsAppMessageRepository.save(message);
        });
    }
}
