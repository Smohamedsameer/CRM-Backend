package com.leadquote.controller;

import com.leadquote.entity.LeadStatus;
import com.leadquote.repository.LeadRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/dashboard")
@RequiredArgsConstructor
public class DashboardController {

    private final LeadRepository leadRepository;

    @GetMapping("/summary")
    public Map<String, Long> summary() {
        return Map.of(
                "totalLeads", leadRepository.count(),
                "newLeads", leadRepository.countByStatus(LeadStatus.NEW),
                "formsPending", leadRepository.countByStatus(LeadStatus.FORM_SENT)
                        + leadRepository.countByStatus(LeadStatus.WELCOME_SENT),
                "formsSubmitted", leadRepository.countByStatus(LeadStatus.FORM_SUBMITTED),
                "quotationsSent", leadRepository.countByStatus(LeadStatus.QUOTATION_SENT),
                "quotationsAccepted", leadRepository.countByStatus(LeadStatus.ACCEPTED),
                "changesRequested", leadRepository.countByStatus(LeadStatus.CHANGES_REQUESTED),
                "ordersConfirmed", leadRepository.countByStatus(LeadStatus.ORDER_CONFIRMED)
        );
    }

    /** Data for the charts under the dashboard's lead table. */
    @GetMapping("/analytics")
    public Map<String, Object> analytics() {
        long total = leadRepository.count();

        List<Map<String, Object>> byStatus = new ArrayList<>();
        for (Object[] row : leadRepository.countGroupedByStatus()) {
            byStatus.add(Map.of("key", String.valueOf(row[0]), "count", ((Number) row[1]).longValue()));
        }

        List<Map<String, Object>> bySource = new ArrayList<>();
        for (Object[] row : leadRepository.countGroupedBySource()) {
            bySource.add(Map.of("key", String.valueOf(row[0]), "count", ((Number) row[1]).longValue()));
        }

        // New leads per month for the last 6 months (including this one), oldest first.
        YearMonth thisMonth = YearMonth.now();
        LinkedHashMap<YearMonth, Long> perMonth = new LinkedHashMap<>();
        for (int i = 5; i >= 0; i--) perMonth.put(thisMonth.minusMonths(i), 0L);
        LocalDateTime from = thisMonth.minusMonths(5).atDay(1).atStartOfDay();
        for (LocalDateTime created : leadRepository.findCreatedAtSince(from)) {
            if (created == null) continue;
            perMonth.computeIfPresent(YearMonth.from(created), (k, v) -> v + 1);
        }
        DateTimeFormatter fmt = DateTimeFormatter.ofPattern("MMM yyyy");
        List<Map<String, Object>> monthly = new ArrayList<>();
        perMonth.forEach((ym, count) -> monthly.add(Map.of("month", ym.format(fmt), "count", count)));

        long won = leadRepository.countByStatus(LeadStatus.ACCEPTED)
                + leadRepository.countByStatus(LeadStatus.ORDER_CONFIRMED);
        long lost = leadRepository.countByStatus(LeadStatus.REJECTED)
                + leadRepository.countByStatus(LeadStatus.CLOSED);
        double conversionRate = total == 0 ? 0 : Math.round(won * 1000.0 / total) / 10.0;

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("totalLeads", total);
        out.put("won", won);
        out.put("lost", lost);
        out.put("conversionRate", conversionRate);
        out.put("byStatus", byStatus);
        out.put("bySource", bySource);
        out.put("monthly", monthly);
        out.put("generatedOn", LocalDate.now().toString());
        return out;
    }
}
