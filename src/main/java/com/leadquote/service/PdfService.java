package com.leadquote.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.leadquote.config.AppProperties;
import com.leadquote.entity.Enquiry;
import com.leadquote.entity.Lead;
import com.leadquote.entity.LeadCoverImage;
import com.leadquote.entity.Quotation;
import com.leadquote.entity.QuotationItem;
import com.leadquote.exception.PdfGenerationException;
import com.leadquote.repository.LeadCoverImageRepository;
import com.lowagie.text.Document;
import com.lowagie.text.DocumentException;
import com.lowagie.text.Element;
import com.lowagie.text.Font;
import com.lowagie.text.Image;
import com.lowagie.text.Paragraph;
import com.lowagie.text.Phrase;
import com.lowagie.text.Rectangle;
import com.lowagie.text.Chunk;
import com.lowagie.text.pdf.BaseFont;
import com.lowagie.text.pdf.ColumnText;
import com.lowagie.text.pdf.PdfContentByte;
import com.lowagie.text.pdf.PdfGState;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfWriter;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

import java.awt.Color;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Builds the Senela PEB quotation: 11 A4 pages that follow the company's own quotation template
 * (cover, covering letter, index, sections 1-9, signature).
 *
 * Only the customer block, the cover project name / photo, Section 1 (building + canopies),
 * the openings table, the Crane 1 table and the Section 7 price table change per quotation; everything
 * else is the fixed company text.
 *
 * Positions are written in "px" taken from a 924 x 1307 px render of the sample PDF and converted to points
 * with K, so they can be compared directly against that render.
 */
@Service
@RequiredArgsConstructor
public class PdfService {

    private final AppProperties appProperties;
    private final LeadCoverImageRepository coverImageRepository;

    // ------------------------------------------------------------------ geometry
    private static final float PW = 595.5f;
    private static final float PH = 842.25f;
    private static final double K = 595.5 / 924.0;

    private static float X(double px) {
        return (float) (px * K);
    }

    private static float Y(double px) {
        return PH - (float) (px * K);
    }

    // ------------------------------------------------------------------ colours
    private static final Color NAME_BROWN = new Color(157, 110, 52);
    private static final Color TABLE_BLUE = new Color(31, 73, 125);
    private static final Color NAVY = new Color(10, 36, 72);
    private static final Color BADGE_FILL = new Color(222, 188, 124);
    private static final Color BADGE_LINE = new Color(205, 160, 95);

    // ------------------------------------------------------------------ fixed company text
    private static final String COMPANY_NAME = "SENELA INTERNATIONAL VENTURES PRIVATE LIMITED";
    private static final String SIGNATORY_NAME = "R. Elangovan";
    private static final String SIGNATORY_PHONE = "9942466663";

    private static final String[] FOOTER_COVER = {
            "Tel: + 91 9942466663, Corporate office: - No: - 3, 3A, 3rd Floor, K&T Business Tower,",
            "Gowri Ammal 1st Street, Porur, Rajagopal Nagar, Chennai-600116",
            "Factory: - No:-32,33&34, SIDCO Nagapanam, Email:info@senelainternaonal.com",
            "Commercial Reg.No. U23955TN2023PTC166250,"
    };
    private static final String[] FOOTER = {
            "Tel: + 91 0447970026, Corporate office: - No: - 3, 3A, 3rd Floor, K&T Business Tower,",
            "Gowri Ammal 1st Street, Porur, Rajagopal Nagar, Chennai-600116",
            "Factory: - No:-32,33&34, SIDCO Nagapanam, Email:info@senelainternaonal.com",
            "Commercial Reg.No. U23955TN2023PTC166250,Paid-up Capital:INR. 1,000,000."
    };
    /** Right edge (px) of each footer line, as measured on the sample, so the block lines up the same way. */
    private static final double[][] FOOTER_PX = {{262, 842}, {355, 802}, {279, 836}, {315, 877}};
    private static final double[][] FOOTER_COVER_PX = {{262, 842}, {355, 802}, {279, 836}, {315, 658}};

    private static final String NA = "NA";
    private static final String BY_DESIGN = "As per design";

    private static final ObjectMapper JSON = new ObjectMapper();
    private static final Map<String, byte[]> RESOURCES = new ConcurrentHashMap<>();
    private static volatile Fonts fonts;

    // ================================================================== entry point

    public String generateQuotationPdf(Quotation quotation) {
        try {
            File dir = new File(appProperties.getPdfStorageDir());
            if (!dir.exists()) dir.mkdirs();
            String fileName = quotation.getQuotationNumber().replace("/", "-") + ".pdf";
            File file = new File(dir, fileName);

            Lead lead = quotation.getLead();
            Enquiry enquiry = lead.getEnquiry();
            Map<String, Object> extra = parseExtra(enquiry);
            Fonts f = fonts();

            Document document = new Document(new Rectangle(PW, PH), 0, 0, 0, 0);
            PdfWriter writer = PdfWriter.getInstance(document, new FileOutputStream(file));
            document.open();
            PdfContentByte cb = writer.getDirectContent();

            Ctx c = new Ctx(f, cb, refText(quotation), dateText(quotation, lead));

            coverPage(c, lead);
            nextPage(document, writer);
            letterPage(c, lead);
            nextPage(document, writer);
            indexPage(c);
            nextPage(document, writer);
            buildingPage(c, enquiry, extra);
            nextPage(document, writer);
            openingsPage(c, enquiry, extra);
            nextPage(document, writer);
            cranePage(c, enquiry);
            nextPage(document, writer);
            materialPage(c);
            nextPage(document, writer);
            finishPage(c);
            nextPage(document, writer);
            pricePage(c, quotation, enquiry);
            nextPage(document, writer);
            termsPage(c, enquiry);
            nextPage(document, writer);
            closingPage(c);

            writer.setPageEmpty(false);
            document.close();
            return file.getPath();
        } catch (Exception ex) {
            throw new PdfGenerationException("Failed to generate PDF for quotation " + quotation.getQuotationNumber(), ex);
        }
    }

    private static void nextPage(Document document, PdfWriter writer) {
        writer.setPageEmpty(false);
        document.newPage();
    }

    // ================================================================== page 1 - cover

