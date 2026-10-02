/*
 * SPDX-FileCopyrightText: 2025-2026 Edgemoor Research Institute and Derek Jenkins
 * SPDX-License-Identifier: AGPL-3.0-only
 *
 * Author: Derek Jenkins <derek@pure-code.net>
 * Additional terms under AGPL-3.0 Section 7 apply. See NOTICE at the repository root.
 */

package com.jaddar.dataholder.dto;

import com.jaddar.dataholder.entity.RdapEntity.ObjectType;
import com.jaddar.dataholder.entity.RdapEvent.EventAction;
import com.jaddar.dataholder.entity.RdapRemark.RemarkType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * DTOs for RDAP data management
 */
public class RdapDataDto {

    // ==================== ENTITY REQUEST ====================

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class RdapEntityRequest {
        private ObjectType objectType;
        private String handle;
        private String ldhName;
        private String unicodeName;
        private List<String> status;
        private String port43;
        
        // Domain fields
        private Boolean secureDnsDelegationSigned;
        private Boolean secureDnsZoneSigned;
        
        // IP fields
        private String startAddress;
        private String endAddress;
        private String ipVersion;
        private String networkName;
        private String networkType;
        private String parentHandle;
        private String country;
        
        // ASN fields
        private Long startAutnum;
        private Long endAutnum;
        private String autnumName;
        private String autnumType;
        
        // Entity/Contact fields
        private String contactName;
        private String organization;
        private String email;
        private String phone;
        private String fax;
        private String addressStreet1;
        private String addressStreet2;
        private String addressCity;
        private String addressState;
        private String addressPostalCode;
        private String addressCountry;
        private List<String> roles;
        private List<String> publicIds;
        
        // Related entities
        private List<RdapEventRequest> events;
        private List<RdapLinkRequest> links;
        private List<RdapNameserverRequest> nameservers;
        private List<RdapRemarkRequest> remarks;
        private List<RdapSecureDnsRequest> secureDnsRecords;
        private List<RdapEntityRequest> childEntities;
        
        // Flags
        private Boolean isTestData;
        
        // Policy expression for redaction rules
        private Long policyExpressionId;
        private Boolean clearPolicyExpression;
    }

    // ==================== RELATED ENTITY REQUESTS ====================

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class RdapEventRequest {
        private EventAction eventAction;
        private String eventActionCustom;
        private LocalDateTime eventDate;
        private String eventActor;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class RdapLinkRequest {
        private String href;
        private String rel;
        private String mediaType;
        private String title;
        private String value;
        private String hreflang;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class RdapNameserverRequest {
        private String handle;
        private String ldhName;
        private String unicodeName;
        private List<String> ipv4Addresses;
        private List<String> ipv6Addresses;
        private List<String> status;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class RdapRemarkRequest {
        private RemarkType remarkType;
        private String title;
        private List<String> description;
        private String remarkTypeValue;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class RdapSecureDnsRequest {
        private Integer keyTag;
        private Integer algorithm;
        private Integer digestType;
        private String digest;
    }

    // ==================== STATS ====================

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class RdapDataStats {
        private long domainCount;
        private long ipCount;
        private long asnCount;
        private long totalCount;
        private long childEntityCount;
        private long eventCount;  
        private long nameserverCount;
        private LocalDateTime lastUpdated;
    }

    // ==================== SCHEMA ====================

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SchemaField {
        private String name;
        private String type;
        private String description;
        private boolean required;
        private String example;
    }

    // ==================== IMPORT/EXPORT ====================

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CsvPreviewResponse {
        private int totalRows;
        private List<String> detectedHeaders;
        private List<List<String>> sampleRows;
        private List<SchemaField> targetFields;
        private Map<String, Integer> suggestedMappings;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ImportResult {
        private int totalRows;
        private int successCount;
        private int failedCount;
        private List<ImportError> errors;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ImportError {
        private int rowNumber;
        private String error;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class JsonImportRequest {
        private List<Map<String, Object>> data;
    }
}