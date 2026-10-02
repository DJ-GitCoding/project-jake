/*
 * SPDX-FileCopyrightText: 2025-2026 Edgemoor Research Institute and Derek Jenkins
 * SPDX-License-Identifier: AGPL-3.0-only
 *
 * Author: Derek Jenkins <derek@pure-code.net>
 * Additional terms under AGPL-3.0 Section 7 apply. See NOTICE at the repository root.
 */

package com.requestormanager.repository;

import com.requestormanager.entity.SubscriptionCredential;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface SubscriptionCredentialRepository extends JpaRepository<SubscriptionCredential, Long> {

    Optional<SubscriptionCredential> findBySubscriptionRequestId(Long subscriptionRequestId);

    Optional<SubscriptionCredential> findByKeycloakClientId(String keycloakClientId);

    Optional<SubscriptionCredential> findByKeycloakClientUuid(String keycloakClientUuid);

    /**
     * Find credentials that have not been delivered yet (for retry)
     */
    @Query("SELECT sc FROM SubscriptionCredential sc WHERE sc.deliveredToDataholder = false AND sc.isActive = true")
    List<SubscriptionCredential> findUndelivered();

    /**
     * Find all active credentials for a given data holder
     */
    @Query("SELECT sc FROM SubscriptionCredential sc " +
           "WHERE sc.subscriptionRequest.dataHolderGroup.id = :dataHolderGroupId AND sc.isActive = true")
    List<SubscriptionCredential> findActiveByDataHolderGroupId(Long dataHolderGroupId);

    /**
     * Find all active credentials
     */
    List<SubscriptionCredential> findByIsActiveTrue();

    boolean existsBySubscriptionRequestId(Long subscriptionRequestId);
}