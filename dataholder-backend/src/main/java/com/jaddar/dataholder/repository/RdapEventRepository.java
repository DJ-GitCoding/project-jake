/*
 * SPDX-FileCopyrightText: 2025-2026 Edgemoor Research Institute and Derek Jenkins
 * SPDX-License-Identifier: AGPL-3.0-only
 *
 * Author: Derek Jenkins <derek@pure-code.net>
 * Additional terms under AGPL-3.0 Section 7 apply. See NOTICE at the repository root.
 */

package com.jaddar.dataholder.repository;

import com.jaddar.dataholder.entity.RdapEvent;
import com.jaddar.dataholder.entity.RdapEvent.EventAction;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface RdapEventRepository extends JpaRepository<RdapEvent, Long> {

    /**
     * Find all events for an RDAP entity
     */
    List<RdapEvent> findByRdapEntityIdOrderByEventDateDesc(Long rdapEntityId);

    /**
     * Find events by entity and action type
     */
    List<RdapEvent> findByRdapEntityIdAndEventAction(Long rdapEntityId, EventAction eventAction);

    /**
     * Find most recent event of a specific type for an entity
     */
    Optional<RdapEvent> findFirstByRdapEntityIdAndEventActionOrderByEventDateDesc(Long rdapEntityId, EventAction eventAction);

    /**
     * Delete all events for an entity
     */
    void deleteByRdapEntityId(Long rdapEntityId);
}