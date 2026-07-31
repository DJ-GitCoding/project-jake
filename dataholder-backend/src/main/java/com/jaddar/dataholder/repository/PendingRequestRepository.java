/*
 * SPDX-FileCopyrightText: 2025-2026 Edgemoor Research Institute and Derek Jenkins
 * SPDX-License-Identifier: AGPL-3.0-only
 *
 * Author: Derek Jenkins <derek@pure-code.net>
 * Additional terms under AGPL-3.0 Section 7 apply. See NOTICE at the repository root.
 */

package com.jaddar.dataholder.repository;

import com.jaddar.dataholder.entity.PendingRequest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface PendingRequestRepository extends JpaRepository<PendingRequest, Long> {

    // Find by request ID (UUID)
    Optional<PendingRequest> findByRequestId(UUID requestId);

    /**
     * Paginated + searchable finder. Optional status filter (null = all statuses).
     * Search matches query value, requestor username, and requestor email (null = no search).
     */
    @Query("SELECT r FROM PendingRequest r WHERE " +
           "(:status IS NULL OR r.status = :status) AND " +
           "(CAST(:search AS string) IS NULL OR LOWER(r.queryValue) LIKE LOWER(CONCAT('%', CAST(:search AS string), '%')) " +
           " OR LOWER(r.requestorUsername) LIKE LOWER(CONCAT('%', CAST(:search AS string), '%')) " +
           " OR LOWER(r.requestorEmail) LIKE LOWER(CONCAT('%', CAST(:search AS string), '%')))")
    Page<PendingRequest> searchAll(@Param("status") PendingRequest.Status status,
                                   @Param("search") String search,
                                   Pageable pageable);

    // Find all requests ordered by creation date (newest first)
    List<PendingRequest> findAllByOrderByCreatedAtDesc();

    // Find requests by status ordered by creation date
    List<PendingRequest> findByStatusOrderByCreatedAtDesc(PendingRequest.Status status);

    // Find by status (without ordering - for counting)
    List<PendingRequest> findByStatus(PendingRequest.Status status);

    // Find by requestor email with status filter
    List<PendingRequest> findByRequestorEmailAndStatusOrderByCreatedAtDesc(String email, PendingRequest.Status status);

    // Find by requestor email (all statuses)
    List<PendingRequest> findByRequestorEmailOrderByCreatedAtDesc(String email);

    // Find by requestor sub with status filter
    List<PendingRequest> findByRequestorSubAndStatusOrderByCreatedAtDesc(String sub, PendingRequest.Status status);

    // Find by requestor sub (all statuses)
    List<PendingRequest> findByRequestorSubOrderByCreatedAtDesc(String sub);

    // Count by status
    long countByStatus(PendingRequest.Status status);

    // Count by requestor email
    long countByRequestorEmail(String email);

    // Check if request exists
    boolean existsByRequestId(UUID requestId);

    // ==================== DISCLOSURE FLAG QUERIES ====================

    // Find confidential requests (optionally filtered by status)
    List<PendingRequest> findByConfidentialTrueOrderByCreatedAtDesc();
    List<PendingRequest> findByConfidentialTrueAndStatusOrderByCreatedAtDesc(PendingRequest.Status status);

    // Find exigent requests (optionally filtered by status)
    List<PendingRequest> findByExigentTrueOrderByCreatedAtDesc();
    List<PendingRequest> findByExigentTrueAndStatusOrderByCreatedAtDesc(PendingRequest.Status status);

    // Find JAKE compliance requests (optionally filtered by status)
    List<PendingRequest> findByJakeComplianceTrueOrderByCreatedAtDesc();
    List<PendingRequest> findByJakeComplianceTrueAndStatusOrderByCreatedAtDesc(PendingRequest.Status status);

    // Counts for disclosure flags
    long countByConfidentialTrue();
    long countByExigentTrue();
    long countByJakeComplianceTrue();

    // Combined flag queries (e.g. confidential + exigent)
    List<PendingRequest> findByConfidentialTrueAndExigentTrueOrderByCreatedAtDesc();
}