    private void coverPage(Ctx c, Lead lead) throws Exception {
        PdfContentByte cb = c.cb;

        // full-page photo (the lead's chosen image, otherwise the bundled default)
        Image photo = loadCoverPhoto(lead);
        float s = Math.max(PW / photo.getWidth(), PH / photo.getHeight());
        float w = photo.getWidth() * s;
        float h = photo.getHeight() * s;
        photo.scaleAbsolute(w, h);
        photo.setAbsolutePosition((PW - w) / 2f, (PH - h) / 2f);
        cb.addImage(photo);

        // white header and footer panels
        cb.saveState();
        cb.setColorFill(Color.WHITE);
        cb.rectangle(0, Y(139), PW, PH - Y(139));
        cb.rectangle(0, 0, PW, Y(1117));
        cb.fill();
        cb.restoreState();

        // navy title band
        PdfGState gs = new PdfGState();
        gs.setFillOpacity(0.93f);
        cb.saveState();
        cb.setGState(gs);
        cb.setColorFill(NAVY);
        cb.rectangle(0, Y(365), PW, (float) ((365 - 256) * K));
        cb.fill();
        cb.restoreState();

        drawLogoAndName(c);
        drawHeaderLine(c, 139);

        // "Quotation for"
        float titleSize = fit(c.f.bold, "Quotation for", X(424));
        text(cb, c.f.bold, titleSize, Color.WHITE, PW / 2f, Y(214), "Quotation for", PdfContentByte.ALIGN_CENTER);

        String line1 = "PROPOSED PRE-ENGINEERED STEEL BUILDING";
        text(cb, c.f.reg, fit(c.f.reg, line1, X(623)), Color.WHITE, PW / 2f, Y(296), line1, PdfContentByte.ALIGN_CENTER);

        String project = firstNonBlank(lead.getProjectName(), lead.getCompanyName(), lead.getCustomerName());
        project = project == null ? "" : project.toUpperCase();
        float projSize = Math.min(fit(c.f.reg, "SRI AYYAPPA GLASS HOUSE \u2013 KARUR", X(710)), 22f);
        projSize = Math.min(projSize, fit(c.f.reg, project, PW - 60f));
        text(cb, c.f.reg, projSize, Color.WHITE, PW / 2f, Y(349), project, PdfContentByte.ALIGN_CENTER);

        footer(c, FOOTER_COVER, FOOTER_COVER_PX);
    }

    private Image loadCoverPhoto(Lead lead) throws Exception {
        if (lead.getId() != null) {
            LeadCoverImage stored = coverImageRepository.findById(lead.getId()).orElse(null);
            if (stored != null && stored.getData() != null && stored.getData().length > 0) {
                try {
                    return Image.getInstance(stored.getData());
                } catch (Exception ignored) {
                    // unsupported image format - fall back to the default cover below
                }
            }
        }
        return Image.getInstance(resource("branding/default-cover.jpg"));
    }

    // ================================================================== page 2 - covering letter

    private void letterPage(Ctx c, Lead lead) throws Exception {
        PdfContentByte cb = c.cb;
        pageFrame(c, 1, 281);
        float sz = c.body(10.2f);

        line(c, "QUOTATION", 340, 224, sz, false);

        // To block: company (or customer), address lines, pincode, GSTIN
        List<String> to = new ArrayList<>();
        String name = firstNonBlank(lead.getCompanyName(), lead.getCustomerName());
        if (name != null) to.add(name.toUpperCase());
        if (lead.getAddress() != null && !lead.getAddress().isBlank()) {
            for (String part : lead.getAddress().split("[\\r\\n,]+")) {
                if (!part.isBlank()) to.add(part.trim().toUpperCase());
            }
        }
        if (lead.getPincode() != null && !lead.getPincode().isBlank()) to.add(lead.getPincode().trim());
        List<String> toLines = new ArrayList<>();
        for (int i = 0; i < to.size(); i++) toLines.add(to.get(i) + (i == to.size() - 1 ? "." : ","));
        if (lead.getGstNumber() != null && !lead.getGstNumber().isBlank()) toLines.add("GSTIN: " + lead.getGstNumber().trim().toUpperCase());

        line(c, "To", 26, 339, sz, false);
        double pitch = Math.min(27.5, 112.0 / Math.max(1, toLines.size()));
        for (int i = 0; i < toLines.size(); i++) line(c, toLines.get(i), 14, 366 + i * pitch, sz, false);

        String project = firstNonBlank(lead.getProjectName(), lead.getCompanyName(), lead.getCustomerName());
        String where = lead.getAddress() == null ? "" : lead.getAddress().replaceAll("[\\r\\n]+", ", ").trim();
        String sub = "Sub.: Supply & Erection of Pre-Engineered Building (PEB) for your "
                + (project == null ? "" : project.toUpperCase()) + " Project"
                + (where.isEmpty() ? "" : " at " + where) + " - reg";
        paragraph(c, sub, 21, 485, 761, sz, 16f, false);

        line(c, "Dear Sir,", 21, 561, sz, false);
        paragraph(c, "With reference to your inquiry, and subsequent discussion we had had with you, please find enclosed here with our proposal for your kind perusal.",
                21, 623, 761, sz, 14.8f, false);
        line(c, "We believe that our Indexed Proposal / Offer will facilitate easy reference.", 21, 698, sz, false);
        paragraph(c, "In case you do require any further clarifications, please feel free to revert to the undersigned with your queries.",
                21, 760, 761, sz, 14.8f, false);
        line(c, "Thanking you and assuring you of our best services at all times, we remain,", 21, 836, sz, false);
        line(c, "Very truly yours,", 21, 862, sz, false);
        line(c, "For Senela International Ventures Pvt. Ltd.", 21, 891, sz, false);
        signature(c, 22, 906, 313);
        line(c, SIGNATORY_NAME, 21, 1084, sz, false);
        line(c, SIGNATORY_PHONE, 21, 1111, sz, false);
    }

    // ================================================================== page 3 - index

    private void indexPage(Ctx c) throws Exception {
        pageFrame(c, 2, 224);
        float sz = c.body(11f);
        line(c, "PROPOSAL INDEX", 418, 338, sz, false);
        c.underline(X(418), Y(338 + 9), X(572));
        line(c, "Section", 72, 422, sz, false);
        line(c, "DESCRIPTION", 175, 422, sz, false);
        line(c, "PAGE", 574, 422, sz, false);

        String[][] rows = {
                {"1.", "Building Description", "03"}, {"2.", "Standard Supplied Material", "05"},
                {"3.", "Material Specification", "06"}, {"4.", "Steel Work Finish", "07"},
                {"5.", "Completion Period", "07"}, {"6.", "Proposal validity", "08"},
                {"7.", "Prices", "08"}, {"8.", "Assumptions & Deviations", "09"},
                {"9.", "Force Majeure Clause", "10"}
        };
        double y = 479;
        for (String[] r : rows) {
            line(c, r[0], 72, y, sz, false);
            line(c, r[1], 208, y, sz, false);
            rightLine(c, r[2], 620, y, sz);
            y += 56.3;
        }
    }

    // ================================================================== page 4 - Section 1 (form data)

    private void buildingPage(Ctx c, Enquiry e, Map<String, Object> x) throws Exception {
        pageFrame(c, 3, 224);
        heading(c, "Section 1. BUILDING DESCRIPTION", 21, 262, 13f);

        PdfPTable t = new PdfPTable(3);
        t.addCell(span(c, "BUILDING DESCRIPTION", 3, 15.2f));
        t.addCell(hdr(c, "S.No.", false));
        t.addCell(hdr(c, "Parameter", false));
        t.addCell(hdr(c, "Details", false));
        int n = 1;
        for (String[] r : buildingRows(e, x)) {
            t.addCell(cell(c, String.valueOf(n++), Element.ALIGN_LEFT));
            t.addCell(cell(c, r[0], Element.ALIGN_LEFT));
            t.addCell(cell(c, r[1], Element.ALIGN_LEFT));
        }
        t.addCell(span(c, "CANOPIES", 3, 15.2f));
        n = 1;
        for (String[] r : canopyRows(x)) {
            t.addCell(cell(c, String.valueOf(n++), Element.ALIGN_LEFT));
            t.addCell(cell(c, r[0], Element.ALIGN_LEFT));
            t.addCell(cell(c, r[1], Element.ALIGN_LEFT));
        }
        drawTable(c, t, 48, 275, 74, 331, 273);
    }

