/*
 * SPDX-FileCopyrightText: 2025-2026 Edgemoor Research Institute and Derek Jenkins
 * SPDX-License-Identifier: AGPL-3.0-only
 *
 * Author: Derek Jenkins <derek@pure-code.net>
 * Additional terms under AGPL-3.0 Section 7 apply. See NOTICE at the repository root.
 */

package com.jaddar.dataholder.repository;

import com.jaddar.dataholder.entity.AgreementStatusLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface AgreementStatusLogRepository extends JpaRepository<AgreementStatusLog, Long> {

    /**
     * Find all status logs for a subscription, ordered by most recent first
     */
    List<AgreementStatusLog> findBySubscriptionIdOrderByCreatedAtDesc(Long subscriptionId);

    /**
     * Find status logs for a subscription within a date range
     */
    @Query("SELECT l FROM AgreementStatusLog l WHERE l.subscription.id = :subscriptionId " +
           "AND l.createdAt BETWEEN :startDate AND :endDate ORDER BY l.createdAt DESC")
    List<AgreementStatusLog> findBySubscriptionIdAndDateRange(
            @Param("subscriptionId") Long subscriptionId,
            @Param("startDate") LocalDateTime startDate,
            @Param("endDate") LocalDateTime endDate);

    /**
     * Find all logs for a specific status transition
     */
    @Query("SELECT l FROM AgreementStatusLog l WHERE l.previousStatus = :previousStatus " +
           "AND l.newStatus = :newStatus ORDER BY l.createdAt DESC")
    List<AgreementStatusLog> findByStatusTransition(
            @Param("previousStatus") String previousStatus,
            @Param("newStatus") String newStatus);

    /**
     * Find logs by source
     */
    List<AgreementStatusLog> findBySourceOrderByCreatedAtDesc(String source);

    /**
     * Find logs changed by a specific user
     */
    List<AgreementStatusLog> findByChangedByOrderByCreatedAtDesc(String changedBy);

    /**
     * Count status changes for a subscription
     */
    long countBySubscriptionId(Long subscriptionId);

    /**
     * Delete all logs for a subscription
     */
    void deleteBySubscriptionId(Long subscriptionId);
}