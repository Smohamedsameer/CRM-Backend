package com.leadquote.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "company")
@Getter
@Setter
public class CompanyProperties {
    private String name;
    private String phone;
    private String email;
    private String address;
    private String gstNumber;
    private String logoPath;
    /** Public base URL of the deployed frontend, e.g. https://yourdomain.com */
    private String publicBaseUrl;
    /**
     * Public HTTPS URL of the deployed BACKEND (Spring Boot), e.g. https://crm-backend-production-7244.up.railway.app.
     * Used for links that must be served by the backend itself - most importantly the quotation PDF
     * that WhatsApp downloads. If this pointed at the frontend, the SPA rewrite would return
     * index.html and WhatsApp would deliver the file as "SE-xxxx-2026.pdf.html".
     */
    private String apiBaseUrl;
}
