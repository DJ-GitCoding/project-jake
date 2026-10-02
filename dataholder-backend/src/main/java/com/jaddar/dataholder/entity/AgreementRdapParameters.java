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
 * Defines which RDAP parameters an agreement is allowed to return.
 * Each boolean field corresponds to a specific RDAP data element.
 * By default, all parameters are enabled (true).
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AgreementRdapParameters {
    private Long id;

    // ==================== Domain Object Fields ====================
    @Builder.Default
    private Boolean domainHandle = true;
    @Builder.Default
    private Boolean domainName = true;
    @Builder.Default
    private Boolean domainStatus = true;
    @Builder.Default
    private Boolean domainPort43 = true;
    @Builder.Default
    private Boolean domainPublicIds = true;

    // ==================== Nameserver Fields ====================
    @Builder.Default
    private Boolean nameservers = true;
    @Builder.Default
    private Boolean nameserverHandle = true;
    @Builder.Default
    private Boolean nameserverName = true;
    @Builder.Default
    private Boolean nameserverIpAddresses = true;
    @Builder.Default
    private Boolean nameserverStatus = true;

    // ==================== Event Fields ====================
    @Builder.Default
    private Boolean events = true;
    @Builder.Default
    private Boolean eventRegistration = true;
    @Builder.Default
    private Boolean eventExpiration = true;
    @Builder.Default
    private Boolean eventLastChanged = true;
    @Builder.Default
    private Boolean eventLastUpdateOfRdapDb = true;
    @Builder.Default
    private Boolean eventTransfer = true;

    // ==================== Entity/Contact Fields (Registrant) ====================
    @Builder.Default
    private Boolean registrantEntity = true;
    @Builder.Default
    private Boolean registrantHandle = true;
    @Builder.Default
    private Boolean registrantName = true;
    @Builder.Default
    private Boolean registrantOrganization = true;
    @Builder.Default
    private Boolean registrantEmail = true;
    @Builder.Default
    private Boolean registrantPhone = true;
    @Builder.Default
    private Boolean registrantFax = true;
    @Builder.Default
    private Boolean registrantAddress = true;
    @Builder.Default
    private Boolean registrantStreet = true;
    @Builder.Default
    private Boolean registrantCity = true;
    @Builder.Default
    private Boolean registrantStateProvince = true;
    @Builder.Default
    private Boolean registrantPostalCode = true;
    @Builder.Default
    private Boolean registrantCountry = true;

    // ==================== Entity/Contact Fields (Admin) ====================
    @Builder.Default
    private Boolean adminEntity = true;
    @Builder.Default
    private Boolean adminHandle = true;
    @Builder.Default
    private Boolean adminName = true;
    @Builder.Default
    private Boolean adminOrganization = true;
    @Builder.Default
    private Boolean adminEmail = true;
    @Builder.Default
    private Boolean adminPhone = true;
    @Builder.Default
    private Boolean adminFax = true;
    @Builder.Default
    private Boolean adminAddress = true;
    @Builder.Default
    private Boolean adminStreet = true;
    @Builder.Default
    private Boolean adminCity = true;
    @Builder.Default
    private Boolean adminStateProvince = true;
    @Builder.Default
    private Boolean adminPostalCode = true;
    @Builder.Default
    private Boolean adminCountry = true;

    // ==================== Entity/Contact Fields (Tech) ====================
    @Builder.Default
    private Boolean techEntity = true;
    @Builder.Default
    private Boolean techHandle = true;
    @Builder.Default
    private Boolean techName = true;
    @Builder.Default
    private Boolean techOrganization = true;
    @Builder.Default
    private Boolean techEmail = true;
    @Builder.Default
    private Boolean techPhone = true;
    @Builder.Default
    private Boolean techFax = true;
    @Builder.Default
    private Boolean techAddress = true;
    @Builder.Default
    private Boolean techStreet = true;
    @Builder.Default
    private Boolean techCity = true;
    @Builder.Default
    private Boolean techStateProvince = true;
    @Builder.Default
    private Boolean techPostalCode = true;
    @Builder.Default
    private Boolean techCountry = true;

    // ==================== Entity/Contact Fields (Billing) ====================
    @Builder.Default
    private Boolean billingEntity = true;
    @Builder.Default
    private Boolean billingHandle = true;
    @Builder.Default
    private Boolean billingName = true;
    @Builder.Default
    private Boolean billingOrganization = true;
    @Builder.Default
    private Boolean billingEmail = true;
    @Builder.Default
    private Boolean billingPhone = true;
    @Builder.Default
    private Boolean billingFax = true;
    @Builder.Default
    private Boolean billingAddress = true;
    @Builder.Default
    private Boolean billingStreet = true;
    @Builder.Default
    private Boolean billingCity = true;
    @Builder.Default
    private Boolean billingStateProvince = true;
    @Builder.Default
    private Boolean billingPostalCode = true;
    @Builder.Default
    private Boolean billingCountry = true;

    // ==================== Entity/Contact Fields (Registrar/Sponsor) ====================
    @Builder.Default
    private Boolean registrarEntity = true;
    @Builder.Default
    private Boolean registrarHandle = true;
    @Builder.Default
    private Boolean registrarName = true;
    @Builder.Default
    private Boolean registrarEmail = true;
    @Builder.Default
    private Boolean registrarPhone = true;
    @Builder.Default
    private Boolean registrarUrl = true;
    @Builder.Default
    private Boolean registrarAbuseContact = true;

    // ==================== DNSSEC Fields ====================
    @Builder.Default
    private Boolean dnssecData = true;
    @Builder.Default
    private Boolean dnssecDelegationSigned = true;
    @Builder.Default
    private Boolean dnssecDsData = true;
    @Builder.Default
    private Boolean dnssecKeyData = true;

    // ==================== Network/IP Fields ====================
    @Builder.Default
    private Boolean networkHandle = true;
    @Builder.Default
    private Boolean networkName = true;
    @Builder.Default
    private Boolean networkType = true;
    @Builder.Default
    private Boolean networkStartAddress = true;
    @Builder.Default
    private Boolean networkEndAddress = true;
    @Builder.Default
    private Boolean networkIpVersion = true;
    @Builder.Default
    private Boolean networkParentHandle = true;
    @Builder.Default
    private Boolean networkCidr = true;
    @Builder.Default
    private Boolean networkCountry = true;

    // ==================== ASN Fields ====================
    @Builder.Default
    private Boolean autnumHandle = true;
    @Builder.Default
    private Boolean autnumStart = true;
    @Builder.Default
    private Boolean autnumEnd = true;
    @Builder.Default
    private Boolean autnumName = true;
    @Builder.Default
    private Boolean autnumType = true;
    @Builder.Default
    private Boolean autnumCountry = true;

    // ==================== Links and Notices ====================
    @Builder.Default
    private Boolean links = true;
    @Builder.Default
    private Boolean notices = true;
    @Builder.Default
    private Boolean remarks = true;

    // ==================== Metadata ====================
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
    }
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }

    // ==================== Helper Methods ====================

    /**
     * Creates a new instance with all parameters enabled (default)
     */
    public static AgreementRdapParameters createDefault() {
        return AgreementRdapParameters.builder().build();
    }

    /**
     * Creates an instance with only public (Level 0) parameters enabled.
     * This typically includes basic domain info but no contact details.
     */
    public static AgreementRdapParameters createPublicOnly() {
        AgreementRdapParameters params = new AgreementRdapParameters();
        // Disable all contact information
        params.setRegistrantEntity(false);
        params.setRegistrantHandle(false);
        params.setRegistrantName(false);
        params.setRegistrantOrganization(false);
        params.setRegistrantEmail(false);
        params.setRegistrantPhone(false);
        params.setRegistrantFax(false);
        params.setRegistrantAddress(false);
        params.setRegistrantStreet(false);
        params.setRegistrantCity(false);
        params.setRegistrantStateProvince(false);
        params.setRegistrantPostalCode(false);
        params.setRegistrantCountry(false);
        
        params.setAdminEntity(false);
        params.setAdminHandle(false);
        params.setAdminName(false);
        params.setAdminOrganization(false);
        params.setAdminEmail(false);
        params.setAdminPhone(false);
        params.setAdminFax(false);
        params.setAdminAddress(false);
        params.setAdminStreet(false);
        params.setAdminCity(false);
        params.setAdminStateProvince(false);
        params.setAdminPostalCode(false);
        params.setAdminCountry(false);
        
        params.setTechEntity(false);
        params.setTechHandle(false);
        params.setTechName(false);
        params.setTechOrganization(false);
        params.setTechEmail(false);
        params.setTechPhone(false);
        params.setTechFax(false);
        params.setTechAddress(false);
        params.setTechStreet(false);
        params.setTechCity(false);
        params.setTechStateProvince(false);
        params.setTechPostalCode(false);
        params.setTechCountry(false);
        
        params.setBillingEntity(false);
        params.setBillingHandle(false);
        params.setBillingName(false);
        params.setBillingOrganization(false);
        params.setBillingEmail(false);
        params.setBillingPhone(false);
        params.setBillingFax(false);
        params.setBillingAddress(false);
        params.setBillingStreet(false);
        params.setBillingCity(false);
        params.setBillingStateProvince(false);
        params.setBillingPostalCode(false);
        params.setBillingCountry(false);
        
        return params;
    }

    /**
     * Check if any registrant contact fields are enabled
     */
    public boolean hasRegistrantAccess() {
        return registrantEntity || registrantHandle || registrantName || 
               registrantOrganization || registrantEmail || registrantPhone ||
               registrantFax || registrantAddress || registrantStreet ||
               registrantCity || registrantStateProvince || registrantPostalCode ||
               registrantCountry;
    }

    /**
     * Check if any admin contact fields are enabled
     */
    public boolean hasAdminAccess() {
        return adminEntity || adminHandle || adminName || adminOrganization ||
               adminEmail || adminPhone || adminFax || adminAddress ||
               adminStreet || adminCity || adminStateProvince ||
               adminPostalCode || adminCountry;
    }

    /**
     * Check if any tech contact fields are enabled
     */
    public boolean hasTechAccess() {
        return techEntity || techHandle || techName || techOrganization ||
               techEmail || techPhone || techFax || techAddress ||
               techStreet || techCity || techStateProvince ||
               techPostalCode || techCountry;
    }

    /**
     * Check if any billing contact fields are enabled
     */
    public boolean hasBillingAccess() {
        return billingEntity || billingHandle || billingName || billingOrganization ||
               billingEmail || billingPhone || billingFax || billingAddress ||
               billingStreet || billingCity || billingStateProvince ||
               billingPostalCode || billingCountry;
    }

    /**
     * Enable all parameters for a given contact role
     */
    public void enableContactRole(ContactRole role) {
        switch (role) {
            case REGISTRANT -> {
                registrantEntity = true; registrantHandle = true;
                registrantName = true; registrantOrganization = true;
                registrantEmail = true; registrantPhone = true;
                registrantFax = true; registrantAddress = true;
                registrantStreet = true; registrantCity = true;
                registrantStateProvince = true; registrantPostalCode = true;
                registrantCountry = true;
            }
            case ADMIN -> {
                adminEntity = true; adminHandle = true;
                adminName = true; adminOrganization = true;
                adminEmail = true; adminPhone = true;
                adminFax = true; adminAddress = true;
                adminStreet = true; adminCity = true;
                adminStateProvince = true; adminPostalCode = true;
                adminCountry = true;
            }
            case TECH -> {
                techEntity = true; techHandle = true;
                techName = true; techOrganization = true;
                techEmail = true; techPhone = true;
                techFax = true; techAddress = true;
                techStreet = true; techCity = true;
                techStateProvince = true; techPostalCode = true;
                techCountry = true;
            }
            case BILLING -> {
                billingEntity = true; billingHandle = true;
                billingName = true; billingOrganization = true;
                billingEmail = true; billingPhone = true;
                billingFax = true; billingAddress = true;
                billingStreet = true; billingCity = true;
                billingStateProvince = true; billingPostalCode = true;
                billingCountry = true;
            }
        }
    }

    /**
     * Disable all parameters for a given contact role
     */
    public void disableContactRole(ContactRole role) {
        switch (role) {
            case REGISTRANT -> {
                registrantEntity = false; registrantHandle = false;
                registrantName = false; registrantOrganization = false;
                registrantEmail = false; registrantPhone = false;
                registrantFax = false; registrantAddress = false;
                registrantStreet = false; registrantCity = false;
                registrantStateProvince = false; registrantPostalCode = false;
                registrantCountry = false;
            }
            case ADMIN -> {
                adminEntity = false; adminHandle = false;
                adminName = false; adminOrganization = false;
                adminEmail = false; adminPhone = false;
                adminFax = false; adminAddress = false;
                adminStreet = false; adminCity = false;
                adminStateProvince = false; adminPostalCode = false;
                adminCountry = false;
            }
            case TECH -> {
                techEntity = false; techHandle = false;
                techName = false; techOrganization = false;
                techEmail = false; techPhone = false;
                techFax = false; techAddress = false;
                techStreet = false; techCity = false;
                techStateProvince = false; techPostalCode = false;
                techCountry = false;
            }
            case BILLING -> {
                billingEntity = false; billingHandle = false;
                billingName = false; billingOrganization = false;
                billingEmail = false; billingPhone = false;
                billingFax = false; billingAddress = false;
                billingStreet = false; billingCity = false;
                billingStateProvince = false; billingPostalCode = false;
                billingCountry = false;
            }
        }
    }

    public enum ContactRole {
        REGISTRANT,
        ADMIN,
        TECH,
        BILLING
    }
}