/*
 * SPDX-FileCopyrightText: 2025-2026 Edgemoor Research Institute and Derek Jenkins
 * SPDX-License-Identifier: AGPL-3.0-only
 *
 * Author: Derek Jenkins <derek@pure-code.net>
 * Additional terms under AGPL-3.0 Section 7 apply. See NOTICE at the repository root.
 */

package com.requestormanager.repository;

import com.requestormanager.entity.DataHolderAgreement;
import com.requestormanager.entity.DataHolderAgreement.AgreementStatus;
import com.requestormanager.entity.DataHolderGroup;
import com.requestormanager.entity.RequestorGroup;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface DataHolderAgreementRepository extends JpaRepository<DataHolderAgreement, Long> {

    Optional<DataHolderAgreement> findByExternalAgreementId(String externalAgreementId);

    List<DataHolderAgreement> findByRequestorGroupId(Long requestorGroupId);

    List<DataHolderAgreement> findByDataHolderGroupId(Long dataHolderGroupId);

    List<DataHolderAgreement> findByRequestorGroupIdAndIsActiveTrue(Long requestorGroupId);

    List<DataHolderAgreement> findByDataHolderGroupIdAndIsActiveTrue(Long dataHolderGroupId);

    /**
     * Find active agreements for a specific data holder group + requestor group pair.
     * Used as a fallback when the live DH Group Admin API is unreachable.
     */
    List<DataHolderAgreement> findByDataHolderGroupAndRequestorGroupAndIsActiveTrue(
            DataHolderGroup dataHolderGroup, RequestorGroup requestorGroup);

    @Query("SELECT dha FROM DataHolderAgreement dha WHERE dha.requestorGroup.id = :groupId " +
           "AND dha.isActive = true AND dha.status = 'ACTIVE' " +
           "AND (dha.effectiveFrom IS NULL OR dha.effectiveFrom <= :now) " +
           "AND (dha.effectiveTo IS NULL OR dha.effectiveTo > :now)")
    List<DataHolderAgreement> findActiveAndEffectiveByRequestorGroup(
            @Param("groupId") Long groupId,
            @Param("now") LocalDateTime now);

    @Query("SELECT dha FROM DataHolderAgreement dha WHERE dha.requestorGroup.id IN :groupIds")
    List<DataHolderAgreement> findByRequestorGroupIdIn(@Param("groupIds") List<Long> groupIds);

    @Query("SELECT dha FROM DataHolderAgreement dha WHERE dha.requestorGroup.id = :groupId " +
           "AND dha.dataHolderGroup.id = :dataHolderGroupId AND dha.isActive = true")
    Optional<DataHolderAgreement> findActiveByRequestorGroupAndDataHolderGroup(
            @Param("groupId") Long groupId,
            @Param("dataHolderGroupId") Long dataHolderGroupId);

    boolean existsByRequestorGroupIdAndDataHolderGroupIdAndIsActiveTrue(Long requestorGroupId, Long dataHolderGroupId);

    @Query("SELECT COUNT(dha) FROM DataHolderAgreement dha WHERE dha.requestorGroup.id = :groupId AND dha.isActive = true")
    Long countActiveByRequestorGroup(@Param("groupId") Long groupId);

    List<DataHolderAgreement> findByStatus(AgreementStatus status);
}