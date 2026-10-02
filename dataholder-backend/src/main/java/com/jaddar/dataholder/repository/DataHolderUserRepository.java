/*
 * SPDX-FileCopyrightText: 2025-2026 Edgemoor Research Institute and Derek Jenkins
 * SPDX-License-Identifier: AGPL-3.0-only
 *
 * Author: Derek Jenkins <derek@pure-code.net>
 * Additional terms under AGPL-3.0 Section 7 apply. See NOTICE at the repository root.
 */

package com.jaddar.dataholder.repository;

import com.jaddar.dataholder.entity.DataHolderUser;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface DataHolderUserRepository extends JpaRepository<DataHolderUser, Long> {

    Optional<DataHolderUser> findByUsername(String username);

    Optional<DataHolderUser> findByUsernameAndIsActiveTrue(String username);

    Optional<DataHolderUser> findByEmail(String email);

    // Login is by email (case-insensitive), matching the other apps.
    Optional<DataHolderUser> findByEmailIgnoreCaseAndIsActiveTrue(String email);

    List<DataHolderUser> findByIsActiveTrue();

    boolean existsByUsername(String username);

    boolean existsByEmail(String email);

    /**
     * Paginated + searchable finder. Search matches username, email, first/last/full name.
     * A null search returns all users.
     */
    @Query("SELECT u FROM DataHolderUser u WHERE CAST(:search AS string) IS NULL OR " +
           "LOWER(u.username) LIKE LOWER(CONCAT('%', CAST(:search AS string), '%')) OR " +
           "LOWER(u.email) LIKE LOWER(CONCAT('%', CAST(:search AS string), '%')) OR " +
           "LOWER(u.firstName) LIKE LOWER(CONCAT('%', CAST(:search AS string), '%')) OR " +
           "LOWER(u.lastName) LIKE LOWER(CONCAT('%', CAST(:search AS string), '%')) OR " +
           "LOWER(u.fullName) LIKE LOWER(CONCAT('%', CAST(:search AS string), '%'))")
    Page<DataHolderUser> searchAll(@Param("search") String search, Pageable pageable);
}
