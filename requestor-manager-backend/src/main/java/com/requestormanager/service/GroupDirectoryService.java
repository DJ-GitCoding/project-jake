/*
 * SPDX-FileCopyrightText: 2025-2026 Edgemoor Research Institute and Derek Jenkins
 * SPDX-License-Identifier: AGPL-3.0-only
 *
 * Author: Derek Jenkins <derek@pure-code.net>
 * Additional terms under AGPL-3.0 Section 7 apply. See NOTICE at the repository root.
 */

package com.requestormanager.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.requestormanager.entity.DataHolderGroup;
import com.requestormanager.entity.GroupDirectorySettings;
import com.requestormanager.repository.DataHolderGroupRepository;
import com.requestormanager.repository.GroupDirectorySettingsRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestTemplate;

import java.net.URI;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/** Consumes a common repository's group registry feed: the central node a DH Group Admin announces itself to, so this Requestor Manager can discover i... */
@Slf4j
@Service
public class GroupDirectoryService {
    /** Where the group registry feed sits under the repository's feed directory. */
    private static final String FEED_PATH = "/group-registries.json";

    private final GroupDirectorySettingsRepository settingsRepository;
    private final DataHolderGroupRepository dataHolderGroupRepository;
    private final ObjectMapper objectMapper;
    private final RestTemplate restTemplate;
    private final Duration cacheTtl;

    /** Last parsed feed, reused until the TTL expires. */
    private volatile CachedFeed cache;

    public GroupDirectoryService(GroupDirectorySettingsRepository settingsRepository,
                                 DataHolderGroupRepository dataHolderGroupRepository,
                                 ObjectMapper objectMapper,
                                 @Value("${registry.directory.cache-seconds:300}") long cacheSeconds,
                                 @Value("${registry.directory.timeout-seconds:10}") long timeoutSeconds) {
        this.settingsRepository = settingsRepository;
        this.dataHolderGroupRepository = dataHolderGroupRepository;
        this.objectMapper = objectMapper;
        this.cacheTtl = Duration.ofSeconds(Math.max(0, cacheSeconds));
        var factory = new org.springframework.http.client.SimpleClientHttpRequestFactory();
        factory.setConnectTimeout((int) timeoutSeconds * 1000);
        factory.setReadTimeout((int) timeoutSeconds * 1000);
        this.restTemplate = new RestTemplate(factory);
    }

    /** One data holder group registry as the repository publishes it: the deployment's own identity, plus the data holder groups behind it, each with its... */
    public record DirectoryRegistry(String code, String name, String description, String baseUrl,
                                    String contactEmail, int groupCount, int templateCount,
                                    List<Map<String, Object>> dataHolderGroups, String updated) {
    }

    private record CachedFeed(List<DirectoryRegistry> registries, String publication, Instant fetchedAt) {
    }

    /** The single settings row, created on first access. */
    @Transactional
    public GroupDirectorySettings getSettings() {
        return settingsRepository.findById(1L).orElseGet(() -> {
            GroupDirectorySettings fresh = new GroupDirectorySettings();
            fresh.setId(1L);
            fresh.setEnabled(false);
            return settingsRepository.save(fresh);
        });
    }

    /** Updates the toggle and URL, dropping the cached feed when either changes. */
    @Transactional
    public GroupDirectorySettings updateSettings(Boolean enabled, String registryUrl, String actor) {
        GroupDirectorySettings settings = getSettings();

        String normalizedUrl = normalizeUrl(registryUrl);
        boolean wantEnabled = enabled != null ? enabled : Boolean.TRUE.equals(settings.getEnabled());
        if (wantEnabled && normalizedUrl == null) {
            throw new IllegalArgumentException(
                    "Enter the common repository URL before enabling it — for example "
                    + "https://registry.example.org/dataholder-registry/registry");
        }

        boolean changed = wantEnabled != Boolean.TRUE.equals(settings.getEnabled())
                || !Objects.equals(normalizedUrl, settings.getRegistryUrl());

        settings.setEnabled(wantEnabled);
        settings.setRegistryUrl(normalizedUrl);
        settings.setUpdatedBy(actor);
        GroupDirectorySettings saved = settingsRepository.save(settings);

        if (changed) cache = null;
        return saved;
    }

    /** Whether the directory is turned on and has a URL. */
    public boolean isEnabled() {
        GroupDirectorySettings settings = getSettings();
        return Boolean.TRUE.equals(settings.getEnabled())
                && settings.getRegistryUrl() != null
                && !settings.getRegistryUrl().isBlank();
    }

