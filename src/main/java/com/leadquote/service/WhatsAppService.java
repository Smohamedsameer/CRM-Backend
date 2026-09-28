package com.leadquote.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.leadquote.config.CompanyProperties;
import com.leadquote.config.WhatsAppProperties;
import com.leadquote.entity.*;
import com.leadquote.exception.WhatsAppApiException;
import com.leadquote.repository.WhatsAppMessageRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Wraps the official WhatsApp Business (Cloud) API. Never uses browser automation.
 * Credentials always come from environment-backed configuration (see WhatsAppProperties),
 * never hard-coded, never sent to the frontend.
 *
 * Every business-initiated message uses one of the four Meta-APPROVED "utility" templates
 * (confirm_util, enquiry_util, order_util, customer_util - see WhatsAppProperties). WhatsApp
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
    // enquiry_util - quotation ready. Body params: customer name, quotation no, amount,
    // quotation/pdf link. The template was approved with an IMAGE header, so the header
    // component carries an image link (whatsapp.enquiry-header-image-url). An image header
    // cannot carry the PDF - the customer opens/downloads the PDF from the link in the body
    // ({{4}} = the public quotation page/PDF link).
    // =========================================================================
    public WhatsAppMessage sendQuotation(Lead lead, Quotation quotation, String quotationPageLink) {
        String headerImageUrl = whatsAppProperties.getEnquiryHeaderImageUrl();
        if (headerImageUrl == null || headerImageUrl.isBlank()) {
            throw new WhatsAppApiException("enquiry_util has an image header, but WHATSAPP_ENQUIRY_HEADER_IMAGE_URL "
                    + "is not set. Set it to a public HTTPS image URL (JPG/PNG, max 5 MB).");
        }

        List<Map<String, Object>> components = new ArrayList<>();
        components.add(headerImageComponent(headerImageUrl));
        components.add(bodyComponent(
                lead.getCustomerName(),
                quotation.getQuotationNumber(),
                String.valueOf(quotation.getTotalAmount()),
                quotationPageLink
        ));

        String summary = "enquiry_util -> quotation " + quotation.getQuotationNumber()
                + ", total " + quotation.getTotalAmount();
        return sendTemplateMessage(lead, WhatsAppMessageType.QUOTATION,
                whatsAppProperties.getEnquiryTemplateName(), components, summary);
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
        WhatsAppMessage message = WhatsAppMessage.builder()
                .lead(lead)
                .type(type)
                .toPhone(lead.getPhone())
                .payloadSummary(summary.length() > 500 ? summary.substring(0, 500) : summary)
                .deliveryStatus(MessageDeliveryStatus.QUEUED)
                .build();
        message = whatsAppMessageRepository.save(message);

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
            log.error("WhatsApp API call failed for lead {}: {}", message.getLead().getLeadCode(), reason);
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
        return dispatch(failedMessage, payload);
    }
}
