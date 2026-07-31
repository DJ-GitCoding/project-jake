/*
 * SPDX-FileCopyrightText: 2025-2026 Edgemoor Research Institute and Derek Jenkins
 * SPDX-License-Identifier: AGPL-3.0-only
 *
 * Author: Derek Jenkins <derek@pure-code.net>
 * Additional terms under AGPL-3.0 Section 7 apply. See NOTICE at the repository root.
 */

package com.jaddar.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jaddar.exception.ApiException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Service for accessing Keycloak events via the Keycloak Admin API.
 *
 * <p>Faithful port of {@code backend/routers/keycloak_events.py}. This module does NOT persist
 * anything to the application database — it authenticates to Keycloak with the {@code admin-cli}
 * client (resource-owner password grant) and proxies queries against Keycloak's Admin REST API
 * ({@code /admin/realms/master/events}, {@code /admin-events}, {@code /events/config}).
 *
 * <p>The admin token is cached in-process (mirroring the Python module-level
 * {@code _admin_token_cache}) and refreshed 30 seconds before it expires.
 */
@Slf4j
@Service
public class KeycloakEventsService {

    private static final Duration EVENTS_TIMEOUT = Duration.ofSeconds(30);
    private static final Duration HEALTH_TIMEOUT = Duration.ofSeconds(10);
    /** Token-expiry safety buffer, matching the Python {@code expires_in - 30}. */
    private static final long EXPIRY_BUFFER_SECONDS = 30;
    /** Default token lifetime when Keycloak omits {@code expires_in}, matching Python's default 300. */
    private static final long DEFAULT_EXPIRES_IN = 300;

    private final WebClient keycloakWebClient;
    private final LoggingService loggingService;
    private final ObjectMapper objectMapper;

    /**
     * Base Keycloak URL, e.g. {@code http://keycloak:8080/realms/master}.
     * Mirrors Python's {@code KEYCLOAK_URL = os.getenv("KEYCLOAK_AUTH_URL", ...)}.
     */
    private final String keycloakUrl;
    private final String keycloakAdmin;
    private final String keycloakAdminPassword;

    // --- admin token cache (mirrors Python _admin_token_cache) ---
    private final ReentrantLock tokenLock = new ReentrantLock();
    private String cachedToken;
    private Instant cachedTokenExpiresAt;

    public KeycloakEventsService(
            @Qualifier("keycloakWebClient") WebClient keycloakWebClient,
            LoggingService loggingService,
            ObjectMapper objectMapper,
            @Value("${jaddar.keycloak-auth-url:http://keycloak:8080/realms/master}") String keycloakAuthUrl,
            @Value("${keycloak.admin:admin}") String keycloakAdmin,
            @Value("${keycloak.admin-password:admin}") String keycloakAdminPassword) {
        this.keycloakWebClient = keycloakWebClient;
        this.loggingService = loggingService;
        this.objectMapper = objectMapper;
        this.keycloakUrl = keycloakAuthUrl;
        this.keycloakAdmin = keycloakAdmin;
        this.keycloakAdminPassword = keycloakAdminPassword;
    }

