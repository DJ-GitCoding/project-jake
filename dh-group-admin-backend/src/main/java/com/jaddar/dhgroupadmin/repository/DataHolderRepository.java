/*
 * SPDX-FileCopyrightText: 2025-2026 Edgemoor Research Institute and Derek Jenkins
 * SPDX-License-Identifier: AGPL-3.0-only
 *
 * Author: Derek Jenkins <derek@pure-code.net>
 * Additional terms under AGPL-3.0 Section 7 apply. See NOTICE at the repository root.
 */

package com.jaddar.dhgroupadmin.repository;

import com.jaddar.dhgroupadmin.entity.DataHolder;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface DataHolderRepository extends JpaRepository<DataHolder, Long> {

    Optional<DataHolder> findByDataholderId(String dataholderId);

    List<DataHolder> findByIsActiveTrue();

    boolean existsByDataholderId(String dataholderId);

    // Legacy: query by the old single-column field (backward compat)
    List<DataHolder> findByDataHolderGroupId(Long dataHolderGroupId);

    List<DataHolder> findByDataHolderGroupIdAndIsActiveTrue(Long dataHolderGroupId);

    long countByDataHolderGroupId(Long dataHolderGroupId);

    // New: query via the join table (many-to-many relationship)
    @Query("SELECT dh FROM DataHolder dh JOIN dh.dataHolderGroups g WHERE g.id = :groupId")
    List<DataHolder> findByGroupMembership(@Param("groupId") Long groupId);

    @Query("SELECT dh FROM DataHolder dh JOIN dh.dataHolderGroups g WHERE g.id = :groupId AND dh.isActive = true")
    List<DataHolder> findActiveByGroupMembership(@Param("groupId") Long groupId);

    @Query("SELECT COUNT(dh) FROM DataHolder dh JOIN dh.dataHolderGroups g WHERE g.id = :groupId")
    long countByGroupMembership(@Param("groupId") Long groupId);

    /**
     * Find all data holders that belong to any of the given groups.
     * Useful for aggregating across multiple group memberships.
     */
    @Query("SELECT DISTINCT dh FROM DataHolder dh JOIN dh.dataHolderGroups g WHERE g.id IN :groupIds")
    List<DataHolder> findByGroupMembershipIn(@Param("groupIds") List<Long> groupIds);

    // ==================== Paginated + searchable finder ====================
    // Search mirrors the frontend Data Holders table (name, id, url, contact, description).
    // Optional group filter matches either the legacy single-column field or the many-to-many
    // membership (mirrors the client-side group filter). Null params disable that filter.
    @Query("SELECT dh FROM DataHolder dh WHERE " +
           "(CAST(:search AS string) IS NULL OR LOWER(dh.name) LIKE LOWER(CONCAT('%', CAST(:search AS string), '%')) " +
           " OR LOWER(dh.dataholderId) LIKE LOWER(CONCAT('%', CAST(:search AS string), '%')) " +
           " OR LOWER(dh.url) LIKE LOWER(CONCAT('%', CAST(:search AS string), '%')) " +
           " OR LOWER(dh.contactEmail) LIKE LOWER(CONCAT('%', CAST(:search AS string), '%')) " +
           " OR LOWER(dh.description) LIKE LOWER(CONCAT('%', CAST(:search AS string), '%'))) " +
           "AND (:groupId IS NULL OR dh.dataHolderGroupId = :groupId " +
           " OR :groupId IN (SELECT g.id FROM dh.dataHolderGroups g))")
    Page<DataHolder> search(@Param("search") String search,
                            @Param("groupId") Long groupId,
                            Pageable pageable);
}
