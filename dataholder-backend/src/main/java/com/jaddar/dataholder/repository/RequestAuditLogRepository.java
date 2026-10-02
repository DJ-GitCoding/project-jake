/*
 * SPDX-FileCopyrightText: 2025-2026 Edgemoor Research Institute and Derek Jenkins
 * SPDX-License-Identifier: AGPL-3.0-only
 *
 * Author: Derek Jenkins <derek@pure-code.net>
 * Additional terms under AGPL-3.0 Section 7 apply. See NOTICE at the repository root.
 */

package com.jaddar.dataholder.repository;

import com.jaddar.dataholder.entity.RequestAuditLog;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface RequestAuditLogRepository extends JpaRepository<RequestAuditLog, Long> {
    Page<RequestAuditLog> findByRequestorSubOrderByRequestTimestampDesc(String requestorSub, Pageable pageable);
    
    List<RequestAuditLog> findByRequestTimestampBetween(LocalDateTime start, LocalDateTime end);
    
    Page<RequestAuditLog> findAllByOrderByRequestTimestampDesc(Pageable pageable);

    Page<RequestAuditLog> findByEventTypeOrderByRequestTimestampDesc(RequestAuditLog.EventType eventType, Pageable pageable);

    Page<RequestAuditLog> findBySeverityOrderByRequestTimestampDesc(RequestAuditLog.Severity severity, Pageable pageable);

    long countByEventType(RequestAuditLog.EventType eventType);

    long countBySeverity(RequestAuditLog.Severity severity);

    long countByRequestTimestampAfter(LocalDateTime since);

    long countByEventTypeAndRequestTimestampAfter(RequestAuditLog.EventType eventType, LocalDateTime since);

    /**
     * One row per day that saw at least one matching event; days with none are absent.
     * `until` is exclusive, so pass the start of the day after the last one wanted.
     */
    @Query(value = "SELECT CAST(request_timestamp AS date) AS day, COUNT(*) AS total "
                 + "FROM request_audit_log "
                 + "WHERE event_type = :eventType "
                 + "AND request_timestamp >= :since AND request_timestamp < :until "
                 + "GROUP BY CAST(request_timestamp AS date) "
                 + "ORDER BY 1", nativeQuery = true)
    List<Object[]> countPerDayBetween(@Param("eventType") String eventType,
                                      @Param("since") LocalDateTime since,
                                      @Param("until") LocalDateTime until);
}