    // ================================================================== page 5 - openings, design & loading

    private void openingsPage(Ctx c, Enquiry e, Map<String, Object> x) throws Exception {
        pageFrame(c, 4, 196);

        PdfPTable t = new PdfPTable(3);
        t.addCell(span(c, "OPENINGS, SKYLIGHTS & TURBO VENTS", 3, 15.2f));
        int n = 1;
        for (String[] r : openingRows(e, x)) {
            t.addCell(cell(c, String.valueOf(n++), Element.ALIGN_LEFT));
            t.addCell(cell(c, r[0], Element.ALIGN_LEFT));
            t.addCell(cell(c, r[1], Element.ALIGN_LEFT));
        }
        t.addCell(span(c, "DESIGN & LOADING", 3, 15.2f));
        fixedBlock(c, t, new String[]{"Sr no.", "Parameter", "Details"}, new String[][]{
                {"Desing code", "AISC code"}, {"Dead Load in kN/m2", "0.1"}, {"Live Load in kN/m2", "0.57"},
                {"Collateral load kN/m2", "0"}, {"Max wind speed in m/s", "44"}, {"Max rainfall intensity in mm/hr.", "150"},
                {"Exposure category", "B"}, {"Seismic zone co-efficient (III)", "0.24"}, {"Maximum temperate variation", "20"}});
        fixedBlock(c, t, new String[]{"Sr no.", "Parameter", "Details"}, new String[][]{
                {"Frames Vertical deflection", "Span/150"}, {"Frames Horizontal deflection", "Height/100"}, {"Purlins", "Span/150"},
                {"Girts", "Span/120"}, {"Crane beams", "Span/600"}, {"Mezzanine beams", "Span/240"}, {"Misc", "NA"}});
        fixedBlock(c, t, new String[]{"Sr no.", "Assumption/Remark", "Details"}, new String[][]{
                {"Hot rolled sections grade (Mpa)", "250"}, {"Cold form sections grade (Mpa)", "350"},
                {"Primary sections grade (Mpa)", "350"}, {"Building design condition", "Enclosed"},
                {"Minimum web thickness in mm", "4"}, {"Minimum flange thickness in mm", "5"},
                {"Minimum flange width in mm", "125"}, {"Minimum web depth in mm", "250"},
                {"Minimum cold form thickness in mm", "1.5"}, {"Sheeting profile width & length in m", "1.22 & 7.65"},
                {"Connection bolts grade", "High strength bolts (ASTM A325 or equivalent)"}});
        drawTable(c, t, 36, 213, 77, 331, 273);
    }

    // ================================================================== page 6 - crane, exclusions, section 2

    private void cranePage(Ctx c, Enquiry e) throws Exception {
        pageFrame(c, 5, 224);
        float sz = c.body(10.2f);

        PdfPTable t = new PdfPTable(3);
        t.addCell(span(c, "Crane 1", 3, 15.2f));
        String[] labels = {"Crane Capacity in kN", "Crane beam top height in mtr", "Crane span in mtr",
                "Crane starting point in mtr", "Crane running length in mtr", "Crane class", "Crane Type",
                "Type of Girder", "Bridge Weight in MT", "Hoist/Trolley Weight in MT", "Number of Wheels",
                "Wheel Load in MT", "Wheel Base in mm"};
        String[] values = craneValues(e);
        for (int i = 0; i < labels.length; i++) {
            t.addCell(cell(c, String.valueOf(i + 1), Element.ALIGN_LEFT));
            t.addCell(cell(c, labels[i], Element.ALIGN_LEFT));
            t.addCell(cell(c, values[i], Element.ALIGN_LEFT));
        }
        drawTable(c, t, 37, 294, 77, 330, 276);

        heading(c, "EXCLUSIONS:", 77, 681, 13f);
        String[] ex = {"1. Roof/wall extracts fans & other exhaust systems", "2. All doors/windows, rolling shutter",
                "3. Masonry work & supervision.", "4. Miscellaneous steel except as specified herein.",
                "5. Crane Girder and Accessories"};
        for (int i = 0; i < ex.length; i++) line(c, ex[i], 77, 729 + i * 26.0, sz, false);

        heading(c, "Section 2. Standard Supplied Items:", 73, 872, 13.5f);
        line(c, "The materials supplied by Senela are as per the Standards applicable to Senela Buildings.", 72, 924, sz, false);
        String[] items = {"1. Anchor Bolts.", "2. Flashing and Trims", "3. Closure Strips", "4. Sealing Tape",
                "5. Rod Bracing", "6. Connection Bolt", "7. Sheeting Fasteners"};
        for (int i = 0; i < items.length; i++) line(c, items[i], 72, 950 + i * 25.5, sz, false);
    }

    // ================================================================== page 7 - Section 3

    private void materialPage(Ctx c) throws Exception {
        pageFrame(c, 6, 196);
        float sz = c.body(10.2f);
        float lead = 13.4f;
        String profile = "Providing and Fixing of single Skin Senela Profile, Overall Width of 1060 mm and covered width of "
                + "1000 mm, with six Crests (29 mm crest height, width of 25 mm at the top of the crest and 74 mm at "
                + "the base of the crest), spaced at 200 mm - crest centre to centre, and with two stiffening ribs (20 mm) "
                + "at the centre of each valley of the sheet, with two anti-capillary grooves (5 mm) on either side of each "
                + "crest making the Trapezoidal Profile the best in the Industry in terms of appearance, strength and "
                + "cost effectiveness.";

        heading(c, "Section 3. Material Specification", 93, 232, 13f);
        line(c, "A.", 33, 280, 12f, false);
        heading(c, "ANCHOR BOLT", 80, 288, 12.5f);
        paragraph(c, "The steel column strut of Pre-Engineered Building and RCC interface shall be designed as a "
                + "fixed/pinned base with a minimum of 20 mm Dia anchor bolt according to the design calculation.", 73, 336, 881, sz, lead, true);

        line(c, "B.", 33, 407, 12f, false);
        heading(c, "ROOFING SHEET: GALVALUME", 62, 415, 12.5f);
        paragraph(c, profile, 73, 468, 881, sz, lead, true);

        line(c, "C.", 33, 637, 12f, false);
        heading(c, "CLADDING: COLOR COATED GALVALUME", 62, 645, 12.5f);
        paragraph(c, profile, 73, 696, 881, sz, lead, true);

        line(c, "D.", 33, 858, 12f, false);
        heading(c, "SHEETING FASTENERS", 66, 866, 12.5f);
        paragraph(c, "The sheets will be fastened with No.14-10 x 20 mm (or as per design) galvanized hex-self tapping "
                + "metal screws of approved make and quality including EPDM / Neoprene washer on each crest of "
                + "sheets for connecting with purlins (or as per design).", 70, 919, 881, sz, lead, true);
    }

    // ================================================================== page 8 - sections 3 (E, F), 4, 5

