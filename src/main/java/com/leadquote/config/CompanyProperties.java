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
}
