package com.leadquote.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.leadquote.config.CompanyProperties;
import com.leadquote.config.WhatsAppProperties;
import com.leadquote.entity.*;
import com.leadquote.exception.WhatsAppApiException;
import com.leadquote.repository.ChatAttachmentRepository;
import com.leadquote.repository.WhatsAppMessageRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.BodyInserters;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Wraps the official WhatsApp Business (Cloud) API. Never uses browser automation.
 * Credentials always come from environment-backed configuration (see WhatsAppProperties),
 * never hard-coded, never sent to the frontend.
 *
 * Every business-initiated message uses a Meta-APPROVED "utility" template
 * (confirm_util, remind_util, pdf_util / enquiry_util, order_util, customer_util - see WhatsAppProperties). WhatsApp
 * requires an approved template for any message sent outside an open 24h customer service
 * window, and simply rejects a free-text "text" type message in that case - so this service
 * always sends "type": "template", never "type": "text", for these four flows.
 *
 * If a call fails, we persist the failure (and the exact request payload, so a retry can
 * replay it verbatim) on the WhatsAppMessage row instead of throwing away the lead/quotation,
 * so an employee can retry manually from the dashboard.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class WhatsAppService {

    private final WebClient webClient;
    private final WhatsAppProperties whatsAppProperties;
    private final CompanyProperties companyProperties;
    private final SettingsService settingsService;
    private final WhatsAppMessageRepository whatsAppMessageRepository;
    private final ChatAttachmentRepository chatAttachmentRepository;
    private final ObjectMapper objectMapper;

    // =========================================================================
    // confirm_util - "Hello {{1}} , thank you for your enquiry ... {{2}}" (2 body params:
    // customer name, enquiry form link). Fired the moment a new lead is created.
    // =========================================================================
    public WhatsAppMessage sendWelcomeMessage(Lead lead, String enquiryFormLink) {
        List<Map<String, Object>> components = List.of(
                bodyComponent(lead.getCustomerName(), enquiryFormLink)
        );
        String summary = "confirm_util -> " + lead.getCustomerName() + ", " + enquiryFormLink;
        return sendTemplateMessage(lead, WhatsAppMessageType.WELCOME,
                whatsAppProperties.getConfirmTemplateName(), components, summary);
    }

    // =========================================================================
    // Quotation ready. Two approved templates are supported:
    //  - pdf_util     : HEADER = the quotation PDF (document). The customer sees a PDF card at the top of
    //                   the message; tapping it opens the PDF in WhatsApp, where it can be saved/downloaded.
    //  - enquiry_util : old template, IMAGE header, PDF only reachable through the link in the body.
    // pdf_util is used whenever whatsapp.pdf-template-name is set. Both take the same 4 body params:
    // customer name, quotation no, amount, quotation page link.
    // =========================================================================
    public WhatsAppMessage sendQuotation(Lead lead, Quotation quotation, String quotationPageLink) {
        List<Map<String, Object>> components = new ArrayList<>();
        String templateName;
        String pdfTemplate = whatsAppProperties.getPdfTemplateName();

        if (pdfTemplate != null && !pdfTemplate.isBlank()) {
            templateName = pdfTemplate.trim();
            String pdfUrl = resolveBackendBaseUrl() + "/api/public/quotations/" + quotation.getSecureToken() + "/pdf";
            String fileName = quotation.getQuotationNumber().replace("/", "-") + ".pdf";
            components.add(headerDocumentComponent(pdfUrl, fileName));
        } else {
            templateName = whatsAppProperties.getEnquiryTemplateName();
        }

        components.add(bodyComponent(
                lead.getCustomerName(),
                quotation.getQuotationNumber(),
                String.valueOf(quotation.getTotalAmount()),
                quotationPageLink
        ));

        String summary = templateName + " -> quotation " + quotation.getQuotationNumber()
                + ", total " + quotation.getTotalAmount();
        return sendTemplateMessage(lead, WhatsAppMessageType.QUOTATION, templateName, components, summary);
    }

    // =========================================================================
    // remind_util - reminder sent the next day if the customer has not filled the enquiry form.
    // Body params: customer name, enquiry form link. Stored with type ENQUIRY_FORM (an existing enum
    // value, so no database change is needed).
    // =========================================================================
    public WhatsAppMessage sendEnquiryReminder(Lead lead, String enquiryFormLink) {
        List<Map<String, Object>> components = List.of(
                bodyComponent(lead.getCustomerName(), enquiryFormLink)
        );
        String summary = whatsAppProperties.getReminderTemplateName() + " -> "
                + lead.getCustomerName() + ", " + enquiryFormLink;
        return sendTemplateMessage(lead, WhatsAppMessageType.ENQUIRY_FORM,
                whatsAppProperties.getReminderTemplateName(), components, summary);
    }

    // =========================================================================
    // order_util - customer accepted. Body param: admin/company phone number.
    // (the email in the approved copy is fixed text inside the template, not a variable)
    // =========================================================================
    public WhatsAppMessage sendAcceptanceConfirmation(Lead lead) {
        String adminPhone = settingsService.getCompanyPhone();
        List<Map<String, Object>> components = List.of(
                bodyComponent(adminPhone)
        );
        String summary = "order_util -> admin phone " + adminPhone;
        return sendTemplateMessage(lead, WhatsAppMessageType.ACCEPTANCE_CONFIRMATION,
                whatsAppProperties.getOrderTemplateName(), components, summary);
    }

    /**
     * Kept only so any existing call site still compiles. order_util already carries the phone
     * number AND the (fixed) company email in one message, so there is nothing left to send here -
     * do NOT call the Graph API a second time for what is really the same template.
     */
    @Deprecated
    public void sendContactDetails(Lead lead) {
        log.debug("sendContactDetails() is a no-op - order_util already includes phone + email");
    }

    // =========================================================================
    // customer_util - admin's reply to a customer's "request changes" note.
    // Body params: customer name, quotation no, admin reply text.
    // =========================================================================
    public WhatsAppMessage sendFollowUpMessage(Lead lead, String quotationNumber, String adminReply) {
        List<Map<String, Object>> components = List.of(
                bodyComponent(lead.getCustomerName(), quotationNumber, adminReply)
        );
        String summary = "customer_util -> quotation " + quotationNumber + ": " + adminReply;
        return sendTemplateMessage(lead, WhatsAppMessageType.FOLLOW_UP,
                whatsAppProperties.getCustomerTemplateName(), components, summary);
    }

    // ---- Internal plumbing -------------------------------------------------

    private Map<String, Object> bodyComponent(String... textParams) {
        List<Map<String, Object>> parameters = new ArrayList<>();
        for (String p : textParams) {
            Map<String, Object> param = new HashMap<>();
            param.put("type", "text");
            param.put("text", p == null ? "" : p);
            parameters.add(param);
        }
        Map<String, Object> body = new HashMap<>();
        body.put("type", "body");
        body.put("parameters", parameters);
        return body;
    }

    /** HEADER component for a template approved with a DOCUMENT (PDF) header. */
    private Map<String, Object> headerDocumentComponent(String link, String fileName) {
        Map<String, Object> document = new HashMap<>();
        document.put("link", link);
        document.put("filename", fileName);

        Map<String, Object> param = new HashMap<>();
        param.put("type", "document");
        param.put("document", document);

        Map<String, Object> header = new HashMap<>();
        header.put("type", "header");
        header.put("parameters", List.of(param));
        return header;
    }

    private Map<String, Object> headerImageComponent(String link) {
        Map<String, Object> image = new HashMap<>();
        image.put("link", link);

        Map<String, Object> param = new HashMap<>();
        param.put("type", "image");
        param.put("image", image);

        Map<String, Object> header = new HashMap<>();
        header.put("type", "header");
        header.put("parameters", List.of(param));
        return header;
    }

    private WhatsAppMessage sendTemplateMessage(Lead lead, WhatsAppMessageType type, String templateName,
                                                 List<Map<String, Object>> components, String summary) {
        return sendTemplateMessage(lead, type, templateName, components, summary, null);
    }

    private WhatsAppMessage sendTemplateMessage(Lead lead, WhatsAppMessageType type, String templateName,
                                                 List<Map<String, Object>> components, String summary,
                                                 java.util.function.Consumer<WhatsAppMessage> customizer) {
        WhatsAppMessage message = WhatsAppMessage.builder()
                .lead(lead)
                .type(type)
                .toPhone(lead != null ? lead.getPhone() : null)
                .payloadSummary(summary.length() > 500 ? summary.substring(0, 500) : summary)
                .deliveryStatus(MessageDeliveryStatus.QUEUED)
                .build();
        if (customizer != null) customizer.accept(message);
        message = whatsAppMessageRepository.save(message);

        // Templates approved WITH a media sample have an image header, and Meta rejects the send if the
        // header parameter is missing. Add it here for every template listed in whatsapp.image-header-templates.
        if (whatsAppProperties.hasImageHeader(templateName)) {
            String headerImageUrl = whatsAppProperties.getHeaderImageUrl();
            if (headerImageUrl == null || headerImageUrl.isBlank()) {
                message.setDeliveryStatus(MessageDeliveryStatus.FAILED);
                message.setErrorMessage("Template " + templateName + " has an image header but WHATSAPP_HEADER_IMAGE_URL is not set.");
                whatsAppMessageRepository.save(message);
                throw new WhatsAppApiException("Template " + templateName + " has an image header, but "
                        + "WHATSAPP_HEADER_IMAGE_URL is not set. Set it to a public HTTPS JPG/PNG (max 5 MB).");
            }
            List<Map<String, Object>> withHeader = new ArrayList<>();
            withHeader.add(headerImageComponent(headerImageUrl));
            withHeader.addAll(components);
            components = withHeader;
        }

        Map<String, Object> template = new HashMap<>();
        template.put("name", templateName);
        template.put("language", Map.of("code", whatsAppProperties.getTemplateLanguage()));
        template.put("components", components);

        Map<String, Object> payload = new HashMap<>();
        payload.put("messaging_product", "whatsapp");
        payload.put("to", normalizePhone(lead.getPhone()));
        payload.put("type", "template");
        payload.put("template", template);

        return dispatch(message, payload);
    }

    // =========================================================================
    // Chat message to a customer who has NOT written in the last 24h (or never): WhatsApp rejects free text
    // there, so the typed message travels as a variable of the approved chat template (chat_util).
    // Body params: customer name, message. The bubble in the Chat screen shows the typed text itself.
    // =========================================================================
    public WhatsAppMessage sendChatTemplate(Lead lead, String greetName, String phone, String text, String sentBy) {
        // WhatsApp template variables cannot contain new lines, tabs or 4+ spaces in a row
        String flat = text.replaceAll("[\\r\\n\\t]+", " ").replaceAll(" {2,}", " ").strip();
        List<Map<String, Object>> components = List.of(bodyComponent(greetName, flat));
        String template = whatsAppProperties.getChatTemplateName();
        String summary = template + " -> " + greetName + ": " + flat;
        return sendTemplateMessage(lead, WhatsAppMessageType.FOLLOW_UP, template, components, summary, m -> {
            m.setDirection(MessageDirection.OUTBOUND);
            m.setToPhone(phone);
            m.setContactPhone(phone);
            m.setBody(text);
            m.setSentBy(sentBy);
            m.setAgentSeen(true);
        });
    }

    // =========================================================================
    // Chat reply - free text typed by an employee in the Chat screen.
    // WhatsApp only accepts a plain "text" message inside the 24h customer-service window
    // (the customer must have messaged us in the last 24h). ChatService checks that first;
    // if Meta still rejects it, the message is kept as FAILED so it shows in the chat with a Retry.
    // =========================================================================
    public WhatsAppMessage sendText(Lead lead, String toPhone, String text, String sentBy) {
        String to = (toPhone != null && !toPhone.isBlank()) ? toPhone : lead.getPhone();
        WhatsAppMessage message = WhatsAppMessage.builder()
                .lead(lead)
                // FOLLOW_UP is reused on purpose: adding a new enum value would need a database
                // check-constraint migration. Chat replies are told apart by direction + body.
                .type(WhatsAppMessageType.FOLLOW_UP)
                .direction(MessageDirection.OUTBOUND)
                .toPhone(to)
                .contactPhone(to)
                .body(text)
                .sentBy(sentBy)
                .agentSeen(true)
                .payloadSummary(text.length() > 500 ? text.substring(0, 500) : text)
                .deliveryStatus(MessageDeliveryStatus.QUEUED)
                .build();
        message = whatsAppMessageRepository.save(message);

        Map<String, Object> payload = new HashMap<>();
        payload.put("messaging_product", "whatsapp");
        payload.put("recipient_type", "individual");
        payload.put("to", normalizePhone(to));
        payload.put("type", "text");
        payload.put("text", Map.of("preview_url", true, "body", text));
        return dispatch(message, payload);
    }

    // =========================================================================
    // Chat attachment - a PDF / image / office file sent from the Chat screen.
    // Like free text, WhatsApp only accepts it inside the customer's 24h window (ChatService checks that).
    // The file is uploaded to Meta first (/media -> media id), then sent as an "image" or "document" message.
    // The bytes are also kept in chat_attachments so staff can open the file later.
    // =========================================================================
    public WhatsAppMessage sendMedia(Lead lead, String toPhone, byte[] data, String filename, String mime,
                                     String caption, String sentBy) {
        String to = (toPhone != null && !toPhone.isBlank()) ? toPhone : lead.getPhone();
        String mediaId = uploadMedia(data, filename, mime); // throws before anything is stored if Meta rejects the file
        boolean image = mime.startsWith("image/");
        boolean hasCaption = caption != null && !caption.isBlank();

        WhatsAppMessage message = WhatsAppMessage.builder()
                .lead(lead)
                .type(WhatsAppMessageType.FOLLOW_UP)
                .direction(MessageDirection.OUTBOUND)
                .toPhone(to)
                .contactPhone(to)
                .body(hasCaption ? caption.strip() : null)
                .sentBy(sentBy)
                .agentSeen(true)
                .attachmentName(filename)
                .attachmentMime(mime)
                .attachmentSize((long) data.length)
                .payloadSummary((image ? "[Photo] " : "[Document] ") + filename)
                .deliveryStatus(MessageDeliveryStatus.QUEUED)
                .build();
        message = whatsAppMessageRepository.save(message);
        chatAttachmentRepository.save(com.leadquote.entity.ChatAttachment.builder().messageId(message.getId()).data(data).build());

        Map<String, Object> media = new HashMap<>();
        media.put("id", mediaId);
        if (hasCaption) media.put("caption", caption.strip());
        if (!image) media.put("filename", filename);

        Map<String, Object> payload = new HashMap<>();
        payload.put("messaging_product", "whatsapp");
        payload.put("recipient_type", "individual");
        payload.put("to", normalizePhone(to));
        payload.put("type", image ? "image" : "document");
        payload.put(image ? "image" : "document", media);
        return dispatch(message, payload);
    }

    /** Uploads a file to WhatsApp and returns its media id (valid for 30 days, so a Retry can reuse it). */
    private String uploadMedia(byte[] data, String filename, String mime) {
        String url = whatsAppProperties.graphBaseUrl() + "/" + whatsAppProperties.getPhoneNumberId() + "/media";
        org.springframework.http.client.MultipartBodyBuilder form = new org.springframework.http.client.MultipartBodyBuilder();
        form.part("messaging_product", "whatsapp");
        form.part("type", mime);
        form.part("file", new org.springframework.core.io.ByteArrayResource(data))
                .filename(filename)
                .contentType(org.springframework.http.MediaType.parseMediaType(mime));
        try {
            Map<?, ?> response = webClient.post()
                    .uri(url)
                    .header("Authorization", "Bearer " + whatsAppProperties.getAccessToken())
                    .body(BodyInserters.fromMultipartData(form.build()))
                    .retrieve()
                    .bodyToMono(Map.class)
                    .block(java.time.Duration.ofSeconds(60));
            Object id = response == null ? null : response.get("id");
            if (id == null) throw new WhatsAppApiException("WhatsApp did not return a media id for the file.", null);
            return id.toString();
        } catch (WhatsAppApiException e) {
            throw e;
        } catch (Exception ex) {
            String reason = ex.getMessage();
            if (ex instanceof org.springframework.web.reactive.function.client.WebClientResponseException wre) {
                reason = ex.getMessage() + " | Meta response: " + wre.getResponseBodyAsString();
            }
            log.error("WhatsApp media upload failed: {}", reason);
            throw new WhatsAppApiException("Could not upload the file to WhatsApp: " + reason, ex);
        }
    }

    /**
     * Public base URL of this backend, in order of preference:
     * 1. company.api-base-url (env COMPANY_API_BASE_URL)
     * 2. RAILWAY_PUBLIC_DOMAIN (set automatically by Railway)
     * 3. the URL of the current HTTP request (the admin clicked "Send", so it hit the backend)
     * 4. company.public-base-url as a last resort
     */
    private String resolveBackendBaseUrl() {
        String configured = companyProperties.getApiBaseUrl();
        if (configured != null && !configured.isBlank()) return withScheme(configured);

        String railway = System.getenv("RAILWAY_PUBLIC_DOMAIN");
        if (railway != null && !railway.isBlank()) return withScheme(railway);

        try {
            String fromRequest = org.springframework.web.servlet.support.ServletUriComponentsBuilder
                    .fromCurrentContextPath().build().toUriString();
            if (fromRequest != null && !fromRequest.isBlank()) {
                if (fromRequest.startsWith("http://") && !fromRequest.contains("localhost")
                        && !fromRequest.contains("127.0.0.1")) {
                    fromRequest = "https://" + fromRequest.substring("http://".length());
                }
                return stripSlash(fromRequest);
            }
        } catch (IllegalStateException noRequest) {
            // Not inside an HTTP request (e.g. a scheduled job) - fall through.
        }
        return stripSlash(companyProperties.getPublicBaseUrl());
    }

    private static String withScheme(String url) {
        String u = url.trim();
        if (!u.startsWith("http://") && !u.startsWith("https://")) u = "https://" + u;
        return stripSlash(u);
    }

    private static String stripSlash(String url) {
        return url != null && url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
    }

    private WhatsAppMessage dispatch(WhatsAppMessage message, Map<String, Object> payload) {
        String url = whatsAppProperties.graphBaseUrl() + "/" + whatsAppProperties.getPhoneNumberId() + "/messages";
        message.setRequestPayload(toJson(payload));

        try {
            Map<String, Object> response = webClient.post()
                    .uri(url)
                    .header("Authorization", "Bearer " + whatsAppProperties.getAccessToken())
                    .header("Content-Type", "application/json")
                    .bodyValue(payload)
                    .retrieve()
                    .bodyToMono(Map.class)
                    .block();

            String wamid = extractMessageId(response);
            message.setProviderMessageId(wamid);
            message.setDeliveryStatus(MessageDeliveryStatus.SENT);
            message.setSentAt(java.time.LocalDateTime.now());
            return whatsAppMessageRepository.save(message);

        } catch (Exception ex) {
            String reason = ex.getMessage();
            if (ex instanceof org.springframework.web.reactive.function.client.WebClientResponseException wre) {
                // Include Meta's own JSON error (code / message / error_data) - it says exactly what is wrong.
                reason = ex.getMessage() + " | Meta response: " + wre.getResponseBodyAsString();
            }
            log.error("WhatsApp API call failed for {}: {}", message.getLead() != null ? message.getLead().getLeadCode() : message.getContactPhone(), reason);
            message.setDeliveryStatus(MessageDeliveryStatus.FAILED);
            message.setErrorMessage(reason.length() > 1000 ? reason.substring(0, 1000) : reason);
            message.setRetryCount(message.getRetryCount() + 1);
            whatsAppMessageRepository.save(message);
            // Intentionally do not rethrow further up as a fatal error for the whole workflow;
            // callers decide whether to surface this to the employee for manual retry.
            throw new WhatsAppApiException("Failed to send WhatsApp message: " + reason, ex);
        }
    }

    @SuppressWarnings("unchecked")
    private String extractMessageId(Map<String, Object> response) {
        try {
            List<Map<String, Object>> messages = (List<Map<String, Object>>) response.get("messages");
            return messages.get(0).get("id").toString();
        } catch (Exception e) {
            return null;
        }
    }

    private String normalizePhone(String phone) {
        return phone.replace("+", "").replace(" ", "").replace("-", "");
    }

    private String toJson(Map<String, Object> payload) {
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (Exception e) {
            log.warn("Could not serialize WhatsApp request payload for storage: {}", e.getMessage());
            return null;
        }
    }

    /**
     * Allows an employee to retry a previously failed message from the dashboard. Replays the
     * EXACT request payload that was stored at send time (template name, language, header/body
     * components, ...) rather than rebuilding it, since a template message can't be reconstructed
     * from payloadSummary alone.
     */
    @SuppressWarnings("unchecked")
    public WhatsAppMessage retry(WhatsAppMessage failedMessage) {
        if (failedMessage.getRequestPayload() == null) {
            throw new WhatsAppApiException(
                    "Cannot retry message " + failedMessage.getId() + ": no stored request payload "
                            + "(it predates template support - resend it from the source screen instead).", null);
        }
        Map<String, Object> payload;
        try {
            payload = objectMapper.readValue(failedMessage.getRequestPayload(), new TypeReference<Map<String, Object>>() {});
        } catch (Exception e) {
            throw new WhatsAppApiException("Cannot retry message " + failedMessage.getId()
                    + ": stored request payload is not valid JSON: " + e.getMessage(), e);
        }
        ensureImageHeader(payload);
        return dispatch(failedMessage, payload);
    }

    /**
     * A failed message stored before the image header was configured for its template (e.g. remind_util, error
     * 132012 "expected IMAGE, received UNKNOWN") has no header component. Add it here so Retry actually works.
     */
    @SuppressWarnings("unchecked")
    private void ensureImageHeader(Map<String, Object> payload) {
        Object t = payload.get("template");
        if (!(t instanceof Map)) return;
        Map<String, Object> template = (Map<String, Object>) t;
        Object name = template.get("name");
        if (name == null || !whatsAppProperties.hasImageHeader(name.toString())) return;

        List<Map<String, Object>> components = new ArrayList<>();
        Object existing = template.get("components");
        if (existing instanceof List<?> list) {
            for (Object c : list) {
                if (c instanceof Map) {
                    Map<String, Object> component = (Map<String, Object>) c;
                    if ("header".equals(component.get("type"))) return; // already has its header
                    components.add(component);
                }
            }
        }
        String headerImageUrl = whatsAppProperties.getHeaderImageUrl();
        if (headerImageUrl == null || headerImageUrl.isBlank()) {
            throw new WhatsAppApiException("Template " + name + " has an image header, but WHATSAPP_HEADER_IMAGE_URL "
                    + "is not set. Set it to a public HTTPS JPG/PNG (max 5 MB).", null);
        }
        components.add(0, headerImageComponent(headerImageUrl));
        template.put("components", components);
    }
}
