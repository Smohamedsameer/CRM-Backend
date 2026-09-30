package com.leadquote.service;

import com.leadquote.dto.EnquirySubmitRequest;
import com.leadquote.entity.Enquiry;
import com.leadquote.entity.Lead;
import com.leadquote.entity.LeadStatus;
import com.leadquote.entity.NotificationType;
import com.leadquote.repository.EnquiryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class EnquiryService {

    private final EnquiryRepository enquiryRepository;
    private final LeadService leadService;
    private final AuditService auditService;
    private final NotificationService notificationService;

    @Transactional
    public Enquiry submit(EnquirySubmitRequest request) {
        Lead lead = leadService.getByEnquiryToken(request.getToken());

        Enquiry enquiry = Enquiry.builder()
                .lead(lead)
                .requirement(request.getRequirement())
                .productOrService(request.getProductOrService())
                .quantity(request.getQuantity())
                .location(request.getLocation())
                .requiredDate(request.getRequiredDate())
                .specifications(request.getSpecifications())
                .materialPreference(request.getMaterialPreference())
                .additionalRequirements(request.getAdditionalRequirements())
                .remarks(request.getRemarks())
                .buildingType(request.getBuildingType())
                .buildingTypeOther(request.getBuildingTypeOther())
                .noOfSheds(request.getNoOfSheds())
                .endFrameType(request.getEndFrameType())
                .columnType(request.getColumnType())
                .spanWidthM(request.getSpanWidthM())
                .lengthM(request.getLengthM())
                .clearEaveHeightM(request.getClearEaveHeightM())
                .eaveHeightM(request.getEaveHeightM())
                .roofCladdingMaterial(request.getRoofCladdingMaterial())
                .roofCladdingThickness(request.getRoofCladdingThickness())
                .roofCladdingColour(request.getRoofCladdingColour())
                .sideCladdingMaterial(request.getSideCladdingMaterial())
                .sideCladdingThickness(request.getSideCladdingThickness())
                .sideCladdingColour(request.getSideCladdingColour())
                .craneRequired(request.getCraneRequired())
                .craneType(request.getCraneType())
                .craneCapacityTonnes(request.getCraneCapacityTonnes())
                .craneHeightM(request.getCraneHeightM())
                .scopeOfWork(request.getScopeOfWork())
                .scopeOfWorkLocation(request.getScopeOfWorkLocation())
                .widthMeasurementBasis(request.getWidthMeasurementBasis())
                .lengthMeasurementBasis(request.getLengthMeasurementBasis())
                .intermediateBaySpacingM(request.getIntermediateBaySpacingM())
                .endBaySpacingM(request.getEndBaySpacingM())
                .brickWallHeightM(request.getBrickWallHeightM())
                .roofSlope(request.getRoofSlope())
                .roofingCladdingRequirement(request.getRoofingCladdingRequirement())
                .mainShedRoofMaterial(request.getMainShedRoofMaterial())
                .mainShedRoofMaterialOther(request.getMainShedRoofMaterialOther())
                .surfacePreparation(request.getSurfacePreparation())
                .protectiveCoating(request.getProtectiveCoating())
                .protectiveCoatingOther(request.getProtectiveCoatingOther())
                .skylightRequired(request.getSkylightRequired())
                .roofVentilatorRequired(request.getRoofVentilatorRequired())
                .roofVentilatorDiameterThroat(request.getRoofVentilatorDiameterThroat())
                .roofVentilatorAirChangePerHr(request.getRoofVentilatorAirChangePerHr())
                .roofVentilatorQuantity(request.getRoofVentilatorQuantity())
                .ridgeVentRequired(request.getRidgeVentRequired())
                .ridgeVentSize(request.getRidgeVentSize())
                .ridgeVentQuantity(request.getRidgeVentQuantity())
                .craneSize(request.getCraneSize())
                .roofStyle(request.getRoofStyle())
                .gutterTypes(request.getGutterTypes())
                .gutterMaterial(request.getGutterMaterial())
                .gutterMaterialOther(request.getGutterMaterialOther())
                .downtakePipesRequired(request.getDowntakePipesRequired())
                .downtakePipeMaterial(request.getDowntakePipeMaterial())
                .downtakePipeMaterialOther(request.getDowntakePipeMaterialOther())
                .canopyRequired(request.getCanopyRequired())
                .canopySizeOo(request.getCanopySizeOo())
                .canopyHeightFfl(request.getCanopyHeightFfl())
                .sheetMetalLouverRequired(request.getSheetMetalLouverRequired())
                .sheetMetalLouverSizeOo(request.getSheetMetalLouverSizeOo())
                .roadTrailerAccess(request.getRoadTrailerAccess())
                .roadGoodsStockingDetail(request.getRoadGoodsStockingDetail())
                .roadShutterProvisionDetail(request.getRoadShutterProvisionDetail())
                .projectPeriod(request.getProjectPeriod())
                .declarantName(request.getDeclarantName())
                .declarantDesignation(request.getDeclarantDesignation())
                .declarantAddress(request.getDeclarantAddress())
                .declarantSignSeal(request.getDeclarantSignSeal())
                .build();

        Enquiry saved = enquiryRepository.save(enquiry);

        lead.setEnquiry(saved);
        lead.setStatus(LeadStatus.FORM_SUBMITTED);

        auditService.log("Enquiry", saved.getId(), "FORM_SUBMITTED", "Enquiry submitted for lead " + lead.getLeadCode());

        notificationService.create(
                NotificationType.ENQUIRY_SUBMITTED,
                "New enquiry submitted",
                lead.getCustomerName() + " (" + lead.getLeadCode() + ") submitted the enquiry form",
                "Lead", lead.getId(), "/leads/" + lead.getId());

        return saved;
    }
}
