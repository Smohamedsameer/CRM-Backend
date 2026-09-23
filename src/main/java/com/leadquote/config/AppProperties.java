package com.leadquote.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "app")
@Getter
@Setter
public class AppProperties {
    /** Directory where generated quotation PDFs are stored. */
    private String pdfStorageDir = "./storage/quotations";
    /** Hours an enquiry-form link stays valid. */
    private int enquiryTokenValidityHours = 168; // 7 days
    /** Days a quotation acceptance link / quotation itself stays valid. */
    private int quotationValidityDays = 15;
}
