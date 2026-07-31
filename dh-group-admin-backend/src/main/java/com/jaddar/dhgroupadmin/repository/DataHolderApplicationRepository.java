/*
 * SPDX-FileCopyrightText: 2025-2026 Edgemoor Research Institute and Derek Jenkins
 * SPDX-License-Identifier: AGPL-3.0-only
 *
 * Author: Derek Jenkins <derek@pure-code.net>
 * Additional terms under AGPL-3.0 Section 7 apply. See NOTICE at the repository root.
 */

package com.jaddar.dhgroupadmin.repository;

import com.jaddar.dhgroupadmin.entity.DataHolderApplication;
import com.jaddar.dhgroupadmin.entity.DataHolderApplication.ApplicationStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface DataHolderApplicationRepository extends JpaRepository<DataHolderApplication, Long> {

    List<DataHolderApplication> findByStatus(ApplicationStatus status);

    List<DataHolderApplication> findByDataHolderGroupId(Long dataHolderGroupId);

    List<DataHolderApplication> findByDataHolderGroupIdAndStatus(Long dataHolderGroupId, ApplicationStatus status);

    boolean existsByContactEmailAndDataHolderGroupIdAndStatus(String contactEmail, Long dataHolderGroupId, ApplicationStatus status);

    // ==================== Paginated + searchable finder ====================
    // Search mirrors the Applications table (organization, contact name/email, TLDs).
    // Optional group + status filters (null disables that filter).
    @Query("SELECT a FROM DataHolderApplication a WHERE " +
           "(CAST(:search AS string) IS NULL OR LOWER(a.organizationName) LIKE LOWER(CONCAT('%', CAST(:search AS string), '%')) " +
           " OR LOWER(a.contactFullName) LIKE LOWER(CONCAT('%', CAST(:search AS string), '%')) " +
           " OR LOWER(a.contactEmail) LIKE LOWER(CONCAT('%', CAST(:search AS string), '%')) " +
           " OR LOWER(a.supportedTlds) LIKE LOWER(CONCAT('%', CAST(:search AS string), '%'))) " +
           "AND (:groupId IS NULL OR a.dataHolderGroupId = :groupId) " +
           "AND (:status IS NULL OR a.status = :status)")
    Page<DataHolderApplication> search(@Param("search") String search,
                                       @Param("groupId") Long groupId,
                                       @Param("status") ApplicationStatus status,
                                       Pageable pageable);
}
