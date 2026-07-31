/*
 * SPDX-FileCopyrightText: 2025-2026 Edgemoor Research Institute and Derek Jenkins
 * SPDX-License-Identifier: AGPL-3.0-only
 *
 * Author: Derek Jenkins <derek@pure-code.net>
 * Additional terms under AGPL-3.0 Section 7 apply. See NOTICE at the repository root.
 */

package com.jaddar.dataholder.repository;

import com.jaddar.dataholder.entity.AgreementSubscription;
import com.jaddar.dataholder.entity.AgreementSubscription.SubscriptionStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * Repository for AgreementSubscription entities on the data holder side.
 *
 * Manages subscription lifecycle from PENDING through ACTIVE/SUSPENDED/TERMINATED.
 * Used by AgreementSubscriptionService for the approval workflow, testing,
 * activation, and by ExternalAgreementController for credential delivery lookups.
 */
@Repository
public interface AgreementSubscriptionRepository extends JpaRepository<AgreementSubscription, Long> {

    // ==================== Lookup by identifiers ====================

    /**
     * Find a subscription by its unique request ID (UUID string).
     * Used for status checks, test execution, activation, and credential delivery.
     */
    Optional<AgreementSubscription> findByRequestId(String requestId);

    /**
     * Find all subscriptions for a given requestor group.
     * Used to check for existing/blocking subscriptions before creating a new one.
     */
    List<AgreementSubscription> findByRequestorGroupId(String requestorGroupId);

    /**
     * Find subscriptions by requestor group and specific status.
     */
    List<AgreementSubscription> findByRequestorGroupIdAndStatus(String requestorGroupId, SubscriptionStatus status);

    // ==================== Status-based queries ====================

    /**
     * Find all subscriptions with a given status.
     */
    List<AgreementSubscription> findByStatus(SubscriptionStatus status);

    /**
     * Find all active subscriptions that are currently effective
     * (effectiveFrom <= now, and effectiveTo is null or > now).
     */
    @Query("SELECT s FROM AgreementSubscription s WHERE s.status = 'ACTIVE' " +
           "AND s.effectiveFrom <= :now " +
           "AND (s.effectiveTo IS NULL OR s.effectiveTo > :now)")
    List<AgreementSubscription> findActiveAndEffective(@Param("now") LocalDateTime now);

    /**
     * Find active and effective subscriptions for a specific requestor group.
     * Used by the ping endpoint and access-level resolution during RDAP queries.
     */
    @Query("SELECT s FROM AgreementSubscription s WHERE s.status = 'ACTIVE' " +
           "AND s.requestorGroupId = :requestorGroupId " +
           "AND s.effectiveFrom <= :now " +
           "AND (s.effectiveTo IS NULL OR s.effectiveTo > :now)")
    List<AgreementSubscription> findActiveAndEffectiveByRequestorGroup(
            @Param("requestorGroupId") String requestorGroupId,
            @Param("now") LocalDateTime now);

    // ==================== Template-based queries ====================

    /**
     * Find all subscriptions associated with a specific template.
     */
    List<AgreementSubscription> findByTemplateId(Long templateId);

    /**
     * Find active subscriptions for a specific template.
     */
    @Query("SELECT s FROM AgreementSubscription s WHERE s.template.id = :templateId " +
           "AND s.status = 'ACTIVE'")
    List<AgreementSubscription> findActiveByTemplateId(@Param("templateId") Long templateId);

    // ==================== Counting / existence checks ====================

    /**
     * Check if a requestor group has any subscription in a blocking status
     * (PENDING, APPROVED, TESTING, or ACTIVE).
     */
    @Query("SELECT COUNT(s) > 0 FROM AgreementSubscription s " +
           "WHERE s.requestorGroupId = :requestorGroupId " +
           "AND s.status IN ('PENDING', 'APPROVED', 'TESTING', 'ACTIVE')")
    boolean hasBlockingSubscription(@Param("requestorGroupId") String requestorGroupId);

    /**
     * Count subscriptions by status (for admin dashboard metrics).
     */
    long countByStatus(SubscriptionStatus status);

    // ==================== Requestor group code lookups ====================

    /**
     * Find subscriptions by requestor group code (the short code identifier).
     * Useful for RDAP query authorization where the token contains the group code.
     */
    List<AgreementSubscription> findByRequestorGroupCode(String requestorGroupCode);

    /**
     * Find subscriptions by requestor group code and status string.
     */
    @Query("SELECT s FROM AgreementSubscription s WHERE s.requestorGroupCode = :groupCode AND s.status = :status")
    List<AgreementSubscription> findByRequestorGroupCodeAndStatus(
            @Param("groupCode") String groupCode,
            @Param("status") String status);

    /**
     * Find active and effective subscriptions by requestor group code.
     */
    @Query("SELECT s FROM AgreementSubscription s WHERE s.status = 'ACTIVE' " +
           "AND s.requestorGroupCode = :groupCode " +
           "AND s.effectiveFrom <= :now " +
           "AND (s.effectiveTo IS NULL OR s.effectiveTo > :now)")
    List<AgreementSubscription> findActiveAndEffectiveByGroupCode(
            @Param("groupCode") String groupCode,
            @Param("now") LocalDateTime now);

    // ==================== Admin controller queries ====================

    /**
     * Find all pending subscriptions (for the admin review queue).
     */
    @Query("SELECT s FROM AgreementSubscription s WHERE s.status = 'PENDING' ORDER BY s.createdAt ASC")
    List<AgreementSubscription> findPendingSubscriptions();

    /**
     * Find all currently active subscriptions (regardless of effectiveFrom/To).
     */
    @Query("SELECT s FROM AgreementSubscription s WHERE s.status = 'ACTIVE' ORDER BY s.activatedAt DESC")
    List<AgreementSubscription> findAllActive();

    // ==================== Ordered queries for admin UI ====================

    /**
     * Find all subscriptions ordered by creation date (newest first).
     */
    List<AgreementSubscription> findAllByOrderByCreatedAtDesc();

    /**
     * Find subscriptions by status ordered by creation date.
     */
    List<AgreementSubscription> findByStatusOrderByCreatedAtDesc(SubscriptionStatus status);
}