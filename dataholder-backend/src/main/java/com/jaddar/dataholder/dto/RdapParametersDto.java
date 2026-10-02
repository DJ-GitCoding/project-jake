/*
 * SPDX-FileCopyrightText: 2025-2026 Edgemoor Research Institute and Derek Jenkins
 * SPDX-License-Identifier: AGPL-3.0-only
 *
 * Author: Derek Jenkins <derek@pure-code.net>
 * Additional terms under AGPL-3.0 Section 7 apply. See NOTICE at the repository root.
 */

package com.jaddar.dataholder.dto;

import com.jaddar.dataholder.entity.AgreementRdapParameters;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.HashMap;
import java.util.Map;

/**
 * DTO for transferring RDAP parameter settings via API.
 * Provides a simplified view and methods for conversion.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RdapParametersDto {

    // Domain fields
    private Boolean domainHandle;
    private Boolean domainName;
    private Boolean domainStatus;
    private Boolean domainPort43;
    private Boolean domainPublicIds;

    // Nameserver fields
    private Boolean nameservers;
    private Boolean nameserverHandle;
    private Boolean nameserverName;
    private Boolean nameserverIpAddresses;
    private Boolean nameserverStatus;

    // Event fields
    private Boolean events;
    private Boolean eventRegistration;
    private Boolean eventExpiration;
    private Boolean eventLastChanged;
    private Boolean eventLastUpdateOfRdapDb;
    private Boolean eventTransfer;

    // Registrant fields
    private Boolean registrantEntity;
    private Boolean registrantHandle;
    private Boolean registrantName;
    private Boolean registrantOrganization;
    private Boolean registrantEmail;
    private Boolean registrantPhone;
    private Boolean registrantFax;
    private Boolean registrantAddress;
    private Boolean registrantStreet;
    private Boolean registrantCity;
    private Boolean registrantStateProvince;
    private Boolean registrantPostalCode;
    private Boolean registrantCountry;

    // Admin fields
    private Boolean adminEntity;
    private Boolean adminHandle;
    private Boolean adminName;
    private Boolean adminOrganization;
    private Boolean adminEmail;
    private Boolean adminPhone;
    private Boolean adminFax;
    private Boolean adminAddress;
    private Boolean adminStreet;
    private Boolean adminCity;
    private Boolean adminStateProvince;
    private Boolean adminPostalCode;
    private Boolean adminCountry;

    // Tech fields
    private Boolean techEntity;
    private Boolean techHandle;
    private Boolean techName;
    private Boolean techOrganization;
    private Boolean techEmail;
    private Boolean techPhone;
    private Boolean techFax;
    private Boolean techAddress;
    private Boolean techStreet;
    private Boolean techCity;
    private Boolean techStateProvince;
    private Boolean techPostalCode;
    private Boolean techCountry;

    // Billing fields
    private Boolean billingEntity;
    private Boolean billingHandle;
    private Boolean billingName;
    private Boolean billingOrganization;
    private Boolean billingEmail;
    private Boolean billingPhone;
    private Boolean billingFax;
    private Boolean billingAddress;
    private Boolean billingStreet;
    private Boolean billingCity;
    private Boolean billingStateProvince;
    private Boolean billingPostalCode;
    private Boolean billingCountry;

    // Registrar fields
    private Boolean registrarEntity;
    private Boolean registrarHandle;
    private Boolean registrarName;
    private Boolean registrarEmail;
    private Boolean registrarPhone;
    private Boolean registrarUrl;
    private Boolean registrarAbuseContact;

    // DNSSEC fields
    private Boolean dnssecData;
    private Boolean dnssecDelegationSigned;
    private Boolean dnssecDsData;
    private Boolean dnssecKeyData;

    // Network fields
    private Boolean networkHandle;
    private Boolean networkName;
    private Boolean networkType;
    private Boolean networkStartAddress;
    private Boolean networkEndAddress;
    private Boolean networkIpVersion;
    private Boolean networkParentHandle;
    private Boolean networkCidr;
    private Boolean networkCountry;

    // ASN fields
    private Boolean autnumHandle;
    private Boolean autnumStart;
    private Boolean autnumEnd;
    private Boolean autnumName;
    private Boolean autnumType;
    private Boolean autnumCountry;

    // Links/Notices
    private Boolean links;
    private Boolean notices;
    private Boolean remarks;

    /**
     * Convert from entity to DTO
     */
    public static RdapParametersDto fromEntity(AgreementRdapParameters entity) {
        if (entity == null) {
            return null;
        }
        
        return RdapParametersDto.builder()
                // Domain
                .domainHandle(entity.getDomainHandle())
                .domainName(entity.getDomainName())
                .domainStatus(entity.getDomainStatus())
                .domainPort43(entity.getDomainPort43())
                .domainPublicIds(entity.getDomainPublicIds())
                // Nameserver
                .nameservers(entity.getNameservers())
                .nameserverHandle(entity.getNameserverHandle())
                .nameserverName(entity.getNameserverName())
                .nameserverIpAddresses(entity.getNameserverIpAddresses())
                .nameserverStatus(entity.getNameserverStatus())
                // Events
                .events(entity.getEvents())
                .eventRegistration(entity.getEventRegistration())
                .eventExpiration(entity.getEventExpiration())
                .eventLastChanged(entity.getEventLastChanged())
                .eventLastUpdateOfRdapDb(entity.getEventLastUpdateOfRdapDb())
                .eventTransfer(entity.getEventTransfer())
                // Registrant
                .registrantEntity(entity.getRegistrantEntity())
                .registrantHandle(entity.getRegistrantHandle())
                .registrantName(entity.getRegistrantName())
                .registrantOrganization(entity.getRegistrantOrganization())
                .registrantEmail(entity.getRegistrantEmail())
                .registrantPhone(entity.getRegistrantPhone())
                .registrantFax(entity.getRegistrantFax())
                .registrantAddress(entity.getRegistrantAddress())
                .registrantStreet(entity.getRegistrantStreet())
                .registrantCity(entity.getRegistrantCity())
                .registrantStateProvince(entity.getRegistrantStateProvince())
                .registrantPostalCode(entity.getRegistrantPostalCode())
                .registrantCountry(entity.getRegistrantCountry())
                // Admin
                .adminEntity(entity.getAdminEntity())
                .adminHandle(entity.getAdminHandle())
                .adminName(entity.getAdminName())
                .adminOrganization(entity.getAdminOrganization())
                .adminEmail(entity.getAdminEmail())
                .adminPhone(entity.getAdminPhone())
                .adminFax(entity.getAdminFax())
                .adminAddress(entity.getAdminAddress())
                .adminStreet(entity.getAdminStreet())
                .adminCity(entity.getAdminCity())
                .adminStateProvince(entity.getAdminStateProvince())
                .adminPostalCode(entity.getAdminPostalCode())
                .adminCountry(entity.getAdminCountry())
                // Tech
                .techEntity(entity.getTechEntity())
                .techHandle(entity.getTechHandle())
                .techName(entity.getTechName())
                .techOrganization(entity.getTechOrganization())
                .techEmail(entity.getTechEmail())
                .techPhone(entity.getTechPhone())
                .techFax(entity.getTechFax())
                .techAddress(entity.getTechAddress())
                .techStreet(entity.getTechStreet())
                .techCity(entity.getTechCity())
                .techStateProvince(entity.getTechStateProvince())
                .techPostalCode(entity.getTechPostalCode())
                .techCountry(entity.getTechCountry())
                // Billing
                .billingEntity(entity.getBillingEntity())
                .billingHandle(entity.getBillingHandle())
                .billingName(entity.getBillingName())
                .billingOrganization(entity.getBillingOrganization())
                .billingEmail(entity.getBillingEmail())
                .billingPhone(entity.getBillingPhone())
                .billingFax(entity.getBillingFax())
                .billingAddress(entity.getBillingAddress())
                .billingStreet(entity.getBillingStreet())
                .billingCity(entity.getBillingCity())
                .billingStateProvince(entity.getBillingStateProvince())
                .billingPostalCode(entity.getBillingPostalCode())
                .billingCountry(entity.getBillingCountry())
                // Registrar
                .registrarEntity(entity.getRegistrarEntity())
                .registrarHandle(entity.getRegistrarHandle())
                .registrarName(entity.getRegistrarName())
                .registrarEmail(entity.getRegistrarEmail())
                .registrarPhone(entity.getRegistrarPhone())
                .registrarUrl(entity.getRegistrarUrl())
                .registrarAbuseContact(entity.getRegistrarAbuseContact())
                // DNSSEC
                .dnssecData(entity.getDnssecData())
                .dnssecDelegationSigned(entity.getDnssecDelegationSigned())
                .dnssecDsData(entity.getDnssecDsData())
                .dnssecKeyData(entity.getDnssecKeyData())
                // Network
                .networkHandle(entity.getNetworkHandle())
                .networkName(entity.getNetworkName())
                .networkType(entity.getNetworkType())
                .networkStartAddress(entity.getNetworkStartAddress())
                .networkEndAddress(entity.getNetworkEndAddress())
                .networkIpVersion(entity.getNetworkIpVersion())
                .networkParentHandle(entity.getNetworkParentHandle())
                .networkCidr(entity.getNetworkCidr())
                .networkCountry(entity.getNetworkCountry())
                // ASN
                .autnumHandle(entity.getAutnumHandle())
                .autnumStart(entity.getAutnumStart())
                .autnumEnd(entity.getAutnumEnd())
                .autnumName(entity.getAutnumName())
                .autnumType(entity.getAutnumType())
                .autnumCountry(entity.getAutnumCountry())
                // Other
                .links(entity.getLinks())
                .notices(entity.getNotices())
                .remarks(entity.getRemarks())
                .build();
    }

    /**
     * Convert DTO to entity
     */
    public AgreementRdapParameters toEntity() {
        return AgreementRdapParameters.builder()
                // Domain
                .domainHandle(defaultTrue(domainHandle))
                .domainName(defaultTrue(domainName))
                .domainStatus(defaultTrue(domainStatus))
                .domainPort43(defaultTrue(domainPort43))
                .domainPublicIds(defaultTrue(domainPublicIds))
                // Nameserver
                .nameservers(defaultTrue(nameservers))
                .nameserverHandle(defaultTrue(nameserverHandle))
                .nameserverName(defaultTrue(nameserverName))
                .nameserverIpAddresses(defaultTrue(nameserverIpAddresses))
                .nameserverStatus(defaultTrue(nameserverStatus))
                // Events
                .events(defaultTrue(events))
                .eventRegistration(defaultTrue(eventRegistration))
                .eventExpiration(defaultTrue(eventExpiration))
                .eventLastChanged(defaultTrue(eventLastChanged))
                .eventLastUpdateOfRdapDb(defaultTrue(eventLastUpdateOfRdapDb))
                .eventTransfer(defaultTrue(eventTransfer))
                // Registrant
                .registrantEntity(defaultTrue(registrantEntity))
                .registrantHandle(defaultTrue(registrantHandle))
                .registrantName(defaultTrue(registrantName))
                .registrantOrganization(defaultTrue(registrantOrganization))
                .registrantEmail(defaultTrue(registrantEmail))
                .registrantPhone(defaultTrue(registrantPhone))
                .registrantFax(defaultTrue(registrantFax))
                .registrantAddress(defaultTrue(registrantAddress))
                .registrantStreet(defaultTrue(registrantStreet))
                .registrantCity(defaultTrue(registrantCity))
                .registrantStateProvince(defaultTrue(registrantStateProvince))
                .registrantPostalCode(defaultTrue(registrantPostalCode))
                .registrantCountry(defaultTrue(registrantCountry))
                // Admin
                .adminEntity(defaultTrue(adminEntity))
                .adminHandle(defaultTrue(adminHandle))
                .adminName(defaultTrue(adminName))
                .adminOrganization(defaultTrue(adminOrganization))
                .adminEmail(defaultTrue(adminEmail))
                .adminPhone(defaultTrue(adminPhone))
                .adminFax(defaultTrue(adminFax))
                .adminAddress(defaultTrue(adminAddress))
                .adminStreet(defaultTrue(adminStreet))
                .adminCity(defaultTrue(adminCity))
                .adminStateProvince(defaultTrue(adminStateProvince))
                .adminPostalCode(defaultTrue(adminPostalCode))
                .adminCountry(defaultTrue(adminCountry))
                // Tech
                .techEntity(defaultTrue(techEntity))
                .techHandle(defaultTrue(techHandle))
                .techName(defaultTrue(techName))
                .techOrganization(defaultTrue(techOrganization))
                .techEmail(defaultTrue(techEmail))
                .techPhone(defaultTrue(techPhone))
                .techFax(defaultTrue(techFax))
                .techAddress(defaultTrue(techAddress))
                .techStreet(defaultTrue(techStreet))
                .techCity(defaultTrue(techCity))
                .techStateProvince(defaultTrue(techStateProvince))
                .techPostalCode(defaultTrue(techPostalCode))
                .techCountry(defaultTrue(techCountry))
                // Billing
                .billingEntity(defaultTrue(billingEntity))
                .billingHandle(defaultTrue(billingHandle))
                .billingName(defaultTrue(billingName))
                .billingOrganization(defaultTrue(billingOrganization))
                .billingEmail(defaultTrue(billingEmail))
                .billingPhone(defaultTrue(billingPhone))
                .billingFax(defaultTrue(billingFax))
                .billingAddress(defaultTrue(billingAddress))
                .billingStreet(defaultTrue(billingStreet))
                .billingCity(defaultTrue(billingCity))
                .billingStateProvince(defaultTrue(billingStateProvince))
                .billingPostalCode(defaultTrue(billingPostalCode))
                .billingCountry(defaultTrue(billingCountry))
                // Registrar
                .registrarEntity(defaultTrue(registrarEntity))
                .registrarHandle(defaultTrue(registrarHandle))
                .registrarName(defaultTrue(registrarName))
                .registrarEmail(defaultTrue(registrarEmail))
                .registrarPhone(defaultTrue(registrarPhone))
                .registrarUrl(defaultTrue(registrarUrl))
                .registrarAbuseContact(defaultTrue(registrarAbuseContact))
                // DNSSEC
                .dnssecData(defaultTrue(dnssecData))
                .dnssecDelegationSigned(defaultTrue(dnssecDelegationSigned))
                .dnssecDsData(defaultTrue(dnssecDsData))
                .dnssecKeyData(defaultTrue(dnssecKeyData))
                // Network
                .networkHandle(defaultTrue(networkHandle))
                .networkName(defaultTrue(networkName))
                .networkType(defaultTrue(networkType))
                .networkStartAddress(defaultTrue(networkStartAddress))
                .networkEndAddress(defaultTrue(networkEndAddress))
                .networkIpVersion(defaultTrue(networkIpVersion))
                .networkParentHandle(defaultTrue(networkParentHandle))
                .networkCidr(defaultTrue(networkCidr))
                .networkCountry(defaultTrue(networkCountry))
                // ASN
                .autnumHandle(defaultTrue(autnumHandle))
                .autnumStart(defaultTrue(autnumStart))
                .autnumEnd(defaultTrue(autnumEnd))
                .autnumName(defaultTrue(autnumName))
                .autnumType(defaultTrue(autnumType))
                .autnumCountry(defaultTrue(autnumCountry))
                // Other
                .links(defaultTrue(links))
                .notices(defaultTrue(notices))
                .remarks(defaultTrue(remarks))
                .build();
    }

    /**
     * Update an existing entity with values from this DTO.
     * Only updates fields that are non-null in the DTO.
     */
    public void updateEntity(AgreementRdapParameters entity) {
        // Domain
        if (domainHandle != null) entity.setDomainHandle(domainHandle);
        if (domainName != null) entity.setDomainName(domainName);
        if (domainStatus != null) entity.setDomainStatus(domainStatus);
        if (domainPort43 != null) entity.setDomainPort43(domainPort43);
        if (domainPublicIds != null) entity.setDomainPublicIds(domainPublicIds);
        // Nameserver
        if (nameservers != null) entity.setNameservers(nameservers);
        if (nameserverHandle != null) entity.setNameserverHandle(nameserverHandle);
        if (nameserverName != null) entity.setNameserverName(nameserverName);
        if (nameserverIpAddresses != null) entity.setNameserverIpAddresses(nameserverIpAddresses);
        if (nameserverStatus != null) entity.setNameserverStatus(nameserverStatus);
        // Events
        if (events != null) entity.setEvents(events);
        if (eventRegistration != null) entity.setEventRegistration(eventRegistration);
        if (eventExpiration != null) entity.setEventExpiration(eventExpiration);
        if (eventLastChanged != null) entity.setEventLastChanged(eventLastChanged);
        if (eventLastUpdateOfRdapDb != null) entity.setEventLastUpdateOfRdapDb(eventLastUpdateOfRdapDb);
        if (eventTransfer != null) entity.setEventTransfer(eventTransfer);
        // Registrant
        if (registrantEntity != null) entity.setRegistrantEntity(registrantEntity);
        if (registrantHandle != null) entity.setRegistrantHandle(registrantHandle);
        if (registrantName != null) entity.setRegistrantName(registrantName);
        if (registrantOrganization != null) entity.setRegistrantOrganization(registrantOrganization);
        if (registrantEmail != null) entity.setRegistrantEmail(registrantEmail);
        if (registrantPhone != null) entity.setRegistrantPhone(registrantPhone);
        if (registrantFax != null) entity.setRegistrantFax(registrantFax);
        if (registrantAddress != null) entity.setRegistrantAddress(registrantAddress);
        if (registrantStreet != null) entity.setRegistrantStreet(registrantStreet);
        if (registrantCity != null) entity.setRegistrantCity(registrantCity);
        if (registrantStateProvince != null) entity.setRegistrantStateProvince(registrantStateProvince);
        if (registrantPostalCode != null) entity.setRegistrantPostalCode(registrantPostalCode);
        if (registrantCountry != null) entity.setRegistrantCountry(registrantCountry);
        // Admin
        if (adminEntity != null) entity.setAdminEntity(adminEntity);
        if (adminHandle != null) entity.setAdminHandle(adminHandle);
        if (adminName != null) entity.setAdminName(adminName);
        if (adminOrganization != null) entity.setAdminOrganization(adminOrganization);
        if (adminEmail != null) entity.setAdminEmail(adminEmail);
        if (adminPhone != null) entity.setAdminPhone(adminPhone);
        if (adminFax != null) entity.setAdminFax(adminFax);
        if (adminAddress != null) entity.setAdminAddress(adminAddress);
        if (adminStreet != null) entity.setAdminStreet(adminStreet);
        if (adminCity != null) entity.setAdminCity(adminCity);
        if (adminStateProvince != null) entity.setAdminStateProvince(adminStateProvince);
        if (adminPostalCode != null) entity.setAdminPostalCode(adminPostalCode);
        if (adminCountry != null) entity.setAdminCountry(adminCountry);
        // Tech
        if (techEntity != null) entity.setTechEntity(techEntity);
        if (techHandle != null) entity.setTechHandle(techHandle);
        if (techName != null) entity.setTechName(techName);
        if (techOrganization != null) entity.setTechOrganization(techOrganization);
        if (techEmail != null) entity.setTechEmail(techEmail);
        if (techPhone != null) entity.setTechPhone(techPhone);
        if (techFax != null) entity.setTechFax(techFax);
        if (techAddress != null) entity.setTechAddress(techAddress);
        if (techStreet != null) entity.setTechStreet(techStreet);
        if (techCity != null) entity.setTechCity(techCity);
        if (techStateProvince != null) entity.setTechStateProvince(techStateProvince);
        if (techPostalCode != null) entity.setTechPostalCode(techPostalCode);
        if (techCountry != null) entity.setTechCountry(techCountry);
        // Billing
        if (billingEntity != null) entity.setBillingEntity(billingEntity);
        if (billingHandle != null) entity.setBillingHandle(billingHandle);
        if (billingName != null) entity.setBillingName(billingName);
        if (billingOrganization != null) entity.setBillingOrganization(billingOrganization);
        if (billingEmail != null) entity.setBillingEmail(billingEmail);
        if (billingPhone != null) entity.setBillingPhone(billingPhone);
        if (billingFax != null) entity.setBillingFax(billingFax);
        if (billingAddress != null) entity.setBillingAddress(billingAddress);
        if (billingStreet != null) entity.setBillingStreet(billingStreet);
        if (billingCity != null) entity.setBillingCity(billingCity);
        if (billingStateProvince != null) entity.setBillingStateProvince(billingStateProvince);
        if (billingPostalCode != null) entity.setBillingPostalCode(billingPostalCode);
        if (billingCountry != null) entity.setBillingCountry(billingCountry);
        // Registrar
        if (registrarEntity != null) entity.setRegistrarEntity(registrarEntity);
        if (registrarHandle != null) entity.setRegistrarHandle(registrarHandle);
        if (registrarName != null) entity.setRegistrarName(registrarName);
        if (registrarEmail != null) entity.setRegistrarEmail(registrarEmail);
        if (registrarPhone != null) entity.setRegistrarPhone(registrarPhone);
        if (registrarUrl != null) entity.setRegistrarUrl(registrarUrl);
        if (registrarAbuseContact != null) entity.setRegistrarAbuseContact(registrarAbuseContact);
        // DNSSEC
        if (dnssecData != null) entity.setDnssecData(dnssecData);
        if (dnssecDelegationSigned != null) entity.setDnssecDelegationSigned(dnssecDelegationSigned);
        if (dnssecDsData != null) entity.setDnssecDsData(dnssecDsData);
        if (dnssecKeyData != null) entity.setDnssecKeyData(dnssecKeyData);
        // Network
        if (networkHandle != null) entity.setNetworkHandle(networkHandle);
        if (networkName != null) entity.setNetworkName(networkName);
        if (networkType != null) entity.setNetworkType(networkType);
        if (networkStartAddress != null) entity.setNetworkStartAddress(networkStartAddress);
        if (networkEndAddress != null) entity.setNetworkEndAddress(networkEndAddress);
        if (networkIpVersion != null) entity.setNetworkIpVersion(networkIpVersion);
        if (networkParentHandle != null) entity.setNetworkParentHandle(networkParentHandle);
        if (networkCidr != null) entity.setNetworkCidr(networkCidr);
        if (networkCountry != null) entity.setNetworkCountry(networkCountry);
        // ASN
        if (autnumHandle != null) entity.setAutnumHandle(autnumHandle);
        if (autnumStart != null) entity.setAutnumStart(autnumStart);
        if (autnumEnd != null) entity.setAutnumEnd(autnumEnd);
        if (autnumName != null) entity.setAutnumName(autnumName);
        if (autnumType != null) entity.setAutnumType(autnumType);
        if (autnumCountry != null) entity.setAutnumCountry(autnumCountry);
        // Other
        if (links != null) entity.setLinks(links);
        if (notices != null) entity.setNotices(notices);
        if (remarks != null) entity.setRemarks(remarks);
    }

    /**
     * Convert to a simplified map grouped by category
     */
    public Map<String, Map<String, Boolean>> toGroupedMap() {
        Map<String, Map<String, Boolean>> result = new HashMap<>();
        
        // Domain
        Map<String, Boolean> domain = new HashMap<>();
        domain.put("handle", domainHandle);
        domain.put("name", domainName);
        domain.put("status", domainStatus);
        domain.put("port43", domainPort43);
        domain.put("publicIds", domainPublicIds);
        result.put("domain", domain);
        
        // Nameserver
        Map<String, Boolean> nameserver = new HashMap<>();
        nameserver.put("enabled", nameservers);
        nameserver.put("handle", nameserverHandle);
        nameserver.put("name", nameserverName);
        nameserver.put("ipAddresses", nameserverIpAddresses);
        nameserver.put("status", nameserverStatus);
        result.put("nameserver", nameserver);
        
        // Events
        Map<String, Boolean> event = new HashMap<>();
        event.put("enabled", events);
        event.put("registration", eventRegistration);
        event.put("expiration", eventExpiration);
        event.put("lastChanged", eventLastChanged);
        event.put("lastUpdateOfRdapDb", eventLastUpdateOfRdapDb);
        event.put("transfer", eventTransfer);
        result.put("events", event);
        
        // Registrant
        Map<String, Boolean> registrant = new HashMap<>();
        registrant.put("entity", registrantEntity);
        registrant.put("handle", registrantHandle);
        registrant.put("name", registrantName);
        registrant.put("organization", registrantOrganization);
        registrant.put("email", registrantEmail);
        registrant.put("phone", registrantPhone);
        registrant.put("fax", registrantFax);
        registrant.put("address", registrantAddress);
        registrant.put("street", registrantStreet);
        registrant.put("city", registrantCity);
        registrant.put("stateProvince", registrantStateProvince);
        registrant.put("postalCode", registrantPostalCode);
        registrant.put("country", registrantCountry);
        result.put("registrant", registrant);
        
        // Admin
        Map<String, Boolean> admin = new HashMap<>();
        admin.put("entity", adminEntity);
        admin.put("handle", adminHandle);
        admin.put("name", adminName);
        admin.put("organization", adminOrganization);
        admin.put("email", adminEmail);
        admin.put("phone", adminPhone);
        admin.put("fax", adminFax);
        admin.put("address", adminAddress);
        admin.put("street", adminStreet);
        admin.put("city", adminCity);
        admin.put("stateProvince", adminStateProvince);
        admin.put("postalCode", adminPostalCode);
        admin.put("country", adminCountry);
        result.put("admin", admin);
        
        // Tech
        Map<String, Boolean> tech = new HashMap<>();
        tech.put("entity", techEntity);
        tech.put("handle", techHandle);
        tech.put("name", techName);
        tech.put("organization", techOrganization);
        tech.put("email", techEmail);
        tech.put("phone", techPhone);
        tech.put("fax", techFax);
        tech.put("address", techAddress);
        tech.put("street", techStreet);
        tech.put("city", techCity);
        tech.put("stateProvince", techStateProvince);
        tech.put("postalCode", techPostalCode);
        tech.put("country", techCountry);
        result.put("tech", tech);
        
        // Billing
        Map<String, Boolean> billing = new HashMap<>();
        billing.put("entity", billingEntity);
        billing.put("handle", billingHandle);
        billing.put("name", billingName);
        billing.put("organization", billingOrganization);
        billing.put("email", billingEmail);
        billing.put("phone", billingPhone);
        billing.put("fax", billingFax);
        billing.put("address", billingAddress);
        billing.put("street", billingStreet);
        billing.put("city", billingCity);
        billing.put("stateProvince", billingStateProvince);
        billing.put("postalCode", billingPostalCode);
        billing.put("country", billingCountry);
        result.put("billing", billing);
        
        // Registrar
        Map<String, Boolean> registrar = new HashMap<>();
        registrar.put("entity", registrarEntity);
        registrar.put("handle", registrarHandle);
        registrar.put("name", registrarName);
        registrar.put("email", registrarEmail);
        registrar.put("phone", registrarPhone);
        registrar.put("url", registrarUrl);
        registrar.put("abuseContact", registrarAbuseContact);
        result.put("registrar", registrar);
        
        // DNSSEC
        Map<String, Boolean> dnssec = new HashMap<>();
        dnssec.put("data", dnssecData);
        dnssec.put("delegationSigned", dnssecDelegationSigned);
        dnssec.put("dsData", dnssecDsData);
        dnssec.put("keyData", dnssecKeyData);
        result.put("dnssec", dnssec);
        
        // Network
        Map<String, Boolean> network = new HashMap<>();
        network.put("handle", networkHandle);
        network.put("name", networkName);
        network.put("type", networkType);
        network.put("startAddress", networkStartAddress);
        network.put("endAddress", networkEndAddress);
        network.put("ipVersion", networkIpVersion);
        network.put("parentHandle", networkParentHandle);
        network.put("cidr", networkCidr);
        network.put("country", networkCountry);
        result.put("network", network);
        
        // ASN
        Map<String, Boolean> autnum = new HashMap<>();
        autnum.put("handle", autnumHandle);
        autnum.put("start", autnumStart);
        autnum.put("end", autnumEnd);
        autnum.put("name", autnumName);
        autnum.put("type", autnumType);
        autnum.put("country", autnumCountry);
        result.put("autnum", autnum);
        
        // Other
        Map<String, Boolean> other = new HashMap<>();
        other.put("links", links);
        other.put("notices", notices);
        other.put("remarks", remarks);
        result.put("other", other);
        
        return result;
    }

    private Boolean defaultTrue(Boolean value) {
        return value != null ? value : true;
    }
}