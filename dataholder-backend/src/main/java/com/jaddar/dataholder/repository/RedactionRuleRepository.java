/*
 * SPDX-FileCopyrightText: 2025-2026 Edgemoor Research Institute and Derek Jenkins
 * SPDX-License-Identifier: AGPL-3.0-only
 *
 * Author: Derek Jenkins <derek@pure-code.net>
 * Additional terms under AGPL-3.0 Section 7 apply. See NOTICE at the repository root.
 */

package com.jaddar.dataholder.repository;

import com.jaddar.dataholder.entity.RedactionRule;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface RedactionRuleRepository extends JpaRepository<RedactionRule, Long> {

    /**
     * Find the rule for a specific combination of access level, sensitivity level, and empty state.
     */
    Optional<RedactionRule> findByAccessLevelAndSensitivityLevelAndEmptyValue(
            Integer accessLevel, Integer sensitivityLevel, Boolean emptyValue);

    /**
     * Find all active rules.
     */
    List<RedactionRule> findByIsActiveTrueOrderByAccessLevelAscSensitivityLevelAscEmptyValueAsc();

    /**
     * Find all rules ordered for display (the full 32-row matrix).
     */
    @Query("SELECT r FROM RedactionRule r ORDER BY r.accessLevel, r.sensitivityLevel, r.emptyValue")
    List<RedactionRule> findAllOrdered();

    /**
     * Find the active rule for a specific condition combination.
     */
    Optional<RedactionRule> findByAccessLevelAndSensitivityLevelAndEmptyValueAndIsActiveTrue(
            Integer accessLevel, Integer sensitivityLevel, Boolean emptyValue);

    /**
     * Count active rules.
     */
    long countByIsActiveTrue();

    /**
     * Check if a rule exists for the given combination.
     */
    boolean existsByAccessLevelAndSensitivityLevelAndEmptyValue(
            Integer accessLevel, Integer sensitivityLevel, Boolean emptyValue);
}
