/*
 * SPDX-FileCopyrightText: 2025-2026 Edgemoor Research Institute and Derek Jenkins
 * SPDX-License-Identifier: AGPL-3.0-only
 *
 * Author: Derek Jenkins <derek@pure-code.net>
 * Additional terms under AGPL-3.0 Section 7 apply. See NOTICE at the repository root.
 */

package com.jaddar.dataholder.repository;

import com.jaddar.dataholder.entity.AgreementRequestType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface AgreementRequestTypeRepository extends JpaRepository<AgreementRequestType, Long> {

    List<AgreementRequestType> findByTemplateIdOrderBySortOrderAsc(Long templateId);

    List<AgreementRequestType> findByTemplateIdAndIsActiveTrueOrderBySortOrderAsc(Long templateId);

    Optional<AgreementRequestType> findByTemplateIdAndNameIgnoreCase(Long templateId, String name);

    @Query("SELECT rt FROM AgreementRequestType rt WHERE rt.template.id = :templateId " +
           "AND rt.isActive = true AND rt.supportsConfidential = true")
    List<AgreementRequestType> findConfidentialTypesByTemplateId(@Param("templateId") Long templateId);

    @Query("SELECT rt FROM AgreementRequestType rt WHERE rt.template.id = :templateId " +
           "AND rt.isActive = true AND rt.supportsExigent = true")
    List<AgreementRequestType> findExigentTypesByTemplateId(@Param("templateId") Long templateId);
}