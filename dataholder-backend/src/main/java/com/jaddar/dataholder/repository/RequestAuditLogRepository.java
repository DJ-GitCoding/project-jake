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
}