    private void finishPage(Ctx c) throws Exception {
        pageFrame(c, 7, 228);
        float sz = c.body(10.2f);
        float lead = 13.4f;

        line(c, "E.", 30, 316, 12f, false);
        heading(c, "FLASHING AND TRIMS:", 50, 318, 12.5f);
        paragraph(c, "Suitable flashing shall be provided at the eaves in PPGI Sheets. Material used for manufacture of "
                + "Flashing shall be same as that of wall cladding. Flashing shall be provided at the junction/termination "
                + "edges of sheeting, vertical corners, barge-roof to wall cladding etc., to ensure neat finish and trims, "
                + "flashing be matching to wall cladding.", 55, 359, 881, sz, lead, true);

        line(c, "F.", 30, 466, 12f, false);
        heading(c, "SEALER:", 50, 469, 12.5f);
        paragraph(c, "Sealer should be applied as per requirement at side laps and end laps of roof panels and around self "
                + "flashing windows. Sealer shall be 6 mm wide x 5 mm thick, asbestos fiber filled pressure sensitive Butyl "
                + "tapes. The sealer shall be non-asphalted, non-shrinking, non-drying and non-toxic and shall have "
                + "superior adhesion to metals, plastics and painted surfaces at temperatures from "
                + "\u201351 degree Celsius to +104 degrees Celsius.", 55, 520, 881, sz, lead, true);

        heading(c, "Section 4. Steel Work Finish: (Primary & Secondary Steel):", 45, 646, 13f);
        paragraph(c, "Primary steel shall be cleaned to specification St2. One shop primer coat of Red Oxide primer shall be "
                + "applied with an average dry film Thickness of 25 microns on all red steel Shop primer provides "
                + "protection for elements While in transit and construction and is not intended to be for permanent "
                + "protection. The final paint ENAMEL will be applied at site.", 48, 698, 881, sz, lead, true);

        heading(c, "Section 5. Completion Period:", 45, 831, 13f);
        line(c, "3.1", 27, 880, sz, false);
        line(c, "Completion period is 08 -10 Weeks from the date of receipt and acceptance of the following:", 70, 880, sz, false);
        String[][] it = {{"i)", "Signed Contract (Any change must be noted and initialed by both parties)."},
                {"ii)", "As per payment terms."}, {"iii)", "Final change order."},
                {"iv)", "Signed final \"approved as noted\" Approval Drawings."}};
        for (int i = 0; i < it.length; i++) {
            line(c, it[i][0], 27, 906 + i * 25.4, sz, false);
            line(c, it[i][1], 65, 906 + i * 25.4, sz, false);
        }
    }

    // ================================================================== page 9 - validity and prices

    private void pricePage(Ctx c, Quotation q, Enquiry e) throws Exception {
        pageFrame(c, 8, 196);
        float sz = c.body(10.2f);

        line(c, "3.2 Please allow a minimum of 10 Days for Approval Drawings to be submitted.", 21, 239, sz, false);
        paragraph(c, "Approval drawings must be returned to our Office within 7 Days of receipt by customer. Any delay in "
                + "the return of the approval drawings back to Senela may result in change in delivery commitment.",
                21, 268, 840, sz, 13.8f, true);

        heading(c, "Section 6. Proposal Validity:", 21, 358, 13.5f);
        paragraph(c, "This proposal is valid for 15 days from the above noted date. Any extension of validity by Senela "
                + "International Ventures Pvt Ltd must be in writing.", 21, 410, 881, sz, 13.8f, true);

        heading(c, "Changes/Revisions:", 26, 491, 13f);
        paragraph(c, "Any change and /or revision to the above stated scope of supply may lead to a variation in the price and "
                + "the delivery period. A change must not hold payment for work completed in the original contract.",
                21, 541, 881, sz, 13.8f, true);

        heading(c, "Section 7. PRICES :Pre-Engineered Steel Building", 21, 632, 13f);

        // ---- price table (area, rate and amount come from the quotation builder)
        double area = q.getAreaSqft() != null ? q.getAreaSqft() : firstQty(q);
        BigDecimal rate = q.getRatePerSqft() != null ? q.getRatePerSqft() : firstRate(q);
        BigDecimal amount = q.getTotalAmount() != null ? q.getTotalAmount() : BigDecimal.ZERO;
        String size = "";
        if (e != null && e.getSpanWidthM() != null && e.getLengthM() != null) {
            size = " \u2013 " + Math.round(e.getSpanWidthM() * 3.28084) + "X" + Math.round(e.getLengthM() * 3.28084);
        }

        Fonts f = c.f;
        PdfPTable t = new PdfPTable(5);
        t.addCell(span(c, "PRE-ENGINEERED STEEL BUILDING \u2013 A TYPE", 5, 22f, Color.WHITE, Color.BLACK));
        String[] heads = {"SL.NO", "PARTICULAR", "AREA", "RATE/SQFT", "AMOUNT(RS)"};
        for (String h : heads) {
            PdfPCell hc = cell(c, h, Element.ALIGN_CENTER);
            hc.setMinimumHeight(22f);
            t.addCell(hc);
        }
        PdfPCell sl = cell(c, "1", Element.ALIGN_CENTER);
        sl.setVerticalAlignment(Element.ALIGN_TOP);
        sl.setPaddingTop(F_PAD_TOP);
        sl.setMinimumHeight(179f);
        t.addCell(sl);

        PdfPCell part = new PdfPCell();
        part.setBorderWidth(0.6f);
        part.setPaddingLeft(2f);
        part.setPaddingTop(2f);
        part.addElement(new Paragraph("PRE-ENGINEERED STEEL BUILDING" + size, new Font(f.reg, 9f, Font.NORMAL, Color.BLACK)));
        part.addElement(new Paragraph(" ", new Font(f.reg, 9f)));
        Font box = new Font(Font.ZAPFDINGBATS, 7f, Font.NORMAL, Color.BLACK);
        String[] scope = {"Structural Design", "Structural Detailing", "Structural Fabrication",
                "Supply & Installation of structure", "Supply & Fixing of Roofing & cladding"};
        for (String sTxt : scope) {
            Paragraph p = new Paragraph(15f);
            p.setIndentationLeft(26f);
            p.add(new Chunk("o", box));
            p.add(new Chunk("   " + sTxt, new Font(f.reg, 9f, Font.NORMAL, Color.BLACK)));
            part.addElement(p);
        }
        t.addCell(part);

        PdfPCell ar = cell(c, "", Element.ALIGN_LEFT);
        ar.setPhrase(new Phrase(inr((long) Math.round(area)) + "\nSQ. FT", new Font(f.reg, 9f)));
        ar.setVerticalAlignment(Element.ALIGN_TOP);
        ar.setPaddingTop(F_PAD_TOP);
        t.addCell(ar);

        PdfPCell rt = new PdfPCell(rupee(c, rate == null ? "0" : plain(rate), 9f));
        rt.setBorderWidth(0.6f);
        rt.setPaddingTop(F_PAD_TOP);
        rt.setPaddingLeft(6f);
        t.addCell(rt);

        PdfPCell am = new PdfPCell(rupee(c, inr(amount) + "/-", 9f));
        am.setBorderWidth(0.6f);
        am.setPaddingTop(F_PAD_TOP);
        am.setPaddingLeft(8f);
        t.addCell(am);

        drawTable(c, t, 21, 673, 58, 321, 88, 71, 163);
    }

    private static final float F_PAD_TOP = 40f;

    // ================================================================== page 10 - optional, terms, bank

