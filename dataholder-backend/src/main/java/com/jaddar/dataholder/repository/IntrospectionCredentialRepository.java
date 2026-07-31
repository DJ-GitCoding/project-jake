/*
 * SPDX-FileCopyrightText: 2025-2026 Edgemoor Research Institute and Derek Jenkins
 * SPDX-License-Identifier: AGPL-3.0-only
 *
 * Author: Derek Jenkins <derek@pure-code.net>
 * Additional terms under AGPL-3.0 Section 7 apply. See NOTICE at the repository root.
 */

package com.jaddar.dataholder.repository;

import com.jaddar.dataholder.entity.IntrospectionCredential;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface IntrospectionCredentialRepository extends JpaRepository<IntrospectionCredential, Long> {

    Optional<IntrospectionCredential> findByRequestId(String requestId);

    Optional<IntrospectionCredential> findByClientId(String clientId);

    /**
     * Find active credential by requestor group code — used during RDAP query
     * introspection to look up which credentials to use for a given requestor.
     */
    Optional<IntrospectionCredential> findByRequestorGroupCodeAndIsActiveTrue(String requestorGroupCode);

    /**
     * Find all active credentials for a requestor group name
     */
    List<IntrospectionCredential> findByRequestorGroupNameAndIsActiveTrue(String requestorGroupName);

    List<IntrospectionCredential> findByIsActiveTrue();

    boolean existsByRequestId(String requestId);

    /**
     * Find credential linked to a subscription
     */
    Optional<IntrospectionCredential> findBySubscriptionId(Long subscriptionId);
}