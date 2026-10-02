/*
 * SPDX-FileCopyrightText: 2025-2026 Edgemoor Research Institute and Derek Jenkins
 * SPDX-License-Identifier: AGPL-3.0-only
 *
 * Author: Derek Jenkins <derek@pure-code.net>
 * Additional terms under AGPL-3.0 Section 7 apply. See NOTICE at the repository root.
 */

package com.jaddar.dataholder.service;

import com.jaddar.dataholder.entity.CustomContactRole;
import com.jaddar.dataholder.repository.CustomContactRoleRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

/**
 * CRUD and lookup for admin-defined {@link CustomContactRole}s. These extend the
 * fixed {@link BuiltInContactRoles} so policy imports can map source contact groups
 * (e.g. "Account Holder") onto distinct RDAP field-path prefixes.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class CustomContactRoleService {

    private final CustomContactRoleRepository repository;

    public List<CustomContactRole> getAll() {
        return repository.findAllByOrderByDisplayNameAsc();
    }

    public List<CustomContactRole> getActive() {
        return repository.findByIsActiveTrue();
    }

    public Optional<CustomContactRole> get(Long id) {
        return repository.findById(id);
    }

    @Transactional
    public CustomContactRole create(String displayName, String description) {
        String name = displayName == null ? "" : displayName.trim();
        String roleKey = normalizeKey(name);
        validate(name, roleKey, null);

        CustomContactRole role = CustomContactRole.builder()
                .roleKey(roleKey)
                .displayName(name)
                .description(blankToNull(description))
                .isActive(true)
                .build();
        CustomContactRole saved = repository.save(role);
        log.info("Created custom contact role: '{}' (key={}, id={})", saved.getDisplayName(), saved.getRoleKey(), saved.getId());
        return saved;
    }

    @Transactional
    public CustomContactRole update(Long id, String displayName, String description, Boolean isActive) {
        CustomContactRole role = repository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Custom role not found: " + id));

        if (displayName != null) {
            String name = displayName.trim();
            String roleKey = normalizeKey(name);
            validate(name, roleKey, id);
            role.setDisplayName(name);
            role.setRoleKey(roleKey);
        }
        if (description != null) role.setDescription(blankToNull(description));
        if (isActive != null) role.setIsActive(isActive);

        CustomContactRole saved = repository.save(role);
        log.info("Updated custom contact role: '{}' (key={}, id={})", saved.getDisplayName(), saved.getRoleKey(), saved.getId());
        return saved;
    }

    @Transactional
    public CustomContactRole toggleActive(Long id) {
        CustomContactRole role = repository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Custom role not found: " + id));
        role.setIsActive(!Boolean.TRUE.equals(role.getIsActive()));
        return repository.save(role);
    }

    @Transactional
    public void delete(Long id) {
        CustomContactRole role = repository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Custom role not found: " + id));
        repository.delete(role);
        log.info("Deleted custom contact role: '{}' (id={})", role.getDisplayName(), id);
    }

    private void validate(String displayName, String roleKey, Long selfId) {
        if (displayName.isBlank())
            throw new IllegalArgumentException("Role name is required");
        if (roleKey.isBlank())
            throw new IllegalArgumentException("Role name must contain at least one letter or digit");
        if (BuiltInContactRoles.collidesWithBuiltIn(displayName) || BuiltInContactRoles.isBuiltInKey(roleKey))
            throw new IllegalArgumentException("'" + displayName + "' conflicts with a built-in role (registrant, admin, tech, billing, abuse)");

        boolean nameTaken = selfId == null
                ? repository.existsByDisplayNameIgnoreCase(displayName)
                : repository.existsByDisplayNameIgnoreCaseAndIdNot(displayName, selfId);
        if (nameTaken)
            throw new IllegalArgumentException("A custom role named '" + displayName + "' already exists");

        boolean keyTaken = selfId == null
                ? repository.existsByRoleKeyIgnoreCase(roleKey)
                : repository.existsByRoleKeyIgnoreCaseAndIdNot(roleKey, selfId);
        if (keyTaken)
            throw new IllegalArgumentException("A custom role with key '" + roleKey + "' already exists");
    }

    /**
     * Converts a display name to a camelCase, field-path-safe key.
     * e.g. "Account Holder" → "accountHolder", "Reseller #2" → "reseller2".
     */
    public static String normalizeKey(String displayName) {
        if (displayName == null) return "";
        String[] tokens = displayName.trim().toLowerCase().split("[^a-z0-9]+");
        StringBuilder sb = new StringBuilder();
        for (String token : tokens) {
            if (token.isEmpty()) continue;
            if (sb.length() == 0) sb.append(token);
            else sb.append(Character.toUpperCase(token.charAt(0))).append(token.substring(1));
        }
        return sb.toString();
    }

    private static String blankToNull(String s) {
        return (s == null || s.isBlank()) ? null : s.trim();
    }
}