    private void termsPage(Ctx c, Enquiry e) throws Exception {
        pageFrame(c, 9, 224);
        float sz = c.body(10.2f);

        heading(c, "OPTIONAL", 21, 285, 12f);
        List<String> opt = new ArrayList<>();
        if (e != null && Boolean.TRUE.equals(e.getCraneRequired()) && e.getLengthM() != null) {
            long ft = Math.round(e.getLengthM() * 3.28084);
            opt.add("Crane Rail Girder for entire length " + ft + "+" + ft + " feet will be extra at actual.");
        }
        opt.add("Roof Ventilator will be charged at Rs.7500/- per piece.");
        opt.add("Doors & Windows will be charged at actual.");
        opt.add("Transport will be extra at actual.");
        String[] roman = {"i)", "ii)", "iii)", "iv)"};
        for (int i = 0; i < opt.size(); i++) {
            line(c, roman[i], 21, 307 + i * 24.4, sz, false);
            line(c, opt.get(i), 78, 307 + i * 24.4, sz, false);
        }

        heading(c, "1. Terms & Conditions:", 21, 437, 11f);
        paragraph(c, "GST 18% excluding; Taxes are subjected to change as levied by the Government. If there is any change in "
                + "GST Tax, Excise tariff or statutory levels imposed by the government at the time of shipment, such "
                + "additional charges to be borne by the Buyer.", 21, 473, 881, sz, 14.6f, true);

        heading(c, "2. Payment Terms:", 21, 587, 11f);
        String[] pay = {"\u2022 10% Advance along with Work order / Signed contract.",
                "\u2022 40% At the stage of approval of drawings or design report.",
                "\u2022 25% Against PEB Materials in-ward at site (Before Erection).",
                "\u2022 20% Against completion of Structural Erection (Before Sheeting).",
                "\u2022 05% Against completion of Sheet fixing."};
        for (int i = 0; i < pay.length; i++) line(c, pay[i], 21, 610 + i * 25.4, sz - 0.4f, false);

        heading(c, "3. Bank Details", 47, 745, 11f);
        String[][] bank = {{"Beneficiary Name", ": Senela International Ventures Pvt Ltd"}, {"Bank Name", ": ICICI Bank"},
                {"Account Number", ": 603705020036"}, {"IFSC Code", ": ICIC0006037"}, {"Branch", ": ICICI Bank Limited"}};
        for (int i = 0; i < bank.length; i++) {
            line(c, bank[i][0], 27, 788 + i * 22.4, sz, false);
            line(c, bank[i][1], 202, 788 + i * 22.4, sz, false);
        }
        line(c, "236, Velachery Main Road,", 213, 910, sz, false);
        line(c, "Selaiyur, Chennai \u2013 600073", 213, 935, sz, false);

        heading(c, "Section 8. Assumption, Deviations & Exclusions:", 21, 992, 10.5f);
        paragraph(c, "1. Items and codes which are not mentioned in this proposal are not in Senela International Scope of supply.",
                17, 1045, 840, sz, 15.5f, false);
        line(c, "2. It is the customer\u2019s responsibility to verify that the specified loads are adequate for the building use.", 17, 1087, sz, false);
    }

    // ================================================================== page 11 - closing

    private void closingPage(Ctx c) throws Exception {
        pageFrame(c, 10, 224);
        float sz = c.body(10.2f);

        line(c, "3. Electricity 3 phase supply and water to be provided free of cost at site during the erection of structures.", 21, 273, sz, false);
        line(c, "4. Force Majeure Clause will be applicable.", 21, 298, sz, false);
        line(c, "5. Building measurement should be taken O/O of metal sheet line only.", 21, 325, sz, false);

        heading(c, "Section 9. Force Majeure Clause:", 27, 386, 10.5f);
        line(c, "Standard Force Majeure Clause will be applicable.", 27, 404, sz, false);
        paragraph(c, "SELLER shall not be liable for any loss or damage to BUYER for delay in delivery or non - Delivery, but not "
                + "limited to war riots, civil commotion, insurrections, revolution, civil war, regulations, orders or acts of any "
                + "authority directly or indirectly interfering more burdensome the production or delivery of the products, "
                + "floods fires weather conditions, factory Pg shutdown or alterations, embargoes, delays in or shortage of "
                + "transportation or inability to obtain labor power failure and any other circumstance or event Beyond The "
                + "Control Of The Seller.", 27, 433, 881, sz, 13.2f, true);

        heading(c, "Governing Law:", 21, 578, 10.5f);
        paragraph(c, "This Agreement shall be construed and endorsed in accordance with and under the laws of the Republic of "
                + "India. Both parties agree that the Indian Courts shall be the competent ones to hear any dispute arising out "
                + "of or in connection with this Agreement. Despite the foregoing or any other future understanding or "
                + "Agreement wherever it is provided for SELLER shall also have the right to take legal action and proceedings "
                + "before the courts of the BUYER\u2019s place of business or any other jurisdiction where BUYER may have "
                + "property or assets.", 21, 601, 881, sz, 13.2f, true);

        line(c, "We thank you for your enquiry and look forward to receiving your valuable purchase order.", 27, 737, sz, false);
        line(c, "For SENELA INTERNATIONAL VENTURES PVT LTD", 35, 790, sz, false);
        signature(c, 30, 806, 390);
        line(c, "Authorized Signatory", 35, 993, sz, false);
    }

    // ================================================================== data -> table rows

    private List<String[]> buildingRows(Enquiry e, Map<String, Object> x) {
        Double w = e == null ? null : e.getSpanWidthM();
        Double l = e == null ? null : e.getLengthM();
        Double eave = e == null ? null : e.getEaveHeightM();
        String type = e == null ? null : e.getBuildingType();

        List<String[]> r = new ArrayList<>();
        r.add(new String[]{"Building type", orNa(buildingTypeLabel(e))});
        r.add(new String[]{"Length in m (o/o of steel)", dim(l)});
        r.add(new String[]{"Width in m (o/o of steel)", dim(w)});
        r.add(new String[]{"Left eave height in m", dim(eave)});
        r.add(new String[]{"Right eave height in m", dim(eave)});
        r.add(new String[]{"Ridge line distance", w == null ? NA : dim("SINGLE_SLOPE".equals(type) || "LEAN_TO".equals(type) ? w : w / 2.0)});
        r.add(new String[]{"Roof slope (ratio)", e == null ? NA : orNa(e.getRoofSlope())});
        String endBay = bays(w, e == null ? null : e.getEndBaySpacingM());
        r.add(new String[]{"Left end wall bay spacing in mtr", endBay});
        r.add(new String[]{"Right end wall bay spacing in mtr", endBay});
        r.add(new String[]{"Side wall bay spacing in mtr", bays(l, e == null ? null : e.getIntermediateBaySpacingM())});
        r.add(new String[]{"Wall bracing type", "Rod"});
        r.add(new String[]{"Roof bracing type", "Rod"});
        r.add(new String[]{"Roof sheeting", sheeting(e == null ? null : e.getRoofCladdingThickness(),
                e == null ? null : firstNonBlank(e.getMainShedRoofMaterial(), e.getRoofCladdingMaterial()),
                e == null ? null : e.getMainShedRoofMaterialOther())});
        r.add(new String[]{"Wall sheeting", sheeting(e == null ? null : e.getSideCladdingThickness(),
                e == null ? null : e.getSideCladdingMaterial(), null)});
        r.add(new String[]{"Base Elevation", "As per civil work"});
        r.add(new String[]{"Braced bays (Bay id)", BY_DESIGN});
        boolean block = e != null && e.getBrickWallHeightM() != null;
        r.add(new String[]{"Block wall in mtr (All round of building)", block ? fmt3(e.getBrickWallHeightM()) + " (not in Senela\u2019s scope)" : NA});
        r.add(new String[]{"Block wall thickness in mtr", block ? "0.23" : NA});
        r.add(new String[]{"Rigid frames interior column locations", interiorColumns(type, w)});
        return r;
    }

