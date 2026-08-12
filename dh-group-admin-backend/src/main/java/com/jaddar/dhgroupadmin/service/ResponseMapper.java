/*
 * SPDX-FileCopyrightText: 2025-2026 Edgemoor Research Institute and Derek Jenkins
 * SPDX-License-Identifier: AGPL-3.0-only
 *
 * Author: Derek Jenkins <derek@pure-code.net>
 * Additional terms under AGPL-3.0 Section 7 apply. See NOTICE at the repository root.
 */

package com.jaddar.dhgroupadmin.service;

import com.jaddar.dhgroupadmin.entity.*;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Centralized response mapping for entities to API response maps.
 * Used by both admin and external controllers.
 */
@Component
public class ResponseMapper {

    public Map<String, Object> toTemplateResponse(AgreementTemplate template) {
        Map<String, Object> r = new HashMap<>();
        r.put("id", template.getId());
        r.put("templateId", template.getTemplateId());
        r.put("name", template.getName());
        r.put("shortDescription", template.getShortDescription());
        r.put("description", template.getDescription());
        r.put("requiredGroupTypes", template.getRequiredGroupTypes());
        r.put("termsAndConditions", template.getTermsAndConditions());
        r.put("dataUsagePolicy", template.getDataUsagePolicy());
        r.put("disclosureMode", template.getDisclosureMode());
        r.put("maxQueriesPerDay", template.getMaxQueriesPerDay());
        r.put("maxQueriesPerMonth", template.getMaxQueriesPerMonth());
        r.put("isPublished", template.getIsPublished());
        r.put("createdAt", template.getCreatedAt());
        r.put("updatedAt", template.getUpdatedAt());
        r.put("createdBy", template.getCreatedBy());
        r.put("dataHolderGroupId", template.getDataHolderGroupId());
        r.put("requestTypes", template.getRequestTypes().stream().map(this::toRequestTypeResponse).toList());
        r.put("defaultAccessLevel", template.getDefaultAccessLevel());
        r.put("highestAccessLevel", template.getHighestAccessLevel());
        r.put("supportsConfidential", template.supportsConfidential());
        r.put("supportsExigent", template.supportsExigent());
        return r;
    }

    public Map<String, Object> toRequestTypeResponse(AgreementRequestType rt) {
        Map<String, Object> r = new HashMap<>();
        r.put("id", rt.getId());
        r.put("name", rt.getName());
        r.put("typeCode", rt.getTypeCode());
        r.put("description", rt.getDescription());
        r.put("accessLevel", rt.getAccessLevel());
        r.put("supportsConfidential", rt.getSupportsConfidential());
        r.put("supportsExigent", rt.getSupportsExigent());
        r.put("requiresManualApproval", rt.getRequiresManualApproval());
        r.put("sortOrder", rt.getSortOrder());
        r.put("isActive", rt.getIsActive());
        r.put("queryValueRegex", rt.getQueryValueRegex());
        r.put("queryValueRegexError", rt.getQueryValueRegexError());
        r.put("createdAt", rt.getCreatedAt());
        r.put("updatedAt", rt.getUpdatedAt());
        if (rt.getRdapParameters() != null) {
            r.put("rdapParameters", toRdapParametersMap(rt.getRdapParameters()));
        }
        r.put("customParameters", rt.getCustomParameters().stream().map(this::toCustomParameterMap).toList());
        return r;
    }

    public Map<String, Object> toCustomParameterMap(RequestTypeCustomParameter p) {
        Map<String, Object> m = new HashMap<>();
        m.put("id", p.getId());
        m.put("name", p.getName());
        m.put("dataType", p.getDataType());
        m.put("required", p.getRequired());
        m.put("description", p.getDescription());
        m.put("defaultValue", p.getDefaultValue());
        m.put("placeholder", p.getPlaceholder());
        m.put("enumValues", p.getEnumValues());
        m.put("validationRegex", p.getValidationRegex());
        m.put("minValue", p.getMinValue());
        m.put("maxValue", p.getMaxValue());
        m.put("maxLength", p.getMaxLength());
        m.put("allowedFileTypes", p.getAllowedFileTypes());
        m.put("maxFileSizeMb", p.getMaxFileSizeMb());
        m.put("fileCriteria", p.getFileCriteria());
        m.put("sortOrder", p.getSortOrder());
        return m;
    }

