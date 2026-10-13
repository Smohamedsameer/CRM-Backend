package com.leadquote.service;

import com.leadquote.dto.ChatDtos.ConversationDto;
import com.leadquote.dto.ChatDtos.LeadInfo;
import com.leadquote.dto.ChatDtos.MessageDto;
import com.leadquote.dto.ChatDtos.ThreadDto;
import com.leadquote.entity.Lead;
import com.leadquote.entity.MessageDeliveryStatus;
import com.leadquote.entity.MessageDirection;
import com.leadquote.entity.WhatsAppMessage;
import com.leadquote.entity.WhatsAppMessageType;
import com.leadquote.exception.NotFoundException;
import com.leadquote.repository.LeadRepository;
import com.leadquote.repository.WhatsAppMessageRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * WhatsApp-style chat on top of the whatsapp_messages table: stores the customer's inbound messages
 * (from the Meta webhook), builds the conversation list / thread for the Chat screen, and sends
 * employee replies.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ChatService {

    /** WhatsApp lets a business send free text only within 24h of the customer's last message. */
    private static final Duration WINDOW = Duration.ofHours(24);
    private static final int MAX_CONVERSATIONS = 300;

    private final WhatsAppMessageRepository messageRepository;
    private final LeadRepository leadRepository;
    private final WhatsAppService whatsAppService;

    // =====================================================================
    // Inbound: called by WhatsAppWebhookController for every customer message
    // =====================================================================

    @Transactional
    public void recordInbound(Map<String, Object> msg) {
        String wamid = str(msg.get("id"));
        String from = str(msg.get("from"));
        if (from == null || from.isBlank()) return;

        // Meta re-delivers webhooks that were not acknowledged in time: never store a message twice.
        if (wamid != null && messageRepository.findByProviderMessageId(wamid).isPresent()) return;

        String digits = from.replaceAll("[^0-9]", "");
        if (digits.length() < 7) return;
        String tail = digits.length() > 10 ? digits.substring(digits.length() - 10) : digits;

        List<Lead> matches = leadRepository.findByPhoneTail(tail);
        if (matches.isEmpty()) {
            log.warn("Inbound WhatsApp message from {} ignored: no lead has this phone number.", digits);
            return;
        }
        Lead lead = matches.get(0); // newest lead with that number

        String type = str(msg.get("type"));
        String text = extractText(type, msg);

        LocalDateTime sentAt = null;
        try {
            sentAt = LocalDateTime.ofInstant(Instant.ofEpochSecond(Long.parseLong(str(msg.get("timestamp")))),
                    ZoneId.systemDefault());
        } catch (Exception ignored) {
            // timestamp is optional for our purposes
        }

        messageRepository.save(WhatsAppMessage.builder()
                .lead(lead)
                // INBOUND_OTHER already exists in the enum for exactly this purpose.
                .type(WhatsAppMessageType.INBOUND_OTHER)
                .direction(MessageDirection.INBOUND)
                .contactPhone(digits)
                .providerMessageId(wamid)
                .body(text)
                .payloadSummary(truncate(text, 500))
                .deliveryStatus(MessageDeliveryStatus.DELIVERED)
                .agentSeen(false)
                .sentAt(sentAt)
                .build());
        log.info("Inbound WhatsApp message stored for lead {} ({})", lead.getLeadCode(), type);
    }

    @SuppressWarnings("unchecked")
    private String extractText(String type, Map<String, Object> msg) {
        if (type == null) return "[Unsupported message]";
        Map<String, Object> inner = (msg.get(type) instanceof Map<?, ?> m) ? (Map<String, Object>) m : Map.of();
        String text;
        switch (type) {
            case "text" -> text = str(inner.get("body"));
            case "button" -> text = str(inner.get("text"));
            case "interactive" -> {
                String t = null;
                for (String key : new String[]{"button_reply", "list_reply"}) {
                    if (inner.get(key) instanceof Map<?, ?> reply) {
                        t = str(reply.get("title"));
                    }
                }
                text = t;
            }
            case "image" -> text = withCaption("[Photo]", str(inner.get("caption")));
            case "video" -> text = withCaption("[Video]", str(inner.get("caption")));
            case "audio" -> text = "[Voice message]";
            case "document" -> text = withCaption("[Document]",
                    firstNonBlank(str(inner.get("caption")), str(inner.get("filename"))));
            case "sticker" -> text = "[Sticker]";
            case "location" -> text = withCaption("[Location]",
                    firstNonBlank(str(inner.get("name")), str(inner.get("address"))));
            case "reaction" -> text = "Reacted " + firstNonBlank(str(inner.get("emoji")), "");
            default -> text = "[" + type + " message]";
        }
        return text == null ? "" : text;
    }

    // =====================================================================
    // Conversation list + thread (for the Chat screen)
    // =====================================================================

    @Transactional(readOnly = true)
    public List<ConversationDto> conversations() {
        List<WhatsAppMessage> latest = messageRepository.findLatestPerLead(PageRequest.of(0, MAX_CONVERSATIONS));

        Map<Long, Long> unread = new HashMap<>();
        for (Object[] row : messageRepository.countUnreadByLead(MessageDirection.INBOUND)) {
            unread.put((Long) row[0], (Long) row[1]);
        }
        Map<Long, LocalDateTime> lastInbound = new HashMap<>();
        for (Object[] row : messageRepository.lastInboundPerLead(MessageDirection.INBOUND)) {
            lastInbound.put((Long) row[0], (LocalDateTime) row[1]);
        }

        return latest.stream().map(m -> {
            Lead lead = m.getLead();
            return new ConversationDto(
                    lead.getId(), lead.getLeadCode(), lead.getCustomerName(), lead.getPhone(),
                    truncate(displayText(m), 90),
                    isInbound(m) ? "INBOUND" : "OUTBOUND",
                    toInstant(effectiveTime(m)),
                    unread.getOrDefault(lead.getId(), 0L),
                    isOpen(lastInbound.get(lead.getId())));
        }).toList();
    }

    @Transactional(readOnly = true)
    public ThreadDto thread(Long leadId) {
        Lead lead = leadRepository.findById(leadId)
                .orElseThrow(() -> new NotFoundException("Lead not found: " + leadId));
        List<MessageDto> messages = messageRepository.findByLeadIdOrderByCreatedAtAscIdAsc(leadId)
                .stream().map(this::toDto).toList();

        LocalDateTime lastInbound = lastInbound(leadId).map(WhatsAppMessage::getCreatedAt).orElse(null);
        boolean open = isOpen(lastInbound);
        return new ThreadDto(
                new LeadInfo(lead.getId(), lead.getLeadCode(), lead.getCustomerName(), lead.getPhone()),
                open,
                open ? toInstant(lastInbound.plus(WINDOW)) : null,
                messages);
    }

    /** Called when an employee opens a chat: clears the unread badge for that lead. */
    @Transactional
    public void markRead(Long leadId) {
        messageRepository.markSeen(leadId, MessageDirection.INBOUND);
    }

    @Transactional(readOnly = true)
    public long unreadCount() {
        return messageRepository.countByDirectionAndAgentSeenFalse(MessageDirection.INBOUND);
    }

    // =====================================================================
    // Outbound: an employee types a reply
    // =====================================================================

    /**
     * Deliberately NOT @Transactional: if Meta rejects the message, WhatsAppService saves it as FAILED and
     * throws; a surrounding transaction would roll that FAILED row back and the chat would lose it.
     */
    public MessageDto sendReply(Long leadId, String text, String employeeName) {
        Lead lead = leadRepository.findById(leadId)
                .orElseThrow(() -> new NotFoundException("Lead not found: " + leadId));

        String clean = text == null ? "" : text.strip();
        if (clean.isEmpty()) throw new IllegalArgumentException("Message is empty.");

        WhatsAppMessage lastIn = lastInbound(leadId).orElse(null);
        if (lastIn == null || !isOpen(lastIn.getCreatedAt())) {
            throw new IllegalArgumentException("The 24-hour reply window is closed. WhatsApp only allows free-text "
                    + "replies within 24 hours of the customer's last message. Wait for the customer to message "
                    + "again, or send an approved template message from the lead page.");
        }

        WhatsAppMessage saved = whatsAppService.sendText(lead, lastIn.getContactPhone(), clean, employeeName);
        return toDto(saved);
    }

    // =====================================================================
    // helpers
    // =====================================================================

    private Optional<WhatsAppMessage> lastInbound(Long leadId) {
        return messageRepository.findFirstByLeadIdAndDirectionOrderByCreatedAtDesc(leadId, MessageDirection.INBOUND);
    }

    private static boolean isOpen(LocalDateTime lastInboundAt) {
        return lastInboundAt != null && lastInboundAt.isAfter(LocalDateTime.now().minus(WINDOW));
    }

    private static boolean isInbound(WhatsAppMessage m) {
        return m.getDirection() == MessageDirection.INBOUND;
    }

    /** When the bubble happened: inbound = when we received it, outbound = when it was sent (else created). */
    private static LocalDateTime effectiveTime(WhatsAppMessage m) {
        if (isInbound(m) || m.getSentAt() == null) return m.getCreatedAt();
        return m.getSentAt();
    }

    private static Instant toInstant(LocalDateTime t) {
        return t == null ? null : t.atZone(ZoneId.systemDefault()).toInstant();
    }

    private MessageDto toDto(WhatsAppMessage m) {
        boolean inbound = isInbound(m);
        boolean typed = m.getBody() != null && !m.getBody().isBlank();
        String kind = inbound ? "CUSTOMER" : (typed ? "REPLY" : "TEMPLATE");
        MessageDeliveryStatus status = m.getDeliveryStatus();
        return new MessageDto(
                m.getId(),
                inbound ? "INBOUND" : "OUTBOUND",
                kind,
                displayText(m),
                inbound || status == null ? null : status.name(),
                m.getErrorMessage(),
                m.getSentBy(),
                toInstant(effectiveTime(m)));
    }

    /**
     * Text shown in the bubble. Typed/inbound messages carry their own body. Template messages (welcome,
     * quotation, ...) only store a technical summary like "confirm_util -> Name, https://link", so turn it
     * into something readable.
     */
    private String displayText(WhatsAppMessage m) {
        if (m.getBody() != null && !m.getBody().isBlank()) return m.getBody();
        String summary = m.getPayloadSummary() == null ? "" : m.getPayloadSummary();
        int arrow = summary.indexOf(" -> ");
        String detail = arrow >= 0 ? summary.substring(arrow + 4) : summary;
        if (m.getType() == null) return detail;
        switch (m.getType()) {
            case WELCOME: {
                int comma = detail.lastIndexOf(", ");
                return "Welcome message with your enquiry form link" + (comma >= 0 ? ":\n" + detail.substring(comma + 2) : ".");
            }
            case QUOTATION:
                return "Quotation sent: " + detail;
            case ACCEPTANCE_CONFIRMATION:
                return "Order acceptance confirmation sent.";
            case FOLLOW_UP:
                return "Follow-up: " + detail;
            default:
                return detail;
        }
    }

    private static String withCaption(String label, String caption) {
        return (caption == null || caption.isBlank()) ? label : label + " " + caption;
    }

    private static String firstNonBlank(String a, String b) {
        return (a != null && !a.isBlank()) ? a : b;
    }

    private static String str(Object o) {
        return o == null ? null : o.toString();
    }

    private static String truncate(String s, int max) {
        if (s == null) return "";
        return s.length() > max ? s.substring(0, max) : s;
    }
}
