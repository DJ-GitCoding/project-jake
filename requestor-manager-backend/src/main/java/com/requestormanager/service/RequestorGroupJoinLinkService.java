/*
 * SPDX-FileCopyrightText: 2025-2026 Edgemoor Research Institute and Derek Jenkins
 * SPDX-License-Identifier: AGPL-3.0-only
 *
 * Author: Derek Jenkins <derek@pure-code.net>
 * Additional terms under AGPL-3.0 Section 7 apply. See NOTICE at the repository root.
 */

package com.requestormanager.service;

import com.requestormanager.entity.RequestorGroup;
import com.requestormanager.entity.SubscriptionCredential;
import com.requestormanager.repository.SubscriptionCredentialRepository;
import com.requestormanager.service.KeycloakClientProvisioningService.JoiningUrlClaim;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Publishes requestor groups' joining links through token introspection.
 *
 * <p>A group admin may give their group a joining link — where someone outside every requestor
 * group can apply to join. This requestor manager serves a public redirect to it, and the URL of
 * that redirect (not the raw link) is what data holders are given: each introspection client
 * provisioned for one of the group's subscriptions reports it as the
 * {@value KeycloakClientProvisioningService#JOINING_URL_CLAIM} claim in its introspection responses.
 * A data holder that offers requestor group links already holds those clients' credentials, so it
 * reads the link straight from introspection and lists it with its public RDAP answers as one of
 * its "Public Information Services". Nothing about the link is stored on a subscription.
 *
 * <p>Because the redirect is handed out rather than the raw link, changing the link needs no
 * update at all; only setting or removing it does.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class RequestorGroupJoinLinkService {

    private final SubscriptionCredentialRepository credentialRepository;
    private final KeycloakClientProvisioningService keycloakService;

    /**
     * Public origin of this requestor manager. Its frontend proxies {@code /api/*} to this
     * backend without requiring a session, so the redirect is reachable there by anyone.
     */
    @Value("${app.frontend-url:http://localhost:3000}")
    private String frontendUrl;

    /** The public redirect for a group's joining link, or null when the group publishes none. */
    public String publicJoinUrl(RequestorGroup group) {
        if (group == null || group.getId() == null
                || group.getJoiningUrl() == null || group.getJoiningUrl().isBlank()) {
            return null;
        }
        String base = frontendUrl.endsWith("/") ? frontendUrl.substring(0, frontendUrl.length() - 1) : frontendUrl;
        return base + "/api/v1/external/requestor-groups/" + group.getId() + "/join";
    }

    /**
     * Bring one group's introspection clients in line with its current joining link. Runs in its
     * own transaction because it is called after the group's edit has committed.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW, readOnly = true)
    public int syncGroup(Long requestorGroupId) {
        return sync(credentialRepository.findActiveByRequestorGroupId(requestorGroupId));
    }

    /** Re-assert every active introspection client's joining link, repairing any earlier failure. */
    @Transactional(propagation = Propagation.REQUIRES_NEW, readOnly = true)
    public int reconcileAll() {
        return sync(credentialRepository.findByIsActiveTrue());
    }

    private int sync(List<SubscriptionCredential> credentials) {
        List<JoiningUrlClaim> claims = credentials.stream()
                .map(c -> new JoiningUrlClaim(c.getKeycloakClientUuid(), c.getKeycloakClientId(),
                        publicJoinUrl(c.getSubscriptionRequest().getRequestorGroup())))
                .toList();
        return keycloakService.syncJoiningUrlClaims(claims);
    }
}
