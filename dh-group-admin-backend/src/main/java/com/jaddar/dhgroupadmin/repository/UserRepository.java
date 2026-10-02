/*
 * SPDX-FileCopyrightText: 2025-2026 Edgemoor Research Institute and Derek Jenkins
 * SPDX-License-Identifier: AGPL-3.0-only
 *
 * Author: Derek Jenkins <derek@pure-code.net>
 * Additional terms under AGPL-3.0 Section 7 apply. See NOTICE at the repository root.
 */

package com.jaddar.dhgroupadmin.repository;

import com.jaddar.dhgroupadmin.entity.User;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Repository
public interface UserRepository extends JpaRepository<User, Long> {

    Optional<User> findByEmail(String email);

    Optional<User> findByEmailAndIsActiveTrue(String email);

    List<User> findByType(Integer type);

    List<User> findByIsActiveTrue();

    List<User> findByTypeAndIsActiveTrue(Integer type);

    boolean existsByEmail(String email);

    // ==================== Paginated + searchable finder ====================
    // Search mirrors the Users table (name, email). Optional type filter (null disables it).
    @Query("SELECT u FROM User u WHERE " +
           "(CAST(:search AS string) IS NULL OR LOWER(u.firstName) LIKE LOWER(CONCAT('%', CAST(:search AS string), '%')) " +
           " OR LOWER(u.lastName) LIKE LOWER(CONCAT('%', CAST(:search AS string), '%')) " +
           " OR LOWER(u.email) LIKE LOWER(CONCAT('%', CAST(:search AS string), '%'))) " +
           "AND (:type IS NULL OR u.type = :type)")
    Page<User> search(@Param("search") String search,
                      @Param("type") Integer type,
                      Pageable pageable);

    // ==================== Group-scoped variants ====================
    // Non-master callers may only see users within their own data holder groups, so the list is
    // restricted to an explicit id set. Callers must skip these when the id set is empty.

    List<User> findByIdIn(Collection<Long> ids);

    @Query("SELECT u FROM User u WHERE u.id IN :ids AND " +
           "(CAST(:search AS string) IS NULL OR LOWER(u.firstName) LIKE LOWER(CONCAT('%', CAST(:search AS string), '%')) " +
           " OR LOWER(u.lastName) LIKE LOWER(CONCAT('%', CAST(:search AS string), '%')) " +
           " OR LOWER(u.email) LIKE LOWER(CONCAT('%', CAST(:search AS string), '%'))) " +
           "AND (:type IS NULL OR u.type = :type)")
    Page<User> searchByIds(@Param("ids") Collection<Long> ids,
                           @Param("search") String search,
                           @Param("type") Integer type,
                           Pageable pageable);
}
