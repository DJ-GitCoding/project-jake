/*
 * SPDX-FileCopyrightText: 2025-2026 Edgemoor Research Institute and Derek Jenkins
 * SPDX-License-Identifier: AGPL-3.0-only
 *
 * Author: Derek Jenkins <derek@pure-code.net>
 * Additional terms under AGPL-3.0 Section 7 apply. See NOTICE at the repository root.
 */

package com.jaddar.dataholder.repository;

import com.jaddar.dataholder.entity.AgreementTemplate;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface AgreementTemplateRepository extends JpaRepository<AgreementTemplate, Long> {

    Optional<AgreementTemplate> findByTemplateId(String templateId);

    List<AgreementTemplate> findByIsPublishedTrue();

    List<AgreementTemplate> findByName(String name);

    boolean existsByTemplateId(String templateId);
}