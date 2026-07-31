/*
 * SPDX-FileCopyrightText: 2025-2026 Edgemoor Research Institute and Derek Jenkins
 * SPDX-License-Identifier: AGPL-3.0-only
 *
 * Author: Derek Jenkins <derek@pure-code.net>
 * Additional terms under AGPL-3.0 Section 7 apply. See NOTICE at the repository root.
 */

package com.jaddar.dataholder.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.util.HashMap;
import java.util.Map;

/**
 * Rewrites a peer URL onto its mTLS listener (http→https, plain port→mTLS port via
 * {@code mtls.port-map}). Needed because several inter-service URLs live in the DB, not config.
 * Returns the URL unchanged when mTLS is disabled or the port isn't a known internal peer.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class MtlsEndpoints {

    private final MtlsProperties props;
    private volatile Map<Integer, Integer> portMap;

    public String resolve(String url) {
        if (!props.isEnabled() || url == null || url.isBlank()) {
            return url;
        }
        try {
            URI u = URI.create(url.trim());
            Integer mapped = portMap().get(u.getPort());
            if (mapped == null) {
                return url; // not an internal peer we remap
            }
            return new URI("https", u.getUserInfo(), u.getHost(), mapped, u.getPath(), u.getQuery(), u.getFragment())
                    .toString();
        } catch (Exception e) {
            log.warn("Failed to rewrite URL '{}' for mTLS: {}", url, e.getMessage());
            return url;
        }
    }

    private Map<Integer, Integer> portMap() {
        Map<Integer, Integer> local = portMap;
        if (local == null) {
            local = parse(props.getPortMap());
            portMap = local;
        }
        return local;
    }

    private static Map<Integer, Integer> parse(String spec) {
        Map<Integer, Integer> map = new HashMap<>();
        if (spec == null) return map;
        for (String pair : spec.split(",")) {
            String trimmed = pair.trim();
            if (trimmed.isEmpty()) continue;
            String[] parts = trimmed.split(":", 2);
            if (parts.length == 2) {
                try {
                    map.put(Integer.parseInt(parts[0].trim()), Integer.parseInt(parts[1].trim()));
                } catch (NumberFormatException ignored) {
                    // skip malformed entry
                }
            }
        }
        return map;
    }
}
