/*
 * SPDX-FileCopyrightText: 2025-2026 Edgemoor Research Institute and Derek Jenkins
 * SPDX-License-Identifier: AGPL-3.0-only
 *
 * Author: Derek Jenkins <derek@pure-code.net>
 * Additional terms under AGPL-3.0 Section 7 apply. See NOTICE at the repository root.
 */

package com.jaddar.dataholder.repository;

import com.jaddar.dataholder.entity.CustomContactRole;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface CustomContactRoleRepository extends JpaRepository<CustomContactRole, Long> {

    List<CustomContactRole> findAllByOrderByDisplayNameAsc();

    /**
     * Paginated + searchable finder. Search matches display name, role key, description.
     * A null search returns all roles.
     */
    @Query("SELECT r FROM CustomContactRole r WHERE CAST(:search AS string) IS NULL OR " +
           "LOWER(r.displayName) LIKE LOWER(CONCAT('%', CAST(:search AS string), '%')) OR " +
           "LOWER(r.roleKey) LIKE LOWER(CONCAT('%', CAST(:search AS string), '%')) OR " +
           "LOWER(r.description) LIKE LOWER(CONCAT('%', CAST(:search AS string), '%'))")
    Page<CustomContactRole> searchAll(@Param("search") String search, Pageable pageable);

    List<CustomContactRole> findByIsActiveTrue();

    Optional<CustomContactRole> findByRoleKeyIgnoreCase(String roleKey);

    boolean existsByRoleKeyIgnoreCase(String roleKey);

    boolean existsByRoleKeyIgnoreCaseAndIdNot(String roleKey, Long id);

    boolean existsByDisplayNameIgnoreCase(String displayName);

    boolean existsByDisplayNameIgnoreCaseAndIdNot(String displayName, Long id);
}