    private List<String[]> canopyRows(Map<String, Object> x) {
        boolean canopy = "yes".equalsIgnoreCase(str(x, "canopy"));
        String size = canopy ? orNa(str(x, "canopySize")) : NA;
        String height = canopy ? orNa(str(x, "canopyHeightFfl")) : NA;
        List<String[]> r = new ArrayList<>();
        r.add(new String[]{"Canopy Extension in m", size});
        r.add(new String[]{"Slope", canopy ? BY_DESIGN : NA});
        r.add(new String[]{"Canopy clear height in m", height});
        r.add(new String[]{"Eave extension in m", NA});
        r.add(new String[]{"Gable extension in m", NA});
        return r;
    }

    private List<String[]> openingRows(Enquiry e, Map<String, Object> x) {
        boolean sky = e != null && Boolean.TRUE.equals(e.getSkylightRequired());
        boolean vent = "yes".equalsIgnoreCase(str(x, "roofVent")) || (e != null && Boolean.TRUE.equals(e.getRoofVentilatorRequired()));
        String throat = str(x, "roofVentThroat");
        String ventLabel = "Turbo Ventilators (" + (throat == null || throat.isBlank() ? "600" : throat.trim()) + " mm throat diameter)";
        String qty = str(x, "roofVentQty");
        String details = str(x, "openingsDetails");

        List<String[]> r = new ArrayList<>();
        r.add(new String[]{"Skylights (FRP or Poly carbonate 3.25m x 1m)", sky ? BY_DESIGN : NA});
        r.add(new String[]{ventLabel, vent ? (qty == null || qty.isBlank() ? BY_DESIGN : qty.trim()) : NA});
        r.add(new String[]{"Windows size", details == null || details.isBlank() ? NA : details.trim()});
        r.add(new String[]{"Door size", details == null || details.isBlank() ? NA : "As mentioned above"});
        return r;
    }

    private String[] craneValues(Enquiry e) {
        boolean crane = e != null && Boolean.TRUE.equals(e.getCraneRequired());
        String[] v = new String[13];
        java.util.Arrays.fill(v, NA);
        if (!crane) return v;
        v[0] = capacity(e.getCraneCapacityTonnes());
        v[1] = orBy(e.getCraneHeightM());
        v[2] = BY_DESIGN;
        v[3] = "0";
        v[4] = e.getLengthM() != null ? fmt3(e.getLengthM()) : BY_DESIGN;
        v[5] = BY_DESIGN;
        v[6] = orBy(e.getCraneType());
        for (int i = 7; i < 13; i++) v[i] = BY_DESIGN;
        return v;
    }

    private static String capacity(String tonnes) {
        if (tonnes == null || tonnes.isBlank()) return BY_DESIGN;
        try {
            double t = Double.parseDouble(tonnes.replaceAll("[^0-9.]", ""));
            return fmtTrim(t * 10) + " (" + fmtTrim(t) + " Tons)";
        } catch (NumberFormatException ex) {
            return tonnes.trim();
        }
    }

    private static String buildingTypeLabel(Enquiry e) {
        if (e == null || e.getBuildingType() == null) return null;
        switch (e.getBuildingType()) {
            case "CLEAR_SPAN": return "Clear Span";
            case "CLEAR_SPAN_ARCHED": return "Clear Span (Arched)";
            case "MULTI_SPAN_1": return "Multi Span-1";
            case "MULTI_SPAN_2": return "Multi Span-2";
            case "MULTI_SPAN_3": return "Multi Span-3";
            case "MULTI_SPAN_ARCHED": return "Multi Span (Arched)";
            case "MULTI_GABLE": return "Multi Gable";
            case "SINGLE_SLOPE": return "Single Slope";
            case "ROOF_SYSTEM": return "Roof System";
            case "LEAN_TO": return "Lean-To";
            case "OTHER": return e.getBuildingTypeOther();
            default: return e.getBuildingType();
        }
    }

    private static String sheeting(String thickness, String material, String other) {
        String m = null;
        if (material == null || material.isBlank()) m = null;
        else switch (material) {
            case "CCGI": m = "Color Coated GI"; break;
            case "CCGL": m = "Color Coated Galvalume"; break;
            case "BAREGALVALUME": m = "Bare Galvalume"; break;
            case "OTHER": m = other; break;
            default: m = material;
        }
        String t = thickness == null ? "" : thickness.trim();
        String out = (t + " " + (m == null ? "" : m)).trim();
        return out.isEmpty() ? NA : out;
    }

    private static String interiorColumns(String type, Double w) {
        if (type == null || w == null) return NA;
        switch (type) {
            case "MULTI_SPAN_1": return fmt3(w / 2);
            case "MULTI_SPAN_2": return fmt3(w / 3) + ", " + fmt3(2 * w / 3);
            case "MULTI_SPAN_3": return fmt3(w / 4) + ", " + fmt3(w / 2) + ", " + fmt3(3 * w / 4);
            default: return NA;
        }
    }

    /** "8@7.620" - number of bays and bay width, as in the sample. */
    private static String bays(Double total, Double spacing) {
        if (total == null || spacing == null || spacing <= 0) return NA;
        long n = Math.max(1, Math.round(total / spacing));
        return n + "@" + String.format(java.util.Locale.ROOT, "%.3f", spacing);
    }

    private static String dim(Double m) {
        if (m == null) return NA;
        return fmt3(m) + " mtr (" + Math.round(m * 3.28084) + " ft)";
    }

    private static String fmt3(double v) {
        return new BigDecimal(v).setScale(3, RoundingMode.HALF_UP).stripTrailingZeros().toPlainString();
    }

    private static String fmtTrim(double v) {
        return new BigDecimal(v).setScale(2, RoundingMode.HALF_UP).stripTrailingZeros().toPlainString();
    }

    private static String orNa(String s) {
        return s == null || s.isBlank() ? NA : s.trim();
    }

    private static String orBy(String s) {
        return s == null || s.isBlank() ? BY_DESIGN : s.trim();
    }

    private static String str(Map<String, Object> m, String k) {
        Object o = m.get(k);
        return o == null ? null : String.valueOf(o);
    }

    private static String firstNonBlank(String... v) {
        for (String s : v) if (s != null && !s.isBlank()) return s.trim();
        return null;
    }

    private static Map<String, Object> parseExtra(Enquiry e) {
        if (e == null || e.getExtraDetails() == null || e.getExtraDetails().isBlank()) return new HashMap<>();
        try {
            return JSON.readValue(e.getExtraDetails(), new TypeReference<Map<String, Object>>() {});
        } catch (Exception ex) {
            return new HashMap<>();
        }
    }

