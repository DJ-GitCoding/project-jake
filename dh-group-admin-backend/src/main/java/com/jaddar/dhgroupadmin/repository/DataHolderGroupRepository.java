/*
 * SPDX-FileCopyrightText: 2025-2026 Edgemoor Research Institute and Derek Jenkins
 * SPDX-License-Identifier: AGPL-3.0-only
 *
 * Author: Derek Jenkins <derek@pure-code.net>
 * Additional terms under AGPL-3.0 Section 7 apply. See NOTICE at the repository root.
 */

package com.jaddar.dhgroupadmin.repository;

import com.jaddar.dhgroupadmin.entity.DataHolderGroup;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface DataHolderGroupRepository extends JpaRepository<DataHolderGroup, Long> {

    List<DataHolderGroup> findByIsActiveTrue();

    boolean existsByName(String name);

    java.util.Optional<DataHolderGroup> findByNameIgnoreCase(String name);

    // ==================== Paginated + searchable finder ====================
    // Search mirrors the Data Holder Groups table (name, description).
    @Query("SELECT g FROM DataHolderGroup g WHERE " +
           "(CAST(:search AS string) IS NULL OR LOWER(g.name) LIKE LOWER(CONCAT('%', CAST(:search AS string), '%')) " +
           " OR LOWER(g.description) LIKE LOWER(CONCAT('%', CAST(:search AS string), '%')))")
    Page<DataHolderGroup> search(@Param("search") String search, Pageable pageable);
}