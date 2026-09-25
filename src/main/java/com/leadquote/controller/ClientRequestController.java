package com.leadquote.controller;

import com.leadquote.dto.ClientRequestReplyRequest;
import com.leadquote.dto.ClientRequestResponse;
import com.leadquote.service.ClientRequestService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/client-requests")
@RequiredArgsConstructor
public class ClientRequestController {

    private final ClientRequestService clientRequestService;

    @GetMapping
    public List<ClientRequestResponse> list() {
        return clientRequestService.list();
    }

    @PostMapping("/{id}/reply")
    public ClientRequestResponse reply(@PathVariable Long id, @Valid @RequestBody ClientRequestReplyRequest request) {
        return clientRequestService.reply(id, request.getMessage());
    }
}
