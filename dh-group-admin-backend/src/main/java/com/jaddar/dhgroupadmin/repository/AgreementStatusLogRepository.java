/*
 * SPDX-FileCopyrightText: 2025-2026 Edgemoor Research Institute and Derek Jenkins
 * SPDX-License-Identifier: AGPL-3.0-only
 *
 * Author: Derek Jenkins <derek@pure-code.net>
 * Additional terms under AGPL-3.0 Section 7 apply. See NOTICE at the repository root.
 */

package com.jaddar.dhgroupadmin.repository;

import com.jaddar.dhgroupadmin.entity.AgreementStatusLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface AgreementStatusLogRepository extends JpaRepository<AgreementStatusLog, Long> {

    List<AgreementStatusLog> findBySubscriptionIdOrderByCreatedAtDesc(Long subscriptionId);

    @Query("SELECT l FROM AgreementStatusLog l WHERE l.subscription.id = :subscriptionId " +
           "AND l.createdAt BETWEEN :startDate AND :endDate ORDER BY l.createdAt DESC")
    List<AgreementStatusLog> findBySubscriptionIdAndDateRange(
            @Param("subscriptionId") Long subscriptionId,
            @Param("startDate") LocalDateTime startDate,
            @Param("endDate") LocalDateTime endDate);

    List<AgreementStatusLog> findBySourceOrderByCreatedAtDesc(String source);

    List<AgreementStatusLog> findByChangedByOrderByCreatedAtDesc(String changedBy);

    long countBySubscriptionId(Long subscriptionId);

    void deleteBySubscriptionId(Long subscriptionId);
}
