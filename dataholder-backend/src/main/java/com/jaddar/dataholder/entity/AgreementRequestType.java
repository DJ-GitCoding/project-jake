/*
 * SPDX-FileCopyrightText: 2025-2026 Edgemoor Research Institute and Derek Jenkins
 * SPDX-License-Identifier: AGPL-3.0-only
 *
 * Author: Derek Jenkins <derek@pure-code.net>
 * Additional terms under AGPL-3.0 Section 7 apply. See NOTICE at the repository root.
 */

package com.jaddar.dataholder.entity;

import lombok.*;
import java.time.LocalDateTime;

/**
 * Represents a type of RDAP request that an agreement template supports.
 * Each request type defines its own access level, RDAP parameters,
 * and whether it supports confidential or exigent disclosures.
 *
 * A template may have multiple request types, e.g.:
 * - "Standard" (access level 1, no confidential/exigent)
 * - "Confidential" (access level 2, confidential=true)
 * - "Exigent" (access level 3, exigent=true)
 *
 * When a subscription is created from a template, the subscription inherits
 * all of the template's request types. Access level and RDAP parameter
 * resolution at query time is driven by the request type.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AgreementRequestType {
    private Long id;

    /**
     * Parent template this request type belongs to
     */
    private AgreementTemplate template;

    /**
     * Name/identifier for this request type (e.g., "standard", "confidential", "exigent")
     */
    private String name;
    private String typeCode;

    /**
     * Human-readable description
     */
    private String description;

    /**
     * The access level granted for this request type (0-3)
     */
    @Builder.Default
    private Integer accessLevel = 0;

    /**
     * Whether this request type supports confidential disclosure requests
     */
    @Builder.Default
    private Boolean supportsConfidential = false;

    /**
     * Whether this request type supports exigent disclosure requests
     */
    @Builder.Default
    private Boolean supportsExigent = false;

    /**
     * Whether subscriptions using this request type require manual approval
     * by the data holder before becoming active.
     */
    @Builder.Default
    private Boolean requiresManualApproval = true;

    /**
     * RDAP parameters that define which fields are accessible for this request type.
     * If null, defaults will be applied based on access level.
     */
    private AgreementRdapParameters rdapParameters;

    /**
     * Display order for UI presentation
     */
    @Builder.Default
    private Integer sortOrder = 0;

    /**
     * Whether this request type is active
     */
    @Builder.Default
    private Boolean isActive = true;

    // ==================== Audit Fields ====================
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
        if (rdapParameters == null) {
            initializeRdapParametersForAccessLevel();
        }
    }
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }

    // ==================== Helper Methods ====================

    /**
     * Initialize RDAP parameters with defaults based on access level
     */
    public void initializeRdapParametersForAccessLevel() {
        if (this.accessLevel == null || this.accessLevel == 0) {
            this.rdapParameters = AgreementRdapParameters.createPublicOnly();
        } else {
            this.rdapParameters = AgreementRdapParameters.createDefault();
        }
    }

    /**
     * Get effective RDAP parameters, creating defaults if needed
     */
    public AgreementRdapParameters getEffectiveRdapParameters() {
        if (rdapParameters != null) {
            return rdapParameters;
        }
        return accessLevel != null && accessLevel > 0
                ? AgreementRdapParameters.createDefault()
                : AgreementRdapParameters.createPublicOnly();
    }

    /**
     * Copy RDAP parameters to create a new instance
     */
    public AgreementRdapParameters copyRdapParameters() {
        if (rdapParameters == null) {
            return accessLevel != null && accessLevel > 0
                    ? AgreementRdapParameters.createDefault()
                    : AgreementRdapParameters.createPublicOnly();
        }

        AgreementRdapParameters copy = AgreementRdapParameters.builder()
                // Domain fields
                .domainHandle(rdapParameters.getDomainHandle())
                .domainName(rdapParameters.getDomainName())
                .domainStatus(rdapParameters.getDomainStatus())
                .domainPort43(rdapParameters.getDomainPort43())
                .domainPublicIds(rdapParameters.getDomainPublicIds())
                // Nameserver fields
                .nameservers(rdapParameters.getNameservers())
                .nameserverHandle(rdapParameters.getNameserverHandle())
                .nameserverName(rdapParameters.getNameserverName())
                .nameserverIpAddresses(rdapParameters.getNameserverIpAddresses())
                .nameserverStatus(rdapParameters.getNameserverStatus())
                // Event fields
                .events(rdapParameters.getEvents())
                .eventRegistration(rdapParameters.getEventRegistration())
                .eventExpiration(rdapParameters.getEventExpiration())
                .eventLastChanged(rdapParameters.getEventLastChanged())
                .eventLastUpdateOfRdapDb(rdapParameters.getEventLastUpdateOfRdapDb())
                .eventTransfer(rdapParameters.getEventTransfer())
                // Registrant fields
                .registrantEntity(rdapParameters.getRegistrantEntity())
                .registrantHandle(rdapParameters.getRegistrantHandle())
                .registrantName(rdapParameters.getRegistrantName())
                .registrantOrganization(rdapParameters.getRegistrantOrganization())
                .registrantEmail(rdapParameters.getRegistrantEmail())
                .registrantPhone(rdapParameters.getRegistrantPhone())
                .registrantFax(rdapParameters.getRegistrantFax())
                .registrantAddress(rdapParameters.getRegistrantAddress())
                .registrantStreet(rdapParameters.getRegistrantStreet())
                .registrantCity(rdapParameters.getRegistrantCity())
                .registrantStateProvince(rdapParameters.getRegistrantStateProvince())
                .registrantPostalCode(rdapParameters.getRegistrantPostalCode())
                .registrantCountry(rdapParameters.getRegistrantCountry())
                // Admin fields
                .adminEntity(rdapParameters.getAdminEntity())
                .adminHandle(rdapParameters.getAdminHandle())
                .adminName(rdapParameters.getAdminName())
                .adminOrganization(rdapParameters.getAdminOrganization())
                .adminEmail(rdapParameters.getAdminEmail())
                .adminPhone(rdapParameters.getAdminPhone())
                .adminFax(rdapParameters.getAdminFax())
                .adminAddress(rdapParameters.getAdminAddress())
                .adminStreet(rdapParameters.getAdminStreet())
                .adminCity(rdapParameters.getAdminCity())
                .adminStateProvince(rdapParameters.getAdminStateProvince())
                .adminPostalCode(rdapParameters.getAdminPostalCode())
                .adminCountry(rdapParameters.getAdminCountry())
                // Tech fields
                .techEntity(rdapParameters.getTechEntity())
                .techHandle(rdapParameters.getTechHandle())
                .techName(rdapParameters.getTechName())
                .techOrganization(rdapParameters.getTechOrganization())
                .techEmail(rdapParameters.getTechEmail())
                .techPhone(rdapParameters.getTechPhone())
                .techFax(rdapParameters.getTechFax())
                .techAddress(rdapParameters.getTechAddress())
                .techStreet(rdapParameters.getTechStreet())
                .techCity(rdapParameters.getTechCity())
                .techStateProvince(rdapParameters.getTechStateProvince())
                .techPostalCode(rdapParameters.getTechPostalCode())
                .techCountry(rdapParameters.getTechCountry())
                // Billing fields
                .billingEntity(rdapParameters.getBillingEntity())
                .billingHandle(rdapParameters.getBillingHandle())
                .billingName(rdapParameters.getBillingName())
                .billingOrganization(rdapParameters.getBillingOrganization())
                .billingEmail(rdapParameters.getBillingEmail())
                .billingPhone(rdapParameters.getBillingPhone())
                .billingFax(rdapParameters.getBillingFax())
                .billingAddress(rdapParameters.getBillingAddress())
                .billingStreet(rdapParameters.getBillingStreet())
                .billingCity(rdapParameters.getBillingCity())
                .billingStateProvince(rdapParameters.getBillingStateProvince())
                .billingPostalCode(rdapParameters.getBillingPostalCode())
                .billingCountry(rdapParameters.getBillingCountry())
                // Registrar fields
                .registrarEntity(rdapParameters.getRegistrarEntity())
                .registrarHandle(rdapParameters.getRegistrarHandle())
                .registrarName(rdapParameters.getRegistrarName())
                .registrarEmail(rdapParameters.getRegistrarEmail())
                .registrarPhone(rdapParameters.getRegistrarPhone())
                .registrarUrl(rdapParameters.getRegistrarUrl())
                .registrarAbuseContact(rdapParameters.getRegistrarAbuseContact())
                // DNSSEC fields
                .dnssecData(rdapParameters.getDnssecData())
                .dnssecDelegationSigned(rdapParameters.getDnssecDelegationSigned())
                .dnssecDsData(rdapParameters.getDnssecDsData())
                .dnssecKeyData(rdapParameters.getDnssecKeyData())
                // Network fields
                .networkHandle(rdapParameters.getNetworkHandle())
                .networkName(rdapParameters.getNetworkName())
                .networkType(rdapParameters.getNetworkType())
                .networkStartAddress(rdapParameters.getNetworkStartAddress())
                .networkEndAddress(rdapParameters.getNetworkEndAddress())
                .networkIpVersion(rdapParameters.getNetworkIpVersion())
                .networkParentHandle(rdapParameters.getNetworkParentHandle())
                .networkCidr(rdapParameters.getNetworkCidr())
                .networkCountry(rdapParameters.getNetworkCountry())
                // ASN fields
                .autnumHandle(rdapParameters.getAutnumHandle())
                .autnumStart(rdapParameters.getAutnumStart())
                .autnumEnd(rdapParameters.getAutnumEnd())
                .autnumName(rdapParameters.getAutnumName())
                .autnumType(rdapParameters.getAutnumType())
                .autnumCountry(rdapParameters.getAutnumCountry())
                // Other fields
                .links(rdapParameters.getLinks())
                .notices(rdapParameters.getNotices())
                .remarks(rdapParameters.getRemarks())
                .build();

        return copy;
    }

    /**
     * Check if this request type matches the given query characteristics
     */
    public boolean matchesRequest(boolean isConfidential, boolean isExigent) {
        if (isConfidential && !Boolean.TRUE.equals(supportsConfidential)) {
            return false;
        }
        if (isExigent && !Boolean.TRUE.equals(supportsExigent)) {
            return false;
        }
        // Standard request matches any type that doesn't require confidential/exigent
        if (!isConfidential && !isExigent) {
            return !Boolean.TRUE.equals(supportsConfidential) && !Boolean.TRUE.equals(supportsExigent);
        }
        return true;
    }

    /**
     * Enum for well-known request type names
     */
    public enum RequestTypeName {
        STANDARD("standard"),
        CONFIDENTIAL("confidential"),
        EXIGENT("exigent");

        private final String value;

        RequestTypeName(String value) {
            this.value = value;
        }

        public String getValue() {
            return value;
        }

        public static RequestTypeName fromString(String text) {
            for (RequestTypeName t : RequestTypeName.values()) {
                if (t.value.equalsIgnoreCase(text) || t.name().equalsIgnoreCase(text)) {
                    return t;
                }
            }
            return STANDARD;
        }
    }
}