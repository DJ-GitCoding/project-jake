/*
 * SPDX-FileCopyrightText: 2025-2026 Edgemoor Research Institute and Derek Jenkins
 * SPDX-License-Identifier: AGPL-3.0-only
 *
 * Author: Derek Jenkins <derek@pure-code.net>
 * Additional terms under AGPL-3.0 Section 7 apply. See NOTICE at the repository root.
 */

package com.jaddar.dataholder.service;

import com.jaddar.dataholder.entity.PolicyExpression;
import com.jaddar.dataholder.entity.PolicyRedactionRule;
import com.jaddar.dataholder.entity.PolicyRedactionRule.RdapObjectType;
import com.jaddar.dataholder.entity.RdapEntity;
import com.jaddar.dataholder.entity.RedactionRule.RedactionBehavior;
import com.jaddar.dataholder.repository.PolicyExpressionRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.core.type.TypeReference;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.regex.Pattern;

/**
 * Service for applying redaction to RDAP responses.
 *
 * Each data element's sensitivity level is resolved from the policy's
 * per-field redaction rules (PolicyRedactionRule.sensitivityLevel).
 * Fields without an explicit rule fall back to the policy's own sensitivityLevel.
 *
 * The resolved per-field sensitivity level is then combined with the
 * request type's access level and the field's emptiness to look up
 * the behavior in the global redaction matrix via RedactionService.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class RdapRedactionService {

    private final PolicyExpressionRepository policyExpressionRepository;
    private final RedactionService redactionService;
    private final ObjectMapper objectMapper;

    private static final Set<String> META_FIELDS = Set.of(
            "rdapConformance", "objectClassName", "notices", "remarks",
            "links", "timestamp", "accessLevel", "agreementNames",
            "errorCode", "title", "description", "pollUrl", "requestId",
            "status_code", "lang", "policyLevels"
    );

    /**
     * Response key carrying the per-element policy levels. Present on the
     * top-level object and on each child entity, alongside the data it describes.
     */
    private static final String POLICY_LEVELS_KEY = "policyLevels";

    /**
     * Apply redaction to all data fields in the RDAP response.
     */
    public Map<String, Object> applyRedactionRules(Map<String, Object> rdapResponse, RdapEntity entity, int accessLevel) {
        PolicyExpression policy = resolvePolicy(rdapResponse, entity);
        int defaultSensitivity = 0;

        // Build per-field rule lookup from the policy's redaction rules
        RdapObjectType objectType = mapEntityType(entity.getObjectType());
        Map<String, List<PolicyRedactionRule>> fieldRules = buildFieldRuleMap(policy, objectType);
        Map<String, Integer> fieldSensitivity = maxSensitivityByField(fieldRules);

        if (policy != null) {
            log.info("POLICY RESOLVED: '{}' (id={}) for entity {} — defaultSensitivity={}, fieldRules={}",
                    policy.getName(), policy.getId(), entity.getHandle(), defaultSensitivity, fieldSensitivity.size());
        } else {
            log.info("POLICY RESOLVED: none for entity {}, defaultSensitivity=0", entity.getHandle());
        }

        Map<String, Object> result = new LinkedHashMap<>(rdapResponse);
        List<String> redactedFields = new ArrayList<>();
        Map<String, Object> topFieldLevels = new LinkedHashMap<>();

        // Redact top-level fields
        for (String key : new ArrayList<>(result.keySet())) {
            if (META_FIELDS.contains(key) || "entities".equals(key)) continue;
            Object value = result.get(key);
            int sens = fieldSensitivity.getOrDefault(key, defaultSensitivity);
            topFieldLevels.put(key, describeLevels(fieldRules.get(key), defaultSensitivity));
            applyRedaction(result, key, value, accessLevel, sens, redactedFields);
        }

        // Redact child entities
        redactChildEntities(result, accessLevel, defaultSensitivity, policy, redactedFields);

        if (!redactedFields.isEmpty()) {
            addRedactionNotice(result, redactedFields, policy != null ? policy.getName() : null);
        }

        // Add noteToRequestor as a notice if the policy has one
        if (policy != null && policy.getNoteToRequestor() != null && !policy.getNoteToRequestor().isBlank()) {
            addNoteToRequestorNotice(result, policy.getNoteToRequestor());
        }

        // Always include the resolved policy name in the response
        if (policy != null) {
            result.put("policyName", policy.getName());
            result.put(POLICY_LEVELS_KEY,
                    buildPolicyLevelsBlock(policy, accessLevel, defaultSensitivity, topFieldLevels));
        }

        return result;
    }

    /**
     * Assemble the top-level policy levels block. Child entities carry their own
     * block so that per-role levels stay attached to the contact they describe.
     */
    private Map<String, Object> buildPolicyLevelsBlock(PolicyExpression policy, int accessLevel,
                                                       int defaultSensitivity, Map<String, Object> fieldLevels) {
        Map<String, Object> block = new LinkedHashMap<>();
        block.put("policyName", policy.getName());
        block.put("accessLevel", accessLevel);
        block.put("defaultSensitivityLevel", defaultSensitivity);
        block.put("fields", fieldLevels);
        return block;
    }

    /**
     * Build a map of lookupKey → contributing rules from the policy's enabled
     * rules that apply to the given object type (or ALL).
     *
     * Imported policy rules use structured paths like "registrant.fn" or
     * "registrant.adr.street1".  These must be translated into the lookup
     * keys that the redaction code actually uses when iterating the RDAP
     * response (top-level map keys such as "ldhName" and vCard keys such
     * as "vcardArray.fn").
     *
     * Several rules can land on the same key — the Int'l and Local variants of
     * a name both translate to "vcardArray.fn", and every address component
     * translates to "vcardArray.adr" — so the rules are kept as a list rather
     * than collapsed on the way in. Redaction uses the highest sensitivity
     * among them (see {@link #maxSensitivity}); the full list is what lets the
     * response report every level that was associated with the element.
     */
    private Map<String, List<PolicyRedactionRule>> buildFieldRuleMap(PolicyExpression policy,
                                                                     RdapObjectType objectType) {
        Map<String, List<PolicyRedactionRule>> map = new LinkedHashMap<>();
        if (policy == null || policy.getRedactionRules() == null) return map;

        policy.getRedactionRules().stream()
                .filter(r -> Boolean.TRUE.equals(r.getIsEnabled()))
                .filter(r -> r.getObjectType() == objectType || r.getObjectType() == RdapObjectType.ALL)
                .forEach(r -> {
                    // Translate dotted policy paths to the keys the redaction loop uses
                    for (String key : translatePolicyPath(r.getFieldPath())) {
                        map.computeIfAbsent(key, k -> new ArrayList<>()).add(r);
                    }
                });

        return map;
    }

    /**
     * Build a role-aware rule map for ENTITY redaction rules.
     * Returns  role → { lookupKey → contributing rules }.
     * A rule with no recognised role prefix (or objectType ALL) is filed under "*".
     */
    private Map<String, Map<String, List<PolicyRedactionRule>>> buildRoleRuleMap(PolicyExpression policy) {
        Map<String, Map<String, List<PolicyRedactionRule>>> roleMap = new LinkedHashMap<>();
        if (policy == null || policy.getRedactionRules() == null) return roleMap;

        policy.getRedactionRules().stream()
                .filter(r -> Boolean.TRUE.equals(r.getIsEnabled()))
                .filter(r -> r.getObjectType() == RdapObjectType.ENTITY || r.getObjectType() == RdapObjectType.ALL)
                .forEach(r -> {
                    Map<String, List<PolicyRedactionRule>> roleBucket =
                            roleMap.computeIfAbsent(extractRole(r.getFieldPath()), k -> new LinkedHashMap<>());
                    for (String key : translatePolicyPath(r.getFieldPath())) {
                        roleBucket.computeIfAbsent(key, k -> new ArrayList<>()).add(r);
                    }
                });

        return roleMap;
    }

    /** Collapse a rule map to the sensitivity level that actually governs each key. */
    private Map<String, Integer> maxSensitivityByField(Map<String, List<PolicyRedactionRule>> ruleMap) {
        Map<String, Integer> map = new LinkedHashMap<>();
        ruleMap.forEach((key, rules) -> map.put(key, maxSensitivity(rules, 0)));
        return map;
    }

    /**
     * The sensitivity level that governs a data element: the highest among all
     * rules that translate to it, so the most restrictive rule wins. Where a
     * policy sets different levels for the Int'l and Local forms of a field,
     * this is what makes the stricter of the two apply to both.
     */
    private int maxSensitivity(List<PolicyRedactionRule> rules, int fallback) {
        if (rules == null || rules.isEmpty()) return fallback;
        int max = 0;
        for (PolicyRedactionRule rule : rules) {
            max = Math.max(max, rule.getSensitivityLevel() != null ? rule.getSensitivityLevel() : 0);
        }
        return max;
    }

    /**
     * Describe the policy levels associated with one data element, for the
     * requestor to see alongside the value.
     *
     * Rules are grouped by their distinct (sensitivity, validation) pair, so an
     * element whose Int'l and Local forms carry different levels reports both
     * pairs rather than only the one that won. The pair that actually governed
     * redaction — the highest sensitivity — is flagged as applied.
     */
    private Map<String, Object> describeLevels(List<PolicyRedactionRule> rules, int defaultSensitivity) {
        Map<String, Object> detail = new LinkedHashMap<>();

        if (rules == null || rules.isEmpty()) {
            Map<String, Object> fallback = new LinkedHashMap<>();
            fallback.put("sensitivityLevel", defaultSensitivity);
            fallback.put("validationLevel", null);
            fallback.put("applied", true);
            fallback.put("policyDefault", true);
            fallback.put("sources", new ArrayList<>());
            detail.put("effectiveSensitivityLevel", defaultSensitivity);
            detail.put("levels", new ArrayList<>(List.of(fallback)));
            return detail;
        }

        Map<String, Map<String, Object>> byPair = new LinkedHashMap<>();
        int effective = 0;
        for (PolicyRedactionRule rule : rules) {
            int sens = rule.getSensitivityLevel() != null ? rule.getSensitivityLevel() : 0;
            Integer validation = rule.getValidationLevel();
            effective = Math.max(effective, sens);

            Map<String, Object> pair = byPair.computeIfAbsent(sens + "|" + validation, k -> {
                Map<String, Object> created = new LinkedHashMap<>();
                created.put("sensitivityLevel", sens);
                created.put("validationLevel", validation);
                created.put("sources", new ArrayList<Map<String, Object>>());
                return created;
            });
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> sources = (List<Map<String, Object>>) pair.get("sources");
            sources.add(describeSource(rule));
        }

        List<Map<String, Object>> levels = new ArrayList<>(byPair.values());
        for (Map<String, Object> pair : levels) {
            pair.put("applied", (Integer) pair.get("sensitivityLevel") == effective);
        }
        levels.sort(Comparator.comparingInt(level -> (Integer) level.get("sensitivityLevel")));

        detail.put("effectiveSensitivityLevel", effective);
        detail.put("levels", levels);
        return detail;
    }

    /** Identify the policy field a level pair came from, so the UI can name it. */
    private Map<String, Object> describeSource(PolicyRedactionRule rule) {
        Map<String, Object> source = new LinkedHashMap<>();
        source.put("fieldPath", rule.getFieldPath());
        source.put("label", rule.getFieldDisplayName());
        source.put("variant", localeVariant(rule));
        return source;
    }

    /**
     * Which locale variant a rule describes, where a policy tracks the Int'l and
     * Local forms of a field separately. Imported policies suffix the
     * local-script variant with ".local" and leave the international form
     * unsuffixed, carrying the distinction in the display name instead.
     * Returns null for fields that have no locale variants at all.
     */
    private String localeVariant(PolicyRedactionRule rule) {
        String path = rule.getFieldPath();
        if (path != null) {
            if (path.endsWith(".local")) return "local";
            if (path.endsWith(".intl")) return "intl";
        }
        String label = rule.getFieldDisplayName();
        if (label != null) {
            String lower = label.toLowerCase();
            if (lower.contains("(local)")) return "local";
            if (lower.contains("(int'l") || lower.contains("(intl")) return "intl";
        }
        return null;
    }

    /**
     * Extract the RDAP contact role from a policy field path.
     * "registrant.fn"   → "registrant"
     * "administrative.adr.city" → "administrative"
     * "handle"           → "*"   (wildcard — applies to all roles)
     */
    private String extractRole(String fieldPath) {
        if (fieldPath == null || !fieldPath.contains(".")) return "*";
        String prefix = fieldPath.substring(0, fieldPath.indexOf('.'));
        return switch (prefix) {
            case "registrant", "administrative", "technical", "billing", "abuse", "registrar" -> prefix;
            case "admin" -> "administrative";
            case "tech" -> "technical";
            default -> "*";
        };
    }

    /**
     * Translate a policy field path produced by PolicyImportService into the
     * keys the redaction loops use when walking the RDAP response maps.
     *
     * Examples:
     *   "registrant.fn"           → ["vcardArray.fn"]
     *   "registrant.email"        → ["vcardArray.email"]
     *   "registrant.tel"          → ["vcardArray.tel"]
     *   "registrant.fax"          → ["vcardArray.tel"]   (fax is a tel with type=fax)
     *   "registrant.adr.street1"  → ["vcardArray.adr"]
     *   "registrant.adr.city"     → ["vcardArray.adr"]
     *   "registrant.handle"       → ["handle"]
     *   "registrant.org"          → ["vcardArray.org"]
     *   "ldhName"                 → ["ldhName"]
     *   "events.registration"     → ["events"]
     *   "status"                  → ["status"]
     */
    private List<String> translatePolicyPath(String policyPath) {
        if (policyPath == null) return List.of();

        // Strip the contact-role prefix (registrant., administrative., admin., tech., etc.)
        String fieldPart = policyPath;
        if (policyPath.contains(".")) {
            String prefix = policyPath.substring(0, policyPath.indexOf('.'));
            if (Set.of("registrant", "administrative", "technical", "billing",
                        "abuse", "registrar", "entity", "admin", "tech").contains(prefix)) {
                fieldPart = policyPath.substring(policyPath.indexOf('.') + 1);
            }
        }

        // Strip .local and .intl suffixes (locale variants) — both map to the same RDAP property
        if (fieldPart.endsWith(".local")) {
            fieldPart = fieldPart.substring(0, fieldPart.length() - ".local".length());
        }
        if (fieldPart.endsWith(".intl")) {
            fieldPart = fieldPart.substring(0, fieldPart.length() - ".intl".length());
        }

        // Normalize old-style camelCase field names to the current format
        // orgId → org_id, personalId → personal_id, emailOrPhone → contact_pref, uniqueId → handle
        fieldPart = switch (fieldPart) {
            case "orgId", "org_id" -> "org_id";
            case "personalId", "personal_id" -> "personal_id";
            case "emailOrPhone", "contact_pref" -> "contact_pref";
            case "uniqueId" -> "handle";
            default -> fieldPart;
        };

        // vCard properties
        if (fieldPart.equals("fn") || fieldPart.equals("org") || fieldPart.equals("email")) {
            return List.of("vcardArray." + fieldPart);
        }
        if (fieldPart.equals("tel") || fieldPart.equals("fax")) {
            return List.of("vcardArray.tel");
        }
        if (fieldPart.equals("contact_pref")) {
            return List.of("vcardArray.email", "vcardArray.tel");
        }

        // Address components all map to the composite vCard "adr" property
        // Handles: adr.street1, adr.city, adr.street.1, adr.street.2.local, etc.
        if (fieldPart.startsWith("adr.")) {
            return List.of("vcardArray.adr");
        }

        // Handle / unique ID / personal ID / org ID
        if (fieldPart.equals("handle") || fieldPart.equals("personal_id") || fieldPart.equals("org_id")) {
            return List.of("handle");
        }

        // Top-level domain fields that need no translation
        if (Set.of("ldhName", "status", "secureDNS", "port43", "unicodeName").contains(fieldPart)) {
            return List.of(fieldPart);
        }

        // Events
        if (fieldPart.startsWith("events.") || fieldPart.equals("events")) {
            return List.of("events");
        }

        // Scope / forensic / dns fields
        if (fieldPart.startsWith("scope.") || fieldPart.startsWith("forensic.") || fieldPart.startsWith("dns.")) {
            return List.of(fieldPart);
        }

        // Registrar sub-fields  (registrar.fn → vcardArray.fn on registrar entity)
        if (fieldPart.startsWith("registrar.")) {
            String sub = fieldPart.substring("registrar.".length());
            return translatePolicyPath(sub);
        }

        // Fallback — use the path as-is
        return List.of(fieldPart);
    }

    @SuppressWarnings("unchecked")
    private void redactChildEntities(Map<String, Object> response, int accessLevel,
                                      int defaultSensitivity, PolicyExpression policy,
                                      List<String> redactedFields) {
        Object entities = response.get("entities");
        if (!(entities instanceof List)) return;

        // Build role-aware rule map:  role → { lookupKey → contributing rules }
        Map<String, Map<String, List<PolicyRedactionRule>>> roleRuleMap = buildRoleRuleMap(policy);
        // Wildcard rules (no role prefix) apply to every entity
        Map<String, List<PolicyRedactionRule>> wildcardRules = roleRuleMap.getOrDefault("*", Map.of());

        Set<String> entityMeta = Set.of("objectClassName", "roles", "links",
                "remarks", "notices", "events", "entities", "publicIds");

        List<Object> entityList = (List<Object>) entities;
        for (Object item : entityList) {
            if (!(item instanceof Map)) continue;
            Map<String, Object> child = (Map<String, Object>) item;

            // For entities with multiple roles (e.g. ["tech","admin","billing","registrant"]),
            // gather rules from ALL applicable roles. The highest sensitivity among
            // them governs each field, so the most restrictive policy applies.
            Map<String, List<PolicyRedactionRule>> effectiveRules = new LinkedHashMap<>();
            wildcardRules.forEach((key, rules) -> effectiveRules.put(key, new ArrayList<>(rules)));

            List<String> childRoles = resolveAllChildRoles(child);
            for (String role : childRoles) {
                roleRuleMap.getOrDefault(role, Map.of()).forEach((key, rules) ->
                        effectiveRules.computeIfAbsent(key, k -> new ArrayList<>()).addAll(rules));
            }

            // Levels for this contact, keyed the same way the redaction loops look fields up
            Map<String, Object> childLevels = new LinkedHashMap<>();

            // Redact flat fields
            for (String key : new ArrayList<>(child.keySet())) {
                if (entityMeta.contains(key) || "vcardArray".equals(key)
                        || POLICY_LEVELS_KEY.equals(key)) continue;
                Object value = child.get(key);
                int sens = maxSensitivity(effectiveRules.get(key), defaultSensitivity);
                childLevels.put(key, describeLevels(effectiveRules.get(key), defaultSensitivity));
                applyRedaction(child, key, value, accessLevel, sens, redactedFields);
            }

            // Redact vCard properties
            redactVcard(child, accessLevel, defaultSensitivity, effectiveRules, childLevels, redactedFields);

            if (policy != null && !childLevels.isEmpty()) {
                Map<String, Object> block = new LinkedHashMap<>();
                block.put("fields", childLevels);
                child.put(POLICY_LEVELS_KEY, block);
            }
        }
    }

    /**
     * Resolve ALL RDAP contact roles for a child entity.
     * Used when an entity has multiple roles (e.g. ["tech","admin","billing","registrant"])
     * so we can merge sensitivity rules from all applicable roles.
     */
    @SuppressWarnings("unchecked")
    private List<String> resolveAllChildRoles(Map<String, Object> childEntity) {
        Object rolesObj = childEntity.get("roles");
        if (!(rolesObj instanceof List)) return List.of("*");
        List<Object> roles = (List<Object>) rolesObj;
        List<String> resolved = new ArrayList<>();
        for (Object role : roles) {
            String r = String.valueOf(role).toLowerCase().trim();
            String mapped = switch (r) {
                case "registrant" -> "registrant";
                case "administrative", "admin" -> "administrative";
                case "technical", "tech" -> "technical";
                case "billing" -> "billing";
                case "abuse" -> "abuse";
                case "registrar" -> "registrar";
                default -> null;
            };
            if (mapped != null && !resolved.contains(mapped)) {
                resolved.add(mapped);
            }
        }
        return resolved.isEmpty() ? List.of("*") : resolved;
    }

    /**
     * Resolve the primary RDAP contact role for a child entity map.
     * Returns a normalised role string matching the policy import conventions
     * (registrant, administrative, technical, billing, abuse, registrar),
     * or "*" if no recognisable role is found.
     */
    @SuppressWarnings("unchecked")
    private String resolveChildRole(Map<String, Object> childEntity) {
        Object rolesObj = childEntity.get("roles");
        if (!(rolesObj instanceof List)) return "*";
        List<Object> roles = (List<Object>) rolesObj;
        // Try to find the most specific role — prefer registrant over others
        // since registrant rules tend to have the highest sensitivity
        String bestRole = "*";
        for (Object role : roles) {
            String r = String.valueOf(role).toLowerCase().trim();
            String mapped = switch (r) {
                case "registrant" -> "registrant";
                case "administrative", "admin" -> "administrative";
                case "technical", "tech" -> "technical";
                case "billing" -> "billing";
                case "abuse" -> "abuse";
                case "registrar" -> "registrar";
                default -> null;
            };
            if (mapped != null) {
                // Prefer registrant (highest sensitivity in most policies)
                if ("registrant".equals(mapped)) return mapped;
                if ("*".equals(bestRole)) bestRole = mapped;
            }
        }
        return bestRole;
    }

    @SuppressWarnings("unchecked")
    private void redactVcard(Map<String, Object> childEntity, int accessLevel,
                              int defaultSensitivity,
                              Map<String, List<PolicyRedactionRule>> entityFieldRules,
                              Map<String, Object> childLevels, List<String> redactedFields) {
        Object vcardRaw = childEntity.get("vcardArray");
        if (!(vcardRaw instanceof List)) return;
        List<Object> vcard = (List<Object>) vcardRaw;
        if (vcard.size() < 2 || !(vcard.get(1) instanceof List)) return;

        List<Object> properties = (List<Object>) vcard.get(1);

        for (int i = properties.size() - 1; i >= 0; i--) {
            Object prop = properties.get(i);
            if (!(prop instanceof List)) continue;
            List<Object> propList = (List<Object>) prop;
            if (propList.size() < 4) continue;

            String propName = propList.get(0) instanceof String ? ((String) propList.get(0)).toLowerCase() : "";
            if ("version".equals(propName)) continue;

            Object propValue = propList.get(3);
            String vcardPath = "vcardArray." + propName;
            List<PolicyRedactionRule> propRules = entityFieldRules.get(vcardPath);
            int sens = maxSensitivity(propRules, defaultSensitivity);
            childLevels.put(vcardPath, describeLevels(propRules, defaultSensitivity));
            boolean isEmpty = isValueEmpty(propValue);
            RedactionBehavior behavior = redactionService.getBehavior(accessLevel, sens, isEmpty);

            switch (behavior) {
                case EMPTY -> {
                    properties.remove(i);
                    redactedFields.add(vcardPath);
                }
                case REDACTED -> {
                    propList.set(3, "REDACTED");
                    redactedFields.add(vcardPath);
                }
                case FULL -> { /* leave as-is */ }
            }
        }
    }

    private void applyRedaction(Map<String, Object> map, String key, Object value,
                                 int accessLevel, int sensitivityLevel, List<String> redactedFields) {
        boolean isEmpty = isValueEmpty(value);
        RedactionBehavior behavior = redactionService.getBehavior(accessLevel, sensitivityLevel, isEmpty);

        switch (behavior) {
            case FULL -> { }
            case EMPTY -> { map.remove(key); redactedFields.add(key); }
            case REDACTED -> { map.put(key, "REDACTED"); redactedFields.add(key); }
        }
    }

    // ==================== POLICY RESOLUTION ====================

    private PolicyExpression resolvePolicy(Map<String, Object> rdapData, RdapEntity entity) {
        PolicyExpression policy = entity.getPolicyExpression();
        if (policy != null) return policy;

        policy = findPolicyByScopeConditions(rdapData, entity);
        if (policy != null) return policy;

        return policyExpressionRepository.findByIsDefaultTrueAndIsActiveTrue().orElse(null);
    }

    private PolicyExpression findPolicyByScopeConditions(Map<String, Object> rdapData, RdapEntity entity) {
        List<PolicyExpression> activePolicies = policyExpressionRepository.findByIsActiveTrue();
        for (PolicyExpression policy : activePolicies) {
            if (Boolean.TRUE.equals(policy.getIsDefault())) continue;
            String scopeJson = policy.getScopeConditions();
            if (scopeJson == null || scopeJson.isBlank()) continue;
            try {
                List<Map<String, String>> conditions = objectMapper.readValue(
                        scopeJson, new TypeReference<List<Map<String, String>>>() {});
                if (conditions.isEmpty()) continue;
                boolean allMatch = conditions.stream().allMatch(c -> evaluateCondition(c, rdapData, entity));
                if (allMatch) {
                    log.info("SCOPE MATCH: policy '{}' (id={}) matched for entity {}",
                            policy.getName(), policy.getId(), entity.getHandle());
                    return policy;
                }
            } catch (Exception e) {
                log.warn("Failed to parse scopeConditions for policy '{}': {}", policy.getName(), e.getMessage());
            }
        }
        return null;
    }

    @SuppressWarnings("unchecked")
    private boolean evaluateCondition(Map<String, String> condition, Map<String, Object> rdapData, RdapEntity entity) {
        String field = condition.get("field");
        String operator = condition.get("operator");
        String expected = condition.get("value");
        if (field == null || operator == null) return false;
        if ("__custom__".equals(field)) {
            field = condition.get("customField");
            if (field == null || field.isBlank()) return false;
        }
        String actual = resolveFieldValue(field, rdapData, entity);
        return switch (operator) {
            case "equals" -> actual != null && actual.equalsIgnoreCase(expected);
            case "not_equals" -> actual == null || !actual.equalsIgnoreCase(expected);
            case "contains" -> actual != null && expected != null && actual.toLowerCase().contains(expected.toLowerCase());
            case "not_contains" -> actual == null || expected == null || !actual.toLowerCase().contains(expected.toLowerCase());
            case "starts_with" -> actual != null && expected != null && actual.toLowerCase().startsWith(expected.toLowerCase());
            case "ends_with" -> actual != null && expected != null && actual.toLowerCase().endsWith(expected.toLowerCase());
            case "is_empty" -> actual == null || actual.isBlank();
            case "is_not_empty" -> actual != null && !actual.isBlank();
            case "in_list" -> { if (actual == null || expected == null) yield false; yield Arrays.stream(expected.split(",")).map(String::trim).anyMatch(v -> v.equalsIgnoreCase(actual)); }
            case "not_in_list" -> { if (actual == null || expected == null) yield true; yield Arrays.stream(expected.split(",")).map(String::trim).noneMatch(v -> v.equalsIgnoreCase(actual)); }
            case "matches_regex" -> { try { yield actual != null && expected != null && Pattern.matches(expected, actual); } catch (Exception e) { yield false; } }
            default -> { log.warn("Unknown operator: {}", operator); yield false; }
        };
    }

    @SuppressWarnings("unchecked")
    private String resolveFieldValue(String field, Map<String, Object> rdapData, RdapEntity entity) {
        Object value = rdapData.get(field);
        if (value != null) return stringifyValue(value);
        switch (field.toLowerCase()) {
            case "name", "ldhname" -> { return entity.getLdhName(); }
            case "handle" -> { return entity.getHandle(); }
            case "objectclassname" -> { return entity.getObjectClassName(); }
            case "unicodename", "unicode_name" -> { return entity.getUnicodeName(); }
        }
        if (field.contains(".")) {
            String[] parts = field.split("\\.");
            Object current = rdapData;
            for (String part : parts) {
                if (current instanceof Map) current = ((Map<String, Object>) current).get(part);
                else if (current instanceof List) current = findInList((List<Object>) current, part);
                else return null;
                if (current == null) return null;
            }
            return stringifyValue(current);
        }
        Object entities = rdapData.get("entities");
        if (entities instanceof List) {
            for (Object ent : (List<Object>) entities) {
                if (ent instanceof Map) {
                    Map<String, Object> entMap = (Map<String, Object>) ent;
                    // Contact-based scope decisions are driven solely by the registrant.
                    // Skip admin/tech/billing/registrar/abuse contacts so that, e.g.,
                    // an "organization is_empty" scope reflects the registrant's
                    // organization regardless of other contacts.
                    if (!hasRole(entMap, "registrant")) continue;
                    Object val = entMap.get(field);
                    if (val != null) return stringifyValue(val);
                    String vcardVal = extractFromVcard(entMap, field);
                    if (vcardVal != null) return vcardVal;
                }
            }
        }

        // Search custom table data for mapped field aliases and standard field references.
        // Custom table column mappings produce keys like "green" (custom alias) or
        // "contact.organization" (standard field reference). Both are searchable here.
        Object customTableData = rdapData.get("customTableData");
        if (customTableData instanceof List) {
            log.debug("resolveFieldValue('{}'):  searching {} custom table entries", field, ((List<?>) customTableData).size());
            for (Object tableEntry : (List<Object>) customTableData) {
                if (!(tableEntry instanceof Map)) continue;
                Map<String, Object> tableMap = (Map<String, Object>) tableEntry;
                Object data = tableMap.get("data");
                if (!(data instanceof List)) continue;
                for (Object row : (List<Object>) data) {
                    if (!(row instanceof Map)) continue;
                    Map<String, Object> rowMap = (Map<String, Object>) row;
                    log.debug("resolveFieldValue('{}'):  checking row with keys: {}", field, rowMap.keySet());
                    // Direct key match (custom aliases like "green")
                    Object val = rowMap.get(field);
                    if (val != null) {
                        log.info("resolveFieldValue('{}'):  FOUND in custom table '{}' → '{}'",
                                field, tableMap.get("tableName"), stringifyValue(val));
                        return stringifyValue(val);
                    }
                }
            }
            log.debug("resolveFieldValue('{}'):  NOT found in any custom table data", field);
        } else {
            log.debug("resolveFieldValue('{}'):  no customTableData present in rdapData (keys: {})", field,
                    rdapData.keySet().stream().limit(15).collect(java.util.stream.Collectors.toList()));
        }

        return null;
    }

    @SuppressWarnings("unchecked")
    private Object findInList(List<Object> list, String key) {
        for (Object item : list) {
            if (item instanceof Map) {
                Map<String, Object> map = (Map<String, Object>) item;
                if (map.containsKey(key)) return map.get(key);
                Object roles = map.get("roles");
                if (roles instanceof List && ((List<?>) roles).stream().anyMatch(r -> r.toString().equalsIgnoreCase(key)))
                    return map;
            }
        }
        return null;
    }

    /**
     * True if the given RDAP entity carries the specified role in its
     * "roles" array (case-insensitive). Used to restrict contact-based
     * scope-condition evaluation to the registrant.
     */
    @SuppressWarnings("unchecked")
    private boolean hasRole(Map<String, Object> entityMap, String role) {
        Object rolesObj = entityMap.get("roles");
        if (!(rolesObj instanceof List)) return false;
        return ((List<Object>) rolesObj).stream()
                .anyMatch(r -> role.equalsIgnoreCase(String.valueOf(r)));
    }

    @SuppressWarnings("unchecked")
    private String extractFromVcard(Map<String, Object> entityMap, String fieldName) {
        Object vcardArray = entityMap.get("vcardArray");
        if (!(vcardArray instanceof List)) return null;
        List<Object> vcard = (List<Object>) vcardArray;
        if (vcard.size() < 2 || !(vcard.get(1) instanceof List)) return null;
        String vcardField = switch (fieldName.toLowerCase()) {
            case "organization", "org" -> "org";
            case "name", "fn" -> "fn";
            case "email" -> "email";
            case "phone", "tel" -> "tel";
            default -> fieldName;
        };
        List<Object> properties = (List<Object>) vcard.get(1);
        for (Object prop : properties) {
            if (prop instanceof List) {
                List<Object> propList = (List<Object>) prop;
                if (propList.size() >= 4 && vcardField.equalsIgnoreCase(String.valueOf(propList.get(0))))
                    return stringifyValue(propList.get(3));
            }
        }
        return null;
    }

    private String stringifyValue(Object value) {
        if (value == null) return null;
        if (value instanceof String) return (String) value;
        if (value instanceof List) {
            List<?> list = (List<?>) value;
            return list.isEmpty() ? null : list.stream().map(Object::toString).reduce((a, b) -> a + ", " + b).orElse(null);
        }
        return value.toString();
    }

    @SuppressWarnings("unchecked")
    private void addNoteToRequestorNotice(Map<String, Object> response, String noteToRequestor) {
        List<Map<String, Object>> notices = (List<Map<String, Object>>) response.computeIfAbsent("notices", k -> new ArrayList<>());
        Map<String, Object> notice = new LinkedHashMap<>();
        notice.put("title", "Note to Requestor");
        notice.put("description", List.of(noteToRequestor));
        notices.add(notice);
    }

    @SuppressWarnings("unchecked")
    private void addRedactionNotice(Map<String, Object> response, List<String> redactedFields, String policyName) {
        List<Map<String, Object>> notices = (List<Map<String, Object>>) response.computeIfAbsent("notices", k -> new ArrayList<>());
        Map<String, Object> notice = new LinkedHashMap<>();
        notice.put("title", "Redacted Fields");
        List<String> desc = new ArrayList<>();
        desc.add("Some fields have been redacted based on your access level.");
        desc.add("Redacted fields: " + String.join(", ", redactedFields));
        if (policyName != null) desc.add("Policy: " + policyName);
        notice.put("description", desc);
        notice.put("type", "object redacted due to authorization");
        notices.add(notice);
    }

    private boolean isValueEmpty(Object value) {
        if (value == null) return true;
        if (value instanceof String s) return s.isBlank();
        if (value instanceof Collection<?> c) return c.isEmpty();
        return false;
    }

    private RdapObjectType mapEntityType(RdapEntity.ObjectType entityType) {
        if (entityType == null) return RdapObjectType.ALL;
        return switch (entityType) {
            case DOMAIN -> RdapObjectType.DOMAIN;
            case IP_NETWORK -> RdapObjectType.IP_NETWORK;
            case AUTNUM -> RdapObjectType.AUTNUM;
            case ENTITY -> RdapObjectType.ENTITY;
            case NAMESERVER -> RdapObjectType.NAMESERVER;
        };
    }

    public Optional<PolicyExpression> getDefaultPolicy() {
        return policyExpressionRepository.findByIsDefaultTrueAndIsActiveTrue();
    }

    public Optional<PolicyExpression> getPolicyById(Long id) {
        return policyExpressionRepository.findById(id);
    }

    /**
     * Resolve the effective policy for an entity when no direct policy assignment exists.
     * This is used by RdapService.processRequest() to find the applicable policy for
     * mapped entities (which have no JPA-managed policyExpression) so that policy-driven
     * controls (e.g. requiresManualVerification) are enforced.
     *
     * Builds a lightweight RDAP-data-like map from the entity and custom table data,
     * then runs the same scope-condition matching and default-policy fallback used
     * during redaction.
     *
     * @param entity           the RDAP entity (may be mapped/detached)
     * @param customTableData  resolved custom table data, or null
     * @return the matched policy, or null if none applies
     */
    public PolicyExpression resolveEffectivePolicy(RdapEntity entity, List<Map<String, Object>> customTableData) {
        // Build a minimal context map for scope-condition evaluation
        Map<String, Object> contextMap = new LinkedHashMap<>();
        if (entity.getLdhName() != null) contextMap.put("ldhName", entity.getLdhName());
        if (entity.getHandle() != null) contextMap.put("handle", entity.getHandle());
        if (entity.getObjectClassName() != null) contextMap.put("objectClassName", entity.getObjectClassName());
        if (entity.getUnicodeName() != null) contextMap.put("unicodeName", entity.getUnicodeName());
        if (entity.getStatus() != null) contextMap.put("status", entity.getStatus());
        if (entity.getPort43() != null) contextMap.put("port43", entity.getPort43());
        // Build the custom-table data the scope evaluator searches. Start from any
        // custom-table rows passed in, then add each child contact's configured
        // custom fields (e.g. "disclose", "blue") so scope conditions on those
        // custom columns resolve. This is scope-only context — it is never part of
        // the RDAP response. Custom fields resolve regardless of contact role,
        // consistent with how other custom aliases are matched.
        List<Map<String, Object>> scopeCustomData = new ArrayList<>();
        if (customTableData != null && !customTableData.isEmpty()) {
            scopeCustomData.addAll(customTableData);
        }
        if (entity.getChildren() != null) {
            for (RdapEntity child : entity.getChildren()) {
                if (child.getObjectType() != RdapEntity.ObjectType.ENTITY) continue;
                Map<String, Object> attrs = child.getCustomAttributes();
                if (attrs == null || attrs.isEmpty()) continue;
                Map<String, Object> tableEntry = new LinkedHashMap<>();
                tableEntry.put("tableName", "contact:" + (child.getHandle() != null ? child.getHandle() : ""));
                tableEntry.put("data", List.of(new LinkedHashMap<>(attrs)));
                scopeCustomData.add(tableEntry);
            }
        }
        if (!scopeCustomData.isEmpty()) {
            contextMap.put("customTableData", scopeCustomData);
        }

        // Include child contact entities so contact-based scope conditions
        // (e.g. an "organization is_empty" check driven by the registrant) can
        // resolve. Each child is serialized to the canonical RDAP shape: the
        // entity-level "roles" array (so the registrant filter in resolveFieldValue
        // can select it) plus a jCard "vcardArray" (so extractFromVcard can read
        // organization/name/email/phone). Without this, mapped/custom contacts are
        // invisible to scope evaluation and selection silently falls to the default.
        if (entity.getChildren() != null && !entity.getChildren().isEmpty()) {
            List<Map<String, Object>> contactEntities = new ArrayList<>();
            for (RdapEntity child : entity.getChildren()) {
                if (child.getObjectType() == RdapEntity.ObjectType.ENTITY) {
                    contactEntities.add(toScopeEntityMap(child));
                }
            }
            if (!contactEntities.isEmpty()) {
                contextMap.put("entities", contactEntities);
            }
        }

        // Try scope-condition matching first
        PolicyExpression policy = findPolicyByScopeConditions(contextMap, entity);
        if (policy != null) return policy;

        // Fall back to system default
        return policyExpressionRepository.findByIsDefaultTrueAndIsActiveTrue().orElse(null);
    }

    /**
     * Serialize a child contact entity into the RDAP map shape used for scope
     * evaluation: the entity-level "roles" array plus a jCard "vcardArray".
     * Mirrors the canonical output serialization so scope conditions see the same
     * structure the client receives. The registrant designation rides on "roles"
     * (a sibling of vcardArray), not inside the vcard.
     */
    private Map<String, Object> toScopeEntityMap(RdapEntity c) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("objectClassName", c.getObjectClassName() != null ? c.getObjectClassName() : "entity");
        if (c.getHandle() != null) m.put("handle", c.getHandle());
        if (c.getRoles() != null && !c.getRoles().isEmpty()) m.put("roles", c.getRoles());
        m.put("vcardArray", buildVcard(c));
        // Expose this contact's custom fields (e.g. "disclose") so scope conditions
        // on non-standard fields resolve against the contact directly — and, because
        // the entities loop is registrant-filtered, are driven by the registrant.
        // putIfAbsent avoids clobbering the standard keys above.
        if (c.getCustomAttributes() != null) {
            for (Map.Entry<String, Object> e : c.getCustomAttributes().entrySet()) {
                if (e.getKey() != null) m.putIfAbsent(e.getKey(), e.getValue());
            }
        }
        return m;
    }

    /**
     * Build a jCard (vcardArray) from a contact entity's flat fields, matching the
     * canonical RDAP serialization (version, fn, org, email, tel, fax, adr). Only
     * the field properties scope conditions can read are emitted; absent fields are
     * skipped so extractFromVcard correctly resolves them as empty.
     */
    private List<Object> buildVcard(RdapEntity e) {
        List<Object> vcard = new ArrayList<>();
        vcard.add("vcard");
        List<List<Object>> props = new ArrayList<>();
        props.add(new ArrayList<>(List.of("version", new LinkedHashMap<>(), "text", "4.0")));
        if (notBlank(e.getContactName()))
            props.add(new ArrayList<>(List.of("fn", new LinkedHashMap<>(), "text", e.getContactName())));
        if (notBlank(e.getOrganization()))
            props.add(new ArrayList<>(List.of("org", new LinkedHashMap<>(), "text", e.getOrganization())));
        if (notBlank(e.getEmail()))
            props.add(new ArrayList<>(List.of("email", new LinkedHashMap<>(), "text", e.getEmail())));
        if (notBlank(e.getPhone())) {
            Map<String, Object> telParams = new LinkedHashMap<>();
            telParams.put("type", "voice");
            props.add(new ArrayList<>(List.of("tel", telParams, "uri",
                    e.getPhone().startsWith("tel:") ? e.getPhone() : "tel:" + e.getPhone())));
        }
        if (notBlank(e.getFax())) {
            Map<String, Object> faxParams = new LinkedHashMap<>();
            faxParams.put("type", "fax");
            props.add(new ArrayList<>(List.of("tel", faxParams, "uri",
                    e.getFax().startsWith("tel:") ? e.getFax() : "tel:" + e.getFax())));
        }
        if (notBlank(e.getAddressStreet1()) || notBlank(e.getAddressCity()) || notBlank(e.getAddressCountry())) {
            List<String> adr = List.of(
                    "",
                    e.getAddressStreet2() != null ? e.getAddressStreet2() : "",
                    e.getAddressStreet1() != null ? e.getAddressStreet1() : "",
                    e.getAddressCity() != null ? e.getAddressCity() : "",
                    e.getAddressState() != null ? e.getAddressState() : "",
                    e.getAddressPostalCode() != null ? e.getAddressPostalCode() : "",
                    e.getAddressCountry() != null ? e.getAddressCountry() : "");
            props.add(new ArrayList<>(List.of("adr", new LinkedHashMap<>(), "text", adr)));
        }
        vcard.add(props);
        return vcard;
    }

    private boolean notBlank(String s) {
        return s != null && !s.isBlank();
    }
}