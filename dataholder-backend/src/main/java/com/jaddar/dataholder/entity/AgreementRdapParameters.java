/*
 * SPDX-FileCopyrightText: 2025-2026 Edgemoor Research Institute and Derek Jenkins
 * SPDX-License-Identifier: AGPL-3.0-only
 *
 * Author: Derek Jenkins <derek@pure-code.net>
 * Additional terms under AGPL-3.0 Section 7 apply. See NOTICE at the repository root.
 */

package com.jaddar.dataholder.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

/**
 * Defines which RDAP parameters an agreement is allowed to return.
 * Each boolean field corresponds to a specific RDAP data element.
 * By default, all parameters are enabled (true).
 */
@Entity
@Table(name = "agreement_rdap_parameters")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AgreementRdapParameters {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // ==================== Domain Object Fields ====================
    
    @Column(name = "domain_handle")
    @Builder.Default
    private Boolean domainHandle = true;
    
    @Column(name = "domain_name")
    @Builder.Default
    private Boolean domainName = true;
    
    @Column(name = "domain_status")
    @Builder.Default
    private Boolean domainStatus = true;
    
    @Column(name = "domain_port43")
    @Builder.Default
    private Boolean domainPort43 = true;
    
    @Column(name = "domain_public_ids")
    @Builder.Default
    private Boolean domainPublicIds = true;

    // ==================== Nameserver Fields ====================
    
    @Column(name = "nameservers")
    @Builder.Default
    private Boolean nameservers = true;
    
    @Column(name = "nameserver_handle")
    @Builder.Default
    private Boolean nameserverHandle = true;
    
    @Column(name = "nameserver_name")
    @Builder.Default
    private Boolean nameserverName = true;
    
    @Column(name = "nameserver_ip_addresses")
    @Builder.Default
    private Boolean nameserverIpAddresses = true;
    
    @Column(name = "nameserver_status")
    @Builder.Default
    private Boolean nameserverStatus = true;

    // ==================== Event Fields ====================
    
    @Column(name = "events")
    @Builder.Default
    private Boolean events = true;
    
    @Column(name = "event_registration")
    @Builder.Default
    private Boolean eventRegistration = true;
    
    @Column(name = "event_expiration")
    @Builder.Default
    private Boolean eventExpiration = true;
    
    @Column(name = "event_last_changed")
    @Builder.Default
    private Boolean eventLastChanged = true;
    
    @Column(name = "event_last_update_of_rdap_db")
    @Builder.Default
    private Boolean eventLastUpdateOfRdapDb = true;
    
    @Column(name = "event_transfer")
    @Builder.Default
    private Boolean eventTransfer = true;

    // ==================== Entity/Contact Fields (Registrant) ====================
    
    @Column(name = "registrant_entity")
    @Builder.Default
    private Boolean registrantEntity = true;
    
    @Column(name = "registrant_handle")
    @Builder.Default
    private Boolean registrantHandle = true;
    
    @Column(name = "registrant_name")
    @Builder.Default
    private Boolean registrantName = true;
    
    @Column(name = "registrant_organization")
    @Builder.Default
    private Boolean registrantOrganization = true;
    
    @Column(name = "registrant_email")
    @Builder.Default
    private Boolean registrantEmail = true;
    
    @Column(name = "registrant_phone")
    @Builder.Default
    private Boolean registrantPhone = true;
    
    @Column(name = "registrant_fax")
    @Builder.Default
    private Boolean registrantFax = true;
    
    @Column(name = "registrant_address")
    @Builder.Default
    private Boolean registrantAddress = true;
    
    @Column(name = "registrant_street")
    @Builder.Default
    private Boolean registrantStreet = true;
    
    @Column(name = "registrant_city")
    @Builder.Default
    private Boolean registrantCity = true;
    
    @Column(name = "registrant_state_province")
    @Builder.Default
    private Boolean registrantStateProvince = true;
    
    @Column(name = "registrant_postal_code")
    @Builder.Default
    private Boolean registrantPostalCode = true;
    
    @Column(name = "registrant_country")
    @Builder.Default
    private Boolean registrantCountry = true;

    // ==================== Entity/Contact Fields (Admin) ====================
    
    @Column(name = "admin_entity")
    @Builder.Default
    private Boolean adminEntity = true;
    
    @Column(name = "admin_handle")
    @Builder.Default
    private Boolean adminHandle = true;
    
    @Column(name = "admin_name")
    @Builder.Default
    private Boolean adminName = true;
    
    @Column(name = "admin_organization")
    @Builder.Default
    private Boolean adminOrganization = true;
    
    @Column(name = "admin_email")
    @Builder.Default
    private Boolean adminEmail = true;
    
    @Column(name = "admin_phone")
    @Builder.Default
    private Boolean adminPhone = true;
    
    @Column(name = "admin_fax")
    @Builder.Default
    private Boolean adminFax = true;
    
    @Column(name = "admin_address")
    @Builder.Default
    private Boolean adminAddress = true;
    
    @Column(name = "admin_street")
    @Builder.Default
    private Boolean adminStreet = true;
    
    @Column(name = "admin_city")
    @Builder.Default
    private Boolean adminCity = true;
    
    @Column(name = "admin_state_province")
    @Builder.Default
    private Boolean adminStateProvince = true;
    
    @Column(name = "admin_postal_code")
    @Builder.Default
    private Boolean adminPostalCode = true;
    
    @Column(name = "admin_country")
    @Builder.Default
    private Boolean adminCountry = true;

    // ==================== Entity/Contact Fields (Tech) ====================
    
    @Column(name = "tech_entity")
    @Builder.Default
    private Boolean techEntity = true;
    
    @Column(name = "tech_handle")
    @Builder.Default
    private Boolean techHandle = true;
    
    @Column(name = "tech_name")
    @Builder.Default
    private Boolean techName = true;
    
    @Column(name = "tech_organization")
    @Builder.Default
    private Boolean techOrganization = true;
    
    @Column(name = "tech_email")
    @Builder.Default
    private Boolean techEmail = true;
    
    @Column(name = "tech_phone")
    @Builder.Default
    private Boolean techPhone = true;
    
    @Column(name = "tech_fax")
    @Builder.Default
    private Boolean techFax = true;
    
    @Column(name = "tech_address")
    @Builder.Default
    private Boolean techAddress = true;
    
    @Column(name = "tech_street")
    @Builder.Default
    private Boolean techStreet = true;
    
    @Column(name = "tech_city")
    @Builder.Default
    private Boolean techCity = true;
    
    @Column(name = "tech_state_province")
    @Builder.Default
    private Boolean techStateProvince = true;
    
    @Column(name = "tech_postal_code")
    @Builder.Default
    private Boolean techPostalCode = true;
    
    @Column(name = "tech_country")
    @Builder.Default
    private Boolean techCountry = true;

    // ==================== Entity/Contact Fields (Billing) ====================
    
    @Column(name = "billing_entity")
    @Builder.Default
    private Boolean billingEntity = true;
    
    @Column(name = "billing_handle")
    @Builder.Default
    private Boolean billingHandle = true;
    
    @Column(name = "billing_name")
    @Builder.Default
    private Boolean billingName = true;
    
    @Column(name = "billing_organization")
    @Builder.Default
    private Boolean billingOrganization = true;
    
    @Column(name = "billing_email")
    @Builder.Default
    private Boolean billingEmail = true;
    
    @Column(name = "billing_phone")
    @Builder.Default
    private Boolean billingPhone = true;
    
    @Column(name = "billing_fax")
    @Builder.Default
    private Boolean billingFax = true;
    
    @Column(name = "billing_address")
    @Builder.Default
    private Boolean billingAddress = true;
    
    @Column(name = "billing_street")
    @Builder.Default
    private Boolean billingStreet = true;
    
    @Column(name = "billing_city")
    @Builder.Default
    private Boolean billingCity = true;
    
    @Column(name = "billing_state_province")
    @Builder.Default
    private Boolean billingStateProvince = true;
    
    @Column(name = "billing_postal_code")
    @Builder.Default
    private Boolean billingPostalCode = true;
    
    @Column(name = "billing_country")
    @Builder.Default
    private Boolean billingCountry = true;

    // ==================== Entity/Contact Fields (Registrar/Sponsor) ====================
    
    @Column(name = "registrar_entity")
    @Builder.Default
    private Boolean registrarEntity = true;
    
    @Column(name = "registrar_handle")
    @Builder.Default
    private Boolean registrarHandle = true;
    
    @Column(name = "registrar_name")
    @Builder.Default
    private Boolean registrarName = true;
    
    @Column(name = "registrar_email")
    @Builder.Default
    private Boolean registrarEmail = true;
    
    @Column(name = "registrar_phone")
    @Builder.Default
    private Boolean registrarPhone = true;
    
    @Column(name = "registrar_url")
    @Builder.Default
    private Boolean registrarUrl = true;
    
    @Column(name = "registrar_abuse_contact")
    @Builder.Default
    private Boolean registrarAbuseContact = true;

    // ==================== DNSSEC Fields ====================
    
    @Column(name = "dnssec_data")
    @Builder.Default
    private Boolean dnssecData = true;
    
    @Column(name = "dnssec_delegation_signed")
    @Builder.Default
    private Boolean dnssecDelegationSigned = true;
    
    @Column(name = "dnssec_ds_data")
    @Builder.Default
    private Boolean dnssecDsData = true;
    
    @Column(name = "dnssec_key_data")
    @Builder.Default
    private Boolean dnssecKeyData = true;

    // ==================== Network/IP Fields ====================
    
    @Column(name = "network_handle")
    @Builder.Default
    private Boolean networkHandle = true;
    
    @Column(name = "network_name")
    @Builder.Default
    private Boolean networkName = true;
    
    @Column(name = "network_type")
    @Builder.Default
    private Boolean networkType = true;
    
    @Column(name = "network_start_address")
    @Builder.Default
    private Boolean networkStartAddress = true;
    
    @Column(name = "network_end_address")
    @Builder.Default
    private Boolean networkEndAddress = true;
    
    @Column(name = "network_ip_version")
    @Builder.Default
    private Boolean networkIpVersion = true;
    
    @Column(name = "network_parent_handle")
    @Builder.Default
    private Boolean networkParentHandle = true;
    
    @Column(name = "network_cidr")
    @Builder.Default
    private Boolean networkCidr = true;
    
    @Column(name = "network_country")
    @Builder.Default
    private Boolean networkCountry = true;

    // ==================== ASN Fields ====================
    
    @Column(name = "autnum_handle")
    @Builder.Default
    private Boolean autnumHandle = true;
    
    @Column(name = "autnum_start")
    @Builder.Default
    private Boolean autnumStart = true;
    
    @Column(name = "autnum_end")
    @Builder.Default
    private Boolean autnumEnd = true;
    
    @Column(name = "autnum_name")
    @Builder.Default
    private Boolean autnumName = true;
    
    @Column(name = "autnum_type")
    @Builder.Default
    private Boolean autnumType = true;
    
    @Column(name = "autnum_country")
    @Builder.Default
    private Boolean autnumCountry = true;

    // ==================== Links and Notices ====================
    
    @Column(name = "links")
    @Builder.Default
    private Boolean links = true;
    
    @Column(name = "notices")
    @Builder.Default
    private Boolean notices = true;
    
    @Column(name = "remarks")
    @Builder.Default
    private Boolean remarks = true;

    // ==================== Metadata ====================

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
    }

    @PreUpdate
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