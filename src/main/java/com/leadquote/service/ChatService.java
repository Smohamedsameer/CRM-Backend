package com.leadquote.service;

import com.leadquote.dto.ChatDtos.ConversationDto;
import com.leadquote.dto.ChatDtos.LeadInfo;
import com.leadquote.dto.ChatDtos.MessageDto;
import com.leadquote.dto.ChatDtos.ThreadDto;
import com.leadquote.entity.ChatAttachment;
import com.leadquote.entity.Lead;
import com.leadquote.entity.MessageDeliveryStatus;
import com.leadquote.entity.MessageDirection;
import com.leadquote.entity.WhatsAppMessage;
import com.leadquote.entity.WhatsAppMessageType;
import com.leadquote.exception.NotFoundException;
import com.leadquote.repository.ChatAttachmentRepository;
import com.leadquote.repository.LeadRepository;
import com.leadquote.repository.WhatsAppMessageRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

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
    private final ChatAttachmentRepository attachmentRepository;
    private final LeadService leadService;
    private final AuditService auditService;

    // =====================================================================
    // Inbound: called by WhatsAppWebhookController for every customer message
    // =====================================================================

    @Transactional
    public void recordInbound(Map<String, Object> msg) {
        recordInbound(msg, null);
    }

    /** profileName = the sender's WhatsApp profile name from the webhook's "contacts" block (may be null). */
    @Transactional
    public void recordInbound(Map<String, Object> msg, String profileName) {
        String wamid = str(msg.get("id"));
        String from = str(msg.get("from"));
        if (from == null || from.isBlank()) return;

        // Meta re-delivers webhooks that were not acknowledged in time: never store a message twice.
        if (wamid != null && messageRepository.findByProviderMessageId(wamid).isPresent()) return;

        String digits = from.replaceAll("[^0-9]", "");
        if (digits.length() < 7) return;
        String tail = digits.length() > 10 ? digits.substring(digits.length() - 10) : digits;

        List<Lead> matches = leadRepository.findByPhoneTail(tail);
        // A number that is not in our leads is NOT turned into a lead: its messages are stored with lead = null
        // and listed under Chat > Other.
        Lead lead = matches.isEmpty() ? null : matches.get(0); // newest lead with that number
        if (lead == null) {
            log.info("Inbound WhatsApp message from {} is not a lead: stored under Chat > Other.", digits);
        }

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
                .contactName(profileName == null || profileName.isBlank() ? null : truncate(profileName.strip(), 200))
                .agentSeen(false)
                .sentAt(sentAt)
                .build());
        log.info("Inbound WhatsApp message stored for {} ({})", lead != null ? lead.getLeadCode() : "other number " + digits, type);
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

        List<ConversationDto> result = new java.util.ArrayList<>(latest.stream().map(m -> {
            Lead lead = m.getLead();
            return new ConversationDto(
                    lead.getId(), lead.getLeadCode(), lead.getCustomerName(), lead.getPhone(),
                    truncate(displayText(m), 90),
                    isInbound(m) ? "INBOUND" : "OUTBOUND",
                    toInstant(effectiveTime(m)),
                    unread.getOrDefault(lead.getId(), 0L),
                    isOpen(lastInbound.get(lead.getId())),
                    false);
        }).toList());

        // people who wrote to us but are not in our leads ("Other")
        Map<String, Long> otherUnread = new HashMap<>();
        for (Object[] row : messageRepository.countUnreadByOther(MessageDirection.INBOUND)) {
            otherUnread.put((String) row[0], (Long) row[1]);
        }
        Map<String, LocalDateTime> otherLastIn = new HashMap<>();
        for (Object[] row : messageRepository.lastInboundPerOther(MessageDirection.INBOUND)) {
            otherLastIn.put((String) row[0], (LocalDateTime) row[1]);
        }
        Map<String, String> otherNames = new HashMap<>();
        for (Object[] row : messageRepository.namesPerOther()) {
            otherNames.put((String) row[0], (String) row[1]);
        }
        for (WhatsAppMessage m : messageRepository.findLatestPerOther(PageRequest.of(0, MAX_CONVERSATIONS))) {
            String phone = m.getContactPhone();
            String name = otherNames.get(phone);
            result.add(new ConversationDto(
                    null, null, name != null ? name : "+" + phone, phone,
                    truncate(displayText(m), 90),
                    isInbound(m) ? "INBOUND" : "OUTBOUND",
                    toInstant(effectiveTime(m)),
                    otherUnread.getOrDefault(phone, 0L),
                    isOpen(otherLastIn.get(phone)),
                    true));
        }
        return result;
    }

    @Transactional(readOnly = true)
    public ThreadDto thread(Long leadId) {
        return threadOf(leadParty(leadId));
    }

    /** Chat with a number that is not in our leads. phone = digits incl. country code (the key from the list). */
    @Transactional(readOnly = true)
    public ThreadDto otherThread(String phone) {
        return threadOf(otherParty(phone));
    }

    private ThreadDto threadOf(Party p) {
        List<WhatsAppMessage> raw = p.lead() != null
                ? messageRepository.findByLeadIdOrderByCreatedAtAscIdAsc(p.lead().getId())
                : messageRepository.findByLeadIsNullAndContactPhoneEndingWithOrderByCreatedAtAscIdAsc(tailOf(p.phone()));
        List<MessageDto> messages = raw.stream().map(this::toDto).toList();

        LocalDateTime lastInbound = lastInbound(p).map(WhatsAppMessage::getCreatedAt).orElse(null);
        boolean open = isOpen(lastInbound);
        LeadInfo info = p.lead() != null
                ? new LeadInfo(p.lead().getId(), p.lead().getLeadCode(), p.lead().getCustomerName(), p.lead().getPhone())
                : new LeadInfo(null, null, p.name(), "+" + p.phone());
        return new ThreadDto(info, p.lead() == null, open,
                open ? toInstant(lastInbound.plus(WINDOW)) : null, messages);
    }

    /** Called when an employee opens a chat: clears the unread badge for that lead. */
    @Transactional
    public void markRead(Long leadId) {
        messageRepository.markSeen(leadId, MessageDirection.INBOUND);
    }

    @Transactional
    public void markOtherRead(String phone) {
        messageRepository.markSeenOther(tailOf(digitsOf(phone)), MessageDirection.INBOUND);
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
        return sendReplyTo(leadParty(leadId), text, employeeName);
    }

    public MessageDto sendOtherReply(String phone, String text, String employeeName) {
        return sendReplyTo(otherParty(phone), text, employeeName);
    }

    private MessageDto sendReplyTo(Party p, String text, String employeeName) {
        String clean = text == null ? "" : text.strip();
        if (clean.isEmpty()) throw new IllegalArgumentException("Message is empty.");

        // Anyone can start the conversation. Inside the 24h window the text goes out as a normal chat message;
        // outside it (or when the customer never wrote) WhatsApp only accepts an approved template.
        WhatsAppMessage lastIn = lastInbound(p).orElse(null);
        WhatsAppMessage saved;
        if (lastIn != null && isOpen(lastIn.getCreatedAt())) {
            saved = whatsAppService.sendText(p.lead(), lastIn.getContactPhone(), clean, employeeName);
        } else {
            saved = whatsAppService.sendChatTemplate(p.lead(), p.greetName(), p.phone(), clean, employeeName);
        }
        return toDto(saved);
    }

    // =====================================================================
    // Files (PDF, images, office documents) sent from the chat
    // =====================================================================

    /** What the Chat screen may send: extension -> the MIME type WhatsApp expects. (We do not trust the browser's type.) */
    private static final Map<String, String> ALLOWED_FILES = Map.ofEntries(
            Map.entry("pdf", "application/pdf"),
            Map.entry("doc", "application/msword"),
            Map.entry("docx", "application/vnd.openxmlformats-officedocument.wordprocessingml.document"),
            Map.entry("xls", "application/vnd.ms-excel"),
            Map.entry("xlsx", "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"),
            Map.entry("ppt", "application/vnd.ms-powerpoint"),
            Map.entry("pptx", "application/vnd.openxmlformats-officedocument.presentationml.presentation"),
            Map.entry("txt", "text/plain"),
            Map.entry("jpg", "image/jpeg"),
            Map.entry("jpeg", "image/jpeg"),
            Map.entry("png", "image/png"));
    private static final long MAX_FILE_BYTES = 10L * 1024 * 1024;   // our upload limit (spring.servlet.multipart)
    private static final long MAX_IMAGE_BYTES = 5L * 1024 * 1024;   // WhatsApp's limit for images

    /** Not @Transactional for the same reason as sendReply: a rejected message must stay saved as FAILED. */
    public MessageDto sendAttachment(Long leadId, MultipartFile file, String caption, String employeeName) {
        return sendAttachmentTo(leadParty(leadId), file, caption, employeeName);
    }

    public MessageDto sendOtherAttachment(String phone, MultipartFile file, String caption, String employeeName) {
        return sendAttachmentTo(otherParty(phone), file, caption, employeeName);
    }

    private MessageDto sendAttachmentTo(Party p, MultipartFile file, String caption, String employeeName) {
        if (file == null || file.isEmpty()) throw new IllegalArgumentException("Please choose a file to send.");

        String name = cleanFileName(file.getOriginalFilename());
        String ext = name.contains(".") ? name.substring(name.lastIndexOf('.') + 1).toLowerCase() : "";
        String mime = ALLOWED_FILES.get(ext);
        if (mime == null) {
            throw new IllegalArgumentException("This file type cannot be sent on WhatsApp. Allowed: PDF, Word, Excel, PowerPoint, TXT, JPG and PNG.");
        }
        if (file.getSize() > MAX_FILE_BYTES) throw new IllegalArgumentException("The file is larger than 10 MB.");
        if (mime.startsWith("image/") && file.getSize() > MAX_IMAGE_BYTES) {
            throw new IllegalArgumentException("WhatsApp accepts images up to 5 MB. Send it as a PDF or make it smaller.");
        }
        String cap = caption == null ? "" : caption.strip();
        if (cap.length() > 1024) throw new IllegalArgumentException("A file caption can have at most 1024 characters.");

        // WhatsApp only allows a file inside the 24h window (outside it, only approved templates can be sent).
        WhatsAppMessage lastIn = lastInbound(p).orElse(null);
        if (lastIn == null || !isOpen(lastIn.getCreatedAt())) {
            throw new IllegalArgumentException("Files can only be sent while the customer's 24-hour chat window is open. "
                    + "Ask the customer to send a message first, or send a text message instead.");
        }
        byte[] bytes;
        try {
            bytes = file.getBytes();
        } catch (java.io.IOException e) {
            throw new IllegalArgumentException("Could not read the file: " + e.getMessage());
        }
        return toDto(whatsAppService.sendMedia(p.lead(), lastIn.getContactPhone(), bytes, name, mime, cap, employeeName));
    }

    /** The stored file of a chat message, for the "open / download" action. */
    @Transactional(readOnly = true)
    public StoredFile attachment(Long messageId) {
        WhatsAppMessage m = messageRepository.findById(messageId)
                .orElseThrow(() -> new NotFoundException("Message not found: " + messageId));
        if (Boolean.TRUE.equals(m.getDeleted()) || m.getAttachmentName() == null) {
            throw new NotFoundException("This message has no file.");
        }
        ChatAttachment a = attachmentRepository.findByMessageId(messageId)
                .orElseThrow(() -> new NotFoundException("The file is no longer stored."));
        return new StoredFile(m.getAttachmentName(), m.getAttachmentMime(), a.getData());
    }

    public record StoredFile(String name, String mime, byte[] data) {
    }

    // =====================================================================
    // Delete for everyone
    // =====================================================================

    /**
     * Removes one of OUR sent messages from the CRM chat for every employee (text and file are wiped; only a
     * "This message was deleted" marker stays). WhatsApp has no API to delete a message from the customer's
     * phone, so the customer still sees it. An employee can delete their own messages; an admin can delete any.
     */
    @Transactional
    public MessageDto deleteMessage(Long messageId, String employeeName, boolean admin) {
        WhatsAppMessage m = messageRepository.findById(messageId)
                .orElseThrow(() -> new NotFoundException("Message not found: " + messageId));
        if (isInbound(m)) throw new IllegalArgumentException("Customer messages cannot be deleted.");
        if (Boolean.TRUE.equals(m.getDeleted())) return toDto(m);
        boolean own = employeeName != null && employeeName.equals(m.getSentBy());
        if (!admin && !own) throw new IllegalArgumentException("You can only delete messages that you sent.");

        m.setDeleted(true);
        m.setDeletedBy(employeeName);
        m.setDeletedAt(LocalDateTime.now());
        m.setBody(null);
        m.setPayloadSummary("[deleted]");
        m.setRequestPayload(null);   // a deleted message can no longer be retried
        m.setErrorMessage(null);
        m.setAttachmentName(null);
        m.setAttachmentMime(null);
        m.setAttachmentSize(null);
        attachmentRepository.deleteByMessageId(messageId);
        messageRepository.save(m);
        auditService.log(m.getLead() != null ? "Lead" : "WhatsAppMessage", m.getLead() != null ? m.getLead().getId() : messageId, "CHAT_MESSAGE_DELETED",
                "Chat message " + messageId + " deleted for everyone by " + employeeName);
        return toDto(m);
    }

    private static String cleanFileName(String original) {
        String n = original == null ? "file" : original.replace('\\', '/');
        n = n.substring(n.lastIndexOf('/') + 1).replaceAll("[\\p{Cntrl}\"<>|?*:]", "_").strip();
        if (n.isEmpty()) n = "file";
        if (n.length() > 100) {
            int dot = n.lastIndexOf('.');
            String ext = dot > 0 && n.length() - dot <= 8 ? n.substring(dot) : "";
            n = n.substring(0, 100 - ext.length()) + ext;
        }
        return n;
    }

    // =====================================================================
    // helpers
    // =====================================================================

    /** Who a conversation is with: a lead, or (lead == null) a number that is not in our leads. */
    private record Party(Lead lead, String phone, String name, String greetName) {
    }

    private Party leadParty(Long leadId) {
        Lead lead = leadRepository.findById(leadId)
                .orElseThrow(() -> new NotFoundException("Lead not found: " + leadId));
        return new Party(lead, lead.getPhone(), lead.getCustomerName(), lead.getCustomerName());
    }

    private Party otherParty(String phoneKey) {
        String digits = digitsOf(phoneKey);
        if (digits.length() < 7) throw new IllegalArgumentException("Invalid phone number.");
        String tail = tailOf(digits);
        String name = null;
        for (Object[] row : messageRepository.namesPerOther()) {
            String p = (String) row[0];
            if (p != null && p.endsWith(tail)) name = (String) row[1];
        }
        // the number exactly as WhatsApp reports it (replies must go to that), else what the caller gave
        String phone = messageRepository.findFirstByLeadIsNullAndDirectionAndContactPhoneEndingWithOrderByCreatedAtDesc(
                MessageDirection.INBOUND, tail).map(WhatsAppMessage::getContactPhone).orElse(digits);
        return new Party(null, phone, name != null ? name : "+" + phone, name != null ? name : "there");
    }

    private Optional<WhatsAppMessage> lastInbound(Party p) {
        return p.lead() != null
                ? lastInbound(p.lead().getId())
                : messageRepository.findFirstByLeadIsNullAndDirectionAndContactPhoneEndingWithOrderByCreatedAtDesc(
                        MessageDirection.INBOUND, tailOf(p.phone()));
    }

    private Optional<WhatsAppMessage> lastInbound(Long leadId) {
        return messageRepository.findFirstByLeadIdAndDirectionOrderByCreatedAtDesc(leadId, MessageDirection.INBOUND);
    }

    private static String digitsOf(String s) {
        return s == null ? "" : s.replaceAll("[^0-9]", "");
    }

    private static String tailOf(String digits) {
        return digits.length() > 10 ? digits.substring(digits.length() - 10) : digits;
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
        boolean deleted = Boolean.TRUE.equals(m.getDeleted());
        boolean hasFile = !deleted && m.getAttachmentName() != null;
        boolean typed = hasFile || (m.getBody() != null && !m.getBody().isBlank());
        String kind = deleted ? "DELETED" : inbound ? "CUSTOMER" : (typed ? "REPLY" : "TEMPLATE");
        MessageDeliveryStatus status = m.getDeliveryStatus();
        // a file bubble shows the file card + its caption; displayText() describes it for the conversation list
        String text = deleted ? "This message was deleted" : hasFile ? (m.getBody() == null ? "" : m.getBody()) : displayText(m);
        return new MessageDto(
                m.getId(),
                inbound ? "INBOUND" : "OUTBOUND",
                kind,
                text,
                inbound || deleted || status == null ? null : status.name(),
                deleted ? null : m.getErrorMessage(),
                m.getSentBy(),
                toInstant(effectiveTime(m)),
                hasFile ? m.getAttachmentName() : null,
                hasFile ? m.getAttachmentMime() : null,
                hasFile ? m.getAttachmentSize() : null);
    }

    /**
     * Text shown in the bubble. Typed/inbound messages carry their own body. Template messages (welcome,
     * quotation, ...) only store a technical summary like "confirm_util -> Name, https://link", so turn it
     * into something readable.
     */
    private String displayText(WhatsAppMessage m) {
        if (Boolean.TRUE.equals(m.getDeleted())) return "This message was deleted";
        if (m.getAttachmentName() != null) {
            String label = (m.getAttachmentMime() != null && m.getAttachmentMime().startsWith("image/") ? "[Photo] " : "[File] ") + m.getAttachmentName();
            return m.getBody() != null && !m.getBody().isBlank() ? label + " " + m.getBody() : label;
        }
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
            case ENQUIRY_FORM:
                return "Reminder: please complete your enquiry form.";
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
