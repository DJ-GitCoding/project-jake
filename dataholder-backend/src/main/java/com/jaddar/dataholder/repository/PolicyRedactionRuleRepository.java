/*
 * SPDX-FileCopyrightText: 2025-2026 Edgemoor Research Institute and Derek Jenkins
 * SPDX-License-Identifier: AGPL-3.0-only
 *
 * Author: Derek Jenkins <derek@pure-code.net>
 * Additional terms under AGPL-3.0 Section 7 apply. See NOTICE at the repository root.
 */

package com.jaddar.dataholder.repository;

import com.jaddar.dataholder.entity.PolicyRedactionRule;
import com.jaddar.dataholder.entity.PolicyRedactionRule.RdapObjectType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface PolicyRedactionRuleRepository extends JpaRepository<PolicyRedactionRule, Long> {

    List<PolicyRedactionRule> findByPolicyExpressionIdOrderByRuleOrderAsc(Long policyExpressionId);

    List<PolicyRedactionRule> findByPolicyExpressionIdAndIsEnabledTrueOrderByRuleOrderAsc(Long policyExpressionId);

    List<PolicyRedactionRule> findByPolicyExpressionIdAndObjectTypeOrderByRuleOrderAsc(
            Long policyExpressionId, RdapObjectType objectType);

    @Query("SELECT r FROM PolicyRedactionRule r WHERE r.policyExpression.id = :policyId " +
           "AND r.isEnabled = true " +
           "AND (r.objectType = :objectType OR r.objectType = 'ALL') " +
           "ORDER BY r.ruleOrder ASC")
    List<PolicyRedactionRule> findEnabledRulesForObjectType(
            @Param("policyId") Long policyId,
            @Param("objectType") RdapObjectType objectType);

    Optional<PolicyRedactionRule> findByPolicyExpressionIdAndObjectTypeAndFieldPath(
            Long policyExpressionId, RdapObjectType objectType, String fieldPath);

    void deleteByPolicyExpressionId(Long policyExpressionId);

    long countByPolicyExpressionId(Long policyExpressionId);

    long countByPolicyExpressionIdAndIsEnabledTrue(Long policyExpressionId);
}
