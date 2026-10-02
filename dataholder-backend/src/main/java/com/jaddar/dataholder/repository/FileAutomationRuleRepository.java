/*
 * SPDX-FileCopyrightText: 2025-2026 Edgemoor Research Institute and Derek Jenkins
 * SPDX-License-Identifier: AGPL-3.0-only
 *
 * Author: Derek Jenkins <derek@pure-code.net>
 * Additional terms under AGPL-3.0 Section 7 apply. See NOTICE at the repository root.
 */

package com.jaddar.dataholder.repository;

import com.jaddar.dataholder.entity.FileAutomationRule;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface FileAutomationRuleRepository extends JpaRepository<FileAutomationRule, Long> {

    List<FileAutomationRule> findByEnabledTrueOrderByPriorityAsc();

    List<FileAutomationRule> findAllByOrderByPriorityAsc();

    boolean existsByNameIgnoreCase(String name);

    /**
     * Paginated + searchable finder. Search matches rule name and description.
     * A null search returns all rules.
     */
    @Query("SELECT r FROM FileAutomationRule r WHERE CAST(:search AS string) IS NULL OR " +
           "LOWER(r.name) LIKE LOWER(CONCAT('%', CAST(:search AS string), '%')) OR " +
           "LOWER(r.description) LIKE LOWER(CONCAT('%', CAST(:search AS string), '%'))")
    Page<FileAutomationRule> searchAll(@Param("search") String search, Pageable pageable);
}
