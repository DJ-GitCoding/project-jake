/*
 * SPDX-FileCopyrightText: 2025-2026 Edgemoor Research Institute and Derek Jenkins
 * SPDX-License-Identifier: AGPL-3.0-only
 *
 * Author: Derek Jenkins <derek@pure-code.net>
 * Additional terms under AGPL-3.0 Section 7 apply. See NOTICE at the repository root.
 */

package com.jaddar.dataholder.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jaddar.dataholder.dto.PolicyExpressionDto.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.util.*;

/**
 * Service that imports policy expression JSON files (registry policy format)
 * and transforms them into PolicyExpression entities with per-field sensitivity levels.
 *
 * <p>As of the updated import format each rule is a flat array entry that carries its
 * own {@code name}/{@code group}/{@code category} inline and exposes {@code sensitivity}
 * and {@code validation} as direct 0–3 levels. This removes the previous need to join
 * against a {@code defintions.elements} lookup and to decode bitmask attribute codes.
 *
 * <p>Each rule's sensitivity level (0-3) is carried onto the per-field entry; the
 * redaction matrix then determines behavior at query time.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class PolicyImportService {

    private final PolicyExpressionService policyExpressionService;
    private final ObjectMapper objectMapper;
    private final ContactRoleResolver contactRoleResolver;

    /** Sensitivity levels are supplied directly (0-3) by the new format. */
    private static String sensitivityLabel(int level) {
        return switch (level) {
            case 0 -> "S0 — Public";
            case 1 -> "S1 — Private";
            case 2 -> "S2 — Very Private";
            case 3 -> "S3 — Ultra Private";
            default -> "S" + level;
        };
    }

    /** Validation levels are supplied directly (0-3) by the new format. */
    private static String validationLabel(int level) {
        return switch (level) {
            case 0 -> "V0 — None";
            case 1 -> "V1 — Syntactic";
            case 2 -> "V2 — Operational";
            case 3 -> "V3 — Identification";
            default -> "V" + level;
        };
    }

    private static int clampLevel(int level) {
        if (level < 0) return 0;
        if (level > 3) return 3;
        return level;
    }

    public record ImportFileResult(
            String fileName, boolean success, String policyName, Long policyId,
            int fieldRuleCount, String error
    ) {
        public static ImportFileResult success(String fileName, String policyName, Long policyId, int count) {
            return new ImportFileResult(fileName, true, policyName, policyId, count, null);
        }
        public static ImportFileResult failure(String fileName, String error) {
            return new ImportFileResult(fileName, false, null, null, 0, error);
        }
    }

    public PolicyExpressionRequest previewImport(MultipartFile file) throws Exception {
        JsonNode root = objectMapper.readTree(file.getInputStream());
        validate(root, file.getOriginalFilename());
        return transform(root, file.getOriginalFilename());
    }

    public List<Map<String, Object>> previewImportMultiple(MultipartFile[] files) {
        List<Map<String, Object>> results = new ArrayList<>();
        for (MultipartFile file : files) {
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("fileName", file.getOriginalFilename());
            try {
                PolicyExpressionRequest request = previewImport(file);
                result.put("success", true);
                result.put("policy", request);
            } catch (Exception e) {
                result.put("success", false);
                result.put("error", e.getMessage());
            }
            results.add(result);
        }
        return results;
    }

    public ImportFileResult importFile(MultipartFile file) {
        String fileName = file.getOriginalFilename();
        try {
            JsonNode root = objectMapper.readTree(file.getInputStream());
            validate(root, fileName);
            PolicyExpressionRequest request = transform(root, fileName);
            PolicyExpressionResponse created = policyExpressionService.createPolicyExpression(request);

            int ruleCount = created.getRedactionRules() != null ? created.getRedactionRules().size() : 0;
            log.info("Imported policy from {}: '{}' (id={}, {} field rules)",
                    fileName, created.getName(), created.getId(), ruleCount);
            return ImportFileResult.success(fileName, created.getName(), created.getId(), ruleCount);
        } catch (Exception e) {
            log.error("Failed to import policy from {}: {}", fileName, e.getMessage());
            return ImportFileResult.failure(fileName, e.getMessage());
        }
    }

    public List<ImportFileResult> importFiles(MultipartFile[] files) {
        List<ImportFileResult> results = new ArrayList<>();
        for (MultipartFile file : files) results.add(importFile(file));
        return results;
    }

    private void validate(JsonNode root, String fileName) {
        if (!root.has("rules") || !root.get("rules").isArray())
            throw new IllegalArgumentException("File '" + fileName + "' missing required 'rules' array");
    }

    private PolicyExpressionRequest transform(JsonNode root, String fileName) {
        // Transform rules → per-field sensitivity entries.
        // Each rule now carries its element name/group/category inline and exposes
        // sensitivity/validation as direct 0-3 levels, so no definitions lookup or
        // bitmask decoding is required.
        List<RedactionRuleRequest> fieldRules = new ArrayList<>();
        JsonNode rules = root.path("rules");
        int ruleOrder = 0;

        // Contact groups whose role is neither built-in nor an active custom role.
        // Any such group blocks the whole file (reported below).
        Set<String> undefinedRoles = new LinkedHashSet<>();

        for (JsonNode rule : rules) {
            String collect = rule.path("collect").asText("").trim();
            if (collect.equalsIgnoreCase("Omit")) continue;

            ElementDef element = new ElementDef(
                    rule.path("element_id").asInt(),
                    rule.path("name").asText(""),
                    rule.path("group").asText(""),
                    rule.path("category").asText("")
            );
            if (element.name.isBlank()) continue;

            // Structural groups (DNS, scope, status, forensic) carry no contact role.
            // Contact groups must resolve to a defined role or the import is blocked.
            String resolvedRole = null;
            if (!isStructuralGroup(element.groupName)) {
                java.util.Optional<String> roleKey = contactRoleResolver.resolveRoleKey(element.groupName);
                if (roleKey.isEmpty()) {
                    undefinedRoles.add(element.groupName.trim());
                    continue;
                }
                resolvedRole = roleKey.get();
            }

            int sensitivityLevel = clampLevel(rule.path("sensitivity").asInt(0));

            Integer validationLevel = rule.has("validation") ? clampLevel(rule.path("validation").asInt(0)) : null;

            FieldMapping mapping = mapElementToRdapField(element, resolvedRole);
            String collLabel = collect.isBlank() ? "Unspecified" : collect;

            RedactionRuleRequest ruleReq = new RedactionRuleRequest();
            ruleReq.setObjectType(mapping.objectType);
            ruleReq.setFieldPath(mapping.fieldPath);
            ruleReq.setFieldDisplayName(mapping.displayName);
            ruleReq.setSensitivityLevel(sensitivityLevel);
            ruleReq.setValidationLevel(validationLevel);
            ruleReq.setDescription(String.format("Sensitivity: %s. Validation: %s. Collection: %s. Source element: %s (ID: %d)",
                    sensitivityLabel(sensitivityLevel),
                    validationLevel != null ? validationLabel(validationLevel) : "No decision",
                    collLabel, element.name, element.id));
            ruleReq.setRuleOrder(ruleOrder++);
            ruleReq.setIsEnabled(true);
            fieldRules.add(ruleReq);
        }

        // Block the whole file if it references any role that is not defined.
        if (!undefinedRoles.isEmpty()) {
            throw new IllegalArgumentException(
                    "Import blocked — undefined contact role(s): " + String.join(", ", undefinedRoles) +
                    ". Define them under Settings → Custom Roles before importing this policy.");
        }

        // Deduplicate by (objectType, fieldPath) — keep higher sensitivity
        Map<String, RedactionRuleRequest> deduped = new LinkedHashMap<>();
        for (RedactionRuleRequest r : fieldRules) {
            String key = r.getObjectType() + "|" + r.getFieldPath();
            RedactionRuleRequest existing = deduped.get(key);
            if (existing == null) {
                deduped.put(key, r);
            } else if (r.getSensitivityLevel() > existing.getSensitivityLevel()) {
                r.setDescription(truncate(existing.getDescription() + " | " + r.getDescription(), 250));
                // Keep the higher validation level too
                if (existing.getValidationLevel() != null && (r.getValidationLevel() == null || existing.getValidationLevel() > r.getValidationLevel())) {
                    r.setValidationLevel(existing.getValidationLevel());
                }
                deduped.put(key, r);
            } else {
                existing.setDescription(truncate(existing.getDescription() + " | " + r.getDescription(), 250));
                if (r.getValidationLevel() != null && (existing.getValidationLevel() == null || r.getValidationLevel() > existing.getValidationLevel())) {
                    existing.setValidationLevel(r.getValidationLevel());
                }
            }
        }
        fieldRules = new ArrayList<>(deduped.values());
        for (int i = 0; i < fieldRules.size(); i++) fieldRules.get(i).setRuleOrder(i);

        // Build description
        List<String> descParts = new ArrayList<>();
        String notes = root.path("notes").asText("");
        if (!notes.isBlank()) descParts.add(notes.trim());
        descParts.add("Imported from: " + fileName);
        String orgName = root.path("org_name").asText("");
        if (!orgName.isBlank()) descParts.add("Organization: " + orgName);
        String scopeSummary = summarizeScope(root.path("scope_attributes"));
        if (!scopeSummary.isBlank()) descParts.add("Scope: " + scopeSummary);
        String effectiveDate = root.path("effective_date").asText("");
        if (!effectiveDate.isBlank()) descParts.add("Effective: " + effectiveDate);
        int version = root.path("version").asInt(0);
        if (version > 0) descParts.add("Version: " + version);

        PolicyExpressionRequest request = new PolicyExpressionRequest();
        request.setName(root.path("description").asText(fileName.replace(".json", "").replace("_", " ")));
        request.setDescription(String.join("\n", descParts));
        request.setIsActive(true);
        request.setIsDefault(false);
        request.setRedactionRules(fieldRules);

        return request;
    }

    private record ElementDef(int id, String name, String groupName, String categoryName) {}
    private record FieldMapping(String objectType, String fieldPath, String displayName) {}

    /**
     * Builds a short human-readable summary of the policy's scope from the
     * {@code scope_attributes} block (person / protection / nexus / personal),
     * e.g. "Natural / Protected / Any / Any". Attributes are available inline,
     * so no manual lookup is required.
     */
    private String summarizeScope(JsonNode scopeAttributes) {
        if (scopeAttributes == null || !scopeAttributes.isObject()) return "";
        List<String> parts = new ArrayList<>();
        for (String key : List.of("person", "protection", "nexus", "personal")) {
            String name = scopeAttributes.path(key).path("name").asText("");
            if (!name.isBlank()) parts.add(name);
        }
        return String.join(" / ", parts);
    }

    /**
     * True for the non-contact structural groups (DNS records, registration scope,
     * op status, payment/transaction forensics). These are handled by the leading
     * branches of {@link #mapElementToRdapField} and carry no contact role.
     */
    private boolean isStructuralGroup(String groupName) {
        String g = groupName == null ? "" : groupName.trim().toLowerCase();
        return g.contains("dns") || g.contains("registration scope") || g.contains("op status")
                || g.contains("payment") || g.contains("transaction");
    }

    /**
     * Maps a policy element to an IETF RDAP field path. For contact-role groups the
     * caller supplies the already-resolved {@code contactRole} key (built-in or custom);
     * it is {@code null} for structural groups, which are handled by the leading branches.
     */
    private FieldMapping mapElementToRdapField(ElementDef element, String contactRole) {
        String name = element.name.trim();
        String group = element.groupName.trim().toLowerCase();
        String category = element.categoryName.trim();

        // DNS / domain-level fields (group: "dns records")
        if (group.contains("dns")) {
            if ("Domain Name".equals(name)) return new FieldMapping("DOMAIN", "ldhName", name);
            if ("NS".equals(name)) return new FieldMapping("NAMESERVER", "ldhName", "Nameserver");
            if (name.contains("Date Created")) return new FieldMapping("DOMAIN", "events.registration", name);
            if (name.contains("Date Expires")) return new FieldMapping("DOMAIN", "events.expiration", name);
            if (name.contains("Registrar URL")) return new FieldMapping("ENTITY", "registrar.url", name);
            if (name.contains("Registrar")) return new FieldMapping("ENTITY", "registrar.fn", name);
            // Generic DNS field
            return new FieldMapping("DOMAIN", "dns." + name.toLowerCase().replaceAll("[^a-z0-9]", "_"), name);
        }

        // Registration scope / operational fields
        if (group.contains("registration scope")) {
            return new FieldMapping("DOMAIN", "scope." + name.toLowerCase().replaceAll("[^a-z0-9]", "_"), name);
        }
        if (group.contains("op status")) {
            if (name.contains("Status") || name.contains("Lock"))
                return new FieldMapping("DOMAIN", "status", name);
            return new FieldMapping("DOMAIN", "status." + name.toLowerCase().replaceAll("[^a-z0-9]", "_"), name);
        }

        // Payment & transaction forensics
        if (group.contains("payment") || group.contains("transaction")) {
            return new FieldMapping("ENTITY", "forensic." + name.toLowerCase().replaceAll("[^a-z0-9]", "_"), name);
        }

        // Contact-role groups — role was resolved by the caller (built-in or custom).
        // A null role here means the group wasn't structural but also had no resolved
        // role; fall back to a generic bucket rather than colliding with a real role.
        if (contactRole == null) contactRole = "entity";

        String ln = name.toLowerCase();
        // Locale variants (Int'l vs Local) are tracked as separate field entries
        // so each gets its own sensitivity/validation rule in the policy expression.
        // A ".local" suffix is appended to the field path for local-script fields.
        boolean isLocal = ln.contains("(local)");
        boolean isIntl = ln.contains("(int'l") || ln.contains("(intl");
        String localeNote = isLocal ? " (Local)" : isIntl ? " (Int'l)" : "";
        String localeSuffix = isLocal ? ".local" : "";

        if (ln.contains("email_or_phone")) return new FieldMapping("ENTITY", contactRole + ".contact_pref", name + " (" + contactRole + ")");
        if (ln.contains("email")) return new FieldMapping("ENTITY", contactRole + ".email", name + " (" + contactRole + ")");
        if (ln.contains("phone")) return new FieldMapping("ENTITY", contactRole + ".tel", name + " (" + contactRole + ")");
        if (ln.contains("fax")) return new FieldMapping("ENTITY", contactRole + ".fax", name + " (" + contactRole + ")");
        if (ln.startsWith("name")) return new FieldMapping("ENTITY", contactRole + ".fn" + localeSuffix, name + localeNote + " (" + contactRole + ")");
        // ID fields must be checked BEFORE the broader startsWith("organization") to avoid
        // "Organization ID" being mismatched to .org instead of .org_id
        if (ln.contains("organization id")) return new FieldMapping("ENTITY", contactRole + ".org_id", name + " (" + contactRole + ")");
        if (ln.contains("personal id")) return new FieldMapping("ENTITY", contactRole + ".personal_id", name + " (" + contactRole + ")");
        if (ln.startsWith("organization") || ln.startsWith("organizarion")) return new FieldMapping("ENTITY", contactRole + ".org" + localeSuffix, name + localeNote + " (" + contactRole + ")");
        if (ln.contains("street")) {
            // Extract the street address number (1, 2, 3) so each line gets a distinct field path
            // and the dedup step does not collapse them into a single entry
            String streetSuffix = "";
            java.util.regex.Matcher m = java.util.regex.Pattern.compile("\\d+").matcher(name);
            if (m.find()) streetSuffix = m.group();
            String fieldPath = contactRole + ".adr.street" + streetSuffix + localeSuffix;
            return new FieldMapping("ENTITY", fieldPath, name + localeNote + " (" + contactRole + ")");
        }
        if (ln.startsWith("city")) return new FieldMapping("ENTITY", contactRole + ".adr.city" + localeSuffix, name + localeNote + " (" + contactRole + ")");
        if (ln.startsWith("state")) return new FieldMapping("ENTITY", contactRole + ".adr.sp" + localeSuffix, name + localeNote + " (" + contactRole + ")");
        if (ln.startsWith("post") || ln.startsWith("postal")) return new FieldMapping("ENTITY", contactRole + ".adr.pc" + localeSuffix, name + localeNote + " (" + contactRole + ")");
        if (ln.startsWith("country")) return new FieldMapping("ENTITY", contactRole + ".adr.cc" + localeSuffix, name + localeNote + " (" + contactRole + ")");
        if (ln.contains("unique") || ln.contains("user account")) return new FieldMapping("ENTITY", contactRole + ".handle", name + " (" + contactRole + ")");
        if (ln.contains("reserved")) return new FieldMapping("ENTITY", contactRole + ".reserved", name + " (" + contactRole + ")");

        // Fallback: use element ID
        return new FieldMapping("ALL", "element_" + element.id, name);
    }

    private static String truncate(String s, int maxLen) {
        if (s == null || s.length() <= maxLen) return s;
        return s.substring(0, maxLen - 3) + "...";
    }
}