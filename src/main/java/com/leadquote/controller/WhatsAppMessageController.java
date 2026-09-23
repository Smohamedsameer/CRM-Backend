package com.leadquote.controller;

import com.leadquote.entity.WhatsAppMessage;
import com.leadquote.exception.NotFoundException;
import com.leadquote.repository.WhatsAppMessageRepository;
import com.leadquote.service.WhatsAppService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/** Employee-facing view of WhatsApp message history + manual retry for failed sends. */
@RestController
@RequestMapping("/api/whatsapp-messages")
@RequiredArgsConstructor
public class WhatsAppMessageController {

    private final WhatsAppMessageRepository whatsAppMessageRepository;
    private final WhatsAppService whatsAppService;

    @PostMapping("/{id}/retry")
    public WhatsAppMessage retry(@PathVariable Long id) {
        WhatsAppMessage message = whatsAppMessageRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Message not found: " + id));
        return whatsAppService.retry(message);
    }
}
