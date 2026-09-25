package com.leadquote.controller;

import com.leadquote.dto.CompanyDocumentContentResponse;
import com.leadquote.dto.CompanyDocumentResponse;
import com.leadquote.dto.CompanyDocumentUploadRequest;
import com.leadquote.service.CompanyDocumentService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/documents")
@RequiredArgsConstructor
public class CompanyDocumentController {

    private final CompanyDocumentService documentService;

    @GetMapping
    public List<CompanyDocumentResponse> list() {
        return documentService.list();
    }

    @PostMapping
    public CompanyDocumentResponse upload(@Valid @RequestBody CompanyDocumentUploadRequest request) {
        return documentService.upload(request);
    }

    @GetMapping("/{id}/content")
    public CompanyDocumentContentResponse content(@PathVariable Long id) {
        return documentService.content(id);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id) {
        documentService.delete(id);
    }
}
