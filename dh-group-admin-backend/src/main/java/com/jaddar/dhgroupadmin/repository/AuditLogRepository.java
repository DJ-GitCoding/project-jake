/*
 * SPDX-FileCopyrightText: 2025-2026 Edgemoor Research Institute and Derek Jenkins
 * SPDX-License-Identifier: AGPL-3.0-only
 *
 * Author: Derek Jenkins <derek@pure-code.net>
 * Additional terms under AGPL-3.0 Section 7 apply. See NOTICE at the repository root.
 */

package com.jaddar.dhgroupadmin.repository;

import com.jaddar.dhgroupadmin.entity.AuditLog;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface AuditLogRepository extends JpaRepository<AuditLog, Long> {

    List<AuditLog> findAllByOrderByCreatedAtDesc();

    Page<AuditLog> findAllByOrderByCreatedAtDesc(Pageable pageable);

    List<AuditLog> findByCategoryOrderByCreatedAtDesc(String category);

    List<AuditLog> findByEntityTypeOrderByCreatedAtDesc(String entityType);

    List<AuditLog> findByPerformedByOrderByCreatedAtDesc(String performedBy);

    List<AuditLog> findByActionOrderByCreatedAtDesc(String action);

    @Query("SELECT a FROM AuditLog a WHERE a.createdAt >= :from AND a.createdAt <= :to ORDER BY a.createdAt DESC")
    List<AuditLog> findByDateRange(@Param("from") LocalDateTime from, @Param("to") LocalDateTime to);

    @Query(value = "SELECT * FROM audit_logs a WHERE " +
           "(CAST(:category AS VARCHAR) IS NULL OR a.category = :category) AND " +
           "(CAST(:entityType AS VARCHAR) IS NULL OR a.entity_type = :entityType) AND " +
           "(CAST(:action AS VARCHAR) IS NULL OR a.action = :action) AND " +
           "(CAST(:performedBy AS VARCHAR) IS NULL OR a.performed_by = :performedBy) AND " +
           "(CAST(:result AS VARCHAR) IS NULL OR a.result = :result) AND " +
           "(CAST(:search AS VARCHAR) IS NULL OR LOWER(COALESCE(a.entity_name,'')) LIKE LOWER(CONCAT('%', CAST(:search AS VARCHAR), '%')) " +
           "  OR LOWER(COALESCE(CAST(a.details AS VARCHAR),'')) LIKE LOWER(CONCAT('%', CAST(:search AS VARCHAR), '%')) " +
           "  OR LOWER(COALESCE(a.entity_id,'')) LIKE LOWER(CONCAT('%', CAST(:search AS VARCHAR), '%'))) " +
           "ORDER BY a.created_at DESC",
           countQuery = "SELECT COUNT(*) FROM audit_logs a WHERE " +
           "(CAST(:category AS VARCHAR) IS NULL OR a.category = :category) AND " +
           "(CAST(:entityType AS VARCHAR) IS NULL OR a.entity_type = :entityType) AND " +
           "(CAST(:action AS VARCHAR) IS NULL OR a.action = :action) AND " +
           "(CAST(:performedBy AS VARCHAR) IS NULL OR a.performed_by = :performedBy) AND " +
           "(CAST(:result AS VARCHAR) IS NULL OR a.result = :result) AND " +
           "(CAST(:search AS VARCHAR) IS NULL OR LOWER(COALESCE(a.entity_name,'')) LIKE LOWER(CONCAT('%', CAST(:search AS VARCHAR), '%')) " +
           "  OR LOWER(COALESCE(CAST(a.details AS VARCHAR),'')) LIKE LOWER(CONCAT('%', CAST(:search AS VARCHAR), '%')) " +
           "  OR LOWER(COALESCE(a.entity_id,'')) LIKE LOWER(CONCAT('%', CAST(:search AS VARCHAR), '%')))",
           nativeQuery = true)
    Page<AuditLog> findFiltered(
            @Param("category") String category,
            @Param("entityType") String entityType,
            @Param("action") String action,
            @Param("performedBy") String performedBy,
            @Param("result") String result,
            @Param("search") String search,
            Pageable pageable);

    @Query("SELECT DISTINCT a.performedBy FROM AuditLog a ORDER BY a.performedBy")
    List<String> findDistinctPerformedBy();

    @Query("SELECT DISTINCT a.action FROM AuditLog a ORDER BY a.action")
    List<String> findDistinctActions();

    long countByCategory(String category);

    long countByResult(String result);

    @Query("SELECT COUNT(a) FROM AuditLog a WHERE a.createdAt >= :since")
    long countSince(@Param("since") LocalDateTime since);
}
