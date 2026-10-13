package com.leadquote.controller;

import com.leadquote.dto.ChatDtos.ConversationDto;
import com.leadquote.dto.ChatDtos.MessageDto;
import com.leadquote.dto.ChatDtos.SendRequest;
import com.leadquote.dto.ChatDtos.ThreadDto;
import com.leadquote.entity.Employee;
import com.leadquote.repository.EmployeeRepository;
import com.leadquote.service.ChatService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

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

    @PostMapping("/leads/{leadId}/read")
    public void markRead(@PathVariable Long leadId) {
        chatService.markRead(leadId);
    }

    @PostMapping("/leads/{leadId}/messages")
    public MessageDto send(@PathVariable Long leadId, @Valid @RequestBody SendRequest request, Authentication auth) {
        String name = employeeRepository.findByEmailIgnoreCase(auth.getName())
                .map(Employee::getFullName)
                .orElse(auth.getName());
        return chatService.sendReply(leadId, request.text(), name);
    }
}
