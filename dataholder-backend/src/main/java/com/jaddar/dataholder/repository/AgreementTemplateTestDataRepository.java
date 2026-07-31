/*
 * SPDX-FileCopyrightText: 2025-2026 Edgemoor Research Institute and Derek Jenkins
 * SPDX-License-Identifier: AGPL-3.0-only
 *
 * Author: Derek Jenkins <derek@pure-code.net>
 * Additional terms under AGPL-3.0 Section 7 apply. See NOTICE at the repository root.
 */

package com.jaddar.dataholder.repository;

import com.jaddar.dataholder.entity.AgreementTemplateTestData;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface AgreementTemplateTestDataRepository extends JpaRepository<AgreementTemplateTestData, Long> {

    /**
     * Find all test data entries for a template, ordered by sortOrder
     */
    List<AgreementTemplateTestData> findByTemplateIdOrderBySortOrderAscIdAsc(Long templateId);

    /**
     * Find only active test data entries for a template
     */
    @Query("SELECT t FROM AgreementTemplateTestData t WHERE t.template.id = :templateId " +
           "AND t.isActive = true ORDER BY t.sortOrder ASC, t.id ASC")
    List<AgreementTemplateTestData> findActiveByTemplateId(@Param("templateId") Long templateId);

    /**
     * Find by template's external templateId (string)
     */
    @Query("SELECT t FROM AgreementTemplateTestData t WHERE t.template.templateId = :templateId " +
           "AND t.isActive = true ORDER BY t.sortOrder ASC, t.id ASC")
    List<AgreementTemplateTestData> findActiveByTemplateTemplateId(@Param("templateId") String templateId);

    /**
     * Check if a specific RDAP entity is already linked to a template
     */
    boolean existsByTemplateIdAndRdapEntityId(Long templateId, Long rdapEntityId);

    /**
     * Count active test data entries for a template
     */
    @Query("SELECT COUNT(t) FROM AgreementTemplateTestData t WHERE t.template.id = :templateId AND t.isActive = true")
    long countActiveByTemplateId(@Param("templateId") Long templateId);

    /**
     * Delete all test data for a template
     */
    void deleteByTemplateId(Long templateId);
}