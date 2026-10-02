/*
 * SPDX-FileCopyrightText: 2025-2026 Edgemoor Research Institute and Derek Jenkins
 * SPDX-License-Identifier: AGPL-3.0-only
 *
 * Author: Derek Jenkins <derek@pure-code.net>
 * Additional terms under AGPL-3.0 Section 7 apply. See NOTICE at the repository root.
 */

package com.jaddar.service;

import com.jaddar.dto.DataHolderCreate;
import com.jaddar.dto.DataHolderUpdate;
import com.jaddar.entity.DataHolder;
import com.jaddar.exception.ApiException;
import com.jaddar.repository.DataHolderRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Service for managing custom RDAP Data Holders (admin CRUD).
 *
 * <p>Ported from {@code backend/services/dataholder_service.py}. The pydantic
 * field-validators from {@code models/dataholder.py} are enforced here as manual
 * checks (base_urls scheme, asn_ranges pairs, ip_ranges CIDR, auth_type) — pydantic
 * validation failures surface as HTTP 422, so these throw
 * {@link ApiException}(UNPROCESSABLE_ENTITY).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DataHolderService {

    private static final List<String> ALLOWED_AUTH_TYPES = List.of("none", "bearer");

    private final DataHolderRepository repository;

    // ============================================================
    // Read
    // ============================================================

    @Transactional(readOnly = true)
    public List<DataHolder> listAll(boolean activeOnly, int limit, int offset) {
        // Python: query [filter is_active].order_by(name).offset(offset).limit(limit).
        // The contract guarantees only findByIsActiveTrue()/findAll()/count() on the
        // repository, so ordering + offset/limit are applied in-memory here.
        List<DataHolder> all = activeOnly
                ? new ArrayList<>(repository.findByIsActiveTrue())
                : new ArrayList<>(repository.findAll());
        all.sort(Comparator.comparing(DataHolder::getName,
                Comparator.nullsLast(Comparator.naturalOrder())));
        int from = Math.min(Math.max(offset, 0), all.size());
        int to = Math.min(from + Math.max(limit, 0), all.size());
        return new ArrayList<>(all.subList(from, to));
    }

    @Transactional(readOnly = true)
    public long count(boolean activeOnly) {
        return activeOnly ? repository.findByIsActiveTrue().size() : repository.count();
    }

    @Transactional(readOnly = true)
    public DataHolder getById(long holderId) {
        return repository.findById(holderId).orElse(null);
    }

    // ============================================================
    // Write
    // ============================================================

    @Transactional
    public DataHolder create(DataHolderCreate data, String createdBy) {
        validateBaseUrlsCreate(data.getBaseUrls());
        validateAsnRanges(data.getAsnRanges(), false);
        validateIpRanges(data.getIpRanges());
        validateAuthType(data.getAuthType(), false);

        DataHolder holder = new DataHolder();
        holder.setName(data.getName());
        holder.setDescription(data.getDescription());
        holder.setBaseUrls(data.getBaseUrls());
        holder.setTlds(data.getTlds() != null ? data.getTlds() : new ArrayList<>());
        holder.setIpRanges(data.getIpRanges() != null ? data.getIpRanges() : new ArrayList<>());
        holder.setAsnRanges(data.getAsnRanges() != null ? data.getAsnRanges() : new ArrayList<>());
        holder.setRequiresAuth(data.isRequiresAuth());
        holder.setAuthType(data.getAuthType());
        holder.setIsActive(data.isActive());
        holder.setCreatedBy(createdBy);

        holder = repository.save(holder);
        log.info("Created data holder: {} (id={})", holder.getName(), holder.getId());
        return holder;
    }

    @Transactional
    public DataHolder update(long holderId, DataHolderUpdate data) {
        DataHolder holder = getById(holderId);
        if (holder == null) {
            return null;
        }

        // Validate only the fields actually supplied (mirror pydantic field-validators).
        validateBaseUrlsUpdate(data.getBaseUrls());
        validateAsnRanges(data.getAsnRanges(), true);
        validateAuthType(data.getAuthType(), true);

        // Python: data.model_dump(exclude_unset=True) then skip None values.
        // Update DTO has no defaults, so "supplied" == non-null here.
        if (data.getName() != null) {
            holder.setName(data.getName());
        }
        if (data.getDescription() != null) {
            holder.setDescription(data.getDescription());
        }
        if (data.getBaseUrls() != null) {
            holder.setBaseUrls(data.getBaseUrls());
        }
        if (data.getTlds() != null) {
            holder.setTlds(data.getTlds());
        }
        if (data.getIpRanges() != null) {
            holder.setIpRanges(data.getIpRanges());
        }
        if (data.getAsnRanges() != null) {
            holder.setAsnRanges(data.getAsnRanges());
        }
        if (data.getRequiresAuth() != null) {
            holder.setRequiresAuth(data.getRequiresAuth());
        }
        if (data.getAuthType() != null) {
            holder.setAuthType(data.getAuthType());
        }
        if (data.getIsActive() != null) {
            holder.setIsActive(data.getIsActive());
        }

        holder = repository.save(holder);
        log.info("Updated data holder: {} (id={})", holder.getName(), holder.getId());
        return holder;
    }

    @Transactional
    public boolean delete(long holderId) {
        DataHolder holder = getById(holderId);
        if (holder == null) {
            return false;
        }
        String name = holder.getName();
        repository.delete(holder);
        log.info("Deleted data holder: {} (id={})", name, holderId);
        return true;
    }

    @Transactional
    public DataHolder toggleActive(long holderId) {
        DataHolder holder = getById(holderId);
        if (holder == null) {
            return null;
        }
        holder.setIsActive(!Boolean.TRUE.equals(holder.getIsActive()));
        holder = repository.save(holder);
        log.info("Toggled data holder {} active={}", holder.getName(), holder.getIsActive());
        return holder;
    }

    @Transactional(readOnly = true)
    public Map<String, Object> getStats() {
        List<DataHolder> all = repository.findAll();
        List<DataHolder> active = new ArrayList<>();
        for (DataHolder h : all) {
            if (Boolean.TRUE.equals(h.getIsActive())) {
                active.add(h);
            }
        }
        int totalTlds = 0;
        int totalIpRanges = 0;
        int totalAsnRanges = 0;
        for (DataHolder h : active) {
            totalTlds += h.getTlds() != null ? h.getTlds().size() : 0;
            totalIpRanges += h.getIpRanges() != null ? h.getIpRanges().size() : 0;
            totalAsnRanges += h.getAsnRanges() != null ? h.getAsnRanges().size() : 0;
        }
        Map<String, Object> stats = new LinkedHashMap<>();
        stats.put("total", all.size());
        stats.put("active_count", active.size());
        stats.put("total_tlds", totalTlds);
        stats.put("total_ip_ranges", totalIpRanges);
        stats.put("total_asn_ranges", totalAsnRanges);
        return stats;
    }

    // ============================================================
    // Validation (ported from models/dataholder.py field_validators)
    // ============================================================

    /** DataHolderCreate.validate_base_urls: every URL must be http(s). */
    private void validateBaseUrlsCreate(List<String> baseUrls) {
        if (baseUrls == null) {
            return;
        }
        for (String url : baseUrls) {
            if (url == null || !(url.startsWith("http://") || url.startsWith("https://"))) {
                throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY,
                        "Invalid URL: " + url + ". Must start with http:// or https://");
            }
        }
    }

    /** DataHolderUpdate.validate_base_urls: if supplied, non-empty + each http(s). */
    private void validateBaseUrlsUpdate(List<String> baseUrls) {
        if (baseUrls == null) {
            return;
        }
        if (baseUrls.isEmpty()) {
            throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY,
                    "base_urls must have at least one URL");
        }
        for (String url : baseUrls) {
            if (url == null || !(url.startsWith("http://") || url.startsWith("https://"))) {
                throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, "Invalid URL: " + url);
            }
        }
    }

    /**
     * validate_asn_ranges. Create variant emits the detailed messages; update variant
     * uses the single combined message ("ASN ranges must be valid [start, end] pairs").
     */
    private void validateAsnRanges(List<List<Integer>> asnRanges, boolean updateVariant) {
        if (asnRanges == null || asnRanges.isEmpty()) {
            return;
        }
        for (List<Integer> pair : asnRanges) {
            if (updateVariant) {
                if (pair == null || pair.size() != 2 || pair.get(0) > pair.get(1)) {
                    throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY,
                            "ASN ranges must be valid [start, end] pairs");
                }
            } else {
                if (pair == null || pair.size() != 2) {
                    throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY,
                            "ASN ranges must be [start, end] pairs");
                }
                if (pair.get(0) > pair.get(1)) {
                    throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY,
                            "ASN range start (" + pair.get(0) + ") must be <= end (" + pair.get(1) + ")");
                }
            }
        }
    }

    /** DataHolderCreate.validate_ip_ranges: each entry must parse as a CIDR network. */
    private void validateIpRanges(List<String> ipRanges) {
        if (ipRanges == null || ipRanges.isEmpty()) {
            return;
        }
        for (String cidr : ipRanges) {
            String err = validateCidr(cidr);
            if (err != null) {
                throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY,
                        "Invalid IP range: " + cidr + ". " + err);
            }
        }
    }

    /**
     * Mimic Python {@code ipaddress.ip_network(cidr, strict=False)}: accepts a bare
     * address or address/prefix, IPv4 or IPv6, host bits set are tolerated.
     * Returns null when valid, else an error message.
     */
    private String validateCidr(String cidr) {
        if (cidr == null || cidr.isBlank()) {
            return "does not appear to be an IPv4 or IPv6 network";
        }
        String address;
        Integer prefix = null;
        int slash = cidr.indexOf('/');
        if (slash >= 0) {
            address = cidr.substring(0, slash);
            String prefixStr = cidr.substring(slash + 1);
            try {
                prefix = Integer.parseInt(prefixStr.trim());
            } catch (NumberFormatException e) {
                return "does not appear to be an IPv4 or IPv6 network";
            }
        } else {
            address = cidr;
        }
        java.net.InetAddress addr;
        try {
            // Guard against hostname resolution: only accept literal IPs.
            if (!isInetLiteral(address)) {
                return "does not appear to be an IPv4 or IPv6 network";
            }
            addr = java.net.InetAddress.getByName(address);
        } catch (java.net.UnknownHostException e) {
            return "does not appear to be an IPv4 or IPv6 network";
        }
        int maxPrefix = (addr instanceof java.net.Inet6Address) ? 128 : 32;
        if (prefix != null && (prefix < 0 || prefix > maxPrefix)) {
            return "is not a valid prefix length";
        }
        return null;
    }

    private boolean isInetLiteral(String s) {
        if (s == null || s.isEmpty()) {
            return false;
        }
        // IPv6 literals contain ':'; IPv4 literals are digits + dots only.
        if (s.indexOf(':') >= 0) {
            return true;
        }
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c != '.' && (c < '0' || c > '9')) {
                return false;
            }
        }
        return true;
    }

    /** validate_auth_type: must be one of {none, bearer}. */
    private void validateAuthType(String authType, boolean updateVariant) {
        if (updateVariant) {
            if (authType != null && !ALLOWED_AUTH_TYPES.contains(authType)) {
                throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY,
                        "auth_type must be 'none' or 'bearer'");
            }
        } else {
            if (!ALLOWED_AUTH_TYPES.contains(authType)) {
                throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY,
                        "auth_type must be one of: " + ALLOWED_AUTH_TYPES);
            }
        }
    }
}
