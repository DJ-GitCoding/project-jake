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
import com.jaddar.entity.DataHolderRegistrySettings;
import com.jaddar.repository.DataHolderRegistrySettingsRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import java.net.InetAddress;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Consumes an external Data Holder Registry: fetches and caches its feed, and
 * resolves queries against the holders it publishes. Off by default.
 */
@Slf4j
@Service
public class DataHolderRegistryService {

    /** Source label on resolutions that came from the registry. */
    public static final String SOURCE = "registry";

    private static final Duration HTTP_TIMEOUT = Duration.ofSeconds(10);

    private final WebClient rdapWebClient;
    private final DataHolderRegistrySettingsRepository settingsRepository;
    private final ObjectMapper objectMapper;
    private final Duration cacheTtl;

    /** Last parsed feed, reused until the TTL expires. */
    private volatile CachedFeed cache;

    public DataHolderRegistryService(@Qualifier("rdapWebClient") WebClient rdapWebClient,
                                     DataHolderRegistrySettingsRepository settingsRepository,
                                     ObjectMapper objectMapper,
                                     @Value("${registry.cache-seconds:300}") long cacheSeconds) {
        this.rdapWebClient = rdapWebClient;
        this.settingsRepository = settingsRepository;
        this.objectMapper = objectMapper;
        this.cacheTtl = Duration.ofSeconds(Math.max(0, cacheSeconds));
    }

    /** One data holder as published by the registry. */
    public record RegistryHolder(
            String name,
            String description,
            List<String> baseUrls,
            List<String> tlds,
            List<String> ipRanges,
            List<long[]> asnRanges,
            boolean requiresAuth) {

        /** Whether this holder serves the given TLD. */
        public boolean servesTld(String tld) {
            if (tlds == null || tld == null) return false;
            String target = normalizeTld(tld);
            for (String candidate : tlds) {
                if (candidate != null && normalizeTld(candidate).equals(target)) return true;
            }
            return false;
        }

        /** Whether any of this holder's CIDR ranges contains the address. */
        public boolean servesIp(InetAddress addr) {
            if (ipRanges == null || addr == null) return false;
            for (String cidr : ipRanges) {
                CidrBlock block = CidrBlock.parse(cidr);
                if (block != null && block.contains(addr)) return true;
            }
            return false;
        }

        /** Whether any of this holder's ASN ranges contains the AS number. */
        public boolean servesAsn(long asn) {
            if (asnRanges == null) return false;
            for (long[] range : asnRanges) {
                if (range.length == 2 && range[0] <= asn && asn <= range[1]) return true;
            }
            return false;
        }

        private static String normalizeTld(String tld) {
            String t = tld.trim().toLowerCase();
            return t.startsWith(".") ? t.substring(1) : t;
        }
    }

    private record CachedFeed(List<RegistryHolder> holders, String publication, Instant fetchedAt) {
    }

    // ------------------------------------------------------------------ //
    //  Settings
    // ------------------------------------------------------------------ //

    /** The single settings row, created on first access. */
    public DataHolderRegistrySettings getSettings() {
        return settingsRepository.findById(1L).orElseGet(() -> {
            DataHolderRegistrySettings fresh = new DataHolderRegistrySettings();
            fresh.setId(1L);
            fresh.setEnabled(false);
            return settingsRepository.save(fresh);
        });
    }

    /** Updates the toggle and URL, dropping the cached feed when either changes. */
    public DataHolderRegistrySettings updateSettings(Boolean enabled, String registryUrl, String actor) {
        DataHolderRegistrySettings settings = getSettings();

        String normalizedUrl = normalizeRegistryUrl(registryUrl);
        boolean wantEnabled = enabled != null ? enabled : Boolean.TRUE.equals(settings.getEnabled());
        if (wantEnabled && (normalizedUrl == null || normalizedUrl.isBlank())) {
            throw new IllegalArgumentException(
                    "Enter the registry URL before enabling it — for example "
                    + "https://registry.example.com/dataholder-registry/registry");
        }

        boolean changed = wantEnabled != Boolean.TRUE.equals(settings.getEnabled())
                || !java.util.Objects.equals(normalizedUrl, settings.getRegistryUrl());

        settings.setEnabled(wantEnabled);
        settings.setRegistryUrl(normalizedUrl);
        settings.setUpdatedBy(actor);
        DataHolderRegistrySettings saved = settingsRepository.save(settings);

        if (changed) {
            cache = null;
        }
        return saved;
    }

