package com.leadquote.dto;

import lombok.Data;

/** One row of the Google Sheet, sent by the Apps Script. Every field is optional except the phone. */
@Data
public class SheetLeadRequest {
    private String name;
    private String phone;
    private String email;
    private String company;
    private String address;
    private String projectName;
}
