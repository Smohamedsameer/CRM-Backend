package com.leadquote.repository;

import com.leadquote.entity.ChatAttachment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

public interface ChatAttachmentRepository extends JpaRepository<ChatAttachment, Long> {

    Optional<ChatAttachment> findByMessageId(Long messageId);

    @Modifying
    @Transactional
    void deleteByMessageId(Long messageId);

    /** Removes every file of a lead's conversation (used when the history or the whole lead is deleted). */
    @Modifying
    @Transactional
    @Query("delete from ChatAttachment a where a.messageId in (select m.id from WhatsAppMessage m where m.lead.id = :leadId)")
    void deleteByLeadId(@Param("leadId") Long leadId);
}