    /** Requires an http(s) URL and strips the trailing slash. */
    private String normalizeRegistryUrl(String url) {
        if (url == null) return null;
        String trimmed = url.trim();
        if (trimmed.isEmpty()) return null;
        if (!trimmed.startsWith("http://") && !trimmed.startsWith("https://")) {
            throw new IllegalArgumentException(
                    "The registry URL must start with http:// or https://");
        }
        while (trimmed.endsWith("/")) trimmed = trimmed.substring(0, trimmed.length() - 1);
        return trimmed;
    }

    /** Whether the registry is turned on and has a URL. */
    public boolean isEnabled() {
        DataHolderRegistrySettings settings = getSettings();
        return Boolean.TRUE.equals(settings.getEnabled())
                && settings.getRegistryUrl() != null
                && !settings.getRegistryUrl().isBlank();
    }

    // ------------------------------------------------------------------ //
    //  Feed access
    // ------------------------------------------------------------------ //

    /** Holders from the registry, or empty when it is off or unreachable. */
    public List<RegistryHolder> getHolders() {
        if (!isEnabled()) return List.of();
        CachedFeed current = cache;
        if (current != null && Duration.between(current.fetchedAt(), Instant.now()).compareTo(cacheTtl) < 0) {
            return current.holders();
        }
        refresh(false);
        current = cache;
        return current != null ? current.holders() : List.of();
    }

    /** Fetches the feed and records the outcome; `manual` marks an operator refresh. */
    public DataHolderRegistrySettings refresh(boolean manual) {
        DataHolderRegistrySettings settings = getSettings();
        if (!isEnabled()) {
            settings.setLastSyncStatus("FAILURE");
            settings.setLastSyncMessage("The registry is turned off, or no URL is configured.");
            settings.setLastSyncedAt(Instant.now());
            return settingsRepository.save(settings);
        }

        String url = settings.getRegistryUrl() + "/dataholders.json";
        try {
            String body = rdapWebClient.get()
                    .uri(url)
                    .header("Accept", "application/json")
                    .retrieve()
                    .bodyToMono(String.class)
                    .block(HTTP_TIMEOUT);
            if (body == null) {
                throw new IllegalStateException("The registry returned an empty response.");
            }

            JsonNode root = objectMapper.readTree(body);
            List<RegistryHolder> holders = parseHolders(root);
            String publication = root.path("publication").asText(null);

            cache = new CachedFeed(holders, publication, Instant.now());

            settings.setLastSyncStatus("SUCCESS");
            settings.setLastSyncMessage("Fetched " + holders.size()
                    + (holders.size() == 1 ? " data holder." : " data holders."));
            settings.setLastSyncedAt(Instant.now());
            settings.setHolderCount(holders.size());
            settings.setFeedPublication(publication);
            if (manual) {
                log.info("Data holder registry refreshed from {} — {} holders", url, holders.size());
            }
            return settingsRepository.save(settings);
        } catch (Exception e) {
            log.warn("Failed to fetch the data holder registry from {}: {}", url, e.toString());
            settings.setLastSyncStatus("FAILURE");
            settings.setLastSyncMessage(friendlyFailure(e));
            settings.setLastSyncedAt(Instant.now());
            return settingsRepository.save(settings);
        }
    }

    /** Turns a fetch failure into a message for the admin UI. */
    private String friendlyFailure(Exception e) {
        String detail = e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
        if (detail.length() > 300) detail = detail.substring(0, 300) + "…";
        return "Could not read the registry feed. Check the URL is reachable and serves "
                + "dataholders.json. (" + detail + ")";
    }

