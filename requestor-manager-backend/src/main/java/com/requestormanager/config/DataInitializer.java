/*
 * SPDX-FileCopyrightText: 2025-2026 Edgemoor Research Institute and Derek Jenkins
 * SPDX-License-Identifier: AGPL-3.0-only
 *
 * Author: Derek Jenkins <derek@pure-code.net>
 * Additional terms under AGPL-3.0 Section 7 apply. See NOTICE at the repository root.
 */

package com.requestormanager.config;

import com.requestormanager.entity.RequestorGroup;
import com.requestormanager.repository.RequestorGroupRepository;
import com.requestormanager.service.KeycloakUserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class DataInitializer implements CommandLineRunner {
    
    private final RequestorGroupRepository requestorGroupRepository;
    private final KeycloakUserService keycloakUserService;
    
    @Override
    public void run(String... args) {
        log.info("Initializing application data...");
        
        // Create default requestor groups if they don't exist
        // These should match Keycloak group names
        createRequestorGroupIfNotExists("ICANN", "Internet Corporation for Assigned Names and Numbers");
        
        log.info("Application data initialization completed.");
        log.info("Note: Users are managed in Keycloak. Master user should be created via keycloak-setup.");
        
        // Sync all RequestorGroups to Keycloak (creates any missing groups)
        try {
            keycloakUserService.syncRequestorGroupsToKeycloak();
            log.info("Keycloak groups synchronized with RequestorGroups");
        } catch (Exception e) {
            log.warn("Failed to sync groups to Keycloak (Keycloak may not be ready): {}", e.getMessage());
        }
    }
    
    private void createRequestorGroupIfNotExists(String name, String description) {
        if (!requestorGroupRepository.existsByName(name)) {
            RequestorGroup group = RequestorGroup.builder()
                    .name(name)
                    .description(description)
                    .build();
            requestorGroupRepository.save(group);
            log.info("Created default requestor group: {}", name);
        } else {
            log.info("Requestor group already exists: {}", name);
        }
    }
}