package com.leadquote.service;

import com.leadquote.config.CompanyProperties;
import com.leadquote.config.WhatsAppProperties;
import com.leadquote.entity.*;
import com.leadquote.exception.WhatsAppApiException;
import com.leadquote.repository.WhatsAppMessageRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Wraps the official WhatsApp Business (Cloud) API. Never uses browser automation.
 * Credentials always come from environment-backed configuration (see WhatsAppProperties),
 * never hard-coded, never sent to the frontend.
 *
 * If a call fails, we persist the failure on the WhatsAppMessage row instead of throwing away
 * the lead/quotation, so an employee can retry manually from the dashboard.
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

    public WhatsAppMessage sendWelcomeMessage(Lead lead, String enquiryFormLink) {
        String body = String.format(
                "Hello %s \uD83D\uDC4B%n%nThank you for your enquiry with %s.%n%n" +
                "To understand your requirements and prepare an accurate quotation, please complete our short enquiry form.%n%n" +
                "Please click the link below:%n%n%s%n%nThank you.",
                lead.getCustomerName(), settingsService.getCompanyName(), enquiryFormLink);

        return sendTextMessage(lead, WhatsAppMessageType.WELCOME, body);
    }

    public WhatsAppMessage sendEnquiryForm(Lead lead, String enquiryFormLink) {
        String body = "Here is your enquiry form link: " + enquiryFormLink;
        return sendTextMessage(lead, WhatsAppMessageType.ENQUIRY_FORM, body);
    }

    public WhatsAppMessage sendQuotation(Lead lead, Quotation quotation, String quotationPageLink) {
        String textBody = String.format(
                "Hello %s,%n%nThank you for providing your requirements.%n%nYour quotation is ready.%n%n" +
                "Quotation No: %s%nTotal Amount: %s%n%n" +
                "Please review the attached quotation.%n%n" +
                "You can accept the quotation or request changes here: %s",
                lead.getCustomerName(), quotation.getQuotationNumber(), quotation.getTotalAmount(), quotationPageLink);

        WhatsAppMessage textMsg = sendTextMessage(lead, WhatsAppMessageType.QUOTATION, textBody);

        // Send the PDF document as a follow-up media message, if it has been generated & is publicly retrievable.
        if (quotation.getPdfPath() != null) {
            sendDocumentMessage(lead, quotation, quotation.getPdfPath());
        }

        return textMsg;
    }

    public WhatsAppMessage sendAcceptanceConfirmation(Lead lead) {
        String body = "Thank you for accepting our quotation! \uD83C\uDF89\n\nOur team will contact you shortly to confirm your order.";
        return sendTextMessage(lead, WhatsAppMessageType.ACCEPTANCE_CONFIRMATION, body);
    }

    public WhatsAppMessage sendContactDetails(Lead lead) {
        String body = String.format("Phone: %s%nEmail: %s", settingsService.getCompanyPhone(), settingsService.getCompanyEmail());
        return sendTextMessage(lead, WhatsAppMessageType.CONTACT_DETAILS, body);
    }

    public WhatsAppMessage sendFollowUpMessage(Lead lead, String customText) {
        return sendTextMessage(lead, WhatsAppMessageType.FOLLOW_UP, customText);
    }

    // ---- Internal plumbing -------------------------------------------------

    private WhatsAppMessage sendTextMessage(Lead lead, WhatsAppMessageType type, String body) {
        WhatsAppMessage message = WhatsAppMessage.builder()
                .lead(lead)
                .type(type)
                .toPhone(lead.getPhone())
                .payloadSummary(body.length() > 500 ? body.substring(0, 500) : body)
                .deliveryStatus(MessageDeliveryStatus.QUEUED)
                .build();
        message = whatsAppMessageRepository.save(message);

        Map<String, Object> payload = new HashMap<>();
        payload.put("messaging_product", "whatsapp");
        payload.put("to", normalizePhone(lead.getPhone()));
        payload.put("type", "text");
        payload.put("text", Map.of("preview_url", true, "body", body));

        return dispatch(message, payload);
    }

    private void sendDocumentMessage(Lead lead, Quotation quotation, String pdfPath) {
        // The PDF MUST be fetched from the backend. The frontend host (Vercel/Netlify) rewrites every
        // unknown path to index.html, so WhatsApp would download HTML and save it as "xxx.pdf.html".
        String publicPdfUrl = resolveBackendBaseUrl() + "/api/public/quotations/" + quotation.getSecureToken() + "/pdf";
        String fileName = quotation.getQuotationNumber().replace("/", "-") + ".pdf";

        WhatsAppMessage message = WhatsAppMessage.builder()
                .lead(lead)
                .type(WhatsAppMessageType.QUOTATION)
                .toPhone(lead.getPhone())
                .payloadSummary("Quotation PDF: " + quotation.getQuotationNumber())
                .deliveryStatus(MessageDeliveryStatus.QUEUED)
                .build();
        message = whatsAppMessageRepository.save(message);

        Map<String, Object> payload = new HashMap<>();
        payload.put("messaging_product", "whatsapp");
        payload.put("to", normalizePhone(lead.getPhone()));
        payload.put("type", "document");
        payload.put("document", Map.of(
                "link", publicPdfUrl,
                "filename", fileName,
                "caption", "Quotation " + quotation.getQuotationNumber()
        ));

        log.info("Sending quotation PDF {} to WhatsApp from {}", fileName, publicPdfUrl);
        dispatch(message, payload);
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
            log.error("WhatsApp API call failed for lead {}: {}", message.getLead().getLeadCode(), ex.getMessage());
            message.setDeliveryStatus(MessageDeliveryStatus.FAILED);
            message.setErrorMessage(ex.getMessage());
            message.setRetryCount(message.getRetryCount() + 1);
            whatsAppMessageRepository.save(message);
            // Intentionally do not rethrow further up as a fatal error for the whole workflow;
            // callers decide whether to surface this to the employee for manual retry.
            throw new WhatsAppApiException("Failed to send WhatsApp message: " + ex.getMessage(), ex);
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

    /** Allows an employee to retry a previously failed message from the dashboard. */
    public WhatsAppMessage retry(WhatsAppMessage failedMessage) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("messaging_product", "whatsapp");
        payload.put("to", normalizePhone(failedMessage.getToPhone()));
        payload.put("type", "text");
        payload.put("text", Map.of("preview_url", true, "body", failedMessage.getPayloadSummary()));
        return dispatch(failedMessage, payload);
    }
}
