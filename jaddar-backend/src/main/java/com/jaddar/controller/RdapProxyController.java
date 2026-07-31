/*
 * SPDX-FileCopyrightText: 2025-2026 Edgemoor Research Institute and Derek Jenkins
 * SPDX-License-Identifier: AGPL-3.0-only
 *
 * Author: Derek Jenkins <derek@pure-code.net>
 * Additional terms under AGPL-3.0 Section 7 apply. See NOTICE at the repository root.
 */

package com.jaddar.controller;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jaddar.entity.DataHolder;
import com.jaddar.repository.DataHolderRepository;
import com.jaddar.service.DataHolderUrlResolver;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientRequestException;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Backward-compatibility proxy routes ported from rdap_routes.py:
 *   - GET /api/rdap/jake-compliance   (proxy a data holder's JAKE compliance data)
 */
@Slf4j
@RestController
@RequestMapping("/api/rdap")
public class RdapProxyController {

    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {
    };

    private final WebClient rdapWebClient;
    private final DataHolderUrlResolver dataHolderUrlResolver;
    private final DataHolderRepository dataHolderRepository;
    private final ObjectMapper objectMapper;

    public RdapProxyController(@Qualifier("rdapWebClient") WebClient rdapWebClient,
                              DataHolderUrlResolver dataHolderUrlResolver,
                              DataHolderRepository dataHolderRepository,
                              ObjectMapper objectMapper) {
        this.rdapWebClient = rdapWebClient;
        this.dataHolderUrlResolver = dataHolderUrlResolver;
        this.dataHolderRepository = dataHolderRepository;
        this.objectMapper = objectMapper;
    }

    @GetMapping("/jake-compliance")
    public Map<String, Object> getJakeCompliance(
            @RequestParam(value = "data_holder_id", required = false) Long dataHolderId) {
        String holderName = null;
        String dataHolderUrl;
        try {
            if (dataHolderId != null) {
                Optional<DataHolder> holderOpt = dataHolderRepository.findById(dataHolderId);
                if (holderOpt.isEmpty()) {
                    return errorBody("Data holder " + dataHolderId + " not found");
                }
                DataHolder holder = holderOpt.get();
                if (holder.getBaseUrls() == null || holder.getBaseUrls().isEmpty()) {
                    return errorBody("Data holder '" + holder.getName() + "' has no base URLs configured");
                }
                holderName = holder.getName();
                dataHolderUrl = dataHolderUrlResolver.toInternalUrl(
                        dataHolderUrlResolver.stripRdapSuffix(holder.getBaseUrls().get(0)));
            } else {
                dataHolderUrl = dataHolderUrlResolver.getDataHolderUrl(null);
            }

            try {
                String body = rdapWebClient.get()
                        .uri(dataHolderUrl + "/api/rdap/jake-compliance")
                        .retrieve()
                        .bodyToMono(String.class)
                        .block(Duration.ofSeconds(15));
                return body != null ? objectMapper.readValue(body, MAP_TYPE) : new LinkedHashMap<>();
            } catch (WebClientResponseException e) {
                if (e.getStatusCode().value() == 404) {
                    String label = holderName != null ? "'" + holderName + "'" : "the default data holder";
                    return errorBody("Data holder " + label + " does not support JAKE compliance. "
                            + "This endpoint is only available on JAKE-enabled data holders.");
                }
                throw e;
            }
        } catch (WebClientRequestException e) {
            String label = holderName != null ? "'" + holderName + "'" : "server";
            return errorBody("Could not connect to data holder " + label + ". It may be offline.");
        } catch (Exception e) {
            log.error("Failed to fetch JAKE compliance", e);
            return errorBody("Unable to retrieve JAKE compliance data");
        }
    }

    private Map<String, Object> errorBody(String error) {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("error", error);
        out.put("dataHolderGroups", List.of());
        out.put("subscriptions", List.of());
        return out;
    }
}
