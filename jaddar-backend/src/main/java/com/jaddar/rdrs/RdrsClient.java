/*
 * SPDX-FileCopyrightText: 2025-2026 Edgemoor Research Institute and Derek Jenkins
 * SPDX-License-Identifier: AGPL-3.0-only
 *
 * Author: Derek Jenkins <derek@pure-code.net>
 * Additional terms under AGPL-3.0 Section 7 apply. See NOTICE at the repository root.
 */

package com.jaddar.rdrs;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jaddar.config.JaddarProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * Talks to ICANN's RDRS API.
 *
 * <p>RDRS authenticates with an {@code httpOnly} cookie, not a bearer token: an Okta
 * access token is exchanged at {@code /users/authenticate} for a {@code Set-Cookie:
 * accessToken=…}, and every later call must carry that cookie. The cookie is held
 * server-side by {@link RdrsSessionStore} and never reaches the browser.
 *
 * <p>The API base URL and the Okta app id come from ICANN's public, unauthenticated
 * {@code /api/config} rather than being hardcoded, because ICANN rotates both.
 */
@Component
@Slf4j
public class RdrsClient {

    private static final Duration TIMEOUT = Duration.ofSeconds(45);
    private static final String COOKIE_NAME = "accessToken";

    private final WebClient rdrs;
    private final JaddarProperties props;
    private final ObjectMapper objectMapper;

    /** Cached copy of ICANN's /api/config. */
    private volatile Map<String, Object> cachedConfig;
    private volatile Instant configFetchedAt = Instant.EPOCH;

    public RdrsClient(@Qualifier("rdrsWebClient") WebClient rdrs,
                      JaddarProperties props,
                      ObjectMapper objectMapper) {
        this.rdrs = rdrs;
        this.props = props;
        this.objectMapper = objectMapper;
    }

    // ─── ICANN app config ───────────────────────────────────────────

    /** Fetch and cache ICANN's public app config. */
    @SuppressWarnings("unchecked")
    public Map<String, Object> config() {
        Map<String, Object> cached = cachedConfig;
        long ttl = props.getRdrs().getConfigTtlSeconds();
        if (cached != null && Instant.now().isBefore(configFetchedAt.plusSeconds(ttl))) {
            return cached;
        }

        Map<String, Object> fresh = rdrs.get()
                .uri(props.getRdrs().getConfigUrl())
                .retrieve()
                .bodyToMono(Map.class)
                .block(TIMEOUT);

        if (fresh == null || fresh.get("restBackendUrl") == null) {
            throw new RdrsException("ICANN's RDRS configuration is unavailable", 503);
        }
        cachedConfig = fresh;
        configFetchedAt = Instant.now();
        log.info("Loaded RDRS config (backend={})", fresh.get("restBackendUrl"));
        return fresh;
    }

    public String apiBaseUrl() {
        return String.valueOf(config().get("restBackendUrl"));
    }

    /** The Okta application id RDRS expects alongside the access token. */
    public String appId() {
        Map<String, Object> cfg = config();
        Object appId = cfg.get("oktaApplicationId");
        return String.valueOf(appId != null ? appId : cfg.get("oktaClientId"));
    }

    // ─── Authentication ─────────────────────────────────────────────

    /**
     * Exchange an Okta access token for an RDRS session cookie.
     *
     * @return the {@code accessToken=…} cookie pair to send on later calls
     */
    public String authenticate(String oktaAccessToken, String appId) {
        String base = apiBaseUrl();
        ResponseEntity<Void> response;
        try {
            response = rdrs.post()
                    .uri(base + "/users/authenticate")
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(Map.of("accessToken", oktaAccessToken, "appId", appId))
                    .retrieve()
                    .toBodilessEntity()
                    .block(TIMEOUT);
        } catch (WebClientResponseException e) {
            log.warn("RDRS rejected the access token: {}", e.getStatusCode());
            throw new RdrsException("ICANN did not accept the sign-in", 502);
        }

        String cookie = extractCookie(response);
        if (cookie == null) {
            throw new RdrsException("ICANN did not return an RDRS session", 502);
        }
        return cookie;
    }

    /** Pull the accessToken cookie out of the Set-Cookie headers. */
    private String extractCookie(ResponseEntity<Void> response) {
        if (response == null) return null;
        List<String> setCookies = response.getHeaders().get(HttpHeaders.SET_COOKIE);
        if (setCookies == null) return null;
        for (String raw : setCookies) {
            String pair = raw.split(";", 2)[0].trim();
            if (pair.startsWith(COOKIE_NAME + "=") && pair.length() > COOKIE_NAME.length() + 1) {
                return pair;
            }
        }
        return null;
    }

    // ─── Authenticated calls ────────────────────────────────────────

    /** The signed-in user's RDRS profile, used to prefill the request form. */
    @SuppressWarnings("unchecked")
    public Map<String, Object> profile(RdrsSessionStore.Session session) {
        return get(session, "/users/profile", Map.class);
    }

