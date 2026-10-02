/*
 * SPDX-FileCopyrightText: 2025-2026 Edgemoor Research Institute and Derek Jenkins
 * SPDX-License-Identifier: AGPL-3.0-only
 *
 * Author: Derek Jenkins <derek@pure-code.net>
 * Additional terms under AGPL-3.0 Section 7 apply. See NOTICE at the repository root.
 */

package com.requestormanager.service;

import com.requestormanager.config.HttpSignatures;
import com.requestormanager.dto.DataHolderGroupDto;
import com.requestormanager.dto.SubscriptionCredentialDto;
import com.requestormanager.entity.DataHolderGroup;
import com.requestormanager.entity.SubscriptionRequest;
import com.requestormanager.enums.UserType;
import com.requestormanager.exception.CustomExceptions.AccessDeniedException;
import com.requestormanager.exception.CustomExceptions.ResourceNotFoundException;
import com.requestormanager.repository.DataHolderGroupRepository;
import com.requestormanager.repository.SubscriptionRequestRepository;
import com.requestormanager.security.KeycloakAuthService.KeycloakUser;
import com.requestormanager.security.SecurityUtils;
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
    private final SubscriptionRequestRepository subscriptionRequestRepository;
    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;
    private final SecurityUtils securityUtils;

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
        String url = dataHolderGroup.getExternalApiUrl() + "/templates";
        
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
        
        String url = dataHolderGroup.getExternalApiUrl() + "/templates/" + templateId;
        
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
        
        String url = dataHolderGroup.getExternalApiUrl() + "/initiate";
        
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
            if (initiationRequest.getRequestorDescription() != null) {
                requestBody.put("requestorDescription", initiationRequest.getRequestorDescription());
            }
            requestBody.put("requestorAgentId", initiationRequest.getRequestorAgentId());

            // Group-defined subscription fields and the legal sections accepted for them.
            if (initiationRequest.getSubscriptionFieldValues() != null) {
                requestBody.put("subscriptionFieldValues", initiationRequest.getSubscriptionFieldValues());
            }
            if (initiationRequest.getAcceptedLegalSectionIds() != null) {
                requestBody.put("acceptedLegalSectionIds", initiationRequest.getAcceptedLegalSectionIds());
            }
            
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

            // Without this the data holder cannot validate any bearer token from this group.
            if (initiationRequest.getIntrospectionUrl() != null) {
                requestBody.put("introspectionUrl", initiationRequest.getIntrospectionUrl());
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
        
        String url = dataHolderGroup.getExternalApiUrl() + "/status/" + requestId;
        
        try {
            HttpEntity<String> request = signedEntity(dataHolderGroup, requestId, "GET", url, null);
            
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
                        .message((String) statusBody.get("message"))
                        .dataHolderGroupCode(dataHolderGroup.getCode())
                        .updatedAt(LocalDateTime.now())
                        .testResult((String) statusBody.get("testResult"))
                        .testCompletedAt(statusBody.get("testCompletedAt") != null ?
                                statusBody.get("testCompletedAt").toString() : null)
                        .pendingChangeStatus((String) statusBody.get("pendingChangeStatus"))
                        .pendingChangeMode((String) statusBody.get("pendingChangeMode"))
                        .pendingProposedAt(parseTimestamp(statusBody.get("pendingProposedAt")))
                        .pendingChangeDeadline(parseTimestamp(statusBody.get("pendingChangeDeadline")))
                        .pendingTemplateSnapshot(asMap(statusBody.get("pendingTemplateSnapshot")))
                        .templateSnapshot(asMap(statusBody.get("templateSnapshot")))
                        .acceptedTerms(asMapList(statusBody.get("acceptedTerms")))
                        .subscriptionFieldValues(asMap(statusBody.get("subscriptionFieldValues")))
                        .build());
            }
        } catch (Exception e) {
            log.error("Failed to get status from {}: {}", dataHolderGroup.getCode(), e.getMessage());
        }
        
        return Optional.empty();
    }

    // ==================== Template Change Proposals ====================

    /** Resolves a subscription by external request id and checks the current user may act on it. */
    public SubscriptionRequest requireSubscriptionAccess(String externalRequestId, boolean requireGroupAdmin) {
        KeycloakUser currentUser = securityUtils.requireCurrentUser();
        SubscriptionRequest subscription = subscriptionRequestRepository.findByExternalRequestId(externalRequestId)
                .orElseThrow(() -> new ResourceNotFoundException("Agreement request not found"));
        UserType type = currentUser.getUserType();
        boolean global = type == UserType.JADDAR_MASTER_ADMIN || type == UserType.GROUP_ADMIN;
        if (!global) {
            String groupName = subscription.getRequestorGroup() != null
                    ? subscription.getRequestorGroup().getName() : null;
            boolean member = groupName != null && currentUser.getGroups() != null
                    && currentUser.getGroups().stream().anyMatch(g -> g.equalsIgnoreCase(groupName));
            if (!member) {
                throw new AccessDeniedException("You don't have permission to access this requestor group");
            }
            if (requireGroupAdmin && !type.canManageGroupContent()) {
                throw new AccessDeniedException("Only group admins can respond to a proposed change");
            }
        }
        return subscription;
    }

    /** Answer a template change the group admin proposed for this subscription. */
    public Map<String, Object> respondToPendingChange(Long dataHolderGroupId, String requestId, boolean accept,
                                                     Map<String, Object> subscriptionFieldValues) {
        requireSubscriptionAccess(requestId, true);
        DataHolderGroup dataHolderGroup = dataHolderGroupRepository.findById(dataHolderGroupId)
                .orElseThrow(() -> new RuntimeException("Data holder group not found: " + dataHolderGroupId));

        String action = accept ? "accept" : "decline";
        String url = dataHolderGroup.getExternalApiUrl()
                + "/subscriptions/" + requestId + "/pending-change/" + action;
        try {
            HttpHeaders headers = createHeaders(dataHolderGroup, requestId);
            headers.setContentType(MediaType.APPLICATION_JSON);
            Map<String, Object> body = new LinkedHashMap<>();
            if (accept && subscriptionFieldValues != null) {
                body.put("subscriptionFieldValues", subscriptionFieldValues);
            }
            ResponseEntity<Map> response = restTemplate.exchange(
                    url, HttpMethod.POST, signedEntity(dataHolderGroup, requestId, "POST", url, body), Map.class);
            log.info("{}ed proposed change on subscription {} at {}", action, requestId, dataHolderGroup.getCode());
            return response.getBody() != null ? response.getBody() : Map.of("success", true);
        } catch (Exception e) {
            log.error("Failed to {} proposed change for {}: {}", action, requestId, e.getMessage());
            throw new RuntimeException("Could not " + action + " the proposed change. Please try again.", e);
        }
    }

    // ==================== Testing Workflow Methods ====================

    /**
     * Request the data holder to start testing for a subscription
     */
    public DataHolderGroupDto.WorkflowActionResponse startTesting(Long dataHolderGroupId, String requestId, String initiatedBy) {
        DataHolderGroup dataHolderGroup = dataHolderGroupRepository.findById(dataHolderGroupId)
                .orElseThrow(() -> new RuntimeException("Data holder group not found: " + dataHolderGroupId));
        
        String url = dataHolderGroup.getExternalApiUrl() + "/" + requestId + "/start-testing";
        
        try {
            HttpHeaders headers = createHeaders(dataHolderGroup, requestId);
            headers.setContentType(MediaType.APPLICATION_JSON);
            
            Map<String, Object> requestBody = new LinkedHashMap<>();
            requestBody.put("initiatedBy", initiatedBy);
            requestBody.put("source", "AGREEMENT_SERVER");
            
            HttpEntity<String> request = signedEntity(dataHolderGroup, requestId, "POST", url, requestBody);
            
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
        
        String url = dataHolderGroup.getExternalApiUrl() + "/" + requestId + "/run-test";
        
        try {
            HttpHeaders headers = createHeaders(dataHolderGroup, requestId);
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
        
        String url = dataHolderGroup.getExternalApiUrl() + "/" + requestId + "/activate";
        
        try {
            HttpHeaders headers = createHeaders(dataHolderGroup, requestId);
            headers.setContentType(MediaType.APPLICATION_JSON);
            
            Map<String, Object> requestBody = new LinkedHashMap<>();
            requestBody.put("initiatedBy", activatedBy);
            requestBody.put("source", "AGREEMENT_SERVER");
            
            HttpEntity<String> request = signedEntity(dataHolderGroup, requestId, "POST", url, requestBody);
            
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

        String url = dataHolderGroup.getExternalApiUrl() + "/" + requestId + "/credentials";

        try {
            /*
             * Signed like the other subscription-scoped calls: this delivers the secret the
             * data holder will authenticate with, so the group admin has to be able to tell
             * it came from the requestor manager that owns this subscription.
             */
            HttpEntity<String> request = signedEntity(dataHolderGroup, requestId, "POST", url, payload);

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

    /**
     * Ask the data holder group whether a subscription already holds introspection credentials.
     *
     * GETs {dataHolderGroup.externalApiUrl}/{requestId}/credentials/status. The response reports
     * presence only — it never carries a client secret — so this can be polled by the
     * reconciliation job without moving secrets around.
     *
     * @return TRUE / FALSE when the group admin answered, or null when it could not be reached or
     *         does not know the subscription. A null means "unknown", and callers must not treat it
     *         as "missing" — re-sending credentials on an unknown state risks overwriting good ones.
     */
    public Boolean hasCredentials(Long dataHolderGroupId, String requestId) {
        DataHolderGroup dataHolderGroup = dataHolderGroupRepository.findById(dataHolderGroupId)
                .orElseThrow(() -> new RuntimeException("Data holder group not found: " + dataHolderGroupId));

        String url = dataHolderGroup.getExternalApiUrl()
                + "/" + requestId + "/credentials/status";

        try {
            ResponseEntity<String> response = restTemplate.exchange(
                    url, HttpMethod.GET, signedEntity(dataHolderGroup, requestId, "GET", url, null), String.class);

            if (!response.getStatusCode().is2xxSuccessful() || response.getBody() == null) {
                return null;
            }

            @SuppressWarnings("unchecked")
            Map<String, Object> body = objectMapper.readValue(
                    response.getBody(), new TypeReference<Map<String, Object>>() {});

            Object hasCredentials = body.get("hasCredentials");
            return hasCredentials instanceof Boolean b ? b : null;

        } catch (Exception e) {
            log.warn("Could not read credential status from {} for request {}: {}",
                    dataHolderGroup.getCode(), requestId, e.getMessage());
            return null;
        }
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
        String url = dataHolderGroup.getExternalApiUrl() + "/health";
        
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

    /**
     * Headers for a call about one subscription. Credentials issued for that subscription
     * take precedence; the group-level pair is the fallback for calls that are not
     * subscription-scoped, such as browsing templates.
     */
    private HttpHeaders createHeaders(DataHolderGroup dataHolderGroup, String externalRequestId) {
        HttpHeaders headers = createHeaders(dataHolderGroup);
        if (externalRequestId == null || externalRequestId.isBlank()) {
            return headers;
        }
        subscriptionRequestRepository.findByExternalRequestId(externalRequestId)
                .filter(sub -> sub.getDhgClientId() != null && !sub.getDhgClientId().isBlank()
                        && sub.getDhgClientSecret() != null && !sub.getDhgClientSecret().isBlank())
                .ifPresent(sub -> headers.setBasicAuth(sub.getDhgClientId(), sub.getDhgClientSecret()));
        return headers;
    }

    /**
     * Headers for a signed request, plus the exact body bytes to send.
     *
     * <p>The body is serialised once here and the digest is taken over those bytes, so what
     * the peer verifies is byte-for-byte what it received. Letting the HTTP client serialise
     * separately would risk a digest that does not match the payload.
     */
    private HttpEntity<String> signedEntity(DataHolderGroup group, String requestId,
                                            String method, String url, Object body) {
        HttpHeaders headers = createHeaders(group, requestId);
        String json = null;
        byte[] payload = new byte[0];
        if (body != null) {
            try {
                json = objectMapper.writeValueAsString(body);
                payload = json.getBytes(java.nio.charset.StandardCharsets.UTF_8);
                headers.setContentType(MediaType.APPLICATION_JSON);
            } catch (Exception e) {
                throw new IllegalStateException("Could not serialise request body: " + e.getMessage(), e);
            }
        }
        signRequest(headers, method, signedPath(url), payload, requestId);
        return new HttpEntity<>(json, headers);
    }

    /**
     * Signs an outbound request to a data holder group.
     *
     * <p>Nothing secret is transmitted: the group admin verifies against the public key we
     * registered with it. The signature covers the method, path and a digest of the body, so
     * it cannot be replayed against another endpoint or with altered content.
     */
    private void signRequest(HttpHeaders headers, String method, String path, byte[] body,
                             String externalRequestId) {
        if (externalRequestId == null || externalRequestId.isBlank()) return;
        subscriptionRequestRepository.findByExternalRequestId(externalRequestId)
                .filter(sub -> sub.getDhgPrivateKey() != null && !sub.getDhgPrivateKey().isBlank()
                        && sub.getDhgClientId() != null && !sub.getDhgClientId().isBlank())
                .ifPresent(sub -> {
                    try {
                        long created = java.time.Instant.now().getEpochSecond();
                        byte[] payload = body == null ? new byte[0] : body;
                        headers.set(HttpSignatures.CONTENT_DIGEST_HEADER, HttpSignatures.contentDigest(payload));
                        headers.set(HttpSignatures.SIGNATURE_INPUT_HEADER,
                                HttpSignatures.signatureInput(created, sub.getDhgClientId()));
                        headers.set(HttpSignatures.SIGNATURE_HEADER,
                                HttpSignatures.sign(method, path, payload, created,
                                        sub.getDhgClientId(), sub.getDhgPrivateKey()));
                    } catch (Exception e) {
                        log.error("Could not sign request for subscription {}: {}",
                                externalRequestId, e.getMessage());
                    }
                });
    }

    /** The path a signature covers: everything after the host and port. */
    private static String signedPath(String url) {
        try {
            return java.net.URI.create(url).getPath();
        } catch (Exception e) {
            return url;
        }
    }

    private HttpHeaders createHeaders(DataHolderGroup dataHolderGroup) {
        HttpHeaders headers = new HttpHeaders();
        headers.setAccept(Collections.singletonList(MediaType.APPLICATION_JSON));
        // Add identifying headers
        headers.set("X-Requestor-Service", "requestor-manager");
        /*
         * Credentials this group admin issued when it accepted our requestor group. They let
         * its external API resolve who is calling, instead of trusting a path or body value.
         * Absent for groups configured before credentials were exchanged.
         */
        if (dataHolderGroup != null
                && dataHolderGroup.getClientId() != null && !dataHolderGroup.getClientId().isBlank()
                && dataHolderGroup.getClientSecret() != null && !dataHolderGroup.getClientSecret().isBlank()) {
            headers.setBasicAuth(dataHolderGroup.getClientId(), dataHolderGroup.getClientSecret());
        }
        return headers;
    }

    /** An ISO timestamp from a group admin payload, or null when absent, blank or unparseable. */
    private LocalDateTime parseTimestamp(Object value) {
        if (value == null) return null;
        String text = value.toString().trim();
        if (text.isEmpty()) return null;
        try {
            return LocalDateTime.parse(text);
        } catch (Exception e) {
            log.debug("Ignoring unparseable timestamp '{}' from group admin", text);
            return null;
        }
    }

    /** A map field from a group admin payload, or null when absent or the wrong shape. */
    @SuppressWarnings("unchecked")
    private java.util.Map<String, Object> asMap(Object value) {
        return value instanceof java.util.Map<?, ?> m ? (java.util.Map<String, Object>) m : null;
    }

    /** A list-of-maps field from a group admin payload, or null when absent or the wrong shape. */
    @SuppressWarnings("unchecked")
    private java.util.List<java.util.Map<String, Object>> asMapList(Object value) {
        if (!(value instanceof java.util.List<?> list)) return null;
        java.util.List<java.util.Map<String, Object>> out = new java.util.ArrayList<>();
        for (Object o : list) {
            if (o instanceof java.util.Map<?, ?> m) out.add((java.util.Map<String, Object>) m);
        }
        return out;
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
                .requestTypes(requestTypes)
                .supportsConfidential(template.get("supportsConfidential") != null ?
                        (Boolean) template.get("supportsConfidential") : false)
                .supportsExigent(template.get("supportsExigent") != null ?
                        (Boolean) template.get("supportsExigent") : false)
                .version((String) template.get("version"))
                .requiredGroupTypes((String) template.get("requiredGroupTypes"))
                .legalSections(asMapList(template.get("legalSections")))
                .subscriptionFields(asMapList(template.get("subscriptionFields")))
                .maxQueriesPerDay(template.get("maxQueriesPerDay") != null ? 
                        ((Number) template.get("maxQueriesPerDay")).intValue() : null)
                .maxQueriesPerMonth(template.get("maxQueriesPerMonth") != null ? 
                        ((Number) template.get("maxQueriesPerMonth")).intValue() : null)
                .requiredFields((List<String>) template.get("requiredFields"))
                .dataHolderGroupCode(dataHolderGroup.getCode())
                .dataHolderGroupName(dataHolderGroup.getName())
                .dataHolderGroupUrl(dataHolderGroup.getBaseUrl())
                .contact(asMap(template.get("contact")))
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