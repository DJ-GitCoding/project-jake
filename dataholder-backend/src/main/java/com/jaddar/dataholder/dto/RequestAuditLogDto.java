/*
 * SPDX-FileCopyrightText: 2025-2026 Edgemoor Research Institute and Derek Jenkins
 * SPDX-License-Identifier: AGPL-3.0-only
 *
 * Author: Derek Jenkins <derek@pure-code.net>
 * Additional terms under AGPL-3.0 Section 7 apply. See NOTICE at the repository root.
 */

package com.jaddar.dataholder.dto;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.Builder;

import java.time.LocalDateTime;
import java.util.List;

/**
 * DTO for request audit log entries exposed via the API.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RequestAuditLogDto {

    private Long id;
    private String queryType;
    private String queryValue;
    private String requestorSub;
    private String requestorUsername;
    private String requestorIp;
    private List<String> agreementNames;
    private Integer accessLevelRequested;
    private Integer accessLevelGranted;
    private String result;
    private String resultMessage;

    // Disclosure flags
    private boolean confidential;
    private boolean exigent;
    private boolean jakeCompliance;

    private LocalDateTime requestTimestamp;
    private Integer responseTimeMs;

    /**
     * Build a DTO from the entity.
     */
    public static RequestAuditLogDto fromEntity(com.jaddar.dataholder.entity.RequestAuditLog entity) {
        return RequestAuditLogDto.builder()
                .id(entity.getId())
                .queryType(entity.getQueryType())
                .queryValue(entity.getQueryValue())
                .requestorSub(entity.getRequestorSub())
                .requestorUsername(entity.getRequestorUsername())
                .requestorIp(entity.getRequestorIp())
                .agreementNames(entity.getAgreementNames() != null
                        ? java.util.Arrays.asList(entity.getAgreementNames()) : null)
                .accessLevelRequested(entity.getAccessLevelRequested())
                .accessLevelGranted(entity.getAccessLevelGranted())
                .result(entity.getResult())
                .resultMessage(entity.getResultMessage())
                .confidential(entity.isConfidential())
                .exigent(entity.isExigent())
                .jakeCompliance(entity.isJakeCompliance())
                .requestTimestamp(entity.getRequestTimestamp())
                .responseTimeMs(entity.getResponseTimeMs())
                .build();
    }
}