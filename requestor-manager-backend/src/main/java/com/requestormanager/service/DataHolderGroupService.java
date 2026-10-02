/*
 * SPDX-FileCopyrightText: 2025-2026 Edgemoor Research Institute and Derek Jenkins
 * SPDX-License-Identifier: AGPL-3.0-only
 *
 * Author: Derek Jenkins <derek@pure-code.net>
 * Additional terms under AGPL-3.0 Section 7 apply. See NOTICE at the repository root.
 */

package com.requestormanager.service;

import com.requestormanager.dto.DataHolderGroupDto;
import com.requestormanager.exception.CustomExceptions.BadRequestException;
import com.requestormanager.exception.CustomExceptions.ResourceAlreadyExistsException;
import com.requestormanager.exception.CustomExceptions.ResourceNotFoundException;
import com.requestormanager.entity.DataHolderGroup;
import com.requestormanager.repository.DataHolderGroupRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Service for managing Data Holder registrations.
 */
@Service
@RequiredArgsConstructor
@Slf4j
@Transactional
public class DataHolderGroupService {

    private final DataHolderGroupRepository dataHolderGroupRepository;
    private final DataHolderGroupClientService clientService;

    /**
     * Get all data holder groups
     */
    @Transactional(readOnly = true)
    public List<DataHolderGroupDto.Response> getAllDataHolderGroups() {
        return withDuplicateFlags(dataHolderGroupRepository.findAll().stream()
                .map(DataHolderGroupDto.Response::fromEntity)
                .collect(Collectors.toList()));
    }

    /**
     * Paginated + searchable variant of the list endpoint.
     * Search covers name/code/description; activeOnly restricts to active groups.
     */
    @Transactional(readOnly = true)
    public Page<DataHolderGroupDto.Response> getDataHolderGroups(String search, boolean activeOnly, Pageable pageable) {
        String s = (search == null || search.isBlank()) ? null : search.trim();
        Page<DataHolderGroup> page = activeOnly
                ? dataHolderGroupRepository.searchActive(s, pageable)
                : dataHolderGroupRepository.searchAll(s, pageable);
        Page<DataHolderGroupDto.Response> mapped = page.map(DataHolderGroupDto.Response::fromEntity);
        withDuplicateFlags(mapped.getContent());
        return mapped;
    }

    /**
     * Get active data holder groups only
     */
    @Transactional(readOnly = true)
    public List<DataHolderGroupDto.Response> getActiveDataHolderGroups() {
        return withDuplicateFlags(dataHolderGroupRepository.findByActiveTrue().stream()
                .map(DataHolderGroupDto.Response::fromEntity)
                .collect(Collectors.toList()));
    }

    /**
     * Get healthy data holder groups only
     */
    @Transactional(readOnly = true)
    public List<DataHolderGroupDto.Response> getHealthyDataHolderGroups() {
        return dataHolderGroupRepository.findByActiveTrueAndHealthStatus(DataHolderGroup.HealthStatus.HEALTHY).stream()
                .map(DataHolderGroupDto.Response::fromEntity)
                .collect(Collectors.toList());
    }

