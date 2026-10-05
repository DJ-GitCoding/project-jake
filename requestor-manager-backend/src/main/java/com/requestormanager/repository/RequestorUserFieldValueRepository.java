/*
 * SPDX-FileCopyrightText: 2025-2026 Edgemoor Research Institute and Derek Jenkins
 * SPDX-License-Identifier: AGPL-3.0-only
 *
 * Author: Derek Jenkins <derek@pure-code.net>
 * Additional terms under AGPL-3.0 Section 7 apply. See NOTICE at the repository root.
 */
package com.requestormanager.repository;

import com.requestormanager.entity.RequestorUserFieldValue;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;

@Repository
public interface RequestorUserFieldValueRepository extends JpaRepository<RequestorUserFieldValue, Long> {
    List<RequestorUserFieldValue> findByKeycloakUserId(String keycloakUserId);

    @Modifying
    @Query("DELETE FROM RequestorUserFieldValue v WHERE v.field.id IN :fieldIds")
    void deleteByFieldIdIn(@Param("fieldIds") Collection<Long> fieldIds);
}
