/*
 * SPDX-FileCopyrightText: 2025-2026 Edgemoor Research Institute and Derek Jenkins
 * SPDX-License-Identifier: AGPL-3.0-only
 *
 * Author: Derek Jenkins <derek@pure-code.net>
 * Additional terms under AGPL-3.0 Section 7 apply. See NOTICE at the repository root.
 */

package com.jaddar.dataholder.repository;

import com.jaddar.dataholder.entity.PolicyExpression;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface PolicyExpressionRepository extends JpaRepository<PolicyExpression, Long> {

    Optional<PolicyExpression> findFirstByIsActiveTrueAndIsDefaultTrue();

    /**
     * Paginated + searchable finder. Search matches name and description (null = no search).
     * Optional active filter (null = all, true = active only, false = inactive only).
     */
    @Query("SELECT p FROM PolicyExpression p WHERE " +
           "(CAST(:search AS string) IS NULL OR LOWER(p.name) LIKE LOWER(CONCAT('%', CAST(:search AS string), '%')) " +
           " OR LOWER(p.description) LIKE LOWER(CONCAT('%', CAST(:search AS string), '%'))) " +
           "AND (:active IS NULL OR p.isActive = :active)")
    Page<PolicyExpression> searchAll(@Param("search") String search,
                                     @Param("active") Boolean active,
                                     Pageable pageable);

    Optional<PolicyExpression> findByIsDefaultTrueAndIsActiveTrue();

    Optional<PolicyExpression> findFirstByIsActiveTrue();

    List<PolicyExpression> findByIsActiveTrue();

    List<PolicyExpression> findAllByOrderByIdDesc();

    Optional<PolicyExpression> findByNameIgnoreCase(String name);

    boolean existsByNameIgnoreCaseAndIdNot(String name, Long id);

    boolean existsByNameIgnoreCase(String name);
}
