package com.leadquote.service;

import com.leadquote.config.AppProperties;
import com.leadquote.config.CompanyProperties;
import com.leadquote.entity.Quotation;
import com.leadquote.entity.QuotationItem;
import com.leadquote.exception.PdfGenerationException;
import com.lowagie.text.*;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfWriter;
import com.lowagie.text.pdf.draw.LineSeparator;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.awt.Color;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.format.DateTimeFormatter;

@Service
@RequiredArgsConstructor
public class PdfService {

    private final CompanyProperties companyProperties;
    private final AppProperties appProperties;
    private final SettingsService settingsService;

    private static final Font TITLE_FONT = new Font(Font.HELVETICA, 20, Font.BOLD);
    private static final Font HEADER_FONT = new Font(Font.HELVETICA, 11, Font.BOLD);
    private static final Font NORMAL_FONT = new Font(Font.HELVETICA, 10, Font.NORMAL);
    private static final Font SMALL_FONT = new Font(Font.HELVETICA, 8, Font.ITALIC, Color.GRAY);

    public String generateQuotationPdf(Quotation quotation) {
        try {
            File dir = new File(appProperties.getPdfStorageDir());
            if (!dir.exists()) dir.mkdirs();

            String fileName = quotation.getQuotationNumber().replace("/", "-") + ".pdf";
            File file = new File(dir, fileName);

            Document document = new Document(PageSize.A4, 40, 40, 50, 50);
            PdfWriter.getInstance(document, new FileOutputStream(file));
            document.open();

            addHeader(document);
            addQuotationMeta(document, quotation);
            addCustomerDetails(document, quotation);
            addItemsTable(document, quotation);
            addTotals(document, quotation);
            addApproxValueNote(document);
            addTermsAndConditions(document);
            addSignatureAndSeal(document);
            addFooter(document);

            document.close();
            return file.getPath();

        } catch (Exception ex) {
            throw new PdfGenerationException("Failed to generate PDF for quotation " + quotation.getQuotationNumber(), ex);
        }
    }

    private void addHeader(Document document) throws DocumentException {
        Paragraph details = new Paragraph();
        details.setFont(NORMAL_FONT);
        details.add(nullToEmpty(settingsService.getCompanyAddress()) + "\n");
        details.add("Phone: " + nullToEmpty(settingsService.getCompanyPhone()) + "  |  Email: " + nullToEmpty(settingsService.getCompanyEmail()) + "\n");
        if (settingsService.getCompanyGstNumber() != null && !settingsService.getCompanyGstNumber().isBlank()) {
            details.add("GSTIN: " + settingsService.getCompanyGstNumber() + "\n");
        }

        Image logo = loadLogo();
        if (logo != null) {
            logo.scaleToFit(60, 60);
            PdfPTable headerTable = new PdfPTable(new float[]{1f, 5f});
            headerTable.setWidthPercentage(100);

            PdfPCell logoCell = new PdfPCell(logo, false);
            logoCell.setBorder(Rectangle.NO_BORDER);
            logoCell.setVerticalAlignment(Element.ALIGN_MIDDLE);
            headerTable.addCell(logoCell);

            PdfPCell textCell = new PdfPCell();
            textCell.setBorder(Rectangle.NO_BORDER);
            textCell.addElement(new Paragraph(settingsService.getCompanyName(), TITLE_FONT));
            textCell.addElement(details);
            headerTable.addCell(textCell);

            document.add(headerTable);
        } else {
            document.add(new Paragraph(settingsService.getCompanyName(), TITLE_FONT));
            document.add(details);
        }

        document.add(Chunk.NEWLINE);
        LineSeparator sep = new LineSeparator();
        document.add(new Chunk(sep));
        document.add(Chunk.NEWLINE);
    }

    /**
     * Loads the company logo for the PDF header. Supports a plain filesystem path (e.g.
     * "/srv/branding/logo.png") or a "classpath:" path (e.g. "classpath:branding/company-logo.png",
     * the default, which ships with this app). Returns null if not configured or unreadable, so the
     * PDF still generates with a text-only header.
     */
    private Image loadLogo() {
        String path = companyProperties.getLogoPath();
        if (path == null || path.isBlank()) return null;
        try {
            if (path.startsWith("classpath:")) {
                String resourcePath = path.substring("classpath:".length());
                try (InputStream in = new ClassPathResource(resourcePath).getInputStream()) {
                    return Image.getInstance(in.readAllBytes());
                }
            }
            return Image.getInstance(path);
        } catch (Exception ex) {
            return null;
        }
    }

