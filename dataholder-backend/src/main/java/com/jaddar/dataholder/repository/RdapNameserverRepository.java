/*
 * SPDX-FileCopyrightText: 2025-2026 Edgemoor Research Institute and Derek Jenkins
 * SPDX-License-Identifier: AGPL-3.0-only
 *
 * Author: Derek Jenkins <derek@pure-code.net>
 * Additional terms under AGPL-3.0 Section 7 apply. See NOTICE at the repository root.
 */

package com.jaddar.dataholder.repository;

import com.jaddar.dataholder.entity.RdapNameserver;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface RdapNameserverRepository extends JpaRepository<RdapNameserver, Long> {

    /**
     * Find all nameservers for an RDAP entity
     */
    List<RdapNameserver> findByRdapEntityIdOrderByLdhNameAsc(Long rdapEntityId);

    /**
     * Find nameserver by LDH name
     */
    @Query("SELECT n FROM RdapNameserver n WHERE LOWER(n.ldhName) = LOWER(:ldhName)")
    Optional<RdapNameserver> findByLdhNameIgnoreCase(@Param("ldhName") String ldhName);

    /**
     * Find nameservers by LDH name for a specific entity
     */
    Optional<RdapNameserver> findByRdapEntityIdAndLdhName(Long rdapEntityId, String ldhName);

    /**
     * Delete all nameservers for an entity
     */
    void deleteByRdapEntityId(Long rdapEntityId);
}