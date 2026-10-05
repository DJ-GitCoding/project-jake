/*
 * SPDX-FileCopyrightText: 2025-2026 Edgemoor Research Institute and Derek Jenkins
 * SPDX-License-Identifier: AGPL-3.0-only
 *
 * Author: Derek Jenkins <derek@pure-code.net>
 * Additional terms under AGPL-3.0 Section 7 apply. See NOTICE at the repository root.
 */
package com.requestormanager.repository;

import com.requestormanager.entity.RequestorGroupUserField;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;

@Repository
public interface RequestorGroupUserFieldRepository extends JpaRepository<RequestorGroupUserField, Long> {
    List<RequestorGroupUserField> findByRequestorGroupIdOrderBySortOrderAscIdAsc(Long requestorGroupId);
    List<RequestorGroupUserField> findByRequestorGroupIdInOrderBySortOrderAscIdAsc(Collection<Long> requestorGroupIds);
}
