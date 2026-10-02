/*
 * SPDX-FileCopyrightText: 2025-2026 Edgemoor Research Institute and Derek Jenkins
 * SPDX-License-Identifier: AGPL-3.0-only
 *
 * Author: Derek Jenkins <derek@pure-code.net>
 * Additional terms under AGPL-3.0 Section 7 apply. See NOTICE at the repository root.
 */

package com.jaddar.dhgroupadmin.service;

import com.jaddar.dhgroupadmin.entity.DataHolderInstance;
import com.jaddar.dhgroupadmin.repository.DataHolderInstanceRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Communicates with the provisioning sidecar to manage data holder instances.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ProvisioningService {

    private final DataHolderInstanceRepository instanceRepository;
    private final com.jaddar.dhgroupadmin.repository.DataHolderGroupRepository dataHolderGroupRepository;
    private final WebClient.Builder webClientBuilder;

    @Value("${provisioner.url:http://provisioning-sidecar:9500}")
    private String provisionerUrl;

    @Value("${provisioner.secret:change-me-in-production}")
    private String provisionerSecret;

    private WebClient client() {
        return webClientBuilder.baseUrl(provisionerUrl)
                .defaultHeader("X-Provisioner-Secret", provisionerSecret)
                .build();
    }

    // ─── Create ────────────────────────────────────────────────────

    public DataHolderInstance createInstance(String name, String subdomain,
                                             String dataholderId, Long dataHolderGroupId,
                                             java.util.List<Long> dataHolderGroupIds,
                                             String adminPassword) {
        if (instanceRepository.existsBySubdomain(subdomain)) {
            throw new IllegalArgumentException("Subdomain '" + subdomain + "' is already in use");
        }

        // Resolve effective group IDs: prefer list, fall back to single
        java.util.List<Long> effectiveGroupIds = (dataHolderGroupIds != null && !dataHolderGroupIds.isEmpty())
                ? dataHolderGroupIds
                : (dataHolderGroupId != null ? java.util.List.of(dataHolderGroupId) : java.util.List.of());

        Long primaryGroupId = !effectiveGroupIds.isEmpty() ? effectiveGroupIds.get(0) : null;

        log.info("Provisioning new instance: name={}, subdomain={}, groups={}", name, subdomain, effectiveGroupIds);

        Map<String, Object> body = Map.of(
                "name", name,
                "subdomain", subdomain,
                "dataholderId", dataholderId != null ? dataholderId : "",
                "dataHolderGroupId", primaryGroupId != null ? primaryGroupId : 0,
                "adminPassword", adminPassword != null ? adminPassword : "admin123"
        );

        @SuppressWarnings("unchecked")
        Map<String, Object> response = client().post()
                .uri("/api/provision")
                .bodyValue(body)
                .retrieve()
                .bodyToMono(Map.class)
                .timeout(Duration.ofMinutes(5))
                .block();

        if (response == null || !Boolean.TRUE.equals(response.get("success"))) {
            String error = response != null ? String.valueOf(response.get("error")) : "No response from provisioner";
            throw new RuntimeException("Provisioning failed: " + error);
        }

        @SuppressWarnings("unchecked")
        Map<String, Object> inst = (Map<String, Object>) response.get("instance");

        DataHolderInstance instance = DataHolderInstance.builder()
                .subdomain(subdomain)
                .name(name)
                .dataholderId(dataholderId)
                .dataHolderGroupId(primaryGroupId)
                .backendPort(((Number) inst.get("backendPort")).intValue())
                .frontendPort(((Number) inst.get("frontendPort")).intValue())
                .backendContainer((String) inst.get("backendContainer"))
                .frontendContainer((String) inst.get("frontendContainer"))
                .databaseName((String) inst.get("database"))
                .adminUsername((String) inst.get("adminUsername"))
                .adminPassword((String) inst.get("adminPassword"))
                .url((String) inst.get("url"))
                .status("running")
                .backendImage("jaddar-dataholder:latest")
                .frontendImage("jaddar-dataholder-frontend:latest")
                .build();

        // Populate multi-group memberships
        if (dataHolderGroupRepository != null && !effectiveGroupIds.isEmpty()) {
            java.util.Set<com.jaddar.dhgroupadmin.entity.DataHolderGroup> groups = new java.util.HashSet<>();
            for (Long gid : effectiveGroupIds) {
                dataHolderGroupRepository.findById(gid).ifPresent(groups::add);
            }
            instance.setDataHolderGroups(groups);
        }

        instance = instanceRepository.save(instance);
        log.info("Instance provisioned and saved: {} (id={})", subdomain, instance.getId());
        return instance;
    }

    // ─── Stop ──────────────────────────────────────────────────────

    public DataHolderInstance stopInstance(String subdomain) {
        DataHolderInstance instance = getOrThrow(subdomain);

        callSidecar("/api/instances/" + subdomain + "/stop", "POST");

        instance.setStatus("stopped");
        return instanceRepository.save(instance);
    }

    // ─── Start ─────────────────────────────────────────────────────

    public DataHolderInstance startInstance(String subdomain) {
        DataHolderInstance instance = getOrThrow(subdomain);

        callSidecar("/api/instances/" + subdomain + "/start", "POST");

        instance.setStatus("running");
        return instanceRepository.save(instance);
    }

    // ─── Teardown ──────────────────────────────────────────────────

    public void teardownInstance(String subdomain) {
        DataHolderInstance instance = getOrThrow(subdomain);

        client().delete()
                .uri("/api/instances/" + subdomain + "/teardown")
                .retrieve()
                .bodyToMono(Map.class)
                .timeout(Duration.ofMinutes(2))
                .block();

        instance.setStatus("destroyed");
        instanceRepository.save(instance);
        log.info("Instance torn down: {}", subdomain);
    }

    // ─── Update (new image version) ────────────────────────────────

    public DataHolderInstance updateInstance(String subdomain, String backendImage, String frontendImage) {
        DataHolderInstance instance = getOrThrow(subdomain);

        Map<String, Object> body = Map.of(
                "backendImage", backendImage != null ? backendImage : "jaddar-dataholder:latest",
                "frontendImage", frontendImage != null ? frontendImage : "jaddar-dataholder-frontend:latest"
        );

        callSidecarWithBody("/api/instances/" + subdomain + "/update", "POST", body);

        if (backendImage != null) instance.setBackendImage(backendImage);
        if (frontendImage != null) instance.setFrontendImage(frontendImage);
        return instanceRepository.save(instance);
    }

    // ─── Status from sidecar ───────────────────────────────────────

    @SuppressWarnings("unchecked")
    public Map<String, Object> getLiveStatus(String subdomain) {
        return client().get()
                .uri("/api/instances/" + subdomain + "/status")
                .retrieve()
                .bodyToMono(Map.class)
                .timeout(Duration.ofSeconds(10))
                .block();
    }

    // ─── List ──────────────────────────────────────────────────────

    public List<DataHolderInstance> listInstances() {
        return instanceRepository.findByStatusNot("destroyed");
    }

    public List<DataHolderInstance> listAllInstances() {
        return instanceRepository.findAll();
    }

    public Optional<DataHolderInstance> findBySubdomain(String subdomain) {
        return instanceRepository.findBySubdomain(subdomain);
    }

    // ─── Helpers ───────────────────────────────────────────────────

    private DataHolderInstance getOrThrow(String subdomain) {
        return instanceRepository.findBySubdomain(subdomain)
                .orElseThrow(() -> new IllegalArgumentException("Instance not found: " + subdomain));
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> callSidecar(String path, String method) {
        return client().method(org.springframework.http.HttpMethod.valueOf(method))
                .uri(path)
                .retrieve()
                .bodyToMono(Map.class)
                .timeout(Duration.ofMinutes(2))
                .block();
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> callSidecarWithBody(String path, String method, Object body) {
        return client().method(org.springframework.http.HttpMethod.valueOf(method))
                .uri(path)
                .bodyValue(body)
                .retrieve()
                .bodyToMono(Map.class)
                .timeout(Duration.ofMinutes(2))
                .block();
    }
}
