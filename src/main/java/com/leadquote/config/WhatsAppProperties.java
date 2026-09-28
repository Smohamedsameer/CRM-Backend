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
     * Public HTTPS URL of the image used in enquiry_util's IMAGE header (WHATSAPP_ENQUIRY_HEADER_IMAGE_URL).
     * Must be JPG/PNG, under 5 MB, and reachable without login (e.g. your logo hosted on the frontend
     * or backend). Ideally the same image you attached when the template was approved.
     */
    private String enquiryHeaderImageUrl;
    /** Sent once the customer accepts a quotation. (WHATSAPP_ORDER_TEMPLATE_NAME) */
    private String orderTemplateName = "order_util";
    /** Sent when an admin replies to a customer's "request changes" note. (WHATSAPP_CUSTOMER_TEMPLATE_NAME) */
    private String customerTemplateName = "customer_util";

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
