/*
 * SPDX-FileCopyrightText: 2025-2026 Edgemoor Research Institute and Derek Jenkins
 * SPDX-License-Identifier: AGPL-3.0-only
 *
 * Author: Derek Jenkins <derek@pure-code.net>
 * Additional terms under AGPL-3.0 Section 7 apply. See NOTICE at the repository root.
 */

package com.jaddar.dhgroupadmin.repository;

import com.jaddar.dhgroupadmin.entity.AgreementTemplate;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface AgreementTemplateRepository extends JpaRepository<AgreementTemplate, Long> {

    Optional<AgreementTemplate> findByTemplateId(String templateId);

    List<AgreementTemplate> findByIsPublishedTrue();

    List<AgreementTemplate> findByName(String name);

    boolean existsByTemplateId(String templateId);

    List<AgreementTemplate> findByDataHolderGroupId(Long dataHolderGroupId);

    List<AgreementTemplate> findByIsPublishedTrueAndDataHolderGroupId(Long dataHolderGroupId);

    // ==================== Paginated + searchable finder ====================
    // Search mirrors the Templates table (name, template id, descriptions).
    // Optional DH-group filter (null disables it).
    @Query("SELECT t FROM AgreementTemplate t WHERE " +
           "(CAST(:search AS string) IS NULL OR LOWER(t.name) LIKE LOWER(CONCAT('%', CAST(:search AS string), '%')) " +
           " OR LOWER(t.templateId) LIKE LOWER(CONCAT('%', CAST(:search AS string), '%')) " +
           " OR LOWER(t.shortDescription) LIKE LOWER(CONCAT('%', CAST(:search AS string), '%')) " +
           " OR LOWER(t.description) LIKE LOWER(CONCAT('%', CAST(:search AS string), '%'))) " +
           "AND (:groupId IS NULL OR t.dataHolderGroupId = :groupId)")
    Page<AgreementTemplate> search(@Param("search") String search,
                                   @Param("groupId") Long groupId,
                                   Pageable pageable);
}