    /**
     * Get data holder group by ID
     */
    @Transactional(readOnly = true)
    public DataHolderGroupDto.Response getDataHolderGroup(Long id) {
        DataHolderGroup dataHolderGroup = dataHolderGroupRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("DataHolderGroup", "id", id));
        return DataHolderGroupDto.Response.fromEntity(dataHolderGroup);
    }

    /** Marks every record whose code is claimed more than once, so neither is hidden. */
    private List<DataHolderGroupDto.Response> withDuplicateFlags(List<DataHolderGroupDto.Response> rows) {
        Set<String> duplicates = new HashSet<>(dataHolderGroupRepository.findDuplicateCodes());
        if (!duplicates.isEmpty()) {
            rows.forEach(r -> r.setDuplicateCode(duplicates.contains(r.getCode())));
        }
        return rows;
    }

    /**
     * Get data holder group by code
     */
    @Transactional(readOnly = true)
    public DataHolderGroupDto.Response getDataHolderGroupByCode(String code) {
        DataHolderGroup dataHolderGroup = dataHolderGroupRepository.findAllByCode(code).stream()
                .min(Comparator.comparing(g -> g.getSource() == DataHolderGroup.Source.MANUAL ? 0 : 1))
                .orElseThrow(() -> new ResourceNotFoundException("DataHolderGroup", "code", code));
        return DataHolderGroupDto.Response.fromEntity(dataHolderGroup);
    }

    /**
     * Create a new data holder group
     */
    public DataHolderGroupDto.Response createDataHolderGroup(DataHolderGroupDto.CreateRequest request) {
        // Normalize code to uppercase
        String code = request.getCode().toUpperCase();
        
        // Check for duplicate code
        if (dataHolderGroupRepository.existsByCodeAndSource(code, DataHolderGroup.Source.MANUAL)) {
            throw new ResourceAlreadyExistsException("DataHolderGroup", "code", code);
        }

        // Validate URL format
        String baseUrl = normalizeUrl(request.getBaseUrl());

        DataHolderGroup dataHolderGroup = DataHolderGroup.builder()
                .code(code)
                .name(request.getName())
                .description(request.getDescription())
                .baseUrl(baseUrl)
                .clientId(trimToNull(request.getClientId()))
                .clientSecret(trimToNull(request.getClientSecret()))
                .contactEmail(request.getContactEmail())
                .notes(request.getNotes())
                .active(request.getActive() != null ? request.getActive() : true)
                .healthStatus(DataHolderGroup.HealthStatus.UNKNOWN)
                .build();

        dataHolderGroup = dataHolderGroupRepository.save(dataHolderGroup);
        log.info("Created data holder group: {} ({})", dataHolderGroup.getName(), dataHolderGroup.getCode());

        return DataHolderGroupDto.Response.fromEntity(dataHolderGroup);
    }

    /**
     * Update an existing data holder group
     */
    public DataHolderGroupDto.Response updateDataHolderGroup(Long id, DataHolderGroupDto.UpdateRequest request) {
        DataHolderGroup dataHolderGroup = dataHolderGroupRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("DataHolderGroup", "id", id));

        if (request.getName() != null) {
            dataHolderGroup.setName(request.getName());
        }
        if (request.getDescription() != null) {
            dataHolderGroup.setDescription(request.getDescription());
        }
        if (request.getClientId() != null) {
            dataHolderGroup.setClientId(trimToNull(request.getClientId()));
        }
        if (request.getClientSecret() != null && !request.getClientSecret().isBlank()) {
            dataHolderGroup.setClientSecret(request.getClientSecret().trim());
        }
        if (request.getBaseUrl() != null) {
            dataHolderGroup.setBaseUrl(normalizeUrl(request.getBaseUrl()));
        }

        if (request.getContactEmail() != null) {
            dataHolderGroup.setContactEmail(request.getContactEmail());
        }
        if (request.getNotes() != null) {
            dataHolderGroup.setNotes(request.getNotes());
        }
        if (request.getActive() != null) {
            dataHolderGroup.setActive(request.getActive());
        }

        dataHolderGroup = dataHolderGroupRepository.save(dataHolderGroup);
        log.info("Updated data holder group: {} ({})", dataHolderGroup.getName(), dataHolderGroup.getCode());

        return DataHolderGroupDto.Response.fromEntity(dataHolderGroup);
    }

    /**
     * Delete a data holder group
     */
    public void deleteDataHolderGroup(Long id) {
        DataHolderGroup dataHolderGroup = dataHolderGroupRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("DataHolderGroup", "id", id));

        // TODO: Check for active agreements before deleting
        
        dataHolderGroupRepository.delete(dataHolderGroup);
        log.info("Deleted data holder group: {} ({})", dataHolderGroup.getName(), dataHolderGroup.getCode());
    }

    /**
     * Toggle active status of a data holder group
     */
    public DataHolderGroupDto.Response toggleActive(Long id) {
        DataHolderGroup dataHolderGroup = dataHolderGroupRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("DataHolderGroup", "id", id));

        dataHolderGroup.setActive(!dataHolderGroup.getActive());
        dataHolderGroup = dataHolderGroupRepository.save(dataHolderGroup);
        
        log.info("Toggled data holder group {} active status to: {}", dataHolderGroup.getCode(), dataHolderGroup.getActive());
        return DataHolderGroupDto.Response.fromEntity(dataHolderGroup);
    }

    /**
     * Perform health check on a data holder group
     */
    public DataHolderGroupDto.HealthResponse checkHealth(Long id) {
        return clientService.checkHealth(id);
    }

    /**
     * Perform health check on all active data holder groups
     */
    public List<DataHolderGroupDto.HealthResponse> checkAllHealth() {
        return clientService.checkAllHealth();
    }

    /**
     * Get templates from a specific data holder group
     */
    @Transactional(readOnly = true)
    public List<DataHolderGroupDto.TemplateInfo> getTemplates(Long id) {
        return clientService.getTemplates(id);
    }

    /**
     * Get templates from all active data holder groups
     */
    @Transactional(readOnly = true)
    public List<DataHolderGroupDto.TemplateInfo> getAllTemplates() {
        return clientService.getAllTemplates();
    }

    // ========== Helper Methods ==========

    private String normalizeUrl(String url) {
        if (url == null) return null;
        
        // Remove trailing slash
        url = url.trim();
        while (url.endsWith("/")) {
            url = url.substring(0, url.length() - 1);
        }
        
        // Validate URL format
        if (!url.startsWith("http://") && !url.startsWith("https://")) {
            throw new BadRequestException("Base URL must start with http:// or https://");
        }
        
        return url;
    }

    /** Blank input means "not supplied" rather than "set to empty". */
    private static String trimToNull(String value) {
        if (value == null) return null;
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
