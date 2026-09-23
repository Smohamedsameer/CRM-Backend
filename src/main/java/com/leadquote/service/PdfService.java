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
import java.time.format.DateTimeFormatter;

@Service
@RequiredArgsConstructor
public class PdfService {

    private final CompanyProperties companyProperties;
    private final AppProperties appProperties;

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
            addTermsAndAcceptance(document);

            document.close();
            return file.getPath();

        } catch (Exception ex) {
            throw new PdfGenerationException("Failed to generate PDF for quotation " + quotation.getQuotationNumber(), ex);
        }
    }

    private void addHeader(Document document) throws DocumentException {
        Paragraph details = new Paragraph();
        details.setFont(NORMAL_FONT);
        details.add(nullToEmpty(companyProperties.getAddress()) + "\n");
        details.add("Phone: " + nullToEmpty(companyProperties.getPhone()) + "  |  Email: " + nullToEmpty(companyProperties.getEmail()) + "\n");
        if (companyProperties.getGstNumber() != null && !companyProperties.getGstNumber().isBlank()) {
            details.add("GSTIN: " + companyProperties.getGstNumber() + "\n");
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
            textCell.addElement(new Paragraph(companyProperties.getName(), TITLE_FONT));
            textCell.addElement(details);
            headerTable.addCell(textCell);

            document.add(headerTable);
        } else {
            document.add(new Paragraph(companyProperties.getName(), TITLE_FONT));
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
        addPlainRow(table, "Date:", quotation.getCreatedAt().format(DateTimeFormatter.ofPattern("dd MMM yyyy")));
        addPlainRow(table, "Valid Until:", quotation.getValidUntil() != null
                ? quotation.getValidUntil().format(DateTimeFormatter.ofPattern("dd MMM yyyy")) : "-");
        addPlainRow(table, "Status:", quotation.getStatus().name());

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
        addPlainRow(table, "Name:", quotation.getCustomerName());
        addPlainRow(table, "Company:", nullToEmpty(quotation.getCompanyName()));
        addPlainRow(table, "Phone:", lead.getPhone());
        addPlainRow(table, "Email:", nullToEmpty(lead.getEmail()));
        if (lead.getSquareFeet() != null) {
            addPlainRow(table, "Square Feet:", String.valueOf(lead.getSquareFeet()));
        }

        document.add(table);
        document.add(Chunk.NEWLINE);

        if (lead.getEnquiry() != null) {
            addPebSpecTable(document, lead.getEnquiry());
        }
    }

    /** Renders the PEB requirement spec (building type, frame, dimensions, cladding, crane, scope) if present. */
    private void addPebSpecTable(Document document, com.leadquote.entity.Enquiry e) throws DocumentException {
        java.util.List<String[]> rows = new java.util.ArrayList<>();
        if (notBlank(e.getBuildingType())) {
            String type = "OTHER".equals(e.getBuildingType()) ? nullToEmpty(e.getBuildingTypeOther()) : labelize(e.getBuildingType());
            rows.add(new String[]{"Building Type", type});
        }
        if (notBlank(e.getNoOfSheds())) rows.add(new String[]{"No. of Sheds", e.getNoOfSheds()});
        if (notBlank(e.getEndFrameType())) rows.add(new String[]{"End Frame", labelize(e.getEndFrameType())});
        if (notBlank(e.getColumnType())) rows.add(new String[]{"Column", labelize(e.getColumnType())});
        if (e.getSpanWidthM() != null) rows.add(new String[]{"Span / Width", e.getSpanWidthM() + " m"});
        if (e.getLengthM() != null) rows.add(new String[]{"Length", e.getLengthM() + " m"});
        if (e.getClearEaveHeightM() != null) rows.add(new String[]{"Clear Eave Height", e.getClearEaveHeightM() + " m"});
        if (e.getEaveHeightM() != null) rows.add(new String[]{"Eave Height", e.getEaveHeightM() + " m"});
        if (notBlank(e.getRoofCladdingMaterial())) {
            rows.add(new String[]{"Roof Cladding", labelize(e.getRoofCladdingMaterial())
                    + (notBlank(e.getRoofCladdingThickness()) ? ", " + e.getRoofCladdingThickness() : "")
                    + (notBlank(e.getRoofCladdingColour()) ? ", " + e.getRoofCladdingColour() : "")});
        }
        if (notBlank(e.getSideCladdingMaterial())) {
            rows.add(new String[]{"Side Cladding", labelize(e.getSideCladdingMaterial())
                    + (notBlank(e.getSideCladdingThickness()) ? ", " + e.getSideCladdingThickness() : "")
                    + (notBlank(e.getSideCladdingColour()) ? ", " + e.getSideCladdingColour() : "")});
        }
        if (Boolean.TRUE.equals(e.getCraneRequired())) {
            String crane = "Yes";
            if (notBlank(e.getCraneType())) crane += " — " + e.getCraneType();
            if (notBlank(e.getCraneCapacityTonnes())) crane += ", " + e.getCraneCapacityTonnes() + " T";
            if (notBlank(e.getCraneHeightM())) crane += ", " + e.getCraneHeightM() + " m height";
            rows.add(new String[]{"Crane / Hoist", crane});
        }
        if (notBlank(e.getScopeOfWork())) {
            String scope = labelize(e.getScopeOfWork());
            if (notBlank(e.getScopeOfWorkLocation())) scope += " (" + e.getScopeOfWorkLocation() + ")";
            rows.add(new String[]{"Scope of Work", scope});
        }
        if (notBlank(e.getRequirement())) rows.add(new String[]{"Requirement", e.getRequirement()});
        if (notBlank(e.getSpecifications())) rows.add(new String[]{"Specifications", e.getSpecifications()});
        if (notBlank(e.getAdditionalRequirements())) rows.add(new String[]{"Additional Requirements", e.getAdditionalRequirements()});
        if (notBlank(e.getRemarks())) rows.add(new String[]{"Remarks", e.getRemarks()});

        if (rows.isEmpty()) return;

        Paragraph title = new Paragraph("Requirement / Building Specification", HEADER_FONT);
        document.add(title);

        PdfPTable table = new PdfPTable(2);
        table.setWidthPercentage(100);
        table.setSpacingBefore(5);
        for (String[] row : rows) {
            addPlainRow(table, row[0] + ":", row[1]);
        }
        document.add(table);
        document.add(Chunk.NEWLINE);
    }

    private boolean notBlank(String s) {
        return s != null && !s.isBlank();
    }

    /** BUILDING_TYPE -> "Building Type" */
    private String labelize(String value) {
        if (value == null) return "";
        String[] words = value.toLowerCase().split("_");
        StringBuilder sb = new StringBuilder();
        for (String w : words) {
            if (sb.length() > 0) sb.append(" ");
            sb.append(Character.toUpperCase(w.charAt(0))).append(w.substring(1));
        }
        return sb.toString();
    }

    private void addItemsTable(Document document, Quotation quotation) throws DocumentException {
        Paragraph title = new Paragraph("Items", HEADER_FONT);
        document.add(title);

        PdfPTable table = new PdfPTable(new float[]{4f, 1.2f, 1.5f, 1.5f});
        table.setWidthPercentage(100);
        table.setSpacingBefore(5);

        addHeaderCell(table, "Description");
        addHeaderCell(table, "Qty");
        addHeaderCell(table, "Unit Price");
        addHeaderCell(table, "Amount");

        for (QuotationItem item : quotation.getItems()) {
            table.addCell(cell(item.getDescription()));
            table.addCell(cell(item.getQuantity() != null ? String.valueOf(item.getQuantity()) : "-"));
            table.addCell(cell(item.getUnitPrice() != null ? item.getUnitPrice().toString() : "-"));
            table.addCell(cell(item.getAmount() != null ? item.getAmount().toString() : "-"));
        }

        document.add(table);
        document.add(Chunk.NEWLINE);
    }

    private void addTotals(Document document, Quotation quotation) throws DocumentException {
        PdfPTable table = new PdfPTable(2);
        table.setWidthPercentage(50);
        table.setHorizontalAlignment(Element.ALIGN_RIGHT);
        table.setSpacingBefore(5);

        addPlainRow(table, "Subtotal:", quotation.getSubtotal().toString());
        addPlainRow(table, "Discount:", quotation.getDiscount().toString());
        addPlainRow(table, "Tax:", quotation.getTax().toString());

        PdfPCell grandLabel = new PdfPCell(new Phrase("Grand Total:", HEADER_FONT));
        grandLabel.setBorder(Rectangle.TOP);
        PdfPCell grandValue = new PdfPCell(new Phrase(quotation.getTotalAmount().toString(), HEADER_FONT));
        grandValue.setBorder(Rectangle.TOP);
        table.addCell(grandLabel);
        table.addCell(grandValue);

        document.add(table);
        document.add(Chunk.NEWLINE);
    }

    private void addTermsAndAcceptance(Document document) throws DocumentException {
        Paragraph terms = new Paragraph("Terms & Conditions", HEADER_FONT);
        document.add(terms);
        Paragraph termsBody = new Paragraph(
                "1. This quotation is valid until the date mentioned above.\n" +
                "2. Prices are subject to change after the validity period.\n" +
                "3. Delivery timelines will be confirmed upon order confirmation.\n" +
                "4. Payment terms as mutually agreed at the time of order confirmation.",
                NORMAL_FONT);
        document.add(termsBody);
        document.add(Chunk.NEWLINE);

        Paragraph acceptance = new Paragraph("Acceptance", HEADER_FONT);
        document.add(acceptance);
        document.add(new Paragraph("Customer Signature: ______________________     Date: ____________", NORMAL_FONT));
        document.add(Chunk.NEWLINE);
        document.add(new Paragraph("This is a system-generated quotation.", SMALL_FONT));
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