    /**
     * Get (and cache) an admin token for the Keycloak Admin API.
     *
     * <p>Mirrors Python {@code get_keycloak_admin_token}: caches the token and refreshes when
     * expired. On HTTP failure raises a 503 {@code "Failed to authenticate with Keycloak"}.
     */
    public String getKeycloakAdminToken() {
        tokenLock.lock();
        try {
            if (cachedToken != null
                    && cachedTokenExpiresAt != null
                    && Instant.now().isBefore(cachedTokenExpiresAt)) {
                return cachedToken;
            }

            MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
            form.add("client_id", "admin-cli");
            form.add("username", keycloakAdmin);
            form.add("password", keycloakAdminPassword);
            form.add("grant_type", "password");

            try {
                Map<String, Object> data = keycloakWebClient.post()
                        .uri(URI.create(keycloakUrl + "/protocol/openid-connect/token"))
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .body(BodyInserters.fromFormData(form))
                        .retrieve()
                        .bodyToMono(new org.springframework.core.ParameterizedTypeReference<Map<String, Object>>() {})
                        .block(EVENTS_TIMEOUT);

                if (data == null || data.get("access_token") == null) {
                    throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE,
                            "Failed to authenticate with Keycloak");
                }

                String accessToken = String.valueOf(data.get("access_token"));
                long expiresIn = data.get("expires_in") instanceof Number
                        ? ((Number) data.get("expires_in")).longValue()
                        : DEFAULT_EXPIRES_IN;

                cachedToken = accessToken;
                cachedTokenExpiresAt = Instant.now().plusSeconds(expiresIn - EXPIRY_BUFFER_SECONDS);

                return accessToken;
            } catch (WebClientResponseException | java.io.UncheckedIOException
                     | IllegalStateException e) {
                log.error("Failed to get Keycloak admin token: {}", e.getMessage());
                throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE,
                        "Failed to authenticate with Keycloak");
            } catch (RuntimeException e) {
                // WebFlux wraps connection errors in various runtime exceptions.
                log.error("Failed to get Keycloak admin token: {}", e.getMessage());
                throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE,
                        "Failed to authenticate with Keycloak");
            }
        } finally {
            tokenLock.unlock();
        }
    }

    /** Get the Keycloak Admin API base URL (strips {@code /realms/master}). */
    public String getAdminApiUrl() {
        return keycloakUrl.replace("/realms/master", "");
    }

    // ------------------------------------------------------------------
    // GET /api/admin/keycloak/events
    // ------------------------------------------------------------------
    public Map<String, Object> getKeycloakEvents(String type,
                                                 String client,
                                                 String user,
                                                 String ipAddress,
                                                 String dateFrom,
                                                 String dateTo,
                                                 int first,
                                                 int max,
                                                 String adminSub) {
        try {
            String token = getKeycloakAdminToken();
            String adminUrl = getAdminApiUrl();

            UriComponentsBuilder ub = UriComponentsBuilder
                    .fromHttpUrl(adminUrl + "/admin/realms/master/events")
                    .queryParam("first", first)
                    .queryParam("max", max);
            if (notBlank(type)) ub.queryParam("type", type);
            if (notBlank(client)) ub.queryParam("client", client);
            if (notBlank(user)) ub.queryParam("user", user);
            if (notBlank(ipAddress)) ub.queryParam("ipAddress", ipAddress);
            if (notBlank(dateFrom)) ub.queryParam("dateFrom", dateFrom);
            if (notBlank(dateTo)) ub.queryParam("dateTo", dateTo);

            List<Map<String, Object>> events = getEventList(ub.build(true).toUri(), token);

            // Log this admin action.
            Map<String, Object> details = new LinkedHashMap<>();
            details.put("event_type_filter", type);
            details.put("client_filter", client);
            details.put("results_count", events.size());
            loggingService.logAdminAction("view_keycloak_events", adminSub, null, null, details);

            Map<String, Object> filters = new LinkedHashMap<>();
            filters.put("type", type);
            filters.put("client", client);
            filters.put("user", user);
            filters.put("ip_address", ipAddress);
            filters.put("date_from", dateFrom);
            filters.put("date_to", dateTo);

            Map<String, Object> result = new LinkedHashMap<>();
            result.put("events", events);
            result.put("count", events.size());
            result.put("first", first);
            result.put("max", max);
            result.put("filters", filters);
            return result;
        } catch (ApiException e) {
            // 503 from token acquisition propagates unchanged.
            throw e;
        } catch (RuntimeException e) {
            log.error("Failed to fetch Keycloak events: {}", e.getMessage());
            throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR,
                    "Failed to fetch Keycloak events: " + e.getMessage());
        }
    }

    // ------------------------------------------------------------------
    // GET /api/admin/keycloak/events/introspection
    // ------------------------------------------------------------------
    public Map<String, Object> getIntrospectionEvents(String client,
                                                      String ipAddress,
                                                      String dateFrom,
                                                      String dateTo,
                                                      boolean includeErrors,
                                                      int first,
                                                      int max) {
        try {
            String token = getKeycloakAdminToken();
            String adminUrl = getAdminApiUrl();

            List<Map<String, Object>> allEvents = new ArrayList<>();

            // Successful introspection events.
            allEvents.addAll(getEventList(
                    introspectionUri(adminUrl, "INTROSPECT_TOKEN", client, ipAddress, dateFrom, dateTo, first, max),
                    token));

            // Failed introspection events, if requested.
            if (includeErrors) {
                allEvents.addAll(getEventList(
                        introspectionUri(adminUrl, "INTROSPECT_TOKEN_ERROR", client, ipAddress, dateFrom, dateTo, first, max),
                        token));
            }

            // Sort by time (newest first).
            allEvents.sort(Comparator.comparingLong(this::eventTime).reversed());

            // Apply pagination after merge.
            int end = Math.min(max, allEvents.size());
            List<Map<String, Object>> paginated = new ArrayList<>(allEvents.subList(0, Math.max(0, end)));

            Map<String, Object> result = new LinkedHashMap<>();
            result.put("events", paginated);
            result.put("count", paginated.size());
            result.put("total_fetched", allEvents.size());
            result.put("first", first);
            result.put("max", max);
            result.put("include_errors", includeErrors);
            return result;
        } catch (ApiException e) {
            throw e;
        } catch (RuntimeException e) {
            log.error("Failed to fetch introspection events: {}", e.getMessage());
            throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR,
                    "Failed to fetch introspection events: " + e.getMessage());
        }
    }

    private URI introspectionUri(String adminUrl, String type, String client, String ipAddress,
                                 String dateFrom, String dateTo, int first, int max) {
        UriComponentsBuilder ub = UriComponentsBuilder
                .fromHttpUrl(adminUrl + "/admin/realms/master/events")
                .queryParam("first", first)
                .queryParam("max", max)
                .queryParam("type", type);
        if (notBlank(client)) ub.queryParam("client", client);
        if (notBlank(ipAddress)) ub.queryParam("ipAddress", ipAddress);
        if (notBlank(dateFrom)) ub.queryParam("dateFrom", dateFrom);
        if (notBlank(dateTo)) ub.queryParam("dateTo", dateTo);
        return ub.build(true).toUri();
    }

    // ------------------------------------------------------------------
    // GET /api/admin/keycloak/events/logins
    // ------------------------------------------------------------------
    public Map<String, Object> getLoginEvents(String client,
                                              String user,
                                              String ipAddress,
                                              boolean includeErrors,
                                              int first,
                                              int max) {
        try {
            String token = getKeycloakAdminToken();
            String adminUrl = getAdminApiUrl();

            List<Map<String, Object>> allEvents = new ArrayList<>();

            // Successful logins.
            allEvents.addAll(getEventList(
                    loginUri(adminUrl, "LOGIN", client, user, ipAddress, first, max), token));

            // Failed logins, if requested.
            if (includeErrors) {
                allEvents.addAll(getEventList(
                        loginUri(adminUrl, "LOGIN_ERROR", client, user, ipAddress, first, max), token));
            }

            // Sort by time (newest first).
            allEvents.sort(Comparator.comparingLong(this::eventTime).reversed());
            int end = Math.min(max, allEvents.size());
            List<Map<String, Object>> paginated = new ArrayList<>(allEvents.subList(0, Math.max(0, end)));

            Map<String, Object> result = new LinkedHashMap<>();
            result.put("events", paginated);
            result.put("count", paginated.size());
            result.put("include_errors", includeErrors);
            return result;
        } catch (ApiException e) {
            throw e;
        } catch (RuntimeException e) {
            log.error("Failed to fetch login events: {}", e.getMessage());
            throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR,
                    "Failed to fetch login events: " + e.getMessage());
        }
    }

    private URI loginUri(String adminUrl, String type, String client, String user,
                         String ipAddress, int first, int max) {
        UriComponentsBuilder ub = UriComponentsBuilder
                .fromHttpUrl(adminUrl + "/admin/realms/master/events")
                .queryParam("first", first)
                .queryParam("max", max)
                .queryParam("type", type);
        if (notBlank(client)) ub.queryParam("client", client);
        if (notBlank(user)) ub.queryParam("user", user);
        if (notBlank(ipAddress)) ub.queryParam("ipAddress", ipAddress);
        return ub.build(true).toUri();
    }

    // ------------------------------------------------------------------
    // GET /api/admin/keycloak/events/types  (static, no Keycloak call)
    // ------------------------------------------------------------------
    public Map<String, Object> getEventTypes() {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("login_events", List.of(
                "LOGIN", "LOGIN_ERROR", "LOGOUT", "LOGOUT_ERROR",
                "CODE_TO_TOKEN", "CODE_TO_TOKEN_ERROR", "REFRESH_TOKEN", "REFRESH_TOKEN_ERROR",
                "INTROSPECT_TOKEN", "INTROSPECT_TOKEN_ERROR", "TOKEN_EXCHANGE", "TOKEN_EXCHANGE_ERROR",
                "CLIENT_LOGIN", "CLIENT_LOGIN_ERROR", "REGISTER", "REGISTER_ERROR",
                "UPDATE_PASSWORD", "UPDATE_PROFILE", "VERIFY_EMAIL", "FEDERATED_IDENTITY_LINK",
                "IDENTITY_PROVIDER_LOGIN", "IMPERSONATE", "CUSTOM_REQUIRED_ACTION", "PERMISSION_TOKEN"));
        result.put("admin_events", List.of("CREATE", "UPDATE", "DELETE", "ACTION"));
        return result;
    }

    // ------------------------------------------------------------------
    // GET /api/admin/keycloak/admin-events
    // ------------------------------------------------------------------
    public Map<String, Object> getAdminEvents(String operationType,
                                              String resourceType,
                                              String resourcePath,
                                              String authRealm,
                                              String authClient,
                                              String authUser,
                                              String dateFrom,
                                              String dateTo,
                                              int first,
                                              int max) {
        try {
            String token = getKeycloakAdminToken();
            String adminUrl = getAdminApiUrl();

            UriComponentsBuilder ub = UriComponentsBuilder
                    .fromHttpUrl(adminUrl + "/admin/realms/master/admin-events")
                    .queryParam("first", first)
                    .queryParam("max", max);
            if (notBlank(operationType)) ub.queryParam("operationTypes", operationType);
            if (notBlank(resourceType)) ub.queryParam("resourceTypes", resourceType);
            if (notBlank(resourcePath)) ub.queryParam("resourcePath", resourcePath);
            if (notBlank(authRealm)) ub.queryParam("authRealm", authRealm);
            if (notBlank(authClient)) ub.queryParam("authClient", authClient);
            if (notBlank(authUser)) ub.queryParam("authUser", authUser);
            if (notBlank(dateFrom)) ub.queryParam("dateFrom", dateFrom);
            if (notBlank(dateTo)) ub.queryParam("dateTo", dateTo);

            List<Map<String, Object>> events = getEventList(ub.build(true).toUri(), token);

            Map<String, Object> result = new LinkedHashMap<>();
            result.put("events", events);
            result.put("count", events.size());
            result.put("first", first);
            result.put("max", max);
            return result;
        } catch (ApiException e) {
            throw e;
        } catch (RuntimeException e) {
            log.error("Failed to fetch admin events: {}", e.getMessage());
            throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR,
                    "Failed to fetch admin events: " + e.getMessage());
        }
    }

    // ------------------------------------------------------------------
    // DELETE /api/admin/keycloak/events
    // ------------------------------------------------------------------
    public Map<String, Object> clearKeycloakEvents(String adminSub) {
        try {
            String token = getKeycloakAdminToken();
            String adminUrl = getAdminApiUrl();

            keycloakWebClient.delete()
                    .uri(URI.create(adminUrl + "/admin/realms/master/events"))
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                    .retrieve()
                    .toBodilessEntity()
                    .block(EVENTS_TIMEOUT);

            Map<String, Object> details = new LinkedHashMap<>();
            details.put("type", "login_events");
            loggingService.logAdminAction("clear_keycloak_events", adminSub, null, null, details);

            Map<String, Object> result = new LinkedHashMap<>();
            result.put("message", "Keycloak events cleared successfully");
            return result;
        } catch (ApiException e) {
            throw e;
        } catch (RuntimeException e) {
            log.error("Failed to clear Keycloak events: {}", e.getMessage());
            throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR,
                    "Failed to clear events: " + e.getMessage());
        }
    }

    // ------------------------------------------------------------------
    // GET /api/admin/keycloak/events/config
    // ------------------------------------------------------------------
    public Map<String, Object> getEventsConfig() {
        try {
            String token = getKeycloakAdminToken();
            String adminUrl = getAdminApiUrl();

            return getJsonObject(URI.create(adminUrl + "/admin/realms/master/events/config"), token);
        } catch (ApiException e) {
            throw e;
        } catch (RuntimeException e) {
            log.error("Failed to fetch events config: {}", e.getMessage());
            throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR,
                    "Failed to fetch events config: " + e.getMessage());
        }
    }

    // ------------------------------------------------------------------
    // PUT /api/admin/keycloak/events/config
    // ------------------------------------------------------------------
    @SuppressWarnings("unchecked")
    public Map<String, Object> updateEventsConfig(boolean eventsEnabled,
                                                  int eventsExpiration,
                                                  boolean adminEventsEnabled,
                                                  boolean adminEventsDetails) {
        try {
            String token = getKeycloakAdminToken();
            String adminUrl = getAdminApiUrl();

            // First get current config.
            Map<String, Object> config =
                    getJsonObject(URI.create(adminUrl + "/admin/realms/master/events/config"), token);

            // Update config.
            config.put("eventsEnabled", eventsEnabled);
            config.put("eventsExpiration", eventsExpiration);
            config.put("adminEventsEnabled", adminEventsEnabled);
            config.put("adminEventsDetailsEnabled", adminEventsDetails);

            // Ensure introspection events are included.
            Object enabledTypesObj = config.get("enabledEventTypes");
            List<Object> enabledEventTypes;
            if (!(enabledTypesObj instanceof List) || ((List<Object>) enabledTypesObj).isEmpty()) {
                enabledEventTypes = new ArrayList<>();
            } else {
                enabledEventTypes = new ArrayList<>((List<Object>) enabledTypesObj);
            }
            for (String eventType : List.of("INTROSPECT_TOKEN", "INTROSPECT_TOKEN_ERROR")) {
                if (!enabledEventTypes.contains(eventType)) {
                    enabledEventTypes.add(eventType);
                }
            }
            config.put("enabledEventTypes", enabledEventTypes);

            // Save config.
            keycloakWebClient.put()
                    .uri(URI.create(adminUrl + "/admin/realms/master/events/config"))
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(config)
                    .retrieve()
                    .toBodilessEntity()
                    .block(EVENTS_TIMEOUT);

            Map<String, Object> details = new LinkedHashMap<>();
            details.put("events_enabled", eventsEnabled);
            details.put("admin_events_enabled", adminEventsEnabled);
            loggingService.logAdminAction("update_keycloak_events_config", null, null, null, details);

            Map<String, Object> result = new LinkedHashMap<>();
            result.put("message", "Events configuration updated");
            result.put("config", config);
            return result;
        } catch (ApiException e) {
            throw e;
        } catch (RuntimeException e) {
            log.error("Failed to update events config: {}", e.getMessage());
            throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR,
                    "Failed to update events config: " + e.getMessage());
        }
    }

    // ------------------------------------------------------------------
    // GET /api/admin/keycloak/health  (no admin auth)
    // ------------------------------------------------------------------
    public Map<String, Object> keycloakHealthCheck() {
        try {
            String token = getKeycloakAdminToken();
            String adminUrl = getAdminApiUrl();

            Map<String, Object> realmInfo =
                    getJsonObject(URI.create(adminUrl + "/admin/realms/master"), token, HEALTH_TIMEOUT);

            Map<String, Object> result = new LinkedHashMap<>();
            result.put("status", "healthy");
            result.put("keycloak_url", adminUrl);
            result.put("realm", realmInfo.get("realm"));
            result.put("events_enabled", realmInfo.getOrDefault("eventsEnabled", false));
            result.put("admin_events_enabled", realmInfo.getOrDefault("adminEventsEnabled", false));
            return result;
        } catch (Exception e) {
            // Python's health check catches *all* exceptions and returns a 200 body.
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("status", "unhealthy");
            result.put("error", e.getMessage());
            return result;
        }
    }

    // ------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------

    private static boolean notBlank(String s) {
        return s != null && !s.isEmpty();
    }

    /** Keycloak events expose a numeric {@code time} (epoch ms); missing -> 0, matching Python's {@code .get("time", 0)}. */
    private long eventTime(Map<String, Object> event) {
        Object t = event.get("time");
        return t instanceof Number ? ((Number) t).longValue() : 0L;
    }

    /** GET a JSON array of event objects with a bearer token (30s timeout). */
    private List<Map<String, Object>> getEventList(URI uri, String token) {
        String body = keycloakWebClient.get()
                .uri(uri)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .retrieve()
                .bodyToMono(String.class)
                .block(EVENTS_TIMEOUT);
        try {
            if (body == null || body.isBlank()) {
                return new ArrayList<>();
            }
            return objectMapper.readValue(body, new TypeReference<List<Map<String, Object>>>() {});
        } catch (Exception e) {
            throw new RuntimeException(e.getMessage(), e);
        }
    }

    private Map<String, Object> getJsonObject(URI uri, String token) {
        return getJsonObject(uri, token, EVENTS_TIMEOUT);
    }

    /** GET a JSON object with a bearer token. */
    private Map<String, Object> getJsonObject(URI uri, String token, Duration timeout) {
        String body = keycloakWebClient.get()
                .uri(uri)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .retrieve()
                .bodyToMono(String.class)
                .block(timeout);
        try {
            if (body == null || body.isBlank()) {
                return new LinkedHashMap<>();
            }
            return objectMapper.readValue(body, new TypeReference<LinkedHashMap<String, Object>>() {});
        } catch (Exception e) {
            throw new RuntimeException(e.getMessage(), e);
        }
    }
}
