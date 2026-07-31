/*
 * SPDX-FileCopyrightText: 2025-2026 Edgemoor Research Institute and Derek Jenkins
 * SPDX-License-Identifier: AGPL-3.0-only
 *
 * Author: Derek Jenkins <derek@pure-code.net>
 * Additional terms under AGPL-3.0 Section 7 apply. See NOTICE at the repository root.
 */

package com.jaddar.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jaddar.dto.RdapResolution;
import com.jaddar.entity.DataHolder;
import com.jaddar.repository.DataHolderRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

/**
 * IANA RDAP Bootstrap Service (RFC 9224 + RFC 8521).
 *
 * Ported from backend/services/iana_bootstrap_service.py.
 *
 * Resolution order:
 *   - domain / ip: custom data holders first, IANA fallback
 *   - asn:         IANA first, custom data holders fallback
 *   - entity:      IANA object-tags first, custom data holders fallback
 *
 * The Python implementation fetched fresh IANA data on every call. To avoid
 * hammering data.iana.org under Spring's request load, this port caches each
 * bootstrap file in memory with a lazy TTL (re-fetched when stale). Set the TTL
 * to Duration.ZERO to mimic the original "always fetch fresh" behaviour.
 */
@Slf4j
@Service
public class IanaBootstrapService {

    private static final String IANA_BASE = "https://data.iana.org/rdap";
    private static final String IANA_DNS_URL = IANA_BASE + "/dns.json";
    private static final String IANA_IPV4_URL = IANA_BASE + "/ipv4.json";
    private static final String IANA_IPV6_URL = IANA_BASE + "/ipv6.json";
    private static final String IANA_ASN_URL = IANA_BASE + "/asn.json";
    private static final String IANA_OBJECT_TAGS_URL = IANA_BASE + "/object-tags.json";

    private static final Duration HTTP_TIMEOUT = Duration.ofSeconds(15);
    /** Lazy cache TTL. The Python service never cached; 1h keeps IANA load sane. */
    private static final Duration CACHE_TTL = Duration.ofHours(1);

    private final WebClient rdapWebClient;
    private final DataHolderRepository dataHolderRepository;
    private final ObjectMapper objectMapper;

    private final ConcurrentHashMap<String, CachedJson> cache = new ConcurrentHashMap<>();

    public IanaBootstrapService(@Qualifier("rdapWebClient") WebClient rdapWebClient,
                                DataHolderRepository dataHolderRepository,
                                ObjectMapper objectMapper) {
        this.rdapWebClient = rdapWebClient;
        this.dataHolderRepository = dataHolderRepository;
        this.objectMapper = objectMapper;
    }

    private record CachedJson(JsonNode node, Instant fetchedAt) {
    }

    // ------------------------------------------------------------------ //
    //  Public resolution methods
    // ------------------------------------------------------------------ //

    public RdapResolution resolveDomain(String domain) {
        String tld = extractTld(domain);
        if (tld == null) {
            return null;
        }
        // 1. Custom data holders first
        RdapResolution custom = resolveTldFromCustom(tld);
        if (custom != null) {
            return custom;
        }
        // 2. IANA bootstrap fallback
        List<String> ianaUrls = resolveTldFromIana(tld);
        if (ianaUrls != null) {
            return ianaResult(ianaUrls);
        }
        return null;
    }

    public RdapResolution resolveIp(String ipStr) {
        InetAddress addr = parseIpAddress(ipStr);
        if (addr == null) {
            return null;
        }
        boolean isV6 = addr.getAddress().length == 16;

        // 1. Custom data holders first
        RdapResolution custom = resolveIpFromCustom(addr.getHostAddress());
        if (custom != null) {
            return custom;
        }
        // 2. IANA bootstrap fallback
        List<String> ianaUrls = resolveIpFromIana(addr, isV6);
        if (ianaUrls != null) {
            return ianaResult(ianaUrls);
        }
        return null;
    }

    public RdapResolution resolveAsn(String asn) {
        Integer asnNum = parseAsn(asn);
        if (asnNum == null) {
            return null;
        }
        // 1. IANA first
        List<String> ianaUrls = resolveAsnFromIana(asnNum);
        if (ianaUrls != null) {
            return ianaResult(ianaUrls);
        }
        // 2. Custom data holders fallback
        return resolveAsnFromCustom(asnNum);
    }

