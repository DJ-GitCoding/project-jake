/*
 * SPDX-FileCopyrightText: 2025-2026 Edgemoor Research Institute and Derek Jenkins
 * SPDX-License-Identifier: AGPL-3.0-only
 *
 * Author: Derek Jenkins <derek@pure-code.net>
 * Additional terms under AGPL-3.0 Section 7 apply. See NOTICE at the repository root.
 */
package com.requestormanager.controller;

import com.requestormanager.dto.ApiResponse;
import com.requestormanager.service.MemberProfileService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/profile")
@RequiredArgsConstructor
@Tag(name = "Profile", description = "The signed-in member's own profile")
public class ProfileController {

    private final MemberProfileService memberProfileService;

    @GetMapping
    @Operation(summary = "Get your profile",
               description = "Standard fields and your requestor groups' custom fields, each with the agreements that require it.")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getProfile() {
        return ResponseEntity.ok(ApiResponse.success(memberProfileService.getOwnProfile()));
    }

    @PutMapping
    @Operation(summary = "Update your profile",
               description = "Standard fields by key (first_name, last_name, phone, street_address, city, state_province, "
                           + "postal_code, country) and customValues by custom field id. Email cannot change here.")
    public ResponseEntity<ApiResponse<Map<String, Object>>> updateProfile(@RequestBody Map<String, Object> body) {
        return ResponseEntity.ok(ApiResponse.success("Profile saved", memberProfileService.updateOwnProfile(body)));
    }
}
