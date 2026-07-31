/*
 * SPDX-FileCopyrightText: 2025-2026 Edgemoor Research Institute and Derek Jenkins
 * SPDX-License-Identifier: AGPL-3.0-only
 *
 * Author: Derek Jenkins <derek@pure-code.net>
 * Additional terms under AGPL-3.0 Section 7 apply. See NOTICE at the repository root.
 */

package com.jaddar.repository;

import com.jaddar.entity.RdapRequestHistory;
import com.jaddar.enums.RequestStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface RdapRequestHistoryRepository extends JpaRepository<RdapRequestHistory, Long> {

    Optional<RdapRequestHistory> findByRequestId(String requestId);

    List<RdapRequestHistory> findByUserSubOrderByCreatedAtDesc(String userSub);

    List<RdapRequestHistory> findByUserSubAndStatusOrderByCreatedAtDesc(String userSub, RequestStatus status);
}
