/*
 * SPDX-FileCopyrightText: 2025-2026 Edgemoor Research Institute and Derek Jenkins
 * SPDX-License-Identifier: AGPL-3.0-only
 *
 * Author: Derek Jenkins <derek@pure-code.net>
 * Additional terms under AGPL-3.0 Section 7 apply. See NOTICE at the repository root.
 */

package com.jaddar.dataholder.service;

import com.jaddar.dataholder.entity.RedactionRule;
import com.jaddar.dataholder.entity.RedactionRule.RedactionBehavior;
import com.jaddar.dataholder.entity.RdapEntity;
import com.jaddar.dataholder.repository.RedactionRuleRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import jakarta.annotation.PostConstruct;
import java.util.*;

/**
 * Service for applying redaction rules to RDAP data.
 *
 * The redaction model is a 32-row matrix keyed by:
 *   (accessLevel, sensitivityLevel, valueIsEmpty) → behavior
 *
 * Where behavior is one of FULL, EMPTY, or REDACTED.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class RedactionService {

    private final RedactionRuleRepository redactionRuleRepository;

    // Cache: key = "accessLevel:sensitivityLevel:isEmpty" → RedactionRule
    private Map<String, RedactionRule> ruleCache = new HashMap<>();

    @PostConstruct
    public void init() {
        refreshRuleCache();
    }

    /**
     * Refresh the rule cache from database.
     */
    public void refreshRuleCache() {
        List<RedactionRule> activeRules = redactionRuleRepository
                .findByIsActiveTrueOrderByAccessLevelAscSensitivityLevelAscEmptyValueAsc();
        ruleCache = new HashMap<>();
        for (RedactionRule rule : activeRules) {
            String key = buildKey(rule.getAccessLevel(), rule.getSensitivityLevel(), rule.getEmptyValue());
            ruleCache.put(key, rule);
        }
        log.info("Loaded {} active redaction rules into cache", ruleCache.size());
    }

    /**
     * Look up the behavior for a given combination.
     * If no rule is found, defaults to FULL (return actual value).
     */
    public RedactionBehavior getBehavior(int accessLevel, int sensitivityLevel, boolean valueIsEmpty) {
        String key = buildKey(accessLevel, sensitivityLevel, valueIsEmpty);
        RedactionRule rule = ruleCache.get(key);
        if (rule == null) {
            return RedactionBehavior.FULL;
        }
        return rule.getRedactionBehavior();
    }

    /**
     * Get the value to return for a field given the access level, sensitivity level,
     * and the actual value of the field.
     *
     * @return the value to include in the response, or null if the field should be omitted.
     */
    public Object getFieldValue(Object actualValue, int accessLevel, int sensitivityLevel) {
        boolean isEmpty = isValueEmpty(actualValue);
        RedactionBehavior behavior = getBehavior(accessLevel, sensitivityLevel, isEmpty);

        return switch (behavior) {
            case FULL -> actualValue;
            case REDACTED -> "REDACTED";
            case EMPTY -> null;
        };
    }

    /**
     * Check whether a field should be included in the response at all.
     */
    public boolean shouldIncludeField(Object actualValue, int accessLevel, int sensitivityLevel) {
        boolean isEmpty = isValueEmpty(actualValue);
        RedactionBehavior behavior = getBehavior(accessLevel, sensitivityLevel, isEmpty);
        return behavior != RedactionBehavior.EMPTY;
    }

    /**
     * Apply redaction to an RDAP entity and build the response map.
     */
    @Transactional(readOnly = true)
    public Map<String, Object> buildRedactedResponse(RdapEntity entity, int accessLevel, int sensitivityLevel) {
        Map<String, Object> response = new LinkedHashMap<>();

        // Always include structural fields
        response.put("objectClassName", entity.getObjectClassName());

        // Apply rules to each field
        addFieldIfAllowed(response, "handle", entity.getHandle(), accessLevel, sensitivityLevel);

        switch (entity.getObjectType()) {
            case DOMAIN -> buildDomainResponse(response, entity, accessLevel, sensitivityLevel);
            case IP_NETWORK -> buildIpNetworkResponse(response, entity, accessLevel, sensitivityLevel);
            case AUTNUM -> buildAutnumResponse(response, entity, accessLevel, sensitivityLevel);
            case ENTITY -> buildEntityResponse(response, entity, accessLevel, sensitivityLevel);
            case NAMESERVER -> buildNameserverResponse(response, entity, accessLevel, sensitivityLevel);
        }

        if (entity.getStatus() != null && !entity.getStatus().isEmpty()) {
            addFieldIfAllowed(response, "status", entity.getStatus(), accessLevel, sensitivityLevel);
        }
        addFieldIfAllowed(response, "port43", entity.getPort43(), accessLevel, sensitivityLevel);

        return response;
    }

    private void buildDomainResponse(Map<String, Object> response, RdapEntity entity, int al, int sl) {
        addFieldIfAllowed(response, "ldhName", entity.getLdhName(), al, sl);
        addFieldIfAllowed(response, "unicodeName", entity.getUnicodeName(), al, sl);

        if (entity.getSecureDnsDelegationSigned() != null || entity.getSecureDnsZoneSigned() != null) {
            Map<String, Object> secureDns = new LinkedHashMap<>();
            addFieldIfAllowed(secureDns, "delegationSigned", entity.getSecureDnsDelegationSigned(), al, sl);
            addFieldIfAllowed(secureDns, "zoneSigned", entity.getSecureDnsZoneSigned(), al, sl);
            if (!secureDns.isEmpty()) {
                response.put("secureDNS", secureDns);
            }
        }
    }

    private void buildIpNetworkResponse(Map<String, Object> response, RdapEntity entity, int al, int sl) {
        addFieldIfAllowed(response, "startAddress", entity.getStartAddress(), al, sl);
        addFieldIfAllowed(response, "endAddress", entity.getEndAddress(), al, sl);
        addFieldIfAllowed(response, "ipVersion", entity.getIpVersion(), al, sl);
        addFieldIfAllowed(response, "name", entity.getNetworkName(), al, sl);
        addFieldIfAllowed(response, "type", entity.getNetworkType(), al, sl);
        addFieldIfAllowed(response, "country", entity.getCountry(), al, sl);
        addFieldIfAllowed(response, "parentHandle", entity.getParentHandle(), al, sl);
    }

    private void buildAutnumResponse(Map<String, Object> response, RdapEntity entity, int al, int sl) {
        addFieldIfAllowed(response, "startAutnum", entity.getStartAutnum(), al, sl);
        addFieldIfAllowed(response, "endAutnum", entity.getEndAutnum(), al, sl);
        addFieldIfAllowed(response, "name", entity.getAutnumName(), al, sl);
        addFieldIfAllowed(response, "type", entity.getAutnumType(), al, sl);
        addFieldIfAllowed(response, "country", entity.getCountry(), al, sl);
    }

    private void buildEntityResponse(Map<String, Object> response, RdapEntity entity, int al, int sl) {
        if (entity.getRoles() != null && !entity.getRoles().isEmpty()) {
            addFieldIfAllowed(response, "roles", entity.getRoles(), al, sl);
        }
        if (entity.getPublicIds() != null && !entity.getPublicIds().isEmpty()) {
            addFieldIfAllowed(response, "publicIds", buildPublicIds(entity.getPublicIds()), al, sl);
        }

        List<Object> vcardArray = buildVcardArray(entity, al, sl);
        if (vcardArray != null && !vcardArray.isEmpty()) {
            response.put("vcardArray", Arrays.asList("vcard", vcardArray));
        }
    }

    private void buildNameserverResponse(Map<String, Object> response, RdapEntity entity, int al, int sl) {
        addFieldIfAllowed(response, "ldhName", entity.getLdhName(), al, sl);
        addFieldIfAllowed(response, "unicodeName", entity.getUnicodeName(), al, sl);
    }

    private List<Object> buildVcardArray(RdapEntity entity, int al, int sl) {
        List<Object> vcardProperties = new ArrayList<>();
        vcardProperties.add(Arrays.asList("version", new HashMap<>(), "text", "4.0"));

        Object fnValue = getFieldValue(entity.getContactName(), al, sl);
        if (fnValue != null) {
            vcardProperties.add(Arrays.asList("fn", new HashMap<>(), "text", fnValue));
        }
        Object orgValue = getFieldValue(entity.getOrganization(), al, sl);
        if (orgValue != null) {
            vcardProperties.add(Arrays.asList("org", new HashMap<>(), "text", orgValue));
        }
        Object emailValue = getFieldValue(entity.getEmail(), al, sl);
        if (emailValue != null) {
            vcardProperties.add(Arrays.asList("email", new HashMap<>(), "text", emailValue));
        }
        Object phoneValue = getFieldValue(entity.getPhone(), al, sl);
        if (phoneValue != null) {
            Map<String, String> phoneParams = new HashMap<>();
            phoneParams.put("type", "voice");
            vcardProperties.add(Arrays.asList("tel", phoneParams, "uri", "tel:" + phoneValue));
        }
        Object faxValue = getFieldValue(entity.getFax(), al, sl);
        if (faxValue != null) {
            Map<String, String> faxParams = new HashMap<>();
            faxParams.put("type", "fax");
            vcardProperties.add(Arrays.asList("tel", faxParams, "uri", "tel:" + faxValue));
        }

        if (hasAddressData(entity)) {
            List<Object> addressComponents = buildAddressComponents(entity, al, sl);
            if (addressComponents != null) {
                vcardProperties.add(Arrays.asList("adr", new HashMap<>(), "text", addressComponents));
            }
        }

        return vcardProperties.size() > 1 ? vcardProperties : null;
    }

    private boolean hasAddressData(RdapEntity entity) {
        return entity.getAddressStreet1() != null || entity.getAddressCity() != null ||
                entity.getAddressState() != null || entity.getAddressPostalCode() != null ||
                entity.getAddressCountry() != null;
    }

    private List<Object> buildAddressComponents(RdapEntity entity, int al, int sl) {
        List<Object> components = new ArrayList<>();
        components.add(""); // PO Box
        components.add(""); // Extended address

        Object street = getFieldValue(combineStreets(entity.getAddressStreet1(), entity.getAddressStreet2()), al, sl);
        components.add(street != null ? street : "");

        Object city = getFieldValue(entity.getAddressCity(), al, sl);
        components.add(city != null ? city : "");

        Object state = getFieldValue(entity.getAddressState(), al, sl);
        components.add(state != null ? state : "");

        Object postalCode = getFieldValue(entity.getAddressPostalCode(), al, sl);
        components.add(postalCode != null ? postalCode : "");

        Object country = getFieldValue(entity.getAddressCountry(), al, sl);
        components.add(country != null ? country : "");

        boolean hasData = components.stream()
                .anyMatch(c -> c != null && !c.toString().isEmpty());

        return hasData ? components : null;
    }

    private String combineStreets(String street1, String street2) {
        if (street1 == null && street2 == null) return null;
        if (street1 == null) return street2;
        if (street2 == null) return street1;
        return street1 + ", " + street2;
    }

    private List<Map<String, String>> buildPublicIds(List<String> publicIds) {
        return publicIds.stream()
                .map(id -> {
                    Map<String, String> publicId = new HashMap<>();
                    publicId.put("type", "IANA Registrar ID");
                    publicId.put("identifier", id);
                    return publicId;
                })
                .toList();
    }

    /**
     * Add a field to the response map if allowed by the redaction matrix.
     */
    private void addFieldIfAllowed(Map<String, Object> map, String key, Object value,
                                    int accessLevel, int sensitivityLevel) {
        if (value == null) return;

        if (!shouldIncludeField(value, accessLevel, sensitivityLevel)) {
            return; // EMPTY behavior — omit
        }

        Object outputValue = getFieldValue(value, accessLevel, sensitivityLevel);
        if (outputValue != null) {
            map.put(key, outputValue);
        }
    }

    // ==================== CRUD Operations ====================

    @Transactional(readOnly = true)
    public List<RedactionRule> getAllRules() {
        return redactionRuleRepository.findAllOrdered();
    }

    @Transactional(readOnly = true)
    public List<RedactionRule> getActiveRules() {
        return redactionRuleRepository
                .findByIsActiveTrueOrderByAccessLevelAscSensitivityLevelAscEmptyValueAsc();
    }

    @Transactional(readOnly = true)
    public Optional<RedactionRule> getRuleById(Long id) {
        return redactionRuleRepository.findById(id);
    }

    @Transactional
    public RedactionRule updateRule(Long id, RedactionRule updates) {
        RedactionRule existing = redactionRuleRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Rule not found: " + id));

        if (updates.getRedactionBehavior() != null) {
            existing.setRedactionBehavior(updates.getRedactionBehavior());
        }
        if (updates.getIsActive() != null) {
            existing.setIsActive(updates.getIsActive());
        }

        RedactionRule saved = redactionRuleRepository.save(existing);
        refreshRuleCache();
        return saved;
    }

    @Transactional
    public void toggleRuleActive(Long id) {
        RedactionRule rule = redactionRuleRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Rule not found: " + id));
        rule.setIsActive(!rule.getIsActive());
        redactionRuleRepository.save(rule);
        refreshRuleCache();
    }

    /**
     * Bulk update behaviors for multiple rules.
     */
    @Transactional
    public void bulkUpdateBehavior(List<Long> ruleIds, RedactionBehavior behavior) {
        for (Long id : ruleIds) {
            redactionRuleRepository.findById(id).ifPresent(rule -> {
                rule.setRedactionBehavior(behavior);
                redactionRuleRepository.save(rule);
            });
        }
        refreshRuleCache();
    }

    /**
     * Initialize the full 32-row matrix if not present.
     */
    @Transactional
    public void initializeDefaultRules() {
        if (redactionRuleRepository.count() > 0) {
            log.info("Redaction rules already exist, skipping initialization");
            return;
        }

        log.info("Initializing default 32-row redaction rules matrix");
        List<RedactionRule> rules = new ArrayList<>();

        for (int accessLevel = 0; accessLevel <= 3; accessLevel++) {
            for (int sensitivityLevel = 0; sensitivityLevel <= 3; sensitivityLevel++) {
                for (boolean isEmpty : new boolean[]{false, true}) {
                    RedactionBehavior behavior = computeDefaultBehavior(accessLevel, sensitivityLevel, isEmpty);
                    rules.add(RedactionRule.builder()
                            .accessLevel(accessLevel)
                            .sensitivityLevel(sensitivityLevel)
                            .emptyValue(isEmpty)
                            .redactionBehavior(behavior)
                            .isActive(true)
                            .build());
                }
            }
        }

        redactionRuleRepository.saveAll(rules);
        refreshRuleCache();
        log.info("Created {} default redaction rules", rules.size());
    }

    /**
     * Compute a sensible default behavior for each combination.
     * General logic:
     *  - If the value is empty, return EMPTY (nothing to show)
     *  - If accessLevel >= sensitivityLevel, return FULL
     *  - Otherwise return REDACTED
     */
    private RedactionBehavior computeDefaultBehavior(int accessLevel, int sensitivityLevel, boolean isEmpty) {
        if (isEmpty) {
            return RedactionBehavior.EMPTY;
        }
        if (accessLevel >= sensitivityLevel) {
            return RedactionBehavior.FULL;
        }
        return RedactionBehavior.REDACTED;
    }

    // ==================== Utility ====================

    private String buildKey(int accessLevel, int sensitivityLevel, boolean valueIsEmpty) {
        return accessLevel + ":" + sensitivityLevel + ":" + (valueIsEmpty ? "1" : "0");
    }

    private boolean isValueEmpty(Object value) {
        if (value == null) return true;
        if (value instanceof String s) return s.isBlank();
        if (value instanceof Collection<?> c) return c.isEmpty();
        return false;
    }
}
