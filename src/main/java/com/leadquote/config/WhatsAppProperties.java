package com.leadquote.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "whatsapp")
@Getter
@Setter
public class WhatsAppProperties {
    /** WHATSAPP_ACCESS_TOKEN */
    private String accessToken;
    /** WHATSAPP_PHONE_NUMBER_ID */
    private String phoneNumberId;
    /** WHATSAPP_BUSINESS_ACCOUNT_ID */
    private String businessAccountId;
    /** WHATSAPP_API_VERSION, e.g. v20.0 */
    private String apiVersion = "v20.0";
    /** Shared secret used to validate incoming webhook verify requests (WHATSAPP_WEBHOOK_VERIFY_TOKEN) */
    private String webhookVerifyToken;

    // ---------------------------------------------------------------------
    // Approved Meta "utility" templates. Every business-initiated message
    // (i.e. anything WE start, not a reply inside an open 24h customer
    // session) MUST use one of these - Meta rejects free-text "text" type
    // messages outside that window. Names below must match EXACTLY what
    // was approved in WhatsApp Manager, including case.
    // ---------------------------------------------------------------------

    /** Sent the moment a new lead comes in: welcome + link to the enquiry form. (WHATSAPP_CONFIRM_TEMPLATE_NAME) */
    private String confirmTemplateName = "confirm_util";
    /** Sent once the customer has filled the enquiry form and the quotation is ready. (WHATSAPP_ENQUIRY_TEMPLATE_NAME) */
    private String enquiryTemplateName = "enquiry_util";
    /**
     * Public HTTPS URL of the image used in the IMAGE header of the templates below (WHATSAPP_HEADER_IMAGE_URL).
     * Must be JPG/PNG, under 5 MB, reachable without login. Falls back to the older
     * WHATSAPP_ENQUIRY_HEADER_IMAGE_URL if only that one is set.
     */
    private String headerImageUrl;
    /** Legacy name, kept so an existing Railway variable keeps working. */
    public void setEnquiryHeaderImageUrl(String url) {
        if (headerImageUrl == null || headerImageUrl.isBlank()) headerImageUrl = url;
    }
    /**
     * Comma-separated template names that were approved with an image header/media sample
     * (WHATSAPP_IMAGE_HEADER_TEMPLATES). Remove a name if that template has NO header - sending a
     * header to a template without one fails with a parameter-mismatch error.
     */
    private String imageHeaderTemplates = "confirm_util,order_util,customer_util,remind_util";

    /**
     * remind_util was approved (as a Marketing template) WITH an image header, so it always needs the header
     * image - even if WHATSAPP_IMAGE_HEADER_TEMPLATES on the server was set before it existed and does not list it.
     * Set WHATSAPP_REMINDER_HAS_IMAGE_HEADER=false only if the template is ever re-created without a header.
     */
    private boolean reminderHasImageHeader = true;

    public boolean hasImageHeader(String templateName) {
        if (templateName == null) return false;
        if (reminderHasImageHeader && templateName.equalsIgnoreCase(reminderTemplateName)) return true;
        if (imageHeaderTemplates == null) return false;
        for (String t : imageHeaderTemplates.split(",")) {
            if (t.trim().equalsIgnoreCase(templateName)) return true;
        }
        return false;
    }
    /**
     * Quotation-ready template whose HEADER is a PDF document (WHATSAPP_PDF_TEMPLATE_NAME). The customer sees
     * the quotation PDF as a tappable card at the top of the message. Leave blank to keep using enquiry_util
     * (image header + link in the body).
     */
    private String pdfTemplateName = "pdf_util";
    /** Reminder sent if the customer has not filled the enquiry form the next day (WHATSAPP_REMINDER_TEMPLATE_NAME). */
    private String reminderTemplateName = "remind_util";
    /** Send the reminder this many hours after the lead was created (WHATSAPP_REMINDER_DELAY_HOURS). */
    private int reminderDelayHours = 24;
    /** Do not remind leads older than this many hours - stops a first deploy from messaging old leads (WHATSAPP_REMINDER_MAX_AGE_HOURS). */
    private int reminderMaxAgeHours = 72;
    /** Sent once the customer accepts a quotation. (WHATSAPP_ORDER_TEMPLATE_NAME) */
    private String orderTemplateName = "order_util";
    /** Sent when an admin replies to a customer's "request changes" note. (WHATSAPP_CUSTOMER_TEMPLATE_NAME) */
    private String customerTemplateName = "customer_util";
    /**
     * Template used when an employee types a message in the Chat screen but the customer has NOT written in the
     * last 24 hours (WHATSAPP_CHAT_TEMPLATE_NAME). WhatsApp does not allow free text in that case, so the typed text is
     * sent as a variable of this approved template. It needs exactly 2 body variables:
     * {{1}} = customer name, {{2}} = the message, e.g. "Hello {{1}}, this is Senela International Ventures. {{2}}".
     */
    private String chatTemplateName = "chat_util";

    /**
     * Language code the templates were approved under in WhatsApp Manager (WHATSAPP_TEMPLATE_LANGUAGE).
     * This must be the exact code Meta shows next to the template, e.g. "en", "en_US" or "en_GB" -
     * "ENG" is not a valid Graph API code by itself. Sending the wrong code is the #1 reason a
     * template send fails with error 132001 ("template name does not exist in the translation").
     */
    private String templateLanguage = "en_US";

    public String graphBaseUrl() {
        return "https://graph.facebook.com/" + apiVersion;
    }
}
