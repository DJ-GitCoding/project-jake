/*
 * SPDX-FileCopyrightText: 2025-2026 Edgemoor Research Institute and Derek Jenkins
 * SPDX-License-Identifier: AGPL-3.0-only
 *
 * Author: Derek Jenkins <derek@pure-code.net>
 * Additional terms under AGPL-3.0 Section 7 apply. See NOTICE at the repository root.
 */

package com.requestormanager.repository;

import com.requestormanager.entity.DataHolderGroup;
import com.requestormanager.entity.DataHolderGroup.HealthStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface DataHolderGroupRepository extends JpaRepository<DataHolderGroup, Long> {
    Optional<DataHolderGroup> findByCode(String code);

    /** Every record claiming a code. A manual and a repository record may share one. */
    List<DataHolderGroup> findAllByCode(String code);

    Optional<DataHolderGroup> findByCodeAndSource(String code, DataHolderGroup.Source source);

    boolean existsByCodeAndSource(String code, DataHolderGroup.Source source);

    /** Codes claimed by more than one record, so both can be flagged as duplicates. */
    @Query("SELECT g.code FROM DataHolderGroup g GROUP BY g.code HAVING COUNT(g) > 1")
    List<String> findDuplicateCodes();
    List<DataHolderGroup> findByActiveTrue();
    List<DataHolderGroup> findByHealthStatus(HealthStatus healthStatus);
    List<DataHolderGroup> findByActiveTrueAndHealthStatus(HealthStatus healthStatus);
    boolean existsByCode(String code);

    // ==================== Paginated + searchable finders ====================
    // Search over name, code, description. The search param is nullable
    // (null = no filtering). Mirrors the SubscriptionRequestRepository pattern.

    @Query("SELECT dhg FROM DataHolderGroup dhg WHERE " +
           "(CAST(:search AS string) IS NULL OR LOWER(dhg.name) LIKE LOWER(CONCAT('%', CAST(:search AS string), '%')) " +
           " OR LOWER(dhg.code) LIKE LOWER(CONCAT('%', CAST(:search AS string), '%')) " +
           " OR LOWER(dhg.description) LIKE LOWER(CONCAT('%', CAST(:search AS string), '%')))")
    Page<DataHolderGroup> searchAll(@Param("search") String search, Pageable pageable);

    @Query("SELECT dhg FROM DataHolderGroup dhg WHERE dhg.active = true AND " +
           "(CAST(:search AS string) IS NULL OR LOWER(dhg.name) LIKE LOWER(CONCAT('%', CAST(:search AS string), '%')) " +
           " OR LOWER(dhg.code) LIKE LOWER(CONCAT('%', CAST(:search AS string), '%')) " +
           " OR LOWER(dhg.description) LIKE LOWER(CONCAT('%', CAST(:search AS string), '%')))")
    Page<DataHolderGroup> searchActive(@Param("search") String search, Pageable pageable);
}