    private void addQuotationMeta(Document document, Quotation quotation) throws DocumentException {
        Paragraph title = new Paragraph("QUOTATION", HEADER_FONT);
        document.add(title);

        PdfPTable table = new PdfPTable(2);
        table.setWidthPercentage(100);
        table.setSpacingBefore(5);

        addPlainRow(table, "Quotation No:", quotation.getQuotationNumber());
        addPlainRow(table, "Quotation Created Date:", quotation.getCreatedAt().format(DateTimeFormatter.ofPattern("dd MMM yyyy")));
        addPlainRow(table, "Valid Until:", quotation.getValidUntil() != null
                ? quotation.getValidUntil().format(DateTimeFormatter.ofPattern("dd MMM yyyy")) : "-");

        document.add(table);
        document.add(Chunk.NEWLINE);
    }

    private void addCustomerDetails(Document document, Quotation quotation) throws DocumentException {
        var lead = quotation.getLead();
        Paragraph title = new Paragraph("Customer Details", HEADER_FONT);
        document.add(title);

        PdfPTable table = new PdfPTable(2);
        table.setWidthPercentage(100);
        table.setSpacingBefore(5);
        addPlainRow(table, "Company:", nullToEmpty(quotation.getCompanyName()));
        addPlainRow(table, "Phone:", lead.getPhone());
        addPlainRow(table, "Email:", nullToEmpty(lead.getEmail()));
        if (lead.getSquareFeet() != null) {
            addPlainRow(table, "Square Feet:", String.valueOf(lead.getSquareFeet()));
        }

        document.add(table);
        document.add(Chunk.NEWLINE);
    }


    /**
     * Rather than showing GST as its own line item, each product's displayed unit price and
     * amount are shown inclusive of its proportional share of the quotation's total GST. There
     * is no separate "GST" column/section in the table.
     */
    private void addItemsTable(Document document, Quotation quotation) throws DocumentException {
        Paragraph title = new Paragraph("Items", HEADER_FONT);
        document.add(title);

        PdfPTable table = new PdfPTable(new float[]{4f, 1.2f, 1.7f, 1.7f});
        table.setWidthPercentage(100);
        table.setSpacingBefore(5);

        addHeaderCell(table, "Description");
        addHeaderCell(table, "Qty");
        addHeaderCell(table, "Unit Price");
        addHeaderCell(table, "Amount");

        BigDecimal subtotal = quotation.getSubtotal() != null ? quotation.getSubtotal() : BigDecimal.ZERO;
        BigDecimal tax = quotation.getTax() != null ? quotation.getTax() : BigDecimal.ZERO;
        boolean hasTax = tax.compareTo(BigDecimal.ZERO) != 0 && subtotal.compareTo(BigDecimal.ZERO) != 0;

        BigDecimal allocatedTax = BigDecimal.ZERO;
        java.util.List<QuotationItem> items = quotation.getItems();
        for (int i = 0; i < items.size(); i++) {
            QuotationItem item = items.get(i);
            BigDecimal amount = item.getAmount() != null ? item.getAmount() : BigDecimal.ZERO;

            BigDecimal taxShare = BigDecimal.ZERO;
            if (hasTax) {
                boolean isLastItem = i == items.size() - 1;
                if (isLastItem) {
                    // Give the last item the remainder so item amounts always sum exactly to subtotal + tax.
                    taxShare = tax.subtract(allocatedTax);
                } else {
                    taxShare = amount.multiply(tax).divide(subtotal, 2, RoundingMode.HALF_UP);
                    allocatedTax = allocatedTax.add(taxShare);
                }
            }

            BigDecimal amountInclTax = amount.add(taxShare).setScale(2, RoundingMode.HALF_UP);
            BigDecimal unitPriceInclTax = (item.getQuantity() != null && item.getQuantity() != 0)
                    ? amountInclTax.divide(BigDecimal.valueOf(item.getQuantity()), 2, RoundingMode.HALF_UP)
                    : amountInclTax;

            table.addCell(cell(item.getDescription()));
            table.addCell(cell(item.getQuantity() != null ? String.valueOf(item.getQuantity()) : "-"));
            table.addCell(cell(unitPriceInclTax.toString()));
            table.addCell(cell(amountInclTax.toString()));
        }

        document.add(table);
        if (hasTax) {
            Paragraph note = new Paragraph("Prices shown are inclusive of applicable GST (GSTIN: "
                    + nullToEmpty(settingsService.getCompanyGstNumber()) + ").", SMALL_FONT);
            document.add(note);
        }
        document.add(Chunk.NEWLINE);
    }

