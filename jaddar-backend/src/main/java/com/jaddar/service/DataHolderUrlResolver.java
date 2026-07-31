/*
 * SPDX-FileCopyrightText: 2025-2026 Edgemoor Research Institute and Derek Jenkins
 * SPDX-License-Identifier: AGPL-3.0-only
 *
 * Author: Derek Jenkins <derek@pure-code.net>
 * Additional terms under AGPL-3.0 Section 7 apply. See NOTICE at the repository root.
 */

package com.jaddar.service;

import com.jaddar.config.JaddarProperties;
import com.jaddar.config.MtlsEndpoints;
import com.jaddar.entity.DataHolder;
import com.jaddar.repository.DataHolderRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Resolves the data holder's root URL for backend-to-backend calls, matching by
 * TLD from the query value, with internal/external URL mapping.
 *
 * Ports the helpers from backend/routers/rdap_routes.py:
 *   _to_internal_url, _strip_rdap_suffix, _get_data_holder_url.
 */
@Slf4j
@Service
public class DataHolderUrlResolver {

    private final DataHolderRepository dataHolderRepository;
    private final JaddarProperties properties;
    private final MtlsEndpoints mtlsEndpoints;

    /** External -> Docker-internal URL map (mirrors _INTERNAL_URL_MAP). */
    private final Map<String, String> internalUrlMap = new LinkedHashMap<>();

    public DataHolderUrlResolver(DataHolderRepository dataHolderRepository, JaddarProperties properties,
                                 MtlsEndpoints mtlsEndpoints) {
        this.dataHolderRepository = dataHolderRepository;
        this.properties = properties;
        this.mtlsEndpoints = mtlsEndpoints;
        String dataholderUrl = properties.getDataholderUrl() != null
                ? properties.getDataholderUrl() : "http://dataholder:8082";
        // Public data holder URLs (per deployment) that map to the internal service URL.
        String publicUrls = properties.getDataholderPublicUrls();
        if (publicUrls != null && !publicUrls.isBlank()) {
            for (String url : publicUrls.split(",")) {
                String key = url.trim();
                if (!key.isEmpty()) {
                    internalUrlMap.put(key, dataholderUrl);
                }
            }
        }
    }

    /** Convert an external/public data holder URL to its Docker-internal equivalent. */
    public String toInternalUrl(String externalUrl) {
        for (Map.Entry<String, String> e : internalUrlMap.entrySet()) {
            if (externalUrl.startsWith(e.getKey())) {
                return e.getValue();
            }
        }
        return externalUrl;
    }

    /** Strip /api/rdap, /rdap, or /api suffix from a URL to get the root. */
    public String stripRdapSuffix(String url) {
        String u = url;
        while (u.endsWith("/")) {
            u = u.substring(0, u.length() - 1);
        }
        for (String suffix : List.of("/api/rdap", "/rdap", "/api")) {
            if (u.endsWith(suffix)) {
                return u.substring(0, u.length() - suffix.length());
            }
        }
        return u;
    }

    /**
     * Get the data holder's root URL, matching by TLD from the query value.
     * Resolution order: TLD match -> first active holder -> dataholderUrl default.
     */
    public String getDataHolderUrl(String queryValue) {
        // 1. TLD-based matching
        if (queryValue != null) {
            String tld = extractTld(queryValue);
            if (tld != null) {
                for (DataHolder holder : dataHolderRepository.findByIsActiveTrue()) {
                    if (holder.servesTld(tld) && holder.getBaseUrls() != null && !holder.getBaseUrls().isEmpty()) {
                        String url = mtlsEndpoints.resolve(toInternalUrl(stripRdapSuffix(holder.getBaseUrls().get(0))));
                        log.info("Matched data holder '{}' for TLD '{}' -> {}", holder.getName(), tld, url);
                        return url;
                    }
                }
            }
        }
        // 2. First active data holder
        for (DataHolder holder : dataHolderRepository.findByIsActiveTrue()) {
            if (holder.getBaseUrls() != null && !holder.getBaseUrls().isEmpty()) {
                return mtlsEndpoints.resolve(toInternalUrl(stripRdapSuffix(holder.getBaseUrls().get(0))));
            }
        }
        // 3. Default
        return mtlsEndpoints.resolve(properties.getDataholderUrl() != null
                ? properties.getDataholderUrl() : "http://dataholder:8082");
    }

    private static String extractTld(String queryValue) {
        String v = queryValue.trim().toLowerCase();
        while (v.endsWith(".")) {
            v = v.substring(0, v.length() - 1);
        }
        if (!v.contains(".")) {
            return null;
        }
        String[] parts = v.split("\\.");
        return parts[parts.length - 1];
    }
}