    private static double firstQty(Quotation q) {
        List<QuotationItem> items = q.getItems();
        return items != null && !items.isEmpty() && items.get(0).getQuantity() != null ? items.get(0).getQuantity() : 0d;
    }

    private static BigDecimal firstRate(Quotation q) {
        List<QuotationItem> items = q.getItems();
        return items != null && !items.isEmpty() ? items.get(0).getUnitPrice() : null;
    }

    private static String refText(Quotation q) {
        String no = q.getQuotationNumber();
        String[] p = no.split("-");
        String tail = p.length >= 3 ? p[1] + "/" + p[2] : no;
        return "SIVPL/PEB: Ref: No:/" + tail;
    }

    private static String dateText(Quotation q, Lead lead) {
        LocalDate d = lead.getQuotationDate() != null ? lead.getQuotationDate()
                : (q.getCreatedAt() != null ? q.getCreatedAt().toLocalDate() : LocalDate.now());
        return d.format(DateTimeFormatter.ofPattern("dd/MM/yyyy"));
    }

    // ------------------------------------------------------------------ number formatting

    private static String plain(BigDecimal v) {
        return v.stripTrailingZeros().toPlainString();
    }

    private static String inr(BigDecimal v) {
        BigDecimal r = v.setScale(2, RoundingMode.HALF_UP);
        String s = inr(r.longValue());
        BigDecimal frac = r.remainder(BigDecimal.ONE).abs();
        return frac.signum() == 0 ? s : s + String.format(java.util.Locale.ROOT, ".%02d", frac.movePointRight(2).intValue());
    }

    /** Indian digit grouping: 5820000 -> 58,20,000 */
    private static String inr(long n) {
        String s = String.valueOf(Math.abs(n));
        if (s.length() <= 3) return (n < 0 ? "-" : "") + s;
        String last3 = s.substring(s.length() - 3);
        String rest = s.substring(0, s.length() - 3);
        StringBuilder sb = new StringBuilder();
        while (rest.length() > 2) {
            sb.insert(0, "," + rest.substring(rest.length() - 2));
            rest = rest.substring(0, rest.length() - 2);
        }
        sb.insert(0, rest);
        return (n < 0 ? "-" : "") + sb + "," + last3;
    }

    // ================================================================== drawing helpers

    /** Per-document drawing context. */
    private static final class Ctx {
        final Fonts f;
        final PdfContentByte cb;
        final String ref;
        final String date;

        Ctx(Fonts f, PdfContentByte cb, String ref, String date) {
            this.f = f;
            this.cb = cb;
            this.ref = ref;
            this.date = date;
        }

        float body(float base) {
            return base * f.scale;
        }

        void underline(float x1, float y, float x2) {
            cb.saveState();
            cb.setLineWidth(0.6f);
            cb.setColorStroke(Color.BLACK);
            cb.moveTo(x1, y);
            cb.lineTo(x2, y);
            cb.stroke();
            cb.restoreState();
        }
    }

    /** Header (logo, company name, gold line, page badge), ref/date line and footer for pages 2-11. */
    private void pageFrame(Ctx c, int badge, double refCenterPx) throws Exception {
        drawLogoAndName(c);
        drawHeaderLine(c, 139);

        PdfContentByte cb = c.cb;
        cb.saveState();
        cb.setColorFill(BADGE_FILL);
        cb.setColorStroke(BADGE_LINE);
        cb.setLineWidth(0.6f);
        cb.roundRectangle(X(38), Y(172), X(66), (float) (28 * K), 3f);
        cb.fillStroke();
        cb.restoreState();
        text(cb, c.f.reg, 9f, Color.WHITE, X(97), Y(163), String.valueOf(badge), PdfContentByte.ALIGN_RIGHT);

        float sz = c.body(10f);
        line(c, c.ref, 21, refCenterPx, sz, false);
        rightLine(c, "Date: " + c.date, 865, refCenterPx, sz);

        footer(c, FOOTER, FOOTER_PX);
    }

    private void drawLogoAndName(Ctx c) throws Exception {
        PdfContentByte cb = c.cb;
        Image logo = Image.getInstance(resource("branding/logo.png"));
        logo.scaleAbsolute(X(95), X(95));
        logo.setAbsolutePosition(X(37), Y(128));
        cb.addImage(logo);

        float target = X(653);
        float size = 14f;
        float natural = c.f.reg.getWidthPoint(COMPANY_NAME, size);
        float spacing = (target - natural) / (COMPANY_NAME.length() - 1);
        cb.beginText();
        cb.setFontAndSize(c.f.reg, size);
        cb.setColorFill(NAME_BROWN);
        cb.setCharacterSpacing(spacing);
        cb.setTextMatrix(X(143), Y(91));
        cb.showText(COMPANY_NAME);
        cb.setCharacterSpacing(0);
        cb.endText();
    }

    private void drawHeaderLine(Ctx c, double yPx) throws Exception {
        Image line = Image.getInstance(resource("branding/header-line.png"));
        line.scaleAbsolute(X(840), X(10));
        line.setAbsolutePosition(X(25), Y(yPx + 5));
        c.cb.addImage(line);
    }

    private void footer(Ctx c, String[] lines, double[][] px) throws Exception {
        Image iso = Image.getInstance(resource("branding/iso.png"));
        iso.scaleAbsolute(X(208), X(208) * 97f / 227f);
        iso.setAbsolutePosition(X(37), Y(1229));
        c.cb.addImage(iso);
        double base = 1157;
        for (int i = 0; i < lines.length; i++) {
            float target = X(px[i][1] - px[i][0]);
            float size = Math.min(8.6f, fit(c.f.reg, lines[i], target));
            text(c.cb, c.f.reg, size, Color.BLACK, X(px[i][0]), Y(base + i * 22.4), lines[i], PdfContentByte.ALIGN_LEFT);
        }
    }

    private void signature(Ctx c, double xPx, double topPx, double widthPx) throws Exception {
        Image sig = Image.getInstance(resource("branding/signature.png"));
        float w = X(widthPx);
        float h = w * 176f / 426f;
        sig.scaleAbsolute(w, h);
        sig.setAbsolutePosition(X(xPx), Y(topPx) - h);
        c.cb.addImage(sig);
    }

    /** One line of text; centerPx is the vertical centre of the line in sample pixels. */
    private void line(Ctx c, String s, double xPx, double centerPx, float size, boolean bold) {
        text(c.cb, bold ? c.f.bold : c.f.reg, size, Color.BLACK, X(xPx), baseline(centerPx, size), s, PdfContentByte.ALIGN_LEFT);
    }

    private void rightLine(Ctx c, String s, double xPx, double centerPx, float size) {
        text(c.cb, c.f.reg, size, Color.BLACK, X(xPx), baseline(centerPx, size), s, PdfContentByte.ALIGN_RIGHT);
    }

    private static float baseline(double centerPx, float size) {
        return Y(centerPx + 0.31 * size / K);
    }

    /** Yellow-highlighted section heading (as in the sample); baselinePx is the text baseline. */
    private void heading(Ctx c, String s, double xPx, double baselinePx, float size) {
        float x = X(xPx);
        float y = Y(baselinePx);
        float w = c.f.reg.getWidthPoint(s, size);
        PdfContentByte cb = c.cb;
        cb.saveState();
        cb.setColorFill(Color.YELLOW);
        cb.rectangle(x - 1.5f, y - size * 0.26f, w + 4f, size * 1.2f);
        cb.fill();
        cb.restoreState();
        text(cb, c.f.reg, size, Color.BLACK, x, y, s, PdfContentByte.ALIGN_LEFT);
    }

