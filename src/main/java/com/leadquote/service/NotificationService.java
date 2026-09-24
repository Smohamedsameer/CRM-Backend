package com.leadquote.service;

import com.leadquote.entity.Notification;
import com.leadquote.entity.NotificationType;
import com.leadquote.exception.NotFoundException;
import com.leadquote.repository.NotificationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class NotificationService {

    private final NotificationRepository notificationRepository;

    /** Creates a notification. Called whenever a customer submits the enquiry form or requests changes. */
    @Transactional
    public Notification create(NotificationType type, String title, String message,
                                String referenceType, Long referenceId, String link) {
        return notificationRepository.save(Notification.builder()
                .type(type)
                .title(title)
                .message(message)
                .referenceType(referenceType)
                .referenceId(referenceId)
                .link(link)
                .read(false)
                .build());
    }

    public Page<Notification> list(Pageable pageable) {
        return notificationRepository.findAllByOrderByCreatedAtDesc(pageable);
    }

    public long unreadCount() {
        return notificationRepository.countByReadFalse();
    }

    @Transactional
    public Notification markRead(Long id) {
        Notification notification = notificationRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Notification not found: " + id));
        notification.setRead(true);
        return notificationRepository.save(notification);
    }

    @Transactional
    public void markAllRead() {
        notificationRepository.markAllRead();
    }
}
