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
import org.springframework.stereotype.Service;

import java.util.Optional;

/**
 * Resolves a policy-import contact group name to an RDAP role key, checking the
 * fixed {@link BuiltInContactRoles} first and then active {@link CustomContactRole}s.
 * Returns empty when the group matches no defined role — the importer treats that
 * as an undefined role and blocks the file.
 */
@Service
@RequiredArgsConstructor
public class ContactRoleResolver {

    private final CustomContactRoleRepository customRoleRepository;

    public Optional<String> resolveRoleKey(String groupName) {
        if (groupName == null || groupName.isBlank()) return Optional.empty();
        String trimmed = groupName.trim();

        Optional<String> builtIn = BuiltInContactRoles.resolve(trimmed.toLowerCase());
        if (builtIn.isPresent()) return builtIn;

        String normalized = CustomContactRoleService.normalizeKey(trimmed);
        for (CustomContactRole role : customRoleRepository.findByIsActiveTrue()) {
            if (role.getDisplayName().trim().equalsIgnoreCase(trimmed)
                    || role.getRoleKey().equalsIgnoreCase(normalized)) {
                return Optional.of(role.getRoleKey());
            }
        }
        return Optional.empty();
    }
}
