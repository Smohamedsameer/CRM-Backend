package com.leadquote.repository;

import com.leadquote.entity.Notification;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface NotificationRepository extends JpaRepository<Notification, Long> {

    Page<Notification> findAllByOrderByCreatedAtDesc(Pageable pageable);

    long countByReadFalse();

    @Modifying
    @Query("UPDATE Notification n SET n.read = true WHERE n.read = false")
    int markAllRead();

    /** Removes the bell notifications that point at a record (used when a lead is deleted). */
    @Modifying
    @Query("DELETE FROM Notification n WHERE n.referenceType = :type AND n.referenceId = :id")
    void deleteByReference(@Param("type") String type, @Param("id") Long id);
}
