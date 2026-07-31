/*
 * SPDX-FileCopyrightText: 2025-2026 Edgemoor Research Institute and Derek Jenkins
 * SPDX-License-Identifier: AGPL-3.0-only
 *
 * Author: Derek Jenkins <derek@pure-code.net>
 * Additional terms under AGPL-3.0 Section 7 apply. See NOTICE at the repository root.
 */

package com.jaddar.dataholder.repository;

import com.jaddar.dataholder.entity.RdapSecureDns;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface RdapSecureDnsRepository extends JpaRepository<RdapSecureDns, Long> {

    /**
     * Find all SecureDNS/DS records for an RDAP entity
     */
    List<RdapSecureDns> findByRdapEntityId(Long rdapEntityId);

    /**
     * Find by key tag
     */
    List<RdapSecureDns> findByRdapEntityIdAndKeyTag(Long rdapEntityId, Integer keyTag);

    /**
     * Delete all SecureDNS records for an entity
     */
    void deleteByRdapEntityId(Long rdapEntityId);
}