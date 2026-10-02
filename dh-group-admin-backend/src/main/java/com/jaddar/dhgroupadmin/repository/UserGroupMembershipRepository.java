/*
 * SPDX-FileCopyrightText: 2025-2026 Edgemoor Research Institute and Derek Jenkins
 * SPDX-License-Identifier: AGPL-3.0-only
 *
 * Author: Derek Jenkins <derek@pure-code.net>
 * Additional terms under AGPL-3.0 Section 7 apply. See NOTICE at the repository root.
 */

package com.jaddar.dhgroupadmin.repository;

import com.jaddar.dhgroupadmin.entity.UserGroupMembership;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;

@Repository
public interface UserGroupMembershipRepository extends JpaRepository<UserGroupMembership, Long> {

    List<UserGroupMembership> findByUserId(Long userId);

    List<UserGroupMembership> findByDataHolderGroupId(Long dataHolderGroupId);

    List<UserGroupMembership> findByDataHolderGroupIdIn(Collection<Long> dataHolderGroupIds);

    boolean existsByUserIdAndDataHolderGroupId(Long userId, Long dataHolderGroupId);

    void deleteByUserIdAndDataHolderGroupId(Long userId, Long dataHolderGroupId);

    void deleteByDataHolderGroupId(Long dataHolderGroupId);
}
