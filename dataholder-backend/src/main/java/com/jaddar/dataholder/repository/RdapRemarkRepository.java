/*
 * SPDX-FileCopyrightText: 2025-2026 Edgemoor Research Institute and Derek Jenkins
 * SPDX-License-Identifier: AGPL-3.0-only
 *
 * Author: Derek Jenkins <derek@pure-code.net>
 * Additional terms under AGPL-3.0 Section 7 apply. See NOTICE at the repository root.
 */

package com.jaddar.dataholder.repository;

import com.jaddar.dataholder.entity.RdapRemark;
import com.jaddar.dataholder.entity.RdapRemark.RemarkType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface RdapRemarkRepository extends JpaRepository<RdapRemark, Long> {

    /**
     * Find all remarks for an RDAP entity
     */
    List<RdapRemark> findByRdapEntityId(Long rdapEntityId);

    /**
     * Find remarks by type (REMARK or NOTICE)
     */
    List<RdapRemark> findByRdapEntityIdAndRemarkType(Long rdapEntityId, RemarkType remarkType);

    /**
     * Delete all remarks for an entity
     */
    void deleteByRdapEntityId(Long rdapEntityId);
}