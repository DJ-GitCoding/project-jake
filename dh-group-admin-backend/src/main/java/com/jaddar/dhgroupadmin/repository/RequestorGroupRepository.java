/*
 * SPDX-FileCopyrightText: 2025-2026 Edgemoor Research Institute and Derek Jenkins
 * SPDX-License-Identifier: AGPL-3.0-only
 *
 * Author: Derek Jenkins <derek@pure-code.net>
 * Additional terms under AGPL-3.0 Section 7 apply. See NOTICE at the repository root.
 */

package com.jaddar.dhgroupadmin.repository;

import com.jaddar.dhgroupadmin.entity.RequestorGroup;
import com.jaddar.dhgroupadmin.entity.RequestorGroup.RegistrationStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface RequestorGroupRepository extends JpaRepository<RequestorGroup, Long> {

    Optional<RequestorGroup> findByCode(String code);

    boolean existsByCode(String code);

    List<RequestorGroup> findByStatus(RegistrationStatus status);

    List<RequestorGroup> findByIsActiveTrue();

    List<RequestorGroup> findByStatusAndIsActiveTrue(RegistrationStatus status);

    List<RequestorGroup> findByDataHolderGroupId(Long dataHolderGroupId);

    List<RequestorGroup> findByStatusAndDataHolderGroupId(RegistrationStatus status, Long dataHolderGroupId);

    // ==================== Paginated + searchable finder ====================
    // Search mirrors the Requestor Groups table (name, code, organization, contact).
    // Optional DH-group + status filters (null disables that filter).
    @Query("SELECT rg FROM RequestorGroup rg WHERE " +
           "(CAST(:search AS string) IS NULL OR LOWER(rg.name) LIKE LOWER(CONCAT('%', CAST(:search AS string), '%')) " +
           " OR LOWER(rg.code) LIKE LOWER(CONCAT('%', CAST(:search AS string), '%')) " +
           " OR LOWER(rg.organization) LIKE LOWER(CONCAT('%', CAST(:search AS string), '%')) " +
           " OR LOWER(rg.contactName) LIKE LOWER(CONCAT('%', CAST(:search AS string), '%')) " +
           " OR LOWER(rg.contactEmail) LIKE LOWER(CONCAT('%', CAST(:search AS string), '%'))) " +
           "AND (:groupId IS NULL OR rg.dataHolderGroupId = :groupId) " +
           "AND (:status IS NULL OR rg.status = :status)")
    Page<RequestorGroup> search(@Param("search") String search,
                                @Param("groupId") Long groupId,
                                @Param("status") RegistrationStatus status,
                                Pageable pageable);
}
