package com.leadquote.service;

import com.leadquote.dto.EnquirySubmitRequest;
import com.leadquote.entity.Enquiry;
import com.leadquote.entity.Lead;
import com.leadquote.entity.LeadStatus;
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
                .build();

        Enquiry saved = enquiryRepository.save(enquiry);

        lead.setEnquiry(saved);
        lead.setStatus(LeadStatus.FORM_SUBMITTED);

        auditService.log("Enquiry", saved.getId(), "FORM_SUBMITTED", "Enquiry submitted for lead " + lead.getLeadCode());

        return saved;
    }
}
