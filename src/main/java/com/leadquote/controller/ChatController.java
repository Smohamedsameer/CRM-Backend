package com.leadquote.controller;

import com.leadquote.dto.ChatDtos.ConversationDto;
import com.leadquote.dto.ChatDtos.MessageDto;
import com.leadquote.dto.ChatDtos.SendRequest;
import com.leadquote.dto.ChatDtos.ThreadDto;
import com.leadquote.entity.Employee;
import com.leadquote.entity.EmployeeRole;
import com.leadquote.repository.EmployeeRepository;
import com.leadquote.service.ChatService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.nio.charset.StandardCharsets;

import java.util.List;
import java.util.Map;

/** Employee-facing WhatsApp chat (requires login like every other /api route). */
@RestController
@RequestMapping("/api/chat")
@RequiredArgsConstructor
public class ChatController {

    private final ChatService chatService;
    private final EmployeeRepository employeeRepository;

    /** Left-hand list: every lead that has any WhatsApp message, newest conversation first. */
    @GetMapping("/conversations")
    public List<ConversationDto> conversations() {
        return chatService.conversations();
    }

    /** Number of unread customer messages, for the sidebar badge. */
    @GetMapping("/unread-count")
    public Map<String, Long> unreadCount() {
        return Map.of("count", chatService.unreadCount());
    }

    @GetMapping("/leads/{leadId}")
    public ThreadDto thread(@PathVariable Long leadId) {
        return chatService.thread(leadId);
    }

    /** A chat with a number that is not in our leads (the "Other" section). phone = digits incl. country code. */
    @GetMapping("/others/{phone}")
    public ThreadDto otherThread(@PathVariable String phone) {
        return chatService.otherThread(phone);
    }

    @PostMapping("/others/{phone}/read")
    public void markOtherRead(@PathVariable String phone) {
        chatService.markOtherRead(phone);
    }

    @PostMapping("/others/{phone}/messages")
    public MessageDto sendOther(@PathVariable String phone, @Valid @RequestBody SendRequest request, Authentication auth) {
        return chatService.sendOtherReply(phone, request.text(), employeeName(auth));
    }

    @PostMapping(value = "/others/{phone}/attachments", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public MessageDto sendOtherAttachment(@PathVariable String phone,
                                          @RequestParam("file") MultipartFile file,
                                          @RequestParam(value = "caption", required = false) String caption,
                                          Authentication auth) {
        return chatService.sendOtherAttachment(phone, file, caption, employeeName(auth));
    }

    @PostMapping("/leads/{leadId}/read")
    public void markRead(@PathVariable Long leadId) {
        chatService.markRead(leadId);
    }

    @PostMapping("/leads/{leadId}/messages")
    public MessageDto send(@PathVariable Long leadId, @Valid @RequestBody SendRequest request, Authentication auth) {
        return chatService.sendReply(leadId, request.text(), employeeName(auth));
    }

    /** Sends a PDF / image / office file (with an optional caption) to the customer. Only inside the 24h window. */
    @PostMapping(value = "/leads/{leadId}/attachments", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public MessageDto sendAttachment(@PathVariable Long leadId,
                                     @RequestParam("file") MultipartFile file,
                                     @RequestParam(value = "caption", required = false) String caption,
                                     Authentication auth) {
        return chatService.sendAttachment(leadId, file, caption, employeeName(auth));
    }

    /** The stored file of a message (login required; the browser fetches it with the JWT and opens it as a blob). */
    @GetMapping("/messages/{id}/attachment")
    public ResponseEntity<byte[]> attachment(@PathVariable Long id) {
        ChatService.StoredFile f = chatService.attachment(id);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(f.mime()))
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.inline().filename(f.name(), StandardCharsets.UTF_8).build().toString())
                .header("X-Content-Type-Options", "nosniff")
                .header(HttpHeaders.CACHE_CONTROL, "private, max-age=300")
                .body(f.data());
    }

    /** Delete for everyone: removes one of our sent messages from the CRM chat (it stays on the customer's WhatsApp). */
    @DeleteMapping("/messages/{id}")
    public MessageDto deleteMessage(@PathVariable Long id, Authentication auth) {
        boolean admin = employeeRepository.findByEmailIgnoreCase(auth.getName())
                .map(e -> e.getRole() == EmployeeRole.ADMIN).orElse(false);
        return chatService.deleteMessage(id, employeeName(auth), admin);
    }

    private String employeeName(Authentication auth) {
        return employeeRepository.findByEmailIgnoreCase(auth.getName())
                .map(Employee::getFullName)
                .orElse(auth.getName());
    }
}
