package com.leadquote.service;

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

import java.io.File;
import java.util.ArrayList;
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
    private final WhatsAppMessageRepository whatsAppMessageRepository;
    private final ObjectMapper objectMapper;

    /**
     * Sends the welcome message as plain free text. Note: WhatsApp's own policy is that the
     * first-ever message to a customer who hasn't messaged you first is supposed to use an
     * approved Message Template, not free text - sending free text here can be rejected or
     * silently undelivered for a brand-new contact. This reverts to text-only per request;
     * see sendWelcomeMessageViaTemplate() below (currently unused) if you want to switch back.
     */
    public WhatsAppMessage sendWelcomeMessage(Lead lead, String enquiryFormLink) {
        String body = String.format(
                "Hello %s \uD83D\uDC4B%n%nThank you for your enquiry with %s.%n%n" +
                "To understand your requirements and prepare an accurate quotation, please complete our short enquiry form.%n%n" +
                "Please click the link below:%n%n%s%n%nThank you.",
                lead.getCustomerName(), companyProperties.getName(), enquiryFormLink);

        Map<String, Object> payload = new HashMap<>();
        payload.put("messaging_product", "whatsapp");
        payload.put("to", normalizePhone(lead.getPhone()));
        payload.put("type", "text");
        payload.put("text", Map.of("preview_url", true, "body", body));

        WhatsAppMessage message = WhatsAppMessage.builder()
                .lead(lead)
                .type(WhatsAppMessageType.WELCOME)
                .toPhone(lead.getPhone())
                .payloadSummary(body.length() > 500 ? body.substring(0, 500) : body)
                .deliveryStatus(MessageDeliveryStatus.QUEUED)
                .build();
        message = whatsAppMessageRepository.save(message);

        return dispatch(message, payload);
    }

    /**
     * Template-based version, kept here unused in case the welcome_enquiry template gets
     * approved later and you want to switch back - just rename this to sendWelcomeMessage and
     * rename the plain-text one above to something else.
     */
    public WhatsAppMessage sendWelcomeMessageViaTemplate(Lead lead, String enquiryFormLink) {
        String readableSummary = String.format(
                "Hello %s \uD83D\uDC4B%n%nThank you for your enquiry with %s.%n%n" +
                "To understand your requirements and prepare an accurate quotation, please complete our short enquiry form.%n%n" +
                "Please click the link below:%n%n%s%n%nThank you.",
                lead.getCustomerName(), companyProperties.getName(), enquiryFormLink);

        Map<String, Object> payload = new HashMap<>();
        payload.put("messaging_product", "whatsapp");
        payload.put("to", normalizePhone(lead.getPhone()));
        payload.put("type", "template");

        List<Map<String, Object>> parameters = new ArrayList<>();
        parameters.add(Map.of("type", "text", "text", lead.getCustomerName()));
        parameters.add(Map.of("type", "text", "text", companyProperties.getName()));
        parameters.add(Map.of("type", "text", "text", enquiryFormLink));

        Map<String, Object> template = new HashMap<>();
        template.put("name", whatsAppProperties.getWelcomeTemplateName());
        template.put("language", Map.of("code", whatsAppProperties.getTemplateLanguage()));
        template.put("components", List.of(Map.of("type", "body", "parameters", parameters)));
        payload.put("template", template);

        WhatsAppMessage message = WhatsAppMessage.builder()
                .lead(lead)
                .type(WhatsAppMessageType.WELCOME)
                .toPhone(lead.getPhone())
                .payloadSummary(readableSummary.length() > 500 ? readableSummary.substring(0, 500) : readableSummary)
                .deliveryStatus(MessageDeliveryStatus.QUEUED)
                .build();
        message = whatsAppMessageRepository.save(message);

        return dispatch(message, payload);
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
        String body = String.format("Phone: %s%nEmail: %s", companyProperties.getPhone(), companyProperties.getEmail());
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
        // In production, host the PDF at a stable HTTPS URL (e.g. an object storage bucket or
        // GET /api/quotations/{id}/pdf behind a signed link) and reference it here as `link`.
        String publicPdfUrl = companyProperties.getPublicBaseUrl() + "/api/public/quotations/" + quotation.getSecureToken() + "/pdf";

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
                "filename", new File(pdfPath).getName()
        ));

        dispatch(message, payload);
    }

    private WhatsAppMessage dispatch(WhatsAppMessage message, Map<String, Object> payload) {
        String url = whatsAppProperties.graphBaseUrl() + "/" + whatsAppProperties.getPhoneNumberId() + "/messages";
        message.setRequestPayload(toJsonSafely(payload));
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
            String reason = extractErrorReason(ex);
            log.error("WhatsApp API call failed for lead {}: {}", message.getLead().getLeadCode(), reason);
            message.setDeliveryStatus(MessageDeliveryStatus.FAILED);
            message.setErrorMessage(reason);
            message.setRetryCount(message.getRetryCount() + 1);
            whatsAppMessageRepository.save(message);
            // Intentionally do not rethrow further up as a fatal error for the whole workflow;
            // callers decide whether to surface this to the employee for manual retry.
            throw new WhatsAppApiException("Failed to send WhatsApp message: " + reason, ex);
        }
    }

    /**
     * WebClientResponseException#getMessage() only gives a generic "404 Not Found from POST ..."
     * string by default - the actually useful part (why Meta rejected it, e.g. "template is
     * paused"/"not approved") is in the response body, which this pulls out when present.
     */
    private String extractErrorReason(Exception ex) {
        if (ex instanceof org.springframework.web.reactive.function.client.WebClientResponseException wcre) {
            String bodyJson = wcre.getResponseBodyAsString();
            try {
                Map<String, Object> body = objectMapper.readValue(bodyJson, Map.class);
                Object error = body.get("error");
                if (error instanceof Map<?, ?> errorMap) {
                    Object msg = errorMap.get("error_user_msg") != null ? errorMap.get("error_user_msg") : errorMap.get("message");
                    if (msg != null) return msg.toString();
                }
            } catch (Exception parseFailure) {
                if (bodyJson != null && !bodyJson.isBlank()) return bodyJson;
            }
        }
        return ex.getMessage();
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

    private String toJsonSafely(Map<String, Object> payload) {
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (Exception e) {
            return null;
        }
    }

    /** Allows an employee to retry a previously failed message from the dashboard. Replays the
     * exact same payload that was originally sent (template, text, or document) rather than
     * rebuilding it as plain text, which would corrupt template/document retries. */
    @SuppressWarnings("unchecked")
    public WhatsAppMessage retry(WhatsAppMessage failedMessage) {
        Map<String, Object> payload;
        if (failedMessage.getRequestPayload() != null) {
            try {
                payload = objectMapper.readValue(failedMessage.getRequestPayload(), Map.class);
            } catch (Exception e) {
                payload = rebuildLegacyTextPayload(failedMessage);
            }
        } else {
            // Older rows saved before requestPayload existed - fall back to a plain text resend.
            payload = rebuildLegacyTextPayload(failedMessage);
        }
        return dispatch(failedMessage, payload);
    }

    private Map<String, Object> rebuildLegacyTextPayload(WhatsAppMessage failedMessage) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("messaging_product", "whatsapp");
        payload.put("to", normalizePhone(failedMessage.getToPhone()));
        payload.put("type", "text");
        payload.put("text", Map.of("preview_url", true, "body", failedMessage.getPayloadSummary()));
        return payload;
    }
}
