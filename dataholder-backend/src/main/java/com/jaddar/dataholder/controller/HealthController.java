/*
 * SPDX-FileCopyrightText: 2025-2026 Edgemoor Research Institute and Derek Jenkins
 * SPDX-License-Identifier: AGPL-3.0-only
 *
 * Author: Derek Jenkins <derek@pure-code.net>
 * Additional terms under AGPL-3.0 Section 7 apply. See NOTICE at the repository root.
 */

package com.jaddar.dataholder.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

@RestController
@RequiredArgsConstructor
public class HealthController {

    @Value("${dataholder.name:Test Data Holder}")
    private String dataHolderName;

    @Value("${dataholder.id:TDH-001}")
    private String dataHolderId;

    @GetMapping("/")
    public ResponseEntity<Map<String, Object>> root() {
        Map<String, Object> info = new HashMap<>();
        info.put("name", dataHolderName);
        info.put("id", dataHolderId);
        info.put("type", "RDAP Data Holder");
        info.put("version", "1.0.0");
        info.put("timestamp", LocalDateTime.now().toString());
        info.put("endpoints", Map.of(
                "domain", "/domain/{domain}",
                "ip", "/ip/{ip}",
                "asn", "/autnum/{asn}",
                "status", "/api/rdap/status/{requestId}",
                "domains", "/api/rdap/domains",
                "admin", "/api/admin/*"
        ));
        return ResponseEntity.ok(info);
    }

    @GetMapping("/health")
    public ResponseEntity<Map<String, Object>> health() {
        Map<String, Object> health = new HashMap<>();
        health.put("status", "UP");
        health.put("name", dataHolderName);
        health.put("id", dataHolderId);
        health.put("timestamp", LocalDateTime.now().toString());
        return ResponseEntity.ok(health);
    }

    @GetMapping("/api/health")
    public ResponseEntity<Map<String, Object>> apiHealth() {
        return health();
    }
}
