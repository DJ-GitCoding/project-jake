/*
 * SPDX-FileCopyrightText: 2025-2026 Edgemoor Research Institute and Derek Jenkins
 * SPDX-License-Identifier: AGPL-3.0-only
 *
 * Author: Derek Jenkins <derek@pure-code.net>
 * Additional terms under AGPL-3.0 Section 7 apply. See NOTICE at the repository root.
 */

package com.requestormanager.service;

import com.requestormanager.dto.RequestorGroupDto;
import com.requestormanager.entity.RequestorGroup;
import com.requestormanager.enums.UserType;
import com.requestormanager.exception.CustomExceptions.*;
import com.requestormanager.repository.RequestorGroupRepository;
import com.requestormanager.security.KeycloakAuthService.KeycloakUser;
import com.requestormanager.security.SecurityUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class RequestorGroupService {
    
    private final RequestorGroupRepository requestorGroupRepository;
    private final SecurityUtils securityUtils;
    private final KeycloakUserService keycloakUserService;

    /**
     * This deployment's Keycloak token-introspection endpoint. Used to auto-fill a new group's
     * {@code defaultIntrospectionUrl} when the caller doesn't supply one, so the value is always
     * environment-appropriate. Mirrors the {@code keycloak.introspect-url} that the provisioning
     * service already ships to data holders at activation.
     */
    @Value("${keycloak.introspect-url:http://keycloak:8080/realms/master/protocol/openid-connect/token/introspect}")
    private String configuredIntrospectUrl;

    @Transactional
    public RequestorGroupDto.RequestorGroupResponse createRequestorGroup(RequestorGroupDto.CreateRequest request) {
        KeycloakUser currentUser = securityUtils.requireCurrentUser();
        
        if (currentUser.getUserType() != UserType.JADDAR_MASTER_ADMIN && currentUser.getUserType() != UserType.GROUP_ADMIN) {
            throw new AccessDeniedException("Only Master and Admin users can create requestor groups");
        }
        
        if (requestorGroupRepository.existsByName(request.getName())) {
            throw new ResourceAlreadyExistsException("RequestorGroup", "name", request.getName());
        }

        /*
         * The code is not decoration: data holders resolve which subscription an RDAP
         * query belongs to by it, so a group without one cannot be authorised at all.
         */
        String code = normalizeCode(request.getCode());
        if (code == null) {
            throw new BadRequestException("A group code is required. Data holders identify this group by it.");
        }
        if (requestorGroupRepository.existsByCode(code)) {
            throw new ResourceAlreadyExistsException("RequestorGroup", "code", code);
        }

        /*
         * Without an introspection endpoint a data holder has no way to validate this
         * group's bearer tokens, so every query from it would be refused.
         */
        String introspectionUrl = request.getDefaultIntrospectionUrl() != null
                ? request.getDefaultIntrospectionUrl().trim() : "";
        if (introspectionUrl.isEmpty()) {
            throw new BadRequestException(
                    "A default introspection URL is required. Data holders validate this group's tokens with it.");
        }

        // The address prefills every subscription this group makes, so it is captured up front.
        requireAddressField(request.getDefaultAddress(), "A street address is required.");
        requireAddressField(request.getDefaultCity(), "A city is required.");
        requireAddressField(request.getDefaultStateProvince(), "A state or province is required.");
        requireAddressField(request.getDefaultPostalCode(), "A postal code is required.");
        requireAddressField(request.getDefaultCountry(), "A country is required.");
        
        keycloakUserService.createKeycloakGroup(request.getName());
        
        RequestorGroup group = RequestorGroup.builder()
                .name(request.getName())
                .code(code)
                .description(request.getDescription())
                .defaultIntrospectionUrl(introspectionUrl)
                .defaultAddress(trimToNull(request.getDefaultAddress()))
                .defaultCity(trimToNull(request.getDefaultCity()))
                .defaultStateProvince(trimToNull(request.getDefaultStateProvince()))
                .defaultPostalCode(trimToNull(request.getDefaultPostalCode()))
                .defaultCountry(trimToNull(request.getDefaultCountry()))
                .createdByKeycloakId(currentUser.getSub())
                .createdByEmail(currentUser.getEmail())
                .createdByName(currentUser.getDisplayName())
                .build();
        
        RequestorGroup savedGroup = requestorGroupRepository.save(group);
        log.info("Requestor group created: {} (code: {}) by {} (also created in Keycloak)",
                savedGroup.getName(), savedGroup.getCode(), currentUser.getEmail());
        
        return mapToResponse(savedGroup);
    }

    public String getDefaultIntrospectionUrl() {
        return configuredIntrospectUrl;
    }

    @Transactional(readOnly = true)
    public List<RequestorGroupDto.RequestorGroupResponse> getAllRequestorGroups() {
        KeycloakUser currentUser = securityUtils.requireCurrentUser();
        
        List<RequestorGroup> groups;
        if (currentUser.getUserType() == UserType.JADDAR_MASTER_ADMIN || currentUser.getUserType() == UserType.GROUP_ADMIN) {
            groups = requestorGroupRepository.findAll();
        } else {
            List<String> userGroups = currentUser.getGroups();
            if (userGroups == null || userGroups.isEmpty()) {
                groups = List.of();
            } else {
                groups = requestorGroupRepository.findByNameIn(userGroups);
            }
        }
        
        return groups.stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    /**
     * Paginated + searchable variant of {@link #getAllRequestorGroups()}.
     * Respects the same role scoping (admins see all; others only their groups).
     */
    @Transactional(readOnly = true)
    public Page<RequestorGroupDto.RequestorGroupResponse> getAllRequestorGroups(String search, Pageable pageable) {
        KeycloakUser currentUser = securityUtils.requireCurrentUser();
        String s = (search == null || search.isBlank()) ? null : search.trim();

        Page<RequestorGroup> page;
        if (currentUser.getUserType() == UserType.JADDAR_MASTER_ADMIN || currentUser.getUserType() == UserType.GROUP_ADMIN) {
            page = requestorGroupRepository.searchAll(s, pageable);
        } else {
            List<String> userGroups = currentUser.getGroups();
            if (userGroups == null || userGroups.isEmpty()) {
                return Page.empty(pageable);
            }
            page = requestorGroupRepository.searchByNames(userGroups, s, pageable);
        }
        return page.map(this::mapToResponse);
    }

    @Transactional(readOnly = true)
    public RequestorGroupDto.RequestorGroupResponse getRequestorGroupById(Long id) {
        KeycloakUser currentUser = securityUtils.requireCurrentUser();
        RequestorGroup group = requestorGroupRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("RequestorGroup", "id", id));
        
        validateGroupAccessPermission(currentUser, group);
        
        return mapToResponse(group);
    }
    
    @Transactional
    public RequestorGroupDto.RequestorGroupResponse updateRequestorGroup(
            Long id, RequestorGroupDto.UpdateRequest request) {
        KeycloakUser currentUser = securityUtils.requireCurrentUser();
        
        if (currentUser.getUserType() != UserType.JADDAR_MASTER_ADMIN && currentUser.getUserType() != UserType.GROUP_ADMIN) {
            throw new AccessDeniedException("Only Master and Admin users can update requestor groups");
        }
        
        RequestorGroup group = requestorGroupRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("RequestorGroup", "id", id));
        
        if (request.getName() != null && !request.getName().equals(group.getName())) {
            if (requestorGroupRepository.existsByName(request.getName())) {
                throw new ResourceAlreadyExistsException("RequestorGroup", "name", request.getName());
            }
            
            keycloakUserService.renameKeycloakGroup(group.getName(), request.getName());
            
            group.setName(request.getName());
        }

        // Handle code update
        if (request.getCode() != null) {
            String code = normalizeCode(request.getCode());
            if (code == null) {
                throw new BadRequestException("A group code is required. Data holders identify this group by it.");
            } else if (!code.equals(group.getCode())) {
                // Code changed — check uniqueness
                if (requestorGroupRepository.existsByCode(code)) {
                    throw new ResourceAlreadyExistsException("RequestorGroup", "code", code);
                }
                group.setCode(code);
            }
        }
        
        if (request.getDescription() != null) {
            group.setDescription(request.getDescription());
        }

        if (request.getDefaultIntrospectionUrl() != null) {
            if (request.getDefaultIntrospectionUrl().isBlank()) {
                throw new BadRequestException(
                        "A default introspection URL is required. Data holders validate this group's tokens with it.");
            }
            group.setDefaultIntrospectionUrl(request.getDefaultIntrospectionUrl().trim());
        }

        if (request.getDefaultAddress() != null) {
            requireAddressField(request.getDefaultAddress(), "A street address is required.");
            group.setDefaultAddress(request.getDefaultAddress().trim());
        }
        if (request.getDefaultCity() != null) {
            requireAddressField(request.getDefaultCity(), "A city is required.");
            group.setDefaultCity(request.getDefaultCity().trim());
        }
        if (request.getDefaultStateProvince() != null) {
            requireAddressField(request.getDefaultStateProvince(), "A state or province is required.");
            group.setDefaultStateProvince(request.getDefaultStateProvince().trim());
        }
        if (request.getDefaultPostalCode() != null) {
            requireAddressField(request.getDefaultPostalCode(), "A postal code is required.");
            group.setDefaultPostalCode(request.getDefaultPostalCode().trim());
        }
        if (request.getDefaultCountry() != null) {
            requireAddressField(request.getDefaultCountry(), "A country is required.");
            group.setDefaultCountry(request.getDefaultCountry().trim());
        }
        
        RequestorGroup savedGroup = requestorGroupRepository.save(group);
        log.info("Requestor group updated: {} (code: {}) by {}",
                savedGroup.getName(), savedGroup.getCode(), currentUser.getEmail());
        
        return mapToResponse(savedGroup);
    }
    
    @Transactional
    public void deleteRequestorGroup(Long id) {
        KeycloakUser currentUser = securityUtils.requireCurrentUser();
        
        if (currentUser.getUserType() != UserType.JADDAR_MASTER_ADMIN && currentUser.getUserType() != UserType.GROUP_ADMIN) {
            throw new AccessDeniedException("Only Master and Admin users can delete requestor groups");
        }
        
        RequestorGroup group = requestorGroupRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("RequestorGroup", "id", id));
        
        keycloakUserService.deleteKeycloakGroup(group.getName());
        
        requestorGroupRepository.delete(group);
        log.info("Requestor group deleted: {} by {} (also deleted from Keycloak)", group.getName(), currentUser.getEmail());
    }

    /**
     * Normalize a code value: trim, uppercase, return null if blank.
     */
    private void requireAddressField(String value, String message) {
        if (value == null || value.trim().isEmpty()) {
            throw new BadRequestException(message);
        }
    }

    private String trimToNull(String value) {
        if (value == null) return null;
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private String normalizeCode(String code) {
        if (code == null || code.trim().isEmpty()) {
            return null;
        }
        return code.trim().toUpperCase();
    }
    
    private boolean userBelongsToGroup(KeycloakUser user, RequestorGroup group) {
        if (user.getGroups() == null) {
            return false;
        }
        return user.getGroups().stream()
                .anyMatch(g -> g.equalsIgnoreCase(group.getName()));
    }
    
    private void validateGroupAccessPermission(KeycloakUser currentUser, RequestorGroup group) {
        if (currentUser.getUserType() == UserType.JADDAR_MASTER_ADMIN || currentUser.getUserType() == UserType.GROUP_ADMIN) {
            return;
        }
        
        if (userBelongsToGroup(currentUser, group)) {
            return;
        }
        
        throw new AccessDeniedException("You don't have permission to access this requestor group");
    }
    
    private RequestorGroupDto.RequestorGroupResponse mapToResponse(RequestorGroup group) {
        List<RequestorGroupDto.AgreementSummary> agreements = group.getDataHolderAgreements().stream()
                .map(a -> RequestorGroupDto.AgreementSummary.builder()
                        .id(a.getId())
                        .externalAgreementId(a.getExternalAgreementId())
                        .name(a.getName())
                        .dataHolderGroupCode(a.getDataHolderGroup().getCode())
                        .dataHolderGroupName(a.getDataHolderGroup().getName())
                        .status(a.getStatus().name())
                        .isCurrentlyEffective(a.isCurrentlyEffective())
                        .effectiveFrom(a.getEffectiveFrom())
                        .effectiveTo(a.getEffectiveTo())
                        .build())
                .toList();

        List<RequestorGroupDto.SubscriptionRequestSummary> subscriptionRequests = group.getSubscriptionRequests().stream()
                .map(sr -> RequestorGroupDto.SubscriptionRequestSummary.builder()
                        .id(sr.getId())
                        .internalRequestId(sr.getInternalRequestId())
                        .externalRequestId(sr.getExternalRequestId())
                        .templateName(sr.getTemplateName())
                        .dataHolderGroupCode(sr.getDataHolderGroup().getCode())
                        .dataHolderGroupName(sr.getDataHolderGroup().getName())
                        .status(sr.getStatus().name())
                        .pendingChangeStatus(sr.getPendingChangeStatus())
                        .createdAt(sr.getCreatedAt())
                        .statusChangedAt(sr.getStatusChangedAt())
                        .build())
                .toList();

        return RequestorGroupDto.RequestorGroupResponse.builder()
                .id(group.getId())
                .name(group.getName())
                .code(group.getCode())
                .description(group.getDescription())
                .defaultIntrospectionUrl(group.getDefaultIntrospectionUrl())
                .defaultAddress(group.getDefaultAddress())
                .defaultCity(group.getDefaultCity())
                .defaultStateProvince(group.getDefaultStateProvince())
                .defaultPostalCode(group.getDefaultPostalCode())
                .defaultCountry(group.getDefaultCountry())
                .agreementCount(agreements.size())
                .subscriptionRequestCount(subscriptionRequests.size())
                .agreements(agreements)
                .subscriptionRequests(subscriptionRequests)
                .createdByKeycloakId(group.getCreatedByKeycloakId())
                .createdByName(group.getCreatedByName())
                .createdByEmail(group.getCreatedByEmail())
                .createdAt(group.getCreatedAt())
                .updatedAt(group.getUpdatedAt())
                .build();
    }
}