    /**
     * Subscription response including the introspection client secret.
     *
     * Use ONLY on endpoints authenticated as a data holder: the data holder must
     * hold the secret to authenticate at the requestor's introspection endpoint,
     * but it must never reach the admin UI or any browser-facing response. Every
     * other caller takes {@link #toSubscriptionResponse}, which omits it.
     */
    public Map<String, Object> toSubscriptionResponseForDataHolder(AgreementSubscription s) {
        Map<String, Object> r = toSubscriptionResponse(s);
        r.put("introspectionClientSecret", s.getIntrospectionClientSecret());
        return r;
    }

    public Map<String, Object> toSubscriptionResponse(AgreementSubscription s) {
        Map<String, Object> r = new HashMap<>();
        r.put("id", s.getId());
        r.put("requestId", s.getRequestId());
        r.put("status", s.getStatus().name());
        r.put("statusMessage", s.getStatusMessage());
        r.put("statusChangedAt", s.getStatusChangedAt());
        r.put("dataholderId", s.getDataholderId());
        r.put("dataholderName", s.getDataholderName());
        r.put("dataHolderGroupId", s.getDataHolderGroupId());
        r.put("requestorGroupId", s.getRequestorGroupId());
        r.put("requestorGroupName", s.getRequestorGroupName());
        r.put("requestorGroupCode", s.getRequestorGroupCode());
        r.put("requestorGroupType", s.getRequestorGroupType());
        r.put("requestorDescription", s.getRequestorDescription());
        r.put("requestorFirstName", s.getRequestorFirstName());
        r.put("requestorLastName", s.getRequestorLastName());
        r.put("requestorOrganization", s.getRequestorOrganization());
        r.put("requestorContactEmail", s.getRequestorContactEmail());
        r.put("requestorPhone", s.getRequestorPhone());
        r.put("requestorAddress", s.getRequestorAddress());
        r.put("requestorCity", s.getRequestorCity());
        r.put("requestorStateProvince", s.getRequestorStateProvince());
        r.put("requestorPostalCode", s.getRequestorPostalCode());
        r.put("requestorCountry", s.getRequestorCountry());
        r.put("formattedAddress", s.getFormattedAddress());
        r.put("requestorAgentId", s.getRequestorAgentId());
        r.put("callbackUrl", s.getRequestorAgentCallbackUrl());
        r.put("effectiveAccessLevel", s.getEffectiveAccessLevel());
        r.put("highestAccessLevel", s.getHighestAccessLevel());
        r.put("supportsConfidential", s.supportsConfidential());
        r.put("supportsExigent", s.supportsExigent());
        r.put("purpose", s.getPurpose());
        r.put("additionalTerms", s.getAdditionalTerms());
        r.put("introspectionUrl", s.getIntrospectionUrl());
        r.put("introspectionClientId", s.getIntrospectionClientId());
        r.put("testResult", s.getTestResult());
        r.put("testDetails", s.getTestDetails());
        r.put("testStartedAt", s.getTestStartedAt());
        r.put("testCompletedAt", s.getTestCompletedAt());
        r.put("reviewedBy", s.getReviewedBy());
        r.put("reviewedAt", s.getReviewedAt());
        r.put("reviewNotes", s.getReviewNotes());
        r.put("effectiveFrom", s.getEffectiveFrom());
        r.put("effectiveTo", s.getEffectiveTo());
        r.put("activatedAt", s.getActivatedAt());
        r.put("activatedBy", s.getActivatedBy());
        r.put("maxQueriesPerDay", s.getMaxQueriesPerDay());
        r.put("maxQueriesPerMonth", s.getMaxQueriesPerMonth());
        r.put("createdAt", s.getCreatedAt());
        r.put("updatedAt", s.getUpdatedAt());
        r.put("requestExpiresAt", s.getRequestExpiresAt());

        if (s.getTemplate() != null) {
            r.put("templateId", s.getTemplate().getTemplateId());
            r.put("templateName", s.getTemplate().getName());
            r.put("templateDescription", s.getTemplate().getDescription());
            r.put("requestTypes", s.getTemplate().getRequestTypes().stream()
                    .map(this::toRequestTypeResponse).toList());
        }
        return r;
    }

    public Map<String, Object> toStatusLogResponse(AgreementStatusLog log) {
        Map<String, Object> r = new HashMap<>();
        r.put("id", log.getId());
        r.put("previousStatus", log.getPreviousStatus());
        r.put("newStatus", log.getNewStatus());
        r.put("changedBy", log.getChangedBy());
        r.put("changeReason", log.getChangeReason());
        r.put("source", log.getSource());
        r.put("createdAt", log.getCreatedAt());
        return r;
    }

