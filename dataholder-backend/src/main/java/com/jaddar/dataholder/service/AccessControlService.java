/*
 * SPDX-FileCopyrightText: 2025-2026 Edgemoor Research Institute and Derek Jenkins
 * SPDX-License-Identifier: AGPL-3.0-only
 *
 * Author: Derek Jenkins <derek@pure-code.net>
 * Additional terms under AGPL-3.0 Section 7 apply. See NOTICE at the repository root.
 */

package com.jaddar.dataholder.service;

import com.jaddar.dataholder.entity.TokenInfo;
import com.jaddar.dataholder.entity.PolicyExpression;
import com.jaddar.dataholder.entity.AgreementRequestType;
import com.jaddar.dataholder.entity.AgreementRdapParameters;
import com.jaddar.dataholder.repository.PolicyExpressionRepository;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.*;

/**
 * Access control service that resolves subscription data from the
 * Data Holder Group Admin.
 *
 * Templates and subscriptions are fetched on demand from the Group Admin
 * via GroupAdminClient.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AccessControlService {

    private final PolicyExpressionRepository policyExpressionRepository;
    private final GroupAdminClient groupAdminClient;

    @Value("${dataholder.policies.default-access-level:1}")
    private int defaultAccessLevel;

    @Value("${dataholder.policies.require-agreement:true}")
    private boolean requireAgreement;

    @Value("${dataholder.policies.manual-verification.enabled:false}")
    private boolean manualVerificationEnabled;

    @Value("${dataholder.policies.manual-verification.required-for-level:3}")
    private int manualVerificationLevel;

    @Value("${dataholder.jwt.secret:ThisIsASecretKeyForJWTTokenGenerationThatMustBeAtLeast256BitsLong!!}")
    private String jwtSecret;

    /**
     * Check if the given token is from a Data Holder admin (local JWT)
     */
    public boolean isDataHolderAdmin(String authHeader) {
        if (authHeader == null || !authHeader.startsWith("Bearer ")) return false;
        try {
            SecretKey signingKey = Keys.hmacShaKeyFor(jwtSecret.getBytes(StandardCharsets.UTF_8));
            Claims claims = Jwts.parserBuilder()
                    .setSigningKey(signingKey).build()
                    .parseClaimsJws(authHeader.substring(7)).getBody();
            return "dataholder".equals(claims.getIssuer())
                    && "access".equals(claims.get("userType"))
                    && "ADMIN".equals(claims.get("role"));
        } catch (Exception e) {
            return false;
        }
    }

    /** Access level for a query that names no request type. */
    public int unscopedAccessLevel() {
        return requireAgreement ? 0 : defaultAccessLevel;
    }

    /**
     * Determine access level by requestor group code and request type code.
     * Fetches from Group Admin by group code.
     */
    public int determineAccessLevelByCodes(String requestorGroupCode, String requestTypeCode,
                                           TokenInfo tokenInfo, boolean isDataHolderAdmin) {
        boolean reqAgreement = requireAgreement;

        if (requestorGroupCode == null || requestorGroupCode.isBlank()
                || requestTypeCode == null || requestTypeCode.isBlank()) {
            if (reqAgreement) return 0;
            return defaultAccessLevel;
        }

        if (!groupAdminClient.isConfigured()) {
            log.warn("Group Admin not configured, returning 0 for code-based lookup");
            return 0;
        }

        List<Map<String, Object>> subs = groupAdminClient.getSubscriptionsByGroupCode(requestorGroupCode);

        if (subs.isEmpty()) {
            log.warn("No active subscription found for requestorGroupCode={}", requestorGroupCode);
            return reqAgreement ? 0 : defaultAccessLevel;
        }

        // Find the subscription matching the requested type code
        Map<String, Object> sub = null;
        for (Map<String, Object> s : subs) {
            if (typeCodeMatches(s.get("requestTypeCode"), requestTypeCode)) {
                sub = s;
                break;
            }
        }

        // If no exact match on type code, try matching by the access level on the template's request types
        if (sub == null) {
            for (Map<String, Object> s : subs) {
                Object rtList = s.get("requestTypes");
                if (rtList instanceof List) {
                    for (Object rt : (List<?>) rtList) {
                        if (rt instanceof Map && typeCodeMatches(((Map<?, ?>) rt).get("typeCode"), requestTypeCode)) {
                            Object al = ((Map<?, ?>) rt).get("accessLevel");
                            int level = al instanceof Number ? ((Number) al).intValue() : 0;
                            log.info("Resolved access level {} for requestorGroupCode={} requestTypeCode={} from request type definition",
                                    level, requestorGroupCode, requestTypeCode);
                            return level;
                        }
                    }
                }
            }
        }

        if (sub == null) {
            log.warn("No subscription matching requestTypeCode={} for requestorGroupCode={}, denying",
                    requestTypeCode, requestorGroupCode);
            return 0;
        }

        int level = sub.get("effectiveAccessLevel") != null
                ? ((Number) sub.get("effectiveAccessLevel")).intValue() : 0;

        log.info("Resolved access level {} for requestorGroupCode={} requestTypeCode={} from Group Admin",
                level, requestorGroupCode, requestTypeCode);
        return level;
    }

    /**
     * Get all active subscriptions from Group Admin (as raw maps).
     *
     * The Group Admin includes each requestor's introspection client secret on this
     * data-holder-authenticated feed, so the raw maps are for server-side use only
     * (token introspection, access decisions). Anything returned to a browser or to an
     * unauthenticated caller must go through {@link #withoutCredentials} or
     * {@link #getPublicSubscriptionSummaries()}.
     */
    public List<Map<String, Object>> getAvailableSubscriptionMaps() {
        if (!groupAdminClient.isConfigured()) return List.of();
        return groupAdminClient.getActiveSubscriptions();
    }

    /** Keys that hold credentials for the requestor's identity provider. Never leave the server. */
    private static final Set<String> CREDENTIAL_KEYS = Set.of("introspectionClientSecret");

    /**
     * Keys safe to publish to anyone: what is offered and at which level, with no requestor
     * identity, contact details, callback URLs or credentials.
     */
    private static final Set<String> PUBLIC_SUMMARY_KEYS = Set.of(
            "requestId", "status", "dataholderId", "dataholderName",
            "dataHolderGroupId", "dataHolderGroupName",
            "templateId", "agreementCode", "templateName", "templateDescription", "requestTypes",
            "effectiveAccessLevel", "highestAccessLevel", "supportsConfidential", "supportsExigent",
            "effectiveFrom", "effectiveTo");

    /** Copies of the given maps with credential keys removed; for the admin UI. */
    public static List<Map<String, Object>> withoutCredentials(List<Map<String, Object>> subscriptions) {
        List<Map<String, Object>> out = new ArrayList<>(subscriptions.size());
        for (Map<String, Object> sub : subscriptions) {
            Map<String, Object> copy = new LinkedHashMap<>(sub);
            copy.keySet().removeAll(CREDENTIAL_KEYS);
            out.add(copy);
        }
        return out;
    }

    /** Active subscriptions reduced to their public summary; for unauthenticated endpoints. */
    public List<Map<String, Object>> getPublicSubscriptionSummaries() {
        List<Map<String, Object>> out = new ArrayList<>();
        for (Map<String, Object> sub : getAvailableSubscriptionMaps()) {
            Map<String, Object> summary = new LinkedHashMap<>();
            for (String key : PUBLIC_SUMMARY_KEYS) {
                if (sub.containsKey(key)) summary.put(key, sub.get(key));
            }
            out.add(summary);
        }
        return out;
    }

    /**
     * Get this data holder's group memberships from all connected Group Admins.
     */
    public List<Map<String, Object>> getGroupMemberships() {
        if (!groupAdminClient.isConfigured()) return List.of();
        return groupAdminClient.getGroupMemberships();
    }

    /**
     * Get published templates from all connected Group Admins.
     */
    public List<Map<String, Object>> getTemplates() {
        if (!groupAdminClient.isConfigured()) return List.of();
        return groupAdminClient.getTemplates();
    }

    /**
     * Check if a requestor group has an active subscription
     */
    public Map<String, Object> checkSubscriptionForGroup(String requestorGroupId) {
        if (!groupAdminClient.isConfigured()) return Map.of("active", false);
        return groupAdminClient.checkSubscription(requestorGroupId);
    }

    public boolean requiresManualVerification(int accessLevel) {
        if (!manualVerificationEnabled) return false;
        return accessLevel >= manualVerificationLevel;
    }

    public Optional<PolicyExpression> getActivePolicyExpression() {
        return policyExpressionRepository.findFirstByIsActiveTrueAndIsDefaultTrue()
                .or(() -> policyExpressionRepository.findFirstByIsActiveTrue());
    }

    public List<PolicyExpression> getActivePolicyExpressions() {
        return policyExpressionRepository.findByIsActiveTrue();
    }

    // ==================== Backward Compatibility ====================

    @Deprecated
    public Optional<PolicyExpression> getActivePolicy() { return getActivePolicyExpression(); }

    /**
     * Stub: confidential support check. With Group Admin, this info comes
     * from the subscription response. Returns false as a safe default.
     */
    public boolean doesRequestTypeSupportConfidential(String requestorGroupCode, String requestTypeCode) {
        // Cannot resolve locally — Group Admin handles this
        return false;
    }

    /**
     * Stub: resolve request type. Returns null since local entities are gone.
     * RDAP parameter filtering falls back to access-level-based defaults.
     */
    @SuppressWarnings("unchecked")
    public AgreementRequestType resolveRequestType(String requestorGroupCode, String requestTypeCode) {
        if (requestorGroupCode == null || requestTypeCode == null || requestTypeCode.isBlank()
                || !groupAdminClient.isConfigured()) {
            return null;
        }

        List<Map<String, Object>> subs = groupAdminClient.getSubscriptionsByGroupCode(requestorGroupCode);
        if (subs.isEmpty()) {
            log.debug("resolveRequestType: no subscriptions found for groupCode={}", requestorGroupCode);
            return null;
        }

        for (Map<String, Object> sub : subs) {
            Object rtList = sub.get("requestTypes");
            if (!(rtList instanceof List)) continue;

            for (Object rtObj : (List<?>) rtList) {
                if (!(rtObj instanceof Map)) continue;
                Map<String, Object> rt = (Map<String, Object>) rtObj;

                if (!typeCodeMatches(rt.get("typeCode"), requestTypeCode)) continue;

                // Found the matching request type — build the entity with RDAP parameters
                log.debug("resolveRequestType: matched groupCode={} typeCode={}", requestorGroupCode, requestTypeCode);

                AgreementRequestType resolved = new AgreementRequestType();
                resolved.setName(rt.get("name") != null ? rt.get("name").toString() : "Type " + requestTypeCode);
                resolved.setTypeCode(requestTypeCode);
                resolved.setIsActive(true);
                resolved.setRequiresManualApproval(getBool(rt, "requiresManualApproval", true));
                resolved.setSupportsExigent(getBool(rt, "supportsExigent", false));

                // Extract rdapParameters map and build AgreementRdapParameters entity
                Object paramsObj = rt.get("rdapParameters");
                if (paramsObj instanceof Map) {
                    Map<String, Object> paramsMap = (Map<String, Object>) paramsObj;
                    AgreementRdapParameters rdapParams = buildRdapParametersFromMap(paramsMap);
                    resolved.setRdapParameters(rdapParams);
                    log.debug("resolveRequestType: built rdapParameters with {} entries", paramsMap.size());
                }

                return resolved;
            }
        }

        log.debug("resolveRequestType: no matching request type found for groupCode={} typeCode={}",
                requestorGroupCode, requestTypeCode);
        return null;
    }

    /**
     * The admin-authored file-format criteria for one {@code file} custom parameter of a request
     * type. Resolved live from the DH Group Admin (the authoring side), so criteria edits take
     * effect immediately with no local copy to sync.
     *
     * @return the criteria map, or null when the parameter has no criteria configured
     */
    @SuppressWarnings("unchecked")
    public Map<String, Object> resolveFileCriteria(String requestorGroupCode, String requestTypeCode,
                                                   String paramName) {
        if (requestorGroupCode == null || requestTypeCode == null || requestTypeCode.isBlank()
                || paramName == null || !groupAdminClient.isConfigured()) {
            return null;
        }

        for (Map<String, Object> sub : groupAdminClient.getSubscriptionsByGroupCode(requestorGroupCode)) {
            Object rtList = sub.get("requestTypes");
            if (!(rtList instanceof List)) continue;

            for (Object rtObj : (List<?>) rtList) {
                if (!(rtObj instanceof Map)) continue;
                Map<String, Object> rt = (Map<String, Object>) rtObj;

                if (!typeCodeMatches(rt.get("typeCode"), requestTypeCode)) continue;

                Object params = rt.get("customParameters");
                if (!(params instanceof List)) return null;
                for (Object pObj : (List<?>) params) {
                    if (!(pObj instanceof Map)) continue;
                    Map<String, Object> p = (Map<String, Object>) pObj;
                    if (!paramName.equals(String.valueOf(p.get("name")))) continue;
                    Object criteria = p.get("fileCriteria");
                    return criteria instanceof Map ? (Map<String, Object>) criteria : null;
                }
                return null; // matched the request type but not the parameter
            }
        }
        return null;
    }

    /**
     * Build an AgreementRdapParameters entity from the Map<String, Object> returned
     * by the DHG admin's subscription API. Each key is a field name (e.g., "adminAddress")
     * and the value is a Boolean.
     */
    private AgreementRdapParameters buildRdapParametersFromMap(Map<String, Object> p) {
        AgreementRdapParameters params = AgreementRdapParameters.createDefault();

        params.setDomainHandle(getBool(p, "domainHandle", true));
        params.setDomainName(getBool(p, "domainName", true));
        params.setDomainStatus(getBool(p, "domainStatus", true));
        params.setDomainPort43(getBool(p, "domainPort43", true));
        params.setDomainPublicIds(getBool(p, "domainPublicIds", true));

        params.setNameservers(getBool(p, "nameservers", true));
        params.setNameserverHandle(getBool(p, "nameserverHandle", true));
        params.setNameserverName(getBool(p, "nameserverName", true));
        params.setNameserverIpAddresses(getBool(p, "nameserverIpAddresses", true));
        params.setNameserverStatus(getBool(p, "nameserverStatus", true));

        params.setEvents(getBool(p, "events", true));
        params.setEventRegistration(getBool(p, "eventRegistration", true));
        params.setEventExpiration(getBool(p, "eventExpiration", true));
        params.setEventLastChanged(getBool(p, "eventLastChanged", true));
        params.setEventLastUpdateOfRdapDb(getBool(p, "eventLastUpdateOfRdapDb", true));
        params.setEventTransfer(getBool(p, "eventTransfer", true));

        params.setRegistrantEntity(getBool(p, "registrantEntity", true));
        params.setRegistrantHandle(getBool(p, "registrantHandle", true));
        params.setRegistrantName(getBool(p, "registrantName", true));
        params.setRegistrantOrganization(getBool(p, "registrantOrganization", true));
        params.setRegistrantEmail(getBool(p, "registrantEmail", true));
        params.setRegistrantPhone(getBool(p, "registrantPhone", true));
        params.setRegistrantFax(getBool(p, "registrantFax", true));
        params.setRegistrantAddress(getBool(p, "registrantAddress", true));
        params.setRegistrantStreet(getBool(p, "registrantStreet", true));
        params.setRegistrantCity(getBool(p, "registrantCity", true));
        params.setRegistrantStateProvince(getBool(p, "registrantStateProvince", true));
        params.setRegistrantPostalCode(getBool(p, "registrantPostalCode", true));
        params.setRegistrantCountry(getBool(p, "registrantCountry", true));

        params.setAdminEntity(getBool(p, "adminEntity", true));
        params.setAdminHandle(getBool(p, "adminHandle", true));
        params.setAdminName(getBool(p, "adminName", true));
        params.setAdminOrganization(getBool(p, "adminOrganization", true));
        params.setAdminEmail(getBool(p, "adminEmail", true));
        params.setAdminPhone(getBool(p, "adminPhone", true));
        params.setAdminFax(getBool(p, "adminFax", true));
        params.setAdminAddress(getBool(p, "adminAddress", true));
        params.setAdminStreet(getBool(p, "adminStreet", true));
        params.setAdminCity(getBool(p, "adminCity", true));
        params.setAdminStateProvince(getBool(p, "adminStateProvince", true));
        params.setAdminPostalCode(getBool(p, "adminPostalCode", true));
        params.setAdminCountry(getBool(p, "adminCountry", true));

        params.setTechEntity(getBool(p, "techEntity", true));
        params.setTechHandle(getBool(p, "techHandle", true));
        params.setTechName(getBool(p, "techName", true));
        params.setTechOrganization(getBool(p, "techOrganization", true));
        params.setTechEmail(getBool(p, "techEmail", true));
        params.setTechPhone(getBool(p, "techPhone", true));
        params.setTechFax(getBool(p, "techFax", true));
        params.setTechAddress(getBool(p, "techAddress", true));
        params.setTechStreet(getBool(p, "techStreet", true));
        params.setTechCity(getBool(p, "techCity", true));
        params.setTechStateProvince(getBool(p, "techStateProvince", true));
        params.setTechPostalCode(getBool(p, "techPostalCode", true));
        params.setTechCountry(getBool(p, "techCountry", true));

        params.setBillingEntity(getBool(p, "billingEntity", true));
        params.setBillingHandle(getBool(p, "billingHandle", true));
        params.setBillingName(getBool(p, "billingName", true));
        params.setBillingOrganization(getBool(p, "billingOrganization", true));
        params.setBillingEmail(getBool(p, "billingEmail", true));
        params.setBillingPhone(getBool(p, "billingPhone", true));
        params.setBillingFax(getBool(p, "billingFax", true));
        params.setBillingAddress(getBool(p, "billingAddress", true));
        params.setBillingStreet(getBool(p, "billingStreet", true));
        params.setBillingCity(getBool(p, "billingCity", true));
        params.setBillingStateProvince(getBool(p, "billingStateProvince", true));
        params.setBillingPostalCode(getBool(p, "billingPostalCode", true));
        params.setBillingCountry(getBool(p, "billingCountry", true));

        params.setRegistrarEntity(getBool(p, "registrarEntity", true));
        params.setRegistrarHandle(getBool(p, "registrarHandle", true));
        params.setRegistrarName(getBool(p, "registrarName", true));
        params.setRegistrarEmail(getBool(p, "registrarEmail", true));
        params.setRegistrarPhone(getBool(p, "registrarPhone", true));
        params.setRegistrarUrl(getBool(p, "registrarUrl", true));
        params.setRegistrarAbuseContact(getBool(p, "registrarAbuseContact", true));

        params.setDnssecData(getBool(p, "dnssecData", true));
        params.setDnssecDelegationSigned(getBool(p, "dnssecDelegationSigned", true));
        params.setDnssecDsData(getBool(p, "dnssecDsData", true));
        params.setDnssecKeyData(getBool(p, "dnssecKeyData", true));

        params.setNetworkHandle(getBool(p, "networkHandle", true));
        params.setNetworkName(getBool(p, "networkName", true));
        params.setNetworkType(getBool(p, "networkType", true));
        params.setNetworkStartAddress(getBool(p, "networkStartAddress", true));
        params.setNetworkEndAddress(getBool(p, "networkEndAddress", true));
        params.setNetworkIpVersion(getBool(p, "networkIpVersion", true));
        params.setNetworkParentHandle(getBool(p, "networkParentHandle", true));
        params.setNetworkCidr(getBool(p, "networkCidr", true));
        params.setNetworkCountry(getBool(p, "networkCountry", true));

        params.setAutnumHandle(getBool(p, "autnumHandle", true));
        params.setAutnumStart(getBool(p, "autnumStart", true));
        params.setAutnumEnd(getBool(p, "autnumEnd", true));
        params.setAutnumName(getBool(p, "autnumName", true));
        params.setAutnumType(getBool(p, "autnumType", true));
        params.setAutnumCountry(getBool(p, "autnumCountry", true));

        params.setLinks(getBool(p, "links", true));
        params.setNotices(getBool(p, "notices", true));
        params.setRemarks(getBool(p, "remarks", true));

        return params;
    }

    /** Whether a typeCode from a Group Admin payload is the one being asked for. */
    private boolean typeCodeMatches(Object payloadTypeCode, String wanted) {
        return payloadTypeCode != null && wanted != null
                && wanted.equals(payloadTypeCode.toString());
    }

    private Boolean getBool(Map<String, Object> map, String key, boolean defaultVal) {
        Object v = map.get(key);
        if (v instanceof Boolean) return (Boolean) v;
        if (v instanceof String) return Boolean.parseBoolean((String) v);
        return defaultVal;
    }

    // ==================== Query Value Regex Validation ====================

    /**
     * Validate the RDAP query value against any regex pattern configured on the
     * matching request type from the Group Admin subscription.
     *
     * @return null if validation passes or no regex is configured; an error message string if validation fails
     */
    @SuppressWarnings("unchecked")
    public String validateQueryValueRegex(String requestorGroupCode, String requestTypeCode, String queryValue) {
        if (requestorGroupCode == null || requestTypeCode == null || requestTypeCode.isBlank()
                || !groupAdminClient.isConfigured()) {
            return null; // no validation possible
        }

        List<Map<String, Object>> subs = groupAdminClient.getSubscriptionsByGroupCode(requestorGroupCode);
        if (subs.isEmpty()) {
            log.debug("Regex validation: no subscriptions found for groupCode={}", requestorGroupCode);
            return null;
        }

        log.info("Regex validation: checking queryValue='{}' for groupCode={} typeCode={} across {} subscription(s)",
                queryValue, requestorGroupCode, requestTypeCode, subs.size());

        // Search through subscriptions to find the matching request type definition
        for (Map<String, Object> sub : subs) {
            Object rtList = sub.get("requestTypes");
            if (!(rtList instanceof List)) {
                log.debug("Regex validation: subscription has no requestTypes list");
                continue;
            }

            for (Object rtObj : (List<?>) rtList) {
                if (!(rtObj instanceof Map)) continue;
                Map<String, Object> rt = (Map<String, Object>) rtObj;

                if (!typeCodeMatches(rt.get("typeCode"), requestTypeCode)) continue;

                // Found the matching request type — check for regex
                String regex = rt.get("queryValueRegex") != null ? rt.get("queryValueRegex").toString().trim() : null;
                log.info("Regex validation: matched typeCode={}, queryValueRegex={}", requestTypeCode, regex);

                if (regex == null || regex.isEmpty()) return null; // no regex configured

                try {
                    boolean matches = queryValue != null && queryValue.matches(regex);
                    log.info("Regex validation: '{}' matches '{}' = {}", queryValue, regex, matches);
                    if (matches) {
                        return null; // passes validation
                    }
                } catch (java.util.regex.PatternSyntaxException e) {
                    log.warn("Invalid regex pattern '{}' on request type code {}: {}", regex, requestTypeCode, e.getMessage());
                    return null; // invalid regex = skip validation
                }

                // Failed — return the configured error message or a default
                String errorMsg = rt.get("queryValueRegexError") != null ? rt.get("queryValueRegexError").toString().trim() : null;
                if (errorMsg == null || errorMsg.isEmpty()) {
                    errorMsg = "Query value '" + queryValue + "' does not meet the requirements for this request type";
                }
                log.info("Regex validation FAILED: '{}' did not match '{}' — rejecting with: {}",
                        queryValue, regex, errorMsg);
                return errorMsg;
            }
        }

        log.debug("Regex validation: no matching request type found for typeCode={}", requestTypeCode);
        return null; // no matching request type found = no validation
    }

}