    public RdapResolution resolveEntity(String entityHandle) {
        String tag = extractEntityTag(entityHandle);
        // 1. IANA object-tags first
        if (tag != null) {
            List<String> ianaUrls = resolveTagFromIana(tag);
            if (ianaUrls != null) {
                return ianaResult(ianaUrls);
            }
        }
        // 2. Custom data holders fallback
        return resolveEntityFromCustom(entityHandle);
    }

    private static RdapResolution ianaResult(List<String> urls) {
        return RdapResolution.builder()
                .baseUrls(urls)
                .source("iana")
                .dataHolderId(null)
                .requiresAuth(false)
                .authType("none")
                .build();
    }

    // ------------------------------------------------------------------ //
    //  IANA bootstrap fetching & matching
    // ------------------------------------------------------------------ //

    private JsonNode fetchIanaJson(String url) {
        CachedJson cached = cache.get(url);
        if (cached != null && Duration.between(cached.fetchedAt(), Instant.now()).compareTo(CACHE_TTL) < 0) {
            return cached.node();
        }
        try {
            String body = rdapWebClient.get()
                    .uri(url)
                    .retrieve()
                    .bodyToMono(String.class)
                    .block(HTTP_TIMEOUT);
            if (body == null) {
                return cached != null ? cached.node() : null;
            }
            JsonNode node = objectMapper.readTree(body);
            cache.put(url, new CachedJson(node, Instant.now()));
            return node;
        } catch (Exception e) {
            log.error("Failed to fetch IANA bootstrap from {}: {}", url, e.toString());
            // Fall back to a stale cached copy if we have one.
            return cached != null ? cached.node() : null;
        }
    }

    private List<String> resolveTldFromIana(String tld) {
        JsonNode data = fetchIanaJson(IANA_DNS_URL);
        if (data == null) {
            return null;
        }
        String tldLower = tld.toLowerCase();
        for (JsonNode service : data.path("services")) {
            if (service.size() < 2) {
                continue;
            }
            JsonNode tldList = service.get(0);
            JsonNode urlList = service.get(1);
            for (JsonNode t : tldList) {
                if (t.asText().equalsIgnoreCase(tldLower)) {
                    return toStringList(urlList);
                }
            }
        }
        return null;
    }

    private List<String> resolveIpFromIana(InetAddress addr, boolean isV6) {
        JsonNode data = fetchIanaJson(isV6 ? IANA_IPV6_URL : IANA_IPV4_URL);
        if (data == null) {
            return null;
        }
        int bestPrefix = -1;
        List<String> bestUrls = null;
        for (JsonNode service : data.path("services")) {
            if (service.size() < 2) {
                continue;
            }
            JsonNode cidrList = service.get(0);
            JsonNode urlList = service.get(1);
            for (JsonNode cidrNode : cidrList) {
                CidrBlock block = CidrBlock.parse(cidrNode.asText());
                if (block != null && block.contains(addr)) {
                    if (block.prefixLen() > bestPrefix) {
                        bestPrefix = block.prefixLen();
                        bestUrls = toStringList(urlList);
                    }
                }
            }
        }
        return bestUrls;
    }

    private List<String> resolveAsnFromIana(int asnNum) {
        JsonNode data = fetchIanaJson(IANA_ASN_URL);
        if (data == null) {
            return null;
        }
        for (JsonNode service : data.path("services")) {
            if (service.size() < 2) {
                continue;
            }
            JsonNode rangeList = service.get(0);
            JsonNode urlList = service.get(1);
            for (JsonNode rangeNode : rangeList) {
                String[] parts = rangeNode.asText().split("-");
                try {
                    if (parts.length == 2) {
                        int start = Integer.parseInt(parts[0].trim());
                        int end = Integer.parseInt(parts[1].trim());
                        if (start <= asnNum && asnNum <= end) {
                            return toStringList(urlList);
                        }
                    } else if (parts.length == 1) {
                        if (Integer.parseInt(parts[0].trim()) == asnNum) {
                            return toStringList(urlList);
                        }
                    }
                } catch (NumberFormatException ignored) {
                    // skip malformed range
                }
            }
        }
        return null;
    }