    /**
     * Only shows a Discount line when the admin actually applied one while generating the
     * quotation; otherwise the PDF shows a single Grand Total line, with GST already folded
     * into the item amounts above.
     */
    private void addTotals(Document document, Quotation quotation) throws DocumentException {
        PdfPTable table = new PdfPTable(2);
        table.setWidthPercentage(50);
        table.setHorizontalAlignment(Element.ALIGN_RIGHT);
        table.setSpacingBefore(5);

        BigDecimal discount = quotation.getDiscount() != null ? quotation.getDiscount() : BigDecimal.ZERO;
        if (discount.compareTo(BigDecimal.ZERO) != 0) {
            BigDecimal subtotal = quotation.getSubtotal() != null ? quotation.getSubtotal() : BigDecimal.ZERO;
            BigDecimal tax = quotation.getTax() != null ? quotation.getTax() : BigDecimal.ZERO;
            addPlainRow(table, "Total:", subtotal.add(tax).setScale(2, RoundingMode.HALF_UP).toString());
            addPlainRow(table, "Discount:", "-" + discount.toString());
        }

        PdfPCell grandLabel = new PdfPCell(new Phrase("Grand Total:", HEADER_FONT));
        grandLabel.setBorder(Rectangle.TOP);
        PdfPCell grandValue = new PdfPCell(new Phrase(quotation.getTotalAmount().toString(), HEADER_FONT));
        grandValue.setBorder(Rectangle.TOP);
        table.addCell(grandLabel);
        table.addCell(grandValue);

        document.add(table);
        document.add(Chunk.NEWLINE);
    }

    /** Approximate-value disclaimer shown directly below the items/totals table. */
    private void addApproxValueNote(Document document) throws DocumentException {
        Paragraph note = new Paragraph(
                "Note: The above amount is approximate and indicative only. Final pricing may vary based on "
                        + "actual site conditions, measurements taken at the time of execution, structural design "
                        + "requirements, statutory approvals, and prevailing material costs.",
                SMALL_FONT);
        document.add(note);
        document.add(Chunk.NEWLINE);
    }

    /** Construction-industry terms & conditions for the quotation. */
    private void addTermsAndConditions(Document document) throws DocumentException {
        Paragraph terms = new Paragraph("Terms & Conditions", HEADER_FONT);
        document.add(terms);
        Paragraph termsBody = new Paragraph(
                "1. This quotation is valid until the date mentioned above; rates are subject to revision thereafter.\n" +
                "2. Scope of work is limited strictly to the items listed above. Civil, foundation, electrical, " +
                "plumbing and any other works are excluded unless explicitly mentioned.\n" +
                "3. Rates are based on the site details and specifications provided by the customer. Any variation " +
                "in site conditions, drawings, statutory requirements, or client-requested changes will be treated " +
                "as an extra and billed separately.\n" +
                "4. Delivery and execution timelines are indicative and subject to site readiness, weather " +
                "conditions, transportation, and statutory approvals.\n" +
                "5. Payment terms: as mutually agreed at the time of order confirmation (advance, milestone and " +
                "balance payments as applicable). Work/supply will commence only after receipt of the agreed advance.\n" +
                "6. Material ordered as per customer-approved specifications is non-returnable and non-refundable " +
                "once fabricated or dispatched.\n" +
                "7. Applicable taxes (GST) are as per the prevailing rate at the time of billing.\n" +
                "8. This document is a quotation only and does not constitute a binding contract until formally " +
                "accepted by the customer and confirmed by us in writing.\n" +
                "9. Any dispute arising out of this quotation shall be subject to the jurisdiction of the courts " +
                "where our registered office is located.",
                NORMAL_FONT);
        document.add(termsBody);
        document.add(Chunk.NEWLINE);
    }

