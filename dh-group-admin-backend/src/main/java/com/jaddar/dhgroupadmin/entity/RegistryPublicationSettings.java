/*
 * SPDX-FileCopyrightText: 2025-2026 Edgemoor Research Institute and Derek Jenkins
 * SPDX-License-Identifier: AGPL-3.0-only
 *
 * Author: Derek Jenkins <derek@pure-code.net>
 * Additional terms under AGPL-3.0 Section 7 apply. See NOTICE at the repository root.
 */

package com.jaddar.dhgroupadmin.entity;

import com.jaddar.dhgroupadmin.config.EncryptedStringConverter;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;

/** Deployment-wide settings for announcing this group admin to a common repository, the central node a Requestor Manager pings when it goes looking fo... */
@Getter
@Setter
@Entity
@Table(name = "registry_publication_settings")
public class RegistryPublicationSettings {
    @Id
    @Column(name = "id")
    private Long id = 1L;

    @Column(name = "enabled", nullable = false)
    private Boolean enabled = false;

    /** Base URL of the common repository's feed directory, e.g. */
    @Column(name = "registry_url", length = 1024)
    private String registryUrl;

    /** Stable identifier this registry claims in the repository; a requestor manager keys its local record on it. */
    @Column(name = "registry_code", length = 64)
    private String registryCode;

    /** Display name of this registry, published alongside the code. */
    @Column(name = "registry_name", length = 255)
    private String registryName;

    @Column(name = "registry_description", length = 2000)
    private String registryDescription;

    /** Where a requestor manager should point its own client, this deployment's public API base URL. */
    @Column(name = "public_base_url", length = 1024)
    private String publicBaseUrl;

    @Column(name = "contact_email", length = 255)
    private String contactEmail;

    /** Secret issued by the repository on first announcement; proves ownership of the entry. */
    @Convert(converter = EncryptedStringConverter.class)
    @Column(name = "announce_token", length = 512)
    private String announceToken;

    /** NOT_REGISTERED, PENDING, APPROVED or REJECTED, as last reported by the repository. */
    @Column(name = "registration_status", length = 32)
    private String registrationStatus = "NOT_REGISTERED";

    @Column(name = "last_announced_at")
    private Instant lastAnnouncedAt;

    /** "SUCCESS" or "FAILURE" for the last announcement attempt. */
    @Column(name = "last_announce_status", length = 32)
    private String lastAnnounceStatus;

    @Column(name = "last_announce_message", length = 1024)
    private String lastAnnounceMessage;

    /** How many data holder groups went out in the last announcement. */
    @Column(name = "published_group_count")
    private Integer publishedGroupCount;

    /** How many templates went out across those groups. */
    @Column(name = "published_template_count")
    private Integer publishedTemplateCount;

    @Column(name = "updated_by", length = 255)
    private String updatedBy;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private Instant updatedAt;

    /** Whether the settings are complete enough to announce with. */
    @Transient
    public boolean isReady() {
        return Boolean.TRUE.equals(enabled)
                && registryUrl != null && !registryUrl.isBlank()
                && registryCode != null && !registryCode.isBlank()
                && publicBaseUrl != null && !publicBaseUrl.isBlank();
    }
}
