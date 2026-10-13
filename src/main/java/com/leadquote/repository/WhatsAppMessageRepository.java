package com.leadquote.repository;

import com.leadquote.entity.MessageDirection;
import com.leadquote.entity.WhatsAppMessage;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface WhatsAppMessageRepository extends JpaRepository<WhatsAppMessage, Long> {
    Optional<WhatsAppMessage> findByProviderMessageId(String providerMessageId);

    @Modifying
    void deleteByLeadId(Long leadId);

    // ---------------------------------------------------------------------
    // Chat module
    // ---------------------------------------------------------------------

    /** Whole conversation with one lead, oldest first. */
    List<WhatsAppMessage> findByLeadIdOrderByCreatedAtAscIdAsc(Long leadId);

    /** The customer's latest message to us (decides whether the 24h reply window is open). */
    Optional<WhatsAppMessage> findFirstByLeadIdAndDirectionOrderByCreatedAtDesc(Long leadId, MessageDirection direction);

    /** One row per lead: that lead's newest message, newest conversation first. */
    @Query("select m from WhatsAppMessage m join fetch m.lead where m.id in "
            + "(select max(m2.id) from WhatsAppMessage m2 group by m2.lead.id) order by m.id desc")
    List<WhatsAppMessage> findLatestPerLead(Pageable pageable);

    /** [leadId, unread inbound count] for every lead with unread customer messages. */
    @Query("select m.lead.id, count(m) from WhatsAppMessage m "
            + "where m.direction = :dir and m.agentSeen = false group by m.lead.id")
    List<Object[]> countUnreadByLead(@Param("dir") MessageDirection dir);

    /** [leadId, time of the customer's latest inbound message]. */
    @Query("select m.lead.id, max(m.createdAt) from WhatsAppMessage m where m.direction = :dir group by m.lead.id")
    List<Object[]> lastInboundPerLead(@Param("dir") MessageDirection dir);

    long countByDirectionAndAgentSeenFalse(MessageDirection direction);

    @Modifying
    @Query("update WhatsAppMessage m set m.agentSeen = true "
            + "where m.lead.id = :leadId and m.direction = :dir and m.agentSeen = false")
    int markSeen(@Param("leadId") Long leadId, @Param("dir") MessageDirection dir);
}
