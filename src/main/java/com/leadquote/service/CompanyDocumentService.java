package com.leadquote.service;

import com.leadquote.dto.CompanyDocumentContentResponse;
import com.leadquote.dto.CompanyDocumentResponse;
import com.leadquote.dto.CompanyDocumentUploadRequest;
import com.leadquote.entity.CompanyDocument;
import com.leadquote.exception.NotFoundException;
import com.leadquote.repository.CompanyDocumentRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.Base64;
import java.util.List;
import java.util.Set;

/** Backs the "Certificates & Documents" page: upload, list, view and delete company files. */
@Service
@RequiredArgsConstructor
public class CompanyDocumentService {

    /** 10 MB per file. */
    private static final long MAX_BYTES = 10L * 1024 * 1024;

    private static final Set<String> ALLOWED_TYPES = Set.of(
            "application/pdf",
            "image/jpeg", "image/png", "image/webp",
            "application/msword",
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
            "application/vnd.ms-excel",
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");

    private final CompanyDocumentRepository repository;

    public List<CompanyDocumentResponse> list() {
        return repository.findAllMetadata();
    }

    @Transactional
    public CompanyDocumentResponse upload(CompanyDocumentUploadRequest req) {
        if (!ALLOWED_TYPES.contains(req.getContentType())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Only PDF, JPG, PNG, WEBP, Word or Excel files can be uploaded.");
        }
        byte[] bytes;
        try {
            bytes = Base64.getDecoder().decode(req.getDataBase64());
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "The file could not be read.");
        }
        if (bytes.length == 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "The file is empty.");
        }
        if (bytes.length > MAX_BYTES) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Files must be 10 MB or smaller.");
        }

        CompanyDocument saved = repository.save(CompanyDocument.builder()
                .category(req.getCategory().trim())
                .docType(req.getDocType().trim())
                .fileName(req.getFileName().trim())
                .contentType(req.getContentType())
                .sizeBytes((long) bytes.length)
                .data(bytes)
                .build());
        return toResponse(saved);
    }

    @Transactional(readOnly = true)
    public CompanyDocumentContentResponse content(Long id) {
        CompanyDocument d = find(id);
        return new CompanyDocumentContentResponse(d.getId(), d.getFileName(), d.getContentType(),
                Base64.getEncoder().encodeToString(d.getData()));
    }

    @Transactional
    public void delete(Long id) {
        repository.delete(find(id));
    }

    private CompanyDocument find(Long id) {
        return repository.findById(id).orElseThrow(() -> new NotFoundException("Document not found: " + id));
    }

    private CompanyDocumentResponse toResponse(CompanyDocument d) {
        return new CompanyDocumentResponse(d.getId(), d.getCategory(), d.getDocType(), d.getFileName(),
                d.getContentType(), d.getSizeBytes(), d.getUploadedAt());
    }
}