    public Map<String, Object> toDataHolderResponse(DataHolder dh) {
        Map<String, Object> r = new HashMap<>();
        r.put("id", dh.getId());
        r.put("dataholderId", dh.getDataholderId());
        r.put("name", dh.getName());
        r.put("url", dh.getUrl());
        r.put("description", dh.getDescription());
        r.put("contactEmail", dh.getContactEmail());
        r.put("callbackUrl", dh.getCallbackUrl());
        // Legacy single group (backward compat)
        r.put("dataHolderGroupId", dh.getDataHolderGroupId());
        // New: all group memberships
        List<Map<String, Object>> groupList = new java.util.ArrayList<>();
        if (dh.getDataHolderGroups() != null) {
            dh.getDataHolderGroups().forEach(g -> {
                Map<String, Object> gm = new HashMap<>();
                gm.put("id", g.getId());
                gm.put("name", g.getName());
                groupList.add(gm);
            });
        }
        // Fallback: if join table empty but legacy field set, add it
        if (groupList.isEmpty() && dh.getDataHolderGroupId() != null) {
            groupList.add(Map.of("id", dh.getDataHolderGroupId(), "name", ""));
        }
        r.put("dataHolderGroupIds", dh.getAllGroupIds());
        r.put("dataHolderGroups", groupList);
        r.put("isActive", dh.getIsActive());
        r.put("lastPingAt", dh.getLastPingAt());
        r.put("createdAt", dh.getCreatedAt());
        r.put("updatedAt", dh.getUpdatedAt());
        return r;
    }

    public Map<String, Boolean> toRdapParametersMap(AgreementRdapParameters p) {
        Map<String, Boolean> m = new HashMap<>();
        m.put("domainHandle", p.getDomainHandle()); m.put("domainName", p.getDomainName());
        m.put("domainStatus", p.getDomainStatus()); m.put("domainPort43", p.getDomainPort43());
        m.put("domainPublicIds", p.getDomainPublicIds());
        m.put("nameservers", p.getNameservers()); m.put("nameserverHandle", p.getNameserverHandle());
        m.put("nameserverName", p.getNameserverName()); m.put("nameserverIpAddresses", p.getNameserverIpAddresses());
        m.put("nameserverStatus", p.getNameserverStatus());
        m.put("events", p.getEvents()); m.put("eventRegistration", p.getEventRegistration());
        m.put("eventExpiration", p.getEventExpiration()); m.put("eventLastChanged", p.getEventLastChanged());
        m.put("eventLastUpdateOfRdapDb", p.getEventLastUpdateOfRdapDb()); m.put("eventTransfer", p.getEventTransfer());
        m.put("registrantEntity", p.getRegistrantEntity()); m.put("registrantHandle", p.getRegistrantHandle());
        m.put("registrantName", p.getRegistrantName()); m.put("registrantOrganization", p.getRegistrantOrganization());
        m.put("registrantEmail", p.getRegistrantEmail()); m.put("registrantPhone", p.getRegistrantPhone());
        m.put("registrantFax", p.getRegistrantFax()); m.put("registrantAddress", p.getRegistrantAddress());
        m.put("registrantStreet", p.getRegistrantStreet()); m.put("registrantCity", p.getRegistrantCity());
        m.put("registrantStateProvince", p.getRegistrantStateProvince()); m.put("registrantPostalCode", p.getRegistrantPostalCode());
        m.put("registrantCountry", p.getRegistrantCountry());
        m.put("adminEntity", p.getAdminEntity()); m.put("adminHandle", p.getAdminHandle());
        m.put("adminName", p.getAdminName()); m.put("adminOrganization", p.getAdminOrganization());
        m.put("adminEmail", p.getAdminEmail()); m.put("adminPhone", p.getAdminPhone());
        m.put("adminFax", p.getAdminFax()); m.put("adminAddress", p.getAdminAddress());
        m.put("adminStreet", p.getAdminStreet()); m.put("adminCity", p.getAdminCity());
        m.put("adminStateProvince", p.getAdminStateProvince()); m.put("adminPostalCode", p.getAdminPostalCode());
        m.put("adminCountry", p.getAdminCountry());
        m.put("techEntity", p.getTechEntity()); m.put("techHandle", p.getTechHandle());
        m.put("techName", p.getTechName()); m.put("techOrganization", p.getTechOrganization());
        m.put("techEmail", p.getTechEmail()); m.put("techPhone", p.getTechPhone());
        m.put("techFax", p.getTechFax()); m.put("techAddress", p.getTechAddress());
        m.put("techStreet", p.getTechStreet()); m.put("techCity", p.getTechCity());
        m.put("techStateProvince", p.getTechStateProvince()); m.put("techPostalCode", p.getTechPostalCode());
        m.put("techCountry", p.getTechCountry());
        m.put("billingEntity", p.getBillingEntity()); m.put("billingHandle", p.getBillingHandle());
        m.put("billingName", p.getBillingName()); m.put("billingOrganization", p.getBillingOrganization());
        m.put("billingEmail", p.getBillingEmail()); m.put("billingPhone", p.getBillingPhone());
        m.put("billingFax", p.getBillingFax()); m.put("billingAddress", p.getBillingAddress());
        m.put("billingStreet", p.getBillingStreet()); m.put("billingCity", p.getBillingCity());
        m.put("billingStateProvince", p.getBillingStateProvince()); m.put("billingPostalCode", p.getBillingPostalCode());
        m.put("billingCountry", p.getBillingCountry());
        m.put("registrarEntity", p.getRegistrarEntity()); m.put("registrarHandle", p.getRegistrarHandle());
        m.put("registrarName", p.getRegistrarName()); m.put("registrarEmail", p.getRegistrarEmail());
        m.put("registrarPhone", p.getRegistrarPhone()); m.put("registrarUrl", p.getRegistrarUrl());
        m.put("registrarAbuseContact", p.getRegistrarAbuseContact());
        m.put("dnssecData", p.getDnssecData()); m.put("dnssecDelegationSigned", p.getDnssecDelegationSigned());
        m.put("dnssecDsData", p.getDnssecDsData()); m.put("dnssecKeyData", p.getDnssecKeyData());
        m.put("networkHandle", p.getNetworkHandle()); m.put("networkName", p.getNetworkName());
        m.put("networkType", p.getNetworkType()); m.put("networkStartAddress", p.getNetworkStartAddress());
        m.put("networkEndAddress", p.getNetworkEndAddress()); m.put("networkIpVersion", p.getNetworkIpVersion());
        m.put("networkParentHandle", p.getNetworkParentHandle()); m.put("networkCidr", p.getNetworkCidr());
        m.put("networkCountry", p.getNetworkCountry());
        m.put("autnumHandle", p.getAutnumHandle()); m.put("autnumStart", p.getAutnumStart());
        m.put("autnumEnd", p.getAutnumEnd()); m.put("autnumName", p.getAutnumName());
        m.put("autnumType", p.getAutnumType()); m.put("autnumCountry", p.getAutnumCountry());
        m.put("links", p.getLinks()); m.put("notices", p.getNotices()); m.put("remarks", p.getRemarks());
        return m;
    }