    /** Parses the feed's dataHolders array. */
    private List<RegistryHolder> parseHolders(JsonNode root) {
        List<RegistryHolder> holders = new ArrayList<>();
        JsonNode list = root.path("dataHolders");
        if (!list.isArray()) {
            throw new IllegalStateException(
                    "The feed has no \"dataHolders\" array — is the URL pointing at the registry's feed directory?");
        }
        for (JsonNode node : list) {
            if (node.has("active") && !node.path("active").asBoolean(true)) continue;
            holders.add(new RegistryHolder(
                    node.path("name").asText(null),
                    node.path("description").asText(null),
                    strings(node.path("baseUrls")),
                    strings(node.path("tlds")),
                    strings(node.path("ipRanges")),
                    asnRanges(node.path("asnRanges")),
                    node.path("requiresAuth").asBoolean(false)));
        }
        return holders;
    }

    private static List<String> strings(JsonNode array) {
        List<String> out = new ArrayList<>();
        if (array != null && array.isArray()) {
            for (JsonNode node : array) {
                String value = node.asText(null);
                if (value != null && !value.isBlank()) out.add(value);
            }
        }
        return out;
    }

    private static List<long[]> asnRanges(JsonNode array) {
        List<long[]> out = new ArrayList<>();
        if (array != null && array.isArray()) {
            for (JsonNode pair : array) {
                if (pair.isArray() && pair.size() >= 2) {
                    out.add(new long[]{pair.get(0).asLong(), pair.get(1).asLong()});
                }
            }
        }
        return out;
    }

    // ------------------------------------------------------------------ //
    //  Resolution
    // ------------------------------------------------------------------ //

    /** First registry holder serving the TLD, or null. */
    public RdapResolution resolveTld(String tld) {
        for (RegistryHolder holder : getHolders()) {
            if (holder.servesTld(tld)) return toResolution(holder);
        }
        return null;
    }

    /** First registry holder serving the address, or null. */
    public RdapResolution resolveIp(InetAddress addr) {
        for (RegistryHolder holder : getHolders()) {
            if (holder.servesIp(addr)) return toResolution(holder);
        }
        return null;
    }

    /** First registry holder serving the AS number, or null. */
    public RdapResolution resolveAsn(long asn) {
        for (RegistryHolder holder : getHolders()) {
            if (holder.servesAsn(asn)) return toResolution(holder);
        }
        return null;
    }

    /** First registry holder with a base URL; entity handles carry no routing. */
    public RdapResolution resolveEntity() {
        for (RegistryHolder holder : getHolders()) {
            if (holder.baseUrls() != null && !holder.baseUrls().isEmpty()) return toResolution(holder);
        }
        return null;
    }

    /** Registry holder as an RdapResolution. */
    private static RdapResolution toResolution(RegistryHolder holder) {
        return RdapResolution.builder()
                .baseUrls(holder.baseUrls())
                .source(SOURCE)
                .dataHolderId(null)
                .dataHolderName(holder.name())
                .requiresAuth(holder.requiresAuth())
                .authType(holder.requiresAuth() ? "bearer" : "none")
                .build();
    }

    // ------------------------------------------------------------------ //
    //  Admin views
    // ------------------------------------------------------------------ //

    /** Read-only listing for the admin page. */
    public List<Map<String, Object>> listHoldersForAdmin() {
        List<Map<String, Object>> out = new ArrayList<>();
        for (RegistryHolder holder : getHolders()) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("name", holder.name());
            row.put("description", holder.description());
            row.put("base_urls", holder.baseUrls());
            row.put("tlds", holder.tlds());
            row.put("ip_ranges", holder.ipRanges());
            List<List<Long>> asns = new ArrayList<>();
            for (long[] range : holder.asnRanges()) asns.add(List.of(range[0], range[1]));
            row.put("asn_ranges", asns);
            row.put("requires_auth", holder.requiresAuth());
            row.put("source", SOURCE);
            out.add(row);
        }
        return out;
    }
}
