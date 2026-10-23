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

    /** Reminder bookkeeping: messages of one type for a lead, optionally excluding FAILED ones. */
    long countByLeadIdAndType(Long leadId, com.leadquote.entity.WhatsAppMessageType type);

    long countByLeadIdAndTypeAndDeliveryStatusNot(Long leadId, com.leadquote.entity.WhatsAppMessageType type,
                                                  com.leadquote.entity.MessageDeliveryStatus status);

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
            + "(select max(m2.id) from WhatsAppMessage m2 where m2.lead is not null group by m2.lead.id) order by m.id desc")
    List<WhatsAppMessage> findLatestPerLead(Pageable pageable);

    /** [leadId, unread inbound count] for every lead with unread customer messages. */
    @Query("select m.lead.id, count(m) from WhatsAppMessage m "
            + "where m.lead is not null and m.direction = :dir and m.agentSeen = false group by m.lead.id")
    List<Object[]> countUnreadByLead(@Param("dir") MessageDirection dir);

    /** [leadId, time of the customer's latest inbound message]. */
    @Query("select m.lead.id, max(m.createdAt) from WhatsAppMessage m where m.lead is not null and m.direction = :dir group by m.lead.id")
    List<Object[]> lastInboundPerLead(@Param("dir") MessageDirection dir);

    long countByDirectionAndAgentSeenFalse(MessageDirection direction);

    @Modifying
    @Query("update WhatsAppMessage m set m.agentSeen = true "
            + "where m.lead.id = :leadId and m.direction = :dir and m.agentSeen = false")
    int markSeen(@Param("leadId") Long leadId, @Param("dir") MessageDirection dir);

    // ---------------------------------------------------------------------
    // "Other" chats: people who wrote to us but are not in our leads (lead is null; matched by phone number)
    // ---------------------------------------------------------------------

    List<WhatsAppMessage> findByLeadIsNullAndContactPhoneEndingWithOrderByCreatedAtAscIdAsc(String phoneTail);

    Optional<WhatsAppMessage> findFirstByLeadIsNullAndDirectionAndContactPhoneEndingWithOrderByCreatedAtDesc(
            MessageDirection direction, String phoneTail);

    /** One row per unknown number: its newest message, newest conversation first. */
    @Query("select m from WhatsAppMessage m where m.lead is null and m.contactPhone is not null and m.id in "
            + "(select max(m2.id) from WhatsAppMessage m2 where m2.lead is null and m2.contactPhone is not null group by m2.contactPhone) "
            + "order by m.id desc")
    List<WhatsAppMessage> findLatestPerOther(Pageable pageable);

    @Query("select m.contactPhone, count(m) from WhatsAppMessage m "
            + "where m.lead is null and m.direction = :dir and m.agentSeen = false group by m.contactPhone")
    List<Object[]> countUnreadByOther(@Param("dir") MessageDirection dir);

    @Query("select m.contactPhone, max(m.createdAt) from WhatsAppMessage m "
            + "where m.lead is null and m.direction = :dir group by m.contactPhone")
    List<Object[]> lastInboundPerOther(@Param("dir") MessageDirection dir);

    /** [phone, WhatsApp profile name] of the unknown numbers. */
    @Query("select m.contactPhone, max(m.contactName) from WhatsAppMessage m "
            + "where m.lead is null and m.contactName is not null group by m.contactPhone")
    List<Object[]> namesPerOther();

    @Modifying
    @Query("update WhatsAppMessage m set m.agentSeen = true "
            + "where m.lead is null and m.contactPhone like concat('%', :tail) and m.direction = :dir and m.agentSeen = false")
    int markSeenOther(@Param("tail") String tail, @Param("dir") MessageDirection dir);

    /** A number that was in "Other" has just been added as a lead: its earlier chat moves into that lead. */
    @Modifying
    @org.springframework.transaction.annotation.Transactional
    @Query("update WhatsAppMessage m set m.lead = :lead where m.lead is null and m.contactPhone like concat('%', :tail)")
    int adoptOther(@Param("lead") com.leadquote.entity.Lead lead, @Param("tail") String tail);
}