    /** Registries from the repository, or empty when it is off or unreachable. */
    @Transactional
    public List<DirectoryRegistry> getRegistries() {
        if (!isEnabled()) return List.of();
        CachedFeed current = cache;
        if (current != null && Duration.between(current.fetchedAt(), Instant.now()).compareTo(cacheTtl) < 0) {
            return current.registries();
        }
        refresh(false);
        current = cache;
        return current != null ? current.registries() : List.of();
    }

    /** Fetches the feed and records the outcome; manual marks an operator refresh. */
    @Transactional
    public GroupDirectorySettings refresh(boolean manual) {
        GroupDirectorySettings settings = getSettings();
        if (!isEnabled()) {
            settings.setLastSyncStatus("FAILURE");
            settings.setLastSyncMessage("The common repository is turned off, or no URL is configured.");
            settings.setLastSyncedAt(Instant.now());
            return settingsRepository.save(settings);
        }

        String url = settings.getRegistryUrl() + FEED_PATH;
        try {
            String body = restTemplate.getForObject(url, String.class);
            if (body == null || body.isBlank()) {
                throw new IllegalStateException("The common repository returned an empty response.");
            }

            JsonNode root = objectMapper.readTree(body);
            List<DirectoryRegistry> registries = parseRegistries(root);
            String publication = root.path("publication").asText(null);

            cache = new CachedFeed(registries, publication, Instant.now());

            int groupTotal = registries.stream().mapToInt(DirectoryRegistry::groupCount).sum();
            settings.setLastSyncStatus("SUCCESS");
            settings.setLastSyncMessage("Fetched " + registries.size()
                    + (registries.size() == 1 ? " group registry" : " group registries")
                    + " listing " + groupTotal
                    + (groupTotal == 1 ? " data holder group." : " data holder groups."));
            settings.setLastSyncedAt(Instant.now());
            settings.setRegistryCount(registries.size());
            settings.setGroupCount(groupTotal);
            settings.setFeedPublication(publication);
            if (manual) {
                log.info("Group registry directory refreshed from {} — {} registries, {} groups",
                        url, registries.size(), groupTotal);
            }
            return settingsRepository.save(settings);
        } catch (Exception e) {
            log.warn("Failed to fetch the group registry directory from {}: {}", url, e.toString());
            settings.setLastSyncStatus("FAILURE");
            settings.setLastSyncMessage(friendlyFailure(e));
            settings.setLastSyncedAt(Instant.now());
            return settingsRepository.save(settings);
        }
    }

    /** The directory as the admin screen shows it: every published registry with its groups, each flagged with whether it is already registered here and w... */
    @Transactional
    public List<Map<String, Object>> listForAdmin() {
        List<Map<String, Object>> rows = new ArrayList<>();
        for (DirectoryRegistry registry : getRegistries()) {
            Optional<DataHolderGroup> local = dataHolderGroupRepository
                    .findByCodeAndSource(registry.code(), DataHolderGroup.Source.REGISTRY);
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("code", registry.code());
            row.put("name", registry.name());
            row.put("description", registry.description());
            row.put("baseUrl", registry.baseUrl());
            row.put("contactEmail", registry.contactEmail());
            row.put("groupCount", registry.groupCount());
            row.put("templateCount", registry.templateCount());
            row.put("dataHolderGroups", registry.dataHolderGroups());
            row.put("updated", registry.updated());
            row.put("registered", local.isPresent());
            row.put("localId", local.map(DataHolderGroup::getId).orElse(null));
            row.put("localBaseUrl", local.map(DataHolderGroup::getBaseUrl).orElse(null));
            row.put("baseUrlDiffers", local.isPresent()
                    && !Objects.equals(local.get().getBaseUrl(), registry.baseUrl()));
            rows.add(row);
        }
        return rows;
    }