    private List<String> resolveTagFromIana(String tag) {
        JsonNode data = fetchIanaJson(IANA_OBJECT_TAGS_URL);
        if (data == null) {
            return null;
        }
        String tagUpper = tag.toUpperCase();
        for (JsonNode service : data.path("services")) {
            // object-tags.json: [contacts, tags, urls]
            if (service.size() < 3) {
                continue;
            }
            JsonNode tagList = service.get(1);
            JsonNode urlList = service.get(2);
            for (JsonNode t : tagList) {
                if (t.asText().equalsIgnoreCase(tagUpper)) {
                    return toStringList(urlList);
                }
            }
        }
        return null;
    }

    // ------------------------------------------------------------------ //
    //  Custom data holder matching (database)
    // ------------------------------------------------------------------ //

    private RdapResolution resolveTldFromCustom(String tld) {
        for (DataHolder holder : dataHolderRepository.findByIsActiveTrue()) {
            if (holder.servesTld(tld)) {
                return holderToResult(holder);
            }
        }
        return null;
    }

    private RdapResolution resolveIpFromCustom(String ipStr) {
        for (DataHolder holder : dataHolderRepository.findByIsActiveTrue()) {
            if (holder.servesIp(ipStr)) {
                return holderToResult(holder);
            }
        }
        return null;
    }

    private RdapResolution resolveAsnFromCustom(int asnNum) {
        for (DataHolder holder : dataHolderRepository.findByIsActiveTrue()) {
            if (holder.servesAsn(asnNum)) {
                return holderToResult(holder);
            }
        }
        return null;
    }

    private RdapResolution resolveEntityFromCustom(String entityHandle) {
        // Entity queries have no specific routing; return the first active holder
        // that has base URLs (mirrors the Python behaviour).
        for (DataHolder holder : dataHolderRepository.findByIsActiveTrue()) {
            if (holder.getBaseUrls() != null && !holder.getBaseUrls().isEmpty()) {
                return holderToResult(holder);
            }
        }
        return null;
    }

    private static RdapResolution holderToResult(DataHolder holder) {
        return RdapResolution.builder()
                .baseUrls(holder.getBaseUrls())
                .source("custom")
                .dataHolderId(holder.getId())
                .dataHolderName(holder.getName())
                .requiresAuth(Boolean.TRUE.equals(holder.getRequiresAuth()))
                .authType(holder.getAuthType())
                .build();
    }

    // ------------------------------------------------------------------ //
    //  Utilities
    // ------------------------------------------------------------------ //

    static String extractTld(String domain) {
        if (domain == null) {
            return null;
        }
        String d = domain.trim().toLowerCase();
        // strip trailing dots
        while (d.endsWith(".")) {
            d = d.substring(0, d.length() - 1);
        }
        if (d.isEmpty()) {
            return null;
        }
        String[] parts = d.split("\\.");
        return parts.length > 0 ? parts[parts.length - 1] : null;
    }

    static Integer parseAsn(String asn) {
        if (asn == null) {
            return null;
        }
        String a = asn.trim().toUpperCase();
        if (a.startsWith("AS")) {
            a = a.substring(2);
        }
        try {
            return Integer.parseInt(a);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    static String extractEntityTag(String entityHandle) {
        if (entityHandle == null) {
            return null;
        }
        String handle = entityHandle.trim();
        if (!handle.contains("-")) {
            return null;
        }
        String tag = handle.substring(handle.lastIndexOf('-') + 1);
        return tag.isEmpty() ? null : tag.toUpperCase();
    }

    private static InetAddress parseIpAddress(String ipStr) {
        if (ipStr == null) {
            return null;
        }
        String s = ipStr.trim();
        // Handle CIDR notation: take the network/host part before '/'
        String hostPart = s.contains("/") ? s.substring(0, s.indexOf('/')) : s;
        // Reject hostnames — only accept literal IPs (no DNS lookups)
        if (!hostPart.matches("[0-9a-fA-F:.]+")) {
            return null;
        }
        try {
            return InetAddress.getByName(hostPart);
        } catch (UnknownHostException e) {
            return null;
        }
    }

    private static List<String> toStringList(JsonNode arr) {
        List<String> out = new ArrayList<>();
        if (arr != null && arr.isArray()) {
            for (JsonNode n : arr) {
                out.add(n.asText());
            }
        }
        return out;
    }
}
