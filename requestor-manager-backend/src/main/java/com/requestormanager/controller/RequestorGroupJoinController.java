/*
 * SPDX-FileCopyrightText: 2025-2026 Edgemoor Research Institute and Derek Jenkins
 * SPDX-License-Identifier: AGPL-3.0-only
 *
 * Author: Derek Jenkins <derek@pure-code.net>
 * Additional terms under AGPL-3.0 Section 7 apply. See NOTICE at the repository root.
 */

package com.requestormanager.controller;

import com.requestormanager.service.RequestorGroupService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.util.Map;

/**
 * Public joining links for requestor groups.
 *
 * <p>Data holders that offer requestor group links list this endpoint with their public RDAP
 * answers, so someone who belongs to no requestor group can find one to join. It needs no
 * authentication (it sits under {@code /api/v1/external/**}) and reveals nothing beyond the link
 * the group's admin chose to publish.
 */
@RestController
@RequestMapping("/api/v1/external/requestor-groups")
@RequiredArgsConstructor
@Tag(name = "Requestor Group Joining", description = "Public links for joining a requestor group")
public class RequestorGroupJoinController {

    private final RequestorGroupService requestorGroupService;

    @GetMapping("/{id}/join")
    @Operation(summary = "Redirect to a requestor group's joining form",
            description = "302 to the joining link the group publishes; 404 when there is none.")
    public ResponseEntity<?> join(@PathVariable Long id) {
        return requestorGroupService.findJoiningUrl(id)
                .<ResponseEntity<?>>map(url -> ResponseEntity.status(HttpStatus.FOUND)
                        .location(URI.create(url))
                        // Never cached, so a link the admin changes or removes takes effect at once.
                        .cacheControl(CacheControl.noStore())
                        .build())
                .orElseGet(() -> ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of(
                        "success", false,
                        "message", "This requestor group does not currently offer a joining link")));
    }
}
