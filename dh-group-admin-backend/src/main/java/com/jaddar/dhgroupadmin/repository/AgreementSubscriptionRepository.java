/*
 * SPDX-FileCopyrightText: 2025-2026 Edgemoor Research Institute and Derek Jenkins
 * SPDX-License-Identifier: AGPL-3.0-only
 *
 * Author: Derek Jenkins <derek@pure-code.net>
 * Additional terms under AGPL-3.0 Section 7 apply. See NOTICE at the repository root.
 */

package com.jaddar.dhgroupadmin.repository;

import com.jaddar.dhgroupadmin.entity.AgreementSubscription;
import com.jaddar.dhgroupadmin.entity.AgreementSubscription.SubscriptionStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface AgreementSubscriptionRepository extends JpaRepository<AgreementSubscription, Long> {

    Optional<AgreementSubscription> findByRequestId(String requestId);

    List<AgreementSubscription> findByRequestorGroupId(String requestorGroupId);

    List<AgreementSubscription> findByRequestorGroupIdAndStatus(String requestorGroupId, SubscriptionStatus status);

    List<AgreementSubscription> findByStatus(SubscriptionStatus status);

    List<AgreementSubscription> findByDataholderId(String dataholderId);

    List<AgreementSubscription> findByDataholderIdAndStatus(String dataholderId, SubscriptionStatus status);

    @Query("SELECT s FROM AgreementSubscription s WHERE s.status = 'ACTIVE' " +
           "AND s.effectiveFrom <= :now " +
           "AND (s.effectiveTo IS NULL OR s.effectiveTo > :now)")
    List<AgreementSubscription> findActiveAndEffective(@Param("now") LocalDateTime now);

    @Query("SELECT s FROM AgreementSubscription s WHERE s.status = 'ACTIVE' " +
           "AND s.requestorGroupId = :requestorGroupId " +
           "AND s.effectiveFrom <= :now " +
           "AND (s.effectiveTo IS NULL OR s.effectiveTo > :now)")
    List<AgreementSubscription> findActiveAndEffectiveByRequestorGroup(
            @Param("requestorGroupId") String requestorGroupId,
            @Param("now") LocalDateTime now);

    @Query("SELECT s FROM AgreementSubscription s WHERE s.status = 'ACTIVE' " +
           "AND s.dataholderId = :dataholderId " +
           "AND s.effectiveFrom <= :now " +
           "AND (s.effectiveTo IS NULL OR s.effectiveTo > :now)")
    List<AgreementSubscription> findActiveAndEffectiveByDataholder(
            @Param("dataholderId") String dataholderId,
            @Param("now") LocalDateTime now);

    @Query("SELECT s FROM AgreementSubscription s WHERE s.status = 'ACTIVE' " +
           "AND s.dataholderId = :dataholderId " +
           "AND s.requestorGroupId = :requestorGroupId " +
           "AND s.effectiveFrom <= :now " +
           "AND (s.effectiveTo IS NULL OR s.effectiveTo > :now)")
    List<AgreementSubscription> findActiveAndEffectiveByDataholderAndGroup(
            @Param("dataholderId") String dataholderId,
            @Param("requestorGroupId") String requestorGroupId,
            @Param("now") LocalDateTime now);

    @Query("SELECT s FROM AgreementSubscription s WHERE s.status = 'ACTIVE' " +
           "AND s.requestorGroupCode = :groupCode " +
           "AND s.dataholderId = :dataholderId " +
           "AND s.effectiveFrom <= :now " +
           "AND (s.effectiveTo IS NULL OR s.effectiveTo > :now)")
    List<AgreementSubscription> findActiveAndEffectiveByDataholderAndGroupCode(
            @Param("dataholderId") String dataholderId,
            @Param("groupCode") String groupCode,
            @Param("now") LocalDateTime now);

    @Query("SELECT s FROM AgreementSubscription s WHERE s.status = 'ACTIVE' " +
           "AND s.requestorGroupCode = :groupCode " +
           "AND s.effectiveFrom <= :now " +
           "AND (s.effectiveTo IS NULL OR s.effectiveTo > :now)")
    List<AgreementSubscription> findActiveAndEffectiveByGroupCode(
            @Param("groupCode") String groupCode,
            @Param("now") LocalDateTime now);

    List<AgreementSubscription> findByTemplateId(Long templateId);

    @Query("SELECT s FROM AgreementSubscription s WHERE s.template.id = :templateId AND s.status = 'ACTIVE'")
    List<AgreementSubscription> findActiveByTemplateId(@Param("templateId") Long templateId);

    @Query("SELECT COUNT(s) > 0 FROM AgreementSubscription s " +
           "WHERE s.requestorGroupId = :requestorGroupId " +
           "AND s.dataholderId = :dataholderId " +
           "AND s.status IN ('PENDING', 'APPROVED', 'TESTING', 'ACTIVE')")
    boolean hasBlockingSubscription(
            @Param("requestorGroupId") String requestorGroupId,
            @Param("dataholderId") String dataholderId);

    long countByStatus(SubscriptionStatus status);

    @Query("SELECT s FROM AgreementSubscription s WHERE s.status = 'PENDING' ORDER BY s.createdAt ASC")
    List<AgreementSubscription> findPendingSubscriptions();

    @Query("SELECT s FROM AgreementSubscription s WHERE s.status = 'ACTIVE' ORDER BY s.activatedAt DESC")
    List<AgreementSubscription> findAllActive();

    List<AgreementSubscription> findAllByOrderByCreatedAtDesc();

    List<AgreementSubscription> findByStatusOrderByCreatedAtDesc(SubscriptionStatus status);

    List<AgreementSubscription> findByDataHolderGroupId(Long dataHolderGroupId);

    List<AgreementSubscription> findByDataHolderGroupIdOrderByCreatedAtDesc(Long dataHolderGroupId);

    @Query("SELECT s FROM AgreementSubscription s WHERE s.status = 'PENDING' AND s.dataHolderGroupId = :groupId ORDER BY s.createdAt ASC")
    List<AgreementSubscription> findPendingByGroupId(@Param("groupId") Long groupId);

    long countByStatusAndDataHolderGroupId(SubscriptionStatus status, Long dataHolderGroupId);

    // ==================== Paginated + searchable finder ====================
    // Search mirrors the Subscriptions table (request id, requestor group, data holder, template).
    // Optional DH-group + status filters (null disables that filter).
    @Query("SELECT s FROM AgreementSubscription s LEFT JOIN s.template t WHERE " +
           "(CAST(:search AS string) IS NULL OR LOWER(s.requestId) LIKE LOWER(CONCAT('%', CAST(:search AS string), '%')) " +
           " OR LOWER(s.requestorGroupName) LIKE LOWER(CONCAT('%', CAST(:search AS string), '%')) " +
           " OR LOWER(s.dataholderName) LIKE LOWER(CONCAT('%', CAST(:search AS string), '%')) " +
           " OR LOWER(s.dataholderId) LIKE LOWER(CONCAT('%', CAST(:search AS string), '%')) " +
           " OR LOWER(t.name) LIKE LOWER(CONCAT('%', CAST(:search AS string), '%'))) " +
           "AND (:groupId IS NULL OR s.dataHolderGroupId = :groupId) " +
           "AND (:status IS NULL OR s.status = :status)")
    Page<AgreementSubscription> search(@Param("search") String search,
                                       @Param("groupId") Long groupId,
                                       @Param("status") SubscriptionStatus status,
                                       Pageable pageable);
}
