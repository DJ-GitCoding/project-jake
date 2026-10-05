/*
 * SPDX-FileCopyrightText: 2025-2026 Edgemoor Research Institute and Derek Jenkins
 * SPDX-License-Identifier: AGPL-3.0-only
 *
 * Author: Derek Jenkins <derek@pure-code.net>
 * Additional terms under AGPL-3.0 Section 7 apply. See NOTICE at the repository root.
 */

package com.requestormanager.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.requestormanager.entity.SubscriptionCredential;
import com.requestormanager.entity.SubscriptionRequest;
import com.requestormanager.repository.SubscriptionCredentialRepository;
import com.requestormanager.service.RequestorUserFieldService.MemberFields;
import com.requestormanager.service.RequestorUserFieldService.RequiredField;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestTemplate;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Slf4j
public class RequestorIntrospectionService {

    public static final String USER_FIELDS_CLAIM = "user_fields";
    public static final String MISSING_FIELDS_CLAIM = "missing_user_fields";

    private final SubscriptionCredentialRepository credentialRepository;
    private final RequestorUserFieldService userFieldService;
    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;

    /** The active introspection client a Basic Authorization header proves, if it proves one. */
    @Transactional(readOnly = true)
    public Optional<SubscriptionCredential> authenticateClient(String authorization) {
        if (authorization == null || !authorization.regionMatches(true, 0, "Basic ", 0, 6)) return Optional.empty();
        String decoded;
        try {
            decoded = new String(Base64.getDecoder().decode(authorization.substring(6).trim()), StandardCharsets.UTF_8);
        } catch (IllegalArgumentException e) {
            return Optional.empty();
        }
        int colon = decoded.indexOf(':');
        if (colon <= 0) return Optional.empty();
        String clientId = decoded.substring(0, colon);
        String secret = decoded.substring(colon + 1);
        return credentialRepository.findByKeycloakClientId(clientId)
                .filter(c -> Boolean.TRUE.equals(c.getIsActive()))
                .filter(c -> c.getClientSecret() != null && MessageDigest.isEqual(
                        c.getClientSecret().getBytes(StandardCharsets.UTF_8), secret.getBytes(StandardCharsets.UTF_8)));
    }

    /** Introspect a token for the client, adding the member information its template requires. */
    @Transactional(readOnly = true)
    public Map<String, Object> introspect(SubscriptionCredential client, String token) {
        SubscriptionRequest subscription = client.getSubscriptionRequest();

        Map<String, Object> response = upstreamIntrospection(client, token);
        if (!Boolean.TRUE.equals(response.get("active"))) return response;

        if (isServiceAccount(response)) return response;

        String sub = response.get("sub") instanceof String s ? s : null;
        if (sub != null) {
            Map<String, String> memberValues = userFieldService.memberValues(sub,
                    str(response.get("given_name")), str(response.get("family_name")), str(response.get("email")));
            addMemberFields(response, userFieldService.memberFields(subscription, memberValues));
        }
        return response;
    }

    /** Pass a client-credentials grant through to the identity provider the client lives in. */
    public ResponseEntity<String> clientCredentialsToken(SubscriptionCredential client) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);
        headers.setBasicAuth(client.getKeycloakClientId(), client.getClientSecret());
        MultiValueMap<String, String> body = new LinkedMultiValueMap<>();
        body.add("grant_type", "client_credentials");
        try {
            ResponseEntity<String> upstream = restTemplate.exchange(client.getTokenUrl(), HttpMethod.POST,
                    new HttpEntity<>(body, headers), String.class);
            return ResponseEntity.status(upstream.getStatusCode()).contentType(MediaType.APPLICATION_JSON)
                    .body(upstream.getBody());
        } catch (HttpStatusCodeException e) {
            return ResponseEntity.status(e.getStatusCode()).contentType(MediaType.APPLICATION_JSON)
                    .body(e.getResponseBodyAsString());
        }
    }

    private void addMemberFields(Map<String, Object> response, MemberFields fields) {
        response.put(USER_FIELDS_CLAIM, fields.values());
        response.put(MISSING_FIELDS_CLAIM, fields.missing().stream().map(RequiredField::key).toList());
    }

    private Map<String, Object> upstreamIntrospection(SubscriptionCredential client, String token) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);
        headers.setBasicAuth(client.getKeycloakClientId(), client.getClientSecret());
        MultiValueMap<String, String> body = new LinkedMultiValueMap<>();
        body.add("token", token);
        try {
            ResponseEntity<String> upstream = restTemplate.exchange(client.getIntrospectionUrl(), HttpMethod.POST,
                    new HttpEntity<>(body, headers), String.class);
            if (upstream.getBody() == null) return inactive();
            return new LinkedHashMap<>(objectMapper.readValue(upstream.getBody(), new TypeReference<Map<String, Object>>() {}));
        } catch (Exception e) {
            log.warn("Upstream introspection for client {} failed: {}", client.getKeycloakClientId(), e.getMessage());
            return inactive();
        }
    }

    private static boolean isServiceAccount(Map<String, Object> response) {
        Object username = response.get("username") != null ? response.get("username") : response.get("preferred_username");
        return username instanceof String u && u.startsWith("service-account-");
    }

    private static String str(Object value) {
        return value instanceof String s ? s : null;
    }

    /** RFC 7662 §2.2: an inactive token is reported with nothing but active: false. */
    public static Map<String, Object> inactive() {
        Map<String, Object> inactive = new LinkedHashMap<>();
        inactive.put("active", false);
        return inactive;
    }
}
