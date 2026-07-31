/*
 * SPDX-FileCopyrightText: 2025-2026 Edgemoor Research Institute and Derek Jenkins
 * SPDX-License-Identifier: AGPL-3.0-only
 *
 * Author: Derek Jenkins <derek@pure-code.net>
 * Additional terms under AGPL-3.0 Section 7 apply. See NOTICE at the repository root.
 */

package com.requestormanager.repository;

import com.requestormanager.entity.RequestorGroup;
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

    Optional<RequestorGroup> findByName(String name);

    Optional<RequestorGroup> findByCode(String code);

    boolean existsByName(String name);

    boolean existsByCode(String code);

    // Find groups by list of names (for matching Keycloak groups)
    @Query("SELECT rg FROM RequestorGroup rg WHERE rg.name IN :names")
    List<RequestorGroup> findByNameIn(@Param("names") List<String> names);

    // Case-insensitive version
    @Query("SELECT rg FROM RequestorGroup rg WHERE LOWER(rg.name) IN :names")
    List<RequestorGroup> findByNameInIgnoreCase(@Param("names") List<String> names);

    // ==================== Paginated + searchable finders ====================
    // Search over name, code, description. The search param is nullable
    // (null = no filtering). Mirrors the SubscriptionRequestRepository pattern.

    @Query("SELECT rg FROM RequestorGroup rg WHERE " +
           "(CAST(:search AS string) IS NULL OR LOWER(rg.name) LIKE LOWER(CONCAT('%', CAST(:search AS string), '%')) " +
           " OR LOWER(rg.code) LIKE LOWER(CONCAT('%', CAST(:search AS string), '%')) " +
           " OR LOWER(rg.description) LIKE LOWER(CONCAT('%', CAST(:search AS string), '%')))")
    Page<RequestorGroup> searchAll(@Param("search") String search, Pageable pageable);

    @Query("SELECT rg FROM RequestorGroup rg WHERE rg.name IN :names AND " +
           "(CAST(:search AS string) IS NULL OR LOWER(rg.name) LIKE LOWER(CONCAT('%', CAST(:search AS string), '%')) " +
           " OR LOWER(rg.code) LIKE LOWER(CONCAT('%', CAST(:search AS string), '%')) " +
           " OR LOWER(rg.description) LIKE LOWER(CONCAT('%', CAST(:search AS string), '%')))")
    Page<RequestorGroup> searchByNames(@Param("names") List<String> names,
                                       @Param("search") String search,
                                       Pageable pageable);
}