    /** Wrapped paragraph; firstBaselinePx is the baseline of its first line. */
    private void paragraph(Ctx c, String s, double xPx, double firstBaselinePx, double rightPx, float size, float leading, boolean justify)
            throws DocumentException {
        ColumnText ct = new ColumnText(c.cb);
        Paragraph p = new Paragraph(leading, s, new Font(c.f.reg, size, Font.NORMAL, Color.BLACK));
        p.setAlignment(justify ? Element.ALIGN_JUSTIFIED : Element.ALIGN_LEFT);
        ct.setSimpleColumn(X(xPx), Y(1125), X(rightPx), Y(firstBaselinePx) + leading);
        ct.addElement(p);
        ct.go();
    }

    private static void text(PdfContentByte cb, BaseFont bf, float size, Color color, float x, float y, String s, int align) {
        cb.beginText();
        cb.setFontAndSize(bf, size);
        cb.setColorFill(color);
        cb.showTextAligned(align, s, x, y, 0);
        cb.endText();
    }

    /** Font size at which s is exactly targetWidth points wide. */
    private static float fit(BaseFont bf, String s, float targetWidth) {
        float w = bf.getWidthPoint(s, 10f);
        return w <= 0 ? 10f : 10f * targetWidth / w;
    }

    // ------------------------------------------------------------------ tables

    private PdfPCell cell(Ctx c, String s, int align) {
        PdfPCell cell = new PdfPCell(new Phrase(s, new Font(c.f.reg, 8.6f, Font.NORMAL, Color.BLACK)));
        cell.setBorderWidth(0.6f);
        cell.setBorderColor(Color.BLACK);
        cell.setHorizontalAlignment(align);
        cell.setVerticalAlignment(Element.ALIGN_MIDDLE);
        cell.setPaddingLeft(4f);
        cell.setPaddingRight(3f);
        cell.setPaddingTop(1f);
        cell.setPaddingBottom(3f);
        cell.setMinimumHeight(15.2f);
        return cell;
    }

    /** Column-header cell: blue with white centred text. */
    private PdfPCell hdr(Ctx c, String s, boolean unused) {
        PdfPCell cell = new PdfPCell(new Phrase(s, new Font(c.f.reg, 8.6f, Font.NORMAL, Color.WHITE)));
        cell.setBackgroundColor(TABLE_BLUE);
        cell.setBorderWidth(0.6f);
        cell.setBorderColor(Color.BLACK);
        cell.setHorizontalAlignment(Element.ALIGN_LEFT);
        cell.setVerticalAlignment(Element.ALIGN_MIDDLE);
        cell.setPaddingLeft(4f);
        cell.setPaddingTop(1f);
        cell.setPaddingBottom(3f);
        cell.setMinimumHeight(15.2f);
        return cell;
    }

    private PdfPCell span(Ctx c, String s, int cols, float minHeight) {
        return span(c, s, cols, minHeight, TABLE_BLUE, Color.WHITE);
    }

    private PdfPCell span(Ctx c, String s, int cols, float minHeight, Color bg, Color fg) {
        PdfPCell cell = new PdfPCell(new Phrase(s, new Font(c.f.reg, 8.8f, Font.NORMAL, fg)));
        cell.setColspan(cols);
        cell.setBackgroundColor(bg);
        cell.setBorderWidth(0.6f);
        cell.setBorderColor(Color.BLACK);
        cell.setHorizontalAlignment(Element.ALIGN_CENTER);
        cell.setVerticalAlignment(Element.ALIGN_MIDDLE);
        cell.setPaddingTop(1f);
        cell.setPaddingBottom(3f);
        cell.setMinimumHeight(minHeight);
        return cell;
    }

    /** A blue column-header row followed by numbered fixed rows. */
    private void fixedBlock(Ctx c, PdfPTable t, String[] head, String[][] rows) {
        for (String h : head) {
            PdfPCell hc = hdr(c, h, false);
            hc.setHorizontalAlignment(Element.ALIGN_CENTER);
            t.addCell(hc);
        }
        int n = 1;
        for (String[] r : rows) {
            t.addCell(cell(c, String.valueOf(n++), Element.ALIGN_LEFT));
            t.addCell(cell(c, r[0], Element.ALIGN_LEFT));
            t.addCell(cell(c, r[1], Element.ALIGN_LEFT));
        }
    }

    private void drawTable(Ctx c, PdfPTable t, double xPx, double topPx, double... colPx) throws DocumentException {
        float[] w = new float[colPx.length];
        for (int i = 0; i < w.length; i++) w[i] = (float) (colPx[i] * K);
        t.setTotalWidth(w);
        t.setLockedWidth(true);
        t.writeSelectedRows(0, -1, X(xPx), Y(topPx), c.cb);
    }

    /** "Rs" amount: rupee sign (DejaVu, has the glyph) followed by the number. */
    private Phrase rupee(Ctx c, String amount, float size) {
        Phrase p = new Phrase();
        p.add(new Chunk("\u20B9", new Font(c.f.rupee, size, Font.NORMAL, Color.BLACK)));
        p.add(new Chunk(amount, new Font(c.f.reg, size, Font.NORMAL, Color.BLACK)));
        return p;
    }

    // ================================================================== resources & fonts

    private static byte[] resource(String path) throws IOException {
        byte[] cached = RESOURCES.get(path);
        if (cached != null) return cached;
        try (InputStream in = new ClassPathResource(path).getInputStream()) {
            byte[] bytes = in.readAllBytes();
            RESOURCES.put(path, bytes);
            return bytes;
        }
    }

    private static BaseFont loadFont(String path) throws IOException, DocumentException {
        return BaseFont.createFont(path, BaseFont.IDENTITY_H, BaseFont.EMBEDDED, true, resource(path), null);
    }

    private static Fonts fonts() throws Exception {
        Fonts f = fonts;
        if (f == null) {
            synchronized (PdfService.class) {
                if (fonts == null) fonts = new Fonts();
                f = fonts;
            }
        }
        return f;
    }

    /**
     * Fonts used on the pages. Body text is Liberation Sans (metric-compatible with the Arimo used in the sample
     * tables). To use Open Sans like the original letter text, drop OpenSans-Regular.ttf into
     * src/main/resources/fonts/ - it is picked up automatically. DejaVu Sans is only used for the rupee sign.
     */
    private static final class Fonts {
        final BaseFont reg;
        final BaseFont bold;
        final BaseFont rupee;
        final float scale;

        Fonts() throws IOException, DocumentException {
            boolean openSans = new ClassPathResource("fonts/OpenSans-Regular.ttf").exists();
            reg = loadFont(openSans ? "fonts/OpenSans-Regular.ttf" : "fonts/LiberationSans-Regular.ttf");
            bold = loadFont(new ClassPathResource("fonts/OpenSans-Bold.ttf").exists() && openSans
                    ? "fonts/OpenSans-Bold.ttf" : "fonts/LiberationSans-Bold.ttf");
            rupee = loadFont("fonts/DejaVuSans.ttf");
            scale = openSans ? 1.0f : 1.08f;
        }
    }
}