    public AgreementRdapParameters buildRdapParameters(Map<String, Boolean> p) {
        return AgreementRdapParameters.builder()
                .domainHandle(p.getOrDefault("domainHandle", true)).domainName(p.getOrDefault("domainName", true))
                .domainStatus(p.getOrDefault("domainStatus", true)).domainPort43(p.getOrDefault("domainPort43", true))
                .domainPublicIds(p.getOrDefault("domainPublicIds", true))
                .nameservers(p.getOrDefault("nameservers", true)).nameserverHandle(p.getOrDefault("nameserverHandle", true))
                .nameserverName(p.getOrDefault("nameserverName", true)).nameserverIpAddresses(p.getOrDefault("nameserverIpAddresses", true))
                .nameserverStatus(p.getOrDefault("nameserverStatus", true))
                .events(p.getOrDefault("events", true)).eventRegistration(p.getOrDefault("eventRegistration", true))
                .eventExpiration(p.getOrDefault("eventExpiration", true)).eventLastChanged(p.getOrDefault("eventLastChanged", true))
                .eventLastUpdateOfRdapDb(p.getOrDefault("eventLastUpdateOfRdapDb", true)).eventTransfer(p.getOrDefault("eventTransfer", true))
                .registrantEntity(p.getOrDefault("registrantEntity", true)).registrantHandle(p.getOrDefault("registrantHandle", true))
                .registrantName(p.getOrDefault("registrantName", true)).registrantOrganization(p.getOrDefault("registrantOrganization", true))
                .registrantEmail(p.getOrDefault("registrantEmail", true)).registrantPhone(p.getOrDefault("registrantPhone", true))
                .registrantFax(p.getOrDefault("registrantFax", true)).registrantAddress(p.getOrDefault("registrantAddress", true))
                .registrantStreet(p.getOrDefault("registrantStreet", true)).registrantCity(p.getOrDefault("registrantCity", true))
                .registrantStateProvince(p.getOrDefault("registrantStateProvince", true)).registrantPostalCode(p.getOrDefault("registrantPostalCode", true))
                .registrantCountry(p.getOrDefault("registrantCountry", true))
                .adminEntity(p.getOrDefault("adminEntity", true)).adminHandle(p.getOrDefault("adminHandle", true))
                .adminName(p.getOrDefault("adminName", true)).adminOrganization(p.getOrDefault("adminOrganization", true))
                .adminEmail(p.getOrDefault("adminEmail", true)).adminPhone(p.getOrDefault("adminPhone", true))
                .adminFax(p.getOrDefault("adminFax", true)).adminAddress(p.getOrDefault("adminAddress", true))
                .adminStreet(p.getOrDefault("adminStreet", true)).adminCity(p.getOrDefault("adminCity", true))
                .adminStateProvince(p.getOrDefault("adminStateProvince", true)).adminPostalCode(p.getOrDefault("adminPostalCode", true))
                .adminCountry(p.getOrDefault("adminCountry", true))
                .techEntity(p.getOrDefault("techEntity", true)).techHandle(p.getOrDefault("techHandle", true))
                .techName(p.getOrDefault("techName", true)).techOrganization(p.getOrDefault("techOrganization", true))
                .techEmail(p.getOrDefault("techEmail", true)).techPhone(p.getOrDefault("techPhone", true))
                .techFax(p.getOrDefault("techFax", true)).techAddress(p.getOrDefault("techAddress", true))
                .techStreet(p.getOrDefault("techStreet", true)).techCity(p.getOrDefault("techCity", true))
                .techStateProvince(p.getOrDefault("techStateProvince", true)).techPostalCode(p.getOrDefault("techPostalCode", true))
                .techCountry(p.getOrDefault("techCountry", true))
                .billingEntity(p.getOrDefault("billingEntity", true)).billingHandle(p.getOrDefault("billingHandle", true))
                .billingName(p.getOrDefault("billingName", true)).billingOrganization(p.getOrDefault("billingOrganization", true))
                .billingEmail(p.getOrDefault("billingEmail", true)).billingPhone(p.getOrDefault("billingPhone", true))
                .billingFax(p.getOrDefault("billingFax", true)).billingAddress(p.getOrDefault("billingAddress", true))
                .billingStreet(p.getOrDefault("billingStreet", true)).billingCity(p.getOrDefault("billingCity", true))
                .billingStateProvince(p.getOrDefault("billingStateProvince", true)).billingPostalCode(p.getOrDefault("billingPostalCode", true))
                .billingCountry(p.getOrDefault("billingCountry", true))
                .registrarEntity(p.getOrDefault("registrarEntity", true)).registrarHandle(p.getOrDefault("registrarHandle", true))
                .registrarName(p.getOrDefault("registrarName", true)).registrarEmail(p.getOrDefault("registrarEmail", true))
                .registrarPhone(p.getOrDefault("registrarPhone", true)).registrarUrl(p.getOrDefault("registrarUrl", true))
                .registrarAbuseContact(p.getOrDefault("registrarAbuseContact", true))
                .dnssecData(p.getOrDefault("dnssecData", true)).dnssecDelegationSigned(p.getOrDefault("dnssecDelegationSigned", true))
                .dnssecDsData(p.getOrDefault("dnssecDsData", true)).dnssecKeyData(p.getOrDefault("dnssecKeyData", true))
                .networkHandle(p.getOrDefault("networkHandle", true)).networkName(p.getOrDefault("networkName", true))
                .networkType(p.getOrDefault("networkType", true)).networkStartAddress(p.getOrDefault("networkStartAddress", true))
                .networkEndAddress(p.getOrDefault("networkEndAddress", true)).networkIpVersion(p.getOrDefault("networkIpVersion", true))
                .networkParentHandle(p.getOrDefault("networkParentHandle", true)).networkCidr(p.getOrDefault("networkCidr", true))
                .networkCountry(p.getOrDefault("networkCountry", true))
                .autnumHandle(p.getOrDefault("autnumHandle", true)).autnumStart(p.getOrDefault("autnumStart", true))
                .autnumEnd(p.getOrDefault("autnumEnd", true)).autnumName(p.getOrDefault("autnumName", true))
                .autnumType(p.getOrDefault("autnumType", true)).autnumCountry(p.getOrDefault("autnumCountry", true))
                .links(p.getOrDefault("links", true)).notices(p.getOrDefault("notices", true))
                .remarks(p.getOrDefault("remarks", true))
                .build();
    }
}