    /** Mirrors what the repository publishes into local records, one per published code. */
    @Transactional
    public int syncToLocal() {
        if (!isEnabled()) return 0;

        Set<String> publishedCodes = new HashSet<>();
        int touched = 0;

        for (DirectoryRegistry registry : getRegistries()) {
            publishedCodes.add(registry.code().toUpperCase());
            DataHolderGroup existing = dataHolderGroupRepository
                    .findByCodeAndSource(registry.code(), DataHolderGroup.Source.REGISTRY)
                    .orElse(null);

            if (existing == null) {
                dataHolderGroupRepository.save(DataHolderGroup.builder()
                        .code(registry.code())
                        .name(registry.name())
                        .description(truncate(registry.description(), 1000))
                        .baseUrl(registry.baseUrl())
                        .contactEmail(registry.contactEmail())
                        .active(true)
                        .source(DataHolderGroup.Source.REGISTRY)
                        .healthStatus(DataHolderGroup.HealthStatus.UNKNOWN)
                        .build());
                touched++;
                log.info("Added data holder group {} ({}) from the central repository",
                        registry.name(), registry.code());
                continue;
            }

            String description = truncate(registry.description(), 1000);
            if (!Objects.equals(existing.getBaseUrl(), registry.baseUrl())) {
                log.warn("Repository feed lists a different base URL for data holder group {}: stored {}, feed {}. Keeping the stored value.",
                        registry.code(), existing.getBaseUrl(), registry.baseUrl());
            }
            boolean changed = !Objects.equals(existing.getName(), registry.name())
                    || !Objects.equals(existing.getDescription(), description)
                    || !Objects.equals(existing.getContactEmail(), registry.contactEmail());
            if (changed) {
                existing.setName(registry.name());
                existing.setDescription(description);
                existing.setContactEmail(registry.contactEmail());
                dataHolderGroupRepository.save(existing);
                touched++;
            }
        }

        for (DataHolderGroup local : dataHolderGroupRepository.findAll()) {
            if (local.getSource() != DataHolderGroup.Source.REGISTRY) continue;
            if (publishedCodes.contains(local.getCode().toUpperCase())) continue;
            if (Boolean.FALSE.equals(local.getActive())) continue;
            local.setActive(false);
            dataHolderGroupRepository.save(local);
            touched++;
            log.info("Deactivated data holder group {} ({}); no longer published by the repository",
                    local.getName(), local.getCode());
        }
        return touched;
    }

    @SuppressWarnings("unchecked")
    private List<DirectoryRegistry> parseRegistries(JsonNode root) {
        JsonNode array = root.path("groupRegistries");
        if (!array.isArray()) {
            throw new IllegalStateException(
                    "The feed has no \"groupRegistries\" array — is the URL pointing at the "
                    + "repository's feed directory?");
        }
        List<DirectoryRegistry> registries = new ArrayList<>();
        for (JsonNode node : array) {
            String code = text(node, "code");
            String baseUrl = text(node, "baseUrl");
            if (code == null || baseUrl == null) continue;
            if (!isAcceptableBaseUrl(baseUrl)) {
                log.warn("Skipping data holder group {} from the repository feed: unusable base URL {}", code, baseUrl);
                continue;
            }

            List<Map<String, Object>> groups = new ArrayList<>();
            int templateTotal = 0;
            JsonNode groupArray = node.path("dataHolderGroups");
            if (groupArray.isArray()) {
                for (JsonNode group : groupArray) {
                    Map<String, Object> converted = objectMapper.convertValue(group, Map.class);
                    if (converted == null) continue;
                    if (converted.get("templates") instanceof List<?> templates) {
                        templateTotal += templates.size();
                    }
                    groups.add(converted);
                }
            }

            registries.add(new DirectoryRegistry(
                    code,
                    text(node, "name") != null ? text(node, "name") : code,
                    text(node, "description"),
                    baseUrl,
                    text(node, "contactEmail"),
                    node.path("groupCount").isInt() ? node.path("groupCount").asInt() : groups.size(),
                    node.path("templateCount").isInt() ? node.path("templateCount").asInt() : templateTotal,
                    groups,
                    text(node, "updated")));
        }
        return registries;
    }

    private static String text(JsonNode node, String field) {
        JsonNode value = node.path(field);
        if (value.isMissingNode() || value.isNull()) return null;
        String asText = value.asText();
        return asText == null || asText.isBlank() ? null : asText;
    }

    /** Turns a fetch failure into a message for the admin UI. */
    private String friendlyFailure(Exception e) {
        String detail = e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
        if (detail.length() > 300) detail = detail.substring(0, 300) + "…";
        return "Could not read the group registry feed. Check the URL is reachable and serves "
                + "group-registries.json. (" + detail + ")";
    }

    /** Whether a feed base URL is usable. */
    private static boolean isAcceptableBaseUrl(String url) {
        try {
            URI uri = new URI(url.trim());
            String scheme = uri.getScheme();
            return scheme != null
                    && (scheme.equalsIgnoreCase("http") || scheme.equalsIgnoreCase("https"))
                    && uri.getHost() != null && !uri.getHost().isBlank()
                    && uri.getRawUserInfo() == null;
        } catch (Exception e) {
            return false;
        }
    }

    private static String normalizeUrl(String url) {
        if (url == null) return null;
        String trimmed = url.trim();
        if (trimmed.isEmpty()) return null;
        if (!trimmed.startsWith("http://") && !trimmed.startsWith("https://")) {
            throw new IllegalArgumentException("The common repository URL must start with http:// or https://");
        }
        while (trimmed.endsWith("/")) trimmed = trimmed.substring(0, trimmed.length() - 1);
        return trimmed;
    }

    private static String truncate(String value, int max) {
        if (value == null) return null;
        return value.length() <= max ? value : value.substring(0, max);
    }
}
