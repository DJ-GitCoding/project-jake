/*
 * SPDX-FileCopyrightText: 2025-2026 Edgemoor Research Institute and Derek Jenkins
 * SPDX-License-Identifier: AGPL-3.0-only
 *
 * Author: Derek Jenkins <derek@pure-code.net>
 * Additional terms under AGPL-3.0 Section 7 apply. See NOTICE at the repository root.
 */

package com.jaddar.dhgroupadmin.repository;

import com.jaddar.dhgroupadmin.entity.SubscriptionCredential;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface SubscriptionCredentialRepository extends JpaRepository<SubscriptionCredential, Long> {

    Optional<SubscriptionCredential> findByClientIdAndIsActiveTrue(String clientId);

    Optional<SubscriptionCredential> findByRequestIdAndIsActiveTrue(String requestId);

    List<SubscriptionCredential> findByRequestId(String requestId);
}