    /**
     * Look a domain up. This is mandatory before creating a request: it is the only
     * source of lookupRecordId, registrarName and ianaId, and its {@code result} says
     * whether the registrar participates in RDRS at all.
     */
    public RdrsDto.LookupResponse lookup(RdrsSessionStore.Session session, String domain) {
        return get(session, "/requests/lookup/" + domain, RdrsDto.LookupResponse.class);
    }

    private <T> T get(RdrsSessionStore.Session session, String path, Class<T> type) {
        try {
            return rdrs.get()
                    .uri(session.getApiBaseUrl() + path)
                    .header(HttpHeaders.COOKIE, session.getCookie())
                    .accept(MediaType.APPLICATION_JSON)
                    .retrieve()
                    .bodyToMono(type)
                    .block(TIMEOUT);
        } catch (WebClientResponseException.Unauthorized | WebClientResponseException.Forbidden e) {
            throw refused("GET " + path, e);
        } catch (WebClientResponseException e) {
            log.warn("RDRS GET {} failed: {}", path, e.getStatusCode());
            throw new RdrsException("ICANN's RDRS service returned an error", 502);
        }
    }

    /**
     * Submit the request.
     *
     * <p>Multipart, with the JSON under a part literally named {@code command} plus up
     * to three optional file parts, exactly as ICANN's own client sends it.
     */
    public void create(RdrsSessionStore.Session session,
                       RdrsDto.Command command,
                       MultipartFile lawEnforcement,
                       MultipartFile powerOfAttorney,
                       List<MultipartFile> additionalAttachments) {

        MultiValueMap<String, Object> parts = new LinkedMultiValueMap<>();
        try {
            // ICANN's client sends this part as an application/json blob, not a string.
            byte[] json = objectMapper.writeValueAsBytes(command);
            parts.add("command", new org.springframework.http.HttpEntity<>(
                    new NamedResource(json, "blob"), jsonPartHeaders()));
        } catch (IOException e) {
            throw new RdrsException("Could not build the RDRS request", 500);
        }

        addFilePart(parts, "law_enforcement", lawEnforcement);
        addFilePart(parts, "power_of_attorney", powerOfAttorney);
        if (additionalAttachments != null) {
            for (MultipartFile file : additionalAttachments) {
                addFilePart(parts, "additional_attachments", file);
            }
        }

        try {
            rdrs.post()
                    .uri(session.getApiBaseUrl() + "/requests/create")
                    .contentType(MediaType.MULTIPART_FORM_DATA)
                    .header(HttpHeaders.COOKIE, session.getCookie())
                    .accept(MediaType.APPLICATION_JSON)
                    .body(BodyInserters.fromMultipartData(parts))
                    .retrieve()
                    .toBodilessEntity()
                    .block(TIMEOUT);
        } catch (WebClientResponseException.Unauthorized | WebClientResponseException.Forbidden e) {
            throw refused("POST /requests/create", e);
        } catch (WebClientResponseException e) {
            // RDRS answers 200 with an empty body on success.
            log.warn("RDRS create failed: {} {}", e.getStatusCode(), e.getResponseBodyAsString());
            throw new RdrsException("ICANN rejected the request. Check the details and try again.", 502);
        }
    }

    /**
     * Turn a 401/403 from ICANN into the right requestor-facing error.
     *
     */
    private RdrsException refused(String call, WebClientResponseException e) {
        HttpHeaders headers = e.getHeaders();
        MediaType type = headers.getContentType();
        boolean edgeBlock = headers.containsKey("cf-mitigated")
                || (type != null && type.isCompatibleWith(MediaType.TEXT_HTML));
        log.warn("RDRS {} refused: {} server={} cf-ray={} cf-mitigated={} type={} body={}",
                call, e.getStatusCode(), headers.getFirst(HttpHeaders.SERVER),
                headers.getFirst("cf-ray"), headers.getFirst("cf-mitigated"), type,
                trim(e.getResponseBodyAsString()));
        if (edgeBlock) {
            return new RdrsException("ICANN's RDRS service blocked the request before it reached "
                    + "your account. Please try again in a few minutes.", 502);
        }
        return new RdrsException("Your ICANN RDRS session has expired; please sign in again",
                403, RdrsException.SIGN_IN_REQUIRED);
    }

    private static String trim(String body) {
        if (body == null || body.isBlank()) return "<empty>";
        String flat = body.replaceAll("\\s+", " ").trim();
        return flat.length() > 300 ? flat.substring(0, 300) + "…" : flat;
    }

    private HttpHeaders jsonPartHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        return headers;
    }

    private void addFilePart(MultiValueMap<String, Object> parts, String name, MultipartFile file) {
        if (file == null || file.isEmpty()) return;
        try {
            String filename = file.getOriginalFilename() != null ? file.getOriginalFilename() : name;
            parts.add(name, new NamedResource(file.getBytes(), filename));
        } catch (IOException e) {
            throw new RdrsException("Could not read the attached file '" + file.getOriginalFilename() + "'", 400);
        }
    }

    /** ByteArrayResource that reports a filename, so multipart parts are named correctly. */
    private static class NamedResource extends ByteArrayResource {
        private final String filename;

        NamedResource(byte[] bytes, String filename) {
            super(bytes);
            this.filename = filename;
        }

        @Override
        public String getFilename() {
            return filename;
        }
    }
}
