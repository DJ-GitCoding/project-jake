/*
 * SPDX-FileCopyrightText: 2025-2026 Edgemoor Research Institute and Derek Jenkins
 * SPDX-License-Identifier: AGPL-3.0-only
 *
 * Author: Derek Jenkins <derek@pure-code.net>
 * Additional terms under AGPL-3.0 Section 7 apply. See NOTICE at the repository root.
 */

package com.requestormanager.service;

import com.requestormanager.config.MtlsEndpoints;
import com.requestormanager.dto.DataHolderGroupDto;
import com.requestormanager.dto.SubscriptionCredentialDto;
import com.requestormanager.entity.DataHolderGroup;
import com.requestormanager.repository.DataHolderGroupRepository;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Service for communicating with external Data Holder APIs.
 * Handles all outbound calls to data holder endpoints.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class DataHolderGroupClientService {

    private final DataHolderGroupRepository dataHolderGroupRepository;
    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;
    private final MtlsEndpoints mtlsEndpoints;

    /**
     * Generic, non-leaking messages surfaced to callers when an outbound call to a data holder
     * fails at the transport level or throws unexpectedly. Raw exception detail (URLs, hosts,
     * stack traces, upstream bodies) is logged server-side only and never placed in these DTOs.
     */
    private static final String DH_UNREACHABLE_MESSAGE = "Could not reach the data holder. Please try again later.";
    private static final String DH_ERROR_MESSAGE = "An unexpected error occurred while contacting the data holder.";

    /**
     * Get available templates from a specific data holder
     */
    public List<DataHolderGroupDto.TemplateInfo> getTemplates(Long dataHolderGroupId) {
        DataHolderGroup dataHolderGroup = dataHolderGroupRepository.findById(dataHolderGroupId)
                .orElseThrow(() -> new RuntimeException("Data holder group not found: " + dataHolderGroupId));
        
        return getTemplates(dataHolderGroup);
    }

    /**
     * Get available templates from a specific data holder
     */
    public List<DataHolderGroupDto.TemplateInfo> getTemplates(DataHolderGroup dataHolderGroup) {
        String url = mtlsEndpoints.resolve(dataHolderGroup.getExternalApiUrl()) + "/templates";
        
        try {
            HttpHeaders headers = createHeaders(dataHolderGroup);
            HttpEntity<Void> request = new HttpEntity<>(headers);
            
            ResponseEntity<String> response = restTemplate.exchange(
                    url,
                    HttpMethod.GET,
                    request,
                    String.class
            );
            
            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                List<Map<String, Object>> templates = objectMapper.readValue(
                        response.getBody(),
                        new TypeReference<List<Map<String, Object>>>() {}
                );
                
                return templates.stream()
                        .map(t -> mapToTemplateInfo(t, dataHolderGroup))
                        .collect(Collectors.toList());
            }
        } catch (Exception e) {
            log.error("Failed to get templates from {}: {}", dataHolderGroup.getCode(), e.getMessage());
        }
        
        return Collections.emptyList();
    }

    /**
     * Get all templates from all active data holders
     */
    public List<DataHolderGroupDto.TemplateInfo> getAllTemplates() {
        List<DataHolderGroup> activeDataHolderGroups = dataHolderGroupRepository.findByActiveTrue();
        List<DataHolderGroupDto.TemplateInfo> allTemplates = new ArrayList<>();
        
        for (DataHolderGroup dh : activeDataHolderGroups) {
            try {
                List<DataHolderGroupDto.TemplateInfo> templates = getTemplates(dh);
                allTemplates.addAll(templates);
            } catch (Exception e) {
                log.warn("Failed to get templates from {}: {}", dh.getCode(), e.getMessage());
            }
        }
        
        return allTemplates;
    }

    /**
     * Get a specific template from a data holder
     */
    public Optional<DataHolderGroupDto.TemplateInfo> getTemplate(Long dataHolderGroupId, String templateId) {
        DataHolderGroup dataHolderGroup = dataHolderGroupRepository.findById(dataHolderGroupId)
                .orElseThrow(() -> new RuntimeException("Data holder group not found: " + dataHolderGroupId));
        
        String url = mtlsEndpoints.resolve(dataHolderGroup.getExternalApiUrl()) + "/templates/" + templateId;
        
        try {
            HttpHeaders headers = createHeaders(dataHolderGroup);
            HttpEntity<Void> request = new HttpEntity<>(headers);
            
            ResponseEntity<String> response = restTemplate.exchange(
                    url,
                    HttpMethod.GET,
                    request,
                    String.class
            );
            
            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                Map<String, Object> template = objectMapper.readValue(
                        response.getBody(),
                        new TypeReference<Map<String, Object>>() {}
                );
                return Optional.of(mapToTemplateInfo(template, dataHolderGroup));
            }
        } catch (Exception e) {
            log.error("Failed to get template {} from {}: {}", templateId, dataHolderGroup.getCode(), e.getMessage());
        }
        
        return Optional.empty();
    }

    /**
     * Initiate an agreement with a data holder
     */
    public DataHolderGroupDto.InitiationResponse initiateAgreement(
            Long dataHolderGroupId, 
            DataHolderGroupDto.InitiationRequest initiationRequest) {
        
        DataHolderGroup dataHolderGroup = dataHolderGroupRepository.findById(dataHolderGroupId)
                .orElseThrow(() -> new RuntimeException("Data holder group not found: " + dataHolderGroupId));
        
        String url = mtlsEndpoints.resolve(dataHolderGroup.getExternalApiUrl()) + "/initiate";
        
        try {
            HttpHeaders headers = createHeaders(dataHolderGroup);
            headers.setContentType(MediaType.APPLICATION_JSON);
            
            // Build request body matching the data holder's expected format
            Map<String, Object> requestBody = new LinkedHashMap<>();
            
            // Template info
            requestBody.put("templateId", initiationRequest.getTemplateId());
            
            // Requestor group info
            requestBody.put("requestorGroupId", initiationRequest.getRequestorGroupId());
            requestBody.put("requestorGroupName", initiationRequest.getRequestorGroupName());
            if (initiationRequest.getRequestorGroupCode() != null) {
                requestBody.put("requestorGroupCode", initiationRequest.getRequestorGroupCode());
            }
            if (initiationRequest.getRequestorGroupType() != null) {
                requestBody.put("requestorGroupType", initiationRequest.getRequestorGroupType());
            }
            requestBody.put("requestorAgentId", initiationRequest.getRequestorAgentId());
            
            // Contact information
            if (initiationRequest.getRequestorFirstName() != null) {
                requestBody.put("requestorFirstName", initiationRequest.getRequestorFirstName());
            }
            if (initiationRequest.getRequestorLastName() != null) {
                requestBody.put("requestorLastName", initiationRequest.getRequestorLastName());
            }
            if (initiationRequest.getRequestorOrganization() != null) {
                requestBody.put("requestorOrganization", initiationRequest.getRequestorOrganization());
            }
            requestBody.put("requestorContactEmail", initiationRequest.getContactEmail());
            if (initiationRequest.getRequestorPhone() != null) {
                requestBody.put("requestorPhone", initiationRequest.getRequestorPhone());
            }
            if (initiationRequest.getRequestorAddress() != null) {
                requestBody.put("requestorAddress", initiationRequest.getRequestorAddress());
            }
            if (initiationRequest.getRequestorCity() != null) {
                requestBody.put("requestorCity", initiationRequest.getRequestorCity());
            }
            if (initiationRequest.getRequestorStateProvince() != null) {
                requestBody.put("requestorStateProvince", initiationRequest.getRequestorStateProvince());
            }
            if (initiationRequest.getRequestorPostalCode() != null) {
                requestBody.put("requestorPostalCode", initiationRequest.getRequestorPostalCode());
            }
            if (initiationRequest.getRequestorCountry() != null) {
                requestBody.put("requestorCountry", initiationRequest.getRequestorCountry());
            }
            
            // Request details
            if (initiationRequest.getReasonForUse() != null) {
                requestBody.put("purpose", initiationRequest.getReasonForUse());
            }
            if (initiationRequest.getRequestedAccessLevel() != null) {
                requestBody.put("requestedAccessLevel", initiationRequest.getRequestedAccessLevel());
            }
            if (initiationRequest.getAdditionalNotes() != null) {
                requestBody.put("additionalTerms", initiationRequest.getAdditionalNotes());
            }
            
            // Callback URL
            if (initiationRequest.getCallbackUrl() != null) {
                requestBody.put("callbackUrl", initiationRequest.getCallbackUrl());
            }
            
            // Additional metadata
            if (initiationRequest.getMetadata() != null) {
                requestBody.put("metadata", initiationRequest.getMetadata());
            }
            
            HttpEntity<Map<String, Object>> request = new HttpEntity<>(requestBody, headers);
            
            log.debug("Sending initiation request to {}: {}", url, requestBody);
            
            ResponseEntity<String> response = restTemplate.exchange(
                    url,
                    HttpMethod.POST,
                    request,
                    String.class
            );
            
            if (response.getBody() != null) {
                Map<String, Object> responseBody = objectMapper.readValue(
                        response.getBody(),
                        new TypeReference<Map<String, Object>>() {}
                );
                
                return DataHolderGroupDto.InitiationResponse.builder()
                        .success(Boolean.TRUE.equals(responseBody.get("success")))
                        .requestId((String) responseBody.get("requestId"))
                        .status((String) responseBody.get("status"))
                        .message((String) responseBody.get("message"))
                        .dataHolderGroupCode(dataHolderGroup.getCode())
                        .dataholderAgentId((String) responseBody.get("dataholderAgentId"))
                        .statusCheckUrl((String) responseBody.get("statusCheckUrl"))
                        .build();
            }
        } catch (RestClientException e) {
            log.error("Failed to initiate agreement with {}: {}", dataHolderGroup.getCode(), e.getMessage(), e);
            return DataHolderGroupDto.InitiationResponse.builder()
                    .success(false)
                    .message(DH_UNREACHABLE_MESSAGE)
                    .dataHolderGroupCode(dataHolderGroup.getCode())
                    .build();
        } catch (Exception e) {
            log.error("Error initiating agreement with {}: {}", dataHolderGroup.getCode(), e.getMessage(), e);
            return DataHolderGroupDto.InitiationResponse.builder()
                    .success(false)
                    .message(DH_ERROR_MESSAGE)
                    .dataHolderGroupCode(dataHolderGroup.getCode())
                    .build();
        }
        
        return DataHolderGroupDto.InitiationResponse.builder()
                .success(false)
                .message("No response from data holder")
                .dataHolderGroupCode(dataHolderGroup.getCode())
                .build();
    }

    /**
     * Check status of an agreement request
     */
    public Optional<DataHolderGroupDto.StatusResponse> getAgreementStatus(Long dataHolderGroupId, String requestId) {
        DataHolderGroup dataHolderGroup = dataHolderGroupRepository.findById(dataHolderGroupId)
                .orElseThrow(() -> new RuntimeException("Data holder group not found: " + dataHolderGroupId));
        
        String url = mtlsEndpoints.resolve(dataHolderGroup.getExternalApiUrl()) + "/status/" + requestId;
        
        try {
            HttpHeaders headers = createHeaders(dataHolderGroup);
            HttpEntity<Void> request = new HttpEntity<>(headers);
            
            ResponseEntity<String> response = restTemplate.exchange(
                    url,
                    HttpMethod.GET,
                    request,
                    String.class
            );
            
            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                Map<String, Object> statusBody = objectMapper.readValue(
                        response.getBody(),
                        new TypeReference<Map<String, Object>>() {}
                );
                
                return Optional.of(DataHolderGroupDto.StatusResponse.builder()
                        .requestId((String) statusBody.get("requestId"))
                        .status((String) statusBody.get("status"))
                        .statusMessage((String) statusBody.get("statusMessage"))
                        .agreementId((String) statusBody.get("agreementId"))
                        .grantedAccessLevel(statusBody.get("grantedAccessLevel") != null ? 
                                ((Number) statusBody.get("grantedAccessLevel")).intValue() : null)
                        .message((String) statusBody.get("message"))
                        .dataHolderGroupCode(dataHolderGroup.getCode())
                        .updatedAt(LocalDateTime.now())
                        .testResult((String) statusBody.get("testResult"))
                        .testCompletedAt(statusBody.get("testCompletedAt") != null ?
                                statusBody.get("testCompletedAt").toString() : null)
                        .build());
            }
        } catch (Exception e) {
            log.error("Failed to get status from {}: {}", dataHolderGroup.getCode(), e.getMessage());
        }
        
        return Optional.empty();
    }

    // ==================== Testing Workflow Methods ====================

    /**
     * Request the data holder to start testing for a subscription
     */
    public DataHolderGroupDto.WorkflowActionResponse startTesting(Long dataHolderGroupId, String requestId, String initiatedBy) {
        DataHolderGroup dataHolderGroup = dataHolderGroupRepository.findById(dataHolderGroupId)
                .orElseThrow(() -> new RuntimeException("Data holder group not found: " + dataHolderGroupId));
        
        String url = mtlsEndpoints.resolve(dataHolderGroup.getExternalApiUrl()) + "/" + requestId + "/start-testing";
        
        try {
            HttpHeaders headers = createHeaders(dataHolderGroup);
            headers.setContentType(MediaType.APPLICATION_JSON);
            
            Map<String, Object> requestBody = new LinkedHashMap<>();
            requestBody.put("initiatedBy", initiatedBy);
            requestBody.put("source", "AGREEMENT_SERVER");
            
            HttpEntity<Map<String, Object>> request = new HttpEntity<>(requestBody, headers);
            
            log.info("Sending start-testing request to {} for subscription {}", dataHolderGroup.getCode(), requestId);
            
            ResponseEntity<String> response = restTemplate.exchange(
                    url,
                    HttpMethod.POST,
                    request,
                    String.class
            );
            
            if (response.getBody() != null) {
                Map<String, Object> responseBody = objectMapper.readValue(
                        response.getBody(),
                        new TypeReference<Map<String, Object>>() {}
                );
                
                return DataHolderGroupDto.WorkflowActionResponse.builder()
                        .success(Boolean.TRUE.equals(responseBody.get("success")))
                        .requestId((String) responseBody.get("requestId"))
                        .status((String) responseBody.get("status"))
                        .message((String) responseBody.get("message"))
                        .dataHolderGroupCode(dataHolderGroup.getCode())
                        .build();
            }
        } catch (RestClientException e) {
            log.error("Failed to start testing with {}: {}", dataHolderGroup.getCode(), e.getMessage(), e);
            return DataHolderGroupDto.WorkflowActionResponse.builder()
                    .success(false)
                    .requestId(requestId)
                    .message(DH_UNREACHABLE_MESSAGE)
                    .dataHolderGroupCode(dataHolderGroup.getCode())
                    .build();
        } catch (Exception e) {
            log.error("Error starting testing with {}: {}", dataHolderGroup.getCode(), e.getMessage(), e);
            return DataHolderGroupDto.WorkflowActionResponse.builder()
                    .success(false)
                    .requestId(requestId)
                    .message(DH_ERROR_MESSAGE)
                    .dataHolderGroupCode(dataHolderGroup.getCode())
                    .build();
        }
        
        return DataHolderGroupDto.WorkflowActionResponse.builder()
                .success(false)
                .requestId(requestId)
                .message("No response from data holder")
                .dataHolderGroupCode(dataHolderGroup.getCode())
                .build();
    }

    /**
     * Request the data holder to run tests for a subscription
     */
    public DataHolderGroupDto.TestExecutionResponse runTest(Long dataHolderGroupId, String requestId) {
        DataHolderGroup dataHolderGroup = dataHolderGroupRepository.findById(dataHolderGroupId)
                .orElseThrow(() -> new RuntimeException("Data holder group not found: " + dataHolderGroupId));
        
        String url = mtlsEndpoints.resolve(dataHolderGroup.getExternalApiUrl()) + "/" + requestId + "/run-test";
        
        try {
            HttpHeaders headers = createHeaders(dataHolderGroup);
            headers.setContentType(MediaType.APPLICATION_JSON);
            
            HttpEntity<Void> request = new HttpEntity<>(headers);
            
            log.info("Sending run-test request to {} for subscription {}", dataHolderGroup.getCode(), requestId);
            
            ResponseEntity<String> response = restTemplate.exchange(
                    url,
                    HttpMethod.POST,
                    request,
                    String.class
            );
            
            if (response.getBody() != null) {
                Map<String, Object> responseBody = objectMapper.readValue(
                        response.getBody(),
                        new TypeReference<Map<String, Object>>() {}
                );
                
                DataHolderGroupDto.TestResultInfo testResult = null;
                if (responseBody.get("testResult") != null) {
                    @SuppressWarnings("unchecked")
                    Map<String, Object> testResultMap = (Map<String, Object>) responseBody.get("testResult");
                    testResult = mapToTestResultInfo(testResultMap);
                }
                
                return DataHolderGroupDto.TestExecutionResponse.builder()
                        .success(Boolean.TRUE.equals(responseBody.get("success")))
                        .requestId((String) responseBody.get("requestId"))
                        .message((String) responseBody.get("message"))
                        .testResult(testResult)
                        .dataHolderGroupCode(dataHolderGroup.getCode())
                        .build();
            }
        } catch (RestClientException e) {
            log.error("Failed to run test with {}: {}", dataHolderGroup.getCode(), e.getMessage(), e);
            return DataHolderGroupDto.TestExecutionResponse.builder()
                    .success(false)
                    .requestId(requestId)
                    .message(DH_UNREACHABLE_MESSAGE)
                    .dataHolderGroupCode(dataHolderGroup.getCode())
                    .build();
        } catch (Exception e) {
            log.error("Error running test with {}: {}", dataHolderGroup.getCode(), e.getMessage(), e);
            return DataHolderGroupDto.TestExecutionResponse.builder()
                    .success(false)
                    .requestId(requestId)
                    .message(DH_ERROR_MESSAGE)
                    .dataHolderGroupCode(dataHolderGroup.getCode())
                    .build();
        }
        
        return DataHolderGroupDto.TestExecutionResponse.builder()
                .success(false)
                .requestId(requestId)
                .message("No response from data holder")
                .dataHolderGroupCode(dataHolderGroup.getCode())
                .build();
    }

    /**
     * Request the data holder to activate a subscription
     */
    public DataHolderGroupDto.WorkflowActionResponse activateSubscription(Long dataHolderGroupId, String requestId, String activatedBy) {
        DataHolderGroup dataHolderGroup = dataHolderGroupRepository.findById(dataHolderGroupId)
                .orElseThrow(() -> new RuntimeException("Data holder group not found: " + dataHolderGroupId));
        
        String url = mtlsEndpoints.resolve(dataHolderGroup.getExternalApiUrl()) + "/" + requestId + "/activate";
        
        try {
            HttpHeaders headers = createHeaders(dataHolderGroup);
            headers.setContentType(MediaType.APPLICATION_JSON);
            
            Map<String, Object> requestBody = new LinkedHashMap<>();
            requestBody.put("initiatedBy", activatedBy);
            requestBody.put("source", "AGREEMENT_SERVER");
            
            HttpEntity<Map<String, Object>> request = new HttpEntity<>(requestBody, headers);
            
            log.info("Sending activate request to {} for subscription {}", dataHolderGroup.getCode(), requestId);
            
            ResponseEntity<String> response = restTemplate.exchange(
                    url,
                    HttpMethod.POST,
                    request,
                    String.class
            );
            
            if (response.getBody() != null) {
                Map<String, Object> responseBody = objectMapper.readValue(
                        response.getBody(),
                        new TypeReference<Map<String, Object>>() {}
                );
                
                DataHolderGroupDto.WorkflowActionResponse.WorkflowActionResponseBuilder builder = 
                        DataHolderGroupDto.WorkflowActionResponse.builder()
                        .success(Boolean.TRUE.equals(responseBody.get("success")))
                        .requestId((String) responseBody.get("requestId"))
                        .status((String) responseBody.get("status"))
                        .message((String) responseBody.get("message"))
                        .agreementId((String) responseBody.get("agreementId"))
                        .dataHolderGroupCode(dataHolderGroup.getCode());
                
                if (responseBody.get("grantedAccessLevel") != null) {
                    builder.grantedAccessLevel(((Number) responseBody.get("grantedAccessLevel")).intValue());
                }
                
                // Parse dates if present
                if (responseBody.get("effectiveFrom") != null) {
                    builder.effectiveFrom(LocalDateTime.parse((String) responseBody.get("effectiveFrom")));
                }
                if (responseBody.get("effectiveTo") != null) {
                    builder.effectiveTo(LocalDateTime.parse((String) responseBody.get("effectiveTo")));
                }
                
                return builder.build();
            }
        } catch (RestClientException e) {
            log.error("Failed to activate subscription with {}: {}", dataHolderGroup.getCode(), e.getMessage(), e);
            return DataHolderGroupDto.WorkflowActionResponse.builder()
                    .success(false)
                    .requestId(requestId)
                    .message(DH_UNREACHABLE_MESSAGE)
                    .dataHolderGroupCode(dataHolderGroup.getCode())
                    .build();
        } catch (Exception e) {
            log.error("Error activating subscription with {}: {}", dataHolderGroup.getCode(), e.getMessage(), e);
            return DataHolderGroupDto.WorkflowActionResponse.builder()
                    .success(false)
                    .requestId(requestId)
                    .message(DH_ERROR_MESSAGE)
                    .dataHolderGroupCode(dataHolderGroup.getCode())
                    .build();
        }
        
        return DataHolderGroupDto.WorkflowActionResponse.builder()
                .success(false)
                .requestId(requestId)
                .message("No response from data holder")
                .dataHolderGroupCode(dataHolderGroup.getCode())
                .build();
    }

    // ==================== Credential Delivery ====================

    /**
     * Send introspection credentials to a data holder for a subscription.
     * POSTs to: {dataHolderGroup.externalApiUrl}/{requestId}/credentials
     */
    public SubscriptionCredentialDto.DeliveryResponse sendCredentials(
            Long dataHolderGroupId,
            String requestId,
            SubscriptionCredentialDto.DeliveryPayload payload) {

        DataHolderGroup dataHolderGroup = dataHolderGroupRepository.findById(dataHolderGroupId)
                .orElseThrow(() -> new RuntimeException("Data holder group not found: " + dataHolderGroupId));

        String url = mtlsEndpoints.resolve(dataHolderGroup.getExternalApiUrl()) + "/" + requestId + "/credentials";

        try {
            HttpHeaders headers = createHeaders(dataHolderGroup);
            headers.setContentType(MediaType.APPLICATION_JSON);

            HttpEntity<SubscriptionCredentialDto.DeliveryPayload> request =
                    new HttpEntity<>(payload, headers);

            log.info("Sending introspection credentials to {} for request {} (clientId: {})",
                    dataHolderGroup.getCode(), requestId, payload.getClientId());

            ResponseEntity<String> response = restTemplate.exchange(
                    url,
                    HttpMethod.POST,
                    request,
                    String.class
            );

            if (response.getBody() != null) {
                @SuppressWarnings("unchecked")
                Map<String, Object> responseBody = objectMapper.readValue(
                        response.getBody(),
                        new TypeReference<Map<String, Object>>() {}
                );

                return SubscriptionCredentialDto.DeliveryResponse.builder()
                        .success(Boolean.TRUE.equals(responseBody.get("success")))
                        .message((String) responseBody.get("message"))
                        .requestId((String) responseBody.get("requestId"))
                        .build();
            }

            if (response.getStatusCode().is2xxSuccessful()) {
                return SubscriptionCredentialDto.DeliveryResponse.builder()
                        .success(true)
                        .message("Accepted (no response body)")
                        .requestId(requestId)
                        .build();
            }

        } catch (RestClientException e) {
            log.error("Failed to send credentials to {}: {}", dataHolderGroup.getCode(), e.getMessage(), e);
            return SubscriptionCredentialDto.DeliveryResponse.builder()
                    .success(false)
                    .message(DH_UNREACHABLE_MESSAGE)
                    .requestId(requestId)
                    .build();
        } catch (Exception e) {
            log.error("Error sending credentials to {}: {}", dataHolderGroup.getCode(), e.getMessage(), e);
            return SubscriptionCredentialDto.DeliveryResponse.builder()
                    .success(false)
                    .message(DH_ERROR_MESSAGE)
                    .requestId(requestId)
                    .build();
        }

        return SubscriptionCredentialDto.DeliveryResponse.builder()
                .success(false)
                .message("No response from data holder")
                .requestId(requestId)
                .build();
    }

    // ==================== Health Check Methods ====================

    /**
     * Perform health check on a data holder
     */
    public DataHolderGroupDto.HealthResponse checkHealth(Long dataHolderGroupId) {
        DataHolderGroup dataHolderGroup = dataHolderGroupRepository.findById(dataHolderGroupId)
                .orElseThrow(() -> new RuntimeException("Data holder group not found: " + dataHolderGroupId));
        
        return checkHealth(dataHolderGroup);
    }

    /**
     * Perform health check on a data holder
     */
    public DataHolderGroupDto.HealthResponse checkHealth(DataHolderGroup dataHolderGroup) {
        String url = mtlsEndpoints.resolve(dataHolderGroup.getExternalApiUrl()) + "/health";
        
        try {
            HttpHeaders headers = createHeaders(dataHolderGroup);
            HttpEntity<Void> request = new HttpEntity<>(headers);
            
            ResponseEntity<String> response = restTemplate.exchange(
                    url,
                    HttpMethod.GET,
                    request,
                    String.class
            );
            
            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                Map<String, Object> healthBody = objectMapper.readValue(
                        response.getBody(),
                        new TypeReference<Map<String, Object>>() {}
                );
                
                // Update data holder status
                dataHolderGroup.setHealthStatus(DataHolderGroup.HealthStatus.HEALTHY);
                dataHolderGroup.setLastContactAt(LocalDateTime.now());
                dataHolderGroupRepository.save(dataHolderGroup);
                
                return DataHolderGroupDto.HealthResponse.builder()
                        .dataHolderGroupCode(dataHolderGroup.getCode())
                        .dataHolderGroupName(dataHolderGroup.getName())
                        .status((String) healthBody.get("status"))
                        .service((String) healthBody.get("service"))
                        .timestamp(healthBody.get("timestamp") != null ? 
                                ((Number) healthBody.get("timestamp")).longValue() : null)
                        .healthy(true)
                        .build();
            }
        } catch (Exception e) {
            log.error("Health check failed for {}: {}", dataHolderGroup.getCode(), e.getMessage(), e);

            // Update data holder status
            dataHolderGroup.setHealthStatus(DataHolderGroup.HealthStatus.UNHEALTHY);
            dataHolderGroupRepository.save(dataHolderGroup);

            return DataHolderGroupDto.HealthResponse.builder()
                    .dataHolderGroupCode(dataHolderGroup.getCode())
                    .dataHolderGroupName(dataHolderGroup.getName())
                    .healthy(false)
                    .errorMessage(DH_UNREACHABLE_MESSAGE)
                    .build();
        }
        
        dataHolderGroup.setHealthStatus(DataHolderGroup.HealthStatus.UNHEALTHY);
        dataHolderGroupRepository.save(dataHolderGroup);
        
        return DataHolderGroupDto.HealthResponse.builder()
                .dataHolderGroupCode(dataHolderGroup.getCode())
                .dataHolderGroupName(dataHolderGroup.getName())
                .healthy(false)
                .errorMessage("No response from data holder")
                .build();
    }

    /**
     * Check health of all active data holders
     */
    public List<DataHolderGroupDto.HealthResponse> checkAllHealth() {
        List<DataHolderGroup> activeDataHolderGroups = dataHolderGroupRepository.findByActiveTrue();
        return activeDataHolderGroups.stream()
                .map(this::checkHealth)
                .collect(Collectors.toList());
    }

    // ========== Helper Methods ==========

    private HttpHeaders createHeaders(DataHolderGroup dataHolderGroup) {
        HttpHeaders headers = new HttpHeaders();
        headers.setAccept(Collections.singletonList(MediaType.APPLICATION_JSON));
        // Add identifying headers
        headers.set("X-Requestor-Service", "requestor-manager");
        return headers;
    }

    @SuppressWarnings("unchecked")
    private DataHolderGroupDto.TemplateInfo mapToTemplateInfo(Map<String, Object> template, DataHolderGroup dataHolderGroup) {
        // Parse request types if present
        List<DataHolderGroupDto.RequestTypeInfo> requestTypes = null;
        if (template.get("requestTypes") != null) {
            List<Map<String, Object>> rtList = (List<Map<String, Object>>) template.get("requestTypes");
            requestTypes = rtList.stream()
                    .map(this::mapToRequestTypeInfo)
                    .collect(Collectors.toList());
        }

        return DataHolderGroupDto.TemplateInfo.builder()
                .templateId((String) template.get("templateId"))
                .name((String) template.get("name"))
                .description((String) template.get("description"))
                .accessLevel(template.get("accessLevel") != null ? 
                        ((Number) template.get("accessLevel")).intValue() : null)
                .requestTypes(requestTypes)
                .supportsConfidential(template.get("supportsConfidential") != null ?
                        (Boolean) template.get("supportsConfidential") : false)
                .supportsExigent(template.get("supportsExigent") != null ?
                        (Boolean) template.get("supportsExigent") : false)
                .version((String) template.get("version"))
                .requiredGroupTypes((String) template.get("requiredGroupTypes"))
                .termsAndConditions((String) template.get("termsAndConditions"))
                .dataUsagePolicy((String) template.get("dataUsagePolicy"))
                .maxQueriesPerDay(template.get("maxQueriesPerDay") != null ? 
                        ((Number) template.get("maxQueriesPerDay")).intValue() : null)
                .maxQueriesPerMonth(template.get("maxQueriesPerMonth") != null ? 
                        ((Number) template.get("maxQueriesPerMonth")).intValue() : null)
                .requiredFields((List<String>) template.get("requiredFields"))
                .dataHolderGroupCode(dataHolderGroup.getCode())
                .dataHolderGroupName(dataHolderGroup.getName())
                .dataHolderGroupUrl(dataHolderGroup.getBaseUrl())
                .build();
    }

    private DataHolderGroupDto.RequestTypeInfo mapToRequestTypeInfo(Map<String, Object> rt) {
        return DataHolderGroupDto.RequestTypeInfo.builder()
                .name((String) rt.get("name"))
                .description((String) rt.get("description"))
                .accessLevel(rt.get("accessLevel") != null ?
                        ((Number) rt.get("accessLevel")).intValue() : null)
                .supportsConfidential(rt.get("supportsConfidential") != null ?
                        (Boolean) rt.get("supportsConfidential") : false)
                .supportsExigent(rt.get("supportsExigent") != null ?
                        (Boolean) rt.get("supportsExigent") : false)
                .requiresManualApproval(rt.get("requiresManualApproval") != null ?
                        (Boolean) rt.get("requiresManualApproval") : true)
                .build();
    }

    /**
     * Map the raw JSON test result from the data holder to our DTO.
     * Handles both the original flat testCases and the new rdapTestResults/summary fields.
     */
    @SuppressWarnings("unchecked")
    private DataHolderGroupDto.TestResultInfo mapToTestResultInfo(Map<String, Object> testResult) {
        DataHolderGroupDto.TestResultInfo.TestResultInfoBuilder builder = DataHolderGroupDto.TestResultInfo.builder()
                .result((String) testResult.get("result"))
                .details((String) testResult.get("details"));
        
        // Parse testedAt
        if (testResult.get("testedAt") != null) {
            try {
                builder.testedAt(LocalDateTime.parse((String) testResult.get("testedAt")));
            } catch (Exception e) {
                log.warn("Failed to parse testedAt: {}", testResult.get("testedAt"));
            }
        }
        
        // Parse validation test cases (backward compatible)
        if (testResult.get("testCases") != null) {
            List<Map<String, Object>> testCaseMaps = (List<Map<String, Object>>) testResult.get("testCases");
            List<DataHolderGroupDto.TestCaseInfo> testCases = testCaseMaps.stream()
                    .map(tc -> DataHolderGroupDto.TestCaseInfo.builder()
                            .name((String) tc.get("name"))
                            .description((String) tc.get("description"))
                            .passed(Boolean.TRUE.equals(tc.get("passed")))
                            .errorMessage((String) tc.get("errorMessage"))
                            .build())
                    .collect(Collectors.toList());
            builder.testCases(testCases);
        }
        
        // Parse RDAP test results (new field from enhanced dataholder)
        if (testResult.get("rdapTestResults") != null) {
            List<Map<String, Object>> rdapResultMaps = (List<Map<String, Object>>) testResult.get("rdapTestResults");
            List<DataHolderGroupDto.RdapTestCaseInfo> rdapTestResults = rdapResultMaps.stream()
                    .map(this::mapToRdapTestCaseInfo)
                    .collect(Collectors.toList());
            builder.rdapTestResults(rdapTestResults);
        }
        
        // Parse summary (new field from enhanced dataholder)
        if (testResult.get("summary") != null) {
            Map<String, Object> summaryMap = (Map<String, Object>) testResult.get("summary");
            builder.summary(mapToTestSummaryInfo(summaryMap));
        }
        
        return builder.build();
    }

    /**
     * Map a single RDAP test case result from the dataholder's JSON response.
     */
    @SuppressWarnings("unchecked")
    private DataHolderGroupDto.RdapTestCaseInfo mapToRdapTestCaseInfo(Map<String, Object> rdapCase) {
        DataHolderGroupDto.RdapTestCaseInfo.RdapTestCaseInfoBuilder builder = DataHolderGroupDto.RdapTestCaseInfo.builder()
                .label((String) rdapCase.get("label"))
                .queryType((String) rdapCase.get("queryType"))
                .queryValue((String) rdapCase.get("queryValue"))
                .requestTypeName((String) rdapCase.get("requestTypeName"))
                .result((String) rdapCase.get("result"))
                .message((String) rdapCase.get("message"));

        if (rdapCase.get("testDataEntryId") != null) {
            builder.testDataEntryId(((Number) rdapCase.get("testDataEntryId")).longValue());
        }
        if (rdapCase.get("resolvedAccessLevel") != null) {
            builder.resolvedAccessLevel(((Number) rdapCase.get("resolvedAccessLevel")).intValue());
        }
        if (rdapCase.get("durationMs") != null) {
            builder.durationMs(((Number) rdapCase.get("durationMs")).longValue());
        }

        // Parse individual checks
        if (rdapCase.get("checks") != null) {
            List<Map<String, Object>> checkMaps = (List<Map<String, Object>>) rdapCase.get("checks");
            List<DataHolderGroupDto.RdapTestCheckInfo> checks = checkMaps.stream()
                    .map(this::mapToRdapTestCheckInfo)
                    .collect(Collectors.toList());
            builder.checks(checks);
        }

        return builder.build();
    }

    /**
     * Map a single RDAP test check from the dataholder's JSON response.
     */
    private DataHolderGroupDto.RdapTestCheckInfo mapToRdapTestCheckInfo(Map<String, Object> check) {
        return DataHolderGroupDto.RdapTestCheckInfo.builder()
                .name((String) check.get("name"))
                .category((String) check.get("category"))
                .passed(Boolean.TRUE.equals(check.get("passed")))
                .message((String) check.get("message"))
                .expected((String) check.get("expected"))
                .actual((String) check.get("actual"))
                .severity((String) check.get("severity"))
                .build();
    }

    /**
     * Map the test summary from the dataholder's JSON response.
     */
    private DataHolderGroupDto.TestSummaryInfo mapToTestSummaryInfo(Map<String, Object> summary) {
        DataHolderGroupDto.TestSummaryInfo.TestSummaryInfoBuilder builder = DataHolderGroupDto.TestSummaryInfo.builder();

        if (summary.get("totalValidationTests") != null) {
            builder.totalValidationTests(((Number) summary.get("totalValidationTests")).intValue());
        }
        if (summary.get("passedValidationTests") != null) {
            builder.passedValidationTests(((Number) summary.get("passedValidationTests")).intValue());
        }
        if (summary.get("failedValidationTests") != null) {
            builder.failedValidationTests(((Number) summary.get("failedValidationTests")).intValue());
        }
        if (summary.get("totalRdapTests") != null) {
            builder.totalRdapTests(((Number) summary.get("totalRdapTests")).intValue());
        }
        if (summary.get("passedRdapTests") != null) {
            builder.passedRdapTests(((Number) summary.get("passedRdapTests")).intValue());
        }
        if (summary.get("failedRdapTests") != null) {
            builder.failedRdapTests(((Number) summary.get("failedRdapTests")).intValue());
        }
        if (summary.get("skippedRdapTests") != null) {
            builder.skippedRdapTests(((Number) summary.get("skippedRdapTests")).intValue());
        }
        if (summary.get("errorRdapTests") != null) {
            builder.errorRdapTests(((Number) summary.get("errorRdapTests")).intValue());
        }
        if (summary.get("totalDurationMs") != null) {
            builder.totalDurationMs(((Number) summary.get("totalDurationMs")).longValue());
        }

        return builder.build();
    }
}