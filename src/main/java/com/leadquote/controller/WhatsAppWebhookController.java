```java
package com.leadquote.controller;

import com.leadquote.config.WhatsAppProperties;
import com.leadquote.entity.MessageDeliveryStatus;
import com.leadquote.entity.WhatsAppMessage;
import com.leadquote.repository.WhatsAppMessageRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * Handles WhatsApp Cloud API webhook:
 * - GET  -> Meta webhook verification
 * - POST -> WhatsApp events/messages/statuses
 */
@RestController
@RequestMapping("/api/webhooks/whatsapp")
@RequiredArgsConstructor
@Slf4j
public class WhatsAppWebhookController {

    private final WhatsAppProperties whatsAppProperties;
    private final WhatsAppMessageRepository whatsAppMessageRepository;

    /**
     * Meta webhook verification
     */
    @GetMapping
    public ResponseEntity<String> verify(
            @RequestParam("hub.mode") String mode,
            @RequestParam("hub.verify_token") String verifyToken,
            @RequestParam("hub.challenge") String challenge) {

        if ("subscribe".equals(mode)
                && whatsAppProperties.getWebhookVerifyToken().equals(verifyToken)) {

            log.info("WhatsApp webhook verification successful");

            return ResponseEntity.ok(challenge);
        }

        log.warn("WhatsApp webhook verification failed");

        return ResponseEntity.status(403).build();
    }

    /**
     * Receive WhatsApp events
     */
    @SuppressWarnings("unchecked")
    @PostMapping
    public ResponseEntity<Void> receive(
            @RequestBody Map<String, Object> payload) {

        try {

            log.info("========== WHATSAPP WEBHOOK RECEIVED ==========");
            log.info("Full payload: {}", payload);

            List<Map<String, Object>> entries =
                    (List<Map<String, Object>>) payload.get("entry");

            if (entries == null) {
                log.info("No entry found");
                return ResponseEntity.ok().build();
            }

            for (Map<String, Object> entry : entries) {

                List<Map<String, Object>> changes =
                        (List<Map<String, Object>>) entry.get("changes");

                if (changes == null) {
                    continue;
                }

                for (Map<String, Object> change : changes) {

                    Map<String, Object> value =
                            (Map<String, Object>) change.get("value");

                    if (value == null) {
                        continue;
                    }

                    /*
                     * ==========================================
                     * 1. OUTBOUND MESSAGE STATUS
                     * ==========================================
                     */

                    List<Map<String, Object>> statuses =
                            (List<Map<String, Object>>) value.get("statuses");

                    if (statuses != null) {

                        for (Map<String, Object> status : statuses) {
                            applyStatusUpdate(status);
                        }
                    }

                    /*
                     * ==========================================
                     * 2. INBOUND CUSTOMER MESSAGE
                     * ==========================================
                     */

                    List<Map<String, Object>> messages =
                            (List<Map<String, Object>>) value.get("messages");

                    if (messages != null) {

                        for (Map<String, Object> message : messages) {

                            log.info("========== CUSTOMER MESSAGE ==========");

                            String from =
                                    String.valueOf(message.get("from"));

                            String messageType =
                                    String.valueOf(message.get("type"));

                            String messageId =
                                    String.valueOf(message.get("id"));

                            log.info("Customer phone : {}", from);
                            log.info("Message ID     : {}", messageId);
                            log.info("Message type   : {}", messageType);

                            /*
                             * TEXT MESSAGE
                             */
                            if ("text".equals(messageType)) {

                                Map<String, Object> text =
                                        (Map<String, Object>) message.get("text");

                                if (text != null) {

                                    String body =
                                            String.valueOf(text.get("body"));

                                    log.info("Customer reply : {}", body);
                                }
                            }

                            log.info("======================================");
                        }
                    }
                }
            }

        } catch (Exception ex) {

            log.error(
                    "Failed to process WhatsApp webhook payload",
                    ex
            );
        }

        /*
         * Always return 200 to Meta after receiving the webhook.
         */
        return ResponseEntity.ok().build();
    }

    /**
     * Process outbound WhatsApp message status
     */
    private void applyStatusUpdate(
            Map<String, Object> statusEvent) {

        String messageId =
                String.valueOf(statusEvent.get("id"));

        String status =
                String.valueOf(statusEvent.get("status"));

        log.info(
                "WhatsApp message status: {} -> {}",
                messageId,
                status
        );

        whatsAppMessageRepository
                .findByProviderMessageId(messageId)
                .ifPresent(message -> {

                    switch (status) {

                        case "delivered" -> {
                            message.setDeliveryStatus(
                                    MessageDeliveryStatus.DELIVERED
                            );

                            message.setDeliveredAt(
                                    LocalDateTime.now()
                            );
                        }

                        case "read" -> {
                            message.setDeliveryStatus(
                                    MessageDeliveryStatus.READ
                            );

                            message.setReadAt(
                                    LocalDateTime.now()
                            );
                        }

                        case "failed" -> {
                            message.setDeliveryStatus(
                                    MessageDeliveryStatus.FAILED
                            );
                        }

                        case "sent" -> {
                            message.setDeliveryStatus(
                                    MessageDeliveryStatus.SENT
                            );
                        }

                        default -> {
                            // Ignore unknown status
                        }
                    }

                    whatsAppMessageRepository.save(message);
                });
    }
}
```
