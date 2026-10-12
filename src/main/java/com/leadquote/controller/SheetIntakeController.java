package com.leadquote.controller;

import com.leadquote.dto.LeadCreateRequest;
import com.leadquote.dto.SheetLeadRequest;
import com.leadquote.entity.Lead;
import com.leadquote.entity.LeadSource;
import com.leadquote.entity.LeadStatus;
import com.leadquote.repository.LeadRepository;
import com.leadquote.service.LeadService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Receives Meta-ad leads from the Google Sheet (via the Apps Script) and runs them through the
 * normal lead workflow: create lead -> WhatsApp welcome message with the enquiry form link.
 */
@RestController
@RequestMapping("/api/intake")
@RequiredArgsConstructor
@Slf4j
public class SheetIntakeController {

    /** A phone that already became a lead within this many days is treated as a duplicate. */
    private static final int DUPLICATE_WINDOW_DAYS = 7;

    private final LeadService leadService;
    private final LeadRepository leadRepository;

    @Value("${intake.api-key:}")
    private String intakeApiKey;

    @PostMapping("/sheet-lead")
    public ResponseEntity<Map<String, Object>> receive(
            @RequestHeader(value = "X-Intake-Key", required = false) String key,
            @RequestBody SheetLeadRequest req) {

        if (!keyMatches(key)) {
            return ResponseEntity.status(401).body(Map.of("status", "UNAUTHORIZED"));
        }

        String phone = normalizePhone(req.getPhone());
        if (phone == null) {
            return ResponseEntity.unprocessableEntity().body(Map.of("status", "INVALID_PHONE"));
        }

        // Same person already became a lead recently (form submitted twice, or the script retried): do nothing.
        Optional<Lead> existing = leadRepository.findFirstByPhoneInAndCreatedAtAfterOrderByCreatedAtDesc(
                List.of(phone, "+" + phone), LocalDateTime.now().minusDays(DUPLICATE_WINDOW_DAYS));
        if (existing.isPresent()) {
            return ResponseEntity.ok(Map.of("status", "DUPLICATE", "leadCode", existing.get().getLeadCode()));
        }

        LeadCreateRequest lead = new LeadCreateRequest();
        lead.setCustomerName(blankToNull(req.getName()) != null ? req.getName().trim() : "Meta Lead");
        lead.setPhone(phone);
        lead.setEmail(validEmailOrNull(req.getEmail()));
        lead.setCompanyName(blankToNull(req.getCompany()));
        lead.setAddress(blankToNull(req.getAddress()));
        lead.setProjectName(blankToNull(req.getProjectName()));
        lead.setSource(LeadSource.META_AD);

        Lead saved = leadService.createLead(lead, "google-sheet");

        // createLead never throws on a WhatsApp failure: the lead is kept and an employee can resend from the dashboard.
        String status = saved.getStatus() == LeadStatus.WELCOME_SENT ? "SENT" : "CREATED_WELCOME_FAILED";
        log.info("Sheet lead {} -> {}", saved.getLeadCode(), status);
        return ResponseEntity.ok(Map.of("status", status, "leadCode", saved.getLeadCode()));
    }

    private boolean keyMatches(String key) {
        if (intakeApiKey == null || intakeApiKey.isBlank() || key == null) return false;
        return MessageDigest.isEqual(key.getBytes(StandardCharsets.UTF_8), intakeApiKey.getBytes(StandardCharsets.UTF_8));
    }

    /**
     * Meta/Sheets phone values look like "p:+919876543210", "98765 43210" or 9876543210.
     * Returns digits with country code (WhatsApp needs it), or null if it can't be a valid number.
     * A bare 10-digit number is assumed to be Indian (+91).
     */
    static String normalizePhone(String raw) {
        if (raw == null) return null;
        String d = raw.replaceAll("[^0-9]", "");
        if (d.length() == 10) d = "91" + d;
        else if (d.length() == 11 && d.startsWith("0")) d = "91" + d.substring(1);
        // Meta sometimes puts +1 in front of a 10-digit Indian mobile (e.g. +19894057810). Your ads target
        // Tamil Nadu, so an 11-digit "1" + Indian-mobile pattern (starts 6-9) is treated as India, not the USA.
        else if (d.length() == 11 && d.startsWith("1") && d.substring(1).matches("^[6-9][0-9]{9}$")) d = "91" + d.substring(1);
        return d.matches("^[0-9]{11,15}$") ? d : null;
    }

    private static String blankToNull(String s) {
        return (s == null || s.isBlank()) ? null : s.trim();
    }

    private static String validEmailOrNull(String s) {
        String v = blankToNull(s);
        return (v != null && v.matches("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$")) ? v : null;
    }
}
