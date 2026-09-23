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

    public String graphBaseUrl() {
        return "https://graph.facebook.com/" + apiVersion;
    }
}