    /** Left: authorized signatory / company seal. Right: customer acceptance signature and date. */
    private void addSignatureAndSeal(Document document) throws DocumentException {
        document.add(Chunk.NEWLINE);

        PdfPTable table = new PdfPTable(new float[]{1f, 1f});
        table.setWidthPercentage(100);
        table.setSpacingBefore(10);

        PdfPCell leftCell = new PdfPCell();
        leftCell.setBorder(Rectangle.NO_BORDER);
        leftCell.setPaddingTop(30);
        Paragraph forCompany = new Paragraph("For " + nullToEmpty(settingsService.getCompanyName()), HEADER_FONT);
        leftCell.addElement(forCompany);
        leftCell.addElement(new Paragraph(" ", NORMAL_FONT));
        leftCell.addElement(new Paragraph(" ", NORMAL_FONT));
        leftCell.addElement(new Paragraph("Authorized Signatory", NORMAL_FONT));
        Paragraph sealLabel = new Paragraph("(Company Seal)", SMALL_FONT);
        leftCell.addElement(sealLabel);
        table.addCell(leftCell);

        PdfPCell rightCell = new PdfPCell();
        rightCell.setBorder(Rectangle.NO_BORDER);
        rightCell.setPaddingTop(30);
        Paragraph acceptTitle = new Paragraph("Customer Acceptance", HEADER_FONT);
        acceptTitle.setAlignment(Element.ALIGN_RIGHT);
        rightCell.addElement(acceptTitle);
        Paragraph sig = new Paragraph("Signature: ______________________", NORMAL_FONT);
        sig.setAlignment(Element.ALIGN_RIGHT);
        rightCell.addElement(sig);
        Paragraph dt = new Paragraph("Dated: ____________", NORMAL_FONT);
        dt.setAlignment(Element.ALIGN_RIGHT);
        rightCell.addElement(dt);
        table.addCell(rightCell);

        document.add(table);
        document.add(Chunk.NEWLINE);
        Paragraph systemNote = new Paragraph("This is a system-generated quotation.", SMALL_FONT);
        document.add(systemNote);
    }

    /** Horizontal rule followed by the company address and contact details, centered. */
    private void addFooter(Document document) throws DocumentException {
        document.add(Chunk.NEWLINE);
        LineSeparator sep = new LineSeparator();
        document.add(new Chunk(sep));
        document.add(Chunk.NEWLINE);

        Paragraph addr = new Paragraph(nullToEmpty(settingsService.getCompanyAddress()), SMALL_FONT);
        addr.setAlignment(Element.ALIGN_CENTER);
        document.add(addr);

        StringBuilder contactLine = new StringBuilder();
        if (notBlank(settingsService.getCompanyPhone())) contactLine.append("Phone: ").append(settingsService.getCompanyPhone());
        if (notBlank(settingsService.getCompanyEmail())) {
            if (contactLine.length() > 0) contactLine.append("  |  ");
            contactLine.append("Email: ").append(settingsService.getCompanyEmail());
        }
        if (contactLine.length() > 0) {
            Paragraph contact = new Paragraph(contactLine.toString(), SMALL_FONT);
            contact.setAlignment(Element.ALIGN_CENTER);
            document.add(contact);
        }
    }

    private boolean notBlank(String s) {
        return s != null && !s.isBlank();
    }

    private void addPlainRow(PdfPTable table, String label, String value) {
        PdfPCell labelCell = new PdfPCell(new Phrase(label, HEADER_FONT));
        labelCell.setBorder(Rectangle.NO_BORDER);
        PdfPCell valueCell = new PdfPCell(new Phrase(value == null ? "-" : value, NORMAL_FONT));
        valueCell.setBorder(Rectangle.NO_BORDER);
        table.addCell(labelCell);
        table.addCell(valueCell);
    }

    private void addHeaderCell(PdfPTable table, String text) {
        PdfPCell cell = new PdfPCell(new Phrase(text, HEADER_FONT));
        cell.setBackgroundColor(new Color(240, 240, 240));
        table.addCell(cell);
    }

    private PdfPCell cell(String text) {
        return new PdfPCell(new Phrase(text == null ? "-" : text, NORMAL_FONT));
    }

    private String nullToEmpty(String s) {
        return s == null ? "" : s;
    }
}
